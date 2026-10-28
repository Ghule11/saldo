package com.example.finance

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class OfflineSnapshotStore(context: Context) {
    private val file = AtomicFile(context.filesDir.resolve("saldo_offline_snapshot.bin"))
    private val alias = "saldo_offline_snapshot_v1"

    data class Snapshot(val overview: FinanceOverview, val status: BankingConnectionStatus)

    fun load(environment: String?): Snapshot? = runCatching {
        if (environment == null || !file.baseFile.exists() || file.baseFile.length() > 10_000_000) return null
        val bytes = file.openRead().use { it.readBytes() }
        if (bytes.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val compressed = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        val json = JSONObject(GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() })
        if (json.optInt("version") != 1 || json.optString("environment") != environment) return null
        Snapshot(json.getJSONObject("overview").toOverview(), json.getJSONObject("status").toStatus())
    }.getOrNull()

    fun save(environment: String, overview: FinanceOverview, status: BankingConnectionStatus) {
        require(environment == status.environment)
        val json = JSONObject()
            .put("version", 1)
            .put("environment", environment)
            .put("overview", overview.toJson())
            .put("status", status.toJson())
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).bufferedWriter().use { it.write(json.toString()) }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(compressed)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { file.failWrite(it) }
            throw error
        }
    }

    fun clear() = file.delete()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }
}

private fun FinanceOverview.toJson() = JSONObject()
    .put("dataSource", dataSource).put("month", month)
    .put("accountInstitution", accountInstitution).put("accountName", accountName)
    .put("currency", currency).put("balanceCents", balanceCents)
    .put("incomeCents", incomeCents).put("expensesCents", expensesCents)
    .put("hasMoreTransactions", hasMoreTransactions).put("fetchedAt", fetchedAt)
    .put("balanceAvailable", balanceAvailable).put("hasImportedHistory", hasImportedHistory)
    .put("spending", JSONArray().also { array -> spending.forEach {
        array.put(JSONObject().put("name", it.name).put("amountCents", it.amountCents)
            .put("fraction", it.fraction.toDouble()))
    } })
    .put("transactions", JSONArray().also { array -> transactions.forEach {
        array.put(JSONObject().put("merchant", it.merchant).put("detail", it.detail)
            .put("dateLabel", it.dateLabel).put("amountCents", it.amountCents)
            .put("category", it.category).put("bookingDate", it.bookingDate)
            .put("identity", it.identity).put("originalMerchant", it.originalMerchant)
            .put("transactionDate", it.transactionDate))
    } })

private fun JSONObject.toOverview(): FinanceOverview {
    val spendingArray = getJSONArray("spending")
    val transactionArray = getJSONArray("transactions")
    return FinanceOverview(
        dataSource = getString("dataSource"), month = getString("month"),
        accountInstitution = getString("accountInstitution"), accountName = getString("accountName"),
        currency = getString("currency"), balanceCents = getLong("balanceCents"),
        incomeCents = getLong("incomeCents"), expensesCents = getLong("expensesCents"),
        spending = (0 until spendingArray.length()).map { spendingArray.getJSONObject(it).run {
            SpendingCategory(getString("name"), getLong("amountCents"), getDouble("fraction").toFloat())
        } },
        transactions = (0 until transactionArray.length()).map { transactionArray.getJSONObject(it).run {
            FinanceTransaction(getString("merchant"), getString("detail"), getString("dateLabel"),
                getLong("amountCents"), getString("category"), nullable("bookingDate"),
                getString("identity"), getString("originalMerchant"), nullable("transactionDate"))
        } },
        hasMoreTransactions = optBoolean("hasMoreTransactions"), fetchedAt = nullable("fetchedAt"),
        balanceAvailable = optBoolean("balanceAvailable", true),
        hasImportedHistory = optBoolean("hasImportedHistory")
    )
}

private fun BankingConnectionStatus.toJson() = JSONObject()
    .put("environment", environment).put("isConfigured", isConfigured)
    .put("isActive", isActive).put("accountCount", accountCount)
    .put("institutionName", institutionName).put("retryAfter", retryAfter)
    .put("refresh", refresh?.let { result ->
        JSONObject().put("lastAttemptAt", result.lastAttemptAt)
            .put("lastSuccessAt", result.lastSuccessAt).put("lastOutcome", result.lastOutcome)
            .put("attempts", JSONArray().also { array -> result.attempts.forEach { attempt ->
                array.put(JSONObject().put("at", attempt.at).put("outcome", attempt.outcome))
            } })
    })

private fun JSONObject.toStatus(): BankingConnectionStatus {
    val refreshJson = optJSONObject("refresh")
    val attemptsJson = refreshJson?.optJSONArray("attempts")
    return BankingConnectionStatus(
        environment = getString("environment"), isConfigured = optBoolean("isConfigured"),
        isActive = optBoolean("isActive"), accountCount = optInt("accountCount"),
        institutionName = nullable("institutionName"), retryAfter = nullable("retryAfter"),
        refresh = refreshJson?.let {
            BankRefreshStatus(it.nullable("lastAttemptAt"), it.nullable("lastSuccessAt"),
                it.nullable("lastOutcome"), (0 until (attemptsJson?.length() ?: 0)).map { index ->
                    attemptsJson!!.getJSONObject(index).run {
                        BankRefreshAttempt(getString("at"), getString("outcome"))
                    }
                })
        }
    )
}

private fun JSONObject.nullable(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
