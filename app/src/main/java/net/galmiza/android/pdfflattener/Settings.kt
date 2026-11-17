package net.galmiza.android.pdfflattener

import android.content.Context
import net.galmiza.android.pdfflattener.PdfFlattener.Category

/** The user's choices, kept on the device only */
class Settings(context: Context) {

    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether annotations of [category] are merged; links are only "merged" (removed) on request */
    fun isMerged(category: Category): Boolean =
        preferences.getBoolean(key(category), category != Category.LINKS)

    fun setMerged(category: Category, merged: Boolean) {
        preferences.edit().putBoolean(key(category), merged).apply()
    }

    var suffix: String
        get() = preferences.getString(KEY_SUFFIX, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SUFFIX
        set(value) = preferences.edit().putString(KEY_SUFFIX, value.trim()).apply()

    /** [Theme.SYSTEM], [Theme.LIGHT] or [Theme.DARK] */
    var theme: Int
        get() = preferences.getInt(KEY_THEME, Theme.SYSTEM)
        set(value) = preferences.edit().putInt(KEY_THEME, value).apply()

    fun options(password: String = "") = PdfFlattener.Options(
        categories = Category.entries.filter(::isMerged).toSet(),
        password = password,
    )

    private fun key(category: Category) = "merge_" + category.name.lowercase()

    companion object {
        const val DEFAULT_SUFFIX = "_flattened"
        private const val KEY_SUFFIX = "suffix"
        private const val KEY_THEME = "theme"
    }
}
