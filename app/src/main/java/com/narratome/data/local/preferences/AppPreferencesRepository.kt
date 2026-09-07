package com.narratome.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.narratome.domain.model.EndpointMode
import com.narratome.domain.model.SyncConflictPolicy
import com.narratome.data.remote.dto.LibrariesResponseDto
import com.narratome.data.remote.dto.LibraryDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class LibraryViewMode(val preferenceValue: String) {
    LargeGrid("large_grid"),
    CompactGrid("compact_grid"),
    List("list");

    companion object {
        fun fromPreferenceValue(value: String?): LibraryViewMode =
            entries.firstOrNull { it.preferenceValue == value } ?: LargeGrid
    }
}

const val DEFAULT_PLAYBACK_NOTIFICATION_RETENTION_MINUTES = 10
val PLAYBACK_NOTIFICATION_RETENTION_MINUTES = listOf(1, 5, 10)

fun normalizePlaybackNotificationRetentionMinutes(value: Int?): Int =
    if (value != null && value in PLAYBACK_NOTIFICATION_RETENTION_MINUTES) {
        value
    } else {
        DEFAULT_PLAYBACK_NOTIFICATION_RETENTION_MINUTES
    }

@Singleton
class AppPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    private object Keys {
        val REQUIRE_HTTPS = booleanPreferencesKey("require_https")
        val ALWAYS_ALLOW_METERED = booleanPreferencesKey("always_allow_metered")
        val PRIMARY_URL = stringPreferencesKey("primary_url")
        val SECONDARY_URL = stringPreferencesKey("secondary_url")
        val HOME_SSID = stringPreferencesKey("home_ssid")
        val ENDPOINT_MODE = stringPreferencesKey("endpoint_mode")
        val SYNC_CONFLICT_POLICY = stringPreferencesKey("sync_conflict_policy")
        val DOWNLOAD_MAX_PARALLEL = intPreferencesKey("download_max_parallel")
        val DOWNLOAD_MAX_PARALLEL_BOOKS = intPreferencesKey("download_max_parallel_books")
        val DOWNLOAD_STRICT_CLEANUP = booleanPreferencesKey("download_strict_cleanup")
        val DOWNLOAD_VERIFY_SIZES = booleanPreferencesKey("download_verify_sizes")
        val SEEK_BACK_SECONDS = intPreferencesKey("seek_back_seconds")
        val SEEK_FORWARD_SECONDS = intPreferencesKey("seek_forward_seconds")
        val PLAYBACK_NOTIFICATION_RETENTION_MINUTES =
            intPreferencesKey("playback_notification_retention_minutes")
        val SLEEP_TIMER_FADE_SECONDS = intPreferencesKey("sleep_timer_fade_seconds")
        val SLEEP_TIMER_SHAKE_TO_EXTEND = booleanPreferencesKey("sleep_timer_shake_to_extend")
        val SELECTED_LIBRARY_ID = stringPreferencesKey("selected_library_id")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
    }

    val primaryUrl: Flow<String> = dataStore.data.map { it[Keys.PRIMARY_URL].orEmpty() }
    val requireHttps: Flow<Boolean> = dataStore.data.map { it[Keys.REQUIRE_HTTPS] == true }

    suspend fun setRequireHttps(required: Boolean) {
        dataStore.edit { it[Keys.REQUIRE_HTTPS] = required }
    }
    val secondaryUrl: Flow<String> = dataStore.data.map { it[Keys.SECONDARY_URL].orEmpty() }
    val homeSsid: Flow<String> = dataStore.data.map { it[Keys.HOME_SSID].orEmpty() }
    val endpointMode: Flow<EndpointMode> = dataStore.data.map { pref ->
        when (pref[Keys.ENDPOINT_MODE]) {
            "PRIMARY" -> EndpointMode.PRIMARY
            "SECONDARY" -> EndpointMode.SECONDARY
            else -> EndpointMode.AUTO
        }
    }
    val syncConflictPolicy: Flow<SyncConflictPolicy> = dataStore.data.map { pref ->
        when (pref[Keys.SYNC_CONFLICT_POLICY]) {
            "PREFER_SERVER" -> SyncConflictPolicy.PREFER_SERVER
            "PREFER_LOCAL" -> SyncConflictPolicy.PREFER_LOCAL
            else -> SyncConflictPolicy.ALWAYS_ASK
        }
    }

    val alwaysAllowMetered: Flow<Boolean> = dataStore.data.map { it[Keys.ALWAYS_ALLOW_METERED] == true }
    val downloadMaxParallelParts: Flow<Int> = dataStore.data.map { pref ->
        (pref[Keys.DOWNLOAD_MAX_PARALLEL] ?: 4).coerceIn(1, 16)
    }
    val downloadMaxParallelBooks: Flow<Int> = dataStore.data.map { pref ->
        (pref[Keys.DOWNLOAD_MAX_PARALLEL_BOOKS] ?: 2).coerceIn(1, 8)
    }
    val downloadStrictCleanup: Flow<Boolean> = dataStore.data.map { it[Keys.DOWNLOAD_STRICT_CLEANUP] == true }
    val downloadVerifySizes: Flow<Boolean> = dataStore.data.map { pref ->
        pref[Keys.DOWNLOAD_VERIFY_SIZES] == true
    }
    val seekBackSeconds: Flow<Int> = dataStore.data.map { pref ->
        (pref[Keys.SEEK_BACK_SECONDS] ?: 30).coerceIn(5, 120)
    }
    val seekForwardSeconds: Flow<Int> = dataStore.data.map { pref ->
        (pref[Keys.SEEK_FORWARD_SECONDS] ?: 30).coerceIn(5, 120)
    }
    val playbackNotificationRetentionMinutes: Flow<Int> = dataStore.data.map { pref ->
        normalizePlaybackNotificationRetentionMinutes(pref[Keys.PLAYBACK_NOTIFICATION_RETENTION_MINUTES])
    }
    val sleepTimerFadeSeconds: Flow<Int> = dataStore.data.map { pref ->
        (pref[Keys.SLEEP_TIMER_FADE_SECONDS] ?: 60).coerceIn(0, 180)
    }
    val sleepTimerShakeToExtend: Flow<Boolean> = dataStore.data.map { pref ->
        pref[Keys.SLEEP_TIMER_SHAKE_TO_EXTEND] != false
    }
    val selectedLibraryId: Flow<String?> = dataStore.data.map { pref ->
        pref[Keys.SELECTED_LIBRARY_ID]?.takeIf { it.isNotBlank() }
    }
    val libraryViewMode: Flow<LibraryViewMode> = dataStore.data.map { pref ->
        LibraryViewMode.fromPreferenceValue(pref[Keys.LIBRARY_VIEW_MODE])
    }

    suspend fun setServerConfig(
        primaryUrl: String,
        secondaryUrl: String,
        homeSsid: String,
        endpointMode: EndpointMode,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PRIMARY_URL] = primaryUrl.trim().trimEnd('/')
            prefs[Keys.SECONDARY_URL] = secondaryUrl.trim().trimEnd('/')
            prefs[Keys.HOME_SSID] = homeSsid.trim()
            prefs[Keys.ENDPOINT_MODE] = endpointMode.name
        }
    }

    suspend fun setSyncConflictPolicy(policy: SyncConflictPolicy) {
        dataStore.edit { it[Keys.SYNC_CONFLICT_POLICY] = policy.name }
    }

    suspend fun setAlwaysAllowMetered(v: Boolean) {
        dataStore.edit { it[Keys.ALWAYS_ALLOW_METERED] = v }
    }

    suspend fun setDownloadMaxParallelParts(v: Int) {
        dataStore.edit { it[Keys.DOWNLOAD_MAX_PARALLEL] = v.coerceIn(1, 16) }
    }

    suspend fun setDownloadMaxParallelBooks(v: Int) {
        dataStore.edit { it[Keys.DOWNLOAD_MAX_PARALLEL_BOOKS] = v.coerceIn(1, 8) }
    }

    suspend fun setDownloadStrictCleanup(v: Boolean) {
        dataStore.edit { it[Keys.DOWNLOAD_STRICT_CLEANUP] = v }
    }

    suspend fun setDownloadVerifySizes(v: Boolean) {
        dataStore.edit { it[Keys.DOWNLOAD_VERIFY_SIZES] = v }
    }

    suspend fun setSeekBackSeconds(v: Int) {
        dataStore.edit { it[Keys.SEEK_BACK_SECONDS] = v.coerceIn(5, 120) }
    }

    suspend fun setSeekForwardSeconds(v: Int) {
        dataStore.edit { it[Keys.SEEK_FORWARD_SECONDS] = v.coerceIn(5, 120) }
    }

    suspend fun setPlaybackNotificationRetentionMinutes(v: Int) {
        dataStore.edit {
            it[Keys.PLAYBACK_NOTIFICATION_RETENTION_MINUTES] =
                normalizePlaybackNotificationRetentionMinutes(v)
        }
    }

    suspend fun setSleepTimerFadeSeconds(v: Int) {
        dataStore.edit { it[Keys.SLEEP_TIMER_FADE_SECONDS] = v.coerceIn(0, 180) }
    }

    suspend fun setSleepTimerShakeToExtend(v: Boolean) {
        dataStore.edit { it[Keys.SLEEP_TIMER_SHAKE_TO_EXTEND] = v }
    }

    suspend fun setSelectedLibraryId(id: String) {
        dataStore.edit { it[Keys.SELECTED_LIBRARY_ID] = id }
    }

    // Primary and secondary endpoints belong to the same configured server.
    private fun libraryCacheKey(prefs: Preferences): Preferences.Key<String> =
        stringPreferencesKey("library_names:" +
            (prefs[Keys.PRIMARY_URL]?.takeIf { it.isNotBlank() } ?: prefs[Keys.SECONDARY_URL].orEmpty()))

    suspend fun cacheLibraries(libraries: List<LibraryDto>) {
        dataStore.edit { prefs ->
            prefs[libraryCacheKey(prefs)] = Json.encodeToString(
                LibrariesResponseDto.serializer(), LibrariesResponseDto(libraries),
            )
        }
    }

    suspend fun cachedLibraries(): List<LibraryDto> {
        val prefs = dataStore.data.first()
        val encoded = prefs[libraryCacheKey(prefs)] ?: return emptyList()
        return runCatching {
            Json.decodeFromString(LibrariesResponseDto.serializer(), encoded).libraries
        }.getOrDefault(emptyList())
    }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { it[Keys.LIBRARY_VIEW_MODE] = mode.preferenceValue }
    }
}
