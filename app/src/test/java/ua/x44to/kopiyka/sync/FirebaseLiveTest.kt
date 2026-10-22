package ua.x44to.kopiyka.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import ua.x44to.kopiyka.BuildConfig
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Tx
import ua.x44to.kopiyka.data.TxType
import kotlin.concurrent.thread

/**
 * Справжня база Firebase: два «телефони» з тим самим кодом обмінюються операціями через інтернет.
 * Запускається в CI (KP_LIVE_TESTS=1); після себе бюджет стирає.
 */
class FirebaseLiveTest {
    private val code = BudgetCode.generate()
    private val server = System.getenv("KP_SYNC_URL") ?: BuildConfig.SYNC_URL
    private val remote get() = FirebaseRemote(server, BudgetCode.budgetId(code))

    @Before
    fun live() {
        assumeTrue("live tests are off", System.getenv("KP_LIVE_TESTS") == "1")
        L.override = "uk"
    }

    @After
    fun cleanup() {
        if (System.getenv("KP_LIVE_TESTS") == "1") runCatching { remote.wipe() }
    }

    private fun waitFor(what: String, ms: Long = 20_000, ok: () -> Boolean) {
        val t0 = System.currentTimeMillis()
        while (!ok()) {
            if (System.currentTimeMillis() - t0 > ms) throw AssertionError("timeout: $what")
            Thread.sleep(100)
        }
    }

    @Test
    fun restRoundTrip() {
        val r = remote
        assertTrue("новий бюджет порожній", r.getAll().isEmpty())
        r.patch(mapOf("t_a" to RemoteEntry(5, "AAA"), "t_b" to RemoteEntry(6)))
        val all = r.getAll()
        assertEquals(RemoteEntry(5, "AAA"), all["t_a"])
        assertEquals(RemoteEntry(6, null), all["t_b"])
    }

    @Test
    fun rulesRejectWrongPaths() {
        // адреса не з 32 шістнадцяткових символів — правила бази мають відмовити
        val bad = FirebaseRemote(server, "not-a-budget")
        val e = runCatching { bad.getAll() }.exceptionOrNull()
        assertTrue("очікували відмову, а маємо $e", e is AccessDenied)
    }

    @Test
    fun twoPhonesThroughFirebase() {
        val a = Phone("Оля", FirebaseRemote(server, BudgetCode.budgetId(code)), System.currentTimeMillis())
        val b = Phone("Андрій", FirebaseRemote(server, BudgetCode.budgetId(code)), System.currentTimeMillis())
        a.update { it.copy(txs = it.txs + Tx("first", TxType.EXPENSE, 12_345, "card", category = "food", date = "2026-10-20")) }
        a.connect(code).create()
        assertTrue(b.connect(code).join())
        assertTrue(b.data.txs.any { it.id == "first" && it.amount == 12_345L })

        // обидва слухають сервер у фоні (Server-Sent Events), як застосунок на екрані
        var stop = false
        val ta = thread { runCatching { a.engine!!.listen({ stop }) } }
        val tb = thread { runCatching { b.engine!!.listen({ stop }) } }
        Thread.sleep(1500)

        b.update { it.copy(txs = it.txs + Tx("second", TxType.EXPENSE, 9_900, "cash", category = "cafe", date = "2026-10-21", by = "Андрій")) }
        b.engine!!.push()
        waitFor("A отримує операцію B") { a.data.txs.any { it.id == "second" && it.by == "Андрій" } }

        a.update { it.copy(txs = it.txs.filter { t -> t.id != "first" }) }
        a.engine!!.push()
        waitFor("B бачить видалення") { b.data.txs.none { it.id == "first" } }

        waitFor("учасники") { a.engine!!.state.members.values.map { it.name }.toSet() == setOf("Оля", "Андрій") }

        stop = true
        a.engine!!.cancel()
        b.engine!!.cancel()
        ta.join(5_000)
        tb.join(5_000)
    }
}
