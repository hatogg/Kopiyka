package ua.x44to.kopiyka.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ua.x44to.kopiyka.data.Period
import ua.x44to.kopiyka.data.TxType
import java.time.LocalDate

/** Екрани поверх вкладок. Кожен відкритий екран лежить у стеку; «Назад» знімає верхній. */
sealed interface Route {
    data class Edit(val txId: String? = null, val type: TxType = TxType.EXPENSE, val account: String? = null, val toAccount: String? = null) : Route
    data object Accounts : Route
    data class AccountEdit(val id: String?) : Route
    data object Categories : Route
    data class CategoryEdit(val id: String?, val type: TxType = TxType.EXPENSE) : Route
    data object Recurrings : Route
    data class RecurringEdit(val id: String?) : Route
    data class BudgetEdit(val id: String?, val category: String? = null) : Route
    data class GoalEdit(val id: String?) : Route
    data object Rates : Route
    data object Backup : Route
}

class Entry(val id: Long, val route: Route)

/** Фільтр списку операцій. */
data class TxFilter(
    val type: TxType? = null,
    val account: String? = null,
    val category: String? = null,
    val query: String = "",
) {
    val active get() = type != null || account != null || category != null || query.isNotBlank()
}

object Tabs {
    const val HOME = 0
    const val TXS = 1
    const val BUDGETS = 2
    const val STATS = 3
    const val MORE = 4
}

class AppState(private val scope: CoroutineScope) {
    var tab by mutableIntStateOf(Tabs.HOME)
    val stack = mutableStateListOf<Entry>()
    private var nextId = 1L
    var filter by mutableStateOf(TxFilter())

    /** Обраний період (спільний для операцій, бюджетів і статистики); null — поточний. */
    var periodStart by mutableStateOf<LocalDate?>(null)
    var goalsTab by mutableStateOf(false)
    val snack = SnackbarHostState()

    fun period(monthStart: Int): Period {
        val ps = periodStart
        return if (ps == null) ua.x44to.kopiyka.data.Calc.periodOf(LocalDate.now(), monthStart)
        else ua.x44to.kopiyka.data.Calc.periodOf(ps, monthStart)
    }

    fun shiftPeriod(monthStart: Int, by: Int) {
        var p = period(monthStart)
        repeat(kotlin.math.abs(by)) { p = if (by > 0) p.next(monthStart) else p.prev(monthStart) }
        periodStart = if (LocalDate.now() in p) null else p.start
    }

    fun push(r: Route) {
        stack.add(Entry(nextId++, r))
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun openTxs(f: TxFilter) {
        filter = f
        stack.clear()
        tab = Tabs.TXS
    }

    /** Повідомлення внизу; [undo] — дія для кнопки «Скасувати». */
    fun toast(text: String, undo: (() -> Unit)? = null) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(
                text, actionLabel = if (undo != null) ua.x44to.kopiyka.data.t("Скасувати", "Undo") else null,
                withDismissAction = undo == null, duration = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState не задано") }
