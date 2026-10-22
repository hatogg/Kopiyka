package ua.x44to.kopiyka

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import ua.x44to.kopiyka.ui.AccountEditScreen
import ua.x44to.kopiyka.ui.AccountsScreen
import ua.x44to.kopiyka.ui.AppState
import ua.x44to.kopiyka.ui.BackupScreen
import ua.x44to.kopiyka.ui.BudgetEditScreen
import ua.x44to.kopiyka.ui.BudgetsScreen
import ua.x44to.kopiyka.ui.CategoriesScreen
import ua.x44to.kopiyka.ui.CategoryEditScreen
import ua.x44to.kopiyka.ui.Entry
import ua.x44to.kopiyka.ui.GoalEditScreen
import ua.x44to.kopiyka.ui.HomeScreen
import ua.x44to.kopiyka.ui.KopiykaTheme
import ua.x44to.kopiyka.ui.LocalApp
import ua.x44to.kopiyka.ui.MoreScreen
import ua.x44to.kopiyka.ui.RatesScreen
import ua.x44to.kopiyka.ui.RecurringEditScreen
import ua.x44to.kopiyka.ui.RecurringsScreen
import ua.x44to.kopiyka.ui.Route
import ua.x44to.kopiyka.ui.SharedScreen
import ua.x44to.kopiyka.ui.StatsScreen
import ua.x44to.kopiyka.ui.Tabs
import ua.x44to.kopiyka.ui.TxEditor
import ua.x44to.kopiyka.ui.TxListScreen
import ua.x44to.kopiyka.ui.isDark
import ua.x44to.kopiyka.sync.Sync
import ua.x44to.kopiyka.work.Notify

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Store.processRecurring()
        try { Notify.schedule(applicationContext) } catch (_: Exception) {}
        try { Sync.schedule(applicationContext) } catch (_: Exception) {}
        if (savedInstanceState == null) handle(intent)
        setContent {
            val data by Store.data.collectAsStateWithLifecycle()
            val dark = isDark(data.settings)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            KopiykaTheme(data.settings) {
                Root(data)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Store.processRecurring()
    }

    override fun onStart() {
        super.onStart()
        Sync.start() // спільний бюджет: живе з'єднання, поки застосунок на екрані
        Store.refreshRatesAsync() // онлайн-курс валют, якщо давно не оновлювали
    }

    override fun onStop() {
        super.onStop()
        Sync.stop()
    }

    /** Віджет і ярлики відкривають застосунок одразу на додаванні: kopiyka://add/expense. */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "kopiyka" || uri.host != "add") return
        Launch.add.value = when (uri.lastPathSegment) {
            "income" -> TxType.INCOME
            "transfer" -> TxType.TRANSFER
            else -> TxType.EXPENSE
        }
    }
}

/** Запити «відкрий додавання» з віджета чи ярлика. */
object Launch {
    val add = MutableStateFlow<TxType?>(null)
}

@Composable
private fun Root(d: AppData) {
    val scope = rememberCoroutineScope()
    val app = remember { AppState(scope) }
    val pending by Launch.add.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val tp = pending ?: return@LaunchedEffect
        Launch.add.value = null
        app.stack.clear()
        app.push(Route.Edit(type = tp))
    }
    // дозвіл на сповіщення — один раз, при першому запуску
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Store.settings { it.copy(notifAsked = true, notifications = ok) }
    }
    NotifAsk(d) { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    BackHandler(enabled = app.stack.isNotEmpty()) { app.pop() }
    BackHandler(enabled = app.stack.isEmpty() && app.tab != Tabs.HOME) { app.tab = Tabs.HOME }

    CompositionLocalProvider(LocalApp provides app) {
        Box(Modifier.fillMaxSize()) {
            MainTabs(d, app)
            for (e in app.stack) {
                key(e.id) { Layer(d, e) }
            }
        }
    }
}

@Composable
private fun NotifAsk(d: AppData, ask: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !d.settings.notifAsked) {
            if (Notify.allowed(ctx)) Store.settings { it.copy(notifAsked = true) } else ask()
        }
    }
}

@Composable
private fun MainTabs(d: AppData, app: AppState) {
    val items = listOf(
        Triple(Tabs.HOME, Icons.Rounded.Home, t("Огляд", "Overview")),
        Triple(Tabs.TXS, Icons.Rounded.Receipt, t("Операції", "History")),
        Triple(Tabs.BUDGETS, Icons.Rounded.PieChart, t("Бюджети", "Budgets")),
        Triple(Tabs.STATS, Icons.Rounded.BarChart, t("Звіти", "Stats")),
        Triple(Tabs.MORE, Icons.Rounded.MoreHoriz, t("Ще", "More")),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                for ((id, icon, label) in items) {
                    NavigationBarItem(
                        selected = app.tab == id, onClick = { app.tab = id },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        },
        floatingActionButton = {
            when (app.tab) {
                Tabs.HOME, Tabs.TXS -> ExtendedFloatingActionButton(
                    onClick = { app.push(Route.Edit()) },
                    icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(t("Додати", "Add")) },
                )
                Tabs.BUDGETS -> ExtendedFloatingActionButton(
                    onClick = { app.push(if (app.goalsTab) Route.GoalEdit(null) else Route.BudgetEdit(null)) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(if (app.goalsTab) t("Ціль", "Goal") else t("Бюджет", "Budget")) },
                )
                else -> {}
            }
        },
        snackbarHost = { if (app.stack.isEmpty()) SnackbarHost(app.snack) },
    ) { padding ->
        when (app.tab) {
            Tabs.HOME -> HomeScreen(d, padding)
            Tabs.TXS -> TxListScreen(d, padding)
            Tabs.BUDGETS -> BudgetsScreen(d, padding)
            Tabs.STATS -> StatsScreen(d, padding)
            else -> MoreScreen(d, padding)
        }
    }
}

/** Екран поверх вкладок: виїжджає збоку, під ним усе лишається як було. */
@Composable
private fun Layer(d: AppData, e: Entry) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    Surface(
        Modifier.fillMaxSize().graphicsLayer {
            translationX = (1f - anim.value) * size.width * 0.15f
            alpha = anim.value
        },
    ) {
        when (val r = e.route) {
            is Route.Edit -> TxEditor(d, r)
            Route.Accounts -> AccountsScreen(d)
            is Route.AccountEdit -> AccountEditScreen(d, r)
            Route.Categories -> CategoriesScreen(d)
            is Route.CategoryEdit -> CategoryEditScreen(d, r)
            Route.Recurrings -> RecurringsScreen(d)
            is Route.RecurringEdit -> RecurringEditScreen(d, r)
            is Route.BudgetEdit -> BudgetEditScreen(d, r)
            is Route.GoalEdit -> GoalEditScreen(d, r)
            Route.Rates -> RatesScreen(d)
            Route.Backup -> BackupScreen(d)
            Route.Shared -> SharedScreen(d)
        }
    }
}
