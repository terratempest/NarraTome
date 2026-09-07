package com.narratome.data.catalog

import com.narratome.data.local.db.CatalogItemEntity
import com.narratome.data.remote.dto.MediaProgressMeDto
import com.narratome.data.remote.dto.toBookSummaryOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private fun JsonPrimitive.asEpochMsOrZero(): Long =
    longOrNull
        ?: contentOrNull?.toLongOrNull()
        ?: 0L

/**
 * Maps minified `GET /api/libraries/{id}/items` rows into [CatalogItemEntity] stubs before hydration.
 */
fun JsonElement.toCatalogStubFromMinified(defaultLibraryId: String): CatalogItemEntity? {
    val obj = this as? JsonObject ?: return null
    val parsed = obj.toBookSummaryOrNull(defaultLibraryId) ?: return null
    val u1 = obj["updatedAt"]?.jsonPrimitive?.asEpochMsOrZero() ?: 0L
    val serverUpdated = when {
        u1 > 0L -> u1
        else -> {
            val m = obj["mtimeMs"]?.jsonPrimitive?.asEpochMsOrZero() ?: 0L
            if (m > 0L) m else obj["birthtimeMs"]?.jsonPrimitive?.asEpochMsOrZero() ?: 0L
        }
    }
    val created = obj["createdAt"]?.jsonPrimitive?.longOrNull
    val addedRaw = obj["addedAt"]?.jsonPrimitive?.asEpochMsOrZero() ?: 0L
    val addedAt = created ?: addedRaw.takeIf { it > 0L }
    val media = obj["media"]?.jsonObject
    val seriesName = media?.get("metadata")?.jsonObject?.get("seriesName")?.jsonPrimitive?.contentOrNull
    return CatalogItemEntity(
        libraryItemId = parsed.id,
        libraryId = parsed.libraryId,
        serverUpdatedAtEpochMs = serverUpdated,
        title = parsed.title,
        author = parsed.author,
        seriesName = seriesName,
        seriesId = null,
        addedAtEpochMs = addedAt,
        coverPath = parsed.coverPath,
        progress = parsed.progress,
        hydrated = false,
        payloadJson = null,
    )
}

fun JsonElement.toProgressSnapshotFromMinified(defaultLibraryId: String): MediaProgressMeDto? {
    val obj = this as? JsonObject ?: return null
    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val progress = obj["userMediaProgress"] as? JsonObject ?: return null
    val libraryId = obj["libraryId"]?.jsonPrimitive?.contentOrNull ?: defaultLibraryId
    return MediaProgressMeDto(
        libraryItemId = id,
        libraryId = libraryId,
        episodeId = progress["episodeId"]?.jsonPrimitive?.contentOrNull,
        hideFromContinueListening = progress["hideFromContinueListening"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
        duration = progress["duration"]?.jsonPrimitive?.doubleOrNull,
        currentTime = progress["currentTime"]?.jsonPrimitive?.doubleOrNull,
        progress = progress["progress"]?.jsonPrimitive?.doubleOrNull,
        lastUpdate = progress["lastUpdate"]?.jsonPrimitive?.asEpochMsOrZero()?.takeIf { it > 0L },
        startedAt = progress["startedAt"]?.jsonPrimitive?.asEpochMsOrZero()?.takeIf { it > 0L },
        finishedAt = progress["finishedAt"]?.jsonPrimitive?.asEpochMsOrZero()?.takeIf { it > 0L },
    )
}
