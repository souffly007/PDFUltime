package fr.bonobo.pdfultime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Une page du document de travail : page d’origine + rotation à appliquer. */
data class PageEdit(val index: Int, var rotation: Int = 0, var selected: Boolean = false)

data class PdfMetadata(val title: String, val author: String, val subject: String, val keywords: String)

object PdfOperations {
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------------------------------------------------------------------------------------
    // Fonctions existantes (1.6.2), inchangées sauf addText
    // ---------------------------------------------------------------------------------------

    fun merge(context: Context, uris: List<Uri>, done: (File) -> Unit, failed: () -> Unit) {
        thread {
            val temporaryFiles = mutableListOf<File>()
            var output: File? = null
            try {
                PDFBoxResourceLoader.init(context)
                output = File(context.cacheDir, "PDF_Ultime_fusion_${System.currentTimeMillis()}.pdf")
                val merger = PDFMergerUtility()
                uris.forEach { uri ->
                    val temporaryFile = File.createTempFile("pdf_", ".pdf", context.cacheDir)
                    temporaryFiles += temporaryFile
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporaryFile.outputStream().use { outputStream ->
                            input.copyTo(outputStream)
                        }
                    } ?: error("Impossible de lire le PDF")
                    merger.addSource(temporaryFile)
                }
                merger.destinationFileName = output!!.absolutePath
                merger.mergeDocuments(null)
                Handler(Looper.getMainLooper()).post { done(output!!) }
            } catch (_: Exception) {
                output?.delete()
                Handler(Looper.getMainLooper()).post { failed() }
            } finally {
                temporaryFiles.forEach { it.delete() }
            }
        }
    }

    fun deletePages(
        context: Context,
        uri: Uri,
        pages: Set<Int>,
        done: (File) -> Unit,
        failed: () -> Unit
    ) {
        thread {
            var temporaryFile: File? = null
            var document: PDDocument? = null
            try {
                PDFBoxResourceLoader.init(context)
                temporaryFile = File.createTempFile("pdf_source_", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temporaryFile!!.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Impossible de lire le PDF")

                document = PDDocument.load(temporaryFile)
                pages.sortedDescending().forEach { index -> document!!.removePage(index) }
                val output = File(context.cacheDir, "PDF_Ultime_sans_pages_${System.currentTimeMillis()}.pdf")
                document!!.save(output)
                Handler(Looper.getMainLooper()).post { done(output) }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { failed() }
            } finally {
                document?.close()
                temporaryFile?.delete()
            }
        }
    }

    fun applySignature(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        pdfX: Float,
        pdfY: Float,
        signature: Bitmap,
        done: (File) -> Unit,
        failed: () -> Unit
    ) {
        thread {
            var temporaryFile: File? = null
            var document: PDDocument? = null
            try {
                PDFBoxResourceLoader.init(context)
                temporaryFile = File.createTempFile("pdf_signature_", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temporaryFile!!.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Impossible de lire le PDF")

                document = PDDocument.load(temporaryFile)
                val page = document!!.getPage(pageIndex)
                val image = LosslessFactory.createFromImage(document, signature)
                val pageWidth = page.mediaBox.width
                val pageHeight = page.mediaBox.height
                val imageWidth = pageWidth * 0.28f
                val imageHeight = imageWidth * signature.height.toFloat() / signature.width.toFloat()
                val imageX = (pdfX - imageWidth / 2f).coerceIn(0f, pageWidth - imageWidth)
                val imageY = (pdfY - imageHeight / 2f).coerceIn(0f, pageHeight - imageHeight)

                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { content ->
                    content.drawImage(image, imageX, imageY, imageWidth, imageHeight)
                }

                val output = File(context.cacheDir, "PDF_Ultime_signe_${System.currentTimeMillis()}.pdf")
                document!!.save(output)
                Handler(Looper.getMainLooper()).post { done(output) }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { failed() }
            } finally {
                document?.close()
                temporaryFile?.delete()
            }
        }
    }

    /**
     * 1.7.0 : le texte peut maintenant contenir plusieurs lignes (la version précédente échouait
     * dès qu’on saisissait un retour à la ligne) et les caractères non pris en charge par la
     * police Helvetica sont remplacés par « ? » au lieu de faire échouer l’opération.
     */
    fun addText(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        pdfX: Float,
        pdfY: Float,
        text: String,
        done: (File) -> Unit,
        failed: () -> Unit
    ) {
        thread {
            var temporaryFile: File? = null
            var document: PDDocument? = null
            try {
                PDFBoxResourceLoader.init(context)
                temporaryFile = File.createTempFile("pdf_text_", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temporaryFile!!.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Impossible de lire le PDF")

                document = PDDocument.load(temporaryFile)
                val page = document!!.getPage(pageIndex)
                val font = PDType1Font.HELVETICA
                val fontSize = 14f
                val lines = text.replace("\r", "").split("\n")
                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { content ->
                    content.beginText()
                    content.setFont(font, fontSize)
                    content.setLeading(fontSize * 1.25f)
                    content.newLineAtOffset(pdfX, pdfY)
                    lines.forEachIndexed { index, line ->
                        if (index > 0) content.newLine()
                        content.showText(safeText(font, line))
                    }
                    content.endText()
                }

                val output = File(context.cacheDir, "PDF_Ultime_annote_${System.currentTimeMillis()}.pdf")
                document!!.save(output)
                Handler(Looper.getMainLooper()).post { done(output) }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { failed() }
            } finally {
                document?.close()
                temporaryFile?.delete()
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Infrastructure commune aux nouveaux outils
    // ---------------------------------------------------------------------------------------

    private fun runAsync(work: () -> File, done: (File) -> Unit, failed: (String) -> Unit) {
        thread {
            try {
                val file = work()
                mainHandler.post { done(file) }
            } catch (t: Throwable) {
                mainHandler.post { failed(describe(t)) }
            }
        }
    }

    private fun describe(t: Throwable): String = when (t) {
        is InvalidPasswordException -> "Mot de passe incorrect."
        is OutOfMemoryError -> "Le document est trop volumineux pour la mémoire de cet appareil."
        is IllegalArgumentException -> t.message ?: "Paramètre invalide."
        else -> "L’opération a échoué. Vérifiez que le fichier est un PDF lisible."
    }

    private fun outputFile(context: Context, tag: String): File =
        File(context.cacheDir, "PDF_Ultime_${tag}_${System.currentTimeMillis()}.pdf")

    /** Copie le PDF en cache, l’ouvre avec PDFBox, exécute [block], puis ferme et nettoie tout. */
    private fun <T> useSource(context: Context, uri: Uri, password: String? = null, block: (PDDocument) -> T): T {
        PDFBoxResourceLoader.init(context)
        val temporary = File.createTempFile("pdf_src_", ".pdf", context.cacheDir)
        var document: PDDocument? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Impossible de lire le PDF")
            val loaded = if (password.isNullOrEmpty()) PDDocument.load(temporary) else PDDocument.load(temporary, password)
            document = loaded
            return block(loaded)
        } finally {
            document?.close()
            temporary.delete()
        }
    }

    private fun safeText(font: PDFont, text: String): String {
        val builder = StringBuilder()
        for (character in text) {
            if (character == '\n' || character == '\r') continue
            try {
                font.encode(character.toString())
                builder.append(character)
            } catch (_: Exception) {
                builder.append('?')
            }
        }
        return builder.toString()
    }

    /**
     * Interprète « 1-3,5,8- » (1-indexé). Une page peut être répétée ou placée dans le désordre :
     * « 3,1,1-2 » est valide.
     */
    fun parsePageSpec(spec: String, pageCount: Int): List<Int> {
        val tokens = spec.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        require(tokens.isNotEmpty()) { "Indiquez les pages à garder, par exemple 1-3,5." }
        val result = mutableListOf<Int>()
        for (token in tokens) {
            val parts = token.split('-')
            val bounds: Pair<Int, Int> = try {
                when (parts.size) {
                    1 -> parts[0].toInt().let { it to it }
                    2 -> parts[0].ifEmpty { "1" }.toInt() to parts[1].ifEmpty { pageCount.toString() }.toInt()
                    else -> throw NumberFormatException()
                }
            } catch (_: NumberFormatException) {
                throw IllegalArgumentException("Plage invalide : « $token ».")
            }
            val start = bounds.first
            val end = bounds.second
            require(start >= 1 && end <= pageCount && start <= end) {
                "Plage hors limites : « $token » (le document compte $pageCount pages)."
            }
            for (page in start..end) result.add(page - 1)
        }
        return result
    }

    // ---------------------------------------------------------------------------------------
    // Organiser : extraire, réordonner, pivoter, dupliquer
    // ---------------------------------------------------------------------------------------

    fun extractPages(context: Context, uri: Uri, spec: String, done: (File) -> Unit, failed: (String) -> Unit) {
        runAsync({
            useSource(context, uri) { source ->
                val indices = parsePageSpec(spec, source.numberOfPages)
                val output = outputFile(context, "extrait")
                PDDocument().use { target ->
                    indices.forEach { target.importPage(source.getPage(it)) }
                    target.save(output)
                }
                output
            }
        }, done, failed)
    }

    fun organize(context: Context, uri: Uri, edits: List<PageEdit>, done: (File) -> Unit, failed: (String) -> Unit) {
        runAsync({
            require(edits.isNotEmpty()) { "Le document doit garder au moins une page." }
            useSource(context, uri) { source ->
                val output = outputFile(context, "organise")
                PDDocument().use { target ->
                    edits.forEach { edit ->
                        val imported = target.importPage(source.getPage(edit.index))
                        imported.rotation = (((imported.rotation + edit.rotation) % 360) + 360) % 360
                    }
                    target.save(output)
                }
                output
            }
        }, done, failed)
    }

    // ---------------------------------------------------------------------------------------
    // Images -> PDF
    // ---------------------------------------------------------------------------------------

    fun imagesToPdf(context: Context, uris: List<Uri>, fitA4: Boolean, done: (File) -> Unit, failed: (String) -> Unit) {
        runAsync({
            PDFBoxResourceLoader.init(context)
            require(uris.isNotEmpty()) { "Aucune image sélectionnée." }
            val output = outputFile(context, "images")
            PDDocument().use { document ->
                for (uri in uris) {
                    val bitmap = decodeImage(context, uri, 3000)
                    try {
                        val a4Width = PDRectangle.A4.width
                        val a4Height = PDRectangle.A4.height
                        val landscape = bitmap.width > bitmap.height
                        val pageRect = when {
                            !fitA4 -> PDRectangle(bitmap.width * 0.48f, bitmap.height * 0.48f)
                            landscape -> PDRectangle(a4Height, a4Width)
                            else -> PDRectangle(a4Width, a4Height)
                        }
                        val margin = if (fitA4) 18f else 0f
                        val scale = min(
                            (pageRect.width - 2 * margin) / bitmap.width,
                            (pageRect.height - 2 * margin) / bitmap.height
                        )
                        val drawWidth = bitmap.width * scale
                        val drawHeight = bitmap.height * scale
                        val x = (pageRect.width - drawWidth) / 2f
                        val y = (pageRect.height - drawHeight) / 2f

                        val mime = context.contentResolver.getType(uri) ?: ""
                        val lossless = mime.contains("png") || mime.contains("webp") || mime.contains("gif")
                        val image = if (lossless) {
                            LosslessFactory.createFromImage(document, bitmap)
                        } else {
                            val flat = flattenOnWhite(bitmap)
                            val created = JPEGFactory.createFromImage(document, flat, 0.88f)
                            if (flat !== bitmap) flat.recycle()
                            created
                        }
                        val page = PDPage(pageRect)
                        document.addPage(page)
                        PDPageContentStream(document, page).use { content ->
                            content.drawImage(image, x, y, drawWidth, drawHeight)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                document.save(output)
            }
            output
        }, done, failed)
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return flat
    }

    private fun decodeImage(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Une des images est illisible." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap: Bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Une des images est illisible.")
        val longest = max(bitmap.width, bitmap.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        val rotation = exifRotation(context, uri)
        if (scale < 1f || rotation != 0) {
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation.toFloat())
            }
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (transformed !== bitmap) bitmap.recycle()
            bitmap = transformed
        }
        return bitmap
    }

    private fun exifRotation(context: Context, uri: Uri): Int {
        if (Build.VERSION.SDK_INT < 24) return 0
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // ---------------------------------------------------------------------------------------
    // PDF -> images (rendu par le moteur Android, enregistrement dans un dossier choisi)
    // ---------------------------------------------------------------------------------------

    fun exportImages(
        context: Context,
        uri: Uri,
        treeUri: Uri,
        baseName: String,
        png: Boolean,
        scale: Float,
        progress: (Int, Int) -> Unit,
        done: (Int) -> Unit,
        failed: (String) -> Unit
    ) {
        thread {
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Impossible de lire le PDF")
                renderer = PdfRenderer(descriptor)
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val total = renderer.pageCount
                val mime = if (png) "image/png" else "image/jpeg"
                val extension = if (png) "png" else "jpg"
                val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                for (index in 0 until total) {
                    val page = renderer.openPage(index)
                    try {
                        var width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        var height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val longest = max(width, height)
                        if (longest > 4096) {
                            val ratio = 4096f / longest
                            width = (width * ratio).roundToInt().coerceAtLeast(1)
                            height = (height * ratio).roundToInt().coerceAtLeast(1)
                        }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val name = String.format(Locale.ROOT, "%s_page_%03d.%s", baseName, index + 1, extension)
                        val target = DocumentsContract.createDocument(context.contentResolver, parent, mime, name)
                            ?: error("Impossible de créer le fichier image")
                        context.contentResolver.openOutputStream(target, "wt")?.use { output ->
                            bitmap.compress(format, 92, output)
                        } ?: error("Impossible d’écrire le fichier image")
                        bitmap.recycle()
                    } finally {
                        page.close()
                    }
                    mainHandler.post { progress(index + 1, total) }
                }
                mainHandler.post { done(total) }
            } catch (t: Throwable) {
                val message = when (t) {
                    is SecurityException -> "Ce PDF est protégé par mot de passe : retirez d’abord la protection."
                    is OutOfMemoryError -> "Mémoire insuffisante : choisissez une résolution plus basse."
                    else -> "L’export a échoué. Vérifiez le dossier choisi et le PDF."
                }
                mainHandler.post { failed(message) }
            } finally {
                try {
                    renderer?.close()
                } catch (_: Exception) {
                }
                try {
                    descriptor?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Compression (recompression JPEG des images internes)
    // ---------------------------------------------------------------------------------------

    fun compress(context: Context, uri: Uri, quality: Float, maxSide: Int, done: (File) -> Unit, failed: (String) -> Unit) {
        runAsync({
            useSource(context, uri) { document ->
                val replacements = HashMap<Any, PDImageXObject>()
                val visited = HashSet<Any>()
                for (page in document.pages) {
                    val resources = page.resources ?: continue
                    recompressImages(document, resources, quality, maxSide, replacements, visited)
                }
                val output = outputFile(context, "compresse")
                document.save(output)
                output
            }
        }, done, failed)
    }

    private fun recompressImages(
        document: PDDocument,
        resources: PDResources,
        quality: Float,
        maxSide: Int,
        replacements: MutableMap<Any, PDImageXObject>,
        visited: MutableSet<Any>
    ) {
        for (name in resources.xObjectNames.toList()) {
            val xobject = try {
                resources.getXObject(name)
            } catch (_: Exception) {
                null
            }
            if (xobject == null) continue
            if (xobject is PDImageXObject) {
                val key: Any = xobject.cosObject
                val existing = replacements[key]
                if (existing != null) {
                    resources.put(name, existing)
                    continue
                }
                if (!visited.add(key)) continue
                val replacement = recompressImage(document, xobject, quality, maxSide)
                if (replacement != null) {
                    replacements[key] = replacement
                    resources.put(name, replacement)
                }
            } else if (xobject is PDFormXObject) {
                val key: Any = xobject.cosObject
                if (!visited.add(key)) continue
                val inner = xobject.resources
                if (inner != null) recompressImages(document, inner, quality, maxSide, replacements, visited)
            }
        }
    }

    private fun recompressImage(document: PDDocument, image: PDImageXObject, quality: Float, maxSide: Int): PDImageXObject? {
        try {
            if (image.isStencil || image.softMask != null || image.mask != null) return null
            if (image.width.toLong() * image.height.toLong() < 40_000L) return null
            val originalLength = image.cosObject.length
            val source = image.image
            val longest = max(source.width, source.height)
            val prepared = if (longest > maxSide) {
                val ratio = maxSide.toFloat() / longest
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * ratio).roundToInt().coerceAtLeast(1),
                    (source.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }
            val replacement = JPEGFactory.createFromImage(document, prepared, quality)
            if (prepared !== source) prepared.recycle()
            source.recycle()
            val newLength = replacement.cosObject.length
            return if (originalLength > 0 && newLength in 1 until originalLength) replacement else null
        } catch (_: Exception) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Sécurité : chiffrer / déchiffrer
    // ---------------------------------------------------------------------------------------

    fun protect(
        context: Context,
        uri: Uri,
        password: String,
        allowPrint: Boolean,
        allowCopy: Boolean,
        allowModify: Boolean,
        done: (File) -> Unit,
        failed: (String) -> Unit
    ) {
        runAsync({
            require(password.isNotEmpty()) { "Choisissez un mot de passe." }
            useSource(context, uri) { document ->
                require(!document.isEncrypted) { "Ce document est déjà protégé : retirez d’abord sa protection." }
                val permission = AccessPermission()
                permission.setCanPrint(allowPrint)
                permission.setCanExtractContent(allowCopy)
                permission.setCanModify(allowModify)
                permission.setCanModifyAnnotations(allowModify)
                permission.setCanFillInForm(allowModify)
                permission.setCanAssembleDocument(allowModify)
                val policy = StandardProtectionPolicy(password, password, permission)
                policy.setEncryptionKeyLength(256)
                policy.setPreferAES(true)
                document.protect(policy)
                val output = outputFile(context, "protege")
                document.save(output)
                output
            }
        }, done, failed)
    }

    /** Produit une copie sans chiffrement. Sert à l’outil « Retirer la protection » et au lecteur. */
    fun unlock(context: Context, uri: Uri, password: String, done: (File) -> Unit, failed: (String) -> Unit) {
        runAsync({
            useSource(context, uri, password) { document ->
                document.setAllSecurityToBeRemoved(true)
                val output = outputFile(context, "deverrouille")
                document.save(output)
                output
            }
        }, done, failed)
    }

    // ---------------------------------------------------------------------------------------
    // Filigrane et numérotation
    // ---------------------------------------------------------------------------------------

    /** Matrice qui permet de dessiner « à l’endroit » sur une page dont /Rotate vaut 90, 180 ou 270. */
    private fun visibleMatrix(rotation: Int, boxWidth: Float, boxHeight: Float): PdfMatrix = when (rotation) {
        90 -> PdfMatrix(0f, 1f, -1f, 0f, boxWidth, 0f)
        180 -> PdfMatrix(-1f, 0f, 0f, -1f, boxWidth, boxHeight)
        270 -> PdfMatrix(0f, -1f, 1f, 0f, 0f, boxHeight)
        else -> PdfMatrix()
    }

    /**
     * @param watermark texte du filigrane (vide = aucun)
     * @param opacity opacité du filigrane, de 0 à 1
     * @param numberPosition 0 = bas centré, 1 = bas à droite, 2 = bas à gauche
     * @param numberWithTotal true = « 3 / 12 », false = « 3 »
     */
    fun decorate(
        context: Context,
        uri: Uri,
        watermark: String,
        opacity: Float,
        numbering: Boolean,
        numberPosition: Int,
        numberWithTotal: Boolean,
        done: (File) -> Unit,
        failed: (String) -> Unit
    ) {
        runAsync({
            useSource(context, uri) { document ->
                val total = document.numberOfPages
                val markFont = PDType1Font.HELVETICA_BOLD
                val numberFont = PDType1Font.HELVETICA
                val mark = safeText(markFont, watermark.trim())
                val state = PDExtendedGraphicsState()
                state.setNonStrokingAlphaConstant(opacity)
                state.setStrokingAlphaConstant(opacity)

                for (index in 0 until total) {
                    val page = document.getPage(index)
                    val box = page.cropBox
                    val rotation = ((page.rotation % 360) + 360) % 360
                    val swapped = rotation == 90 || rotation == 270
                    val visibleWidth = if (swapped) box.height else box.width
                    val visibleHeight = if (swapped) box.width else box.height

                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                        content.transform(PdfMatrix.getTranslateInstance(box.lowerLeftX, box.lowerLeftY))
                        content.transform(visibleMatrix(rotation, box.width, box.height))

                        if (mark.isNotEmpty()) {
                            val unit = markFont.getStringWidth(mark) / 1000f
                            val diagonal = sqrt(visibleWidth * visibleWidth + visibleHeight * visibleHeight)
                            val size = (0.62f * diagonal / max(unit, 0.01f)).coerceIn(12f, 140f)
                            val textWidth = unit * size
                            val angle = Math.toRadians(45.0)
                            val cos = Math.cos(angle).toFloat()
                            val sin = Math.sin(angle).toFloat()
                            val startX = visibleWidth / 2f - cos * textWidth / 2f + sin * size * 0.3f
                            val startY = visibleHeight / 2f - sin * textWidth / 2f - cos * size * 0.3f
                            content.saveGraphicsState()
                            content.setGraphicsStateParameters(state)
                            content.beginText()
                            content.setFont(markFont, size)
                            content.setNonStrokingColor(0.45f, 0.45f, 0.45f)
                            content.setTextMatrix(PdfMatrix.getRotateInstance(angle, startX, startY))
                            content.showText(mark)
                            content.endText()
                            content.restoreGraphicsState()
                        }

                        if (numbering) {
                            val label = if (numberWithTotal) "${index + 1} / $total" else "${index + 1}"
                            val size = 10f
                            val labelWidth = numberFont.getStringWidth(label) / 1000f * size
                            val x = when (numberPosition) {
                                1 -> visibleWidth - labelWidth - 28f
                                2 -> 28f
                                else -> (visibleWidth - labelWidth) / 2f
                            }
                            content.beginText()
                            content.setFont(numberFont, size)
                            content.setNonStrokingColor(0.15f, 0.15f, 0.15f)
                            content.newLineAtOffset(x, 20f)
                            content.showText(label)
                            content.endText()
                        }
                    }
                }
                val output = outputFile(context, "marque")
                document.save(output)
                output
            }
        }, done, failed)
    }

    // ---------------------------------------------------------------------------------------
    // Métadonnées
    // ---------------------------------------------------------------------------------------

    fun readMetadata(context: Context, uri: Uri, done: (PdfMetadata) -> Unit, failed: (String) -> Unit) {
        thread {
            try {
                val metadata = useSource(context, uri) { document ->
                    val info = document.documentInformation
                    PdfMetadata(
                        info.title.orEmpty(),
                        info.author.orEmpty(),
                        info.subject.orEmpty(),
                        info.keywords.orEmpty()
                    )
                }
                mainHandler.post { done(metadata) }
            } catch (t: Throwable) {
                mainHandler.post { failed(describe(t)) }
            }
        }
    }

    fun updateMetadata(
        context: Context,
        uri: Uri,
        metadata: PdfMetadata,
        clearAll: Boolean,
        done: (File) -> Unit,
        failed: (String) -> Unit
    ) {
        runAsync({
            useSource(context, uri) { document ->
                if (clearAll) {
                    document.documentInformation = PDDocumentInformation()
                    document.documentCatalog.metadata = null
                } else {
                    val info = document.documentInformation
                    info.title = metadata.title.trim().ifEmpty { null }
                    info.author = metadata.author.trim().ifEmpty { null }
                    info.subject = metadata.subject.trim().ifEmpty { null }
                    info.keywords = metadata.keywords.trim().ifEmpty { null }
                    info.modificationDate = Calendar.getInstance()
                }
                val output = outputFile(context, "meta")
                document.save(output)
                output
            }
        }, done, failed)
    }
}
