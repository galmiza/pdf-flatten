package net.galmiza.android.pdfflattener

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only access to the flattened PDF for the app it is shared with. A dozen lines of
 * framework code instead of the AndroidX FileProvider, to keep the app dependency-free.
 */
class OutputProvider : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri) = "application/pdf"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read-only")
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val file = fileFor(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val known = columns.filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        return MatrixCursor(known.toTypedArray(), 1).apply {
            addRow(known.map { if (it == OpenableColumns.DISPLAY_NAME) file.name else file.length() })
        }
    }

    private fun fileFor(uri: Uri): File {
        val dir = outputDir(context!!)
        val file = File(dir, uri.lastPathSegment ?: throw FileNotFoundException(uri.toString()))
        // Only files directly inside the output directory are served
        if (file.canonicalFile.parentFile != dir.canonicalFile || !file.isFile) {
            throw FileNotFoundException(uri.toString())
        }
        return file
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        /** Holds the latest flattened PDF; emptied before each new document (not in the trimmable cache) */
        fun outputDir(context: Context) = File(context.noBackupFilesDir, "out")

        fun uriFor(context: Context, file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.output")
            .appendPath(file.name)
            .build()
    }
}
