package com.pcmonitor.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.ArcadeYellowDark
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────
// Time of day
// ─────────────────────────────────────────────────────────

enum class SkyPhase { DAWN, DAY, DUSK, NIGHT }

/** Local clock, hours as a fraction (21.5 = 9:30 PM). No location needed. */
fun currentHourFraction(): Float {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
}

fun skyPhase(hour: Float = currentHourFraction()): SkyPhase = when {
    hour >= 5f && hour < 7f -> SkyPhase.DAWN
    hour >= 7f && hour < 17f -> SkyPhase.DAY
    hour >= 17f && hour < 19f -> SkyPhase.DUSK
    else -> SkyPhase.NIGHT
}

/** False when the user turned animations off (Settings → Accessibility → Remove animations). */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

private data class Palette(
    val skyTop: Color, val skyBottom: Color, val mountains: Color, val snow: Color,
    val forest: Color, val spires: Color, val lake: Color, val lakeHi: Color,
    val meadow: Color, val meadowTop: Color, val dirt: Color, val dirtHi: Color,
    val cloud: Color, val flowerAlpha: Float, val windowLit: Boolean
)

private fun paletteFor(phase: SkyPhase) = when (phase) {
    SkyPhase.DAY -> Palette(
        Color(0xFF4FC3F7), Color(0xFF81D4FA), Color(0xFF1E88E5), Color.White,
        Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF0288D1), Color(0xFF29B6F6),
        Color(0xFF4CAF50), Color(0xFF388E3C), Color(0xFF8D6E63), Color(0xFFA1887F),
        Color.White, 1f, false
    )
    SkyPhase.DAWN -> Palette(
        Color(0xFF5C6BC0), Color(0xFFFFAB91), Color(0xFF3F51B5), Color(0xFFFFE0B2),
        Color(0xFF2E5E33), Color(0xFF1E4423), Color(0xFF3949AB), Color(0xFFFFAB91),
        Color(0xFF43A047), Color(0xFF2E7D32), Color(0xFF795548), Color(0xFF8D6E63),
        Color(0xFFFFE0D6), 0.85f, true
    )
    SkyPhase.DUSK -> Palette(
        Color(0xFF4A148C), Color(0xFFFF7043), Color(0xFF311B92), Color(0xFFFFCCBC),
        Color(0xFF1B4D20), Color(0xFF123A16), Color(0xFF283593), Color(0xFFFF8A65),
        Color(0xFF33691E), Color(0xFF2A5718), Color(0xFF5D4037), Color(0xFF6D4C41),
        Color(0xFFFFCCBC), 0.7f, true
    )
    SkyPhase.NIGHT -> Palette(
        Color(0xFF070D1F), Color(0xFF1A2A52), Color(0xFF1A2E5A), Color(0xFFB0BEC5),
        Color(0xFF0F2A14), Color(0xFF0A1F0E), Color(0xFF0B2545), Color(0xFF2C4A7A),
        Color(0xFF1B3D1E), Color(0xFF153218), Color(0xFF4E3B33), Color(0xFF5D4A40),
        Color(0xFF37474F), 0.35f, true
    )
}

// ─────────────────────────────────────────────────────────
// Live landscape (splash background)
// ─────────────────────────────────────────────────────────

/**
 * The splash landscape, matching the real time: dawn, day, dusk or night colours, with the
 * sun or moon where it would be for the current hour. Ambient motion (drifting clouds, lake
 * shimmer, twinkling stars, fireflies, one passing bird) stops when animations are off.
 */
@Composable
fun PixelLiveScene(modifier: Modifier = Modifier, hour: Float = currentHourFraction()) {
    val phase = skyPhase(hour)
    val p = paletteFor(phase)
    val motion = rememberMotionEnabled()

    val loop = rememberInfiniteTransition(label = "ambient")
    val tAnim by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "t"
    )
    val t = if (motion) tAnim else 0f

    // One bird crosses during the day, once, shortly after the screen appears.
    val bird = remember { Animatable(-0.1f) }
    LaunchedEffect(motion, phase) {
        if (motion && phase != SkyPhase.NIGHT) {
            delay(900)
            bird.animateTo(1.15f, tween(7_000, easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val px = 4.dp.toPx()

        // Sky
        drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom), 0f, h * 0.45f), Offset.Zero, Size(w, h * 0.45f))

        // Stars (night, dimmer at dawn/dusk), twinkling
        if (phase != SkyPhase.DAY) {
            val starAlpha = if (phase == SkyPhase.NIGHT) 1f else 0.35f
            for (i in 0 until 28) {
                val sx = ((i * 137) % 100) / 100f * w
                val sy = ((i * 71) % 100) / 100f * h * 0.36f
                val tw = 0.55f + 0.45f * sin(2 * PI.toFloat() * (t * 9f + i * 0.37f))
                drawRect(Color.White.copy(alpha = starAlpha * tw), Offset(sx, sy), Size(px * 0.6f, px * 0.6f))
            }
        }

        // Sun by day, moon by night, on an arc that follows the hour (behind the mountains when low).
        if (phase == SkyPhase.NIGHT) {
            val prog = (((hour - 19f) + 24f) % 24f) / 10f
            val mx = w * (0.1f + 0.8f * prog)
            val my = h * (0.32f - 0.24f * sin(PI.toFloat() * prog))
            // Pixel full moon: a stepped disc with a few craters and a soft halo.
            drawCircle(Color(0x1FFFFFFF), px * 9f, Offset(mx, my))
            val moon = Color(0xFFF5F3CE)
            drawRect(moon, Offset(mx - px * 2, my - px * 4), Size(px * 4, px * 8))
            drawRect(moon, Offset(mx - px * 3, my - px * 3), Size(px * 6, px * 6))
            drawRect(moon, Offset(mx - px * 4, my - px * 2), Size(px * 8, px * 4))
            val crater = Color(0xFFDAD6A8)
            drawRect(crater, Offset(mx - px * 2, my - px * 2), Size(px * 2, px * 2))
            drawRect(crater, Offset(mx + px * 1, my + px * 1), Size(px * 1.5f, px * 1.5f))
            drawRect(crater, Offset(mx + px * 1.5f, my - px * 2.5f), Size(px, px))
        } else {
            val prog = ((hour - 5f) / 14f).coerceIn(0f, 1f)
            val sx = w * (0.1f + 0.8f * prog)
            val sy = h * (0.36f - 0.26f * sin(PI.toFloat() * prog))
            val sunColor = if (phase == SkyPhase.DAY) Color(0xFFFFEB3B) else Color(0xFFFF8A65)
            drawCircle(sunColor.copy(alpha = 0.25f), px * 8f, Offset(sx, sy))
            drawRect(sunColor, Offset(sx - px * 3.5f, sy - px * 3.5f), Size(px * 7, px * 7))
        }

        // Drifting clouds
        fun cloud(baseX: Float, y: Float, cw: Float, speed: Float) {
            val x = ((baseX + t * speed) % 1.3f - 0.15f) * w
            drawRect(p.cloud.copy(alpha = if (phase == SkyPhase.NIGHT) 0.5f else 1f), Offset(x, y), Size(w * cw, h * 0.03f))
            drawRect(p.cloud.copy(alpha = if (phase == SkyPhase.NIGHT) 0.5f else 1f), Offset(x + w * cw * 0.16f, y - h * 0.02f), Size(w * cw * 0.68f, h * 0.025f))
        }
        cloud(0.10f, h * 0.08f, 0.25f, 0.6f)
        cloud(0.65f, h * 0.05f, 0.28f, 0.4f)

        // Bird: a little "v" crossing the sky
        if (bird.value in 0f..1.1f) {
            val bx = bird.value * w
            val by = h * 0.16f + sin(bird.value * 18f) * px
            val wing = if ((bird.value * 40).toInt() % 2 == 0) px else -px * 0.3f
            drawRect(Color(0xFF263238), Offset(bx, by), Size(px, px))
            drawRect(Color(0xFF263238), Offset(bx - px, by - wing), Size(px, px))
            drawRect(Color(0xFF263238), Offset(bx + px, by - wing), Size(px, px))
        }

        // Mountains
        val mountain = Path().apply {
            moveTo(0f, h * 0.45f); lineTo(w * 0.15f, h * 0.28f); lineTo(w * 0.35f, h * 0.42f)
            lineTo(w * 0.55f, h * 0.22f); lineTo(w * 0.75f, h * 0.38f); lineTo(w * 0.90f, h * 0.26f)
            lineTo(w, h * 0.40f); lineTo(w, h * 0.55f); lineTo(0f, h * 0.55f); close()
        }
        drawPath(mountain, p.mountains)
        drawRect(p.snow, Offset(w * 0.52f, h * 0.22f), Size(w * 0.06f, h * 0.035f))
        drawRect(p.snow.copy(alpha = 0.9f), Offset(w * 0.13f, h * 0.28f), Size(w * 0.04f, h * 0.025f))
        drawRect(p.snow.copy(alpha = 0.9f), Offset(w * 0.88f, h * 0.26f), Size(w * 0.04f, h * 0.025f))

        // Forest
        drawRect(p.forest, Offset(0f, h * 0.42f), Size(w, h * 0.16f))
        for (i in 0..20) drawRect(p.spires, Offset(i * (w / 20f), h * 0.40f), Size(w / 35f, h * 0.04f))

        // Lake with a moving shimmer
        drawRect(p.lake, Offset(0f, h * 0.52f), Size(w, h * 0.15f))
        drawRect(p.lakeHi, Offset(w * 0.1f, h * 0.54f), Size(w * 0.8f, h * 0.04f))
        for (i in 0 until 6) {
            val shimmerX = (w * (i / 6f + t * 3f)) % w
            drawRect(Color.White.copy(alpha = 0.18f), Offset(shimmerX, h * (0.60f + (i % 3) * 0.018f)), Size(w * 0.06f, px * 0.5f))
        }

        // Cabin; its window glows from dusk to dawn
        drawRect(Color(0xFF5D4037), Offset(w * 0.72f, h * 0.50f), Size(w * 0.18f, h * 0.05f))
        drawRect(Color(0xFF8D6E63), Offset(w * 0.70f, h * 0.47f), Size(w * 0.22f, h * 0.035f))
        val windowCenter = Offset(w * 0.80f, h * 0.5225f)
        if (p.windowLit) drawCircle(Color(0x26FFD54F), w * 0.035f, windowCenter)
        drawRect(if (p.windowLit) Color(0xFFFFD54F) else Color(0xFFFFF176), Offset(w * 0.78f, h * 0.51f), Size(w * 0.04f, h * 0.025f))

        // Meadow and path
        drawRect(p.meadow, Offset(0f, h * 0.65f), Size(w, h * 0.35f))
        drawRect(p.meadowTop, Offset(0f, h * 0.65f), Size(w, h * 0.03f))
        drawRect(p.dirt, Offset(0f, h * 0.80f), Size(w * 0.55f, h * 0.20f))
        drawRect(p.dirtHi, Offset(0f, h * 0.82f), Size(w * 0.50f, h * 0.14f))

        val flowers = listOf(Color(0xFFE53935), Color(0xFFFFEB3B), Color(0xFFFFFFFF), Color(0xFFAB47BC))
        for (i in 0..12) {
            drawRect(
                flowers[i % flowers.size].copy(alpha = p.flowerAlpha),
                Offset(w * 0.05f + i * w * 0.075f, h * 0.72f + (i % 4) * h * 0.05f),
                Size(px, px)
            )
        }

        // Fireflies drifting over the meadow at night
        if (phase == SkyPhase.NIGHT || phase == SkyPhase.DUSK) {
            for (i in 0 until 9) {
                val a = 2 * PI.toFloat() * (t * (2f + i % 3) + i * 0.29f)
                val fx = w * (0.08f + i * 0.105f) + cos(a) * px * 4
                val fy = h * (0.70f + (i % 4) * 0.045f) + sin(a * 1.3f) * px * 3
                val glow = 0.4f + 0.6f * ((sin(a * 2f) + 1f) / 2f)
                drawCircle(Color(0xFFFFF59D).copy(alpha = 0.12f * glow), px * 1.2f, Offset(fx, fy))
                drawRect(Color(0xFFFFF59D).copy(alpha = glow), Offset(fx - px * 0.4f, fy - px * 0.4f), Size(px * 0.8f, px * 0.8f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// The adventurer, drawn so his front arm can move
// ─────────────────────────────────────────────────────────

/**
 * Draws the hat-wearing adventurer at [origin] on a 16-unit grid of size [u].
 * [armDeg] rotates the front arm about the shoulder: 0 hangs down, -90 points ahead, -140 is raised.
 */
private fun DrawScope.drawAdventurer(
    origin: Offset, u: Float, armDeg: Float,
    holdingPhone: Boolean = false, lantern: Boolean = false, lanternGlow: Float = 1f
) {
    fun r(c: Color, x: Float, y: Float, w: Float, h: Float) =
        drawRect(c, Offset(origin.x + x * u, origin.y + y * u), Size(w * u, h * u))

    // Lantern in the back hand at night
    if (lantern) {
        val lc = Offset(origin.x + 2.2f * u, origin.y + 13.6f * u)
        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.14f * lanternGlow), u * 2.6f, lc)
        r(Color(0xFF424242), 1.9f, 11.6f, 0.6f, 1.0f)
        r(Color(0xFFFFD54F), 1.4f, 12.6f, 1.6f, 1.8f)
    }
    r(Color(0xFFF9A825), 5f, 1f, 6f, 3f)          // hat crown
    r(Color(0xFFFBC02D), 3f, 4f, 10f, 1.5f)       // brim
    r(Color(0xFFC62828), 5f, 3.5f, 6f, 0.8f)      // ribbon
    r(Color(0xFFFFCC80), 5f, 5.5f, 6f, 3.5f)      // face
    r(Color(0xFF212121), 8f, 6.5f, 1.5f, 1.5f)    // eye
    r(Color(0xFF6D4C41), 2f, 8.5f, 2.5f, 4.5f)    // backpack
    r(Color(0xFF8D6E63), 2.5f, 9.5f, 1.5f, 2.5f)
    r(Color(0xFF1E88E5), 4f, 9f, 7f, 4f)          // coat
    r(Color(0xFF4E342E), 5f, 13f, 2.5f, 3f)       // legs
    r(Color(0xFF4E342E), 8f, 13f, 2.5f, 3f)
    r(Color(0xFF212121), 5f, 15.5f, 3f, 0.8f)     // shoes
    r(Color(0xFF212121), 8f, 15.5f, 3.5f, 0.8f)

    // Front arm, rotating about the shoulder
    val pivot = Offset(origin.x + 10.5f * u, origin.y + 9.6f * u)
    rotate(armDeg, pivot) {
        drawRect(Color(0xFF1565C0), Offset(pivot.x - 0.75f * u, pivot.y), Size(1.5f * u, 3.4f * u))
        drawRect(Color(0xFFFFCC80), Offset(pivot.x - 0.75f * u, pivot.y + 3.4f * u), Size(1.5f * u, 1.2f * u))
        if (holdingPhone) {
            drawRect(Color(0xFF263238), Offset(pivot.x - 1.1f * u, pivot.y + 4.2f * u), Size(2.2f * u, 1.3f * u))
            drawRect(Color(0xFF80DEEA), Offset(pivot.x - 0.8f * u, pivot.y + 4.4f * u), Size(1.6f * u, 0.9f * u))
        }
    }
}

/** Splash adventurer: waves hello a few times on arrival, then again every few seconds; lantern at night. */
@Composable
fun WavingCharacter(modifier: Modifier = Modifier, size: Dp = 64.dp, phase: SkyPhase = skyPhase()) {
    val motion = rememberMotionEnabled()
    val arm = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        delay(250)
        while (true) {
            arm.animateTo(-140f, tween(320))
            repeat(3) {
                arm.animateTo(-105f, tween(200))
                arm.animateTo(-155f, tween(200))
            }
            arm.animateTo(0f, tween(380))
            delay(4_000)
        }
    }
    val flicker by rememberInfiniteTransition(label = "lantern").animateFloat(
        0.7f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "flicker"
    )
    val night = phase == SkyPhase.NIGHT || phase == SkyPhase.DUSK
    Canvas(modifier = modifier.size(size)) {
        drawAdventurer(Offset.Zero, this.size.width / 16f, arm.value, lantern = night, lanternGlow = if (motion) flicker else 1f)
    }
}

/** The orange cat: tail swishes, blinks now and then. */
@Composable
fun AnimatedCat(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val motion = rememberMotionEnabled()
    val loop = rememberInfiniteTransition(label = "cat")
    val tail by loop.animateFloat(-14f, 14f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tail")
    val eye by loop.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 3_600
            1f at 3_300; 0.1f at 3_400; 1f at 3_500
        }),
        label = "blink"
    )
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 12f
        val orange = Color(0xFFFB8C00)
        val dark = Color(0xFFE65100)
        fun r(c: Color, x: Float, y: Float, w: Float, h: Float) = drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))

        rotate(if (motion) tail else 0f, Offset(2.6f * u, 10.5f * u)) {
            r(dark, 1.5f, 6.5f, 1.5f, 4f)
            r(dark, 2f, 5.5f, 1.5f, 1.5f)
        }
        r(dark, 4f, 2f, 1.5f, 2f)
        r(dark, 7.5f, 2f, 1.5f, 2f)
        r(orange, 3.5f, 3.5f, 6f, 4f)
        val eyeH = 1.2f * (if (motion) eye else 1f)
        r(Color(0xFF212121), 7f, 4.5f + (1.2f - eyeH) / 2f, 1.2f, eyeH)
        r(orange, 3f, 7f, 6f, 4f)
        r(Color.White, 6.5f, 7.5f, 2f, 3f)
    }
}

// ─────────────────────────────────────────────────────────
// Add Device: the adventurer scanning a QR code off the monitor
// ─────────────────────────────────────────────────────────

private val QR = arrayOf(
    "1111111", "1000101", "1011101", "1010001", "1011101", "1000001", "1111111"
)

/** Monitor showing a QR code; the adventurer points his phone at it and a scan beam sweeps. */
@Composable
fun PixelScanScene(modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val loop = rememberInfiniteTransition(label = "scan")
    val sweep by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    val beam by loop.animateFloat(0.10f, 0.28f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beam")
    val bob by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "bob")

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val u = h / 16f

        // Monitor (left)
        val mx = w * 0.04f
        drawRect(ArcadeYellow, Offset(mx, u * 1f), Size(u * 12, u * 9))
        drawRect(Color(0xFF0F1B2E), Offset(mx + u, u * 2f), Size(u * 10, u * 7))
        val screen = Offset(mx + u * 1.6f, u * 2.6f)
        val screenSize = Size(u * 8.8f, u * 5.8f)
        drawRect(Color(0xFFF5F5F0), screen, screenSize)
        val cell = u * 0.72f
        val qrOrigin = Offset(screen.x + (screenSize.width - cell * 7) / 2, screen.y + (screenSize.height - cell * 7) / 2)
        QR.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                if (ch == '1') drawRect(Color(0xFF212121), Offset(qrOrigin.x + col * cell, qrOrigin.y + row * cell), Size(cell, cell))
            }
        }
        // Scan line moving over the code
        val sy = qrOrigin.y + (if (motion) sweep else 0.5f) * cell * 7
        drawRect(Color(0xFF4CAF50).copy(alpha = 0.85f), Offset(qrOrigin.x - u * 0.4f, sy), Size(cell * 7 + u * 0.8f, u * 0.35f))
        drawRect(ArcadeYellowDark, Offset(mx + u * 5, u * 10), Size(u * 2, u * 2))  // neck
        drawRect(ArcadeYellow, Offset(mx + u * 3, u * 12), Size(u * 6, u * 2))   // base

        // Adventurer (right), facing the monitor, arm out holding the phone
        val charLeft = w * 0.60f
        val charTop = u * 0f - (if (motion) bob else 0f) * u * 0.3f
        val cx = charLeft + 8f * u // flip pivot = character centre
        val pivot = Offset(charLeft + 10.5f * u, charTop + 9.6f * u)
        // Hand position for armDeg = -90 (pointing ahead), then mirrored about cx
        val handLocal = Offset(pivot.x + 4.8f * u, pivot.y)
        val hand = Offset(2 * cx - handLocal.x, handLocal.y)

        // Beam from phone to screen
        val beamPath = Path().apply {
            moveTo(hand.x, hand.y - u * 0.5f)
            lineTo(screen.x + screenSize.width, screen.y)
            lineTo(screen.x + screenSize.width, screen.y + screenSize.height)
            lineTo(hand.x, hand.y + u * 0.5f)
            close()
        }
        drawPath(beamPath, Color(0xFF66BB6A).copy(alpha = if (motion) beam else 0.18f))

        withTransform({ scale(-1f, 1f, pivot = Offset(cx, h / 2)) }) {
            drawAdventurer(Offset(charLeft, charTop), u, -90f, holdingPhone = true)
        }
    }
}

