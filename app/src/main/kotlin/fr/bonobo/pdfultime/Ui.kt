package fr.bonobo.pdfultime

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

/** Petits composants d’interface partagés par les nouveaux écrans (même style que l’existant, sans XML). */
object Ui {
    fun installInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    fun header(activity: ComponentActivity, title: String): LinearLayout {
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(8), activity.dp(4), activity.dp(12), activity.dp(4))
        }
        bar.addView(Button(activity).apply {
            text = "‹"
            textSize = 28f
            setOnClickListener { activity.finish() }
        }, LinearLayout.LayoutParams(activity.dp(56), activity.dp(56)))
        bar.addView(TextView(activity).apply {
            text = title
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemeManager.text(activity))
            setPadding(activity.dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return bar
    }

    fun heading(context: Context, label: String): TextView = TextView(context).apply {
        text = label
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ThemeManager.text(context))
        setPadding(0, context.dp(18), 0, context.dp(6))
    }

    fun body(context: Context, label: String, alphaValue: Float = .8f): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        setTextColor(ThemeManager.text(context))
        alpha = alphaValue
        setPadding(0, context.dp(2), 0, context.dp(6))
    }

    fun card(context: Context): MaterialCardView = MaterialCardView(context).apply {
        radius = context.dp(20).toFloat()
        setCardBackgroundColor(ThemeManager.surface(context))
        setContentPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
    }

    fun button(context: Context, label: String, onClick: () -> Unit): MaterialButton = MaterialButton(context).apply {
        text = label
        isAllCaps = false
        setTextColor(ThemeManager.primaryText(context))
        backgroundTintList = ColorStateList.valueOf(ThemeManager.primary(context))
        cornerRadius = context.dp(24)
        setOnClickListener { onClick() }
    }

    fun input(context: Context, hintText: String, password: Boolean = false): EditText = EditText(context).apply {
        hint = hintText
        setTextColor(ThemeManager.text(context))
        setHintTextColor((ThemeManager.text(context) and 0x00FFFFFF) or 0x80000000.toInt())
        inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        setSingleLine(true)
    }

    fun checkbox(context: Context, label: String, checked: Boolean = false): CheckBox = CheckBox(context).apply {
        text = label
        isChecked = checked
        textSize = 16f
        setTextColor(ThemeManager.text(context))
    }

    fun radios(context: Context, labels: List<String>, selected: Int = 0): RadioGroup {
        val group = RadioGroup(context)
        labels.forEachIndexed { index, label ->
            group.addView(RadioButton(context).apply {
                id = View.generateViewId()
                text = label
                textSize = 16f
                setTextColor(ThemeManager.text(context))
                setPadding(0, context.dp(6), 0, context.dp(6))
                isChecked = index == selected
            })
        }
        return group
    }

    fun selectedIndex(group: RadioGroup): Int {
        val checked = group.checkedRadioButtonId
        for (i in 0 until group.childCount) {
            if (group.getChildAt(i).id == checked) return i
        }
        return 0
    }

    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0) cursor.getString(column)?.let { return it }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment ?: "document.pdf"
    }

    fun baseName(context: Context, uri: Uri): String =
        displayName(context, uri).replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "")

    fun fileSize(context: Context, uri: Uri): Long {
        try {
            if (uri.scheme == "file") return File(uri.path ?: return 0L).length()
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (column >= 0 && !cursor.isNull(column)) return cursor.getLong(column)
                }
            }
        } catch (_: Exception) {
        }
        return 0L
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576L -> String.format(Locale.getDefault(), "%.1f Mo", bytes / 1_048_576.0)
        bytes >= 1024L -> "${bytes / 1024} Ko"
        else -> "$bytes o"
    }

    fun createPdfIntent(name: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = "application/pdf"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_TITLE, name)
    }

    /** Copie le fichier temporaire vers l’emplacement choisi par l’utilisateur. */
    fun saveFile(context: Context, source: File, destination: Uri): Boolean {
        return try {
            val stream = context.contentResolver.openOutputStream(destination, "wt") ?: return false
            stream.use { output -> source.inputStream().use { input -> input.copyTo(output) } }
            true
        } catch (_: Exception) {
            false
        }
    }
}
