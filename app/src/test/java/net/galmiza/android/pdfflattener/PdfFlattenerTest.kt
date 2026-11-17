package net.galmiza.android.pdfflattener

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.util.Matrix
import net.galmiza.android.pdfflattener.PdfFlattener.Category
import net.galmiza.android.pdfflattener.PdfFlattener.Options
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfFlattenerTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun drawsVisibleAnnotationsIntoThePageAndRemovesThem() {
        val input = pdf { document, page ->
            PDPageContentStream(document, page).use { it.addRect(50f, 700f, 200f, 50f); it.fill() }
            val signature = ink(document, PDRectangle(100f, 100f, 200f, 100f))
            page.annotations = listOf(
                signature,
                popup(signature),
                PDAnnotationLink().apply { rectangle = PDRectangle(50f, 700f, 200f, 50f) },
                ink(document, PDRectangle(300f, 100f, 50f, 50f)).apply { isHidden = true },
            )
            document.addPage(PDPage(PDRectangle.A4)) // no annotation
        }

        val output = folder.root.resolve("signed_flattened.pdf")
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = PdfFlattener().flatten(input, output, folder.newFolder()) { done, total ->
            progress += done to total
        }

        assertEquals(2, result.pages)
        assertEquals(mapOf(Category.HANDWRITING to 1), result.merged)
        assertEquals(mapOf(Category.LINKS to 1), result.kept)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        PDDocument.load(output).use { document ->
            val page = document.getPage(0)
            val remaining = page.annotations
            assertEquals(1, remaining.size)
            assertTrue(remaining[0] is PDAnnotationLink)

            val content = page.contents.bufferedReader(Charsets.ISO_8859_1).readText()
            // The ink is placed at its rectangle: bbox (0,0)-(200,100) → rect origin (100,100), scale 1
            assertTrue(content, Regex("""1 0 0 1 100(\.0+)? 100(\.0+)? cm\s+/\w+ Do""").containsMatchIn(content))
            // The original drawing was isolated from the annotations by q ... Q
            assertTrue(content, content.trimStart().startsWith("q"))
            assertEquals(1, page.resources.xObjectNames.count())
        }
    }

    @Test
    fun leavesUnselectedCategoriesWithTheirPopups() {
        val input = pdf { document, page ->
            val signature = ink(document, PDRectangle(100f, 100f, 200f, 100f))
            val box = square(PDRectangle(300f, 300f, 50f, 50f))
            page.annotations = listOf(signature, popup(signature), box, popup(box))
        }

        val output = folder.root.resolve("out.pdf")
        val options = Options(categories = setOf(Category.MARKUP))
        val result = PdfFlattener().flatten(input, output, folder.newFolder(), options)

        assertEquals(mapOf(Category.MARKUP to 1), result.merged)
        assertEquals(mapOf(Category.HANDWRITING to 1), result.kept)
        PDDocument.load(output).use { document ->
            val subtypes = document.getPage(0).annotations.map { it.subtype }
            assertEquals(listOf("Ink", "Popup"), subtypes)
        }
    }

    @Test
    fun removesLinksOnRequest() {
        val input = pdf { _, page ->
            page.annotations = listOf(PDAnnotationLink().apply { rectangle = PDRectangle(50f, 700f, 200f, 50f) })
        }

        val output = folder.root.resolve("out.pdf")
        val result = PdfFlattener().flatten(input, output, folder.newFolder(), Options(Category.entries.toSet()))

        assertEquals(mapOf(Category.LINKS to 1), result.merged)
        PDDocument.load(output).use { assertTrue(it.getPage(0).annotations.isEmpty()) }
    }

    @Test
    fun formFieldsAreMergedOrKeptFillable() {
        val input = pdf { document, page ->
            val form = PDAcroForm(document, COSDictionary()) // no default font: unavailable in JVM tests
            document.documentCatalog.acroForm = form
            val field = PDTextField(form).apply { partialName = "name" }
            val widget = field.widgets.single().apply {
                rectangle = PDRectangle(100f, 500f, 200f, 20f)
                this.page = page
                appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance(document, rectangle)) }
            }
            form.fields = listOf(field)
            page.annotations = listOf(widget)
        }

        val kept = folder.root.resolve("kept.pdf")
        val keepForms = Options(categories = Category.entries.toSet() - Category.FORMS - Category.LINKS)
        assertEquals(mapOf(Category.FORMS to 1), PdfFlattener().flatten(input, kept, folder.newFolder(), keepForms).kept)
        PDDocument.load(kept).use {
            assertNotNull(it.documentCatalog.getAcroForm(null))
            assertEquals(1, it.getPage(0).annotations.size)
        }

        val merged = folder.root.resolve("merged.pdf")
        assertEquals(mapOf(Category.FORMS to 1), PdfFlattener().flatten(input, merged, folder.newFolder()).merged)
        PDDocument.load(merged).use {
            assertNull(it.documentCatalog.getAcroForm(null))
            assertTrue(it.getPage(0).annotations.isEmpty())
        }
    }

    @Test
    fun buildsAppearancesForAnnotationsThatHaveNone() {
        val input = pdf { _, page -> page.annotations = listOf(square(PDRectangle(100f, 100f, 80f, 40f))) }

        val result = PdfFlattener().flatten(input, folder.root.resolve("out.pdf"), folder.newFolder())

        assertEquals(mapOf(Category.MARKUP to 1), result.merged)
        assertTrue(result.kept.isEmpty())
    }

    @Test
    fun boundsFollowTheAppearanceMatrix() {
        val box = PDRectangle(10f, 20f, 100f, 50f)
        // 90° rotation then translation: (x, y) → (-y + 5, x + 7)
        val rotated = Matrix(0f, 1f, -1f, 0f, 5f, 7f)
        assertArrayEquals(floatArrayOf(-65f, 17f, -15f, 117f), PdfFlattener.transformedBounds(box, rotated), 1e-4f)
        assertArrayEquals(floatArrayOf(10f, 20f, 110f, 70f), PdfFlattener.transformedBounds(box, Matrix()), 1e-4f)
    }

    @Test
    fun outputNameGetsTheSuffix() {
        assertEquals("contract_flattened.pdf", MainActivity.flattenedName("contract.pdf"))
        assertEquals("Lease 2026_flattened.pdf", MainActivity.flattenedName("Lease 2026.PDF"))
        assertEquals("a_b_flattened.pdf", MainActivity.flattenedName("a/b"))
        assertEquals("document_flattened.pdf", MainActivity.flattenedName(null))
        assertEquals("contrat_aplati.pdf", MainActivity.flattenedName("contrat.pdf", "_aplati"))
        assertEquals("contract_x_y.pdf", MainActivity.flattenedName("contract.pdf", "_x/y"))
    }

    /** A one-page A4 document set up by [build], saved to a temporary file */
    private fun pdf(build: (PDDocument, PDPage) -> Unit): File {
        val file = folder.newFile()
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            build(document, page)
            document.save(file)
        }
        return file
    }

    /** An ink signature as Android annotation apps write it: a stroked path in an appearance stream */
    private fun ink(document: PDDocument, rect: PDRectangle): PDAnnotation =
        PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE, "Ink")
            rectangle = rect
            appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance(document, rect)) }
        }

    private fun appearance(document: PDDocument, rect: PDRectangle) = PDAppearanceStream(document).apply {
        bBox = PDRectangle(rect.width, rect.height)
        resources = PDResources()
        stream.createOutputStream().use {
            it.write("0 0 1 RG 2 w 5 5 m ${rect.width - 5} ${rect.height - 5} l S".toByteArray())
        }
    }

    /** A square without appearance stream, like many apps write them */
    private fun square(rect: PDRectangle) =
        PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply { rectangle = rect }

    private fun popup(parent: PDAnnotation) = PDAnnotationPopup().apply {
        rectangle = PDRectangle(400f, 400f, 100f, 50f)
        cosObject.setItem(COSName.PARENT, parent.cosObject)
    }
}
