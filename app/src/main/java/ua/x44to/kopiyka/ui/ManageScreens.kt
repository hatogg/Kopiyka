@file:OptIn(ExperimentalLayoutApi::class)

package ua.x44to.kopiyka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.x44to.kopiyka.data.AccType
import ua.x44to.kopiyka.data.Account
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Calc
import ua.x44to.kopiyka.data.Category
import ua.x44to.kopiyka.data.Every
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.PALETTE
import ua.x44to.kopiyka.data.Recurring
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate

@Composable
private fun SaveBar(text: String = t("Зберегти", "Save"), onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp)) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(text) }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** «−12,5» → -1250; порожньо → 0. */
private fun parseSigned(text: String): Long? {
    val tt = text.trim()
    val neg = tt.startsWith("-") || tt.startsWith("−")
    val body = tt.trimStart('-', '−').trim()
    if (body.isEmpty()) return 0
    val v = Money.parse(body) ?: return null
    return if (neg) -v else v
}

// ───────── рахунки ─────────

@Composable
fun AccountsScreen(d: AppData) {
    val app = LocalApp.current
    val s = d.settings
    val hide = s.hideAmounts
    val bal = remember(d) { Calc.balances(d) }
    val total = remember(d) { Calc.total(d, bal) }
    val active = d.accounts.filter { !it.archived }
    val archived = d.accounts.filter { it.archived }
    SubScreen(
        t("Рахунки", "Accounts"),
        fab = {
            ExtendedFloatingActionButton(
                onClick = { app.push(Route.AccountEdit(null)) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(t("Рахунок", "Account")) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp)) {
            item {
                SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Text(t("Усього", "Total"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(Money.format(total, s.base, hide = hide), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                SoftCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        active.forEachIndexed { i, a ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            AccountRow(a, bal[a.id] ?: 0, hide, s) { app.push(Route.AccountEdit(a.id)) }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { app.push(Route.Edit(type = TxType.TRANSFER)) }, enabled = active.size >= 2) {
                        Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Переказ чи обмін", "Transfer or exchange"))
                    }
                    OutlinedButton(onClick = { app.push(Route.AccountEdit(null, cash = if (s.base == "USD") "EUR" else "USD")) }) {
                        Icon(Icons.Rounded.Payments, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Валютна готівка", "Foreign cash"))
                    }
                }
            }
            if (archived.isNotEmpty()) {
                item { SectionTitle(t("Архів", "Archived")) }
                item {
                    SoftCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            for (a in archived) AccountRow(a, bal[a.id] ?: 0, hide, s) { app.push(Route.AccountEdit(a.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(a: Account, balance: Long, hide: Boolean, s: ua.x44to.kopiyka.data.Settings, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Badge(accIcon(a.type), tint(a.color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(L.accType(a.type), a.currency, if (!a.inTotal) t("не в загальному балансі", "not in total") else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.format(balance, a.currency, hide = hide), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            // валюта: скільки це в основній за поточним курсом
            if (a.currency != s.base && balance != 0L) Text(
                "≈ " + Money.format(Money.convert(balance, a.currency, s.base, s), s.base, hide = hide),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AccountEditScreen(d: AppData, r: Route.AccountEdit) {
    val app = LocalApp.current
    val existing = remember { d.account(r.id) }
    val balNow = remember { existing?.let { Calc.balances(d)[it.id] } ?: 0L }
    val cashName = { c: String -> when (c) { "USD" -> t("Долари", "Dollars"); "EUR" -> t("Євро", "Euros"); "PLN" -> t("Злоті", "Zloty"); "GBP" -> t("Фунти", "Pounds"); else -> t("Готівка", "Cash") + " " + c } }
    var name by remember { mutableStateOf(existing?.name ?: r.cash?.let(cashName) ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: if (r.cash != null) AccType.CASH else AccType.CARD) }
    var currency by remember { mutableStateOf(existing?.currency ?: r.cash ?: d.settings.base) }
    var balText by remember { mutableStateOf(if (balNow == 0L && existing == null) "" else (if (balNow < 0) "-" else "") + Money.plain(kotlin.math.abs(balNow))) }
    var color by remember { mutableStateOf(existing?.color ?: PALETTE[(d.accounts.size * 5) % PALETTE.size]) }
    var inTotal by remember { mutableStateOf(existing?.inTotal ?: true) }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var err by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val used = remember { existing != null && Store.accountUsed(existing.id) }

    SubScreen(
        if (existing == null) t("Новий рахунок", "New account") else t("Рахунок", "Account"),
        actions = { if (existing != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, t("Видалити", "Delete")) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(accIcon(type), tint(color), 56.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        name, { name = it.take(30); err = false }, label = { Text(t("Назва", "Name")) },
                        placeholder = { Text(t("Монобанк, Приват, гаманець…", "Bank card, wallet…")) },
                        singleLine = true, isError = err && name.isBlank(), modifier = Modifier.weight(1f),
                    )
                }
                FieldLabel(t("Тип", "Type"))
                Chips(AccType.entries.toList(), type, { L.accType(it) }, { type = it }, icon = { accIcon(it) })
                FieldLabel(t("Валюта", "Currency"))
                Chips(Money.CURRENCIES.map { it.code }, currency, { "${Money.symbol(it)} $it" }, { c ->
                    if (existing == null && name == cashName(currency)) name = cashName(c)
                    currency = c
                })
                if (currency != d.settings.base) {
                    val s = d.settings
                    val one = Money.convert(10_000, currency, s.base, s)
                    Text(
                        "1 ${Money.symbol(currency)} = ${Money.format(one, s.base, cents = true)}" +
                            if (s.ratesAuto) " · " + ratesInfo(s) else " · " + t("курс вписано вручну", "manual rate"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (existing != null && used && currency != existing.currency) Text(
                    t("Суми вже записаних операцій не перераховуються — лише змінюється валюта.", "Existing amounts won't be converted — only the currency label changes."),
                    style = MaterialTheme.typography.bodySmall, color = warnColor,
                )
                OutlinedTextField(
                    balText, { v -> balText = v.filter { it.isDigit() || it in ",.-−" }; err = false },
                    label = { Text(if (existing == null) t("Скільки зараз на рахунку", "Current balance") else t("Поточний залишок", "Current balance")) },
                    supportingText = { Text(t("Для кредитки чи боргу — зі знаком мінус", "Use a minus for a credit card or debt")) },
                    suffix = { Text(Money.symbol(currency)) }, singleLine = true, isError = err && parseSigned(balText) == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldLabel(t("Колір", "Colour"))
                ColorPicker(color) { color = it }
                SwitchRow(t("Враховувати в загальному балансі", "Include in total balance"), inTotal) { inTotal = it }
                if (existing != null) SwitchRow(t("В архіві (сховати зі списків)", "Archived (hide from lists)"), archived) { archived = it }
            }
            SaveBar {
                val b = parseSigned(balText)
                if (name.isBlank() || b == null) { err = true; return@SaveBar }
                val base = existing ?: Account(Store.newId(), name.trim())
                // залишок задаємо через початковий: initial = бажаний − рух за всі операції
                val movement = balNow - (existing?.initial ?: 0L)
                Store.saveAccount(base.copy(name = name.trim(), type = type, currency = currency, initial = b - movement, color = color, inTotal = inTotal, archived = archived))
                app.pop()
            }
        }
    }
    if (confirm && existing != null) Confirm(
        if (used) t("Архівувати рахунок?", "Archive account?") else t("Видалити рахунок?", "Delete account?"),
        if (used) t("На рахунку є операції, тож він піде в архів — історія залишиться.", "The account has transactions, so it will be archived — history stays.")
        else t("Рахунок без операцій буде видалено.", "The account has no transactions and will be deleted."),
        if (used) t("Архівувати", "Archive") else t("Видалити", "Delete"),
        onConfirm = { Store.deleteAccount(existing.id); app.pop() }, onDismiss = { confirm = false },
    )
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

// ───────── категорії ─────────

@Composable
fun CategoriesScreen(d: AppData) {
    val app = LocalApp.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val type = if (tab == 0) TxType.EXPENSE else TxType.INCOME
    val counts = remember(d) { d.txs.groupingBy { it.category }.eachCount() }
    SubScreen(
        t("Категорії", "Categories"),
        fab = {
            ExtendedFloatingActionButton(
                onClick = { app.push(Route.CategoryEdit(null, type)) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(t("Категорія", "Category")) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            Segmented(listOf(t("Витрати", "Expenses"), t("Доходи", "Income")), tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 96.dp)) {
                items(d.categories.filter { it.type == type }, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { app.push(Route.CategoryEdit(c.id, c.type)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CatBadge(c.icon, c.color)
                        Spacer(Modifier.width(14.dp))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(L.ops(counts[c.id] ?: 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryEditScreen(d: AppData, r: Route.CategoryEdit) {
    val app = LocalApp.current
    val existing = remember { d.category(r.id) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: r.type) }
    var icon by remember { mutableStateOf(existing?.icon ?: "cart") }
    var color by remember { mutableStateOf(existing?.color ?: PALETTE[(d.categories.size * 7) % PALETTE.size]) }
    var err by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    SubScreen(
        if (existing == null) t("Нова категорія", "New category") else t("Категорія", "Category"),
        actions = { if (existing != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, t("Видалити", "Delete")) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(icon, color, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        name, { name = it.take(30); err = false }, label = { Text(t("Назва", "Name")) },
                        singleLine = true, isError = err, modifier = Modifier.weight(1f),
                    )
                }
                if (existing == null) Segmented(listOf(t("Витрата", "Expense"), t("Дохід", "Income")), if (type == TxType.INCOME) 1 else 0, { type = if (it == 1) TxType.INCOME else TxType.EXPENSE })
                FieldLabel(t("Іконка", "Icon"))
                IconPicker(icon, color) { icon = it }
                FieldLabel(t("Колір", "Colour"))
                ColorPicker(color) { color = it }
                Spacer(Modifier.height(8.dp))
            }
            SaveBar {
                if (name.isBlank()) { err = true; return@SaveBar }
                val c = (existing ?: Category(Store.newId(), name, type, icon, color)).copy(name = name.trim(), type = type, icon = icon, color = color)
                Store.saveCategory(c)
                if (existing == null) app.newCategory = c.id
                app.pop()
            }
        }
    }
    if (confirm && existing != null) Confirm(
        t("Видалити категорію?", "Delete category?"),
        t("Її операції залишаться, але стануть «Без категорії». Бюджет на неї буде видалено.", "Its transactions stay but become “Uncategorized”. Its budget will be removed."),
        t("Видалити", "Delete"),
        onConfirm = { Store.deleteCategory(existing.id); app.pop() }, onDismiss = { confirm = false },
    )
}

// ───────── регулярні платежі ─────────

@Composable
fun RecurringsScreen(d: AppData) {
    val app = LocalApp.current
    val s = d.settings
    val hide = s.hideAmounts
    val list = remember(d) { d.recurring.sortedWith(compareBy<Recurring> { !it.active }.thenBy { Calc.nextDate(it) ?: LocalDate.MAX }) }
    val monthlyOut = remember(d) { d.recurring.filter { it.active && it.type == TxType.EXPENSE }.sumOf { Calc.monthlyCost(d, it) } }
    val monthlyIn = remember(d) { d.recurring.filter { it.active && it.type == TxType.INCOME }.sumOf { Calc.monthlyCost(d, it) } }
    SubScreen(
        t("Регулярні платежі", "Recurring payments"),
        fab = {
            ExtendedFloatingActionButton(
                onClick = { app.push(Route.RecurringEdit(null)) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(t("Платіж", "Payment")) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp)) {
            if (list.isEmpty()) {
                item {
                    Empty(
                        Icons.Rounded.Repeat, t("Регулярних платежів немає", "No recurring payments"),
                        t(
                            "Додайте підписки, оренду, комуналку чи зарплату — Копійка запише їх сама або нагадає вчасно.",
                            "Add subscriptions, rent, utilities or salary — Kopiyka will record them for you or remind you on time.",
                        ),
                    )
                }
            } else {
                item {
                    SoftCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(20.dp)) {
                            Stat(t("Щомісяця витрати", "Monthly out"), "≈ " + Money.format(monthlyOut, s.base, hide = hide), Modifier.weight(1f))
                            Stat(t("Щомісяця доходи", "Monthly in"), "≈ " + Money.format(monthlyIn, s.base, hide = hide), Modifier.weight(1f), incomeColor)
                        }
                    }
                }
            }
            items(list, key = { it.id }) { r ->
                val next = Calc.nextDate(r)
                Row(
                    Modifier.fillMaxWidth().clickable { app.push(Route.RecurringEdit(r.id)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (r.type == TxType.TRANSFER) Badge(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.secondary)
                    else d.category(r.category).let { CatBadge(it?.icon, it?.color ?: 0xFF757575) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.note.ifBlank { if (r.type == TxType.TRANSFER) "${d.account(r.account)?.name} → ${d.account(r.toAccount)?.name}" else d.catName(r.category) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (!r.active) t("вимкнено", "paused")
                            else L.every(r.every, r.interval) + (next?.let { t(" · наступний ", " · next ") + shortDay(it) } ?: t(" · завершено", " · ended")) +
                                if (!r.auto) t(" · нагадування", " · reminder") else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        signed(r.type, r.amount, d.curOf(r.account), hide), fontWeight = FontWeight.SemiBold,
                        color = if (r.active) amountColor(r.type) else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
fun RecurringEditScreen(d: AppData, r: Route.RecurringEdit) {
    val app = LocalApp.current
    val existing = remember { d.recurring.firstOrNull { it.id == r.id } }
    val accounts = d.accounts.filter { !it.archived || it.id == existing?.account || it.id == existing?.toAccount }
    val initialNext = remember { existing?.let { Calc.nextDate(it) } }
    var type by remember { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(existing?.let { Money.plain(it.amount) } ?: "") }
    var account by remember { mutableStateOf(existing?.account ?: accounts.firstOrNull()?.id) }
    var toAccount by remember { mutableStateOf(existing?.toAccount ?: accounts.getOrNull(1)?.id) }
    var category by remember { mutableStateOf(existing?.category) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var every by remember { mutableStateOf(existing?.every ?: Every.MONTH) }
    var interval by remember { mutableIntStateOf(existing?.interval ?: 1) }
    var start by remember { mutableStateOf(initialNext ?: existing?.start?.let { LocalDate.parse(it) } ?: LocalDate.now()) }
    var end by remember { mutableStateOf(existing?.end?.let { LocalDate.parse(it) }) }
    var auto by remember { mutableStateOf(existing?.auto ?: true) }
    var remind by remember { mutableStateOf(existing?.remind ?: true) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    SubScreen(
        if (existing == null) t("Новий платіж", "New payment") else t("Регулярний платіж", "Recurring payment"),
        actions = { if (existing != null) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, t("Видалити", "Delete")) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(
                    listOf(t("Витрата", "Expense"), t("Дохід", "Income"), t("Переказ", "Transfer")), type.ordinal,
                    { i -> val nt = TxType.entries[i]; if (nt != type) { type = nt; category = null } },
                )
                MoneyField(amount, { amount = it; err = null }, t("Сума", "Amount"), d.curOf(account), error = err != null && Money.parse(amount) == null)
                OutlinedTextField(
                    note, { note = it.take(60) }, label = { Text(t("Назва", "Name")) },
                    placeholder = { Text(t("Netflix, оренда, зарплата…", "Netflix, rent, salary…")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                FieldLabel(if (type == TxType.TRANSFER) t("З рахунку", "From") else t("Рахунок", "Account"))
                Chips(accounts.map { it.id }, account, { id -> d.account(id)?.name ?: "" }, { account = it })
                if (type == TxType.TRANSFER) {
                    FieldLabel(t("На рахунок", "To"))
                    Chips(accounts.map { it.id }.filter { it != account }, toAccount, { id -> d.account(id)?.name ?: "" }, { toAccount = it })
                } else {
                    FieldLabel(t("Категорія", "Category"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in d.categories.filter { it.type == type && !it.archived }) {
                            FilterChip(
                                selected = c.id == category, onClick = { category = c.id; err = null }, label = { Text(c.name) },
                                leadingIcon = { Icon(catIcon(c.icon), null, Modifier.size(18.dp), tint = tint(c.color)) },
                            )
                        }
                    }
                }
                FieldLabel(t("Як часто", "How often"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Кожні", "Every"), Modifier.padding(end = 8.dp))
                    FilledTonalIconButton(onClick = { if (interval > 1) interval-- }) { Icon(Icons.Rounded.Remove, null) }
                    Text("$interval", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp))
                    FilledTonalIconButton(onClick = { if (interval < 99) interval++ }) { Icon(Icons.Rounded.Add, null) }
                }
                Chips(Every.entries.toList(), every, { L.everyName(it) }, { every = it })
                Text(L.every(every, interval).replaceFirstChar { it.uppercase(L.locale) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickStart = true }) {
                        Text((if (existing == null) t("Перший: ", "First: ") else t("Наступний: ", "Next: ")) + L.date(start))
                    }
                    OutlinedButton(onClick = { pickEnd = true }) { Text(end?.let { t("До ", "Until ") + L.date(it) } ?: t("Без кінця", "No end")) }
                    if (end != null) TextButton(onClick = { end = null }) { Text("✕") }
                }
                SwitchRow2(t("Записувати автоматично", "Record automatically"), if (auto) t("Операція з'явиться сама в день платежу", "The transaction appears by itself on the day") else t("Лише нагадаю — а ви натиснете «Сплатити»", "I'll just remind you — then tap “Pay”"), auto) { auto = it }
                SwitchRow2(t("Сповіщення", "Notification"), t("Повідомити в день платежу", "Notify on the day"), remind) { remind = it }
                if (existing != null) SwitchRow2(t("Активний", "Active"), t("Вимкніть, щоб призупинити", "Turn off to pause"), active) { active = it }
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            SaveBar {
                val a = Money.parse(amount)
                if (a == null || a <= 0) { err = t("Введіть суму", "Enter an amount"); return@SaveBar }
                if (account == null) { err = t("Оберіть рахунок", "Pick an account"); return@SaveBar }
                if (type == TxType.TRANSFER && (toAccount == null || toAccount == account)) { err = t("Оберіть рахунок для зарахування", "Pick a destination account"); return@SaveBar }
                if (type != TxType.TRANSFER && category == null) { err = t("Оберіть категорію", "Pick a category"); return@SaveBar }
                // якщо змінився розклад чи наступна дата — відлік починається з неї заново
                val scheduleChanged = existing == null || start != initialNext || every != existing.every || interval != existing.interval
                val rec = (existing ?: Recurring(Store.newId(), type, a, account!!, start = start.toString())).copy(
                    type = type, amount = a, account = account!!, toAccount = if (type == TxType.TRANSFER) toAccount else null,
                    category = if (type == TxType.TRANSFER) null else category, note = note.trim(), every = every, interval = interval,
                    end = end?.toString(), auto = auto, remind = remind, active = active,
                ).let { if (scheduleChanged) it.copy(start = start.toString(), n = 0, reminded = -1) else it }
                Store.saveRecurring(rec)
                Store.processRecurring()
                app.pop()
            }
        }
    }
    if (pickStart) DatePick(start, { start = it }, { pickStart = false })
    if (pickEnd) DatePick(end ?: start.plusYears(1), { end = it }, { pickEnd = false })
    if (confirm && existing != null) Confirm(
        t("Видалити платіж?", "Delete payment?"),
        t("Уже записані операції залишаться.", "Already recorded transactions stay."),
        t("Видалити", "Delete"),
        onConfirm = { Store.deleteRecurring(existing.id); app.pop() }, onDismiss = { confirm = false },
    )
}

@Composable
private fun SwitchRow2(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

/** «сьогодні», «завтра» або «5 листопада» — без дня тижня, щоб рядок був коротким. */
private fun shortDay(d: LocalDate): String {
    val today = LocalDate.now()
    return if (d == today || d == today.plusDays(1) || d == today.minusDays(1)) L.dayTitle(d).replaceFirstChar { it.lowercase(L.locale) } else L.date(d)
}
