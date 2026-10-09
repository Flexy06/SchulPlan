package de.flexy.stundenplan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Speichert die WebUntis-Zugangsdaten nur auf diesem Gerät. Das Passwort wird mit einem
 * Schlüssel aus dem Android-Keystore verschlüsselt (verlässt das Gerät nie, auch nicht im Backup).
 */
class CredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun load(): UntisAccount? {
        val user = prefs.getString("user", null) ?: return null
        val enc = prefs.getString("password", null) ?: return null
        val password = runCatching { decrypt(enc) }.getOrNull() ?: return null
        return UntisAccount(
            server = prefs.getString("server", UntisAccount.DEFAULT_SERVER) ?: UntisAccount.DEFAULT_SERVER,
            school = prefs.getString("school", UntisAccount.DEFAULT_SCHOOL) ?: UntisAccount.DEFAULT_SCHOOL,
            user = user,
            password = password,
        )
    }

    fun save(a: UntisAccount) {
        prefs.edit()
            .putString("server", a.server)
            .putString("school", a.school)
            .putString("user", a.user)
            .putString("password", encrypt(a.password))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    /** Anzeigename der Klasse (z.B. "10a"), wird beim Abgleich gemerkt. */
    var klasse: String?
        get() = prefs.getString("klasse", null)
        set(v) = prefs.edit().putString("klasse", v).apply()

    val user: String? get() = prefs.getString("user", null)
    val school: String get() = prefs.getString("school", UntisAccount.DEFAULT_SCHOOL) ?: UntisAccount.DEFAULT_SCHOOL

    /* ------------------------------------------------------------------------------------------ */

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val iv = Base64.decode(stored.substringBefore(':'), Base64.NO_WRAP)
        val data = Base64.decode(stored.substringAfter(':'), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(data), Charsets.UTF_8)
    }

    private companion object {
        const val ALIAS = "schulplan_untis"
    }
}
