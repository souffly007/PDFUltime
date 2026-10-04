package fr.bonobo.pdfultime

import android.content.Context
import androidx.activity.ComponentActivity

object ThemeManager {
    private const val PREFS = "pdf_ultime_preferences"
    private const val KEY_THEME = "theme"

    const val LIGHT = "light"
    const val DARK = "dark"
    const val CYANOGEN = "cyanogen"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, LIGHT) ?: LIGHT

    fun apply(activity: ComponentActivity) {
        when (current(activity)) {
            DARK -> activity.setTheme(R.style.Theme_PDFUltime_Dark)
            CYANOGEN -> activity.setTheme(R.style.Theme_PDFUltime_Cyanogen)
            else -> activity.setTheme(R.style.Theme_PDFUltime)
        }
    }

    fun set(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme)
            .apply()
    }

    fun isDark(context: Context): Boolean = current(context) != LIGHT

    fun background(context: Context): Int =
        when (current(context)) {
            CYANOGEN -> 0xFF071114.toInt()
            DARK -> 0xFF101114.toInt()
            else -> 0xFFF7F7FB.toInt()
        }

    fun surface(context: Context): Int =
        when (current(context)) {
            CYANOGEN -> 0xFF10252A.toInt()
            DARK -> 0xFF1E2024.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

    fun primary(context: Context): Int =
        if (current(context) == CYANOGEN) 0xFF00BCD4.toInt() else 0xFF6C5CE7.toInt()

    fun primaryText(context: Context): Int =
        if (current(context) == CYANOGEN) 0xFF001114.toInt() else 0xFFFFFFFF.toInt()

    fun text(context: Context): Int =
        if (isDark(context)) 0xFFF4F4F4.toInt() else 0xFF202124.toInt()
}
