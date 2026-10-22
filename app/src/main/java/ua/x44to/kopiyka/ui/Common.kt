@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ua.x44to.kopiyka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.PALETTE
import ua.x44to.kopiyka.data.Tx
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate

fun AppData.catName(id: String?) = category(id)?.name ?: t("Без категорії", "Uncategorized")

fun AppData.txTitle(tx: Tx): String = when (tx.type) {
    TxType.TRANSFER -> "${account(tx.account)?.name ?: "?"} → ${account(tx.toAccount)?.name ?: "?"}"
    else -> catName(tx.category)
}

fun AppData.curOf(accountId: String?) = account(accountId)?.currency ?: settings.base

/** Кругла плашка з іконкою на світлому тлі свого кольору. */
@Composable
fun Badge(icon: ImageVector, color: Color, size: Dp = 40.dp, iconSize: Dp = size * 0.55f) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun CatBadge(icon: String?, color: Long, size: Dp = 40.dp) = Badge(catIcon(icon), tint(color), size)

@Composable
fun amountColor(type: TxType): Color = when (type) {
    TxType.INCOME -> incomeColor
    TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
    TxType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun signed(type: TxType, minor: Long, cur: String, hide: Boolean): String = when (type) {
    TxType.EXPENSE -> Money.format(-minor, cur, hide = hide)
    TxType.INCOME -> Money.format(minor, cur, sign = true, hide = hide)
    TxType.TRANSFER -> Money.format(minor, cur, hide = hide)
}

@Composable
fun TxRow(d: AppData, tx: Tx, showDate: Boolean = false, onClick: () -> Unit) {
    val hide = d.settings.hideAmounts
    val acc = d.account(tx.account)
    val cur = acc?.currency ?: d.settings.base
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tx.type == TxType.TRANSFER) Badge(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.secondary)
        else d.category(tx.category).let { CatBadge(it?.icon, it?.color ?: 0xFF757575) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.txTitle(tx), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                tx.note.takeIf { it.isNotBlank() },
                if (tx.type != TxType.TRANSFER) acc?.name else null,
                if (showDate) L.shortDate(LocalDate.parse(tx.date)) else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(
                sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tx.recurring != null) {
                    Icon(Icons.Rounded.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    signed(tx.type, tx.amount, cur, hide), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold, color = amountColor(tx.type), maxLines = 1,
                )
            }
            val toCur = d.account(tx.toAccount)?.currency
            if (tx.type == TxType.TRANSFER && toCur != null && toCur != cur) Text(
                "→ " + Money.format(tx.toAmount ?: tx.amount, toCur, hide = hide),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «‹ Жовтень 2026 ›» — перемикач місяців. Натискання на назву повертає до поточного. */
@Composable
fun PeriodBar(app: AppState, monthStart: Int, modifier: Modifier = Modifier) {
    val p = app.period(monthStart)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { app.shiftPeriod(monthStart, -1) }) { Icon(Icons.Rounded.ChevronLeft, t("Попередній", "Previous")) }
        Text(
            p.title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { app.periodStart = null }.padding(vertical = 8.dp),
        )
        IconButton(onClick = { app.shiftPeriod(monthStart, 1) }) { Icon(Icons.Rounded.ChevronRight, t("Наступний", "Next")) }
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Смужка прогресу із заокругленими кінцями. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(height).clip(CircleShape).background(color))
    }
}

/** Колір смужки бюджету: спокійний, жовтіє ближче до ліміту, червоніє за ним. */
@Composable
fun budgetColor(ratio: Float): Color = when {
    ratio > 1f -> MaterialTheme.colorScheme.error
    ratio > 0.85f -> warnColor
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLow, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    val colors = CardDefaults.cardColors(containerColor = color)
    val shape = RoundedCornerShape(24.dp)
    if (onClick != null) Card(onClick = onClick, modifier = m, shape = shape, colors = colors, content = content)
    else Card(modifier = m, shape = shape, colors = colors, content = content)
}

/** Підекран із заголовком і стрілкою «Назад». */
@Composable
fun SubScreen(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val app = LocalApp.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { app.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Назад", "Back")) } },
                actions = actions,
            )
        },
        floatingActionButton = fab,
        snackbarHost = { SnackbarHost(app.snack) },
        content = content,
    )
}

@Composable
fun ColorPicker(selected: Long, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in PALETTE) {
            val col = argb(c)
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(col).clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) Icon(Icons.Rounded.Check, null, tint = onColor(col), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun IconPicker(selected: String, color: Long, onPick: (String) -> Unit) {
    val col = tint(color)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, icon) in CAT_ICONS) {
            val sel = key == selected
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(if (sel) col else col.copy(alpha = 0.12f))
                    .clickable { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (sel) onColor(col) else col, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Поле суми з цифровою клавіатурою. */
@Composable
fun MoneyField(value: String, onValue: (String) -> Unit, label: String, currency: String, modifier: Modifier = Modifier, error: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }) },
        label = { Text(label) },
        suffix = { Text(Money.symbol(currency)) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Вибір одного варіанта чипами. */
@Composable
fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, icon: ((T) -> ImageVector?)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            val ic = icon?.invoke(o)
            FilterChip(
                selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) },
                leadingIcon = if (ic != null) ({ Icon(ic, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}

@Composable
fun DatePick(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Скасувати", "Cancel")) } },
    ) {
        DatePicker(st)
    }
}

@Composable
fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Скасувати", "Cancel")) } },
    )
}

@Composable
fun Empty(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Badge(icon, MaterialTheme.colorScheme.primary, 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Рядок налаштувань / меню. */
@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Маленька підпис-плашка: «↓ Доходи +12 000 ₴». */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface, style: TextStyle = MaterialTheme.typography.titleMedium) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = style, color = valueColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Outlined(selected: Boolean, color: Color): Modifier =
    if (selected) Modifier.border(2.dp, color, RoundedCornerShape(16.dp)) else Modifier

@Composable
fun FullScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
}
