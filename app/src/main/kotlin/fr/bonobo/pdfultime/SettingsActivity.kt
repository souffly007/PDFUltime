package fr.bonobo.pdfultime

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(20))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(22), dp(24) + bars.top, dp(22), dp(20) + bars.bottom)
            insets
        }

        root.addView(TextView(this).apply {
            text = "Paramètres"
            textSize = 30f
            setTypeface(typeface, 1)
        })
        root.addView(TextView(this).apply {
            text = "Personnalisation de PDF Ultime"
            textSize = 16f
            setPadding(0, dp(4), 0, dp(22))
        })

        root.addView(TextView(this).apply {
            text = "Thème de l’application"
            textSize = 20f
            setTypeface(typeface, 1)
        })

        val group = RadioGroup(this)
        val choices = listOf(
            ThemeManager.LIGHT to "Clair",
            ThemeManager.DARK to "Sombre",
            ThemeManager.CYANOGEN to "CyanogenMod"
        )
        val current = ThemeManager.current(this)
        choices.forEach { (value, label) ->
            val radio = RadioButton(this).apply {
                text = label
                textSize = 17f
                tag = value
                isChecked = value == current
                setPadding(0, dp(8), 0, dp(8))
            }
            group.addView(radio)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val selected = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            ThemeManager.set(this, selected)
            recreate()
        }
        root.addView(group)

        root.addView(TextView(this).apply {
            text = "Le thème est mémorisé et appliqué à tous les écrans."
            alpha = .7f
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(root)
    }
}
