using Finance.Api.Models;

namespace Finance.Api.Data;

public static class SampleFinanceData
{
    public static FinanceOverview Overview { get; } = new(
        DataSource: "sample",
        Month: "Oktober 2026",
        AccountInstitution: "Bank Austria",
        AccountName: "Beispielkonto",
        Currency: "EUR",
        BalanceCents: 428617,
        IncomeCents: 240000,
        ExpensesCents: 124673,
        Spending:
        [
            new("Wohnen", 92000, 1.0),
            new("Transport", 16800, 0.183),
            new("Essen & Freizeit", 11963, 0.13),
            new("Shopping", 3910, 0.043)
        ],
        Transactions:
        [
            new("BILLA", "Lebensmittel · Karte", "Heute · 12:42", -4287, "Lebensmittel"),
            new("Wiener Linien", "Jahreskarte · Karte", "Heute · 08:10", -5100, "Transport"),
            new("Gehalt", "Eingang · Überweisung", "Gestern", 240000, "Einkommen"),
            new("Spotify", "Monatsabo · Lastschrift", "17. Okt.", -1099, "Freizeit"),
            new("Miete", "Wohnung · Dauerauftrag", "15. Okt.", -92000, "Wohnen"),
            new("dm drogerie", "Drogerie · Karte", "14. Okt.", -2376, "Shopping"),
            new("Hofer", "Lebensmittel · Karte", "13. Okt.", -3154, "Lebensmittel"),
            new("Café Kaffee", "Essen & Trinken · Karte", "12. Okt.", -895, "Freizeit"),
            new("ÖBB", "Zugticket · Karte", "10. Okt.", -11700, "Transport"),
            new("Buchhandlung", "Bücher · Karte", "9. Okt.", -1534, "Shopping"),
            new("Restaurant", "Essen gehen · Karte", "8. Okt.", -2528, "Freizeit")
        ]);
}
