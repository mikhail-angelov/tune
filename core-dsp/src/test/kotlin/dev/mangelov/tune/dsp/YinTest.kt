package dev.mangelov.tune.dsp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class YinTest {
    @Test fun `all tunings and calibrations detect within one cent`() {
        val yin = Yin()
        for (tuning in Tuning.ALL) for (midi in tuning.notes) for (a4 in listOf(415.0, 440.0, 466.0)) for (offset in listOf(-50, -10, 0, 10, 50)) {
            val frequency = Note.frequency(midi, a4) * 2.0.pow(offset / 1200.0)
            val samples = FloatArray(2048) { sin(2 * PI * it * frequency / 24_000).toFloat() }
            val result = yin.detect(samples)
            assertNotNull("$frequency Hz", result)
            assertTrue("$frequency Hz: ${result!!.frequencyHz}", abs(Note.cents(result.frequencyHz, frequency)) < 1.0)
        }
    }
    @Test fun `strong second harmonic keeps low string fundamental`() {
        val yin = Yin()
        for (midi in listOf(40, 45)) {
            val frequency = Note.frequency(midi)
            val samples = FloatArray(2048) { index ->
                val t = index / 24_000.0
                (0.3 * sin(2 * PI * frequency * t) +
                    0.8 * sin(4 * PI * frequency * t) +
                    0.18 * sin(6 * PI * frequency * t)).toFloat()
            }
            val result = yin.detect(samples)
            assertNotNull(result)
            assertTrue("$midi: ${result!!.frequencyHz}", abs(Note.cents(result.frequencyHz, frequency)) < 1.0)
        }
    }
    @Test fun `weak fundamental with strong second harmonic does not jump an octave`() {
        val frequency = Note.frequency(40)
        val samples = FloatArray(2048) { index ->
            val t = index / 24_000.0
            (0.05 * sin(2 * PI * frequency * t) + 0.8 * sin(4 * PI * frequency * t)).toFloat()
        }
        val result = Yin().detect(samples)
        assertNotNull(result)
        assertTrue("${result!!.frequencyHz}", abs(Note.cents(result.frequencyHz, frequency)) < 1.0)
    }
    @Test fun `tracker gates silence after signal decays`() {
        val tracker = PitchTracker(48_000)
        var last: PitchTracker.Reading? = null
        repeat(8) { chunk ->
            val loud = FloatArray(1024) { (0.1 * sin(2 * PI * 110.0 * (chunk * 1024 + it) / 48_000)).toFloat() }
            tracker.accept(loud, loud.size) { last = it }
        }
        assertTrue(last is PitchTracker.Reading.Pitch)
        val quiet = FloatArray(1024)
        repeat(2) { tracker.accept(quiet, quiet.size) { last = it } }
        assertEquals(PitchTracker.Reading.Silence, last)
    }
    @Test fun `noise is rejected`() {
        val random = java.util.Random(1)
        val samples = FloatArray(2048) { random.nextFloat() * 2 - 1 }
        assertNull(Yin().detect(samples))
    }
    @Test fun `notes round trip`() {
        for (midi in 36..88) assertEquals(midi, Note.nearestMidi(Note.frequency(midi)))
    }
}
