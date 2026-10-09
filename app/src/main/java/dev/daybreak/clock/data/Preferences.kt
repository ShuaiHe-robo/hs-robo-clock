package dev.daybreak.clock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("preferences")
class Preferences(private val context: Context) {
    private val darkKey = booleanPreferencesKey("dark")
    val dark = context.settings.data.map { it[darkKey] ?: true }
    suspend fun setDark(value: Boolean) { context.settings.edit { it[darkKey] = value } }
}
