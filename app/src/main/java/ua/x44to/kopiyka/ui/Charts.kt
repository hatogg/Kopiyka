package ua.x44to.kopiyka.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Кільцева діаграма: частки категорій із невеликими проміжками. */
@Composable
fun Donut(slices: List<Pair<Color, Long>>, modifier: Modifier = Modifier, thickness: Dp = 26.dp, center: @Composable BoxScope.() -> Unit = {}) {
    val total = max(1L, slices.sumOf { it.second })
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val d = min(size.width, size.height) - stroke
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            val sz = Size(d, d)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(stroke))
            val gap = if (slices.count { it.second > 0 } > 1) 2.2f else 0f
            var start = -90f
            for ((c, v) in slices) {
                val sweep = 360f * v / total * anim.value
                if (sweep > gap + 0.3f) drawArc(c, start + gap / 2, sweep - gap, false, tl, sz, style = Stroke(stroke))
                start += sweep
            }
        }
        center()
    }
}

/** Доходи (ліворуч) і витрати (праворуч) по місяцях. */
@Composable
fun IncomeExpenseBars(
    labels: List<String>, income: List<Long>, expense: List<Long>,
    incomeCol: Color, expenseCol: Color, modifier: Modifier = Modifier, highlight: Int = labels.lastIndex,
) {
    val maxV = max(1L, max(income.maxOrNull() ?: 0, expense.maxOrNull() ?: 0))
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(income, expense) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = labels.size
            if (n == 0) return@Canvas
            val groupW = size.width / n
            val barW = min(groupW * 0.3f, 16.dp.toPx())
            val gap = 3.dp.toPx()
            for (i in 0..3) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            drawLine(grid.copy(alpha = 1f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            for (i in 0 until n) {
                val a = if (i == highlight) 1f else 0.55f
                val cx = groupW * (i + 0.5f)
                val hi = size.height * income[i] / maxV * anim.value
                val he = size.height * expense[i] / maxV * anim.value
                val r = CornerRadius(barW / 2, barW / 2)
                if (hi > 0) drawRoundRect(incomeCol.copy(alpha = a), Offset(cx - gap / 2 - barW, size.height - hi), Size(barW, hi), r)
                if (he > 0) drawRoundRect(expenseCol.copy(alpha = a), Offset(cx + gap / 2, size.height - he), Size(barW, he), r)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Темп витрат: наростаючий підсумок цього періоду (до сьогодні) проти минулого, і лінія ліміту.
 * [upTo] — скільки днів поточного періоду вже минуло (лінія обривається на сьогодні).
 */
@Composable
fun PaceChart(cur: LongArray, upTo: Int, prev: LongArray, limit: Long?, curCol: Color, prevCol: Color, limitCol: Color, modifier: Modifier = Modifier) {
    val days = max(cur.size, prev.size)
    val maxV = max(1L, listOf(cur.take(upTo).maxOrNull() ?: 0L, prev.maxOrNull() ?: 0L, limit ?: 0L).max()) * 1.08f
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(cur.contentHashCode(), prev.contentHashCode()) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (days < 2) return@Canvas
        fun x(i: Int) = size.width * i / (days - 1).toFloat()
        fun y(v: Long) = size.height - size.height * (v / maxV)
        for (i in 0..3) {
            val yy = size.height * i / 4f
            drawLine(grid, Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        if (prev.isNotEmpty()) {
            val p = Path()
            prev.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
            drawPath(p, prevCol, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        if (limit != null && limit > 0) {
            val ly = y(limit)
            drawLine(limitCol, Offset(0f, ly), Offset(size.width, ly), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }
        val n = min(upTo, cur.size)
        if (n > 0) {
            val shown = max(1, (n * anim.value).toInt())
            val line = Path()
            val area = Path()
            area.moveTo(x(0), size.height)
            for (i in 0 until shown) {
                val px = x(i); val py = y(cur[i])
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
                area.lineTo(px, py)
            }
            area.lineTo(x(shown - 1), size.height)
            area.close()
            drawPath(area, curCol.copy(alpha = 0.14f))
            drawPath(line, curCol, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(curCol, 5.dp.toPx(), Offset(x(shown - 1), y(cur[shown - 1])))
            drawCircle(Color.White, 2.dp.toPx(), Offset(x(shown - 1), y(cur[shown - 1])))
        }
    }
}

@Composable
fun Legend(color: Color, text: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
        } else Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
