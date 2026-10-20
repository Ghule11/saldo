using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.AspNetCore.DataProtection;

namespace Finance.Api.Services;

public sealed class BankingDataCache
{
    private readonly object _sync = new();
    private readonly IDataProtector _protector;
    private readonly ILogger<BankingDataCache> _logger;
    private readonly string _directory;
    private CachedBankingData? _production;
    private CachedBankingData? _sandbox;
    private DateTimeOffset _rateLimitedUntil;

    public BankingDataCache(IDataProtectionProvider dataProtectionProvider, ILogger<BankingDataCache> logger)
    {
        _protector = dataProtectionProvider.CreateProtector("FinanceApp.Saldo.BankingDataCache.v1");
        _logger = logger;
        _directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Saldo");
        Directory.CreateDirectory(_directory);
        _production = Load("PRODUCTION");
        _sandbox = Load("SANDBOX");
    }

    public BankingAccountDataResponse? Get(string environment, string sessionId, bool requireCompleteHistory)
    {
        lock (_sync)
        {
            var entry = environment == "PRODUCTION" ? _production : _sandbox;
            if (entry is null || entry.SessionId != sessionId ||
                (requireCompleteHistory && !entry.CompleteHistory))
            {
                return null;
            }

            return entry.Data with { IsCached = true };
        }
    }

    public bool IsRateLimited()
    {
        lock (_sync) return DateTimeOffset.UtcNow < _rateLimitedUntil;
    }

    public void MarkRateLimited()
    {
        lock (_sync) _rateLimitedUntil = DateTimeOffset.UtcNow.AddMinutes(5);
    }

    public void Save(string environment, string sessionId, bool completeHistory, BankingAccountDataResponse data)
    {
        lock (_sync)
        {
            var previous = environment == "PRODUCTION" ? _production : _sandbox;
            if (previous?.SessionId == sessionId && previous.CompleteHistory && !completeHistory)
            {
                return;
            }

            var entry = new CachedBankingData(sessionId, completeHistory, data);
            try
            {
                var protectedData = _protector.Protect(JsonSerializer.SerializeToUtf8Bytes(entry));
                var target = FilePath(environment);
                var temporary = $"{target}.tmp";
                File.WriteAllBytes(temporary, protectedData);
                File.Move(temporary, target, overwrite: true);
                if (environment == "PRODUCTION") _production = entry;
                else _sandbox = entry;
            }
            catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or CryptographicException)
            {
                _logger.LogWarning(exception, "Banking data could not be cached locally.");
            }
        }
    }

    private CachedBankingData? Load(string environment)
    {
        try
        {
            var path = FilePath(environment);
            if (!File.Exists(path)) return null;
            return JsonSerializer.Deserialize<CachedBankingData>(_protector.Unprotect(File.ReadAllBytes(path)));
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or CryptographicException or JsonException)
        {
            _logger.LogWarning(exception, "The local banking data cache could not be read.");
            return null;
        }
    }

    private string FilePath(string environment) => Path.Combine(
        _directory,
        environment == "PRODUCTION" ? "banking-production-data.bin" : "banking-sandbox-data.bin");

    private sealed record CachedBankingData(string SessionId, bool CompleteHistory, BankingAccountDataResponse Data);
}
