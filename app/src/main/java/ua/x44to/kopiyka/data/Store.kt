package ua.x44to.kopiyka.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ua.x44to.kopiyka.widget.BalanceWidget
import ua.x44to.kopiyka.work.Notify
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Усі дані — один JSON-файл у пам'яті застосунку. Читаємо й пишемо цілком; інтернет не потрібен. */
object Store {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val pretty = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data
    private var file: File? = null
    private var app: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val c = context.applicationContext
        app = c
        val f = File(c.filesDir, "data.json")
        file = f
        _data.value = try {
            if (f.exists()) json.decodeFromString(AppData.serializer(), f.readText()) else Defaults.data()
        } catch (e: Exception) {
            // зіпсований файл не губимо: відкладаємо поруч, щоб можна було врятувати
            try { f.copyTo(File(c.filesDir, "data.broken.json"), overwrite = true) } catch (_: Exception) {}
            Defaults.data()
        }
        if (!f.exists()) save(_data.value)
    }

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        if (next === _data.value) return
        _data.value = next
        save(next)
        val c = app ?: return
        scope.launch {
            try { BalanceWidget().updateAll(c) } catch (_: Exception) {}
        }
    }

    private fun save(d: AppData) {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), d))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private inline fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> {
        val key = id(item)
        return if (any { id(it) == key }) map { if (id(it) == key) item else it } else this + item
    }

    // ───────── операції ─────────

    fun saveTx(t: Tx) {
        val before = _data.value
        update { d -> d.copy(txs = d.txs.upsert(t) { it.id }) }
        if (t.type == TxType.EXPENSE) budgetAlerts(before, _data.value)
    }

    /** Видаляє операцію й повертає її — щоб можна було «Скасувати». */
    fun deleteTx(id: String): Tx? {
        val t = _data.value.txs.firstOrNull { it.id == id } ?: return null
        update { d -> d.copy(txs = d.txs.filter { it.id != id }) }
        return t
    }

    fun restoreTx(t: Tx) = update { d -> if (d.txs.any { it.id == t.id }) d else d.copy(txs = d.txs + t) }

    // ───────── рахунки ─────────

    fun saveAccount(a: Account) = update { d -> d.copy(accounts = d.accounts.upsert(a) { it.id }) }

    fun accountUsed(id: String) = _data.value.let { d -> d.txs.any { it.account == id || it.toAccount == id } || d.recurring.any { it.account == id || it.toAccount == id } }

    /** Рахунок без операцій видаляємо; з операціями — лише архівуємо (історія не губиться). */
    fun deleteAccount(id: String) = update { d ->
        if (d.txs.any { it.account == id || it.toAccount == id }) d.copy(accounts = d.accounts.map { if (it.id == id) it.copy(archived = true) else it })
        else d.copy(
            accounts = d.accounts.filter { it.id != id },
            recurring = d.recurring.filter { it.account != id && it.toAccount != id },
            goals = d.goals.map { if (it.account == id) it.copy(account = null) else it },
        )
    }

    // ───────── категорії ─────────

    fun saveCategory(c: Category) = update { d -> d.copy(categories = d.categories.upsert(c) { it.id }) }

    /** Операції категорії лишаються — стають «Без категорії». */
    fun deleteCategory(id: String) = update { d ->
        d.copy(
            categories = d.categories.filter { it.id != id },
            txs = d.txs.map { if (it.category == id) it.copy(category = null) else it },
            budgets = d.budgets.filter { it.category != id },
            recurring = d.recurring.map { if (it.category == id) it.copy(category = null) else it },
        )
    }

    // ───────── бюджети й цілі ─────────

    fun saveBudget(b: Budget) = update { d ->
        // на одну категорію — один бюджет
        val others = d.budgets.filter { it.id != b.id && it.category != b.category }
        d.copy(budgets = others + b)
    }

    fun deleteBudget(id: String) = update { d -> d.copy(budgets = d.budgets.filter { it.id != id }) }

    fun saveGoal(g: Goal) = update { d -> d.copy(goals = d.goals.upsert(g) { it.id }) }

    fun deleteGoal(id: String) = update { d -> d.copy(goals = d.goals.filter { it.id != id }) }

    /** Поповнити (amount > 0) або зняти (amount < 0) з цілі, не прив'язаної до рахунку. */
    fun contribute(goalId: String, amount: Long) = update { d ->
        d.copy(goals = d.goals.map { if (it.id == goalId) it.copy(saved = it.saved + Contribution(LocalDate.now().toString(), amount)) else it })
    }

    // ───────── регулярні платежі ─────────

    fun saveRecurring(r: Recurring) = update { d -> d.copy(recurring = d.recurring.upsert(r) { it.id }) }

    fun deleteRecurring(id: String) = update { d -> d.copy(recurring = d.recurring.filter { it.id != id }) }

    private fun txFrom(r: Recurring, date: LocalDate, amount: Long = r.amount): Tx {
        val d = _data.value
        val toAmount = if (r.type == TxType.TRANSFER && r.toAccount != null) {
            val from = d.account(r.account)?.currency ?: d.settings.base
            val to = d.account(r.toAccount)?.currency ?: d.settings.base
            if (from != to) Money.convert(amount, from, to, d.settings) else null
        } else null
        return Tx(
            id = newId(), type = r.type, amount = amount, account = r.account, toAccount = r.toAccount, toAmount = toAmount,
            category = if (r.type == TxType.TRANSFER) null else r.category, date = date.toString(), note = r.note,
            recurring = r.id, created = System.currentTimeMillis(),
        )
    }

    /**
     * Додає всі «автоматичні» повтори, дата яких уже настала. Повертає додані операції
     * (для сповіщень). Повтори «лише нагадати» чекають, поки людина натисне «Сплатити».
     */
    fun processRecurring(today: LocalDate = LocalDate.now()): List<Pair<Recurring, Tx>> {
        val added = ArrayList<Pair<Recurring, Tx>>()
        val d0 = _data.value
        if (d0.recurring.none { it.active && it.auto }) return added
        val newRec = d0.recurring.map { r0 ->
            var r = r0
            if (!r.active || !r.auto || d0.account(r.account) == null) return@map r
            var guard = 0
            while (guard++ < 400) {
                val date = Calc.nextDate(r) ?: break
                if (date.isAfter(today)) break
                added.add(r to txFrom(r, date))
                r = r.copy(n = r.n + 1)
            }
            r
        }
        if (added.isEmpty()) return added
        val before = d0
        update { d -> d.copy(txs = d.txs + added.map { it.second }, recurring = newRec) }
        if (added.any { it.second.type == TxType.EXPENSE }) budgetAlerts(before, _data.value)
        return added
    }

    /** «Сплатити» платіж-нагадування: додати операцію й перейти до наступної дати. */
    fun payRecurring(id: String, amount: Long? = null) {
        val r = _data.value.recurring.firstOrNull { it.id == id } ?: return
        val date = Calc.nextDate(r) ?: return
        val today = LocalDate.now()
        val tx = txFrom(r, if (date.isAfter(today)) today else date, amount ?: r.amount)
        val before = _data.value
        update { d -> d.copy(txs = d.txs + tx, recurring = d.recurring.map { if (it.id == id) it.copy(n = it.n + 1) else it }) }
        if (tx.type == TxType.EXPENSE) budgetAlerts(before, _data.value)
    }

    fun skipRecurring(id: String) = update { d -> d.copy(recurring = d.recurring.map { if (it.id == id) it.copy(n = it.n + 1) else it }) }

    fun markReminded(id: String, n: Int) = update { d -> d.copy(recurring = d.recurring.map { if (it.id == id) it.copy(reminded = n) else it }) }

    // ───────── налаштування ─────────

    fun setSettings(s: Settings) = update { d -> d.copy(settings = s) }

    fun settings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }

    /** Нова основна валюта: ліміти бюджетів перераховуємо, щоб вони не «стрибнули». */
    fun setBase(code: String) = update { d ->
        val old = d.settings.base
        if (old == code) d
        else d.copy(
            settings = d.settings.copy(base = code),
            budgets = d.budgets.map { it.copy(limit = Money.convert(it.limit, old, code, d.settings)) },
        )
    }

    /** Курс «1 [code] = [perBase] основної валюти». Зберігаємо відносно гривні. */
    fun setRate(code: String, perBase: Double) = update { d ->
        val s = d.settings
        val baseRate = s.rates[s.base] ?: Money.DEFAULT_RATES[s.base] ?: 1.0
        val uah = if (code == "UAH") 1.0 else perBase * baseRate
        if (code == "UAH") {
            // гривня — опора; якщо основна валюта інша, змінюємо курс основної
            d.copy(settings = s.copy(rates = s.rates + (s.base to 1.0 / perBase), ratesDate = LocalDate.now().toString()))
        } else d.copy(settings = s.copy(rates = s.rates + (code to uah), ratesDate = LocalDate.now().toString()))
    }

    // ───────── сповіщення про бюджет ─────────

    /** Після нової витрати: якщо бюджет перейшов 80 % або 100 % — сповіщення. */
    private fun budgetAlerts(before: AppData, after: AppData) {
        val c = app ?: return
        val s = after.settings
        if (!s.notifications || !s.budgetAlerts) return
        val p = Calc.current(after)
        for (b in after.budgets) {
            if (b.limit <= 0) continue
            val was = Calc.spentInCategory(before, p, b.category)
            val now = Calc.spentInCategory(after, p, b.category)
            val name = if (b.category == null) t("Загальний бюджет", "Overall budget") else after.category(b.category)?.name ?: continue
            val text = when {
                was <= b.limit && now > b.limit -> t("Перевищено на ${Money.format(now - b.limit, s.base)}", "Over by ${Money.format(now - b.limit, s.base)}")
                was < b.limit * 8 / 10 && now >= b.limit * 8 / 10 && now <= b.limit ->
                    t("Витрачено ${now * 100 / b.limit}% — залишилось ${Money.format(b.limit - now, s.base)}", "${now * 100 / b.limit}% spent — ${Money.format(b.limit - now, s.base)} left")
                else -> continue
            }
            Notify.budget(c, b.id, name, text)
        }
    }

    // ───────── резервна копія та експорт ─────────

    fun exportJson(): String = pretty.encodeToString(AppData.serializer(), _data.value)

    /** Відновлення з копії. Повертає текст помилки або null, якщо все гаразд. */
    fun importJson(text: String): String? {
        val d = try {
            json.decodeFromString(AppData.serializer(), text)
        } catch (e: Exception) {
            return t("Це не схоже на копію Копійки", "This doesn't look like a Kopiyka backup")
        }
        if (d.accounts.isEmpty()) return t("У файлі немає рахунків", "The file has no accounts")
        update { d.copy(settings = d.settings.copy(notifAsked = _data.value.settings.notifAsked)) }
        return null
    }

    fun markBackup() = settings { it.copy(lastBackup = LocalDate.now().toString()) }

    /** CSV для Excel/Таблиць: з BOM, роздільник «;» і десяткова кома для української мови. */
    fun csv(): String {
        val d = _data.value
        val sep = if (L.ukr) ';' else ','
        val dec = if (L.ukr) "," else "."
        fun q(s: String) = if (s.any { it == sep || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        fun num(m: Long) = (if (m < 0) "-" else "") + "${kotlin.math.abs(m) / 100}$dec${(kotlin.math.abs(m) % 100).toString().padStart(2, '0')}"
        val sb = StringBuilder("﻿")
        val head = if (L.ukr) listOf("Дата", "Тип", "Сума", "Валюта", "Рахунок", "Категорія", "Нотатка", "На рахунок", "Зараховано", "Сума в ${d.settings.base}")
        else listOf("Date", "Type", "Amount", "Currency", "Account", "Category", "Note", "To account", "Received", "Amount in ${d.settings.base}")
        sb.append(head.joinToString(sep.toString())).append("\r\n")
        for (t in d.txs.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.created })) {
            val acc = d.account(t.account)
            val to = d.account(t.toAccount)
            val signed = if (t.type == TxType.EXPENSE) -t.amount else t.amount
            val row = listOf(
                t.date, L.txType(t.type), num(signed), acc?.currency ?: "", acc?.name ?: "",
                d.category(t.category)?.name ?: "", t.note, to?.name ?: "",
                if (t.type == TxType.TRANSFER) num(t.toAmount ?: t.amount) else "",
                if (t.type == TxType.TRANSFER) "" else num(if (t.type == TxType.EXPENSE) -Calc.base(d, t) else Calc.base(d, t)),
            )
            sb.append(row.joinToString(sep.toString()) { q(it) }).append("\r\n")
        }
        return sb.toString()
    }

    /** Стерти все й почати з чистого аркуша. */
    fun reset() = update { d -> Defaults.data().let { it.copy(settings = it.settings.copy(notifAsked = d.settings.notifAsked)) } }
}
