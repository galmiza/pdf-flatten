package net.galmiza.android.pdfflattener

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * The light/dark choice made in the settings. Android 12+ applies it to the whole app, splash
 * screen included, and remembers it; older versions get it through each activity's configuration.
 */
object Theme {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    /**
     * Call from [Activity.attachBaseContext], after super, with its [base] context: the activity's
     * own resources must not be touched before the override is applied.
     */
    fun applyTo(activity: Activity, base: Context, theme: Int) {
        if (Build.VERSION.SDK_INT >= 31 || theme == SYSTEM) return
        val night = if (theme == DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        activity.applyOverrideConfiguration(Configuration().apply {
            uiMode = (base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        })
    }

    /** Applies a new choice; on Android 12+ the system recreates the open activities itself */
    fun set(context: Context, theme: Int) {
        Settings(context).theme = theme
        if (Build.VERSION.SDK_INT >= 31) {
            val mode = when (theme) {
                LIGHT -> UiModeManager.MODE_NIGHT_NO
                DARK -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO // follow the system
            }
            context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
        }
    }
}
