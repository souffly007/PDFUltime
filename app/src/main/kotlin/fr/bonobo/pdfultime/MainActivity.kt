package fr.bonobo.pdfultime

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
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
    private val saveMergedPdf = 46
    private val pickPdfForTool = 47
    private val pickImages = 48
    private var displayedTheme: String? = null
    private var pendingMergedPdf: File? = null
    private var pendingTool: String? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        pendingTool = savedInstanceState?.getString("pending_tool")
        displayedTheme = ThemeManager.current(this)
        buildHome()
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pending_tool", pendingTool)
    }

    /** « Ouvrir avec » (VIEW) et menu Partager (SEND) sur un PDF. */
    private fun handleIncomingIntent(incoming: Intent?) {
        incoming ?: return
        val uri: Uri? = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        if (uri != null) openReader(uri)
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
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, bars.bottom)
            insets
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
            text = "Version ${versionName()}"
            textSize = 14f
            setTextColor(ThemeManager.text(context))
            alpha = .65f
            setPadding(0, dp(2), 0, 0)
        })
        root.addView(TextView(this).apply {
            text = "Vos documents. Votre téléphone. Votre liberté."
            textSize = 16f
            setTextColor(ThemeManager.text(context))
            alpha = .72f
            setPadding(0, dp(4), 0, dp(12))
        })

        root.addView(action("Ouvrir un PDF", "Lire, parcourir et zoomer") { choosePdf() })
        root.addView(action("Fusionner des PDF", "Réunir plusieurs documents en un seul") { chooseMany() })

        root.addView(category(
            "Organiser",
            "Réordonner, extraire, pivoter ou supprimer des pages.",
            "Organiser les pages" to { chooseForTool("organize") },
            "Extraire des pages" to { chooseForTool(PdfToolActivity.EXTRACT) },
            "Supprimer des pages" to { choosePdfForDeletion() }
        ))
        root.addView(category(
            "Éditer",
            "Signer, annoter, marquer et nettoyer vos documents.",
            "Signer un document" to { choosePdfForSignature() },
            "Remplir et annoter" to { choosePdfForAnnotation() },
            "Filigrane et numérotation" to { chooseForTool(PdfToolActivity.WATERMARK) },
            "Métadonnées" to { chooseForTool(PdfToolActivity.METADATA) }
        ))
        root.addView(category(
            "Convertir",
            "Passer d’images à PDF et inversement.",
            "Images en PDF" to { chooseImages() },
            "PDF en images" to { chooseForTool(PdfToolActivity.TO_IMAGES) }
        ))
        root.addView(category(
            "Optimiser et protéger",
            "Alléger un fichier ou le protéger par mot de passe.",
            "Compresser" to { chooseForTool(PdfToolActivity.COMPRESS) },
            "Protéger par mot de passe" to { chooseForTool(PdfToolActivity.PROTECT) },
            "Retirer la protection" to { chooseForTool(PdfToolActivity.UNLOCK) }
        ))
        root.addView(category(
            "Application",
            "Thème et informations.",
            "Paramètres" to { startActivity(Intent(this, SettingsActivity::class.java)) },
            "À propos" to { startActivity(Intent(this, AboutActivity::class.java)) }
        ))

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
            text = "Les fichiers restent sur votre appareil."
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(context))
            alpha = .65f
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply {
            // Keep the privacy message and logo well above Android's navigation area.
            bottomMargin = dp(120)
        })
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun action(label: String, sub: String, click: () -> Unit): View = MaterialCardView(this).apply {
        radius = dp(24).toFloat(); setCardBackgroundColor(ThemeManager.primary(context)); setContentPadding(dp(20), dp(16), dp(20), dp(16))
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(context).apply { text = label; textSize = 20f; setTextColor(ThemeManager.primaryText(context)); setTypeface(typeface, 1) })
        box.addView(TextView(context).apply { text = sub; textSize = 14f; setTextColor(ThemeManager.primaryText(context)); alpha = .88f; setPadding(0, dp(3), 0, 0) })
        addView(box); setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun category(title: String, subtitle: String, vararg entries: Pair<String, () -> Unit>): View {
        val card = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            setCardBackgroundColor(ThemeManager.surface(context))
            setContentPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = title; textSize = 20f; setTextColor(ThemeManager.text(context)); setTypeface(typeface, 1) })
        box.addView(TextView(this).apply { text = subtitle; setTextColor(ThemeManager.text(context)); alpha = .7f; setPadding(0, dp(4), 0, dp(14)) })
        entries.forEach { (label, click) -> box.addView(toolButton(label, click)) }
        card.addView(box)
        return card
    }

    private fun toolButton(text: String, click: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        setTextColor(ThemeManager.primaryText(context))
        backgroundTintList = ColorStateList.valueOf(ThemeManager.primary(context))
        cornerRadius = dp(24)
        setOnClickListener { click() }
        isAllCaps = false
    }

    private fun pdfPicker(multiple: Boolean = false) = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "application/pdf"
        addCategory(Intent.CATEGORY_OPENABLE)
        if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    private fun choosePdf() = startActivityForResult(pdfPicker(), openPdf)
    private fun chooseMany() = startActivityForResult(pdfPicker(multiple = true), mergePdfs)
    private fun choosePdfForDeletion() = startActivityForResult(pdfPicker(), deletePages)
    private fun choosePdfForSignature() = startActivityForResult(pdfPicker(), signaturePdf)
    private fun choosePdfForAnnotation() = startActivityForResult(pdfPicker(), annotatePdf)

    private fun chooseForTool(tool: String) {
        pendingTool = tool
        startActivityForResult(pdfPicker(), pickPdfForTool)
    }

    private fun chooseImages() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }, pickImages)

    private fun openReader(uri: Uri) { startActivity(Intent(this, PdfReaderActivity::class.java).setData(uri)) }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == saveMergedPdf) {
            savePendingPdf(pendingMergedPdf, if (resultCode == RESULT_OK) data?.data else null, "PDF fusionné enregistré")
            return
        }
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
        if (requestCode == pickPdfForTool) data.data?.let { uri ->
            val tool = pendingTool
            pendingTool = null
            when (tool) {
                null -> {}
                "organize" -> startActivity(Intent(this, OrganizeActivity::class.java).setData(uri))
                else -> startActivity(
                    Intent(this, PdfToolActivity::class.java)
                        .setData(uri)
                        .putExtra(PdfToolActivity.EXTRA_TOOL, tool)
                )
            }
        }
        if (requestCode == pickImages) {
            val clip: ClipData? = data.clipData ?: data.data?.let { ClipData.newRawUri("image", it) }
            if (clip != null && clip.itemCount > 0) {
                startActivity(
                    Intent(this, PdfToolActivity::class.java)
                        .putExtra(PdfToolActivity.EXTRA_TOOL, PdfToolActivity.IMAGES_TO_PDF)
                        .apply { clipData = clip }
                )
            }
        }
        if (requestCode == mergePdfs) {
            val uris = mutableListOf<Uri>(); data.data?.let(uris::add); data.clipData?.let { for (i in 0 until it.itemCount) uris.add(it.getItemAt(i).uri) }
            if (uris.size < 2) {
                Toast.makeText(this, "Choisissez au moins deux PDF à fusionner", Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(this, "Fusion des PDF en cours…", Toast.LENGTH_SHORT).show()
            PdfOperations.merge(this, uris, done = { file ->
                pendingMergedPdf = file
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/pdf"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, "PDF_Ultime_fusion.pdf")
                }, saveMergedPdf)
            }, failed = {
                Toast.makeText(this, "La fusion a échoué. Vérifiez que les fichiers sont bien des PDF lisibles.", Toast.LENGTH_LONG).show()
            })
        }
    }

    private fun savePendingPdf(source: File?, destination: Uri?, successMessage: String) {
        if (source == null) return
        if (destination == null) {
            source.delete()
            pendingMergedPdf = null
            return
        }
        try {
            contentResolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Impossible d’enregistrer le PDF")
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible d’enregistrer le PDF", Toast.LENGTH_LONG).show()
        } finally {
            source.delete()
            pendingMergedPdf = null
        }
    }

    override fun onDestroy() {
        pendingMergedPdf?.delete()
        super.onDestroy()
    }
}
