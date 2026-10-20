namespace Finance.Api.Models;

public sealed record FinanceOverview(
    string DataSource,
    string Month,
    string AccountInstitution,
    string AccountName,
    string Currency,
    long BalanceCents,
    long IncomeCents,
    long ExpensesCents,
    IReadOnlyList<SpendingCategory> Spending,
    IReadOnlyList<FinanceTransaction> Transactions);

public sealed record SpendingCategory(string Name, long AmountCents, double Fraction);

public sealed record FinanceTransaction(
    string Merchant,
    string Detail,
    string DateLabel,
    long AmountCents,
    string Category);
