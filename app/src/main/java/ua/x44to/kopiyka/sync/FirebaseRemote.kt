package ua.x44to.kopiyka.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Сервер відповів відмовою (неправильні правила бази або адреса). */
class AccessDenied(msg: String) : IOException(msg)

/**
 * Firebase Realtime Database через REST: звичайні HTTP-запити й потік подій (Server-Sent Events).
 * Без бібліотек Google — працює й на телефонах без сервісів Google.
 *
 * [server] — адреса бази («https://kopiyka-xxxx-default-rtdb.europe-west1.firebasedatabase.app»),
 * [ns] — простір імен для локального емулятора бази в тестах.
 */
class FirebaseRemote(server: String, budgetId: String, private val ns: String? = null) : Remote {
    private val root = server.trimEnd('/') + "/b/$budgetId"
    private val base = "$root/r"
    private val json = Json { ignoreUnknownKeys = true }

    private fun url(path: String = "", vararg query: String): URL {
        val q = (listOfNotNull(ns?.let { "ns=$it" }) + query).joinToString("&")
        return URL("$base$path.json" + if (q.isEmpty()) "" else "?$q")
    }

    private fun open(u: URL, method: String = "GET"): HttpURLConnection {
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.useCaches = false
        if (method == "PATCH") {
            // HttpURLConnection не вміє PATCH — Firebase розуміє заміну методу заголовком
            c.requestMethod = "POST"
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else c.requestMethod = method
        return c
    }

    private fun check(c: HttpURLConnection) {
        val code = c.responseCode
        if (code in 200..299) return
        val body = try { c.errorStream?.bufferedReader()?.readText().orEmpty() } catch (_: Exception) { "" }
        if (code == 401 || code == 403) throw AccessDenied("HTTP $code $body")
        throw IOException("HTTP $code $body")
    }

    override fun getAll(): Map<String, RemoteEntry> {
        val c = open(url())
        try {
            check(c)
            val text = c.inputStream.bufferedReader().readText()
            return entries(json.parseToJsonElement(text))
        } finally {
            c.disconnect()
        }
    }

    private fun getOne(key: String): RemoteEntry? {
        val c = open(url("/$key"))
        try {
            check(c)
            return entry(json.parseToJsonElement(c.inputStream.bufferedReader().readText()))
        } finally {
            c.disconnect()
        }
    }

    /** Стерти весь бюджет із сервера (прибирання після тестів). */
    fun wipe() {
        val q = ns?.let { "?ns=$it" } ?: ""
        val c = open(URL("$root.json$q"), "DELETE")
        try {
            check(c)
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    override fun patch(entries: Map<String, RemoteEntry>) {
        if (entries.isEmpty()) return
        val body = buildJsonObject {
            for ((k, e) in entries) put(k, buildJsonObject { put("t", e.t); e.d?.let { put("d", it) } })
        }.toString().toByteArray(Charsets.UTF_8)
        val c = open(url("", "print=silent"), "PATCH")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            check(c)
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    @Volatile
    private var live: HttpURLConnection? = null

    override fun cancel() {
        try { live?.disconnect() } catch (_: Exception) {}
    }

    override fun listen(onChange: (Map<String, RemoteEntry>, Boolean) -> Unit, stop: () -> Boolean) {
        val c = open(url())
        live = c
        c.readTimeout = 75_000 // сервер шле keep-alive кожні ~30 с; тиша довше — з'єднання мертве
        c.setRequestProperty("Accept", "text/event-stream")
        try {
            check(c)
            val r = c.inputStream.bufferedReader(Charsets.UTF_8)
            var event = ""
            val data = StringBuilder()
            while (!stop()) {
                val line = r.readLine() ?: throw IOException("stream closed")
                when {
                    line.startsWith("event:") -> event = line.substring(6).trim()
                    line.startsWith("data:") -> data.append(line.substring(5).trim())
                    line.isEmpty() -> {
                        if (event.isNotEmpty()) handle(event, data.toString(), onChange)
                        event = ""
                        data.setLength(0)
                    }
                }
            }
        } finally {
            live = null
            c.disconnect()
        }
    }

    private fun handle(event: String, data: String, onChange: (Map<String, RemoteEntry>, Boolean) -> Unit) {
        when (event) {
            "keep-alive" -> return
            "cancel", "auth_revoked" -> throw AccessDenied("stream $event: $data")
            "put", "patch" -> {
                val o = json.parseToJsonElement(data).jsonObject
                val path = o["path"]?.jsonPrimitive?.content ?: return
                val value = o["data"] ?: JsonNull
                val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
                when (parts.size) {
                    // увесь бюджет (put) або кілька записів (patch)
                    0 -> onChange(entries(value), event == "put")
                    // один запис цілком (null — вузол видалено з бази)
                    1 -> onChange(mapOf(parts[0] to (entry(value) ?: RemoteEntry(0))).filterValues { it.t > 0 }, false)
                    // частина запису — дочитуємо запис повністю
                    else -> getOne(parts[0])?.let { onChange(mapOf(parts[0] to it), false) }
                }
            }
        }
    }

    private fun entries(e: JsonElement): Map<String, RemoteEntry> {
        if (e !is JsonObject) return emptyMap()
        val out = HashMap<String, RemoteEntry>(e.size)
        for ((k, v) in e) entry(v)?.let { out[k] = it }
        return out
    }

    private fun entry(v: JsonElement): RemoteEntry? {
        if (v !is JsonObject) return null
        val t = (v["t"] as? JsonPrimitive)?.longOrNull ?: return null
        val d = (v["d"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return RemoteEntry(t, d)
    }
}
