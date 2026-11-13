package com.pcmonitor.app.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.RetroProgressBar
import com.pcmonitor.app.ui.theme.CreamBorderLight
import com.pcmonitor.app.ui.theme.CreamGreenAccent
import com.pcmonitor.app.ui.theme.CreamSurface
import com.pcmonitor.app.ui.theme.CreamText
import com.pcmonitor.app.ui.theme.CreamTextSecondary
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily

/** One physical core: its temperature and the average load of its threads. */
data class CoreReading(val temp: Float, val load: Float)

/**
 * Group per-thread readings into physical cores. The agent sends one temperature per logical
 * processor; on SMT chips (2 threads per core) each pair shares its core's sensor, so adjacent
 * pairs carry the same value. If the pairs don't match (no SMT, or hybrid Intel P/E cores),
 * each thread is shown as its own row.
 */
fun groupCores(temps: List<Float>, loads: List<Float>): Pair<List<CoreReading>, Boolean> {
    val n = minOf(temps.size, loads.size)
    val smt = n >= 2 && n % 2 == 0 && (0 until n step 2).all { kotlin.math.abs(temps[it] - temps[it + 1]) <= 1.5f }
    return if (smt) {
        (0 until n step 2).map { CoreReading(maxOf(temps[it], temps[it + 1]), (loads[it] + loads[it + 1]) / 2f) } to true
    } else {
        (0 until n).map { CoreReading(temps[it], loads[it]) } to false
    }
}

/**
 * Min / max per core and the low / high of the all-core average, since this device's screen
 * was opened (that is when the agent streams live data). Reset clears it.
 */
class CoreTempStats {
    private var min = FloatArray(0)
    private var max = FloatArray(0)
    var avgLow = Float.NaN
        private set
    var avgHigh = Float.NaN
        private set
    /** Bumped on every change so the table recomposes. */
    var version by mutableIntStateOf(0)
        private set

    fun update(cores: List<Float>) {
        if (cores.isEmpty()) return
        if (min.size != cores.size) reset(cores.size)
        cores.forEachIndexed { i, t ->
            if (t < min[i]) min[i] = t
            if (t > max[i]) max[i] = t
        }
        val avg = cores.average().toFloat()
        if (avgLow.isNaN() || avg < avgLow) avgLow = avg
        if (avgHigh.isNaN() || avg > avgHigh) avgHigh = avg
        version++
    }

    fun minOf(i: Int) = min.getOrElse(i) { Float.NaN }
    fun maxOf(i: Int) = max.getOrElse(i) { Float.NaN }

    fun reset(size: Int = min.size) {
        min = FloatArray(size) { Float.MAX_VALUE }
        max = FloatArray(size) { -Float.MAX_VALUE }
        avgLow = Float.NaN
        avgHigh = Float.NaN
        version++
    }
}

private val Hot = Color(0xFFB91C1C)
private val Warm = Color(0xFFC2410C)

private fun tempColor(t: Float) = when {
    t.isNaN() -> CreamTextSecondary
    t >= 85f -> Hot
    t >= 75f -> Warm
    else -> CreamText
}

/**
 * Per-core temperature table:
 *   Core   Now  Min  Max  [load]
 *   ...
 *   All    avg  low  high
 */
@Composable
fun CoreTempTable(
    cores: List<CoreReading>,
    smt: Boolean,
    stats: CoreTempStats,
    useFahrenheit: Boolean
) {
    @Suppress("UNUSED_VARIABLE") val v = stats.version // recompose when stats change
    fun fmt(t: Float): String = when {
        t.isNaN() || t == Float.MAX_VALUE || t == -Float.MAX_VALUE -> "--"
        useFahrenheit -> "${(t * 9f / 5f + 32).toInt()}°"
        else -> "${t.toInt()}°"
    }
    val labelW = 58.dp
    val colW = 40.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CreamSurface)
            .border(1.dp, CreamBorderLight, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (smt) "${cores.size} cores · ${cores.size * 2} threads" else "${cores.size} cores",
                fontFamily = ReadableFontFamily,
                fontSize = 11.sp,
                color = CreamTextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Reset",
                fontFamily = ReadableFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PixelGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { stats.reset() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        // Column headings
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Heading("Core", labelW, TextAlign.Start)
            Heading("Now", colW)
            Heading("Min", colW)
            Heading("Max", colW)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Load", fontFamily = ReadableFontFamily, fontSize = 10.sp, color = CreamTextSecondary, modifier = Modifier.weight(1f))
        }
        cores.forEachIndexed { i, c ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cell("Core ${i + 1}", labelW, CreamText, TextAlign.Start)
                Cell(fmt(c.temp), colW, tempColor(c.temp))
                Cell(fmt(stats.minOf(i)), colW, CreamTextSecondary)
                Cell(fmt(stats.maxOf(i)), colW, tempColor(stats.maxOf(i)))
                Spacer(modifier = Modifier.width(10.dp))
                RetroProgressBar(
                    progress = (c.load / 100f).coerceIn(0f, 1f),
                    color = CreamGreenAccent,
                    trackColor = Color(0xFFE5DAC0),
                    modifier = Modifier.weight(1f),
                    height = 6.dp
                )
                Text(
                    "${c.load.toInt()}%",
                    fontFamily = PixelFontFamily,
                    fontSize = 8.sp,
                    color = CreamTextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
        // All-core average: now, and the lowest / highest the average has been
        Spacer(modifier = Modifier.height(4.dp))
        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(CreamBorderLight))
        Spacer(modifier = Modifier.height(6.dp))
        val avgNow = cores.map { it.temp }.average().toFloat()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Cell("Average", labelW, CreamText, TextAlign.Start, bold = true)
            Cell(fmt(avgNow), colW, tempColor(avgNow), bold = true)
            Cell(fmt(stats.avgLow), colW, CreamTextSecondary, bold = true)
            Cell(fmt(stats.avgHigh), colW, tempColor(stats.avgHigh), bold = true)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Spacer(modifier = Modifier.width(labelW + colW))
            Text("Low avg", fontFamily = ReadableFontFamily, fontSize = 9.sp, color = CreamTextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(colW))
            Text("High avg", fontFamily = ReadableFontFamily, fontSize = 9.sp, color = CreamTextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(colW))
        }
        Text(
            text = "Min, max and averages since you opened this computer.",
            fontFamily = ReadableFontFamily,
            fontSize = 10.sp,
            color = CreamTextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Heading(text: String, width: Dp, align: TextAlign = TextAlign.Center) {
    Text(text, fontFamily = ReadableFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = CreamTextSecondary, textAlign = align, modifier = Modifier.width(width))
}

@Composable
private fun Cell(text: String, width: Dp, color: Color, align: TextAlign = TextAlign.Center, bold: Boolean = false) {
    Text(
        text,
        fontFamily = PixelFontFamily,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (align == TextAlign.Start) 8.sp else 9.sp,
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = align,
        modifier = Modifier.width(width)
    )
}
