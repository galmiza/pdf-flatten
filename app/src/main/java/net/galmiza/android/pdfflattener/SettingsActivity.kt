package net.galmiza.android.pdfflattener

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import net.galmiza.android.pdfflattener.PdfFlattener.Category

/** What to merge, the output file name, theme and language, and the about section */
class SettingsActivity : Activity() {

    private lateinit var settings: Settings

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Theme.applyTo(this, base, Settings(base).theme)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        settings = Settings(this)

        val categories = findViewById<ViewGroup>(R.id.categories)
        for ((category, title, summary) in CATEGORIES) {
            val row = addRow(categories, getString(title), getString(summary))
            val toggle = row.findViewById<Switch>(R.id.toggle)
            toggle.visibility = View.VISIBLE
            toggle.isChecked = settings.isMerged(category)
            row.setOnClickListener {
                toggle.toggle()
                settings.setMerged(category, toggle.isChecked)
            }
            // Announced as a switch by screen readers, although the row handles the clicks
            row.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Switch::class.java.name
                    info.isCheckable = true
                    if (Build.VERSION.SDK_INT >= 36) {
                        info.checked = if (toggle.isChecked) {
                            AccessibilityNodeInfo.CHECKED_STATE_TRUE
                        } else {
                            AccessibilityNodeInfo.CHECKED_STATE_FALSE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        info.isChecked = toggle.isChecked
                    }
                }
            }
        }

        val suffix = findViewById<EditText>(R.id.suffix)
        val example = findViewById<TextView>(R.id.suffix_example)
        fun showExample() {
            val name = MainActivity.flattenedName(getString(R.string.example_name), settings.suffix)
            example.text = getString(R.string.suffix_example, name)
        }
        suffix.setText(settings.suffix)
        showExample()
        suffix.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                settings.suffix = s.toString()
                showExample()
            }
        })

        val display = findViewById<ViewGroup>(R.id.display)
        val themes = resources.getStringArray(R.array.themes)
        val themeRow = addRow(display, getString(R.string.theme), themes[settings.theme])
        themeRow.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.theme)
                .setSingleChoiceItems(themes, settings.theme) { dialog, which ->
                    dialog.dismiss()
                    themeRow.findViewById<TextView>(R.id.summary).text = themes[which]
                    Theme.set(this, which)
                    if (Build.VERSION.SDK_INT < 31) recreate()
                }
                .show()
        }
        // Per-app language: a system screen from Android 13 on; before, the app follows the phone
        if (Build.VERSION.SDK_INT >= 33) {
            addRow(display, getString(R.string.language), getString(R.string.language_summary)).setOnClickListener {
                open(Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        }

        val about = findViewById<ViewGroup>(R.id.about)
        addRow(about, getString(R.string.privacy), getString(R.string.privacy_summary)).setOnClickListener {
            showText(R.string.privacy, getString(R.string.privacy_text, CONTACT_EMAIL))
        }
        addRow(about, getString(R.string.licenses), getString(R.string.licenses_summary)).setOnClickListener {
            val text = resources.openRawResource(R.raw.licenses).bufferedReader().use { it.readText() }
            showText(R.string.licenses, text)
        }
        addRow(about, getString(R.string.contact), CONTACT_EMAIL).setOnClickListener {
            open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name)))
        }
        addRow(about, getString(R.string.source_code), SOURCE_URL.removePrefix("https://")).setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        addRow(about, getString(R.string.version), version).isClickable = false
    }

    private fun addRow(parent: ViewGroup, title: String, summary: String): View {
        val row = layoutInflater.inflate(R.layout.item_setting, parent, false)
        row.findViewById<TextView>(R.id.title).text = title
        row.findViewById<TextView>(R.id.summary).text = summary
        row.findViewById<View>(R.id.toggle).visibility = View.GONE
        parent.addView(row)
        return row
    }

    /** Mail and web links go to other apps; nothing happens if none is installed */
    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // no mail app or browser: the address stays readable in the row
        }
    }

    private fun showText(title: Int, text: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private companion object {
        const val CONTACT_EMAIL = "galmiza@gmail.com"
        const val SOURCE_URL = "https://github.com/galmiza/pdf-flatten"

        val CATEGORIES = listOf(
            Triple(Category.HANDWRITING, R.string.merge_handwriting, R.string.merge_handwriting_summary),
            Triple(Category.TEXT, R.string.merge_text, R.string.merge_text_summary),
            Triple(Category.MARKUP, R.string.merge_markup, R.string.merge_markup_summary),
            Triple(Category.NOTES, R.string.merge_notes, R.string.merge_notes_summary),
            Triple(Category.FORMS, R.string.merge_forms, R.string.merge_forms_summary),
            Triple(Category.LINKS, R.string.merge_links, R.string.merge_links_summary),
        )
    }
}
