package com.nightxd.zerofloating

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class CamProvider : ContentProvider() {

    private fun fileFor(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        return File(File(ctx.cacheDir, "cam"), File(name).name)
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val f = fileFor(uri)
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun getType(uri: Uri): String? = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        const val AUTH = "com.nightxd.zerofloating.cam"
    }
}
