package ua.x44to.kopiyka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Calc
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.Tx
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate

@Composable
fun HomeScreen(d: AppData, padding: PaddingValues) {
    val app = LocalApp.current
    val s = d.settings
    val hide = s.hideAmounts
    val today = LocalDate.now()
    val bal = remember(d) { Calc.balances(d) }
    val total = remember(d) { Calc.total(d, bal) }
    val p = remember(d.settings.monthStart, today) { Calc.current(d) }
    val sums = remember(d, p) { Calc.sums(d, p) }
    val overall = d.budgets.firstOrNull { it.category == null }
    val bs = remember(d, p) { overall?.let { Calc.budgetState(d, it, p) } }
    val upcoming = remember(d) { Calc.upcoming(d, 7).distinctBy { it.first.id }.take(4) }
    val top = remember(d, p) { Calc.byCategory(d, p, TxType.EXPENSE).take(4) }
    val recent = remember(d) { d.txs.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.created }).take(5) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${L.date(today, false)}, ${L.weekday(today.dayOfWeek)}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(t("Копійка", "Kopiyka"), style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { Store.settings { it.copy(hideAmounts = !it.hideAmounts) } }) {
                    Icon(if (hide) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, t("Приховати суми", "Hide amounts"))
                }
            }
        }

        // ── загальний баланс ──
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(t("Загальний баланс", "Total balance"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(
                        Money.format(total, s.base, hide = hide), style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Flow(Modifier.weight(1f), Icons.Rounded.ArrowDownward, t("Доходи", "Income"), Money.format(sums.income, s.base, sign = true, hide = hide), true)
                        Flow(Modifier.weight(1f), Icons.Rounded.ArrowUpward, t("Витрати", "Expenses"), Money.format(-sums.expense, s.base, hide = hide), false)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        t("За ", "For ") + p.title.lowercase(L.locale).let { if (L.ukr) it else p.title },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // ── рахунки ──
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(d.accounts.filter { !it.archived }, key = { it.id }) { a ->
                    Card(
                        onClick = { app.openTxs(TxFilter(account = a.id)) },
                        modifier = Modifier.width(156.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Badge(accIcon(a.type), tint(a.color), 36.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Money.format(bal[a.id] ?: 0, a.currency, hide = hide), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            // валютна готівка: еквівалент в основній валюті за поточним курсом
                            if (a.currency != d.settings.base && (bal[a.id] ?: 0) != 0L) Text(
                                "≈ " + Money.format(Money.convert(bal[a.id] ?: 0, a.currency, d.settings.base, d.settings), d.settings.base, hide = hide),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                    }
                }
                item {
                    Card(
                        onClick = { app.push(Route.Accounts) },
                        modifier = Modifier.width(112.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Badge(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.primary, 36.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(t("Рахунки", "Accounts"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(t("Керувати", "Manage"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // ── денний ліміт ──
        item {
            if (bs != null) {
                SoftCard(onClick = { app.tab = Tabs.BUDGETS; app.goalsTab = false }) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val monthOver = bs.left < 0
                                val over = monthOver || bs.leftToday < 0
                                Text(
                                    when {
                                        monthOver -> t("Бюджет місяця перевищено на", "Monthly budget exceeded by")
                                        over -> t("Сьогодні понад денний ліміт на", "Over today's limit by")
                                        else -> t("Сьогодні можна ще", "Left for today")
                                    },
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    Money.format(if (monthOver) -bs.left else if (over) -bs.leftToday else bs.leftToday, s.base, hide = hide),
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Badge(Icons.Rounded.Savings, MaterialTheme.colorScheme.primary, 48.dp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Bar(bs.ratio, budgetColor(bs.ratio))
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text(
                                t("${Money.format(bs.spent, s.base, hide = hide)} з ${Money.format(bs.limit, s.base, hide = hide)}", "${Money.format(bs.spent, s.base, hide = hide)} of ${Money.format(bs.limit, s.base, hide = hide)}"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                            )
                            Text(
                                t("ще ", "") + L.days(bs.daysLeft) + t("", " left") + " · " + Money.format(bs.perDay, s.base, hide = hide) + t("/день", "/day"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                SoftCard {
                    Column(Modifier.padding(20.dp)) {
                        Text(t("Скільки можна витрачати щодня?", "How much can you spend a day?"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            t(
                                "Задайте бюджет на місяць — Копійка порахує денний ліміт і попередить, коли наближаєтесь до межі.",
                                "Set a monthly budget — Kopiyka will work out a daily limit and warn you before you overspend.",
                            ),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { app.push(Route.BudgetEdit(null)) }) { Text(t("Задати бюджет", "Set a budget")) }
                    }
                }
            }
        }

        // ── найближчі платежі ──
        if (upcoming.isNotEmpty()) {
            item { SectionTitle(t("Найближчі платежі", "Upcoming payments"), t("Усі", "All")) { app.push(Route.Recurrings) } }
            item {
                SoftCard {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        upcoming.forEachIndexed { i, (r, date) ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val due = !date.isAfter(today)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (r.type == TxType.TRANSFER) Badge(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.secondary)
                                else d.category(r.category).let { CatBadge(it?.icon, it?.color ?: 0xFF757575) }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.note.ifBlank { d.catName(r.category) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (date.isBefore(today)) t("Прострочено · ", "Overdue · ") else "") + L.dayTitle(date) +
                                            if (r.auto) t(" · автоматично", " · automatic") else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (date.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (due && !r.auto) {
                                    FilledTonalButton(onClick = {
                                        Store.payRecurring(r.id)
                                        app.toast(t("Записано", "Recorded"))
                                    }) { Text(signed(r.type, r.amount, d.curOf(r.account), hide)) }
                                } else {
                                    Text(signed(r.type, r.amount, d.curOf(r.account), hide), fontWeight = FontWeight.SemiBold, color = amountColor(r.type))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── куди йдуть гроші ──
        if (top.isNotEmpty()) {
            item { SectionTitle(t("Куди йдуть гроші", "Where the money goes"), t("Статистика", "Stats")) { app.periodStart = null; app.tab = Tabs.STATS } }
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val maxV = top.first().second.coerceAtLeast(1)
                        for ((cat, sum) in top) {
                            val c = d.category(cat)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CatBadge(c?.icon, c?.color ?: 0xFF757575, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(d.catName(cat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(Money.format(sum, s.base, hide = hide), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Bar(sum.toFloat() / maxV, tint(c?.color ?: 0xFF757575), height = 6.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── останні операції ──
        item { SectionTitle(t("Останні операції", "Recent"), if (recent.isNotEmpty()) t("Усі", "All") else null) { app.openTxs(TxFilter()) } }
        item {
            if (recent.isEmpty()) {
                Empty(
                    Icons.Rounded.Add, t("Поки порожньо", "Nothing here yet"),
                    t("Натисніть «Додати», щоб записати першу витрату чи дохід.", "Tap “Add” to record your first expense or income."),
                )
            } else {
                SoftCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        for (tx in recent) TxRow(d, tx, showDate = true) { app.push(Route.Edit(txId = tx.id)) }
                    }
                }
            }
        }
    }
}

/** Плашка «↓ Доходи» / «↑ Витрати» усередині картки балансу. */
@Composable
private fun Flow(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, income: Boolean) {
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(on.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (income) incomeColor.copy(alpha = 0.2f) else on.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (income) incomeColor else on, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.75f))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
