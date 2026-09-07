package com.narratome.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.narratome.data.repository.CoverCacheRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.FileNotFoundException

class ArtworkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ArtworkProviderEntryPoint {
        fun repository(): CoverCacheRepository
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        if (mode != "r" || uri.scheme != "content" ||
            uri.authority != "${context.packageName}.artwork" || uri.pathSegments.size != 1 ||
            uri.query != null || uri.fragment != null) throw FileNotFoundException("Invalid artwork request")
        val itemId = uri.lastPathSegment ?: throw FileNotFoundException("Missing artwork ID")
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ArtworkProviderEntryPoint::class.java
        )
        val repository = hiltEntryPoint.repository()

        val file = repository.localCoverFile(itemId)

        return if (file != null) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            throw FileNotFoundException("Cover not found")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
