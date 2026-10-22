package ua.x44to.kopiyka.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Budget
import ua.x44to.kopiyka.data.Category
import ua.x44to.kopiyka.data.Defaults
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Tx
import ua.x44to.kopiyka.data.TxType
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Сервер у пам'яті: те саме, що Firebase, але без мережі. */
class FakeRemote : Remote {
    val db = HashMap<String, RemoteEntry>()
    private val listeners = CopyOnWriteArrayList<(Map<String, RemoteEntry>, Boolean) -> Unit>()
    var online = true

    @Synchronized
    override fun getAll(): Map<String, RemoteEntry> {
        if (!online) throw IOException("offline")
        return HashMap(db)
    }

    override fun patch(entries: Map<String, RemoteEntry>) {
        if (!online) throw IOException("offline")
        synchronized(this) { db.putAll(entries) }
        for (l in listeners) l(entries, false)
    }

    override fun listen(onChange: (Map<String, RemoteEntry>, Boolean) -> Unit, stop: () -> Boolean) {
        onChange(getAll(), true)
        listeners.add(onChange)
    }
}

/** «Телефон» для тестів: дані в пам'яті + рушій синхронізації. */
class Phone(val name: String, private val remote: Remote, private var clock: Long) {
    @Volatile var data: AppData = Defaults.data()
    var engine: SyncEngine? = null
    private val local = object : LocalData {
        override val data get() = this@Phone.data
        override fun mutateRemote(transform: (AppData) -> AppData) {
            synchronized(this@Phone) { this@Phone.data = transform(this@Phone.data) }
        }
    }

    fun update(t: (AppData) -> AppData) = synchronized(this) {
        val p = data
        data = t(p)
        engine?.localChanged(p, data)
    }

    fun connect(code: String): SyncEngine =
        SyncEngine(remote, code, local, SyncState(code = code, device = "dev-$name", name = name), clock = { clock++ }).also { engine = it }
}

class SyncEngineTest {
    private lateinit var code: String
    private lateinit var server: FakeRemote

    @Before
    fun setUp() {
        L.override = "uk"
        code = BudgetCode.generate()
        server = FakeRemote()
    }

    private fun tx(id: String, amount: Long, by: String? = null) =
        Tx(id, TxType.EXPENSE, amount, "card", category = "food", date = "2026-10-01", by = by)

    @Test
    fun codes() {
        assertEquals(code, BudgetCode.normalize(BudgetCode.pretty(code).lowercase().replace("-", " ")))
        assertNull(BudgetCode.normalize("hello"))
        assertTrue(BudgetCode.budgetId(code).matches(Regex("[0-9a-f]{32}")))
        val sealed = Seal(code).seal("привіт ₴")
        assertEquals("привіт ₴", Seal(code).open(sealed))
        assertNull(Seal(BudgetCode.generate()).open(sealed))
    }

    @Test
    fun twoPhonesStayInSync() {
        val a = Phone("Оля", server, 1_000)
        val b = Phone("Андрій", server, 900_000) // годинник Андрія «поспішає»
        a.update { it.copy(txs = it.txs + tx("x1", 25_000)) }
        a.connect(code).create()
        assertTrue(server.db.containsKey("t_x1"))
        assertTrue("сервер бачить лише шифр", server.db.values.none { (it.d ?: "").contains("food") })

        b.update { it.copy(txs = listOf(tx("own", 1))) }
        assertTrue(b.connect(code).join())
        assertEquals(listOf("x1"), b.data.txs.map { it.id })
        assertEquals(setOf("Оля", "Андрій"), b.engine!!.state.members.values.map { it.name }.toSet())
        a.engine!!.listen({ false })
        b.engine!!.listen({ false })

        b.update { it.copy(txs = it.txs + tx("x2", 9_900, by = "Андрій")) }
        b.engine!!.push()
        assertTrue(a.data.txs.any { it.id == "x2" && it.by == "Андрій" })

        a.update { it.copy(txs = it.txs.filter { t -> t.id != "x1" }) }
        a.engine!!.push()
        assertTrue(b.data.txs.none { it.id == "x1" })

        // одночасна правка одного запису — обидва телефони сходяться до однакового результату
        a.update { it.copy(txs = it.txs.map { t -> if (t.id == "x2") t.copy(note = "Оля") else t }) }
        b.update { it.copy(txs = it.txs.map { t -> if (t.id == "x2") t.copy(note = "Андрій") else t }) }
        a.engine!!.push()
        b.engine!!.push()
        assertEquals(a.data.txs.first { it.id == "x2" }.note, b.data.txs.first { it.id == "x2" }.note)

        // спільні налаштування синхронізуються, особисті (тема) — ні
        a.update { it.copy(settings = it.settings.copy(monthStart = 10, theme = 2)) }
        a.engine!!.push()
        assertEquals(10, b.data.settings.monthStart)
        assertEquals(0, b.data.settings.theme)

        a.update { it.copy(categories = it.categories + Category("c9", "Кава", TxType.EXPENSE, "cafe", 1), budgets = it.budgets + Budget("bb", null, 4_200_000)) }
        a.engine!!.push()
        assertTrue(b.data.categories.any { it.id == "c9" } && b.data.budgets.any { it.id == "bb" })
    }

    @Test
    fun offlineChangesWaitInQueue() {
        val a = Phone("A", server, 1)
        val b = Phone("B", server, 1)
        a.connect(code).create()
        b.connect(code).join()
        b.engine!!.listen({ false })
        server.online = false
        a.update { it.copy(txs = it.txs + tx("off", 100)) }
        try {
            a.engine!!.push()
            error("push без мережі мав упасти")
        } catch (_: IOException) {
        }
        assertEquals(1, a.engine!!.pendingCount)
        server.online = true
        a.engine!!.push()
        assertEquals(0, a.engine!!.pendingCount)
        assertTrue(b.data.txs.any { it.id == "off" })
    }

    @Test
    fun wrongCodeFindsNothing() {
        Phone("A", server, 1).connect(code).create()
        assertFalse(Phone("X", server, 1).connect(BudgetCode.generate()).join())
    }

    @Test
    fun selfHealsAfterServerWipe() {
        val a = Phone("A", server, 1)
        a.update { it.copy(txs = it.txs + tx("k", 5)) }
        a.connect(code).create()
        server.db.clear()
        a.engine!!.receive(emptyMap(), full = true)
        a.engine!!.push()
        assertTrue(server.db.containsKey("t_k"))
    }

    @Test
    fun recordsDiffOnlyWhatChanged() {
        val d0 = Defaults.data().copy(txs = (1..2000).map { tx("p$it", it.toLong()) })
        val d1 = d0.copy(txs = d0.txs.map { if (it.id == "p7") it.copy(note = "!") else it })
        assertEquals(setOf("t_p7"), Records.changed(d0, d1))
        val d2 = d1.copy(txs = d1.txs.filter { it.id != "p8" })
        assertEquals(setOf("t_p8"), Records.changed(d1, d2))
        val back = Records.apply(d2, mapOf("t_p8" to Records.encode(d1, "t_p8")))
        assertTrue(back.txs.any { it.id == "p8" })
    }
}
