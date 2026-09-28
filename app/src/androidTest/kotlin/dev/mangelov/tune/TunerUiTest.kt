package dev.mangelov.tune

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TunerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detectedStringAndCentsAreVisible() {
        compose.setContent {
            GaugeAndReading(TunerUiState.Detected(110.0, "A2", -7.0, 1, false, 0.98f))
        }
        compose.onNodeWithText("STRING 5", substring = true).assertExists()
        compose.onNodeWithText("-7 ¢", substring = true).assertExists()
    }

    @Test fun listeningAndDetectedUseSameHeight() {
        var state by mutableStateOf<TunerUiState>(TunerUiState.Listening)
        compose.setContent { MaterialTheme { GaugeAndReading(state, Modifier.width(360.dp)) } }
        val firstBounds = compose.onNodeWithTag("gauge_reading").getUnclippedBoundsInRoot()
        val before = firstBounds.bottom - firstBounds.top
        compose.runOnUiThread { state = TunerUiState.Detected(110.0, "A2", -7.0, 1, false, 0.98f) }
        compose.waitForIdle()
        val secondBounds = compose.onNodeWithTag("gauge_reading").getUnclippedBoundsInRoot()
        val after = secondBounds.bottom - secondBounds.top
        assertEquals(before.value, after.value, 0.1f)
    }

    @Test fun inTuneShowsCheckBesideCents() {
        compose.setContent {
            GaugeAndReading(TunerUiState.Detected(110.0, "A2", 0.0, 1, true, 0.98f))
        }
        compose.onNodeWithText("✓  0 ¢  ·  IN TUNE").assertExists()
    }
}
