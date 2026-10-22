package ua.x44to.kopiyka.data

import kotlin.math.abs
import kotlin.math.roundToLong

/** Валюти, курси, формат сум, калькулятор для введення. */
object Money {
    class Cur(val code: String, val symbol: String)

    val CURRENCIES = listOf(
        Cur("UAH", "₴"), Cur("USD", "$"), Cur("EUR", "€"), Cur("PLN", "zł"), Cur("GBP", "£"),
        Cur("CZK", "Kč"), Cur("CHF", "CHF"), Cur("CAD", "C$"), Cur("TRY", "₺"), Cur("MDL", "L"),
    )

    /** Приблизні курси (гривень за одиницю). Застосунок без інтернету — курси змінює користувач. */
    val DEFAULT_RATES: Map<String, Double> = mapOf(
        "UAH" to 1.0, "USD" to 44.0, "EUR" to 51.5, "PLN" to 12.1, "GBP" to 59.5,
        "CZK" to 2.07, "CHF" to 55.0, "CAD" to 32.0, "TRY" to 1.05, "MDL" to 2.5,
    )

    fun symbol(code: String) = CURRENCIES.firstOrNull { it.code == code }?.symbol ?: code

    /** Перевести суму між валютами за курсами з налаштувань. */
    fun convert(amount: Long, from: String, to: String, s: Settings): Long {
        if (from == to) return amount
        val rf = s.rates[from] ?: DEFAULT_RATES[from] ?: 1.0
        val rt = s.rates[to] ?: DEFAULT_RATES[to] ?: 1.0
        return (amount * rf / rt).roundToLong()
    }

    /**
     * 123456 → «1 234,56 ₴» (укр.) або «₴1,234.56» (англ.). Нульові копійки не показуємо.
     * sign = true додає «+» до додатних.
     */
    fun format(minor: Long, code: String, sign: Boolean = false, hide: Boolean = false, cents: Boolean? = null): String {
        val sym = symbol(code)
        if (hide) return if (L.ukr) "•••• $sym" else "$sym••••"
        val neg = minor < 0
        val a = abs(minor)
        val whole = a / 100
        val frac = (a % 100).toInt()
        val groupSep = if (L.ukr) " " else ","
        val decSep = if (L.ukr) "," else "."
        val w = whole.toString().reversed().chunked(3).joinToString(groupSep).reversed()
        val showCents = cents ?: (frac != 0)
        val num = if (showCents) "$w$decSep${frac.toString().padStart(2, '0')}" else w
        val s = if (neg) "−" else if (sign && minor > 0) "+" else ""
        return if (L.ukr) "$s$num $sym" else "$s$sym$num"
    }

    /** Коротко для графіків: 12,3 тис. / 1,2 млн. */
    fun short(minor: Long, code: String): String {
        val v = abs(minor) / 100.0
        val sym = symbol(code)
        val (n, suf) = when {
            v >= 1_000_000 -> v / 1_000_000 to if (L.ukr) " млн" else "M"
            v >= 10_000 -> v / 1_000 to if (L.ukr) " тис" else "K"
            else -> v to ""
        }
        val txt = if (suf.isEmpty()) "%.0f".format(n) else "%.1f".format(n)
        val t = (if (L.ukr) txt.replace('.', ',') else txt) + suf
        return (if (minor < 0) "−" else "") + if (L.ukr) "$t $sym" else "$sym$t"
    }

    /** Сума з текстового поля: «1 250,5» → 125050. */
    fun parse(text: String): Long? {
        val t = text.replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        val d = t.toDoubleOrNull() ?: return null
        if (d < 0 || d > 1e12) return null
        return (d * 100).roundToLong()
    }

    /** Для поля редагування: 125050 → «1250,5». */
    fun plain(minor: Long): String {
        val whole = minor / 100; val frac = (minor % 100).toInt()
        val dec = if (L.ukr) "," else "."
        return when {
            frac == 0 -> whole.toString()
            frac % 10 == 0 -> "$whole$dec${frac / 10}"
            else -> "$whole$dec${frac.toString().padStart(2, '0')}"
        }
    }

    /**
     * Вираз із клавіатури-калькулятора: «250+80×2» → 41000 копійок.
     * Множення й ділення раніше за додавання й віднімання. Повертає null, якщо вираз неповний.
     */
    fun eval(expr: String): Long? {
        if (expr.isEmpty()) return 0
        val nums = ArrayList<Double>()
        val ops = ArrayList<Char>()
        var cur = StringBuilder()
        for (ch in expr) {
            when (ch) {
                in '0'..'9' -> cur.append(ch)
                ',', '.' -> cur.append('.')
                '+', '−', '×', '÷' -> {
                    nums.add(cur.toString().toDoubleOrNull() ?: return null)
                    ops.add(ch); cur = StringBuilder()
                }
            }
        }
        if (cur.isEmpty()) { if (ops.isNotEmpty()) ops.removeAt(ops.size - 1) else return 0 }
        else nums.add(cur.toString().toDoubleOrNull() ?: return null)
        // спочатку × і ÷
        val n2 = ArrayList<Double>(); val o2 = ArrayList<Char>()
        n2.add(nums[0])
        for (i in ops.indices) {
            val b = nums[i + 1]
            when (ops[i]) {
                '×' -> n2[n2.size - 1] = n2.last() * b
                '÷' -> n2[n2.size - 1] = if (b == 0.0) return null else n2.last() / b
                else -> { o2.add(ops[i]); n2.add(b) }
            }
        }
        var r = n2[0]
        for (i in o2.indices) r = if (o2[i] == '+') r + n2[i + 1] else r - n2[i + 1]
        if (r < 0 || r > 1e12) return null
        return (r * 100).roundToLong()
    }
}
