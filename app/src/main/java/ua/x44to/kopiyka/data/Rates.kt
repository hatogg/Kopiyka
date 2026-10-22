package ua.x44to.kopiyka.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Онлайн-курси валют до гривні.
 * Monobank — середнє між купівлею й продажем (близько до курсу обмінників і карток);
 * НБУ — офіційний курс (сайт НБУ буває недоступний з-за кордону — тоді беремо інше джерело).
 */
object Rates {
    enum class Source { MONO, NBU }

    /** Числові коди ISO 4217 валют, які знає Копійка. */
    val ISO = mapOf(
        "UAH" to 980, "USD" to 840, "EUR" to 978, "PLN" to 985, "GBP" to 826,
        "CZK" to 203, "CHF" to 756, "CAD" to 124, "TRY" to 949, "MDL" to 498,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun url(s: Source) = when (s) {
        Source.MONO -> "https://api.monobank.ua/bank/currency"
        Source.NBU -> "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json"
    }

    /** Курси «гривень за одиницю» для валют Копійки. Без мережі чи при збої кидає IOException. */
    fun fetch(s: Source): Map<String, Double> {
        val text = get(url(s))
        val m = when (s) {
            Source.MONO -> parseMono(text)
            Source.NBU -> parseNbu(text)
        }
        if (m.isEmpty()) throw IOException("no rates in the answer")
        return m
    }

    /** Спершу вибране джерело, потім інше. Повертає курси й джерело, з якого вони насправді. */
    fun fetchAny(preferred: Source): Pair<Map<String, Double>, Source> {
        var last: IOException? = null
        for (s in listOf(preferred) + Source.entries.filter { it != preferred }) {
            try {
                return fetch(s) to s
            } catch (e: IOException) {
                last = e
            } catch (e: RuntimeException) {
                last = IOException(e)
            }
        }
        throw last ?: IOException("no source")
    }

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Kopiyka (Android)")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            c.disconnect()
        }
    }

    /** [{"currencyCodeA":840,"currencyCodeB":980,"rateBuy":44.83,"rateSell":45.23}, {"currencyCodeA":826,…,"rateCross":60.6}] */
    fun parseMono(text: String): Map<String, Double> {
        val arr = json.parseToJsonElement(text) as? JsonArray ?: return emptyMap()
        val byIso = ISO.entries.associate { it.value to it.key }
        val out = HashMap<String, Double>()
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            if (o.int("currencyCodeB") != 980) continue
            val code = byIso[o.int("currencyCodeA")] ?: continue
            val buy = o.num("rateBuy")
            val sell = o.num("rateSell")
            val v = when {
                buy != null && sell != null && buy > 0 && sell > 0 -> (buy + sell) / 2
                else -> o.num("rateCross") ?: sell ?: buy
            } ?: continue
            if (v > 0) out[code] = v
        }
        if (out.isNotEmpty()) out["UAH"] = 1.0
        return out
    }

    /** [{"r030":840,"txt":"Долар США","rate":41.23,"cc":"USD","exchangedate":"22.10.2026"}] */
    fun parseNbu(text: String): Map<String, Double> {
        val arr = json.parseToJsonElement(text) as? JsonArray ?: return emptyMap()
        val out = HashMap<String, Double>()
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val cc = (o["cc"] as? JsonPrimitive)?.content ?: continue
            if (cc !in ISO) continue
            val v = o.num("rate") ?: continue
            if (v > 0) out[cc] = v
        }
        if (out.isNotEmpty()) out["UAH"] = 1.0
        return out
    }

    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
}
