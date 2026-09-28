package dev.mangelov.tune

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.LocalActivity
import dev.mangelov.tune.dsp.Note
import dev.mangelov.tune.dsp.Tuning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.Locale

private val Deep = Color(0xFF07130F)
private val Panel = Color(0xFF10241C)
private val Lime = Color(0xFFB4FF78)
private val Mint = Color(0xFF65E6A4)
private val Ink = Color(0xFFECF8EE)
private val Muted = Color(0xFF92AE9C)
private val DarkColors = darkColorScheme(primary = Lime, onPrimary = Deep, background = Deep,
    onBackground = Ink, surface = Panel, onSurface = Ink, surfaceVariant = Color(0xFF18362A), outline = Muted)
private val LightColors = lightColorScheme(primary = Color(0xFF126D45), onPrimary = Color.White,
    background = Color(0xFFF0F8F1), onBackground = Color(0xFF102A1C), surface = Color.White,
    onSurface = Color(0xFF102A1C), surfaceVariant = Color(0xFFDCEFE2), outline = Color(0xFF5A7A66))

@Composable
fun TunerScreen(model: TunerViewModel, onAllow: () -> Unit, onSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val locked by model.lockedString.collectAsStateWithLifecycle()
    val view = LocalView.current
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            val bars = WindowInsetsControllerCompat(window, window.decorView)
            bars.isAppearanceLightStatusBars = !settings.darkTheme
            bars.isAppearanceLightNavigationBars = !settings.darkTheme
            window.isNavigationBarContrastEnforced = false
        }
    }
    val active = state is TunerUiState.Listening || state is TunerUiState.Detected
    DisposableEffect(view, active, settings.keepScreenOn) {
        view.keepScreenOn = active && settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    var lastHaptic by remember { mutableLongStateOf(0L) }
    var wasInTune by remember { mutableStateOf(false) }
    val detected = state as? TunerUiState.Detected
    LaunchedEffect(detected?.inTune, detected?.noteName) {
        val inTune = detected?.inTune == true
        if (inTune && !wasInTune && settings.haptics && SystemClock.elapsedRealtime() - lastHaptic >= 800L) {
            if (Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            lastHaptic = SystemClock.elapsedRealtime()
        }
        wasInTune = inTune
    }
    MaterialTheme(colorScheme = if (settings.darkTheme) DarkColors else LightColors) {
        when (state) {
            TunerUiState.NeedsPermission -> MessageScreen(stringResource(R.string.permission_title), stringResource(R.string.permission_body), stringResource(R.string.allow), onAllow)
            TunerUiState.PermissionDeniedPermanently -> MessageScreen(stringResource(R.string.permission_title), stringResource(R.string.permission_body), stringResource(R.string.settings), onSettings)
            else -> TunerContent(state, settings, locked, model)
        }
    }
}

@Composable
private fun MessageScreen(title: String, body: String, action: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(body, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunerContent(state: TunerUiState, settings: TunerSettings, locked: Int?, model: TunerViewModel) {
    var showTunings by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val tuning = Tuning.ALL[settings.tuningIndex]
    val detected = state as? TunerUiState.Detected
    val background = MaterialTheme.colorScheme.background
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = if (settings.darkTheme) 0.09f else 0.04f)
    Box(Modifier.fillMaxSize().background(background).background(Brush.verticalGradient(listOf(glow, Color.Transparent, Color.Transparent))).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TUNE", color = MaterialTheme.colorScheme.primary, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showTunings = true }, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(50))) {
                    Text(tuning.name + "  ▾", color = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { showSettings = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Settings" }) {
                    SettingsGlyph()
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(28.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                GaugeAndReading(state, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            AmplitudeWaveform(model.level)
            Spacer(Modifier.height(20.dp))
            StringRow(tuning, detected?.stringIndex, locked, model, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showTunings) ModalBottomSheet(onDismissRequest = { showTunings = false }) {
        Text(stringResource(R.string.tuning), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        Tuning.ALL.forEachIndexed { index, choice ->
            TextButton(onClick = { model.updateSettings { it.copy(tuningIndex = index) }; model.lockString(null); showTunings = false }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(choice.name, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showSettings) ModalBottomSheet(onDismissRequest = { showSettings = false }) {
        Text(stringResource(R.string.preferences), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        SettingToggle(stringResource(R.string.haptics), settings.haptics) { model.updateSettings { current -> current.copy(haptics = it) } }
        SettingToggle(stringResource(R.string.keep_screen_on), settings.keepScreenOn) { model.updateSettings { current -> current.copy(keepScreenOn = it) } }
        SettingToggle(stringResource(R.string.dark_theme), settings.darkTheme) { model.updateSettings { current -> current.copy(darkTheme = it) } }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.calibration))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.updateSettings { it.copy(a4 = (it.a4 - 1).coerceAtLeast(415)) } }) { Text("−") }
                Text("${settings.a4} Hz")
                TextButton(onClick = { model.updateSettings { it.copy(a4 = (it.a4 + 1).coerceAtMost(466)) } }) { Text("+") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsGlyph() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(Modifier.size(23.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        for (index in 0 until 8) {
            val angle = index * PI / 4
            val inner = Offset(center.x + 8.dp.toPx() * cos(angle).toFloat(), center.y + 8.dp.toPx() * sin(angle).toFloat())
            val outer = Offset(center.x + 11.dp.toPx() * cos(angle).toFloat(), center.y + 11.dp.toPx() * sin(angle).toFloat())
            drawLine(color, inner, outer, 2.3.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(color, 7.dp.toPx(), center, style = Stroke(2.3.dp.toPx()))
        drawCircle(color, 2.1.dp.toPx(), center)
    }
}

@Composable
private fun SettingToggle(label: String, value: Boolean, update: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { update(!value) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = value, onCheckedChange = update)
    }
}

@Composable
internal fun GaugeAndReading(state: TunerUiState, modifier: Modifier = Modifier) {
    val detected = state as? TunerUiState.Detected
    val deviation = detected?.cents?.toFloat()?.coerceIn(-50f, 50f) ?: 0f
    val needle = remember { Animatable(0f) }
    LaunchedEffect(deviation) { needle.animateTo(deviation, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
    val idleLabel = when (state) {
        TunerUiState.MicBusy -> stringResource(R.string.mic_busy)
        TunerUiState.MicError -> stringResource(R.string.mic_error)
        else -> stringResource(R.string.play_string)
    }
    val stringText = if (detected == null) idleLabel else "String ${6 - detected.stringIndex}, ${detected.noteName}"
    val centsText = when {
        detected == null -> idleLabel
        detected.inTune -> "In tune, ${detected.cents.roundToInt()} cents"
        detected.cents < 0 -> "${abs(detected.cents).roundToInt()} cents flat, tighten string"
        else -> "${detected.cents.roundToInt()} cents sharp, loosen string"
    }
    var spoken by remember { mutableStateOf("") }
    var lastSpoken by remember { mutableLongStateOf(0L) }
    LaunchedEffect(detected?.noteName, detected?.cents?.roundToInt(), detected?.stringIndex, idleLabel) {
        val wait = 1000L - (SystemClock.elapsedRealtime() - lastSpoken)
        if (wait > 0) delay(wait)
        spoken = if (detected == null) idleLabel else "$stringText, $centsText"
        lastSpoken = SystemClock.elapsedRealtime()
    }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val text = MaterialTheme.colorScheme.onSurface
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier.testTag("gauge_reading").semantics { contentDescription = spoken; liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height((42f * scale).dp), contentAlignment = Alignment.Center) {
            Text(if (detected == null) idleLabel else "STRING ${6 - detected.stringIndex}  ·  ${detected.noteName}  ·  ${String.format(Locale.US, "%.1f", detected.frequencyHz)} Hz",
                color = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
        }
        Canvas(Modifier.fillMaxWidth().height(158.dp)) {
            val center = Offset(size.width / 2, size.height * 0.94f)
            val radius = minOf(size.width * 0.37f, size.height * 0.83f)
            val bounds = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
            drawArc(outline.copy(alpha = 0.45f), -157.5f, 135f, false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(primary.copy(alpha = 0.95f), -96.75f, 13.5f, false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            for (mark in -50..50 step 10) {
                val angle = Math.toRadians(mark * 1.35 - 90)
                val outer = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                val innerRadius = radius - if (mark == 0) 18.dp.toPx() else 10.dp.toPx()
                val inner = Offset(center.x + innerRadius * cos(angle).toFloat(), center.y + innerRadius * sin(angle).toFloat())
                drawLine(if (mark == 0) primary else outline, inner, outer, 2.dp.toPx(), StrokeCap.Round)
            }
            val angle = Math.toRadians(needle.value * 1.35 - 90)
            val tip = Offset(center.x + (radius - 24.dp.toPx()) * cos(angle).toFloat(), center.y + (radius - 24.dp.toPx()) * sin(angle).toFloat())
            drawLine(if (detected?.inTune == true) primary else text, center, tip, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(primary, 5.dp.toPx(), center)
        }
        Row(Modifier.fillMaxWidth().height((26f * scale).dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LOWER", color = outline, fontSize = 10.sp, letterSpacing = 1.sp)
            Text("HIGHER", color = outline, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        Box(Modifier.fillMaxWidth().height((54f * scale).dp), contentAlignment = Alignment.TopCenter) {
            Text(when {
                detected == null -> ""
                detected.inTune -> "✓  ${if (detected.cents > 0) "+" else ""}${detected.cents.roundToInt()} ¢  ·  IN TUNE"
                detected.cents < 0 -> "${detected.cents.roundToInt()} ¢  ·  TIGHTEN"
                else -> "+${detected.cents.roundToInt()} ¢  ·  LOOSEN"
            }, color = if (detected?.inTune == true) primary else text,
                fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AmplitudeWaveform(levels: StateFlow<Float>) {
    val level by levels.collectAsStateWithLifecycle()
    val history = remember { FloatArray(72) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(level) {
        fun advance(value: Float) {
            System.arraycopy(history, 1, history, 0, history.size - 1)
            history[history.lastIndex] = value
            frame++
        }
        advance(level)
        if (level <= 0.001f) repeat(history.size) { delay(24); advance(0f) }
    }
    val green = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LIVE INPUT", color = muted, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
            Text("●", color = if (level > 0.02f) green else muted, fontSize = 11.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(54.dp)) {
            val currentFrame = frame
            val mid = size.height / 2
            drawLine(muted.copy(alpha = 0.24f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            val path = Path()
            history.forEachIndexed { index, amplitude ->
                val x = size.width * index / history.lastIndex
                val wave = sin((index + currentFrame) * 0.72f) * amplitude * size.height * 0.4f
                if (index == 0) path.moveTo(x, mid + wave) else path.lineTo(x, mid + wave)
            }
            if (history.any { it > 0.01f }) {
                drawPath(path, green.copy(alpha = 0.18f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, green, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun StringRow(tuning: Tuning, selected: Int?, locked: Int?, model: TunerViewModel, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("STRINGS", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
            Text("6 LOW  ·  1 HIGH", color = MaterialTheme.colorScheme.outline, fontSize = 10.sp, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            tuning.notes.forEachIndexed { index, midi ->
                val selectedNow = selected == index || locked == index
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.weight(1f).height(73.dp).clip(shape)
                    .background(if (selectedNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable { model.lockString(if (locked == index) null else index) }
                    .semantics { contentDescription = "String ${6 - index}, ${Note.name(midi)}" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("${6 - index}", color = if (selectedNow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(Note.name(midi), color = if (selectedNow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(if (locked == null) "AUTO STRING SELECTION" else "LOCKED TO STRING ${6 - locked}",
            color = MaterialTheme.colorScheme.outline, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}
