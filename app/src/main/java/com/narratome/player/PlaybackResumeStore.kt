package com.narratome.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackResumeDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_resume")

data class PlaybackResumeSnapshot(
    val libraryId: String?,
    val libraryItemId: String?,
    val windowIndex: Int,
    val positionMs: Long,
)

@Singleton
class PlaybackResumeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.playbackResumeDataStore

    private object Keys {
        val LIBRARY_ID = stringPreferencesKey("library_id")
        val LIBRARY_ITEM_ID = stringPreferencesKey("library_item_id")
        val WINDOW_INDEX = intPreferencesKey("window_index")
        val POSITION_MS = stringPreferencesKey("position_ms")
    }

    suspend fun save(
        libraryId: String?,
        libraryItemId: String?,
        windowIndex: Int,
        positionMs: Long,
    ) {
        dataStore.edit { prefs ->
            if (libraryId != null) prefs[Keys.LIBRARY_ID] = libraryId else prefs.remove(Keys.LIBRARY_ID)
            if (libraryItemId != null) prefs[Keys.LIBRARY_ITEM_ID] = libraryItemId else prefs.remove(Keys.LIBRARY_ITEM_ID)
            prefs[Keys.WINDOW_INDEX] = windowIndex
            prefs[Keys.POSITION_MS] = positionMs.toString()
        }
    }

    suspend fun readSnapshot(): PlaybackResumeSnapshot? {
        val prefs = dataStore.data.first()
        val itemId = prefs[Keys.LIBRARY_ITEM_ID] ?: return null
        val pos = prefs[Keys.POSITION_MS]?.toLongOrNull() ?: 0L
        return PlaybackResumeSnapshot(
            libraryId = prefs[Keys.LIBRARY_ID],
            libraryItemId = itemId,
            windowIndex = prefs[Keys.WINDOW_INDEX] ?: 0,
            positionMs = pos,
        )
    }

    fun observeLibraryItemId() = dataStore.data.map { it[Keys.LIBRARY_ITEM_ID] }
}
