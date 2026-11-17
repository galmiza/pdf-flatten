package net.galmiza.android.pdfflattener

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Keeps the content clear of the system bars, which apps draw under from Android 15 on.
 * Older versions lay the window out between the bars themselves.
 */
fun View.padForSystemBars() {
    if (Build.VERSION.SDK_INT < 30) return
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        // The keyboard pushes the content up too, so text fields stay visible
        val ime = insets.getInsets(WindowInsets.Type.ime())
        view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + maxOf(bars.bottom, ime.bottom))
        WindowInsets.CONSUMED
    }
}
