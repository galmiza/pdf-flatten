package net.galmiza.android.pdfflattener

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/**
 * Renders pages of the flattened PDF with the platform renderer. Display mode ignores
 * annotations, like many mobile viewers do, so what shows here is what any recipient will see.
 * Not thread-safe: use it from a single thread.
 */
class PdfPreview(file: File) : Closeable {

    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount get() = renderer.pageCount

    /** Page [index] rendered [width] pixels wide; only one page bitmap lives at a time. */
    fun render(index: Int, width: Int): Bitmap = renderer.openPage(index).use { page ->
        val height = (width.toLong() * page.height / page.width).toInt().coerceIn(1, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE) // PDF pages are transparent by default
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    private companion object {
        /** Guards against absurd aspect ratios (e.g. a till receipt) allocating a huge bitmap */
        const val MAX_HEIGHT = 8192
    }
}
