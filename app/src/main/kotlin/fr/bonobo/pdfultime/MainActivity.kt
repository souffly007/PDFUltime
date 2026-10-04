package fr.bonobo.pdfultime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.res.ColorStateList
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val openPdf = 41
    private val mergePdfs = 42
    private val deletePages = 43
    private val signaturePdf = 44
    private val annotatePdf = 45
    private var displayedTheme: String? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        displayedTheme = ThemeManager.current(this)
        buildHome()
        intent?.data?.let { if (intent?.type == "application/pdf") openReader(it) }
    }

    override fun onResume() {
        super.onResume()
        val currentTheme = ThemeManager.current(this)
        if (displayedTheme != null && displayedTheme != currentTheme) {
            recreate()
        }
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(20))
            setBackgroundColor(ThemeManager.background(context))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(dp(22), dp(28), dp(22), dp(20) + navigation.bottom)
            insets
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "PDF Ultime"
            textSize = 30f
            setTextColor(ThemeManager.text(context))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Vos documents. Votre téléphone. Votre liberté."
            textSize = 16f
            setTextColor(ThemeManager.text(context))
            alpha = .72f
            setPadding(0, dp(4), 0, dp(12))
        })

        val open = action("Ouvrir un PDF", "Lire, parcourir et zoomer") { choosePdf() }
        root.addView(open)
        val merge = action("Fusionner des PDF", "Réunir plusieurs documents en un seul") { chooseMany() }
        root.addView(merge)
        val tools = MaterialCardView(this).apply { radius = 28f; setCardBackgroundColor(ThemeManager.surface(context)); setContentPadding(20, 18, 20, 18) }
        val toolsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolsBox.addView(TextView(this).apply { text = "Outils PDF"; textSize = 20f; setTextColor(ThemeManager.text(context)); setTypeface(typeface, 1) })
        toolsBox.addView(TextView(this).apply { text = "Des outils simples pour agir sur vos fichiers."; setTextColor(ThemeManager.text(context)); alpha = .7f; setPadding(0, 4, 0, 14) })
        toolsBox.addView(toolButton("Supprimer des pages") { choosePdfForDeletion() })
        toolsBox.addView(toolButton("Signer un document") { choosePdfForSignature() })
        toolsBox.addView(toolButton("Remplir et annoter") { choosePdfForAnnotation() })
        toolsBox.addView(toolButton("Paramètres") { startActivity(Intent(this, SettingsActivity::class.java)) })
        toolsBox.addView(toolButton("À propos") { startActivity(Intent(this, AboutActivity::class.java)) })
        tools.addView(toolsBox)
        root.addView(tools, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(6))
        }
        footer.addView(ImageView(this).apply {
            setImageResource(R.drawable.zen_projects_footer)
            alpha = .9f
            contentDescription = "Zen Projects"
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        footer.addView(TextView(this).apply {
            text = "PDF Ultime  •  Version 1.1.6\nLes fichiers restent sur votre appareil."
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(context))
            alpha = .65f
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))
    }

    private fun action(label: String, sub: String, click: () -> Unit): View = MaterialCardView(this).apply {
        radius = dp(24).toFloat(); setCardBackgroundColor(ThemeManager.primary(context)); setContentPadding(dp(20), dp(16), dp(20), dp(16))
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(context).apply { text = label; textSize = 20f; setTextColor(ThemeManager.primaryText(context)); setTypeface(typeface, 1) })
        box.addView(TextView(context).apply { text = sub; textSize = 14f; setTextColor(ThemeManager.primaryText(context)); alpha = .88f; setPadding(0, dp(3), 0, 0) })
        addView(box); setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun toolButton(text: String, click: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        setTextColor(ThemeManager.primaryText(context))
        backgroundTintList = ColorStateList.valueOf(ThemeManager.primary(context))
        cornerRadius = dp(24)
        setOnClickListener { click() }
        isAllCaps = false
    }
    private fun choosePdf() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/pdf"; addCategory(Intent.CATEGORY_OPENABLE) }, openPdf)
    private fun chooseMany() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/pdf"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); addCategory(Intent.CATEGORY_OPENABLE) }, mergePdfs)
    private fun choosePdfForDeletion() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/pdf"; addCategory(Intent.CATEGORY_OPENABLE) }, deletePages)
    private fun choosePdfForSignature() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/pdf"; addCategory(Intent.CATEGORY_OPENABLE) }, signaturePdf)
    private fun choosePdfForAnnotation() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/pdf"; addCategory(Intent.CATEGORY_OPENABLE) }, annotatePdf)
    private fun openReader(uri: Uri) { startActivity(Intent(this, PdfReaderActivity::class.java).setData(uri)) }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == openPdf) data.data?.let { openReader(it) }
        if (requestCode == deletePages) data.data?.let {
            startActivity(Intent(this, DeletePagesActivity::class.java).setData(it))
        }
        if (requestCode == signaturePdf) data.data?.let {
            startActivity(Intent(this, PdfReaderActivity::class.java).setData(it).putExtra("signature_mode", true))
        }
        if (requestCode == annotatePdf) data.data?.let {
            startActivity(Intent(this, PdfReaderActivity::class.java).setData(it).putExtra("text_mode", true))
        }
        if (requestCode == mergePdfs) {
            val uris = mutableListOf<Uri>(); data.data?.let(uris::add); data.clipData?.let { for (i in 0 until it.itemCount) uris.add(it.getItemAt(i).uri) }
            if (uris.size > 1) PdfOperations.merge(this, uris) { Toast.makeText(this, "PDF fusionné : ${it.name}", Toast.LENGTH_LONG).show() }
        }
    }
}
