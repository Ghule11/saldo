package com.example.finance

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CloudConnectionStore(context: Context) {
    private val preferences = context.getSharedPreferences("saldo_cloud_connection", Context.MODE_PRIVATE)
    private val alias = "saldo_cloud_device_token"

    data class Credentials(val token: String, val environment: String)

    fun credentials(): Credentials? = runCatching {
        val stored = preferences.getString("token", null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        if (bytes.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val plaintext = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        if (plaintext.startsWith("{")) {
            val json = JSONObject(plaintext)
            Credentials(json.getString("token"), json.getString("environment"))
        } else Credentials(plaintext, "SANDBOX")
    }.getOrNull()

    fun save(token: String, environment: String) {
        require(Regex("^[A-Za-z0-9_-]{43}$").matches(token))
        require(environment in setOf("SANDBOX", "PRODUCTION"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plaintext = JSONObject().put("token", token).put("environment", environment).toString()
        val stored = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("token", Base64.encodeToString(stored, Base64.NO_WRAP)).commit())
    }

    fun clear() {
        preferences.edit().remove("token").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }
}
