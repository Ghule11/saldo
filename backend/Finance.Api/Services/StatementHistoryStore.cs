using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.VisualBasic.FileIO;

namespace Finance.Api.Services;

public sealed class StatementHistoryStore
{
    private static readonly CultureInfo AustrianCulture = CultureInfo.GetCultureInfo("de-AT");
    private readonly object _sync = new();
    private readonly IDataProtector _protector;
    private readonly ILogger<StatementHistoryStore> _logger;
    private readonly string _filePath;
    private ImportedHistory? _history;

    public StatementHistoryStore(IDataProtectionProvider protectionProvider, ILogger<StatementHistoryStore> logger)
    {
        _protector = protectionProvider.CreateProtector("FinanceApp.Saldo.StatementHistory.v1");
        _logger = logger;
        var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Saldo");
        Directory.CreateDirectory(directory);
        _filePath = Path.Combine(directory, "bank-austria-history.bin");
        _history = Load();
    }

    public ImportedHistory? Get()
    {
        lock (_sync) return _history;
    }

    public ImportedHistory ImportCsv(string path)
    {
        if (!File.Exists(path)) throw new FileNotFoundException("Die CSV-Datei wurde nicht gefunden.", path);

        using var parser = new TextFieldParser(path, Encoding.UTF8, detectEncoding: true);
        parser.TextFieldType = FieldType.Delimited;
        parser.SetDelimiters(";");
        parser.HasFieldsEnclosedInQuotes = true;

        var header = parser.ReadFields() ?? throw new FormatException("Die CSV-Datei ist leer.");
        var columns = header.Select((name, index) => (Name: name.Trim(), Index: index))
            .Where(item => !string.IsNullOrWhiteSpace(item.Name))
            .ToDictionary(item => item.Name, item => item.Index, StringComparer.OrdinalIgnoreCase);
        foreach (var name in new[] { "Buchungsdatum", "Betrag", "Buchungstext", "Währung" })
        {
            if (!columns.ContainsKey(name)) throw new FormatException($"Die CSV-Spalte '{name}' fehlt.");
        }

        var transactions = new List<ImportedTransaction>();
        var duplicates = new Dictionary<string, int>();
        while (!parser.EndOfData)
        {
            var fields = parser.ReadFields();
            if (fields is null) continue;
            string Field(string name) => columns.TryGetValue(name, out var index) && index < fields.Length
                ? fields[index].Trim() : "";

            var date = DateOnly.ParseExact(Field("Buchungsdatum"), "dd.MM.yyyy", AustrianCulture);
            if (!string.Equals(Field("Währung"), "EUR", StringComparison.OrdinalIgnoreCase))
            {
                throw new FormatException("Die Umsatzliste enthält eine andere Währung als EUR.");
            }

            var amount = decimal.Parse(Field("Betrag"), NumberStyles.Number, AustrianCulture);
            var cents = amount * 100m;
            if (cents != decimal.Truncate(cents)) throw new FormatException("Ein Betrag hat mehr als zwei Nachkommastellen.");
            var amountCents = checked((long)cents);
            var detail = Regex.Replace(Field("Buchungstext"), @"\s+", " ").Trim();
            var purpose = Regex.Replace(Field("Verwendungszweck"), @"\s+", " ").Trim();
            if (!string.IsNullOrWhiteSpace(purpose) && !detail.Contains(purpose, StringComparison.OrdinalIgnoreCase))
            {
                detail = $"{detail} · {purpose}";
            }
            var namedParty = amountCents >= 0 ? Field("Auftraggebername") : Field("Empfängername");
            var merchant = string.IsNullOrWhiteSpace(namedParty) ? detail : namedParty;
            if (string.IsNullOrWhiteSpace(merchant)) merchant = "Umsatz";

            var baseKey = $"{date:yyyy-MM-dd}\u001f{amountCents}\u001f{detail}\u001f{namedParty}";
            duplicates.TryGetValue(baseKey, out var duplicateIndex);
            duplicates[baseKey] = duplicateIndex + 1;
            var fingerprint = Convert.ToHexString(SHA256.HashData(
                Encoding.UTF8.GetBytes($"{baseKey}\u001f{duplicateIndex}"))).ToLowerInvariant();
            transactions.Add(new ImportedTransaction(
                date.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
                amountCents,
                merchant,
                detail,
                $"statement:{fingerprint}"));
        }

        if (transactions.Count == 0) throw new FormatException("Die CSV-Datei enthält keine Umsätze.");
        var dates = transactions.Select(item => item.BookingDate).OrderBy(item => item, StringComparer.Ordinal).ToArray();
        var history = new ImportedHistory(
            "EUR",
            DateTimeOffset.UtcNow,
            dates[0],
            dates[^1],
            transactions);
        var protectedData = _protector.Protect(JsonSerializer.SerializeToUtf8Bytes(history));
        var temporary = $"{_filePath}.tmp";
        File.WriteAllBytes(temporary, protectedData);
        File.Move(temporary, _filePath, overwrite: true);
        lock (_sync) _history = history;
        return history;
    }

    private ImportedHistory? Load()
    {
        try
        {
            if (!File.Exists(_filePath)) return null;
            return JsonSerializer.Deserialize<ImportedHistory>(_protector.Unprotect(File.ReadAllBytes(_filePath)));
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or CryptographicException or JsonException)
        {
            _logger.LogWarning(exception, "The local statement history could not be read.");
            return null;
        }
    }
}

public sealed record ImportedHistory(
    string Currency,
    DateTimeOffset ImportedAt,
    string FirstBookingDate,
    string LastBookingDate,
    IReadOnlyList<ImportedTransaction> Transactions);

public sealed record ImportedTransaction(
    string BookingDate,
    long AmountCents,
    string Merchant,
    string Detail,
    string Identity);
