package com.narratome.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.narratome.R
import com.narratome.data.repository.CoverCacheRepository

fun playbackArtworkUri(
    context: Context,
    coverCacheRepository: CoverCacheRepository,
    itemId: String,
): Uri =
    if (coverCacheRepository.localCoverFile(itemId) != null) {
        "content://${context.packageName}.artwork/$itemId".toUri()
    } else {
        fallbackArtworkUri(context)
    }

private fun fallbackArtworkUri(context: Context): Uri {
    val resources = context.resources
    val resourceId = R.drawable.narratomeicon
    return Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(resources.getResourcePackageName(resourceId))
        .appendPath(resources.getResourceTypeName(resourceId))
        .appendPath(resources.getResourceEntryName(resourceId))
        .build()
}
