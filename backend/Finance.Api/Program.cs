using Finance.Api.Data;
using Finance.Api.Services;
using Microsoft.AspNetCore.DataProtection;
using System.Security.Cryptography;
using System.Text.Encodings.Web;

var builder = WebApplication.CreateBuilder(args);

var dataProtectionKeysPath = Path.Combine(
    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
    "Saldo",
    "DataProtectionKeys");
Directory.CreateDirectory(dataProtectionKeysPath);
var dataProtection = builder.Services.AddDataProtection()
    .SetApplicationName("FinanceApp.Saldo")
    .PersistKeysToFileSystem(new DirectoryInfo(dataProtectionKeysPath));
if (OperatingSystem.IsWindows())
{
    dataProtection.ProtectKeysWithDpapi();
}

// Add services to the container.
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();
builder.Services.AddSingleton<BankingSessionStore>();
builder.Services.AddHttpClient<EnableBankingService>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseHttpsRedirection();

app.MapGet("/api/health", () => Results.Ok(new
{
    status = "ok",
    service = "Finance.Api"
}))
.WithName("GetHealth")
.WithSummary("Checks that the FinanceApp backend is responding.");

app.MapGet("/api/overview", () => Results.Ok(SampleFinanceData.Overview))
    .WithName("GetOverview")
    .WithSummary("Returns the current finance overview from the configured sample data source.");

app.MapGet("/api/banking/status", async (EnableBankingService banking, CancellationToken cancellationToken) =>
{
    try
    {
        return Results.Ok(await banking.GetStatusAsync(cancellationToken));
    }
    catch (EnableBankingApiException exception)
    {
        return Results.Problem(exception.Message, statusCode: exception.StatusCode, title: "Enable Banking could not be reached");
    }
    catch (CryptographicException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel ist ungültig.", statusCode: 500);
    }
    catch (IOException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel konnte nicht gelesen werden.", statusCode: 500);
    }
})
.WithName("GetBankingStatus")
.WithSummary("Reports the selected Enable Banking environment and connection status.");

app.MapGet("/api/banking/data", async (bool? allTransactions, EnableBankingService banking, CancellationToken cancellationToken) =>
{
    try
    {
        return Results.Ok(await banking.GetAccountDataAsync(cancellationToken, allTransactions == true));
    }
    catch (EnableBankingApiException exception)
    {
        return Results.Problem(exception.Message, statusCode: exception.StatusCode, title: "Enable Banking could not be reached");
    }
    catch (InvalidOperationException exception)
    {
        return Results.Problem(exception.Message, statusCode: 409, title: "No bank account is connected");
    }
    catch (CryptographicException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel ist ungültig.", statusCode: 500);
    }
    catch (IOException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel konnte nicht gelesen werden.", statusCode: 500);
    }
})
.WithName("GetBankingData")
.WithSummary("Fetches balances and transactions for the connected account in the selected environment.");

app.MapGet("/api/banking/institutions", async (
    string? country,
    EnableBankingService banking,
    CancellationToken cancellationToken) =>
{
    try
    {
        return Results.Ok(await banking.GetInstitutionsAsync(country, cancellationToken));
    }
    catch (EnableBankingApiException exception)
    {
        return Results.Problem(exception.Message, statusCode: exception.StatusCode, title: "Enable Banking could not be reached");
    }
    catch (InvalidOperationException exception)
    {
        return Results.Problem(exception.Message, statusCode: 503, title: "Enable Banking is not ready");
    }
    catch (CryptographicException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel ist ungültig.", statusCode: 500);
    }
    catch (IOException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel konnte nicht gelesen werden.", statusCode: 500);
    }
})
.WithName("GetBankingInstitutions")
.WithSummary("Lists institutions available to the selected Enable Banking application.");

app.MapPost("/api/banking/authorization", async (
    StartBankAuthorizationRequest request,
    EnableBankingService banking,
    CancellationToken cancellationToken) =>
{
    if (string.IsNullOrWhiteSpace(request.InstitutionName) || string.IsNullOrWhiteSpace(request.Country))
    {
        return Results.BadRequest(new { error = "InstitutionName und Country sind erforderlich." });
    }

    try
    {
        return Results.Ok(await banking.StartAuthorizationAsync(request, cancellationToken));
    }
    catch (EnableBankingApiException exception)
    {
        return Results.Problem(exception.Message, statusCode: exception.StatusCode, title: "Enable Banking authorization could not be started");
    }
    catch (ArgumentException exception)
    {
        return Results.BadRequest(new { error = exception.Message });
    }
    catch (InvalidOperationException exception)
    {
        return Results.Problem(exception.Message, statusCode: 503, title: "Enable Banking is not ready");
    }
    catch (CryptographicException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel ist ungültig.", statusCode: 500);
    }
    catch (IOException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel konnte nicht gelesen werden.", statusCode: 500);
    }
})
.WithName("StartBankingAuthorization")
.WithSummary("Starts authorization for a selected institution in the configured environment.");

app.MapPost("/api/banking/authorization/bank-austria", async (
    EnableBankingService banking,
    CancellationToken cancellationToken) =>
{
    try
    {
        return Results.Ok(await banking.StartBankAustriaAuthorizationAsync(cancellationToken));
    }
    catch (EnableBankingApiException exception)
    {
        return Results.Problem(exception.Message, statusCode: exception.StatusCode);
    }
    catch (InvalidOperationException exception)
    {
        return Results.Problem(exception.Message, statusCode: 503);
    }
    catch (CryptographicException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel ist ungültig.", statusCode: 500);
    }
    catch (IOException)
    {
        return Results.Problem("Der konfigurierte private Schlüssel konnte nicht gelesen werden.", statusCode: 500);
    }
})
.WithName("StartBankAustriaAuthorization")
.WithSummary("Starts personal Bank Austria account authorization through an active restricted production application.");

app.MapGet("/api/banking/callback", async (
    string? code,
    string? state,
    string? error,
    string? error_description,
    EnableBankingService banking,
    CancellationToken cancellationToken) =>
{
    if (!string.IsNullOrWhiteSpace(error))
    {
        if (!string.IsNullOrWhiteSpace(state))
        {
            banking.CancelAuthorization(state);
        }

        return CallbackPage(
            "Verbindung abgebrochen",
            string.IsNullOrWhiteSpace(error_description) ? "Die Konto-Freigabe wurde abgebrochen." : error_description,
            StatusCodes.Status400BadRequest);
    }

    if (string.IsNullOrWhiteSpace(code) || string.IsNullOrWhiteSpace(state))
    {
        return CallbackPage("Verbindung nicht abgeschlossen", "Der Rücksprung enthält keinen gültigen Autorisierungscode.", StatusCodes.Status400BadRequest);
    }

    try
    {
        var result = await banking.CompleteAuthorizationAsync(code, state, cancellationToken);
        return CallbackPage(
            "Konto verbunden",
            $"{result.InstitutionName} wurde autorisiert. Zurück zu Saldo wechseln und Daten aktualisieren. Gefundene Konten: {result.AccountCount}.",
            StatusCodes.Status200OK);
    }
    catch (EnableBankingApiException exception)
    {
        return CallbackPage("Konto-Verbindung fehlgeschlagen", exception.Message, exception.StatusCode);
    }
    catch (ArgumentException exception)
    {
        return CallbackPage("Konto-Verbindung fehlgeschlagen", exception.Message, StatusCodes.Status400BadRequest);
    }
    catch (InvalidOperationException exception)
    {
        return CallbackPage("Konto nicht verfügbar", exception.Message, StatusCodes.Status503ServiceUnavailable);
    }
    catch (CryptographicException)
    {
        return CallbackPage("Konto nicht verfügbar", "Der konfigurierte private Schlüssel ist ungültig.", StatusCodes.Status500InternalServerError);
    }
    catch (IOException)
    {
        return CallbackPage("Konto nicht verfügbar", "Der konfigurierte private Schlüssel konnte nicht gelesen werden.", StatusCodes.Status500InternalServerError);
    }
})
.WithName("CompleteBankingAuthorization")
.WithSummary("Receives the authorization callback and exchanges its code for a session.");

app.Run();

static IResult CallbackPage(string title, string message, int statusCode)
{
    var safeTitle = HtmlEncoder.Default.Encode(title);
    var safeMessage = HtmlEncoder.Default.Encode(message);
    var html = $"""<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{safeTitle}</title><body style="font-family:system-ui,sans-serif;max-width:36rem;margin:3rem auto;padding:0 1rem;color:#18231f"><h1>{safeTitle}</h1><p>{safeMessage}</p><p>Du kannst dieses Fenster schließen und zu Saldo zurückkehren.</p></body></html>""";
    return Results.Content(html, "text/html; charset=utf-8", statusCode: statusCode);
}
