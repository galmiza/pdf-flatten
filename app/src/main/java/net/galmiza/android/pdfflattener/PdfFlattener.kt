package net.galmiza.android.pdfflattener

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Burns the visible annotations of the selected [Category]s (ink signatures, stamps, highlights,
 * notes, form fields...) into the content of their page, then removes them. The drawing stays
 * vector: the appearance stream the annotating app produced is drawn as a form XObject, exactly
 * like a viewer shows it, so text remains selectable and the file keeps its size.
 *
 * Annotations of the other categories are left untouched, with their popups.
 */
class PdfFlattener {

    /** Annotation families the user can choose to merge, from their PDF subtype */
    enum class Category {
        HANDWRITING, TEXT, MARKUP, NOTES, FORMS, LINKS,
        /** Rare types (redaction, watermark, multimedia...): always merged, not offered as a choice */
        OTHER;

        companion object {
            fun of(annotation: PDAnnotation): Category = when (annotation.subtype) {
                "Ink", "Stamp" -> HANDWRITING
                "FreeText" -> TEXT
                "Highlight", "Underline", "StrikeOut", "Squiggly", "Caret",
                "Square", "Circle", "Line", "Polygon", "PolyLine" -> MARKUP
                "Text", "FileAttachment", "Sound" -> NOTES
                "Widget" -> FORMS
                "Link" -> LINKS
                else -> OTHER
            }
        }
    }

    data class Options(
        /** Links are kept by default: they are invisible and still useful */
        val categories: Set<Category> = Category.entries.toSet() - Category.LINKS,
        /** Needed only for PDFs that ask for a password to open */
        val password: String = "",
    )

    data class Result(
        val pages: Int,
        /** Annotations drawn into their page (links: deactivated), per category */
        val merged: Map<Category, Int>,
        /** Annotations left as they were, per category */
        val kept: Map<Category, Int>,
    ) {
        val mergedCount get() = merged.values.sum()
    }

    fun interface Progress {
        fun onPage(done: Int, total: Int)
    }

    /**
     * Reads [input] and writes the flattened copy to [output]. Pages are processed one at a time
     * and PDFBox buffers beyond [MAX_MAIN_MEMORY] in [tempDir], so large documents don't exhaust RAM.
     * The copy is never encrypted, so recipients can open it without the password.
     *
     * @throws com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException if the file needs a
     * password to open and [Options.password] is not it
     */
    fun flatten(
        input: File, output: File, tempDir: File,
        options: Options = Options(),
        progress: Progress = Progress { _, _ -> },
    ): Result {
        val memory = MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY).setTempDir(tempDir)
        PDDocument.load(input, options.password, memory).use { document ->
            if (document.isEncrypted) document.isAllSecurityToBeRemoved = true

            val selected = options.categories + Category.OTHER
            // Read without PDFBox's default fixup, which edits the form even when it should stay as is
            val form = if (Category.FORMS in selected) document.documentCatalog.getAcroForm(null) else null
            if (form != null) {
                // Fields whose look is left to the viewer have no appearance to draw yet
                val missing = form.fieldTree.any { field ->
                    field is PDTerminalField && field.widgets.any { it.normalAppearanceStream == null }
                }
                if (form.needAppearances || missing) runCatching { form.refreshAppearances() }
                // Dynamic XFA forms are rendered from XML by a few viewers only; keep the static PDF
                form.xfa = null
            }

            val merged = HashMap<Category, Int>()
            val kept = HashMap<Category, Int>()
            val total = document.numberOfPages
            document.pages.forEachIndexed { index, page ->
                flattenPage(document, page, selected, merged, kept)
                progress.onPage(index + 1, total)
            }

            // The fields' widgets are now page content; the empty form would only confuse viewers
            if (form != null && kept[Category.FORMS] == null) document.documentCatalog.acroForm = null

            document.save(output)
            return Result(total, merged, kept)
        }
    }

    private fun flattenPage(
        document: PDDocument, page: PDPage, selected: Set<Category>,
        merged: MutableMap<Category, Int>, kept: MutableMap<Category, Int>,
    ) {
        val annotations = page.annotations
        if (annotations.isEmpty()) return

        val toDraw = ArrayList<PDAnnotation>()
        val toKeep = ArrayList<PDAnnotation>()
        val popups = ArrayList<PDAnnotationPopup>()
        for (annotation in annotations) {
            if (annotation is PDAnnotationPopup) {
                popups += annotation
                continue
            }
            val category = Category.of(annotation)
            when {
                category !in selected -> {
                    toKeep += annotation
                    kept.merge(category, 1, Int::plus)
                }
                // Hidden: nothing to draw, so merging it means dropping it
                annotation.isHidden || annotation.isNoView -> Unit
                appearanceOf(document, annotation) != null -> {
                    toDraw += annotation
                    merged.merge(category, 1, Int::plus)
                }
                // A link has nothing to show: merging it means deactivating it
                category == Category.LINKS -> merged.merge(category, 1, Int::plus)
                else -> {
                    toKeep += annotation
                    kept.merge(category, 1, Int::plus)
                }
            }
        }

        // A popup only displays its parent's comment: it goes away with it
        val keptDictionaries = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
        toKeep.mapTo(keptDictionaries) { it.cosObject }
        toKeep += popups.filter { (it.cosObject.getDictionaryObject(COSName.PARENT) as? COSDictionary) in keptDictionaries }

        if (toDraw.isNotEmpty()) {
            // resetContext wraps the existing content in q/Q, so whatever state it leaves behind
            // (transformations, colors, clipping) cannot shift the annotations drawn after it
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                for (annotation in toDraw) {
                    draw(stream, appearanceOf(document, annotation)!!, annotation.rectangle)
                }
            }
        }

        if (toKeep.isEmpty()) page.cosObject.removeItem(COSName.ANNOTS) else page.annotations = toKeep
    }

    /** The normal appearance of [annotation], built by PDFBox for the common types that lack one. */
    private fun appearanceOf(document: PDDocument, annotation: PDAnnotation): PDAppearanceStream? {
        annotation.normalAppearanceStream?.let { return it }
        if (annotation is PDAnnotationWidget) return null
        runCatching { annotation.constructAppearances(document) }
        return annotation.normalAppearanceStream
    }

    /**
     * Draws [appearance] the way PDF 32000-1 §12.5.5 places it: its bounding box, transformed by
     * its matrix, is mapped onto the annotation [rect].
     */
    private fun draw(stream: PDPageContentStream, appearance: PDAppearanceStream, rect: PDRectangle?) {
        val bbox = appearance.bBox ?: return
        if (rect == null) return
        val box = transformedBounds(bbox, appearance.matrix)
        val width = box[2] - box[0]
        val height = box[3] - box[1]
        if (width <= 0f || height <= 0f || rect.width <= 0f || rect.height <= 0f) return

        // Some writers omit these keys in appearance streams; a form XObject requires them
        appearance.cosObject.setItem(COSName.TYPE, COSName.XOBJECT)
        appearance.cosObject.setItem(COSName.SUBTYPE, COSName.FORM)

        val scaleX = rect.width / width
        val scaleY = rect.height / height
        stream.saveGraphicsState()
        stream.transform(
            Matrix(
                scaleX, 0f, 0f, scaleY,
                rect.lowerLeftX - box[0] * scaleX,
                rect.lowerLeftY - box[1] * scaleY,
            )
        )
        stream.drawForm(appearance) // the form applies its own matrix
        stream.restoreGraphicsState()
    }

    companion object {
        /** RAM PDFBox may use for buffers before spilling to temporary files */
        const val MAX_MAIN_MEMORY = 16L * 1024 * 1024

        /** Axis-aligned bounds `[minX, minY, maxX, maxY]` of [box] once transformed by [m]. */
        internal fun transformedBounds(box: PDRectangle, m: Matrix): FloatArray {
            val xs = floatArrayOf(box.lowerLeftX, box.upperRightX)
            val ys = floatArrayOf(box.lowerLeftY, box.upperRightY)
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (x in xs) for (y in ys) {
                val tx = m.scaleX * x + m.shearX * y + m.translateX
                val ty = m.shearY * x + m.scaleY * y + m.translateY
                minX = minOf(minX, tx); maxX = maxOf(maxX, tx)
                minY = minOf(minY, ty); maxY = maxOf(maxY, ty)
            }
            return floatArrayOf(minX, minY, maxX, maxY)
        }
    }
}
