@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ua.x44to.kopiyka.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
fun TxListScreen(d: AppData, padding: PaddingValues) {
    val app = LocalApp.current
    val f = app.filter
    val s = d.settings
    val hide = s.hideAmounts
    val p = app.period(s.monthStart)
    var searching by remember { mutableStateOf(f.query.isNotEmpty()) }
    val searchAll = f.query.isNotBlank()

    val list = remember(d, f, p) {
        val q = f.query.trim().lowercase()
        d.txs.asSequence().filter { tx ->
            (searchAll || LocalDate.parse(tx.date) in p) &&
                (f.type == null || tx.type == f.type) &&
                (f.account == null || tx.account == f.account || tx.toAccount == f.account) &&
                (f.category == null || tx.category == f.category) &&
                (q.isEmpty() || tx.note.lowercase().contains(q) || d.catName(tx.category).lowercase().contains(q) ||
                    Money.plain(tx.amount).contains(q))
        }.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.created }).toList()
    }
    val groups = remember(list) { list.groupBy { it.date } }
    var inc = 0L
    var exp = 0L
    for (tx in list) when (tx.type) {
        TxType.INCOME -> inc += Calc.base(d, tx)
        TxType.EXPENSE -> exp += Calc.base(d, tx)
        else -> {}
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Операції", "Transactions"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (searching) app.filter = f.copy(query = "")
                searching = !searching
            }) { Icon(if (searching) Icons.Rounded.SearchOff else Icons.Rounded.Search, t("Пошук", "Search")) }
        }
        if (searching) {
            OutlinedTextField(
                value = f.query, onValueChange = { app.filter = f.copy(query = it) },
                placeholder = { Text(t("Нотатка, категорія або сума", "Note, category or amount")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (f.query.isNotEmpty()) ({ IconButton(onClick = { app.filter = f.copy(query = "") }) { Icon(Icons.Rounded.Close, null) } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (!searchAll) PeriodBar(app, s.monthStart, Modifier.padding(horizontal = 4.dp))
        // фільтри
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((tp, label) in listOf(null to t("Усі", "All"), TxType.EXPENSE to t("Витрати", "Expenses"), TxType.INCOME to t("Доходи", "Income"), TxType.TRANSFER to t("Перекази", "Transfers"))) {
                FilterChip(selected = f.type == tp, onClick = { app.filter = f.copy(type = tp) }, label = { Text(label) })
            }
            var accMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = f.account != null, onClick = { accMenu = true },
                    label = { Text(d.account(f.account)?.name ?: t("Рахунок", "Account")) },
                )
                DropdownMenu(expanded = accMenu, onDismissRequest = { accMenu = false }) {
                    DropdownMenuItem(text = { Text(t("Усі рахунки", "All accounts")) }, onClick = { app.filter = f.copy(account = null); accMenu = false })
                    for (a in d.accounts) DropdownMenuItem(
                        text = { Text(a.name) },
                        leadingIcon = { Icon(accIcon(a.type), null, tint = tint(a.color)) },
                        onClick = { app.filter = f.copy(account = a.id); accMenu = false },
                    )
                }
            }
            if (f.category != null) {
                InputChip(
                    selected = true, onClick = { app.filter = f.copy(category = null) },
                    label = { Text(d.catName(f.category)) },
                    trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)) },
                )
            }
        }
        // підсумок
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Stat(t("Доходи", "Income"), Money.format(inc, s.base, sign = true, hide = hide), Modifier.weight(1f), incomeColor, MaterialTheme.typography.titleSmall)
            Stat(t("Витрати", "Expenses"), Money.format(-exp, s.base, hide = hide), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Stat(t("Різниця", "Net"), Money.format(inc - exp, s.base, sign = true, hide = hide), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        }

        if (list.isEmpty()) {
            Empty(
                if (f.active) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                if (f.active) t("Нічого не знайдено", "Nothing found") else t("Операцій немає", "No transactions"),
                if (f.active) t("Спробуйте інший фільтр або період.", "Try another filter or period.")
                else t("За цей період ще нічого не записано.", "Nothing recorded for this period yet."),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
            ) {
                for ((date, txs) in groups) {
                    stickyHeader(key = "h$date") {
                        val day = LocalDate.parse(date)
                        var dayNet = 0L
                        for (tx in txs) when (tx.type) {
                            TxType.INCOME -> dayNet += Calc.base(d, tx)
                            TxType.EXPENSE -> dayNet -= Calc.base(d, tx)
                            else -> {}
                        }
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (searchAll) L.date(day, true) else L.dayTitle(day), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                            )
                            Text(
                                Money.format(dayNet, s.base, sign = true, hide = hide), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(txs, key = { it.id }) { tx -> SwipeTx(d, tx, app) }
                }
            }
        }
    }
}

/** Рядок, який можна змахнути вліво, щоб видалити (зі «Скасувати»). */
@Composable
private fun SwipeTx(d: AppData, tx: Tx, app: AppState) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) {
                val removed = Store.deleteTx(tx.id)
                if (removed != null) app.toast(t("Операцію видалено", "Transaction deleted")) { Store.restoreTx(removed) }
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
            ) {
                Text(t("Видалити", "Delete"), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            TxRow(d, tx) { app.push(Route.Edit(txId = tx.id)) }
        }
    }
}
