package dev.mangelov.tune.dsp

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

object Note {
    private val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    fun frequency(midi: Int, a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69) / 12.0)
    fun cents(frequency: Double, target: Double): Double = 1200.0 * log2(frequency / target)
    fun nearestMidi(frequency: Double, a4: Double = 440.0): Int = (69 + 12 * log2(frequency / a4)).roundToInt()
    fun name(midi: Int): String = names[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1)
}

data class Tuning(val name: String, val notes: IntArray) {
    companion object {
        val ALL = listOf(
            Tuning("Standard", intArrayOf(40, 45, 50, 55, 59, 64)),
            Tuning("Drop D", intArrayOf(38, 45, 50, 55, 59, 64)),
            Tuning("Half-step down", intArrayOf(39, 44, 49, 54, 58, 63)),
            Tuning("Open G", intArrayOf(38, 43, 47, 50, 55, 62)),
            Tuning("DADGAD", intArrayOf(38, 45, 50, 55, 57, 62)),
        )
    }
}
