package dev.mangelov.tune.audio

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dev.mangelov.tune.dsp.PitchTracker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MicSource(private val context: Context, private val listener: (Event) -> Unit) {
    sealed interface Event {
        data class Reading(val value: PitchTracker.Reading) : Event
        data class Level(val rms: Float) : Event
        data object Busy : Event
        data object Available : Event
        data object Error : Event
    }
    private val generation = AtomicInteger(0)
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "TuneMic") }
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var running = false
    @Volatile private var busy = false

    @Synchronized fun start() {
        if (running) return
        running = true
        busy = false
        val session = generation.incrementAndGet()
        worker.execute { runCapture(session) }
    }
    @Synchronized fun stop() {
        if (!running) return
        running = false
        generation.incrementAndGet()
        try { recorder?.stop() } catch (_: IllegalStateException) { }
    }
    fun close() { stop(); worker.shutdown() }

    private fun runCapture(session: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var failures = 0
        while (generation.get() == session) {
            try {
                capture(session)
                failures = 0
            } catch (e: Exception) {
                if (generation.get() != session) break
                Log.e("TuneMic", "Capture failed", e)
                listener(Event.Error)
                failures++
                if (failures >= 3) break
                try { Thread.sleep((250L shl (failures - 1))) } catch (_: InterruptedException) { break }
            }
        }
        synchronized(this) { if (generation.get() == session) running = false }
    }
    private fun capture(session: Int) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Microphone permission revoked")
        val manager = context.getSystemService(AudioManager::class.java)
        val builtIn = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?: throw IllegalStateException("No built-in microphone")
        val source = if (manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true")
            MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        var record: AudioRecord? = null
        for (rate in intArrayOf(48_000, 44_100)) {
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            if (minimum <= 0) continue
            val candidate = try {
                val builder = AudioRecord.Builder().setAudioSource(source).setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
                ).setBufferSizeInBytes(minimum * 4)
                if (Build.VERSION.SDK_INT >= 30) builder.setPrivacySensitive(false)
                builder.build()
            } catch (_: Exception) { null }
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED && candidate.audioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
                record = candidate; break
            }
            candidate?.release()
        }
        val mic = record ?: throw IllegalStateException("Unable to initialize AudioRecord")
        recorder = mic
        val effects = ArrayList<AudioEffect>(3)
        try {
            if (!mic.setPreferredDevice(builtIn)) throw IllegalStateException("Unable to select built-in microphone")
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(mic.audioSessionId)?.also { it.enabled = false; effects.add(it) }
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(mic.audioSessionId)?.also { it.enabled = false; effects.add(it) }
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(mic.audioSessionId)?.also { it.enabled = false; effects.add(it) }
            Log.i("TuneMic", "source=$source rate=${mic.sampleRate} effectsDisabled=${effects.size}")
            val callback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>) {
                    if (generation.get() != session) return
                    val silenced = configs.any { it.clientAudioSessionId == mic.audioSessionId && it.isClientSilenced }
                    if (silenced != busy) { busy = silenced; listener(if (silenced) Event.Busy else Event.Available) }
                }
            }
            manager.registerAudioRecordingCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
            try {
                mic.startRecording()
                if (mic.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IllegalStateException("AudioRecord did not start")
                val tracker = PitchTracker(mic.sampleRate)
                val buffer = FloatArray(1024)
                var zeroSince = 0L
                val readingListener: (PitchTracker.Reading) -> Unit = { listener(Event.Reading(it)) }
                while (generation.get() == session) {
                    val count = mic.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count < 0) throw IllegalStateException("AudioRecord.read returned $count")
                    if (count == 0) continue
                    var nonzero = false
                    var power = 0.0
                    for (i in 0 until count) {
                        val sample = buffer[i]
                        if (sample != 0f) nonzero = true
                        power += sample * sample
                    }
                    listener(Event.Level(kotlin.math.sqrt(power / count).toFloat()))
                    if (nonzero) {
                        zeroSince = 0
                        if (busy) { busy = false; listener(Event.Available) }
                    } else if (zeroSince == 0L) zeroSince = SystemClock.elapsedRealtime()
                    else if (!busy && SystemClock.elapsedRealtime() - zeroSince > 1500) { busy = true; listener(Event.Busy) }
                    tracker.accept(buffer, count, readingListener)
                }
            } finally { manager.unregisterAudioRecordingCallback(callback) }
        } finally {
            recorder = null
            try { mic.stop() } catch (_: IllegalStateException) { }
            effects.forEach { it.release() }
            mic.release()
        }
    }
}
