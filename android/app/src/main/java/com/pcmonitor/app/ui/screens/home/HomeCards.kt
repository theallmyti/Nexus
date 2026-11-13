package com.pcmonitor.app.ui.screens.home

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.problem
import com.pcmonitor.app.notification.NotificationHelper
import com.pcmonitor.app.ui.theme.CreamText
import com.pcmonitor.app.ui.theme.CreamTextSecondary
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.WhiteCardBg
import com.pcmonitor.app.ui.theme.WhiteCardBorder
import com.pcmonitor.app.widget.DevicesWidget

// Colour marks state only: red for problems, green for done. Everything else stays neutral.
private val ProblemRed = Color(0xFFB91C1C)
private val DoneGreen = Color(0xFF15803D)
private val Track = Color(0xFFE8DFC8)

/** Card title, in the app's original heading style (sentence case, bold). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFF94A3B8)) {
    Text(
        text = text,
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WhiteCardBg)
            .border(1.dp, WhiteCardBorder, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) { content() }
}

/** Devices over a limit, one tappable row each. Only shown when there is something to fix. */
@Composable
fun NeedsAttentionCard(devices: List<Device>, onDeviceClick: (String) -> Unit) {
    val problems = devices.mapNotNull { d -> d.problem()?.let { d to it } }
    if (problems.isEmpty()) return
    HomeCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Needs attention", color = ProblemRed)
                Spacer(modifier = Modifier.weight(1f))
                Text("${problems.size}", fontFamily = ReadableFontFamily, fontSize = 12.sp, color = CreamTextSecondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            problems.forEach { (device, problem) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDeviceClick(device.id) }
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ProblemRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.name, fontFamily = ReadableFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CreamText)
                        Text(problem, fontFamily = ReadableFontFamily, fontSize = 12.sp, color = ProblemRed)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open ${device.name}", tint = CreamTextSecondary)
                }
            }
        }
    }
}

private const val PREFS = "home_checklist"

/** Called by the alert-limits screen so the checklist can tick that step. */
fun markThresholdsReviewed(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("thresholds_seen", true).apply()
}

private data class Step(val label: String, val done: Boolean, val action: String, val onAction: () -> Unit)

/**
 * "Start here": the four things a new account needs, with progress and one clear next step.
 * Hides itself once everything is done or the user skips it.
 */
@Composable
fun StartHereCard(hasDevices: Boolean, onAddDevice: () -> Unit, onOpenThresholds: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    // Bumped on resume so steps finished in system dialogs or other screens tick off on return.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    if (tick < 0) return // reading tick re-runs this card on resume
    if (prefs.getBoolean("dismissed", false)) return

    val widgetIds = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, DevicesWidget::class.java))
    val steps = listOf(
        Step("Pair your first computer", hasDevices, "Pair", onAddDevice),
        Step("Allow alert notifications", NotificationHelper.canPostNotifications(context), "Allow") {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        Step("Review your alert limits", prefs.getBoolean("thresholds_seen", false), "Review") {
            markThresholdsReviewed(context); onOpenThresholds()
        },
        Step("Add the home-screen widget", widgetIds.isNotEmpty(), "Add") {
            val mgr = AppWidgetManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(context, DevicesWidget::class.java), null, null)
            }
        }
    )
    val doneCount = steps.count { it.done }
    if (doneCount == steps.size) return
    val next = steps.first { !it.done }

    HomeCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Start here", color = CreamTextSecondary)
                Spacer(modifier = Modifier.weight(1f))
                Text("$doneCount of ${steps.size} done", fontFamily = ReadableFontFamily, fontSize = 12.sp, color = CreamTextSecondary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Progress
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Track)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(doneCount / steps.size.toFloat())
                        .height(4.dp)
                        .background(DoneGreen)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (step.done) DoneGreen else Color.Transparent)
                            .border(1.5.dp, if (step.done) DoneGreen else CreamTextSecondary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (step.done) Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = step.label,
                        fontFamily = ReadableFontFamily,
                        fontSize = 14.sp,
                        fontWeight = if (step == next) FontWeight.Bold else FontWeight.Normal,
                        color = if (step.done) CreamTextSecondary else CreamText
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${next.action} →",
                    fontFamily = PixelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { next.onAction() }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
                Text(
                    text = "Skip for now",
                    fontFamily = ReadableFontFamily,
                    fontSize = 14.sp,
                    color = CreamTextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { prefs.edit().putBoolean("dismissed", true).apply(); tick++ }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}
