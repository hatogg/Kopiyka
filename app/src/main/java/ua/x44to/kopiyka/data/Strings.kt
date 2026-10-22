package ua.x44to.kopiyka.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Мова: українська для uk/ru/be, інакше англійська. Тексти — парами прямо в коді: t("Огляд", "Overview"). */
object L {
    /** для тестів зі скриншотами */
    @Volatile var override: String? = null

    val ukr: Boolean get() = (override ?: Locale.getDefault().language) in setOf("uk", "ru", "be")
    val locale: Locale get() = if (ukr) Locale.forLanguageTag("uk") else Locale.ENGLISH

    private val monthsUk = listOf("Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень", "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень")
    private val monthsUkGen = listOf("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня")
    private val monthsUkShort = listOf("січ", "лют", "бер", "кві", "тра", "чер", "лип", "сер", "вер", "жов", "лис", "гру")
    private val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val daysUk = listOf("понеділок", "вівторок", "середа", "четвер", "п'ятниця", "субота", "неділя")
    private val daysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun month(m: Int) = if (ukr) monthsUk[m - 1] else monthsEn[m - 1]
    fun monthShort(m: Int) = if (ukr) monthsUkShort[m - 1] else monthsEn[m - 1].take(3)
    fun monthTitle(ym: YearMonth) = "${month(ym.monthValue)} ${ym.year}"
    fun weekday(d: DayOfWeek) = if (ukr) daysUk[d.ordinal] else daysEn[d.ordinal]

    /** «22 жовтня» / «October 22»; рік — якщо не поточний. */
    fun date(d: LocalDate, withYear: Boolean = d.year != LocalDate.now().year): String {
        val base = if (ukr) "${d.dayOfMonth} ${monthsUkGen[d.monthValue - 1]}" else "${monthsEn[d.monthValue - 1]} ${d.dayOfMonth}"
        return if (withYear) (if (ukr) "$base ${d.year}" else "$base, ${d.year}") else base
    }

    fun shortDate(d: LocalDate) = if (ukr) "${d.dayOfMonth} ${monthsUkShort[d.monthValue - 1]}" else "${monthsEn[d.monthValue - 1].take(3)} ${d.dayOfMonth}"

    /** «Сьогодні», «Вчора», «Завтра» або дата з днем тижня. */
    fun dayTitle(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> t("Сьогодні", "Today")
            today.minusDays(1) -> t("Вчора", "Yesterday")
            today.plusDays(1) -> t("Завтра", "Tomorrow")
            else -> "${date(d)}, ${weekday(d.dayOfWeek)}"
        }
    }

    fun every(e: Every, n: Int): String = when (e) {
        Every.DAY -> if (n == 1) t("щодня", "daily") else t("кожні $n дн.", "every $n days")
        Every.WEEK -> if (n == 1) t("щотижня", "weekly") else t("кожні $n тиж.", "every $n weeks")
        Every.MONTH -> if (n == 1) t("щомісяця", "monthly") else t("кожні $n міс.", "every $n months")
        Every.YEAR -> if (n == 1) t("щороку", "yearly") else t("кожні $n р.", "every $n years")
    }

    fun everyName(e: Every) = when (e) {
        Every.DAY -> t("День", "Day"); Every.WEEK -> t("Тиждень", "Week"); Every.MONTH -> t("Місяць", "Month"); Every.YEAR -> t("Рік", "Year")
    }

    fun accType(a: AccType) = when (a) {
        AccType.CARD -> t("Картка", "Card"); AccType.CASH -> t("Готівка", "Cash")
        AccType.SAVINGS -> t("Накопичувальний", "Savings"); AccType.OTHER -> t("Інше", "Other")
    }

    fun txType(tp: TxType) = when (tp) {
        TxType.EXPENSE -> t("Витрата", "Expense"); TxType.INCOME -> t("Дохід", "Income"); TxType.TRANSFER -> t("Переказ", "Transfer")
    }

    /** «3 операції» з правильним відмінком. */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10; val m100 = n % 100
        val w = if (m10 == 1 && m100 != 11) one else if (m10 in 2..4 && m100 !in 12..14) few else many
        return "$n $w"
    }

    fun ops(n: Int) = if (ukr) plural(n, "операція", "операції", "операцій") else "$n " + if (n == 1) "transaction" else "transactions"
    fun days(n: Int) = if (ukr) plural(n, "день", "дні", "днів") else "$n " + if (n == 1) "day" else "days"
    fun months(n: Int) = if (ukr) plural(n, "місяць", "місяці", "місяців") else "$n " + if (n == 1) "month" else "months"
}

fun t(uk: String, en: String) = if (L.ukr) uk else en
