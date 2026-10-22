package ua.x44to.kopiyka.data

import kotlinx.serialization.Serializable

/** Суми зберігаються в копійках (центах) — Long, без похибок плаваючої коми. */

@Serializable
enum class TxType { EXPENSE, INCOME, TRANSFER }

@Serializable
enum class AccType { CARD, CASH, SAVINGS, OTHER }

@Serializable
enum class Every { DAY, WEEK, MONTH, YEAR }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccType = AccType.CARD,
    val currency: String = "UAH",
    /** початковий залишок */
    val initial: Long = 0,
    val color: Long = 0xFF2E7D6B,
    val archived: Boolean = false,
    /** враховувати в загальному балансі */
    val inTotal: Boolean = true,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    /** EXPENSE або INCOME */
    val type: TxType,
    val icon: String,
    val color: Long,
    val archived: Boolean = false,
)

@Serializable
data class Tx(
    val id: String,
    val type: TxType,
    /** завжди додатна, у валюті рахунку */
    val amount: Long,
    val account: String,
    /** для переказу — куди */
    val toAccount: String? = null,
    /** для переказу між валютами — скільки надійшло */
    val toAmount: Long? = null,
    val category: String? = null,
    /** yyyy-MM-dd */
    val date: String,
    val note: String = "",
    /** якщо створено регулярним платежем */
    val recurring: String? = null,
    val created: Long = 0,
    /** хто додав (ім'я учасника спільного бюджету) */
    val by: String? = null,
)

@Serializable
data class Budget(
    val id: String,
    /** null — загальний бюджет на всі витрати */
    val category: String? = null,
    /** у базовій валюті */
    val limit: Long,
)

@Serializable
data class Recurring(
    val id: String,
    val type: TxType,
    val amount: Long,
    val account: String,
    val toAccount: String? = null,
    val category: String? = null,
    val note: String = "",
    val every: Every = Every.MONTH,
    val interval: Int = 1,
    /** перша дата, yyyy-MM-dd */
    val start: String,
    /** номер наступного повтору: дата = start + n × interval */
    val n: Int = 0,
    val end: String? = null,
    /** додавати операцію автоматично (інакше — лише нагадати) */
    val auto: Boolean = true,
    val remind: Boolean = true,
    val active: Boolean = true,
    /** номер повтору, про який уже нагадали */
    val reminded: Int = -1,
)

@Serializable
data class Contribution(val date: String, val amount: Long)

@Serializable
data class Goal(
    val id: String,
    val name: String,
    val target: Long,
    val currency: String = "UAH",
    val deadline: String? = null,
    val icon: String = "savings",
    val color: Long = 0xFF00897B,
    /** якщо прив'язано до рахунку — прогрес = залишок рахунку */
    val account: String? = null,
    val saved: List<Contribution> = emptyList(),
)

@Serializable
data class Settings(
    val base: String = "UAH",
    /** скільки гривень коштує одиниця валюти (UAH = 1) */
    val rates: Map<String, Double> = Money.DEFAULT_RATES,
    val ratesDate: String? = null,
    /** з якого числа починається бюджетний місяць (день зарплати) */
    val monthStart: Int = 1,
    val notifications: Boolean = true,
    val budgetAlerts: Boolean = true,
    /** 0 — як у системі, 1 — світла, 2 — темна */
    val theme: Int = 0,
    val dynamic: Boolean = true,
    val hideAmounts: Boolean = false,
    val lastBackup: String? = null,
    /** чи вже питали дозвіл на сповіщення */
    val notifAsked: Boolean = false,
)

@Serializable
data class AppData(
    val version: Int = 1,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val txs: List<Tx> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val settings: Settings = Settings(),
) {
    fun account(id: String?) = accounts.firstOrNull { it.id == id }
    fun category(id: String?) = categories.firstOrNull { it.id == id }
    fun goal(id: String?) = goals.firstOrNull { it.id == id }
}

/** Палітра для рахунків, категорій і цілей. */
val PALETTE = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
    0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33, 0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E,
    0xFF6D4C41, 0xFF757575, 0xFF546E7A, 0xFF2E7D6B,
)
