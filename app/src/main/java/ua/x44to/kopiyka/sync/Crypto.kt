package ua.x44to.kopiyka.sync

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Код спільного бюджету: 12 символів без схожих на вигляд (0/O, 1/I/L), напр. «7KQ2-M9PA-X4TB».
 * З нього виводяться і адреса бюджету на сервері, і ключ шифрування — сервер бачить лише шифр.
 */
object BudgetCode {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val LENGTH = 12

    fun generate(rnd: SecureRandom = SecureRandom()): String = buildString { repeat(LENGTH) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }

    /** «7kq2 m9pa-x4tb» → «7KQ2M9PAX4TB»; null, якщо це не схоже на код. */
    fun normalize(input: String): String? {
        val s = input.uppercase().filter { it.isLetterOrDigit() }
        return s.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    fun pretty(code: String) = code.chunked(4).joinToString("-")

    /** Адреса бюджету: 32 шістнадцяткові символи (правила бази пропускають лише такі). */
    fun budgetId(code: String): String =
        MessageDigest.getInstance("SHA-256").digest("kopiyka/budget/v1/$code".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}

/** AES-256-GCM: кожен запис шифрується окремо з випадковим вектором; ключ — PBKDF2 від коду. */
class Seal(code: String) {
    private val key: SecretKeySpec = run {
        val spec = PBEKeySpec(code.toCharArray(), "kopiyka/key/v1".toByteArray(), 60_000, 256)
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
    private val rnd = SecureRandom()

    fun seal(plain: String): String {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    /** null — якщо шифр пошкоджений або зашифрований іншим кодом. */
    fun open(sealed: String): String? = try {
        val all = Base64.getDecoder().decode(sealed)
        if (all.size < 12 + 16) null
        else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all, 0, 12))
            String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}
