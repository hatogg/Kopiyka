package ua.x44to.kopiyka.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Бюджетний період: зазвичай календарний місяць, але може починатися з дня зарплати. */
data class Period(val start: LocalDate, val end: LocalDate) {
    val days get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    operator fun contains(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
    fun next(monthStart: Int) = Calc.periodOf(end.plusDays(1), monthStart)
    fun prev(monthStart: Int) = Calc.periodOf(start.minusDays(1), monthStart)
    val title: String get() = if (start.dayOfMonth == 1 && end == start.plusMonths(1).minusDays(1)) L.monthTitle(java.time.YearMonth.from(start))
        else "${L.shortDate(start)} – ${L.shortDate(end)}" + if (end.year != LocalDate.now().year) " ${end.year}" else ""
}

class Sums(val income: Long, val expense: Long, val byCategory: Map<String?, Long>, val count: Int) {
    val net get() = income - expense
}

class BudgetState(val budget: Budget, val spent: Long, val days: Int, val daysLeft: Int, val todaySpent: Long) {
    val limit get() = budget.limit
    val left get() = limit - spent
    val ratio get() = if (limit <= 0) 0f else spent.toFloat() / limit
    /** денний ліміт: залишок на початок сьогоднішнього дня, поділений на дні, що лишились (разом із сьогодні) */
    val perDay get() = if (daysLeft <= 0) 0L else maxOf(0L, limit - (spent - todaySpent)) / daysLeft
    /** скільки ще можна витратити сьогодні (може бути від'ємним) */
    val leftToday get() = perDay - todaySpent
    /** прогноз на кінець періоду за поточним темпом */
    val forecast get() = if (daysLeft <= 0 || days - daysLeft <= 0) spent else spent * days / (days - daysLeft + 1)
}

object Calc {
    fun periodOf(d: LocalDate, monthStart: Int): Period {
        val s = monthStart.coerceIn(1, 28)
        val start = if (d.dayOfMonth >= s) d.withDayOfMonth(s) else d.minusMonths(1).withDayOfMonth(s)
        return Period(start, start.plusMonths(1).minusDays(1))
    }

    fun current(data: AppData) = periodOf(LocalDate.now(), data.settings.monthStart)

    /** Залишки всіх рахунків у їхніх валютах — один прохід по операціях. */
    fun balances(data: AppData): Map<String, Long> {
        val m = HashMap<String, Long>()
        for (a in data.accounts) m[a.id] = a.initial
        for (t in data.txs) {
            when (t.type) {
                TxType.INCOME -> m[t.account] = (m[t.account] ?: 0) + t.amount
                TxType.EXPENSE -> m[t.account] = (m[t.account] ?: 0) - t.amount
                TxType.TRANSFER -> {
                    m[t.account] = (m[t.account] ?: 0) - t.amount
                    val to = t.toAccount ?: continue
                    m[to] = (m[to] ?: 0) + (t.toAmount ?: t.amount)
                }
            }
        }
        return m
    }

    fun total(data: AppData, bal: Map<String, Long> = balances(data)): Long {
        val s = data.settings
        return data.accounts.filter { !it.archived && it.inTotal }.sumOf { Money.convert(bal[it.id] ?: 0, it.currency, s.base, s) }
    }

    /** Сума операції в базовій валюті. */
    fun base(data: AppData, t: Tx): Long {
        val cur = data.account(t.account)?.currency ?: data.settings.base
        return Money.convert(t.amount, cur, data.settings.base, data.settings)
    }

    fun sums(data: AppData, p: Period, accountFilter: String? = null): Sums {
        var inc = 0L; var exp = 0L; var n = 0
        val by = HashMap<String?, Long>()
        for (t in data.txs) {
            if (t.type == TxType.TRANSFER) continue
            val d = LocalDate.parse(t.date)
            if (d !in p) continue
            if (accountFilter != null && t.account != accountFilter) continue
            val v = base(data, t)
            n++
            if (t.type == TxType.INCOME) inc += v else exp += v
            by[t.category] = (by[t.category] ?: 0) + v
        }
        return Sums(inc, exp, by, n)
    }

    fun spentInCategory(data: AppData, p: Period, category: String?): Long {
        var s = 0L
        for (t in data.txs) {
            if (t.type != TxType.EXPENSE) continue
            if (category != null && t.category != category) continue
            if (LocalDate.parse(t.date) !in p) continue
            s += base(data, t)
        }
        return s
    }

    fun budgetState(data: AppData, b: Budget, p: Period): BudgetState {
        val today = LocalDate.now()
        val left = if (today in p) ChronoUnit.DAYS.between(today, p.end).toInt() + 1 else if (today.isBefore(p.start)) p.days else 0
        val todaySpent = if (today in p) spentInCategory(data, Period(today, today), b.category) else 0L
        return BudgetState(b, spentInCategory(data, p, b.category), p.days, left, todaySpent)
    }

    /** Суми за категоріями одного типу, від більшої до меншої (у базовій валюті). */
    fun byCategory(data: AppData, p: Period, type: TxType, account: String? = null): List<Pair<String?, Long>> {
        val m = HashMap<String?, Long>()
        for (t in data.txs) {
            if (t.type != type) continue
            if (account != null && t.account != account) continue
            if (LocalDate.parse(t.date) !in p) continue
            m[t.category] = (m[t.category] ?: 0) + base(data, t)
        }
        return m.entries.map { it.key to it.value }.sortedByDescending { it.second }
    }

    /** Скільки в середньому на місяць витрачали в категорії за [n] попередніх періодів. */
    fun average(data: AppData, p: Period, category: String?, n: Int = 3): Long {
        var sum = 0L
        var pp = p
        var counted = 0
        repeat(n) {
            pp = pp.prev(data.settings.monthStart)
            val s = spentInCategory(data, pp, category)
            if (s > 0) { sum += s; counted++ }
        }
        return if (counted == 0) 0 else sum / counted
    }

    /** Приблизно скільки регулярні платежі коштують на місяць (у базовій валюті). */
    fun monthlyCost(data: AppData, r: Recurring): Long {
        val cur = data.account(r.account)?.currency ?: data.settings.base
        val v = Money.convert(r.amount, cur, data.settings.base, data.settings).toDouble()
        val perMonth = when (r.every) {
            Every.DAY -> v * 30.44 / r.interval
            Every.WEEK -> v * 4.348 / r.interval
            Every.MONTH -> v / r.interval
            Every.YEAR -> v / 12.0 / r.interval
        }
        return perMonth.toLong()
    }

    // ── регулярні платежі ──

    fun dateOf(r: Recurring, n: Int): LocalDate {
        val s = LocalDate.parse(r.start)
        val k = (n * r.interval).toLong()
        return when (r.every) {
            Every.DAY -> s.plusDays(k)
            Every.WEEK -> s.plusWeeks(k)
            Every.MONTH -> s.plusMonths(k)
            Every.YEAR -> s.plusYears(k)
        }
    }

    fun nextDate(r: Recurring): LocalDate? {
        val d = dateOf(r, r.n)
        val end = r.end?.let { LocalDate.parse(it) }
        return if (end != null && d.isAfter(end)) null else d
    }

    /** Найближчі повтори активних регулярних платежів у межах [days] днів (зокрема прострочені нагадування). */
    fun upcoming(data: AppData, days: Int): List<Pair<Recurring, LocalDate>> {
        val today = LocalDate.now()
        val until = today.plusDays(days.toLong())
        val out = ArrayList<Pair<Recurring, LocalDate>>()
        for (r in data.recurring) {
            if (!r.active) continue
            var n = r.n
            var guard = 0
            while (guard++ < 60) {
                val d = dateOf(r, n)
                val end = r.end?.let { LocalDate.parse(it) }
                if (end != null && d.isAfter(end)) break
                if (d.isAfter(until)) break
                out.add(r to d)
                n++
            }
        }
        return out.sortedBy { it.second }
    }

    /** Ціль: скільки зібрано (у валюті цілі). */
    fun goalSaved(data: AppData, g: Goal, bal: Map<String, Long> = balances(data)): Long {
        val acc = data.account(g.account)
        return if (acc != null) Money.convert(bal[acc.id] ?: 0, acc.currency, g.currency, data.settings)
        else g.saved.sumOf { it.amount }
    }

    /** Скільки відкладати щомісяця, щоб устигнути до дедлайну. */
    fun goalMonthly(g: Goal, saved: Long): Long? {
        val dl = g.deadline?.let { LocalDate.parse(it) } ?: return null
        val left = g.target - saved
        if (left <= 0) return 0
        val months = maxOf(1L, ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), dl.withDayOfMonth(1)) + 1)
        return (left + months - 1) / months
    }

    /** Доходи й витрати за останні [count] періодів (від старого до нового). */
    fun series(data: AppData, last: Period, count: Int): List<Triple<Period, Long, Long>> {
        val ps = ArrayList<Period>()
        var p = last
        repeat(count) { ps.add(0, p); p = p.prev(data.settings.monthStart) }
        val inc = LongArray(count); val exp = LongArray(count)
        for (t in data.txs) {
            if (t.type == TxType.TRANSFER) continue
            val d = LocalDate.parse(t.date)
            val i = ps.indexOfFirst { d in it }
            if (i < 0) continue
            val v = base(data, t)
            if (t.type == TxType.INCOME) inc[i] += v else exp[i] += v
        }
        return ps.mapIndexed { i, pp -> Triple(pp, inc[i], exp[i]) }
    }

    /** Наростаючі витрати по днях періоду (для графіка темпу). */
    fun cumulative(data: AppData, p: Period, category: String? = null): LongArray {
        val daily = LongArray(p.days)
        for (t in data.txs) {
            if (t.type != TxType.EXPENSE) continue
            if (category != null && t.category != category) continue
            val d = LocalDate.parse(t.date)
            if (d !in p) continue
            daily[ChronoUnit.DAYS.between(p.start, d).toInt()] += base(data, t)
        }
        for (i in 1 until daily.size) daily[i] += daily[i - 1]
        return daily
    }
}
