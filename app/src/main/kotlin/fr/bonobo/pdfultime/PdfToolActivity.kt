package fr.bonobo.pdfultime

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * Écran générique « choisir un fichier, régler quelques options, appliquer, enregistrer ».
 * L’outil est choisi par l’extra [EXTRA_TOOL].
 */
class PdfToolActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TOOL = "tool"
        const val EXTRACT = "extract"
        const val COMPRESS = "compress"
        const val PROTECT = "protect"
        const val UNLOCK = "unlock"
        const val WATERMARK = "watermark"
        const val METADATA = "metadata"
        const val TO_IMAGES = "to_images"
        const val IMAGES_TO_PDF = "images_to_pdf"
        private const val REQUEST_SAVE = 81
        private const val REQUEST_TREE = 82
    }

    private lateinit var tool: String
    private var sources: List<Uri> = emptyList()
    private var pdfUri: Uri? = null
    private var pageCount = 0
    private var pending: File? = null
    private var onApply: () -> Unit = {}

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var applyButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        tool = intent.getStringExtra(EXTRA_TOOL) ?: return finish()

        val list = mutableListOf<Uri>()
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { list.add(it) }
        }
        if (list.isEmpty()) intent.data?.let { list.add(it) }
        if (list.isEmpty()) return finish()
        sources = list
        if (tool != IMAGES_TO_PDF) {
            pdfUri = list[0]
            pageCount = readPageCount(list[0])
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.background(this@PdfToolActivity))
        }
        Ui.installInsets(root)
        root.addView(Ui.header(this, titleOf(tool)))

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        buildSummary()
        content.addView(Ui.body(this, descriptionOf(tool)))

        applyButton = Ui.button(this, "Appliquer") { onApply() }
        status = Ui.body(this, "", .75f)
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }

        when (tool) {
            EXTRACT -> buildExtract()
            COMPRESS -> buildCompress()
            PROTECT -> buildProtect()
            UNLOCK -> buildUnlock()
            WATERMARK -> buildWatermark()
            METADATA -> buildMetadata()
            TO_IMAGES -> buildToImages()
            IMAGES_TO_PDF -> buildImagesToPdf()
            else -> return finish()
        }

        content.addView(status)
        content.addView(progress, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        content.addView(applyButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    // ------------------------------------------------------------------ présentation

    private fun titleOf(tool: String) = when (tool) {
        EXTRACT -> "Extraire des pages"
        COMPRESS -> "Compresser un PDF"
        PROTECT -> "Protéger par mot de passe"
        UNLOCK -> "Retirer la protection"
        WATERMARK -> "Filigrane et numérotation"
        METADATA -> "Métadonnées"
        TO_IMAGES -> "PDF en images"
        IMAGES_TO_PDF -> "Images en PDF"
        else -> "Outil PDF"
    }

    private fun descriptionOf(tool: String) = when (tool) {
        EXTRACT -> "Crée un nouveau PDF avec seulement les pages choisies. Vous pouvez aussi les réordonner ou les répéter : 3,1,1-2."
        COMPRESS -> "Réduit le poids des images contenues dans le PDF. Le texte reste net. Un PDF sans image ne sera presque pas allégé."
        PROTECT -> "Chiffrement AES 256 bits. Conservez bien ce mot de passe : il ne peut pas être récupéré."
        UNLOCK -> "Crée une copie sans mot de passe. Si le document demande un mot de passe pour s’ouvrir, saisissez-le ci-dessous."
        WATERMARK -> "Ajoute un texte en filigrane diagonal et/ou des numéros de page. Le document original n’est pas modifié."
        METADATA -> "Titre, auteur, sujet et mots-clés sont lisibles par quiconque reçoit le fichier. Modifiez-les ou effacez-les avant de partager."
        TO_IMAGES -> "Enregistre chaque page sous forme d’image dans le dossier de votre choix."
        IMAGES_TO_PDF -> "Assemble les images dans l’ordre de sélection, une image par page."
        else -> ""
    }

    private fun buildSummary() {
        val card = Ui.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (tool == IMAGES_TO_PDF) {
            box.addView(Ui.body(this, "${sources.size} image${if (sources.size > 1) "s" else ""} sélectionnée${if (sources.size > 1) "s" else ""}", 1f))
        } else {
            val uri = pdfUri!!
            box.addView(Ui.body(this, Ui.displayName(this, uri), 1f))
            val size = Ui.fileSize(this, uri)
            val pages = if (pageCount > 0) "$pageCount page${if (pageCount > 1) "s" else ""}" else null
            val details = listOfNotNull(if (size > 0) Ui.formatSize(size) else null, pages).joinToString(" · ")
            if (details.isNotEmpty()) box.addView(Ui.body(this, details, .65f))
        }
        card.addView(box)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun readPageCount(uri: Uri): Int {
        return try {
            val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: return 0
            try {
                val renderer = PdfRenderer(descriptor)
                val count = renderer.pageCount
                renderer.close()
                count
            } finally {
                descriptor.close()
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        applyButton.isEnabled = !busy
        applyButton.alpha = if (busy) .5f else 1f
        if (busy) status.text = "Traitement en cours…"
    }

    private fun fail(message: String) {
        setBusy(false)
        status.text = ""
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun suggestedName(suffix: String) = Ui.baseName(this, pdfUri!!) + suffix + ".pdf"

    private fun offerSave(file: File, name: String) {
        pending?.delete()
        pending = file
        status.text = "Choisissez où enregistrer le résultat."
        startActivityForResult(Ui.createPdfIntent(name), REQUEST_SAVE)
    }

    // ------------------------------------------------------------------ formulaires

    private fun buildExtract() {
        content.addView(Ui.heading(this, "Pages à garder"))
        val input = Ui.input(this, "Exemple : 1-3,5,8-")
        content.addView(input)
        if (pageCount > 0) content.addView(Ui.body(this, "Ce document compte $pageCount pages.", .6f))
        onApply = {
            setBusy(true)
            PdfOperations.extractPages(this, pdfUri!!, input.text.toString(),
                { offerSave(it, suggestedName("_extrait")) }, { fail(it) })
        }
    }

    private fun buildCompress() {
        content.addView(Ui.heading(this, "Niveau de compression"))
        val levels = Ui.radios(
            this,
            listOf(
                "Légère — qualité 80 %",
                "Équilibrée — qualité 60 %, images limitées à 1800 px",
                "Forte — qualité 40 %, images limitées à 1200 px"
            ),
            1
        )
        content.addView(levels)
        onApply = {
            val choice = Ui.selectedIndex(levels)
            val quality = floatArrayOf(.8f, .6f, .4f)[choice]
            val maxSide = intArrayOf(2600, 1800, 1200)[choice]
            setBusy(true)
            PdfOperations.compress(this, pdfUri!!, quality, maxSide, { file ->
                val before = Ui.fileSize(this, pdfUri!!)
                val after = file.length()
                val message = if (before > 0 && after < before) {
                    "Taille : ${Ui.formatSize(before)} → ${Ui.formatSize(after)} (−${100 - after * 100 / before} %)"
                } else {
                    "Le fichier obtenu (${Ui.formatSize(after)}) n’est pas plus léger que l’original (${Ui.formatSize(before)}). Il contient peut-être peu d’images."
                }
                AlertDialog.Builder(this)
                    .setTitle("Compression terminée")
                    .setMessage(message)
                    .setCancelable(false)
                    .setNegativeButton("Annuler") { _, _ ->
                        file.delete()
                        setBusy(false)
                        status.text = ""
                    }
                    .setPositiveButton("Enregistrer") { _, _ -> offerSave(file, suggestedName("_compresse")) }
                    .show()
            }, { fail(it) })
        }
    }

    private fun buildProtect() {
        content.addView(Ui.heading(this, "Mot de passe"))
        val first = Ui.input(this, "Mot de passe", password = true)
        val second = Ui.input(this, "Confirmer le mot de passe", password = true)
        content.addView(first)
        content.addView(second)
        content.addView(Ui.heading(this, "Autorisations"))
        val print = Ui.checkbox(this, "Autoriser l’impression", true)
        val copy = Ui.checkbox(this, "Autoriser la copie du texte", true)
        val modify = Ui.checkbox(this, "Autoriser la modification", false)
        content.addView(print)
        content.addView(copy)
        content.addView(modify)
        onApply = {
            val password = first.text.toString()
            when {
                password.isEmpty() -> Toast.makeText(this, "Choisissez un mot de passe", Toast.LENGTH_LONG).show()
                password != second.text.toString() -> Toast.makeText(this, "Les deux mots de passe sont différents", Toast.LENGTH_LONG).show()
                else -> {
                    setBusy(true)
                    PdfOperations.protect(this, pdfUri!!, password, print.isChecked, copy.isChecked, modify.isChecked,
                        { offerSave(it, suggestedName("_protege")) }, { fail(it) })
                }
            }
        }
    }

    private fun buildUnlock() {
        content.addView(Ui.heading(this, "Mot de passe du document"))
        val input = Ui.input(this, "Laisser vide s’il n’y en a pas", password = true)
        content.addView(input)
        onApply = {
            setBusy(true)
            PdfOperations.unlock(this, pdfUri!!, input.text.toString(),
                { offerSave(it, suggestedName("_deverrouille")) }, { fail(it) })
        }
    }

    private fun buildWatermark() {
        content.addView(Ui.heading(this, "Filigrane"))
        val text = Ui.input(this, "Texte (ex. CONFIDENTIEL) — vide = aucun")
        content.addView(text)
        content.addView(Ui.body(this, "Intensité du filigrane", .7f))
        val opacity = Ui.radios(this, listOf("Discret (20 %)", "Moyen (40 %)", "Marqué (60 %)"), 0)
        content.addView(opacity)

        content.addView(Ui.heading(this, "Numérotation"))
        val numbering = Ui.checkbox(this, "Numéroter les pages", false)
        content.addView(numbering)
        val position = Ui.radios(this, listOf("Bas centré", "Bas à droite", "Bas à gauche"), 0)
        content.addView(position)
        val withTotal = Ui.checkbox(this, "Afficher le total (3 / 12)", true)
        content.addView(withTotal)

        onApply = {
            if (text.text.toString().isBlank() && !numbering.isChecked) {
                Toast.makeText(this, "Saisissez un filigrane ou activez la numérotation", Toast.LENGTH_LONG).show()
            } else {
                setBusy(true)
                val alpha = floatArrayOf(.2f, .4f, .6f)[Ui.selectedIndex(opacity)]
                PdfOperations.decorate(
                    this, pdfUri!!, text.text.toString(), alpha,
                    numbering.isChecked, Ui.selectedIndex(position), withTotal.isChecked,
                    { offerSave(it, suggestedName("_marque")) }, { fail(it) }
                )
            }
        }
    }

    private fun buildMetadata() {
        content.addView(Ui.heading(this, "Propriétés du document"))
        val title: EditText = Ui.input(this, "Titre")
        val author: EditText = Ui.input(this, "Auteur")
        val subject: EditText = Ui.input(this, "Sujet")
        val keywords: EditText = Ui.input(this, "Mots-clés")
        listOf(title, author, subject, keywords).forEach { content.addView(it) }
        val clear = Ui.checkbox(this, "Tout effacer (propriétés et données XMP)", false)
        content.addView(clear)
        clear.setOnCheckedChangeListener { _, checked ->
            listOf(title, author, subject, keywords).forEach { it.isEnabled = !checked }
        }

        applyButton.isEnabled = false
        applyButton.alpha = .5f
        progress.visibility = View.VISIBLE
        status.text = "Lecture des propriétés…"
        PdfOperations.readMetadata(this, pdfUri!!, { metadata ->
            title.setText(metadata.title)
            author.setText(metadata.author)
            subject.setText(metadata.subject)
            keywords.setText(metadata.keywords)
            progress.visibility = View.GONE
            status.text = ""
            applyButton.isEnabled = true
            applyButton.alpha = 1f
        }, { message ->
            progress.visibility = View.GONE
            status.text = message
        })

        onApply = {
            setBusy(true)
            PdfOperations.updateMetadata(
                this, pdfUri!!,
                PdfMetadata(title.text.toString(), author.text.toString(), subject.text.toString(), keywords.text.toString()),
                clear.isChecked,
                { offerSave(it, suggestedName("_meta")) }, { fail(it) }
            )
        }
    }

    private fun buildToImages() {
        content.addView(Ui.heading(this, "Format"))
        val format = Ui.radios(this, listOf("PNG (sans perte)", "JPEG (plus léger)"), 0)
        content.addView(format)
        content.addView(Ui.heading(this, "Résolution"))
        val quality = Ui.radios(this, listOf("Normale (≈ 110 dpi)", "Haute (≈ 180 dpi)"), 0)
        content.addView(quality)
        applyButton.text = "Choisir le dossier et exporter"
        onApply = {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_TREE)
        }
        formatChoice = format
        qualityChoice = quality
    }

    private var formatChoice: android.widget.RadioGroup? = null
    private var qualityChoice: android.widget.RadioGroup? = null

    private fun buildImagesToPdf() {
        content.addView(Ui.heading(this, "Taille des pages"))
        val mode = Ui.radios(this, listOf("A4, image ajustée avec marges", "Même taille que chaque image"), 0)
        content.addView(mode)
        onApply = {
            setBusy(true)
            PdfOperations.imagesToPdf(this, sources, Ui.selectedIndex(mode) == 0,
                { file ->
                    pending?.delete()
                    pending = file
                    status.text = "Choisissez où enregistrer le résultat."
                    startActivityForResult(Ui.createPdfIntent("PDF_Ultime_images.pdf"), REQUEST_SAVE)
                }, { fail(it) })
        }
    }

    private fun runExport(tree: Uri) {
        val png = Ui.selectedIndex(formatChoice ?: return) == 0
        val scale = if (Ui.selectedIndex(qualityChoice ?: return) == 0) 1.5f else 2.5f
        setBusy(true)
        PdfOperations.exportImages(
            this, pdfUri!!, tree, Ui.baseName(this, pdfUri!!), png, scale,
            progress = { current, total -> status.text = "Page $current / $total" },
            done = { total ->
                Toast.makeText(this, "$total image${if (total > 1) "s" else ""} enregistrée${if (total > 1) "s" else ""}", Toast.LENGTH_LONG).show()
                finish()
            },
            failed = { fail(it) }
        )
    }

    // ------------------------------------------------------------------ résultats

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SAVE -> {
                val source = pending
                pending = null
                if (source == null) return
                val destination = if (resultCode == RESULT_OK) data?.data else null
                if (destination == null) {
                    source.delete()
                    setBusy(false)
                    status.text = ""
                    return
                }
                val saved = Ui.saveFile(this, source, destination)
                source.delete()
                if (saved) {
                    Toast.makeText(this, "PDF enregistré", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    fail("Impossible d’enregistrer le PDF")
                }
            }
            REQUEST_TREE -> {
                val tree = if (resultCode == RESULT_OK) data?.data else null
                if (tree != null) runExport(tree)
            }
        }
    }

    override fun onDestroy() {
        pending?.delete()
        super.onDestroy()
    }
}
