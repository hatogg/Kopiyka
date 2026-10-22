package ua.x44to.kopiyka.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ua.x44to.kopiyka.MainActivity
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Calc
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.Period
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate

/** Віджет: загальний баланс, витрати за сьогодні й місяць, кнопки «− Витрата» і «+ Дохід». */
class BalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context.applicationContext)
        provideContent {
            GlanceTheme {
                Body(context, Store.data.value)
            }
        }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}

/** Окремий Uri для кожної кнопки, щоб Android не сплутав їхні PendingIntent. */
fun addIntent(context: Context, type: TxType): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("kopiyka://add/${type.name.lowercase()}"))
        .setComponent(ComponentName(context, MainActivity::class.java))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun Body(context: Context, d: AppData) {
    val s = d.settings
    val hide = s.hideAmounts
    val today = LocalDate.now()
    val total = Calc.total(d)
    val p = Calc.current(d)
    val todaySpent = Calc.sums(d, Period(today, today)).expense
    val monthSpent = Calc.sums(d, p).expense
    val overall = d.budgets.firstOrNull { it.category == null }
    val bs = overall?.let { Calc.budgetState(d, it, p) }
    val muted = GlanceTheme.colors.onSurfaceVariant
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
    ) {
        Text(t("Баланс", "Balance"), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Text(
            Money.format(total, s.base, hide = hide),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1,
        )
        Text(
            t("Сьогодні ", "Today ") + Money.format(-todaySpent, s.base, hide = hide) + " · " + t("місяць ", "month ") + Money.format(-monthSpent, s.base, hide = hide),
            style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1,
        )
        if (bs != null) {
            val ok = bs.left >= 0 && bs.leftToday >= 0
            Text(
                when {
                    bs.left < 0 -> t("Бюджет перевищено на ", "Budget exceeded by ") + Money.format(-bs.left, s.base, hide = hide)
                    bs.leftToday < 0 -> t("Понад денний ліміт: ", "Over today's limit: ") + Money.format(-bs.leftToday, s.base, hide = hide)
                    else -> t("Можна ще сьогодні: ", "Left today: ") + Money.format(bs.leftToday, s.base, hide = hide)
                },
                style = TextStyle(color = if (ok) GlanceTheme.colors.primary else GlanceTheme.colors.error, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            Button(t("− Витрата", "− Expense"), GlanceTheme.colors.primaryContainer, GlanceTheme.colors.onPrimaryContainer, addIntent(context, TxType.EXPENSE), GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Button(t("+ Дохід", "+ Income"), GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer, addIntent(context, TxType.INCOME), GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun Button(text: String, bg: ColorProvider, fg: ColorProvider, intent: Intent, modifier: GlanceModifier) {
    Box(
        modifier.height(40.dp).background(bg).cornerRadius(20.dp).clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), maxLines = 1)
    }
}
