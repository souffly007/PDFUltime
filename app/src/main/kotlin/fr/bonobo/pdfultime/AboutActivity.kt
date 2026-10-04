package fr.bonobo.pdfultime

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class AboutActivity : ComponentActivity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(26), dp(24), dp(22))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(24), dp(26) + bars.top, dp(24), dp(22) + bars.bottom)
            insets
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.pdf_ultime_icon)
            contentDescription = "Icône PDF Ultime"
        }, LinearLayout.LayoutParams(dp(130), dp(130)))
        root.addView(TextView(this).apply {
            text = "PDF Ultime"
            textSize = 30f
            setTypeface(typeface, 1)
            setPadding(0, dp(16), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Lecteur et éditeur PDF libre\nVersion 1.1.6\n\nCréée avec passion par Bonobo.\n\nPDF Ultime est distribué sous licence GPL v3."
            textSize = 16f
            gravity = Gravity.CENTER
        })
        setContentView(root)
    }
}
