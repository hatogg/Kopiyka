package ua.x44to.kopiyka

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ua.x44to.kopiyka.data.AccType
import ua.x44to.kopiyka.data.Account
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Budget
import ua.x44to.kopiyka.data.Contribution
import ua.x44to.kopiyka.data.Defaults
import ua.x44to.kopiyka.data.Goal
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Recurring
import ua.x44to.kopiyka.data.Settings
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.Tx
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import ua.x44to.kopiyka.sync.BudgetCode
import ua.x44to.kopiyka.sync.FirebaseRemote
import ua.x44to.kopiyka.sync.LocalData
import ua.x44to.kopiyka.sync.Sync
import ua.x44to.kopiyka.sync.SyncEngine
import ua.x44to.kopiyka.sync.SyncState
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Не перевірка, а фотосесія: проходить екрани з демо-даними й зберігає скриншоти в files/screens
 * (звідти їх забирає .github/screenshots.sh). Українською та англійською, у світлій і темній темі.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext

    @Test
    fun screenshots() {
        for (lang in listOf("uk", "en")) {
            L.override = lang
            shell("cmd uimode night no")
            Store.init(ctx)
            Store.update { demo() }
            ActivityScenario.launch(MainActivity::class.java).use {
                settle()
                shot("$lang-light-home")
                // новий запис: калькулятор і категорія
                Launch.add.value = TxType.EXPENSE
                settle()
                for (k in "250+80") key(k.toString())
                last(t("Кафе й ресторани", "Cafes & restaurants"))
                settle()
                shot("$lang-light-add")
                noteCheck(lang)
                compose.onNodeWithContentDescription(t("Закрити", "Close")).performClick()
                settle()
                tab(t("Операції", "History"))
                settle()
                shot("$lang-light-history")
                tab(t("Бюджети", "Budgets"))
                settle()
                shot("$lang-light-budgets")
                last(t("Цілі накопичення", "Savings goals"))
                settle()
                shot("$lang-light-goals")
                tab(t("Звіти", "Stats"))
                settle()
                shot("$lang-light-stats")
                tab(t("Ще", "More"))
                settle()
                shot("$lang-light-more")
                if (Sync.configured) sharedFlow(lang)
                last(t("Регулярні платежі", "Recurring payments"))
                settle()
                shot("$lang-light-recurring")
                compose.onNodeWithContentDescription(t("Назад", "Back")).performClick()
                settle()
                last(t("Рахунки", "Accounts"))
                settle()
                shot("$lang-light-accounts")
                compose.onNodeWithContentDescription(t("Назад", "Back")).performClick()
                settle()
                // онлайн-курс: застосунок сам підтягує його при запуску
                last(t("Валюти й курси", "Currencies & rates"))
                runCatching { waitFor("онлайн-курс", 20_000) { Store.data.value.settings.ratesTime > 0 } }
                    .onFailure { Log.w("Kopiyka", "online rates didn't arrive: $it") }
                Log.d("Kopiyka", "rates from ${Store.data.value.settings.ratesFrom}: USD=${Store.data.value.settings.rates["USD"]}")
                settle()
                shot("$lang-light-rates")
                compose.onNodeWithContentDescription(t("Назад", "Back")).performClick()
                settle()
                tab(t("Огляд", "Overview"))
                settle()
            }
            shell("cmd uimode night yes")
            ActivityScenario.launch(MainActivity::class.java).use {
                settle()
                shot("$lang-dark-home")
                tab(t("Бюджети", "Budgets"))
                settle()
                shot("$lang-dark-budgets")
                tab(t("Звіти", "Stats"))
                settle()
                shot("$lang-dark-stats")
                Launch.add.value = TxType.EXPENSE
                settle()
                for (k in "1250") key(k.toString())
                last(t("Продукти", "Groceries"))
                settle()
                shot("$lang-dark-add")
                compose.onNodeWithContentDescription(t("Закрити", "Close")).performClick()
                settle()
                tab(t("Огляд", "Overview"))
                settle()
            }
        }
        shell("cmd uimode night no")
        L.override = null
    }

    /**
     * Регресія бага «натискаю на суму — пише в коментар»: поки пишемо нотатку, цифрової клавіатури немає;
     * дотик до суми повертає її, і цифри йдуть у суму, а нотатка лишається як була.
     */
    private fun noteCheck(lang: String) {
        val note = t("Кава з колегами", "Coffee with colleagues")
        compose.onNode(hasSetTextAction()).performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput(note)
        settle()
        shot("$lang-light-add-note")
        if (compose.onAllNodesWithText("÷", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            throw AssertionError("цифрова клавіатура не сховалась, поки пишемо нотатку")
        compose.onAllNodesWithText("250 + 80", useUnmergedTree = true).onFirst().performClick()
        settle()
        key("5")
        settle()
        if (compose.onAllNodesWithText("250 + 805", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
            throw AssertionError("після дотику до суми цифра не потрапила в суму")
        compose.onNode(hasSetTextAction()).assert(hasText(note))
        Log.d("Kopiyka", "note check ok")
    }

    /**
     * Спільний бюджет через справжню базу: застосунок створює бюджет, «другий телефон» (рушій синхронізації
     * прямо в тесті) приєднується за кодом і додає витрату — вона має з'явитися в застосунку за кілька секунд.
     */
    private fun sharedFlow(lang: String) {
        last(t("Спільний бюджет", "Shared budget"))
        settle()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(t("Оля", "Olivia"))
        compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        settle()
        shot("$lang-light-shared-setup")
        last(t("Створити спільний бюджет", "Create a shared budget"))
        val t0 = System.currentTimeMillis()
        waitFor("бюджет створено") { Sync.status.value.code != null }
        val code = Sync.status.value.code!!
        Log.d("Kopiyka", "created in ${System.currentTimeMillis() - t0} ms")

        val partner = Partner(code, t("Андрій", "Andrew"))
        try {
            check(partner.engine.join()) { "другий телефон не знайшов бюджет" }
            val missing = Store.data.value.txs.count { s -> partner.data.txs.none { it.id == s.id } }
            check(missing == 0) { "другому телефону не дійшло $missing з ${Store.data.value.txs.size} операцій" }
            Log.d("Kopiyka", "partner joined with ${partner.data.txs.size} transactions")
            val tx = Tx(
                "partner-${System.currentTimeMillis()}", TxType.EXPENSE, if (L.ukr) 18_500 else 1_850, "cash", category = "food",
                date = LocalDate.now().toString(), note = t("Овочі на ринку", "Farmers market"), created = System.currentTimeMillis(), by = partner.name,
            )
            val t1 = System.currentTimeMillis()
            partner.add(tx)
            waitFor("операція другого телефона з'явилась у застосунку") { Store.data.value.txs.any { it.id == tx.id } }
            Log.d("Kopiyka", "partner tx arrived in ${System.currentTimeMillis() - t1} ms")
            waitFor("обидва учасники") { Sync.status.value.members.size >= 2 }

            // і навпаки: витрата з застосунку доходить до другого телефона
            val mine = Tx("mine-${System.currentTimeMillis()}", TxType.EXPENSE, if (L.ukr) 9_500 else 450, "card", category = "cafe", date = LocalDate.now().toString(), by = Sync.author())
            Store.saveTx(mine)
            partner.listenFor(20_000) { partner.data.txs.any { it.id == mine.id } }
            Thread.sleep(3500) // хай зникне повідомлення «бюджет створено»
            settle()
            shot("$lang-light-shared")
            compose.onNodeWithContentDescription(t("Назад", "Back")).performClick()
            settle()
            tab(t("Операції", "History"))
            settle()
            shot("$lang-light-shared-history")
            tab(t("Ще", "More"))
            settle()
        } finally {
            Sync.leave()
            runCatching { partner.remote.wipe() }
        }
    }

    private fun waitFor(what: String, ms: Long = 30_000, ok: () -> Boolean) {
        val t0 = System.currentTimeMillis()
        while (!ok()) {
            if (System.currentTimeMillis() - t0 > ms) throw AssertionError("не дочекались: $what")
            Thread.sleep(200)
        }
    }

    /** «Другий телефон»: власні дані в пам'яті й той самий рушій синхронізації, що в застосунку. */
    private class Partner(code: String, val name: String) {
        @Volatile var data = AppData()
        val remote = FirebaseRemote(Sync.server, BudgetCode.budgetId(code), Sync.namespace)
        private val local = object : LocalData {
            override val data get() = this@Partner.data
            override fun mutateRemote(transform: (AppData) -> AppData) {
                synchronized(this@Partner) { this@Partner.data = transform(this@Partner.data) }
            }
        }
        val engine = SyncEngine(remote, code, local, SyncState(code = code, device = "partner-test", name = name))

        fun add(tx: Tx) {
            synchronized(this) {
                val p = data
                data = data.copy(txs = data.txs + tx)
                engine.localChanged(p, data)
            }
            engine.push()
        }

        /** Послухати сервер, доки не виконається умова. */
        fun listenFor(ms: Long, ok: () -> Boolean) {
            val t0 = System.currentTimeMillis()
            val th = Thread { runCatching { engine.listen({ ok() || System.currentTimeMillis() - t0 > ms }) } }
            th.start()
            while (!ok() && System.currentTimeMillis() - t0 < ms) Thread.sleep(200)
            engine.cancel()
            th.join(5_000)
            if (!ok()) throw AssertionError("витрата з застосунку не дійшла до другого телефона")
        }
    }

    /** Клавіша калькулятора: вона в найвищому шарі, тож беремо останній збіг. */
    private fun key(k: String) {
        compose.onAllNodesWithText(k, useUnmergedTree = true).onLast().performClick()
        compose.waitForIdle()
    }

    private fun last(text: String) {
        compose.onAllNodesWithText(text, useUnmergedTree = true).onLast().performClick()
        compose.waitForIdle()
    }

    /** Вкладка внизу: лише вибірний елемент навігації. */
    private fun tab(text: String) {
        compose.onAllNodes(hasText(text) and isSelectable()).onLast().performClick()
    }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: error("no screenshot for $name")
        val dir = File(ctx.filesDir, "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shell(cmd: String) {
        inst.uiAutomation.executeShellCommand(cmd).use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        }
    }

    /** Два з половиною місяці звичайного життя: зарплата, продукти, кава, оренда, підписки, донати. */
    private fun demo(): AppData {
        val uk = L.ukr
        val k = if (uk) 1.0 else 1 / 40.0
        val base = if (uk) "UAH" else "USD"
        fun m(uah: Double) = (uah * k * 100).roundToLong()
        val rnd = Random(44)
        val today = LocalDate.now()
        val accounts = listOf(
            Account("card", t("Картка", "Debit card"), AccType.CARD, base, m(14_200.0), 0xFF1E88E5),
            Account("cash", t("Готівка", "Cash"), AccType.CASH, base, m(4_300.0), 0xFF43A047),
            Account("jar", t("Скарбничка", "Savings jar"), AccType.SAVINGS, base, m(9_000.0), 0xFF00897B),
            Account("usd", if (uk) "Долари" else "Euro", AccType.CASH, if (uk) "USD" else "EUR", 60_000, 0xFF6D4C41),
        )
        val txs = ArrayList<Tx>()
        var id = 0
        fun add(daysAgo: Int, type: TxType, uah: Double, cat: String?, note: String = "", acc: String = "card", to: String? = null, rec: String? = null) {
            if (daysAgo < 0) return
            val d = today.minusDays(daysAgo.toLong())
            txs.add(Tx("t${id++}", type, m(uah), acc, to, null, cat, d.toString(), note, rec, d.toEpochDay() * 1000 + id))
        }
        fun r(a: Int, b: Int) = rnd.nextInt(a, b + 1).toDouble() + if (rnd.nextBoolean()) 0.0 else rnd.nextInt(1, 99) / 100.0
        for (ago in 0..75) {
            val d = today.minusDays(ago.toLong())
            val dom = d.dayOfMonth
            if (ago % 2 == 0 || rnd.nextInt(5) == 0) add(ago, TxType.EXPENSE, r(150, 720), "food", listOf(t("Супермаркет", "Supermarket"), t("Ринок", "Market"), "", "")[rnd.nextInt(4)], if (rnd.nextInt(4) == 0) "cash" else "card")
            if (rnd.nextInt(7) < 2) add(ago, TxType.EXPENSE, r(95, 520), "cafe", listOf(t("Кава", "Coffee"), t("Обід", "Lunch"), t("Піца з друзями", "Pizza with friends"))[rnd.nextInt(3)])
            if (rnd.nextInt(7) < 3) {
                if (rnd.nextInt(3) > 0) add(ago, TxType.EXPENSE, 16.0, "transport", t("Метро", "Metro"), "cash")
                else add(ago, TxType.EXPENSE, r(140, 290), "transport", t("Таксі", "Taxi"))
            }
            if (d.dayOfWeek.value == 6 && (ago / 7) % 2 == 0) add(ago, TxType.EXPENSE, r(1300, 1750), "car", t("Пальне", "Fuel"))
            if (d.dayOfWeek.value == 1) add(ago, TxType.EXPENSE, r(200, 500), "donate", t("Збір на дрони", "Fundraiser"))
            if (dom == 5) add(ago, TxType.EXPENSE, 250.0, "phone", t("Інтернет", "Internet"), rec = "r_net")
            if (dom == 12) add(ago, TxType.EXPENSE, 279.0, "subs", "Netflix", rec = "r_netflix")
            if (dom == 3) add(ago, TxType.EXPENSE, 12_000.0, "home", t("Оренда", "Rent"), rec = "r_rent")
            if (dom == 20) add(ago, TxType.EXPENSE, r(1700, 2600), "home", t("Комуналка", "Utilities"))
            if (dom == 14 || dom == 28) add(ago, TxType.EXPENSE, r(300, 650), "fun", t("Кіно", "Cinema"))
            if (dom == 9) add(ago, TxType.EXPENSE, r(1500, 3200), "clothes")
            if (dom == 17) add(ago, TxType.EXPENSE, r(420, 900), "health", t("Аптека", "Pharmacy"))
            if (dom == 25) add(ago, TxType.INCOME, 23_000.0, "salary", t("Зарплата", "Salary"), rec = "r_salary")
            if (dom == 10) add(ago, TxType.INCOME, 15_000.0, "salary", t("Аванс", "Advance"), rec = "r_advance")
            if (dom == 7) add(ago, TxType.TRANSFER, 2_000.0, null, t("Зняв готівку", "Cash withdrawal"), "card", "cash")
            if (dom == 15) add(ago, TxType.INCOME, r(6000, 9000), "side", t("Логотип для кав'ярні", "Logo for a café"))
            if (dom == 1) add(ago, TxType.INCOME, r(150, 260), "cashback")
            if (dom == 26) add(ago, TxType.TRANSFER, 5_000.0, null, t("Відкладаю", "Saving"), "card", "jar")
        }
        fun nextDay(dom: Int): LocalDate {
            val thisMonth = today.withDayOfMonth(dom)
            return if (thisMonth.isAfter(today)) thisMonth else thisMonth.plusMonths(1)
        }
        val recurring = listOf(
            Recurring("r_util", TxType.EXPENSE, m(2_150.0), "card", category = "home", note = t("Комуналка", "Utilities"), start = today.toString(), auto = false),
            Recurring("r_rent", TxType.EXPENSE, m(12_000.0), "card", category = "home", note = t("Оренда", "Rent"), start = nextDay(3).toString(), auto = false),
            Recurring("r_net", TxType.EXPENSE, m(250.0), "card", category = "phone", note = t("Інтернет", "Internet"), start = nextDay(5).toString()),
            Recurring("r_netflix", TxType.EXPENSE, m(279.0), "card", category = "subs", note = "Netflix", start = nextDay(12).toString()),
            Recurring("r_gym", TxType.EXPENSE, m(900.0), "card", category = "sport", note = t("Спортзал", "Gym"), start = today.plusDays(4).toString(), auto = false),
            Recurring("r_salary", TxType.INCOME, m(23_000.0), "card", category = "salary", note = t("Зарплата", "Salary"), start = nextDay(25).toString()),
            Recurring("r_advance", TxType.INCOME, m(15_000.0), "card", category = "salary", note = t("Аванс", "Advance"), start = nextDay(10).toString()),
            Recurring("r_jar", TxType.TRANSFER, m(5_000.0), "card", toAccount = "jar", note = t("У скарбничку", "To the jar"), start = nextDay(26).toString()),
        )
        val goals = listOf(
            Goal(
                "g_trip", t("Відпустка в горах", "Mountain holiday"), m(30_000.0), base, today.plusMonths(5).withDayOfMonth(1).toString(), "flight", 0xFF1E88E5,
                saved = listOf(Contribution(today.minusDays(40).toString(), m(8_000.0)), Contribution(today.minusDays(10).toString(), m(4_400.0))),
            ),
            Goal("g_laptop", t("Новий ноутбук", "New laptop"), m(45_000.0), base, today.plusMonths(8).withDayOfMonth(1).toString(), "laptop", 0xFF5E35B1, account = "jar"),
            Goal("g_safety", t("Подушка безпеки", "Emergency fund"), 100_000, if (uk) "USD" else "EUR", null, "savings", 0xFF43A047, saved = listOf(Contribution(today.minusDays(60).toString(), 100_000))),
        )
        return Defaults.data().copy(
            accounts = accounts,
            txs = txs,
            budgets = listOf(
                Budget("b_all", null, m(42_000.0)),
                Budget("b_food", "food", m(9_000.0)),
                Budget("b_cafe", "cafe", m(2_800.0)),
                Budget("b_fun", "fun", m(1_000.0)),
                Budget("b_car", "car", m(6_000.0)),
            ),
            recurring = recurring,
            goals = goals,
            settings = Settings(base = base, dynamic = false, notifAsked = true, ratesDate = today.minusDays(3).toString()),
        )
    }
}
