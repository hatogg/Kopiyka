package ua.x44to.kopiyka.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.t
import ua.x44to.kopiyka.sync.AccessDenied
import ua.x44to.kopiyka.sync.BudgetCode
import ua.x44to.kopiyka.sync.Sync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Опис стану синхронізації одним рядком (і для «Ще», і для цього екрана). */
fun syncLine(s: Sync.Status): String = when (s.phase) {
    Sync.Phase.OFF -> t("Ведіть один бюджет разом — за кодом", "Keep one budget together — with a code")
    Sync.Phase.CONNECTING -> t("Підключаюсь…", "Connecting…")
    Sync.Phase.ONLINE -> t("Синхронізовано", "In sync") + (if (s.lastSync > 0) " · " + SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(s.lastSync)) else "")
    Sync.Phase.OFFLINE -> if (s.pending > 0) t("Немає інтернету — ${s.pending} змін надішлються пізніше", "Offline — ${s.pending} changes will be sent later")
        else t("Немає інтернету — оновиться, щойно з'явиться мережа", "Offline — will update when back online")
    Sync.Phase.DENIED -> t("Сервер відмовив у доступі", "The server denied access")
}

@Composable
fun SharedScreen(@Suppress("UNUSED_PARAMETER") d: AppData) {
    val st by Sync.status.collectAsStateWithLifecycle()
    SubScreen(t("Спільний бюджет", "Shared budget")) { pad ->
        Column(
            Modifier.padding(pad).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 24.dp),
        ) {
            when {
                !Sync.configured -> Empty(
                    Icons.Rounded.CloudOff, t("Спільний бюджет не налаштовано", "Shared budget isn't set up"),
                    t("У цій збірці не вказано адресу бази даних.", "This build has no database address."),
                )
                st.code == null -> Setup(st)
                else -> Joined(st)
            }
        }
    }
}

@Composable
private fun Setup(st: Sync.Status) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var name by remember { mutableStateOf(st.name) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmJoin by remember { mutableStateOf(false) }
    val validCode = BudgetCode.normalize(code) != null

    fun failText(e: Throwable): String = when {
        e is Sync.JoinError && e.notFound -> t("Бюджету з таким кодом немає. Перевірте код.", "No budget with this code. Check the code.")
        e is AccessDenied || e.cause is AccessDenied -> t("Сервер відмовив у доступі. Спробуйте пізніше.", "The server denied access. Try again later.")
        else -> t("Немає з'єднання з інтернетом. Спробуйте ще раз.", "No internet connection. Please try again.")
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Badge(Icons.Rounded.Groups, MaterialTheme.colorScheme.primary, 72.dp)
        Spacer(Modifier.height(14.dp))
        Text(t("Ведіть бюджет разом", "Keep a budget together"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            t(
                "Двоє (чи більше) людей бачать і додають ті самі операції, рахунки, бюджети й цілі. Зміни з'являються на обох телефонах за кілька секунд.",
                "Two (or more) people see and add the same transactions, accounts, budgets and goals. Changes show up on both phones within seconds.",
            ),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            name, { name = it.take(30) }, label = { Text(t("Ваше ім'я", "Your name")) },
            supportingText = { Text(t("Його бачитимуть інші учасники біля ваших операцій", "Others will see it next to your transactions")) },
            singleLine = true, leadingIcon = { Icon(Icons.Rounded.Person, null) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        error?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = {
                focus.clearFocus()
                if (name.isBlank()) { error = t("Вкажіть ім'я", "Enter your name"); return@Button }
                error = null
                busy = t("Створюю спільний бюджет…", "Creating the shared budget…")
                scope.launch {
                    try {
                        Sync.create(name)
                        app.toast(t("Спільний бюджет створено — поділіться кодом", "Shared budget created — share the code"))
                    } catch (e: Exception) {
                        error = failText(e)
                    }
                    busy = null
                }
            },
            enabled = busy == null, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
        ) { Text(t("Створити спільний бюджет", "Create a shared budget"), style = MaterialTheme.typography.titleMedium) }
        Text(
            t("Ваші поточні дані стануть спільними. Потім дайте код іншій людині.", "Your current data becomes shared. Then give the code to the other person."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )

        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(t("або", "or"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f))
        }

        OutlinedTextField(
            code, { v -> code = v.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.take(16) },
            label = { Text(t("Код від іншої людини", "Code from the other person")) },
            placeholder = { Text("XXXX-XXXX-XXXX") },
            singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (validCode) confirmJoin = true }),
            isError = code.isNotEmpty() && code.count { it.isLetterOrDigit() } >= BudgetCode.LENGTH && !validCode,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = {
                focus.clearFocus()
                if (name.isBlank()) { error = t("Вкажіть ім'я", "Enter your name"); return@FilledTonalButton }
                error = null
                confirmJoin = true
            },
            enabled = busy == null && validCode, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
        ) { Text(t("Приєднатися", "Join"), style = MaterialTheme.typography.titleMedium) }

        busy?.let {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        PrivacyNote()
    }

    if (confirmJoin) Confirm(
        t("Приєднатися до бюджету?", "Join the budget?"),
        t(
            "Дані на цьому телефоні буде замінено спільним бюджетом. Копію поточних даних збережемо в пам'яті застосунку.",
            "The data on this phone will be replaced with the shared budget. A copy of the current data is kept in the app.",
        ),
        t("Приєднатися", "Join"),
        onConfirm = {
            busy = t("Завантажую спільний бюджет…", "Downloading the shared budget…")
            scope.launch {
                try {
                    Sync.join(code, name)
                    app.toast(t("Готово! Тепер бюджет спільний", "Done! The budget is shared now"))
                } catch (e: Exception) {
                    error = failText(e)
                }
                busy = null
            }
        },
        onDismiss = { confirmJoin = false },
        danger = false,
    )
}

@Composable
private fun Joined(st: Sync.Status) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val clipboard = LocalClipboardManager.current
    val pretty = BudgetCode.pretty(st.code ?: "")
    var renaming by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("Код спільного бюджету", "Shared budget code"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                pretty, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val text = t(
                        "Давай вести бюджет разом у Копійці! Відкрий Копійку → Ще → Спільний бюджет → Приєднатися і введи код: $pretty",
                        "Let's keep our budget together in Kopiyka! Open Kopiyka → More → Shared budget → Join and enter the code: $pretty",
                    )
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(i, t("Поділитися кодом", "Share the code")))
                }) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Поділитися", "Share"))
                }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(pretty)); app.toast(t("Код скопійовано", "Code copied")) }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Копіювати", "Copy"))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t("Інша людина: Копійка → Ще → Спільний бюджет → Приєднатися", "The other person: Kopiyka → More → Shared budget → Join"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), textAlign = TextAlign.Center,
            )
        }
    }

    SoftCard {
        MenuRow(
            when (st.phase) {
                Sync.Phase.ONLINE -> Icons.Rounded.CloudDone
                Sync.Phase.CONNECTING -> Icons.Rounded.CloudSync
                else -> Icons.Rounded.CloudOff
            },
            t("Синхронізація", "Sync"), syncLine(st),
        )
    }

    SectionTitle(t("Учасники", "Members"))
    SoftCard {
        if (st.members.isEmpty()) MenuRow(Icons.Rounded.Person, st.name.ifBlank { t("Ви", "You") }, t("це ви", "that's you"))
        for ((id, m) in st.members) {
            MenuRow(Icons.Rounded.Person, m.name, if (id == st.device) t("це ви", "that's you") else null)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        MenuRow(Icons.Rounded.Edit, t("Змінити моє ім'я", "Change my name"), st.name.ifBlank { null }, { renaming = true })
    }

    SoftCard {
        MenuRow(
            Icons.AutoMirrored.Rounded.Logout, t("Вийти зі спільного бюджету", "Leave the shared budget"),
            t("Дані лишаться на телефоні, але перестануть оновлюватись", "Data stays on this phone but stops syncing"), { leaving = true },
        )
    }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.padding(horizontal = 20.dp)) { PrivacyNote() }

    if (renaming) {
        var v by remember { mutableStateOf(st.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(t("Ваше ім'я", "Your name")) },
            text = {
                OutlinedTextField(
                    v, { v = it.take(30) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = { TextButton(onClick = { if (v.isNotBlank()) Sync.rename(v); renaming = false }) { Text(t("Зберегти", "Save")) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(t("Скасувати", "Cancel")) } },
        )
    }
    if (leaving) Confirm(
        t("Вийти зі спільного бюджету?", "Leave the shared budget?"),
        t(
            "Дані залишаться на цьому телефоні, але більше не оновлюватимуться. Повернутися можна за тим самим кодом.",
            "The data stays on this phone but won't update anymore. You can rejoin with the same code.",
        ),
        t("Вийти", "Leave"),
        onConfirm = { Sync.leave(); app.toast(t("Бюджет більше не спільний", "The budget isn't shared anymore")) },
        onDismiss = { leaving = false },
    )
}

@Composable
private fun PrivacyNote() {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            t(
                "Дані шифруються кодом бюджету ще на телефоні — сервер зберігає лише зашифровані записи і прочитати їх не може. Хто знає код, той бачить і змінює бюджет, тож давайте його лише тим, кому довіряєте.",
                "Data is encrypted with the budget code on the phone — the server only stores encrypted records and can't read them. Anyone with the code can see and edit the budget, so share it only with people you trust.",
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
