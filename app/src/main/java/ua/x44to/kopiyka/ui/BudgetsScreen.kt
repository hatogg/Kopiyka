@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ua.x44to.kopiyka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Budget
import ua.x44to.kopiyka.data.BudgetState
import ua.x44to.kopiyka.data.Calc
import ua.x44to.kopiyka.data.Goal
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.PALETTE
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate

@Composable
fun BudgetsScreen(d: AppData, padding: PaddingValues) {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        Text(
            if (app.goalsTab) t("Цілі", "Goals") else t("Бюджети", "Budgets"), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
        )
        Segmented(
            listOf(t("Бюджети", "Budgets"), t("Цілі накопичення", "Savings goals")),
            if (app.goalsTab) 1 else 0, { app.goalsTab = it == 1 },
            Modifier.padding(horizontal = 16.dp),
        )
        if (app.goalsTab) Goals(d, padding) else Budgets(d, padding)
    }
}

@Composable
private fun Budgets(d: AppData, padding: PaddingValues) {
    val app = LocalApp.current
    val s = d.settings
    val hide = s.hideAmounts
    val p = app.period(s.monthStart)
    val states = remember(d, p) {
        d.budgets.map { Calc.budgetState(d, it, p) }
            .sortedWith(compareBy<BudgetState> { it.budget.category != null }.thenByDescending { it.ratio })
    }
    val unbudgeted = remember(d, p) {
        val withBudget = d.budgets.mapNotNull { it.category }.toSet()
        Calc.byCategory(d, p, TxType.EXPENSE).filter { it.first != null && it.first !in withBudget }
    }
    PeriodBar(app, s.monthStart, Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp)) {
        if (states.isEmpty()) {
            item {
                Empty(
                    Icons.Rounded.PieChart, t("Бюджетів ще немає", "No budgets yet"),
                    t(
                        "Задайте загальний ліміт на місяць або окремо на категорії — наприклад, кафе чи розваги.",
                        "Set an overall monthly limit or limits for categories like cafes or entertainment.",
                    ),
                )
            }
        }
        items(states, key = { it.budget.id }) { st -> BudgetCard(d, st, p.contains(LocalDate.now())) { app.push(Route.BudgetEdit(st.budget.id)) } }
        if (unbudgeted.isNotEmpty()) {
            item { SectionTitle(t("Витрати без бюджету", "Spending without a budget")) }
            item {
                SoftCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        unbudgeted.forEachIndexed { i, (cat, sum) ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val c = d.category(cat)
                            Row(
                                Modifier.fillMaxWidth().clickable { app.push(Route.BudgetEdit(null, cat)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CatBadge(c?.icon, c?.color ?: 0xFF757575, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(d.catName(cat), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Money.format(sum, s.base, hide = hide), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.Add, t("Задати ліміт", "Set a limit"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(d: AppData, st: BudgetState, current: Boolean, onClick: () -> Unit) {
    val s = d.settings
    val hide = s.hideAmounts
    val overall = st.budget.category == null
    val c = d.category(st.budget.category)
    SoftCard(onClick = onClick, color = if (overall) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (overall) Badge(Icons.Rounded.PieChart, MaterialTheme.colorScheme.primary) else CatBadge(c?.icon, c?.color ?: 0xFF757575)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (overall) t("Усі витрати", "All spending") else d.catName(st.budget.category), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Money.format(st.spent, s.base, hide = hide)} ${t("з", "of")} ${Money.format(st.limit, s.base, hide = hide)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${(st.ratio * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = budgetColor(st.ratio), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Bar(st.ratio, budgetColor(st.ratio), height = 10.dp)
            Spacer(Modifier.height(8.dp))
            val left = st.left
            Text(
                when {
                    left < 0 -> t("Перевищено на ${Money.format(-left, s.base, hide = hide)}", "Over by ${Money.format(-left, s.base, hide = hide)}")
                    current && st.daysLeft > 0 -> t(
                        "Залишилось ${Money.format(left, s.base, hide = hide)} · ${Money.format(st.perDay, s.base, hide = hide)} на день",
                        "${Money.format(left, s.base, hide = hide)} left · ${Money.format(st.perDay, s.base, hide = hide)} a day",
                    )
                    else -> t("Залишилось ${Money.format(left, s.base, hide = hide)}", "${Money.format(left, s.base, hide = hide)} left")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (current && st.daysLeft > 1 && st.spent > 0 && left >= 0 && st.forecast > st.limit) {
                Text(
                    t("За цим темпом до кінця місяця вийде ≈ ${Money.format(st.forecast, s.base, hide = hide)}", "At this pace you'll reach ≈ ${Money.format(st.forecast, s.base, hide = hide)}"),
                    style = MaterialTheme.typography.bodySmall, color = warnColor,
                )
            }
        }
    }
}

@Composable
private fun Goals(d: AppData, padding: PaddingValues) {
    val app = LocalApp.current
    val bal = remember(d) { Calc.balances(d) }
    var open by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = padding.calculateBottomPadding() + 96.dp)) {
        if (d.goals.isEmpty()) {
            item {
                Empty(
                    Icons.Rounded.Flag, t("Цілей ще немає", "No goals yet"),
                    t(
                        "Відкладайте на відпустку, техніку чи «подушку безпеки» — Копійка покаже прогрес і скільки відкладати щомісяця.",
                        "Save for a trip, a gadget or an emergency fund — Kopiyka shows progress and how much to put aside each month.",
                    ),
                )
            }
        }
        items(d.goals, key = { it.id }) { g -> GoalCard(d, g, Calc.goalSaved(d, g, bal)) { open = g.id } }
    }
    val g = d.goal(open)
    if (g != null) GoalDialog(d, g, Calc.goalSaved(d, g, bal), onDismiss = { open = null }, onEdit = { open = null; app.push(Route.GoalEdit(g.id)) })
}

@Composable
private fun GoalCard(d: AppData, g: Goal, saved: Long, onClick: () -> Unit) {
    val hide = d.settings.hideAmounts
    val ratio = if (g.target <= 0) 0f else saved.toFloat() / g.target
    val done = saved >= g.target
    val col = tint(g.color)
    SoftCard(onClick = onClick) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(catIcon(g.icon), col, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Money.format(saved, g.currency, hide = hide)} ${t("з", "of")} ${Money.format(g.target, g.currency, hide = hide)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (done) Icon(Icons.Rounded.CheckCircle, null, tint = incomeColor, modifier = Modifier.size(28.dp))
                else Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = col, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Bar(ratio, if (done) incomeColor else col, height = 10.dp)
            val sub = buildList {
                if (done) add(t("Ціль досягнуто!", "Goal reached!"))
                else add(t("Ще ${Money.format(g.target - saved, g.currency, hide = hide)}", "${Money.format(g.target - saved, g.currency, hide = hide)} to go"))
                val dl = g.deadline?.let { LocalDate.parse(it) }
                if (dl != null && !done) {
                    add(t("до ", "by ") + L.date(dl))
                    val m = Calc.goalMonthly(g, saved)
                    if (m != null && m > 0) add("≈ ${Money.format(m, g.currency, hide = hide)}${t("/міс", "/mo")}")
                }
                d.account(g.account)?.let { add(it.name) }
            }
            Spacer(Modifier.height(8.dp))
            Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Поповнити або зняти з цілі. Для цілі-рахунку — переказ на цей рахунок. */
@Composable
private fun GoalDialog(d: AppData, g: Goal, saved: Long, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val app = LocalApp.current
    var text by remember { mutableStateOf("") }
    val acc = d.account(g.account)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Badge(catIcon(g.icon), tint(g.color), 48.dp) },
        title = { Text(g.name) },
        text = {
            Column {
                Text(
                    "${Money.format(saved, g.currency)} ${t("з", "of")} ${Money.format(g.target, g.currency)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                if (acc != null) {
                    Text(
                        t("Прогрес — це залишок рахунку «${acc.name}». Поповнюйте його переказом.", "Progress is the balance of “${acc.name}”. Top it up with a transfer."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MoneyField(text, { text = it }, t("Сума", "Amount"), g.currency)
                }
            }
        },
        confirmButton = {
            if (acc != null) {
                TextButton(onClick = { onDismiss(); app.push(Route.Edit(type = TxType.TRANSFER, toAccount = acc.id, account = d.accounts.firstOrNull { it.id != acc.id && !it.archived }?.id)) }) {
                    Text(t("Переказати", "Transfer"))
                }
            } else {
                Row {
                    TextButton(onClick = take@{
                        val v = Money.parse(text) ?: return@take
                        if (v > 0) { Store.contribute(g.id, -v); onDismiss() }
                    }) { Text(t("Зняти", "Withdraw")) }
                    TextButton(onClick = add@{
                        val v = Money.parse(text) ?: return@add
                        if (v > 0) {
                            Store.contribute(g.id, v)
                            onDismiss()
                            if (saved < g.target && saved + v >= g.target) app.toast(t("Ціль «${g.name}» досягнуто!", "Goal “${g.name}” reached!"))
                        }
                    }) { Text(t("Додати", "Add")) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onEdit) { Text(t("Змінити", "Edit")) } },
    )
}

// ───────── редагування бюджету ─────────

@Composable
fun BudgetEditScreen(d: AppData, r: Route.BudgetEdit) {
    val app = LocalApp.current
    val s = d.settings
    val existing = remember { d.budgets.firstOrNull { it.id == r.id } }
    var category by remember { mutableStateOf(existing?.category ?: r.category) }
    var text by remember { mutableStateOf(existing?.let { Money.plain(it.limit) } ?: "") }
    var err by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val p = Calc.current(d)
    val avg = remember(category) { Calc.average(d, p, category) }
    SubScreen(
        if (existing == null) t("Новий бюджет", "New budget") else t("Бюджет", "Budget"),
        actions = { if (existing != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, t("Видалити", "Delete")) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                MoneyField(text, { text = it; err = false }, t("Ліміт на місяць", "Monthly limit"), s.base, error = err)
                if (avg > 0) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { text = Money.plain(avg) }) {
                        Text(t("У середньому витрачаєте ${Money.format(avg, s.base)}", "You usually spend ${Money.format(avg, s.base)}"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(t("На що", "For"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                val opts = listOf<String?>(null) + d.categories.filter { it.type == TxType.EXPENSE && !it.archived }.map { it.id }
                for (id in opts) {
                    val c = d.category(id)
                    val taken = d.budgets.any { it.category == id && it.id != existing?.id }
                    Row(
                        Modifier.fillMaxWidth().clickable { category = id }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = category == id, onClick = { category = id })
                        Spacer(Modifier.width(8.dp))
                        if (id == null) Badge(Icons.Rounded.PieChart, MaterialTheme.colorScheme.primary, 36.dp) else CatBadge(c?.icon, c?.color ?: 0xFF757575, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (id == null) t("Усі витрати (загальний)", "All spending (overall)") else d.catName(id))
                            if (taken) Text(t("бюджет уже є — буде замінено", "already has a budget — will be replaced"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = save@{
                        val v = Money.parse(text)
                        if (v == null || v <= 0) { err = true; return@save }
                        Store.saveBudget(Budget(existing?.id ?: Store.newId(), category, v))
                        app.pop()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(t("Зберегти", "Save")) }
            }
        }
    }
    if (confirm && existing != null) Confirm(
        t("Видалити бюджет?", "Delete budget?"), t("Операції залишаться.", "Transactions stay."), t("Видалити", "Delete"),
        onConfirm = { Store.deleteBudget(existing.id); app.pop() }, onDismiss = { confirm = false },
    )
}

// ───────── редагування цілі ─────────

@Composable
fun GoalEditScreen(d: AppData, r: Route.GoalEdit) {
    val app = LocalApp.current
    val s = d.settings
    val existing = remember { d.goals.firstOrNull { it.id == r.id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.let { Money.plain(it.target) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: s.base) }
    var deadline by remember { mutableStateOf(existing?.deadline?.let { LocalDate.parse(it) }) }
    var icon by remember { mutableStateOf(existing?.icon ?: "savings") }
    var color by remember { mutableStateOf(existing?.color ?: PALETTE[8]) }
    var account by remember { mutableStateOf(existing?.account) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf(false) }
    val currencies = (listOf(s.base) + d.accounts.map { it.currency } + listOf("UAH", "USD", "EUR")).distinct()

    SubScreen(
        if (existing == null) t("Нова ціль", "New goal") else t("Ціль", "Goal"),
        actions = { if (existing != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, t("Видалити", "Delete")) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(catIcon(icon), tint(color), 56.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        name, { name = it.take(40); err = false }, label = { Text(t("Назва", "Name")) },
                        placeholder = { Text(t("Відпустка, ноутбук…", "Trip, laptop…")) },
                        singleLine = true, isError = err && name.isBlank(), modifier = Modifier.weight(1f),
                    )
                }
                MoneyField(target, { target = it; err = false }, t("Скільки потрібно", "Target amount"), currency, error = err && Money.parse(target) == null)
                Chips(currencies, currency, { "${Money.symbol(it)} $it" }, { currency = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = true }) {
                        Text(deadline?.let { t("До ", "By ") + L.date(it, true) } ?: t("Без дедлайну", "No deadline"))
                    }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text(t("Прибрати", "Clear")) }
                }
                Text(t("Де лежать гроші", "Where the money is"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Chips(
                    listOf<String?>(null) + d.accounts.filter { !it.archived }.map { it.id }, account,
                    { id -> d.account(id)?.name ?: t("Відкладаю вручну", "Track manually") }, { account = it },
                )
                Text(
                    if (account == null) t("Додавайте суми в цілі вручну.", "Add amounts to the goal by hand.")
                    else t("Прогрес = залишок цього рахунку (зручно для «банки» чи депозиту).", "Progress = this account's balance (handy for a savings jar or deposit)."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(t("Іконка", "Icon"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconPicker(icon, color) { icon = it }
                Text(t("Колір", "Colour"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorPicker(color) { color = it }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = save@{
                        val v = Money.parse(target)
                        if (name.isBlank() || v == null || v <= 0) { err = true; return@save }
                        Store.saveGoal(
                            (existing ?: Goal(Store.newId(), name, v)).copy(
                                name = name.trim(), target = v, currency = currency, deadline = deadline?.toString(),
                                icon = icon, color = color, account = account,
                            ),
                        )
                        app.pop()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(t("Зберегти", "Save")) }
            }
        }
    }
    if (picking) DatePick(deadline ?: LocalDate.now().plusMonths(6), { deadline = it }, { picking = false })
    if (confirm && existing != null) Confirm(
        t("Видалити ціль?", "Delete goal?"), t("Рахунки й операції не зміняться.", "Accounts and transactions won't change."), t("Видалити", "Delete"),
        onConfirm = { Store.deleteGoal(existing.id); app.pop() }, onDismiss = { confirm = false },
    )
}
