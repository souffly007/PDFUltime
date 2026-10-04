package fr.bonobo.pdfultime

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlin.concurrent.thread

object PdfOperations {
    fun merge(context: Context, uris: List<Uri>, done: (File) -> Unit) {
        thread {
            try {
                PDFBoxResourceLoader.init(context)
                val output = File(context.filesDir, "PDF_Ultime_${System.currentTimeMillis()}.pdf")
                val merger = PDFMergerUtility()
                uris.forEach { uri ->
                    val temporaryFile = File.createTempFile("pdf_", ".pdf", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporaryFile.outputStream().use { outputStream ->
                            input.copyTo(outputStream)
                        }
                    } ?: error("Impossible de lire le PDF")
                    merger.addSource(temporaryFile)
                    temporaryFile.deleteOnExit()
                }
                merger.destinationFileName = output.absolutePath
                merger.mergeDocuments(null)
                Handler(Looper.getMainLooper()).post { done(output) }
            } catch (_: Exception) {
                // Une erreur de fusion sera signalée dans une prochaine version.
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
                val output = File(context.filesDir, "PDF_Ultime_sans_pages_${System.currentTimeMillis()}.pdf")
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

                val output = File(context.filesDir, "PDF_Ultime_signe_${System.currentTimeMillis()}.pdf")
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
                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, 14f)
                    content.newLineAtOffset(pdfX, pdfY)
                    content.showText(text)
                    content.endText()
                }

                val output = File(context.filesDir, "PDF_Ultime_annote_${System.currentTimeMillis()}.pdf")
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
}
