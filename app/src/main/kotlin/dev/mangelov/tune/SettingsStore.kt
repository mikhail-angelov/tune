package dev.mangelov.tune

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("tune_settings")
data class TunerSettings(
    val tuningIndex: Int = 0,
    val a4: Int = 440,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val darkTheme: Boolean = true,
)
class SettingsStore(private val context: Context) {
    private val tuning = intPreferencesKey("tuning")
    private val reference = intPreferencesKey("a4")
    private val haptics = booleanPreferencesKey("haptics")
    private val screen = booleanPreferencesKey("screen")
    private val dark = booleanPreferencesKey("dark")
    private fun read(it: androidx.datastore.preferences.core.Preferences): TunerSettings =
        TunerSettings(
            (it[tuning] ?: 0).coerceIn(0, 4), (it[reference] ?: 440).coerceIn(415, 466),
            it[haptics] ?: true, it[screen] ?: true, it[dark] ?: true,
        )
    val values: Flow<TunerSettings> = context.dataStore.data.map(::read)
    suspend fun update(change: (TunerSettings) -> TunerSettings) { context.dataStore.edit {
        val value = change(read(it))
        it[tuning] = value.tuningIndex; it[reference] = value.a4
        it[haptics] = value.haptics
        it[screen] = value.keepScreenOn; it[dark] = value.darkTheme
    } }
}
