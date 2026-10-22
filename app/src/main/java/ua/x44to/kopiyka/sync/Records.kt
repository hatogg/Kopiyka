package ua.x44to.kopiyka.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ua.x44to.kopiyka.data.Account
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Budget
import ua.x44to.kopiyka.data.Category
import ua.x44to.kopiyka.data.Goal
import ua.x44to.kopiyka.data.Recurring
import ua.x44to.kopiyka.data.Tx

/** Налаштування, спільні для всіх учасників бюджету. Тема, сповіщення тощо — у кожного свої. */
@Serializable
data class SharedSettings(
    val base: String,
    val rates: Map<String, Double>,
    val ratesDate: String? = null,
    val monthStart: Int = 1,
)

/**
 * Дані бюджету як набір окремих записів «ключ → JSON»: кожна операція, рахунок, категорія…
 * Синхронізуємо записи, а не весь файл: двоє людей можуть одночасно додавати операції,
 * і нічого не загубиться (конфлікт можливий лише при правці того самого запису).
 * Ключ — «t_<id>» для операцій, «a_» рахунки, «c_» категорії, «b_» бюджети, «r_» регулярні, «g_» цілі,
 * «s_main» — спільні налаштування.
 */
object Records {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    const val SETTINGS = "s_main"

    private class Kind<T : Any>(val prefix: String, val ser: KSerializer<T>, val id: (T) -> String, val list: (AppData) -> List<T>, val set: (AppData, List<T>) -> AppData)

    private val kinds: List<Kind<*>> = listOf(
        Kind("a_", Account.serializer(), { it.id }, { it.accounts }, { d, l -> d.copy(accounts = l) }),
        Kind("c_", Category.serializer(), { it.id }, { it.categories }, { d, l -> d.copy(categories = l) }),
        Kind("t_", Tx.serializer(), { it.id }, { it.txs }, { d, l -> d.copy(txs = l) }),
        Kind("b_", Budget.serializer(), { it.id }, { it.budgets }, { d, l -> d.copy(budgets = l) }),
        Kind("r_", Recurring.serializer(), { it.id }, { it.recurring }, { d, l -> d.copy(recurring = l) }),
        Kind("g_", Goal.serializer(), { it.id }, { it.goals }, { d, l -> d.copy(goals = l) }),
    )

    fun shared(d: AppData) = SharedSettings(d.settings.base, d.settings.rates, d.settings.ratesDate, d.settings.monthStart)

    /** Чи можна такий ключ класти в базу (Firebase не любить «. $ # [ ] /»). */
    fun validKey(k: String) = k.isNotEmpty() && k.length <= 200 && k.none { it in ".$#[]/" || it.code < 32 }

    /** Усі ключі даних. */
    fun keys(d: AppData): Set<String> {
        val out = HashSet<String>()
        for (k in kinds) addKeys(k, d, out)
        out.add(SETTINGS)
        return out
    }

    private fun <T : Any> addKeys(k: Kind<T>, d: AppData, out: MutableSet<String>) {
        for (x in k.list(d)) out.add(k.prefix + k.id(x))
    }

    /** Ключі записів, що відрізняються між двома станами (додані, змінені, видалені). */
    fun changed(prev: AppData, next: AppData): Set<String> {
        val out = HashSet<String>()
        for (k in kinds) diff(k, prev, next, out)
        if (shared(prev) != shared(next)) out.add(SETTINGS)
        return out
    }

    private fun <T : Any> diff(k: Kind<T>, prev: AppData, next: AppData, out: MutableSet<String>) {
        val a = k.list(prev)
        val b = k.list(next)
        if (a === b) return
        val old = HashMap<String, T>(a.size * 2)
        for (x in a) old[k.id(x)] = x
        for (x in b) {
            val id = k.id(x)
            val o = old.remove(id)
            if (o == null || (o !== x && o != x)) out.add(k.prefix + id)
        }
        for (id in old.keys) out.add(k.prefix + id)
    }

    /** JSON запису за ключем або null, якщо такого запису немає (видалений). */
    fun encode(d: AppData, key: String): String? {
        if (key == SETTINGS) return json.encodeToString(SharedSettings.serializer(), shared(d))
        val k = kinds.firstOrNull { key.startsWith(it.prefix) } ?: return null
        return encodeOf(k, d, key.substring(k.prefix.length))
    }

    private fun <T : Any> encodeOf(k: Kind<T>, d: AppData, id: String): String? {
        val x = k.list(d).firstOrNull { k.id(it) == id } ?: return null
        return json.encodeToString(k.ser, x)
    }

    /** Застосувати записи, що прийшли від інших: JSON — додати/замінити, null — видалити. Невідомі й биті пропускаємо. */
    fun apply(d0: AppData, changes: Map<String, String?>): AppData {
        var d = d0
        changes[SETTINGS]?.let { j ->
            runCatching { json.decodeFromString(SharedSettings.serializer(), j) }.getOrNull()?.let { s ->
                d = d.copy(settings = d.settings.copy(base = s.base, rates = s.rates, ratesDate = s.ratesDate, monthStart = s.monthStart))
            }
        }
        for (k in kinds) d = applyKind(k, d, changes)
        return d
    }

    private fun <T : Any> applyKind(k: Kind<T>, d: AppData, changes: Map<String, String?>): AppData {
        val mine = changes.filterKeys { it.startsWith(k.prefix) }
        if (mine.isEmpty()) return d
        val upserts = LinkedHashMap<String, T>()
        val deletes = HashSet<String>()
        for ((key, j) in mine) {
            val id = key.substring(k.prefix.length)
            if (j == null) deletes.add(id)
            else runCatching { json.decodeFromString(k.ser, j) }.getOrNull()?.takeIf { k.id(it) == id }?.let { upserts[id] = it }
        }
        val list = ArrayList<T>(k.list(d).size + upserts.size)
        for (x in k.list(d)) {
            val id = k.id(x)
            if (id in deletes) continue
            val u = upserts.remove(id)
            list.add(u ?: x)
        }
        list.addAll(upserts.values)
        return k.set(d, list)
    }
}
