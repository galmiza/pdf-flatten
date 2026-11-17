package net.galmiza.android.pdfflattener

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import net.galmiza.android.pdfflattener.PdfFlattener.Category
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The main screen: pick (or receive) a PDF, flatten it, compare it with the original, then save
 * or share it. All file and PDF work runs on one background thread, which also owns the renderers.
 */
class MainActivity : Activity() {

    private lateinit var start: View
    private lateinit var headline: TextView
    private lateinit var body: TextView
    private lateinit var pickButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var hint: TextView
    private lateinit var result: View
    private lateinit var resultTitle: TextView
    private lateinit var resultDetails: TextView
    private lateinit var showOriginal: TextView
    private lateinit var showFlattened: TextView
    private lateinit var previewImage: ImageView
    private lateinit var caption: TextView
    private lateinit var pager: View
    private lateinit var previousButton: View
    private lateinit var nextButton: View
    private lateinit var pageText: TextView

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings

    private var output: File? = null
    private var title: String? = null
    private var details: String? = null
    private var original = false
    private var pageIndex = 0
    private var pageCount = 0
    private var busy = false
    private var canCompare = false

    /** Accessed on the worker thread only */
    private var originalPreview: PdfPreview? = null
    private var flattenedPreview: PdfPreview? = null

    /** Theme applied when this activity was created, to follow a change made in the settings */
    private var appliedTheme = Theme.SYSTEM

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        appliedTheme = Settings(base).theme
        Theme.applyTo(this, base, appliedTheme)
    }

    override fun onResume() {
        super.onResume()
        // Android 12+ recreates activities itself when the theme changes
        if (Build.VERSION.SDK_INT < 31 && settings.theme != appliedTheme && !busy) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        start = findViewById(R.id.start)
        headline = findViewById(R.id.headline)
        body = findViewById(R.id.body)
        pickButton = findViewById(R.id.pick)
        progressBar = findViewById(R.id.progress)
        hint = findViewById(R.id.hint)
        result = findViewById(R.id.result)
        resultTitle = findViewById(R.id.result_title)
        resultDetails = findViewById(R.id.result_details)
        showOriginal = findViewById(R.id.show_original)
        showFlattened = findViewById(R.id.show_flattened)
        previewImage = findViewById(R.id.preview)
        caption = findViewById(R.id.caption)
        pager = findViewById(R.id.pager)
        previousButton = findViewById(R.id.previous)
        nextButton = findViewById(R.id.next)
        pageText = findViewById(R.id.page)

        findViewById<View>(R.id.root).padForSystemBars()
        PDFBoxResourceLoader.init(applicationContext)
        settings = Settings(this)

        pickButton.setOnClickListener { pickPdf() }
        findViewById<View>(R.id.another).setOnClickListener { pickPdf() }
        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        showOriginal.setOnClickListener { setOriginal(true) }
        showFlattened.setOnClickListener { setOriginal(false) }
        previousButton.setOnClickListener { showPage(pageIndex - 1) }
        nextButton.setOnClickListener { showPage(pageIndex + 1) }
        findViewById<View>(R.id.save).setOnClickListener { askWhereToSave() }
        findViewById<View>(R.id.share).setOnClickListener { share() }

        val restored = savedInstanceState?.getString(STATE_OUTPUT)?.let(::File)?.takeIf { it.isFile }
        if (restored != null) {
            original = savedInstanceState.getBoolean(STATE_ORIGINAL)
            showResult(
                restored, savedInstanceState.getString(STATE_TITLE), savedInstanceState.getString(STATE_DETAILS),
                savedInstanceState.getInt(STATE_PAGE),
            )
        } else {
            showStart()
            if (savedInstanceState == null) handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        output?.let { outState.putString(STATE_OUTPUT, it.path) }
        outState.putString(STATE_TITLE, title)
        outState.putString(STATE_DETAILS, details)
        outState.putBoolean(STATE_ORIGINAL, original)
        outState.putInt(STATE_PAGE, pageIndex)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.execute { closePreviews() }
        worker.shutdown()
    }

    /** A PDF opened from a file manager ("Open with") or sent from another app ("Share") */
    private fun handleIntent(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) flatten(uri)
    }

    private fun pickPdf() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(PDF)
        startActivityForResult(intent, REQUEST_OPEN)
    }

    private fun askWhereToSave() {
        val file = output ?: return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(PDF)
            .putExtra(Intent.EXTRA_TITLE, file.name)
        startActivityForResult(intent, REQUEST_SAVE)
    }

    @Deprecated("Framework Activity API, kept to stay free of AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_OPEN -> flatten(uri)
            REQUEST_SAVE -> save(uri)
        }
    }

    private fun flatten(source: Uri, password: String = "") {
        if (busy) return
        busy = true
        output = null
        original = false
        showWorking()
        val options = settings.options(password)
        val suffix = settings.suffix

        worker.execute {
            val error = try {
                closePreviews()
                val name = displayName(source)
                val work = workDir().apply { deleteRecursively(); mkdirs() }

                // PDFBox and PdfRenderer need random access, which content streams don't offer.
                // The copy is kept for the "Original" preview.
                val input = File(work, ORIGINAL)
                val stream = contentResolver.openInputStream(source) ?: error("No content")
                stream.use { from -> input.outputStream().use { from.copyTo(it) } }

                // Cleared only now: the source may be the previous result, shared back to this app
                val outDir = OutputProvider.outputDir(this).apply { deleteRecursively(); mkdirs() }
                val file = File(outDir, flattenedName(name, suffix))
                val stats = PdfFlattener().flatten(input, file, work, options) { done, total ->
                    main.post { showProgress(done, total) }
                }
                main.post {
                    busy = false
                    showResult(file, resultTitle(stats), resultDetails(stats), 0)
                }
                null
            } catch (e: InvalidPasswordException) {
                main.post {
                    busy = false
                    showStart()
                    askPassword(source, wrong = password.isNotEmpty())
                }
                null
            } catch (e: OutOfMemoryError) {
                getString(R.string.error_memory)
            } catch (e: Exception) {
                getString(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            }
            if (error != null) main.post {
                busy = false
                showError(error)
            }
        }
    }

    private fun askPassword(source: Uri, wrong: Boolean) {
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint(R.string.password_hint)
            isSingleLine = true
        }
        val margin = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(margin, 0, margin, 0)
            addView(field)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.password_title)
            .setMessage(if (wrong) R.string.password_wrong else R.string.password_message)
            .setView(container)
            .setPositiveButton(R.string.open) { _, _ -> flatten(source, field.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        field.requestFocus()
    }

    private fun resultTitle(stats: PdfFlattener.Result): String {
        val pages = resources.getQuantityString(R.plurals.result_pages, stats.pages, stats.pages)
        val merged = if (stats.mergedCount == 0) {
            getString(R.string.result_nothing)
        } else {
            resources.getQuantityString(R.plurals.result_merged, stats.mergedCount, stats.mergedCount)
        }
        return listOf(merged, pages).joinToString(getString(R.string.list_separator)) { it.unbreakable() }
    }

    private fun resultDetails(stats: PdfFlattener.Result): String? {
        val merged = describe(stats.merged)
        val kept = describe(stats.kept)?.let { getString(R.string.result_kept, it) }
        return listOfNotNull(merged, kept).joinToString("\n").ifEmpty { null }
    }

    /** "52 form fields · 1 handwriting or signature", in a fixed category order */
    private fun describe(counts: Map<Category, Int>): String? = Category.entries
        .mapNotNull { category ->
            counts[category]?.let { resources.getQuantityString(PLURALS.getValue(category), it, it).unbreakable() }
        }
        .joinToString(getString(R.string.list_separator))
        .ifEmpty { null }

    /** Lines wrap between the items of a list, never inside one ("1 / text box") */
    private fun String.unbreakable() = replace(' ', '\u00A0')

    private fun showStart() {
        output = null
        start.visibility = View.VISIBLE
        result.visibility = View.GONE
        headline.setText(R.string.start_headline)
        body.setText(R.string.start_body)
        pickButton.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        hint.visibility = View.VISIBLE
    }

    private fun showWorking() {
        showStart()
        headline.setText(R.string.working_headline)
        body.setText(R.string.reading)
        pickButton.visibility = View.GONE
        hint.visibility = View.INVISIBLE
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
    }

    private fun showError(message: String) {
        showStart()
        headline.setText(R.string.error_headline)
        body.text = message
    }

    private fun showProgress(done: Int, total: Int) {
        progressBar.isIndeterminate = false
        progressBar.max = total
        progressBar.progress = done
        body.text = getString(R.string.progress, done, total)
    }

    private fun showResult(file: File, title: String?, details: String?, page: Int) {
        output = file
        this.title = title
        this.details = details
        start.visibility = View.GONE
        result.visibility = View.VISIBLE
        resultTitle.text = title ?: file.name
        resultDetails.text = details
        resultDetails.visibility = if (details == null) View.GONE else View.VISIBLE
        previewImage.setImageDrawable(null)
        updatePreviewControls()

        val source = File(workDir(), ORIGINAL)
        worker.execute {
            closePreviews()
            val count = try {
                flattenedPreview = PdfPreview(file)
                // An encrypted original can't be rendered without its password: no comparison then
                originalPreview = runCatching { PdfPreview(source) }.getOrNull()
                flattenedPreview!!.pageCount
            } catch (e: Exception) {
                0 // the file is still valid for other viewers; only the preview is unavailable
            }
            val comparable = originalPreview != null
            main.post {
                pageCount = count
                canCompare = comparable
                if (!comparable) original = false
                showPage(page.coerceIn(0, maxOf(count - 1, 0)))
            }
        }
    }

    private fun setOriginal(original: Boolean) {
        if (this.original == original) return
        this.original = original
        showPage(pageIndex)
    }

    private fun showPage(index: Int) {
        if (index !in 0 until pageCount) {
            updatePreviewControls()
            return
        }
        pageIndex = index
        updatePreviewControls()
        val original = original
        val parent = previewImage.parent as View
        val width = (parent.width - parent.paddingLeft - parent.paddingRight).takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        worker.execute {
            val bitmap: Bitmap? = try {
                (if (original) originalPreview else flattenedPreview)?.render(index, width)
            } catch (e: Exception) {
                null
            }
            main.post {
                if (index == pageIndex && original == this.original && output != null) {
                    previewImage.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun updatePreviewControls() {
        findViewById<View>(R.id.segments).visibility = if (canCompare) View.VISIBLE else View.GONE
        showOriginal.isSelected = original
        showFlattened.isSelected = !original
        caption.setText(if (original) R.string.caption_original else R.string.caption_flattened)
        pager.visibility = if (pageCount > 1) View.VISIBLE else View.INVISIBLE
        previousButton.isEnabled = pageIndex > 0
        nextButton.isEnabled = pageIndex < pageCount - 1
        pageText.text = getString(R.string.page, pageIndex + 1, pageCount)
    }

    private fun save(destination: Uri) {
        val file = output ?: return
        worker.execute {
            val ok = try {
                val stream = contentResolver.openOutputStream(destination, "wt") ?: error("No stream")
                stream.use { to -> file.inputStream().use { it.copyTo(to) } }
                true
            } catch (e: Exception) {
                false
            }
            main.post {
                Toast.makeText(this, if (ok) R.string.saved else R.string.error_save, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun share() {
        val file = output ?: return
        val uri = OutputProvider.uriFor(this, file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(PDF)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(file.name, uri) // lets the chooser preview the file
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun closePreviews() {
        originalPreview?.close()
        flattenedPreview?.close()
        originalPreview = null
        flattenedPreview = null
    }

    /**
     * Holds the copy of the document being processed, kept for the "Original" preview. Not in the
     * cache: Android empties it when storage runs low, which would lose the result before it is saved.
     */
    private fun workDir() = File(noBackupFilesDir, "work")

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        private const val PDF = "application/pdf"
        private const val ORIGINAL = "original.pdf"
        private const val REQUEST_OPEN = 1
        private const val REQUEST_SAVE = 2
        private const val STATE_OUTPUT = "output"
        private const val STATE_TITLE = "title"
        private const val STATE_DETAILS = "details"
        private const val STATE_ORIGINAL = "original"
        private const val STATE_PAGE = "page"

        private val PLURALS = mapOf(
            Category.HANDWRITING to R.plurals.count_handwriting,
            Category.TEXT to R.plurals.count_text,
            Category.MARKUP to R.plurals.count_markup,
            Category.NOTES to R.plurals.count_notes,
            Category.FORMS to R.plurals.count_forms,
            Category.LINKS to R.plurals.count_links,
            Category.OTHER to R.plurals.count_other,
        )

        /** `contract.pdf` → `contract_flattened.pdf`, without characters that are invalid in file names */
        internal fun flattenedName(original: String?, suffix: String = Settings.DEFAULT_SUFFIX): String {
            val invalid = Regex("""[\\/:*?"<>|\u0000-\u001f]""")
            val base = original.orEmpty()
                .replace(Regex("""\.pdf$""", RegexOption.IGNORE_CASE), "")
                .replace(invalid, "_")
                .trim()
                .ifEmpty { "document" }
            return base + suffix.replace(invalid, "_") + ".pdf"
        }
    }
}
