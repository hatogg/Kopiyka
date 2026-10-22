@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package ua.x44to.kopiyka.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Every
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.Recurring
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.Tx
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate

private const val OPS = "+−×÷"

/** Натискання клавіші калькулятора: не дає ввести «1,2,3» чи «++», максимум 2 знаки після коми. */
fun keypadPress(cur: String, k: Char): String {
    val lastNum = cur.takeLastWhile { it !in OPS }
    return when {
        k == '⌫' -> cur.dropLast(1)
        k in OPS -> when {
            cur.isEmpty() -> cur
            cur.last() in OPS || cur.last() == ',' -> cur.dropLast(1) + k
            else -> cur + k
        }
        k == ',' -> when {
            lastNum.contains(',') -> cur
            lastNum.isEmpty() -> cur + "0,"
            else -> "$cur,"
        }
        else -> when {
            lastNum.contains(',') && lastNum.substringAfter(',').length >= 2 -> cur
            lastNum == "0" -> cur.dropLast(1) + k
            lastNum.replace(",", "").length >= 9 -> cur
            else -> cur + k
        }
    }
}

/** «12500+300,5» → «12 500 + 300,5» для показу. */
fun prettyExpr(expr: String): String {
    if (expr.isEmpty()) return "0"
    val sb = StringBuilder()
    var num = StringBuilder()
    fun flush() {
        if (num.isEmpty()) return
        val s = num.toString()
        val whole = s.substringBefore(',')
        val g = whole.reversed().chunked(3).joinToString(if (L.ukr) " " else ",").reversed()
        sb.append(g)
        if (s.contains(',')) sb.append(if (L.ukr) "," else ".").append(s.substringAfter(','))
        num = StringBuilder()
    }
    for (c in expr) {
        if (c in OPS) { flush(); sb.append(' ').append(c).append(' ') } else num.append(c)
    }
    flush()
    return sb.toString()
}

private fun toKeypad(minor: Long) = Money.plain(minor).replace('.', ',')

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = i == selected, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(o, maxLines = 1) }
        }
    }
}

@Composable
fun TxEditor(d: AppData, r: Route.Edit) {
    val app = LocalApp.current
    val s = d.settings
    val existing = remember { r.txId?.let { id -> d.txs.firstOrNull { it.id == id } } }
    val accounts = d.accounts.filter { !it.archived || it.id == existing?.account || it.id == existing?.toAccount }
    val lastAcc = remember { d.txs.maxByOrNull { it.created }?.account?.takeIf { id -> accounts.any { it.id == id } } }

    var type by remember { mutableStateOf(existing?.type ?: r.type) }
    var expr by remember { mutableStateOf(existing?.let { toKeypad(it.amount) } ?: "") }
    var toExpr by remember { mutableStateOf(existing?.toAmount?.let { toKeypad(it) } ?: "") }
    var editTo by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(existing?.account ?: r.account ?: lastAcc ?: accounts.firstOrNull()?.id) }
    var toAccount by remember { mutableStateOf(existing?.toAccount ?: r.toAccount ?: accounts.firstOrNull { it.id != account }?.id) }
    var category by remember { mutableStateOf(existing?.category) }
    var date by remember { mutableStateOf(existing?.let { LocalDate.parse(it.date) } ?: LocalDate.now()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var repeat by remember { mutableStateOf(false) }
    var every by remember { mutableStateOf(Every.MONTH) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val cur = d.curOf(account)
    val toCur = d.curOf(toAccount)
    val crossCurrency = type == TxType.TRANSFER && toAccount != null && cur != toCur
    val amount = Money.eval(expr)
    // категорії: спершу ті, якими користуються найчастіше
    val usage = remember(d.txs.size) { d.txs.groupingBy { it.category }.eachCount() }
    val cats = d.categories.filter { it.type == type && (!it.archived || it.id == category) }
        .sortedByDescending { usage[it.id] ?: 0 }

    fun save() {
        val a = Money.eval(expr)
        if (a == null || a <= 0) { error = t("Введіть суму", "Enter an amount"); editTo = false; return }
        val acc = d.account(account) ?: run { error = t("Оберіть рахунок", "Pick an account"); return }
        if (type == TxType.TRANSFER) {
            if (toAccount == null || toAccount == account) { error = t("Оберіть інший рахунок для зарахування", "Pick a different destination account"); return }
        } else if (category == null) {
            error = t("Оберіть категорію", "Pick a category"); return
        }
        val toAmount = if (crossCurrency) Money.eval(toExpr)?.takeIf { it > 0 } ?: Money.convert(a, acc.currency, toCur, s) else null
        val recId = if (repeat && existing == null) Store.newId() else existing?.recurring
        val tx = Tx(
            id = existing?.id ?: Store.newId(), type = type, amount = a, account = acc.id,
            toAccount = if (type == TxType.TRANSFER) toAccount else null, toAmount = toAmount,
            category = if (type == TxType.TRANSFER) null else category, date = date.toString(), note = note.trim(),
            recurring = recId, created = existing?.created ?: System.currentTimeMillis(),
        )
        Store.saveTx(tx)
        if (repeat && existing == null && recId != null) {
            Store.saveRecurring(
                Recurring(
                    id = recId, type = type, amount = a, account = acc.id, toAccount = tx.toAccount, category = tx.category,
                    note = tx.note, every = every, interval = 1, start = date.toString(), n = 1,
                ),
            )
        }
        app.pop()
        app.toast(if (existing == null) t("Збережено", "Saved") else t("Зміни збережено", "Changes saved"))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.pop() }) { Icon(Icons.Rounded.Close, t("Закрити", "Close")) }
            Text(
                if (existing == null) t("Нова операція", "New transaction") else t("Редагування", "Edit transaction"),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, t("Видалити", "Delete")) }
        }
        Segmented(
            listOf(t("Витрата", "Expense"), t("Дохід", "Income"), t("Переказ", "Transfer")),
            type.ordinal,
            { i ->
                val nt = TxType.entries[i]
                if (nt != type) {
                    type = nt
                    category = null
                    editTo = false
                    error = null
                    if (nt == TxType.TRANSFER && (toAccount == null || toAccount == account)) toAccount = accounts.firstOrNull { it.id != account }?.id
                }
            },
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // сума
        val color = when (type) {
            TxType.INCOME -> incomeColor
            TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
            TxType.TRANSFER -> MaterialTheme.colorScheme.primary
        }
        AmountDisplay(
            expr, cur, color, active = !editTo || !crossCurrency,
            sign = when (type) { TxType.EXPENSE -> "−"; TxType.INCOME -> "+"; else -> "" },
            onClick = { editTo = false },
        )
        if (crossCurrency) {
            val auto = amount?.let { Money.convert(it, cur, toCur, s) } ?: 0
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (editTo) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { editTo = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("Зараховано", "Received"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    if (toExpr.isEmpty()) "≈ " + Money.format(auto, toCur) else prettyExpr(toExpr) + " " + Money.symbol(toCur),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (toExpr.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (error != null) Text(
            error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = TextAlign.Center,
        )

        // усе інше — гортається, якщо екран невеликий
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (type != TxType.TRANSFER) {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(184.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(cats, key = { it.id }) { c ->
                        val sel = c.id == category
                        CatTile(c.name, sel, { CatBadgeSel(c.icon, c.color, sel) }) { category = c.id; error = null }
                    }
                    item(key = "+new") {
                        CatTile(t("Нова", "New"), false, { Badge(Icons.Rounded.Add, MaterialTheme.colorScheme.onSurfaceVariant, 44.dp) }) {
                            app.push(Route.CategoryEdit(null, type))
                        }
                    }
                }
                Label(t("Рахунок", "Account"))
                AccountChips(accounts, account, s.base) { account = it; error = null }
            } else {
                Label(t("З рахунку", "From"))
                AccountChips(accounts, account, s.base) { account = it; error = null; if (toAccount == it) toAccount = accounts.firstOrNull { a -> a.id != it }?.id }
                Label(t("На рахунок", "To"))
                AccountChips(accounts.filter { it.id != account }, toAccount, s.base) { toAccount = it; error = null; toExpr = "" }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { picking = true }, label = { Text(L.dayTitle(date)) },
                    leadingIcon = { Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp)) },
                )
                val today = LocalDate.now()
                if (date != today) FilterChip(false, { date = today }, { Text(t("Сьогодні", "Today")) })
                if (date != today.minusDays(1)) FilterChip(false, { date = today.minusDays(1) }, { Text(t("Вчора", "Yesterday")) })
                if (existing == null) FilterChip(
                    repeat, { repeat = !repeat }, { Text(t("Повторювати", "Repeat")) },
                    leadingIcon = { Icon(Icons.Rounded.Repeat, null, Modifier.size(18.dp)) },
                )
            }
            if (repeat) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Chips(Every.entries.toList(), every, { L.every(it, 1) }, { every = it })
                }
                Text(
                    t("Наступні платежі додаватимуться самі — змінити можна в «Регулярних платежах».", "Next payments will be added automatically — change it in “Recurring payments”."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(200) },
                placeholder = { Text(t("Нотатка", "Note")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Keypad(
            onKey = { k ->
                error = null
                if (editTo && crossCurrency) toExpr = keypadPress(toExpr, k) else expr = keypadPress(expr, k)
            },
            onClear = { if (editTo && crossCurrency) toExpr = "" else expr = "" },
            onSave = { save() },
        )
    }

    if (picking) DatePick(date, { date = it }, { picking = false })
    if (confirmDelete && existing != null) Confirm(
        t("Видалити операцію?", "Delete transaction?"),
        t("Її можна буде повернути одразу після видалення.", "You can undo right after deleting."),
        t("Видалити", "Delete"),
        onConfirm = {
            val removed = Store.deleteTx(existing.id)
            app.pop()
            if (removed != null) app.toast(t("Операцію видалено", "Transaction deleted")) { Store.restoreTx(removed) }
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun AmountDisplay(expr: String, cur: String, color: Color, active: Boolean, sign: String, onClick: () -> Unit) {
    val result = Money.eval(expr)
    val hasOps = expr.any { it in OPS }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        val text = (if (expr.isEmpty() || hasOps) "" else sign) + prettyExpr(expr)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = if (text.length > 12) 34.sp else 46.sp, fontWeight = FontWeight.SemiBold,
                color = if (expr.isEmpty()) MaterialTheme.colorScheme.outline else if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(Money.symbol(cur), fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (hasOps) Text(
            if (result != null) "= " + Money.format(result, cur) else "…",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun CatTile(name: String, selected: Boolean, badge: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        badge()
        Spacer(Modifier.height(4.dp))
        Text(
            name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 13.sp,
        )
    }
}

@Composable
private fun CatBadgeSel(icon: String, color: Long, selected: Boolean) {
    val c = tint(color)
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (selected) c else c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (selected) Icons.Rounded.Check else catIcon(icon), null, tint = if (selected) onColor(c) else c, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AccountChips(accounts: List<ua.x44to.kopiyka.data.Account>, selected: String?, base: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (a in accounts) {
            FilterChip(
                selected = a.id == selected, onClick = { onPick(a.id) },
                label = { Text(if (a.currency == base) a.name else "${a.name} · ${Money.symbol(a.currency)}") },
                leadingIcon = { Icon(accIcon(a.type), null, Modifier.size(18.dp), tint = tint(a.color)) },
            )
        }
    }
}

/** Клавіатура-калькулятор: цифри, кома, + − × ÷, стерти (довге натискання — усе) і «Зберегти». */
@Composable
private fun Keypad(onKey: (Char) -> Unit, onClear: () -> Unit, onSave: () -> Unit) {
    val rows = listOf("789÷", "456×", "123−", ",0⌫+")
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in row) {
                    val op = k in OPS
                    Box(
                        Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (op) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .combinedClickable(onClick = { onKey(k) }, onLongClick = if (k == '⌫') onClear else null),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '⌫') Icon(Icons.AutoMirrored.Rounded.Backspace, t("Стерти", "Delete"), tint = MaterialTheme.colorScheme.onSurface)
                        else Text(
                            if (k == ',' && !L.ukr) "." else k.toString(), fontSize = 22.sp,
                            color = if (op) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Check, null)
            Spacer(Modifier.width(8.dp))
            Text(t("Зберегти", "Save"), style = MaterialTheme.typography.titleMedium)
        }
    }
}
