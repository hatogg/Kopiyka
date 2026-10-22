@file:OptIn(ExperimentalLayoutApi::class)

package ua.x44to.kopiyka.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Calc
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.Rates
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import ua.x44to.kopiyka.sync.Sync
import ua.x44to.kopiyka.work.Notify
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun MoreScreen(d: AppData, padding: PaddingValues) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val s = d.settings
    var themeDialog by remember { mutableStateOf(false) }
    var monthDialog by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Store.settings { it.copy(notifications = ok, notifAsked = true) }
        if (!ok) app.toast(t("Без дозволу сповіщення не приходитимуть", "Notifications need permission"))
    }
    val sync by Sync.status.collectAsStateWithLifecycle()
    val activeAcc = d.accounts.filter { !it.archived }
    val total = remember(d) { Calc.total(d) }
    val recActive = d.recurring.filter { it.active }
    val monthly = remember(d) { recActive.filter { it.type == TxType.EXPENSE }.sumOf { Calc.monthlyCost(d, it) } }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
        item { Text(t("Ще", "More"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)) }
        item {
            val others = sync.members.filter { it.first != sync.device }.map { it.second.name }
            SoftCard(color = if (sync.code != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                MenuRow(
                    Icons.Rounded.Groups,
                    if (sync.code != null && others.isNotEmpty()) t("Спільний бюджет з: ", "Shared with: ") + others.joinToString(", ")
                    else t("Спільний бюджет", "Shared budget"),
                    syncLine(sync), { app.push(Route.Shared) },
                )
            }
        }
        item {
            SoftCard {
                MenuRow(
                    Icons.Rounded.AccountBalanceWallet, t("Рахунки", "Accounts"),
                    "${activeAcc.size} · ${Money.format(total, s.base, hide = s.hideAmounts)}", { app.push(Route.Accounts) },
                )
                MenuRow(
                    Icons.Rounded.Category, t("Категорії", "Categories"),
                    t("${d.categories.count { it.type == TxType.EXPENSE }} витрат · ${d.categories.count { it.type == TxType.INCOME }} доходів",
                        "${d.categories.count { it.type == TxType.EXPENSE }} expense · ${d.categories.count { it.type == TxType.INCOME }} income"),
                    { app.push(Route.Categories) },
                )
                MenuRow(
                    Icons.Rounded.Repeat, t("Регулярні платежі", "Recurring payments"),
                    if (recActive.isEmpty()) t("Підписки, оренда, зарплата…", "Subscriptions, rent, salary…")
                    else t("${recActive.size} активних · ≈ ${Money.format(monthly, s.base, hide = s.hideAmounts)}/міс", "${recActive.size} active · ≈ ${Money.format(monthly, s.base, hide = s.hideAmounts)}/mo"),
                    { app.push(Route.Recurrings) },
                )
                MenuRow(
                    Icons.Rounded.CurrencyExchange, t("Валюти й курси", "Currencies & rates"),
                    t("Основна: ", "Main: ") + "${Money.symbol(s.base)} ${s.base}" + if (s.ratesAuto) " · " + t("онлайн-курс", "online rates") else "", { app.push(Route.Rates) },
                )
            }
        }
        item { SectionTitle(t("Налаштування", "Settings")) }
        item {
            SoftCard {
                MenuRow(
                    Icons.Rounded.DarkMode, t("Тема", "Theme"),
                    listOf(t("Як у системі", "System default"), t("Світла", "Light"), t("Темна", "Dark"))[s.theme.coerceIn(0, 2)],
                    { themeDialog = true },
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    MenuRow(
                        Icons.Rounded.Palette, t("Кольори Material You", "Material You colours"), t("Під шпалери телефона", "Match your wallpaper"),
                        { Store.settings { it.copy(dynamic = !it.dynamic) } },
                    ) { Switch(s.dynamic, { v -> Store.settings { it.copy(dynamic = v) } }) }
                }
                MenuRow(
                    Icons.Rounded.Event, t("Початок місяця", "Month starts on"),
                    if (s.monthStart == 1) t("1 число — календарний місяць", "The 1st — calendar month")
                    else t("${s.monthStart} число — день зарплати", "Day ${s.monthStart} — payday"),
                    { monthDialog = true },
                )
                MenuRow(
                    Icons.Rounded.Notifications, t("Сповіщення", "Notifications"), t("Регулярні платежі та бюджети", "Recurring payments and budgets"),
                    null,
                ) {
                    Switch(s.notifications && Notify.allowed(ctx), { v ->
                        if (v && !Notify.allowed(ctx) && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else Store.settings { it.copy(notifications = v) }
                    })
                }
                MenuRow(
                    Icons.Rounded.VisibilityOff, t("Приховувати суми", "Hide amounts"), t("Щоб ніхто не підглянув", "For privacy in public"),
                    { Store.settings { it.copy(hideAmounts = !it.hideAmounts) } },
                ) { Switch(s.hideAmounts, { v -> Store.settings { it.copy(hideAmounts = v) } }) }
            }
        }
        item { SectionTitle(t("Дані", "Data")) }
        item {
            SoftCard {
                MenuRow(
                    Icons.Rounded.Backup, t("Резервна копія та експорт", "Backup & export"),
                    s.lastBackup?.let { t("Остання копія: ", "Last backup: ") + L.date(LocalDate.parse(it)) } ?: t("Копію ще не робили", "No backup yet"),
                    { app.push(Route.Backup) },
                )
            }
        }
        item { SectionTitle(t("Про застосунок", "About")) }
        item {
            SoftCard {
                MenuRow(
                    Icons.Rounded.Info, t("Копійка", "Kopiyka") + if (version.isNotEmpty()) " $version" else "",
                    t(
                        "Без реклами й реєстрації. Дані зберігаються на телефоні; для спільного бюджету — ще й на сервері, зашифровані кодом.",
                        "No ads, no sign-up. Data stays on your phone; a shared budget is also kept on a server, encrypted with its code.",
                    ),
                )
            }
        }
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text(t("Тема", "Theme")) },
            text = {
                Column {
                    listOf(t("Як у системі", "System default"), t("Світла", "Light"), t("Темна", "Dark")).forEachIndexed { i, name ->
                        Row(
                            Modifier.fillMaxWidth().clip(CircleShape).clickable { Store.settings { it.copy(theme = i) }; themeDialog = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(s.theme == i, { Store.settings { it.copy(theme = i) }; themeDialog = false })
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text(t("Закрити", "Close")) } },
        )
    }
    if (monthDialog) {
        AlertDialog(
            onDismissRequest = { monthDialog = false },
            title = { Text(t("З якого числа починається місяць?", "Which day does your month start?")) },
            text = {
                Column {
                    Text(
                        t("Якщо зарплата приходить, наприклад, 25-го — оберіть 25, і бюджети рахуватимуться від зарплати до зарплати.",
                            "If you're paid on the 25th, pick 25 and budgets will run from payday to payday."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in 1..28) {
                            val sel = n == s.monthStart
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { Store.settings { it.copy(monthStart = n) }; app.periodStart = null; monthDialog = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$n", color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { monthDialog = false }) { Text(t("Закрити", "Close")) } },
        )
    }
}

// ───────── валюти й курси ─────────

@Composable
fun RatesScreen(d: AppData) {
    val s = d.settings
    val used = d.accounts.map { it.currency }.toSet()
    val others = Money.CURRENCIES.map { it.code }.filter { it != s.base }.sortedBy { if (it in used) 0 else 1 }
    val rs by Store.ratesState.collectAsStateWithLifecycle()
    SubScreen(t("Валюти й курси", "Currencies & rates")) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(t("Основна валюта", "Main currency"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t("У ній рахуються загальний баланс, бюджети й статистика.", "Total balance, budgets and stats are shown in it."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Chips(Money.CURRENCIES.map { it.code }, s.base, { "${Money.symbol(it)} $it" }, { Store.setBase(it) })
                    Spacer(Modifier.height(20.dp))
                    Text(t("Курси", "Exchange rates"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t(
                            "Потрібні, щоб рахувати валютну готівку й рахунки в загальному балансі та переказах між валютами.",
                            "Used to count foreign-currency cash and accounts in the total balance and in transfers between currencies.",
                        ),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SoftCard {
                    MenuRow(
                        Icons.Rounded.Public, t("Онлайн-курс", "Online rates"),
                        if (s.ratesAuto) ratesInfo(s) else t("Вимкнено — курси вписуєте ви", "Off — you enter the rates"),
                        { Store.settings { it.copy(ratesAuto = !it.ratesAuto) }; if (!s.ratesAuto) Store.refreshRatesAsync(force = true) },
                    ) {
                        Switch(s.ratesAuto, { v -> Store.settings { it.copy(ratesAuto = v) }; if (v) Store.refreshRatesAsync(force = true) })
                    }
                    if (s.ratesAuto) {
                        Column(Modifier.padding(start = 64.dp, end = 16.dp, bottom = 12.dp)) {
                            val src = runCatching { Rates.Source.valueOf(s.ratesSource) }.getOrDefault(Rates.Source.MONO)
                            Chips(
                                Rates.Source.entries.toList(), src,
                                { when (it) { Rates.Source.MONO -> "Monobank"; Rates.Source.NBU -> t("НБУ", "NBU") } },
                                { v -> Store.settings { it.copy(ratesSource = v.name) }; Store.refreshRatesAsync(force = true) },
                            )
                            Text(
                                when (src) {
                                    Rates.Source.MONO -> t("Середнє між купівлею й продажем — близько до курсу обмінників.", "The middle of buy and sell — close to exchange offices.")
                                    Rates.Source.NBU -> t("Офіційний курс Національного банку.", "The official National Bank rate.")
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { Store.refreshRatesAsync(force = true) }, enabled = !rs.busy) {
                                    if (rs.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(t("Оновити зараз", "Update now"))
                                }
                            }
                            rs.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            items(others, key = { "${s.base}-$it-${s.ratesAuto}" }) { code -> RateRow(d, code, code in used) }
        }
    }
}

/** «Monobank · сьогодні о 14:30» */
fun ratesInfo(s: ua.x44to.kopiyka.data.Settings): String {
    if (s.ratesTime <= 0) return t("Ще не оновлювались", "Not updated yet")
    val from = when (s.ratesFrom) { "NBU" -> t("НБУ", "NBU"); "MONO" -> "Monobank"; else -> "" }
    val dt = java.time.Instant.ofEpochMilli(s.ratesTime).atZone(java.time.ZoneId.systemDefault())
    val time = "%02d:%02d".format(dt.hour, dt.minute)
    val day = dt.toLocalDate()
    val whenText = when (day) {
        LocalDate.now() -> t("сьогодні о $time", "today at $time")
        LocalDate.now().minusDays(1) -> t("вчора о $time", "yesterday at $time")
        else -> L.date(day)
    }
    return listOf(from, whenText).filter { it.isNotEmpty() }.joinToString(" · ")
}

private fun rateText(v: Double): String {
    val str = when {
        v >= 100 -> "%.2f".format(java.util.Locale.ROOT, v)
        v >= 1 -> "%.3f".format(java.util.Locale.ROOT, v)
        else -> "%.5f".format(java.util.Locale.ROOT, v)
    }.trimEnd('0').trimEnd('.')
    return if (L.ukr) str.replace('.', ',') else str
}

@Composable
private fun RateRow(d: AppData, code: String, used: Boolean) {
    val s = d.settings
    val rate = Money.convert(100_000_000L, code, s.base, s) / 100_000_000.0
    var text by remember(s.base) { mutableStateOf(rateText(rate)) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "1 ${Money.symbol(code)} $code =", style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (used) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.width(120.dp),
        )
        if (s.ratesAuto) {
            // онлайн-курс: лише показуємо (щоб змінити вручну — вимкніть «Онлайн-курс»)
            Text(
                rateText(rate) + " " + Money.symbol(s.base), style = MaterialTheme.typography.titleMedium,
                fontWeight = if (used) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 16.dp),
            )
        } else OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() || it == ',' || it == '.' }
                val x = text.replace(',', '.').toDoubleOrNull()
                if (x != null && x > 0) Store.setRate(code, x)
            },
            singleLine = true,
            suffix = { Text(Money.symbol(s.base)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

// ───────── резервна копія ─────────

private fun write(ctx: Context, uri: Uri, text: String): Boolean = try {
    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
} catch (_: Exception) {
    try {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (_: Exception) { false }
}

private fun read(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { s ->
        val bytes = s.readBytes()
        if (bytes.size > 30_000_000) null else bytes.toString(Charsets.UTF_8)
    }
} catch (_: Exception) { null }

@Composable
fun BackupScreen(d: AppData) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    var restoreText by remember { mutableStateOf<String?>(null) }
    var resetDialog by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            if (write(ctx, uri, Store.exportJson())) { Store.markBackup(); app.toast(t("Копію збережено", "Backup saved")) }
            else app.toast(t("Не вдалося зберегти файл", "Couldn't save the file"))
        }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = read(ctx, uri)
            if (txt == null) app.toast(t("Не вдалося прочитати файл", "Couldn't read the file")) else restoreText = txt
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) app.toast(if (write(ctx, uri, Store.csv())) t("Таблицю збережено", "Spreadsheet saved") else t("Не вдалося зберегти файл", "Couldn't save the file"))
    }

    SubScreen(t("Резервна копія та експорт", "Backup & export")) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                t(
                    "Усі дані лежать лише на телефоні. Збережіть копію на Google Диск, у «Файли» чи надішліть собі — і нічого не загубиться, навіть якщо зміните телефон.",
                    "Your data lives only on this phone. Save a backup to Google Drive or Files, or send it to yourself — nothing gets lost even if you switch phones.",
                ),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SoftCard {
                MenuRow(
                    Icons.Rounded.Download, t("Зберегти копію", "Save a backup"),
                    d.settings.lastBackup?.let { t("Остання: ", "Last: ") + L.date(LocalDate.parse(it)) } ?: t("Файл .json з усіма даними", "A .json file with all your data"),
                    { saveJson.launch("kopiyka-$today.json") },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                MenuRow(
                    Icons.Rounded.Restore, t("Відновити з копії", "Restore from backup"), t("Поточні дані буде замінено", "Replaces the current data"),
                    { openJson.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                )
            }
            SoftCard {
                MenuRow(
                    Icons.Rounded.TableChart, t("Експорт у таблицю (CSV)", "Export to a spreadsheet (CSV)"),
                    t("${L.ops(d.txs.size)} — відкриється в Excel чи Google Таблицях", "${L.ops(d.txs.size)} — opens in Excel or Google Sheets"),
                    { saveCsv.launch("kopiyka-$today.csv") },
                )
            }
            SoftCard {
                MenuRow(Icons.Rounded.DeleteForever, t("Видалити всі дані", "Delete all data"), t("Почати з чистого аркуша", "Start from scratch"), { resetDialog = true })
            }
        }
    }

    val pending = restoreText
    if (pending != null) Confirm(
        t("Відновити з копії?", "Restore from backup?"),
        t("Усі поточні рахунки, операції, бюджети й цілі буде замінено даними з файлу.", "All current accounts, transactions, budgets and goals will be replaced with the file's data.") +
            if (Sync.active) t(" Спільний бюджет на цьому телефоні буде вимкнено — в інших учасників нічого не зміниться.", " The shared budget will be turned off on this phone — nothing changes for the others.") else "",
        t("Відновити", "Restore"),
        onConfirm = {
            if (Sync.active) Sync.leave() // інакше відновлення з копії перезаписало б бюджет усім учасникам
            val err = Store.importJson(pending)
            app.toast(err ?: t("Дані відновлено", "Data restored"))
        },
        onDismiss = { restoreText = null },
    )
    if (resetDialog) Confirm(
        t("Видалити всі дані?", "Delete all data?"),
        t("Операції, рахунки, бюджети й цілі зникнуть назавжди. Спершу краще зберегти копію.", "Transactions, accounts, budgets and goals will be gone for good. Save a backup first.") +
            if (Sync.active) t(" Спільний бюджет на цьому телефоні буде вимкнено — в інших учасників дані залишаться.", " The shared budget will be turned off on this phone — the others keep their data.") else "",
        t("Видалити все", "Delete everything"),
        onConfirm = { if (Sync.active) Sync.leave(); Store.reset(); app.toast(t("Усе видалено", "Everything deleted")) },
        onDismiss = { resetDialog = false },
    )
}
