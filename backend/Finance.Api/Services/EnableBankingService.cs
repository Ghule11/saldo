using System.Collections.Concurrent;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.DataProtection;

namespace Finance.Api.Services;

public sealed class EnableBankingService(
    HttpClient httpClient,
    IConfiguration configuration,
    BankingSessionStore sessionStore)
{
    private const string ApiBaseUrl = "https://api.enablebanking.com";
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        PropertyNameCaseInsensitive = true
    };

    private readonly string? _applicationId = configuration["EnableBanking:ApplicationId"];
    private readonly string? _privateKeyPath = configuration["EnableBanking:PrivateKeyPath"];
    private readonly string _redirectUrl = configuration["EnableBanking:RedirectUrl"]
        ?? "http://10.0.2.2:5006/api/banking/callback";

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(_applicationId) &&
        !string.IsNullOrWhiteSpace(_privateKeyPath) &&
        File.Exists(_privateKeyPath);

    public async Task<BankingStatus> GetStatusAsync(CancellationToken cancellationToken)
    {
        if (!IsConfigured)
        {
            return new BankingStatus(false, false, "NOT_CONFIGURED", null, null, 0);
        }

        var application = await GetApplicationAsync(cancellationToken);
        var isSandbox = string.Equals(application.Environment, "SANDBOX", StringComparison.OrdinalIgnoreCase);
        var connected = isSandbox ? sessionStore.ConnectedSession : null;

        return new BankingStatus(
            IsConfigured,
            isSandbox,
            application.Environment,
            connected?.InstitutionName,
            connected?.InstitutionCountry,
            connected?.AccountUids.Count ?? 0);
    }

    public async Task<IReadOnlyList<EnableBankingInstitution>> GetInstitutionsAsync(
        string? country,
        CancellationToken cancellationToken)
    {
        await EnsureSandboxApplicationAsync(cancellationToken);
        var response = await SendAsync<EnableBankingInstitutionsResponse>(
            HttpMethod.Get,
            "/aspsps",
            null,
            cancellationToken);

        return response.Aspsps
            .Where(item => string.IsNullOrWhiteSpace(country) ||
                           string.Equals(item.Country, country, StringComparison.OrdinalIgnoreCase))
            .OrderBy(item => item.Country)
            .ThenBy(item => item.Name)
            .ToArray();
    }

    public async Task<BankingAccountDataResponse> GetAccountDataAsync(CancellationToken cancellationToken)
    {
        await EnsureSandboxApplicationAsync(cancellationToken);

        var connected = sessionStore.ConnectedSession
            ?? throw new InvalidOperationException("Zuerst ein Sandbox-Konto verbinden.");

        var accounts = new List<BankingAccountData>();
        foreach (var accountUid in connected.AccountUids)
        {
            var encodedAccountUid = Uri.EscapeDataString(accountUid);
            var balances = await SendAsync<JsonElement>(
                HttpMethod.Get,
                $"/accounts/{encodedAccountUid}/balances",
                null,
                cancellationToken);
            var transactions = await SendAsync<JsonElement>(
                HttpMethod.Get,
                $"/accounts/{encodedAccountUid}/transactions",
                null,
                cancellationToken);

            accounts.Add(new BankingAccountData(balances, transactions));
        }

        return new BankingAccountDataResponse(
            connected.InstitutionName,
            connected.InstitutionCountry,
            accounts);
    }

    public async Task<AuthorizationStartResult> StartAuthorizationAsync(
        StartBankAuthorizationRequest request,
        CancellationToken cancellationToken)
    {
        await EnsureSandboxApplicationAsync(cancellationToken);

        var institutions = await GetInstitutionsAsync(request.Country, cancellationToken);
        var institution = institutions.FirstOrDefault(item =>
            string.Equals(item.Name, request.InstitutionName, StringComparison.OrdinalIgnoreCase));

        if (institution is null)
        {
            throw new ArgumentException("Diese Testbank ist in der Sandbox nicht verfügbar.");
        }

        var state = Guid.NewGuid().ToString("D");
        var requestedConsentSeconds = 30L * 24 * 60 * 60;
        var consentSeconds = institution.MaximumConsentValidity > 0
            ? Math.Min(requestedConsentSeconds, institution.MaximumConsentValidity)
            : requestedConsentSeconds;

        var body = new StartAuthorizationPayload(
            new AccessRequest(true, true, DateTimeOffset.UtcNow.AddSeconds(consentSeconds)),
            new AspspRequest(institution.Name, institution.Country),
            state,
            _redirectUrl,
            "personal");

        var response = await SendAsync<StartAuthorizationResponse>(
            HttpMethod.Post,
            "/auth",
            body,
            cancellationToken);

        sessionStore.AddPendingAuthorization(new PendingAuthorization(
            state,
            institution.Name,
            institution.Country,
            DateTimeOffset.UtcNow.AddMinutes(10)));

        return new AuthorizationStartResult(response.Url, state, _redirectUrl);
    }

    public async Task<AuthorizationCompletionResult> CompleteAuthorizationAsync(
        string code,
        string state,
        CancellationToken cancellationToken)
    {
        if (!sessionStore.TryConsumePendingAuthorization(state, out var pending) ||
            pending.ExpiresAt < DateTimeOffset.UtcNow)
        {
            throw new ArgumentException("Der Autorisierungsstatus ist ungültig oder abgelaufen. Starte die Verbindung erneut.");
        }

        await EnsureSandboxApplicationAsync(cancellationToken);
        var response = await SendAsync<AuthorizeSessionResponse>(
            HttpMethod.Post,
            "/sessions",
            new AuthorizeSessionPayload(code),
            cancellationToken);

        var accountUids = response.Accounts
            .Where(account => !string.IsNullOrWhiteSpace(account.Uid))
            .Select(account => account.Uid!)
            .Distinct(StringComparer.Ordinal)
            .ToArray();

        sessionStore.SetConnectedSession(new ConnectedBankSession(
            response.SessionId,
            pending.InstitutionName,
            pending.InstitutionCountry,
            accountUids));

        return new AuthorizationCompletionResult(pending.InstitutionName, pending.InstitutionCountry, accountUids.Length);
    }

    public ConnectedBankSession? GetConnectedSession() => sessionStore.ConnectedSession;

    public void CancelAuthorization(string state) => sessionStore.TryConsumePendingAuthorization(state, out _);

    private async Task EnsureSandboxApplicationAsync(CancellationToken cancellationToken)
    {
        EnsureConfigured();
        var application = await GetApplicationAsync(cancellationToken);
        if (!string.Equals(application.Environment, "SANDBOX", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException(
                "Saldo erlaubt für diesen Ablauf nur eine Enable Banking App aus der SANDBOX-Umgebung.");
        }
    }

    private Task<EnableBankingApplication> GetApplicationAsync(CancellationToken cancellationToken)
    {
        EnsureConfigured();
        return SendAsync<EnableBankingApplication>(HttpMethod.Get, "/application", null, cancellationToken);
    }

    private void EnsureConfigured()
    {
        if (!IsConfigured)
        {
            throw new InvalidOperationException(
                "Enable Banking ist noch nicht eingerichtet. Hinterlege ApplicationId und PrivateKeyPath in den lokalen .NET User Secrets.");
        }
    }

    private async Task<TResponse> SendAsync<TResponse>(
        HttpMethod method,
        string path,
        object? body,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(method, $"{ApiBaseUrl}{path}");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", CreateJwt());
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        if (body is not null)
        {
            request.Content = new StringContent(
                JsonSerializer.Serialize(body, JsonOptions),
                Encoding.UTF8,
                "application/json");
        }

        using var response = await httpClient.SendAsync(request, cancellationToken);
        var content = await response.Content.ReadAsStringAsync(cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            throw new EnableBankingApiException((int)response.StatusCode, ReadProviderErrorCode(content));
        }

        return JsonSerializer.Deserialize<TResponse>(content, JsonOptions)
            ?? throw new EnableBankingApiException(502, "EMPTY_PROVIDER_RESPONSE");
    }

    private string CreateJwt()
    {
        EnsureConfigured();

        var now = DateTimeOffset.UtcNow;
        var header = new JwtHeader("JWT", "RS256", _applicationId!);
        var payload = new JwtPayload(
            "enablebanking.com",
            "api.enablebanking.com",
            now.ToUnixTimeSeconds(),
            now.AddMinutes(5).ToUnixTimeSeconds());

        var encodedHeader = Base64UrlEncode(JsonSerializer.SerializeToUtf8Bytes(header));
        var encodedPayload = Base64UrlEncode(JsonSerializer.SerializeToUtf8Bytes(payload));
        var signedContent = Encoding.ASCII.GetBytes($"{encodedHeader}.{encodedPayload}");

        using var rsa = RSA.Create();
        rsa.ImportFromPem(File.ReadAllText(_privateKeyPath!));
        var signature = rsa.SignData(signedContent, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        return $"{encodedHeader}.{encodedPayload}.{Base64UrlEncode(signature)}";
    }

    private static string ReadProviderErrorCode(string content)
    {
        try
        {
            using var document = JsonDocument.Parse(content);
            return document.RootElement.TryGetProperty("code", out var code)
                ? code.GetString() ?? "PROVIDER_ERROR"
                : "PROVIDER_ERROR";
        }
        catch (JsonException)
        {
            return "PROVIDER_ERROR";
        }
    }

    private static string Base64UrlEncode(byte[] value) =>
        Convert.ToBase64String(value).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    private sealed record JwtHeader(string typ, string alg, string kid);
    private sealed record JwtPayload(string iss, string aud, long iat, long exp);
}

public sealed class BankingSessionStore
{
    private readonly ConcurrentDictionary<string, PendingAuthorization> _pending = new(StringComparer.Ordinal);
    private readonly IDataProtector _protector;
    private readonly string _sessionFilePath;
    private ConnectedBankSession? _connectedSession;

    public BankingSessionStore(IDataProtectionProvider dataProtectionProvider)
    {
        _protector = dataProtectionProvider.CreateProtector("FinanceApp.Saldo.EnableBankingSession.v1");

        var storageDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Saldo");
        Directory.CreateDirectory(storageDirectory);
        _sessionFilePath = Path.Combine(storageDirectory, "enable-banking-session.bin");
        _connectedSession = LoadConnectedSession();
    }

    public ConnectedBankSession? ConnectedSession => Volatile.Read(ref _connectedSession);

    public void AddPendingAuthorization(PendingAuthorization authorization)
    {
        foreach (var old in _pending.Where(item => item.Value.ExpiresAt < DateTimeOffset.UtcNow).ToArray())
        {
            _pending.TryRemove(old.Key, out _);
        }

        _pending[authorization.State] = authorization;
    }

    public bool TryConsumePendingAuthorization(string state, out PendingAuthorization authorization) =>
        _pending.TryRemove(state, out authorization!);

    public void SetConnectedSession(ConnectedBankSession session)
    {
        var serialized = JsonSerializer.SerializeToUtf8Bytes(session);
        var protectedData = _protector.Protect(serialized);
        var temporaryPath = $"{_sessionFilePath}.tmp";
        File.WriteAllBytes(temporaryPath, protectedData);
        File.Move(temporaryPath, _sessionFilePath, overwrite: true);
        Volatile.Write(ref _connectedSession, session);
    }

    private ConnectedBankSession? LoadConnectedSession()
    {
        if (!File.Exists(_sessionFilePath))
        {
            return null;
        }

        try
        {
            var protectedData = File.ReadAllBytes(_sessionFilePath);
            var serialized = _protector.Unprotect(protectedData);
            return JsonSerializer.Deserialize<ConnectedBankSession>(serialized);
        }
        catch (Exception exception) when (exception is CryptographicException or JsonException or IOException)
        {
            return null;
        }
    }
}

public sealed record StartBankAuthorizationRequest(string InstitutionName, string Country);
public sealed record BankingStatus(
    bool IsConfigured,
    bool IsSandbox,
    string Environment,
    string? InstitutionName,
    string? InstitutionCountry,
    int AccountCount);
public sealed record BankingAccountDataResponse(
    string InstitutionName,
    string InstitutionCountry,
    IReadOnlyList<BankingAccountData> Accounts);
public sealed record BankingAccountData(JsonElement Balances, JsonElement Transactions);
public sealed record AuthorizationStartResult(string Url, string State, string RedirectUrl);
public sealed record AuthorizationCompletionResult(string InstitutionName, string InstitutionCountry, int AccountCount);
public sealed record PendingAuthorization(
    string State,
    string InstitutionName,
    string InstitutionCountry,
    DateTimeOffset ExpiresAt);
public sealed record ConnectedBankSession(
    string SessionId,
    string InstitutionName,
    string InstitutionCountry,
    IReadOnlyList<string> AccountUids);

public sealed class EnableBankingApiException(int statusCode, string errorCode)
    : Exception($"Enable Banking API antwortet mit HTTP {statusCode} ({errorCode}).")
{
    public int StatusCode { get; } = statusCode;
    public string ErrorCode { get; } = errorCode;
}

public sealed record EnableBankingInstitutionsResponse(
    [property: JsonPropertyName("aspsps")] IReadOnlyList<EnableBankingInstitution> Aspsps);

public sealed record EnableBankingInstitution(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("country")] string Country,
    [property: JsonPropertyName("maximum_consent_validity")] long MaximumConsentValidity,
    [property: JsonPropertyName("sandbox")] JsonElement? Sandbox);

public sealed record EnableBankingApplication(
    [property: JsonPropertyName("environment")] string Environment);

public sealed record StartAuthorizationPayload(
    [property: JsonPropertyName("access")] AccessRequest Access,
    [property: JsonPropertyName("aspsp")] AspspRequest Aspsp,
    [property: JsonPropertyName("state")] string State,
    [property: JsonPropertyName("redirect_url")] string RedirectUrl,
    [property: JsonPropertyName("psu_type")] string PsuType);

public sealed record AccessRequest(
    [property: JsonPropertyName("balances")] bool Balances,
    [property: JsonPropertyName("transactions")] bool Transactions,
    [property: JsonPropertyName("valid_until")] DateTimeOffset ValidUntil);

public sealed record AspspRequest(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("country")] string Country);

public sealed record StartAuthorizationResponse(
    [property: JsonPropertyName("url")] string Url,
    [property: JsonPropertyName("authorization_id")] string AuthorizationId);

public sealed record AuthorizeSessionPayload(
    [property: JsonPropertyName("code")] string Code);

public sealed record AuthorizeSessionResponse(
    [property: JsonPropertyName("session_id")] string SessionId,
    [property: JsonPropertyName("accounts")] IReadOnlyList<AuthorizedAccount> Accounts);

public sealed record AuthorizedAccount(
    [property: JsonPropertyName("uid")] string? Uid);
