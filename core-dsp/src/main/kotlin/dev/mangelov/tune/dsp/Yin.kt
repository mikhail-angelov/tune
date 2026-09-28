package dev.mangelov.tune.dsp

import kotlin.math.max

class Yin(private val sampleRate: Int = 24_000, private val size: Int = 2048) {
    private val minLag = max(2, sampleRate / 1400)
    private val maxLag = sampleRate / 65
    private val differences = DoubleArray(maxLag + 2)
    private val normalized = DoubleArray(maxLag + 2)
    data class Result(val frequencyHz: Double, val confidence: Float)

    fun detect(samples: FloatArray): Result? {
        require(samples.size >= size)
        var bestLag = minLag
        var bestValue = Double.POSITIVE_INFINITY
        var running = 0.0
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until size - maxLag) {
                val delta = samples[i].toDouble() - samples[i + lag]
                sum += delta * delta
            }
            differences[lag] = sum
            running += sum
            val value = if (running > 0) sum * lag / running else 1.0
            normalized[lag] = value
            if (lag >= minLag && value < bestValue) { bestValue = value; bestLag = lag }
        }
        var selected = -1
        for (lag in minLag until maxLag) {
            if (normalized[lag] < 0.12 && normalized[lag] <= normalized[lag - 1] && normalized[lag] < normalized[lag + 1]) {
                selected = lag
                break
            }
        }
        if (selected < 0) selected = bestLag
        // A weak fundamental can make the second harmonic win the first-minimum search.
        // Prefer the doubled period only when its normalized error is much smaller.
        while (selected * 2 + 2 <= maxLag && normalized[selected] > 0.001) {
            var doubled = selected * 2
            for (lag in doubled - 2..doubled + 2) if (normalized[lag] < normalized[doubled]) doubled = lag
            if (normalized[doubled] < normalized[selected] * 0.5) selected = doubled else break
        }
        val confidence = (1.0 - normalized[selected]).coerceIn(0.0, 1.0).toFloat()
        if (confidence < 0.75f) return null
        val left = normalized[selected - 1]
        val center = normalized[selected]
        val right = normalized[selected + 1]
        val denominator = left - 2 * center + right
        val offset = if (denominator != 0.0) (0.5 * (left - right) / denominator).coerceIn(-1.0, 1.0) else 0.0
        return Result(sampleRate / (selected + offset), confidence)
    }
}
