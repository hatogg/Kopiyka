package ua.x44to.kopiyka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Calc
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun StatsScreen(d: AppData, padding: PaddingValues) {
    val app = LocalApp.current
    val s = d.settings
    val hide = s.hideAmounts
    val p = app.period(s.monthStart)
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val type = if (mode == 0) TxType.EXPENSE else TxType.INCOME
    val cats = remember(d, p, type) { Calc.byCategory(d, p, type) }
    val total = cats.sumOf { it.second }
    val series = remember(d, p) { Calc.series(d, p, 6) }
    val cur = remember(d, p) { Calc.cumulative(d, p) }
    val prevP = p.prev(s.monthStart)
    val prev = remember(d, prevP) { Calc.cumulative(d, prevP) }
    val today = LocalDate.now()
    val upTo = when {
        today.isAfter(p.end) -> p.days
        today.isBefore(p.start) -> 0
        else -> ChronoUnit.DAYS.between(p.start, today).toInt() + 1
    }
    val overall = d.budgets.firstOrNull { it.category == null }
    val sums = remember(d, p) { Calc.sums(d, p) }
    val biggest = remember(d, p) {
        d.txs.filter { it.type == TxType.EXPENSE && LocalDate.parse(it.date) in p }.maxByOrNull { Calc.base(d, it) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
        item {
            Text(t("Статистика", "Statistics"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
            PeriodBar(app, s.monthStart, Modifier.padding(horizontal = 4.dp))
            Segmented(listOf(t("Витрати", "Expenses"), t("Доходи", "Income")), mode, { mode = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        // ── кільце категорій ──
        item {
            SoftCard {
                if (cats.isEmpty()) {
                    Empty(
                        Icons.Rounded.BarChart,
                        if (mode == 0) t("Витрат немає", "No expenses") else t("Доходів немає", "No income"),
                        t("За цей період нічого не записано.", "Nothing recorded for this period."),
                    )
                } else {
                    Donut(
                        cats.map { (id, v) -> tint(d.category(id)?.color ?: 0xFF757575) to v },
                        Modifier.fillMaxWidth().height(220.dp).padding(top = 20.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (mode == 0) t("Витрачено", "Spent") else t("Отримано", "Received"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(total, s.base, hide = hide), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(L.ops(sumsCount(d, p, type)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(bottom = 8.dp)) {
                        for ((id, v) in cats) {
                            val c = d.category(id)
                            val pct = if (total > 0) v * 100f / total else 0f
                            Row(
                                Modifier.fillMaxWidth().clickable { app.openTxs(TxFilter(type = type, category = id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CatBadge(c?.icon, c?.color ?: 0xFF757575, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(d.catName(id), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(
                                            if (pct >= 10 || pct == 0f) "${pct.toInt()}%" else "%.1f%%".format(L.locale, pct),
                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(Money.format(v, s.base, hide = hide), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Bar(pct / 100f, tint(c?.color ?: 0xFF757575), height = 5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
        // ── підсумки ──
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val days = if (upTo > 0) upTo else p.days
                Tile(t("У середньому за день", "Average per day"), Money.format(sums.expense / days.coerceAtLeast(1), s.base, hide = hide), Modifier.weight(1f))
                Tile(
                    t("Заощаджено", "Saved"),
                    if (sums.income > 0) "${(sums.net * 100 / sums.income).coerceAtLeast(-999L)}%" else "—",
                    Modifier.weight(1f),
                    sub = Money.format(sums.net, s.base, sign = true, hide = hide),
                )
            }
            if (biggest != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Tile(
                    t("Найбільша витрата", "Biggest expense"),
                    Money.format(biggest.amount, d.curOf(biggest.account), hide = hide),
                    Modifier.weight(1f),
                    sub = listOf(d.catName(biggest.category), biggest.note, L.shortDate(LocalDate.parse(biggest.date))).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { app.push(Route.Edit(txId = biggest.id)) },
                )
            }
        }
        // ── доходи й витрати по місяцях ──
        item {
            SectionTitle(t("Доходи й витрати", "Income vs expenses"))
            SoftCard {
                Column(Modifier.padding(16.dp)) {
                    IncomeExpenseBars(
                        series.map { L.monthShort(it.first.end.monthValue) },
                        series.map { it.second }, series.map { it.third },
                        incomeColor, MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(incomeColor, t("Доходи", "Income"))
                        Legend(MaterialTheme.colorScheme.primary, t("Витрати", "Expenses"))
                    }
                    Spacer(Modifier.height(12.dp))
                    val avgExp = series.dropLast(1).map { it.third }.filter { it > 0 }.let { if (it.isEmpty()) 0L else it.sum() / it.size }
                    val nowExp = series.last().third
                    if (avgExp > 0) {
                        val diff = (nowExp - avgExp) * 100 / avgExp
                        Text(
                            when {
                                diff > 5 -> t("Цього місяця витрати на $diff% більші, ніж зазвичай", "Spending is $diff% higher than usual")
                                diff < -5 -> t("Цього місяця витрати на ${-diff}% менші, ніж зазвичай", "Spending is ${-diff}% lower than usual")
                                else -> t("Витрати приблизно як зазвичай", "Spending is about usual")
                            } + t(" (у середньому ${Money.format(avgExp, s.base, hide = hide)})", " (average ${Money.format(avgExp, s.base, hide = hide)})"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // ── темп витрат ──
        item {
            SectionTitle(t("Темп витрат", "Spending pace"))
            SoftCard {
                Column(Modifier.padding(16.dp)) {
                    val nowV = if (upTo > 0) cur[upTo - 1] else 0L
                    val prevV = if (upTo > 0 && prev.isNotEmpty()) prev[(upTo - 1).coerceAtMost(prev.size - 1)] else 0L
                    Row {
                        Stat(t("Зараз", "Now"), Money.format(nowV, s.base, hide = hide), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                        Stat(t("Минулого місяця на цей день", "Last month by this day"), Money.format(prevV, s.base, hide = hide), Modifier.weight(1.4f))
                    }
                    Spacer(Modifier.height(12.dp))
                    PaceChart(
                        cur, upTo, prev, overall?.limit,
                        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Legend(MaterialTheme.colorScheme.primary, t("Цей місяць", "This month"))
                        Legend(MaterialTheme.colorScheme.outline, t("Минулий", "Last month"), dashed = true)
                        if (overall != null) Legend(MaterialTheme.colorScheme.error, t("Бюджет", "Budget"), dashed = true)
                    }
                }
            }
        }
    }
}

private fun sumsCount(d: AppData, p: ua.x44to.kopiyka.data.Period, type: TxType) =
    d.txs.count { it.type == type && LocalDate.parse(it.date) in p }

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, sub: String? = null, onClick: (() -> Unit)? = null) {
    androidx.compose.material3.Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
