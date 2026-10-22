package ua.x44to.kopiyka.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.x44to.kopiyka.MainActivity
import ua.x44to.kopiyka.R
import ua.x44to.kopiyka.data.Calc
import ua.x44to.kopiyka.data.L
import ua.x44to.kopiyka.data.Money
import ua.x44to.kopiyka.data.Recurring
import ua.x44to.kopiyka.data.Store
import ua.x44to.kopiyka.data.TxType
import ua.x44to.kopiyka.data.t
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Notify {
    private const val CH_PAY = "payments"
    private const val CH_BUDGET = "budgets"

    fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_PAY, t("Регулярні платежі", "Recurring payments"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, t("Бюджети", "Budgets"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    @SuppressLint("MissingPermission")
    private fun show(c: Context, channel: String, id: Int, title: String, text: String) {
        if (!allowed(c)) return
        channels(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(c).notify(id, n) } catch (_: SecurityException) {}
    }

    fun budget(c: Context, budgetId: String, name: String, text: String) =
        show(c, CH_BUDGET, 10_000 + (budgetId.hashCode() and 0xFFFF), t("Бюджет «$name»", "Budget “$name”"), text)

    fun payment(c: Context, r: Recurring, title: String, text: String) =
        show(c, CH_PAY, 20_000 + (r.id.hashCode() and 0xFFFF), title, text)

    /** Щодня перевіряємо регулярні платежі навіть коли застосунок закритий. */
    fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<RecurringWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("recurring", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Додає настали автоплатежі й нагадує про ті, що треба сплатити вручну. */
    fun run(c: Context) {
        Store.init(c)
        val added = Store.processRecurring()
        val d = Store.data.value
        if (!d.settings.notifications) return
        for ((r, tx) in added) {
            if (!r.remind) continue
            val cur = d.account(tx.account)?.currency ?: d.settings.base
            val what = d.category(tx.category)?.name ?: r.note.ifBlank { L.txType(r.type) }
            val sum = Money.format(tx.amount, cur, sign = tx.type == TxType.INCOME)
            payment(c, r, t("Додано: $what", "Added: $what"), t("$sum · регулярний платіж", "$sum · recurring payment"))
        }
        val today = LocalDate.now()
        for (r in d.recurring) {
            if (!r.active || r.auto || !r.remind || r.reminded == r.n) continue
            val date = Calc.nextDate(r) ?: continue
            if (date.isAfter(today)) continue
            val cur = d.account(r.account)?.currency ?: d.settings.base
            val what = r.note.ifBlank { d.category(r.category)?.name ?: L.txType(r.type) }
            payment(c, r, t("Час сплатити: $what", "Time to pay: $what"), t("${Money.format(r.amount, cur)} — відкрийте Копійку, щоб відмітити", "${Money.format(r.amount, cur)} — open Kopiyka to mark it paid"))
            Store.markReminded(r.id, r.n)
        }
    }
}

class RecurringWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Notify.run(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
