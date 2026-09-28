package dev.mangelov.tune.dsp

import kotlin.math.log2
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class PitchTracker(sampleRate: Int) {
    private val outputRate = sampleRate / 2
    private val yin = Yin(outputRate)
    private val ring = FloatArray(2048)
    private val window = FloatArray(2048)
    private var position = 0
    private var filled = 0
    private var sinceDetection = 0
    private var previousInput = 0.0
    private var previousHighPass = 0.0
    private val filters = arrayOf(Biquad.lowPass(sampleRate, 2500.0, 0.5411961), Biquad.lowPass(sampleRate, 2500.0, 1.306563))
    private var decimationPhase = 0
    private var windowPower = 0.0
    private val history = DoubleArray(5)
    private val sortedHistory = DoubleArray(5)
    private var historyCount = 0
    private var historyPosition = 0
    private var smoothedLog = Double.NaN
    private var stableLog = Double.NaN
    private var stableCount = 0

    sealed interface Reading {
        data object Silence : Reading
        data class Pitch(val frequencyHz: Double, val confidence: Float) : Reading
    }

    fun accept(input: FloatArray, count: Int, onReading: (Reading) -> Unit) {
        var sum = 0.0
        for (i in 0 until count) {
            val raw = input[i].toDouble()
            sum += raw * raw
            val high = raw - previousInput + 0.995 * previousHighPass
            previousInput = raw; previousHighPass = high
            val filtered = filters[1].apply(filters[0].apply(high))
            decimationPhase = 1 - decimationPhase
            if (decimationPhase != 0) continue
            val old = ring[position].toDouble()
            windowPower += filtered * filtered - old * old
            ring[position] = filtered.toFloat()
            position = (position + 1) % ring.size
            filled = (filled + 1).coerceAtMost(ring.size)
            sinceDetection++
        }
        if (sinceDetection < 512 || filled < ring.size) return
        sinceDetection = 0
        val rms = sqrt(sum / count)
        if (rms < 10.0.pow(-45.0 / 20.0) || sqrt(windowPower.coerceAtLeast(0.0) / ring.size) < 10.0.pow(-45.0 / 20.0)) {
            resetPitch()
            onReading(Reading.Silence)
            return
        }
        for (i in window.indices) window[i] = ring[(position + i) % ring.size]
        val result = yin.detect(window)
        if (result == null || result.confidence < 0.85f || result.frequencyHz !in 65.0..420.0) {
            resetPitch()
            onReading(Reading.Silence)
            return
        }
        val currentLog = log2(result.frequencyHz)
        if (stableLog.isNaN() || abs(currentLog - stableLog) > 50.0 / 1200.0) {
            resetPitch()
            stableLog = currentLog
            stableCount = 1
        } else {
            stableLog += 0.35 * (currentLog - stableLog)
            stableCount++
        }
        if (stableCount < 3) return
        history[historyPosition] = currentLog
        historyPosition = (historyPosition + 1) % history.size
        historyCount = (historyCount + 1).coerceAtMost(history.size)
        for (i in 0 until historyCount) sortedHistory[i] = history[i]
        java.util.Arrays.sort(sortedHistory, 0, historyCount)
        val median = sortedHistory[historyCount / 2]
        smoothedLog = if (smoothedLog.isNaN()) median else smoothedLog + 0.25 * (median - smoothedLog)
        onReading(Reading.Pitch(2.0.pow(smoothedLog), result.confidence))
    }

    private fun resetPitch() {
        stableLog = Double.NaN
        stableCount = 0
        historyCount = 0
        historyPosition = 0
        smoothedLog = Double.NaN
    }
}

internal class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var z1 = 0.0
    private var z2 = 0.0
    fun apply(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
    companion object {
        fun lowPass(sampleRate: Int, cutoff: Double, q: Double): Biquad {
            val omega = 2.0 * Math.PI * cutoff / sampleRate
            val alpha = kotlin.math.sin(omega) / (2.0 * q)
            val cos = kotlin.math.cos(omega)
            val scale = 1.0 / (1.0 + alpha)
            return Biquad((1 - cos) * 0.5 * scale, (1 - cos) * scale, (1 - cos) * 0.5 * scale, -2 * cos * scale, (1 - alpha) * scale)
        }
    }
}
