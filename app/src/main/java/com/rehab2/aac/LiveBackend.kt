package com.rehab2.aac

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-scoped credential, never the provider's API key; excluded from backup. */
object LiveBackend {
    private const val ALIAS = "novarehab_live_device"
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    @Volatile private var current: Call? = null
    private fun file(c: Context) = java.io.File(c.noBackupFilesDir, "live-backend.json")
    private fun key(): SecretKey {
        check(Build.VERSION.SDK_INT >= 23) { "Zajem zahteva Android 6 ali novejši." }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    fun url(c: Context): String = runCatching { JSONObject(file(c).readText()).getString("url") }.getOrDefault("")
    fun save(c: Context, url: String, token: String) {
        val uri = java.net.URI(url.trim().trimEnd('/'))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) { "Vnesite korenski HTTPS-naslov strežnika." }
        require(token.length in 32..256 && !token.startsWith("sk-")) { "Vnesite omejeni ključ naprave, ne ključa ponudnika API." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        file(c).writeText(JSONObject().put("url", uri.toString().trimEnd('/')).put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString())
    }
    fun deviceToken(c: Context): String {
        val json = JSONObject(file(c).readText())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(json.getString("iv"), Base64.NO_WRAP))) }
        return String(cipher.doFinal(Base64.decode(json.getString("token"), Base64.NO_WRAP)), Charsets.UTF_8)
    }
    fun cancel() { current?.cancel() }
    fun request(c: Context, path: String, body: JSONObject? = JSONObject(), token: String = deviceToken(c), track: Boolean = true): JSONObject {
        val base = url(c); check(base.startsWith("https://")) { "Backend ni nastavljen." }
        val request = Request.Builder().url(base + path).header("Authorization", "Bearer $token")
        if (body == null) request.delete() else request.post(body.toString().toRequestBody("application/json".toMediaType()))
        val call = client.newCall(request.build()); if (track) current = call
        try { return call.execute().use { response ->
            check(response.isSuccessful) { "Povezava: HTTP ${response.code}. Preizkus nadaljujte lokalno ali ponovno povežite." }
            JSONObject(response.body?.string() ?: "{}")
        } } finally { if (current === call) current = null }
    }
}
