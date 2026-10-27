package com.example.finance

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FinanceOverview(
    val dataSource: String,
    val month: String,
    val accountInstitution: String,
    val accountName: String,
    val currency: String,
    val balanceCents: Long,
    val incomeCents: Long,
    val expensesCents: Long,
    val spending: List<SpendingCategory>,
    val transactions: List<FinanceTransaction>,
    val hasMoreTransactions: Boolean = false,
    val fetchedAt: String? = null,
    val isCached: Boolean = false,
    val balanceAvailable: Boolean = true,
    val hasImportedHistory: Boolean = false
)

data class SpendingCategory(
    val name: String,
    val amountCents: Long,
    val fraction: Float
)

data class FinanceTransaction(
    val merchant: String,
    val detail: String,
    val dateLabel: String,
    val amountCents: Long,
    val category: String,
    val bookingDate: String? = null,
    val identity: String = "",
    val originalMerchant: String = merchant,
    val transactionDate: String? = null
)

data class BankingConnectionStatus(
    val environment: String,
    val isConfigured: Boolean,
    val isActive: Boolean,
    val accountCount: Int,
    val institutionName: String?,
    val retryAfter: String?,
    val refresh: BankRefreshStatus? = null
)

data class BankRefreshStatus(
    val lastAttemptAt: String?,
    val lastSuccessAt: String?,
    val lastOutcome: String?,
    val attempts: List<BankRefreshAttempt> = emptyList()
)

data class BankRefreshAttempt(
    val at: String,
    val outcome: String
)

object FinanceApi {
    private const val TIMEOUT_MILLIS = 60_000
    private const val CLOUD_URL = "https://saldo-bank-probe.saldo-cloud-probe.workers.dev"
    private const val PRODUCTION_CLOUD_URL = "https://saldo-bank.saldo-cloud-probe.workers.dev"
    private var cloudStore: CloudConnectionStore? = null

    fun configureCloud(store: CloudConnectionStore) {
        cloudStore = store
    }

    fun isCloudConnected(): Boolean = cloudStore?.credentials() != null

    fun cloudEnvironment(): String? = cloudStore?.credentials()?.environment

    fun disconnectCloud() {
        if (!isCloudConnected()) return
        requestJson("/api/pairing/self-revoke", "POST")
        cloudStore?.clear()
    }

    fun pairCloud(code: String, environment: String) {
        val cloudUrl = if (environment == "PRODUCTION") PRODUCTION_CLOUD_URL else CLOUD_URL
        val connection = URL("$cloudUrl/api/pairing/claim").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(JSONObject().put("code", code.trim()).toString().toByteArray()) }
            if (connection.responseCode !in 200..299) {
                throw IOException("Kopplungscode ungültig oder abgelaufen.")
            }
            val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val token = result.getString("token")
            (cloudStore ?: throw IOException("Cloud-Speicher ist nicht bereit.")).save(token, environment)
        } finally {
            connection.disconnect()
        }
    }

    fun fetchOverview(): FinanceOverview {
        return getJson("/api/overview").toFinanceOverview()
    }

    fun fetchBankingStatus(): BankingConnectionStatus {
        val status = getJson("/api/banking/status")
        val refresh = status.optJSONObject("refresh")?.let {
            val attemptsJson = it.optJSONArray("attempts")
            val attempts = (0 until (attemptsJson?.length() ?: 0)).mapNotNull { index ->
                val attempt = attemptsJson?.optJSONObject(index) ?: return@mapNotNull null
                val at = attempt.optString("at").cleanValue() ?: return@mapNotNull null
                val outcome = attempt.optString("outcome").cleanValue() ?: return@mapNotNull null
                BankRefreshAttempt(at, outcome)
            }
            BankRefreshStatus(
                lastAttemptAt = it.optString("lastAttemptAt").cleanValue(),
                lastSuccessAt = it.optString("lastSuccessAt").cleanValue(),
                lastOutcome = it.optString("lastOutcome").cleanValue(),
                attempts = attempts
            )
        }
        return BankingConnectionStatus(
            environment = status.optString("environment", "SANDBOX"),
            isConfigured = status.optBoolean("isConfigured"),
            isActive = status.optBoolean("isActive"),
            accountCount = status.optInt("accountCount"),
            institutionName = status.optString("institutionName").cleanValue(),
            retryAfter = status.optString("retryAfter").cleanValue(),
            refresh = refresh
        )
    }

    fun fetchDashboardOverview(status: BankingConnectionStatus): FinanceOverview {
        if (status.accountCount > 0) {
            val path = if (status.environment == "SANDBOX")
                "/api/banking/data?allTransactions=true" else "/api/banking/data"
            return getJson(path)
                .toBankingOverview(status.environment == "SANDBOX")
        }
        return fetchOverview()
    }

    fun fetchImportedHistory(): FinanceOverview? {
        val response = getJson("/api/history")
        val items = response.optJSONArray("transactions") ?: return null
        if (items.length() == 0) return null
        val transactions = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val amount = item.getLong("amountCents")
            val merchant = item.getString("merchant")
            FinanceTransaction(
                merchant = if (amount < 0) FinanceCategories.displayMerchant(merchant)
                    else FinanceCategories.displaySender(merchant),
                detail = item.getString("detail"),
                dateLabel = item.getString("bookingDate").toGermanDate(),
                amountCents = amount,
                category = if (amount > 0) "Eingang" else "Ausgang",
                bookingDate = item.getString("bookingDate"),
                identity = item.getString("identity"),
                originalMerchant = merchant
            )
        }
        return FinanceOverview(
            dataSource = "local_statement",
            month = "Importierte Umsätze",
            accountInstitution = "Bank Austria",
            accountName = "Umsatzliste",
            currency = response.optString("currency", "EUR"),
            balanceCents = 0,
            incomeCents = 0,
            expensesCents = 0,
            spending = emptyList(),
            transactions = transactions,
            balanceAvailable = false,
            hasImportedHistory = true
        )
    }

    fun mergeImportedHistory(bank: FinanceOverview, history: FinanceOverview?): FinanceOverview {
        if (history == null || bank.dataSource != "banking_production") return bank
        val bankTransactions = bank.transactions.filterNot { it.identity.startsWith("statement:") }
        val oldestBankDate = bankTransactions.mapNotNull { it.bookingDate }.minOrNull()
        val older = history.transactions.filter { oldestBankDate == null || it.bookingDate.orEmpty() < oldestBankDate }
        return bank.copy(
            transactions = bankTransactions + older,
            hasImportedHistory = older.isNotEmpty()
        )
    }

    fun startBankAustriaAuthorization(): String? {
        val result = requestJson("/api/banking/authorization/bank-austria", "POST")
        return if (cloudEnvironment() == "PRODUCTION") result.getString("url") else null
    }

    private fun getJson(path: String): JSONObject = requestJson(path, "GET")

    private fun requestJson(path: String, method: String): JSONObject {
        val cloudCredentials = cloudStore?.credentials()
        val cloudToken = cloudCredentials?.token
        val baseUrl = when (cloudCredentials?.environment) {
            "PRODUCTION" -> PRODUCTION_CLOUD_URL
            "SANDBOX" -> CLOUD_URL
            else -> BuildConfig.FINANCE_API_BASE_URL
        }
        val connection = URL("$baseUrl$path")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            if (cloudToken != null) connection.setRequestProperty("Authorization", "Bearer $cloudToken")
            if (path.startsWith("/api/banking/data")) {
                connection.setRequestProperty("Psu-User-Agent", "Saldo/1.0 (Android)")
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                if (status == 401 && cloudToken != null) {
                    throw IOException("Cloud-Kopplung ungültig. Bitte erneut koppeln.")
                }
                if (status == 429) {
                    throw IOException("Bank Austria lässt gerade keine weiteren Abrufe zu. Bitte später erneut versuchen.")
                }
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.let { runCatching { JSONObject(it).optString("detail") }.getOrNull() }
                    ?.takeIf { it.isNotBlank() }
                throw IOException(detail ?: "Enable Banking antwortet mit HTTP $status.")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toFinanceOverview(): FinanceOverview {
        val spendingJson = getJSONArray("spending")
        val spending = (0 until spendingJson.length()).map { index ->
            val item = spendingJson.getJSONObject(index)
            SpendingCategory(
                name = item.getString("name"),
                amountCents = item.getLong("amountCents"),
                fraction = item.getDouble("fraction").toFloat()
            )
        }

        val transactionsJson = getJSONArray("transactions")
        val transactions = (0 until transactionsJson.length()).map { index ->
            val item = transactionsJson.getJSONObject(index)
            FinanceTransaction(
                merchant = item.getString("merchant"),
                detail = item.getString("detail"),
                dateLabel = item.getString("dateLabel"),
                amountCents = item.getLong("amountCents"),
                category = item.getString("category"),
                identity = fingerprint("sample", item.getString("merchant"), item.getString("detail"),
                    item.getString("dateLabel"), item.getLong("amountCents").toString())
            )
        }

        return FinanceOverview(
            dataSource = getString("dataSource"),
            month = getString("month"),
            accountInstitution = getString("accountInstitution"),
            accountName = getString("accountName"),
            currency = getString("currency"),
            balanceCents = getLong("balanceCents"),
            incomeCents = getLong("incomeCents"),
            expensesCents = getLong("expensesCents"),
            spending = spending,
            transactions = transactions,
            hasMoreTransactions = false
        )
    }

    private fun JSONObject.toBankingOverview(isSandbox: Boolean): FinanceOverview {
        val accounts = optJSONArray("accounts")
            ?: throw IOException("Enable Banking hat keine Kontodaten zurückgegeben.")
        val account = (0 until accounts.length())
            .mapNotNull { accounts.optJSONObject(it) }
            .firstOrNull()
            ?: throw IOException("Enable Banking hat kein Konto zurückgegeben.")

        val balances = account.optJSONObject("balances")?.optJSONArray("balances")
        val balance = balances?.let { items ->
            (0 until items.length())
                .mapNotNull { items.optJSONObject(it) }
                .firstOrNull { it.optString("balance_type") in setOf("ITAV", "CLAV", "ITBD") }
                ?: (0 until items.length()).mapNotNull { items.optJSONObject(it) }.firstOrNull()
        }

        val transactionsResponse = account.optJSONObject("transactions")
        val transactionItems = transactionsResponse?.optJSONArray("transactions")
        val transactions = transactionItems?.let { items ->
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val amount = item.optJSONObject("transaction_amount")
                    ?.optString("amount")
                    ?.toMinorUnits()
                    ?: 0L
                val direction = item.optString("credit_debit_indicator")
                val signedAmount = if (direction == "CRDT") kotlin.math.abs(amount) else -kotlin.math.abs(amount)
                val remittance = item.optJSONArray("remittance_information")?.let { lines ->
                    (0 until lines.length()).mapNotNull { lines.optString(it).cleanValue() }
                        .joinToString("\n").ifBlank { null }
                }
                val parties = if (direction == "CRDT") listOf("debtor", "creditor") else listOf("creditor", "debtor")
                val merchant = parties
                    .firstNotNullOfOrNull { item.optJSONObject(it)?.optString("name")?.cleanValue() }
                    ?: remittance?.replace(Regex("\\s+"), " ")
                    ?: "Umsatz"
                val displayMerchant = merchant
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("[()]+$"), "")
                    .replace(Regex("\\s+\\d{4,}$"), "")
                    .trim()
                val readableMerchant = if (signedAmount < 0) FinanceCategories.displayMerchant(displayMerchant)
                    else FinanceCategories.displaySender(displayMerchant)

                FinanceTransaction(
                    merchant = readableMerchant.ifBlank { "Umsatz" },
                    detail = remittance ?: item.optJSONObject("bank_transaction_code")?.optString("code").cleanValue().orEmpty(),
                    dateLabel = item.optString("booking_date").toGermanDate(),
                    amountCents = signedAmount,
                    category = if (direction == "CRDT") "Eingang" else "Ausgang",
                    bookingDate = item.optString("booking_date").cleanValue(),
                    originalMerchant = merchant,
                    transactionDate = item.optString("transaction_date").cleanValue(),
                    identity = fingerprint(
                        if (isSandbox) "banking_sandbox" else "banking_production",
                        optString("institutionName"),
                        item.optString("entry_reference"),
                        item.optString("booking_date"),
                        signedAmount.toString(),
                        remittance?.lineSequence()?.firstOrNull().orEmpty(),
                        item.optJSONObject("creditor")?.optString("name").orEmpty(),
                        item.optJSONObject("debtor")?.optString("name").orEmpty(),
                        item.optString("reference_number")
                    )
                )
            }
        }.orEmpty()

        val balanceAmount = balance?.optJSONObject("balance_amount")
        val currency = balanceAmount?.optString("currency")
            ?.cleanValue()
            ?: transactionItems?.optJSONObject(0)?.optJSONObject("transaction_amount")?.optString("currency")?.cleanValue()
            ?: if (isSandbox) "DKK" else "EUR"
        val amountCents = balanceAmount?.optString("amount")?.toMinorUnits() ?: 0L
        val continuationKey = transactionsResponse?.optString("continuation_key")
        return FinanceOverview(
            dataSource = if (isSandbox) "banking_sandbox" else "banking_production",
            month = if (isSandbox) "Sandbox-Testdaten" else "Bankdaten",
            accountInstitution = optString("institutionName").cleanValue().orEmpty()
                .ifBlank { if (isSandbox) "Mock ASPSP" else "Bank Austria" },
            accountName = if (isSandbox) "Sandbox-Konto" else "Mein Konto",
            currency = currency,
            balanceCents = amountCents,
            incomeCents = 0,
            expensesCents = 0,
            spending = emptyList(),
            transactions = transactions,
            hasMoreTransactions = !continuationKey.isNullOrBlank() && continuationKey != "null",
            fetchedAt = optString("fetchedAt").cleanValue(),
            isCached = optBoolean("isCached"),
            balanceAvailable = balanceAmount != null
        )
    }

    private fun String.toMinorUnits(): Long = BigDecimal(this)
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

    private fun String?.cleanValue(): String? = this?.takeUnless { it.isBlank() || it == "null" }

    private fun String.toGermanDate(): String = try {
        LocalDate.parse(this).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY))
    } catch (_: Exception) {
        this
    }

    private fun fingerprint(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
        .digest(parts.joinToString("\u001f").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
