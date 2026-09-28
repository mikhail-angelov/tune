package dev.mangelov.tune

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mangelov.tune.audio.MicSource
import dev.mangelov.tune.dsp.Note
import dev.mangelov.tune.dsp.PitchTracker
import dev.mangelov.tune.dsp.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10

sealed interface TunerUiState {
    data object NeedsPermission : TunerUiState
    data object PermissionDeniedPermanently : TunerUiState
    data object MicBusy : TunerUiState
    data object MicError : TunerUiState
    data object Listening : TunerUiState
    data class Detected(val frequencyHz: Double, val noteName: String, val cents: Double,
        val stringIndex: Int, val inTune: Boolean, val confidence: Float) : TunerUiState
}

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val source = MicSource(application) { event -> handle(event) }
    private val _state = MutableStateFlow<TunerUiState>(TunerUiState.NeedsPermission)
    val state: StateFlow<TunerUiState> = _state
    private val _settings = MutableStateFlow(TunerSettings())
    val settings: StateFlow<TunerSettings> = _settings
    private val _lockedString = MutableStateFlow<Int?>(null)
    val lockedString: StateFlow<Int?> = _lockedString
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level
    @Volatile private var active = false
    private var selected: Int? = null
    private var candidate: Int? = null
    private var candidateCount = 0
    init { viewModelScope.launch { store.values.collect { _settings.value = it } } }
    fun start() { active = true; if (_state.value !is TunerUiState.Detected) _state.value = TunerUiState.Listening; source.start() }
    fun stop() { active = false; source.stop(); _level.value = 0f; _state.value = TunerUiState.Listening }
    fun needsPermission(permanent: Boolean) {
        stop(); _state.value = if (permanent) TunerUiState.PermissionDeniedPermanently else TunerUiState.NeedsPermission
    }
    fun lockString(index: Int?) { _lockedString.value = index; selected = index; candidate = null; candidateCount = 0 }
    fun updateSettings(change: (TunerSettings) -> TunerSettings) { viewModelScope.launch { store.update(change) } }
    private fun handle(event: MicSource.Event) {
        if (!active) return
        when (event) {
            is MicSource.Event.Level -> {
                val db = 20.0 * log10(event.rms.coerceAtLeast(0.000001f).toDouble())
                _level.value = ((db + 55.0) / 45.0).coerceIn(0.0, 1.0).toFloat()
            }
            is MicSource.Event.Reading -> when (val value = event.value) {
                PitchTracker.Reading.Silence -> Unit
                is PitchTracker.Reading.Pitch -> resolve(value)?.let { _state.value = it }
            }
            MicSource.Event.Busy -> if (_state.value !is TunerUiState.Detected) _state.value = TunerUiState.MicBusy
            MicSource.Event.Available -> if (_state.value !is TunerUiState.Detected) _state.value = TunerUiState.Listening
            MicSource.Event.Error -> _state.value = TunerUiState.MicError
        }
    }
    private fun resolve(pitch: PitchTracker.Reading.Pitch): TunerUiState.Detected? {
        val config = _settings.value
        val tuning = Tuning.ALL[config.tuningIndex]
        val nearest = tuning.notes.indices.minBy { abs(Note.cents(pitch.frequencyHz, Note.frequency(tuning.notes[it], config.a4.toDouble()))) }
        val locked = _lockedString.value
        val nearestTarget = Note.frequency(tuning.notes[nearest], config.a4.toDouble())
        if (locked == null && abs(Note.cents(pitch.frequencyHz, nearestTarget)) > 300) return null
        val index = when {
            locked != null -> locked
            selected == null -> nearest.also { selected = it }
            selected == nearest -> { candidate = null; candidateCount = 0; selected }
            else -> {
                val oldTarget = Note.frequency(tuning.notes[selected!!], config.a4.toDouble())
                if (abs(Note.cents(pitch.frequencyHz, oldTarget)) <= 60) { candidate = null; candidateCount = 0; selected }
                else {
                    if (candidate != nearest) { candidate = nearest; candidateCount = 1 }
                    else if (++candidateCount >= 3) { selected = nearest; candidateCount = 0 }
                    if (selected != nearest) return _state.value as? TunerUiState.Detected
                    selected
                }
            }
        }
        val stringIndex = index ?: nearest
        val targetMidi = tuning.notes[stringIndex]
        val target = Note.frequency(targetMidi, config.a4.toDouble())
        val deviation = Note.cents(pitch.frequencyHz, target)
        return TunerUiState.Detected(pitch.frequencyHz, Note.name(targetMidi), deviation,
            stringIndex, abs(deviation) <= 5, pitch.confidence)
    }
    override fun onCleared() { source.close(); super.onCleared() }
}
