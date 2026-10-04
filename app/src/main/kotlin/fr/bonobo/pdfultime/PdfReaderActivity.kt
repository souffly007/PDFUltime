package fr.bonobo.pdfultime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import java.io.File

class PdfReaderActivity : ComponentActivity() {
    private lateinit var renderer: PdfRenderer
    private lateinit var descriptor: ParcelFileDescriptor
    private lateinit var pageView: PdfPageView
    private var page = 0
    private var zoom = 1f
    private var signatureMode = false
    private var textMode = false
    private lateinit var pdfUri: Uri
    private var pendingSignedPdf: File? = null
    private val saveSignedPdfRequest = 72
    private var pendingTextPdf: File? = null
    private val saveTextPdfRequest = 73

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        pdfUri = uri
        signatureMode = intent.getBooleanExtra("signature_mode", false)
        textMode = intent.getBooleanExtra("text_mode", false)

        try {
            descriptor = contentResolver.openFileDescriptor(uri, "r")!!
            renderer = PdfRenderer(descriptor)
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible d’ouvrir ce PDF", Toast.LENGTH_LONG).show()
            return finish()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(32, 33, 36))
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val close = Button(this).apply {
            text = "‹"
            textSize = 28f
            setOnClickListener { finish() }
        }
        val info = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val sign = Button(this).apply {
            text = "✎"
            textSize = 18f
            setOnClickListener {
                signatureMode = !signatureMode
                Toast.makeText(
                    this@PdfReaderActivity,
                    if (signatureMode) "Touchez l’endroit où placer la signature" else "Mode signature désactivé",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val textTool = Button(this).apply {
            text = "T"
            textSize = 18f
            setOnClickListener {
                textMode = !textMode
                signatureMode = false
                Toast.makeText(
                    this@PdfReaderActivity,
                    if (textMode) "Touchez l’endroit où ajouter le texte" else "Mode texte désactivé",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val minus = Button(this).apply {
            text = "−"
            textSize = 20f
            setOnClickListener {
                zoom = (zoom - .15f).coerceAtLeast(.55f)
                if (zoom <= 1f) pageView.resetPan()
                pageView.invalidate()
            }
        }
        val plus = Button(this).apply {
            text = "+"
            textSize = 20f
            setOnClickListener {
                zoom = (zoom + .15f).coerceAtMost(3f)
                pageView.invalidate()
            }
        }

        bar.addView(close, LinearLayout.LayoutParams(dp(56), dp(56)))
        bar.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(sign, LinearLayout.LayoutParams(dp(52), dp(56)))
        bar.addView(textTool, LinearLayout.LayoutParams(dp(52), dp(56)))
        bar.addView(minus, LinearLayout.LayoutParams(dp(52), dp(56)))
        bar.addView(plus, LinearLayout.LayoutParams(dp(52), dp(56)))
        root.addView(bar)

        pageView = PdfPageView()
        root.addView(pageView, LinearLayout.LayoutParams(-1, 0, 1f))

        fun updatePage() {
            info.text = "${page + 1} / ${renderer.pageCount}"
            pageView.invalidate()
        }

        val pageNavigation = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val previous = Button(this).apply {
            text = "‹"
            textSize = 30f
            contentDescription = "Page précédente"
            setOnClickListener {
                if (zoom <= 1f) {
                    page = (page - 1).coerceAtLeast(0)
                    pageView.resetPan()
                    updatePage()
                }
            }
        }
        val pageLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val next = Button(this).apply {
            text = "›"
            textSize = 30f
            contentDescription = "Page suivante"
            setOnClickListener {
                if (zoom <= 1f) {
                    page = (page + 1).coerceAtMost(renderer.pageCount - 1)
                    pageView.resetPan()
                    updatePage()
                }
            }
        }
        pageNavigation.addView(previous, LinearLayout.LayoutParams(dp(64), dp(56)))
        pageNavigation.addView(pageLabel, LinearLayout.LayoutParams(dp(120), dp(56)))
        pageNavigation.addView(next, LinearLayout.LayoutParams(dp(64), dp(56)))
        root.addView(pageNavigation, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        pageView.onPageChanged = {
            page = it.coerceIn(0, renderer.pageCount - 1)
            zoom = 1f
            pageView.resetPan()
            updatePage()
        }
        updatePage()
        if (signatureMode) {
            Toast.makeText(this, "Touchez l’endroit où placer la signature", Toast.LENGTH_LONG).show()
        }
        if (textMode) {
            Toast.makeText(this, "Touchez l’endroit où ajouter le texte", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSignatureDialog(pdfX: Float, pdfY: Float) {
        val pad = SignaturePadView()
        val container = LinearLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(pad, LinearLayout.LayoutParams(-1, dp(220)))
        }
        AlertDialog.Builder(this)
            .setTitle("Dessiner votre signature")
            .setMessage("Tracez votre signature puis appuyez sur Appliquer.")
            .setView(container)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Appliquer") { _, _ ->
                if (!pad.hasInk()) {
                    Toast.makeText(this, "Dessinez d’abord votre signature", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                PdfOperations.applySignature(
                    this,
                    pdfUri,
                    page,
                    pdfX,
                    pdfY,
                    pad.toBitmap(),
                    done = { file ->
                        pendingSignedPdf = file
                        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/pdf"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "PDF_Ultime_signe.pdf")
                        }
                        startActivityForResult(saveIntent, saveSignedPdfRequest)
                    },
                    failed = {
                        Toast.makeText(this, "Impossible d’appliquer la signature", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .show()
    }

    private fun showTextDialog(pdfX: Float, pdfY: Float) {
        val input = EditText(this).apply {
            hint = "Texte à ajouter"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("Ajouter du texte")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Appliquer") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                PdfOperations.addText(
                    this,
                    pdfUri,
                    page,
                    pdfX,
                    pdfY,
                    text,
                    done = { file ->
                        pendingTextPdf = file
                        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/pdf"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "PDF_Ultime_annote.pdf")
                        }
                        startActivityForResult(saveIntent, saveTextPdfRequest)
                    },
                    failed = {
                        Toast.makeText(this, "Impossible d’ajouter le texte", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != saveSignedPdfRequest && requestCode != saveTextPdfRequest) return
        val source = if (requestCode == saveSignedPdfRequest) pendingSignedPdf else pendingTextPdf
        val destination = data?.data
        if (resultCode != RESULT_OK || source == null || destination == null) return
        try {
            contentResolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Impossible d’enregistrer le PDF")
            source.delete()
            Toast.makeText(
                this,
                if (requestCode == saveSignedPdfRequest) "PDF signé enregistré" else "PDF annoté enregistré",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible d’enregistrer le PDF signé", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (::renderer.isInitialized) renderer.close()
        if (::descriptor.isInitialized) descriptor.close()
        super.onDestroy()
    }

    private inner class PdfPageView : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var offsetX = 0f
        private var offsetY = 0f
        private var lastScale = 1f
        private var lastLeft = 0f
        private var lastTop = 0f
        private var lastBitmapWidth = 0
        private var lastBitmapHeight = 0
        private var bitmap: Bitmap? = null
        var onPageChanged: ((Int) -> Unit)? = null

        fun resetPan() {
            offsetX = 0f
            offsetY = 0f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return

            val pdfPage = renderer.openPage(page)
            val availableWidth = (width - dp(24)).coerceAtLeast(1)
            val availableHeight = (height - dp(24)).coerceAtLeast(1)
            val fitScale = min(
                availableWidth.toFloat() / pdfPage.width,
                availableHeight.toFloat() / pdfPage.height
            )
            val actualScale = fitScale * zoom
            val bitmapWidth = (pdfPage.width * actualScale).roundToInt().coerceAtLeast(1)
            val bitmapHeight = (pdfPage.height * actualScale).roundToInt().coerceAtLeast(1)

            if (bitmap?.width != bitmapWidth || bitmap?.height != bitmapHeight) {
                bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            }

            bitmap!!.eraseColor(Color.WHITE)
            pdfPage.render(
                bitmap!!,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            pdfPage.close()

            val left = (width - bitmapWidth) / 2f
            val top = (height - bitmapHeight) / 2f
            lastScale = actualScale
            lastLeft = left
            lastTop = top
            lastBitmapWidth = bitmapWidth
            lastBitmapHeight = bitmapHeight
            val maxOffsetX = ((bitmapWidth - width) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((bitmapHeight - height) / 2f).coerceAtLeast(0f)
            offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
            offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
            canvas.drawBitmap(bitmap!!, left + offsetX, top + offsetY, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    lastX = event.x
                    lastY = event.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (zoom > 1f) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        offsetX += dx
                        offsetY += dy
                        moved = moved || abs(event.x - downX) > dp(4) || abs(event.y - downY) > dp(4)
                        lastX = event.x
                        lastY = event.y
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val delta = event.x - downX
                    if (textMode && zoom <= 1f && !moved) {
                        val point = pdfPoint(event.x, event.y)
                        if (point != null) {
                            textMode = false
                            showTextDialog(point.first, point.second)
                        }
                        return true
                    }
                    if (signatureMode && zoom <= 1f && !moved) {
                        val point = pdfPoint(event.x, event.y)
                        if (point != null) {
                            signatureMode = false
                            showSignatureDialog(point.first, point.second)
                        }
                        return true
                    }
                    if (zoom <= 1f && !moved && abs(delta) > dp(80)) {
                        onPageChanged?.invoke(if (delta < 0) page + 1 else page - 1)
                    }
                    return true
                }
            }
            return true
        }

        private fun pdfPoint(x: Float, y: Float): Pair<Float, Float>? {
            if (lastScale <= 0f || x < lastLeft || x > lastLeft + lastBitmapWidth || y < lastTop || y > lastTop + lastBitmapHeight) return null
            val xFromLeft = (x - lastLeft - offsetX) / lastScale
            val yFromTop = (y - lastTop - offsetY) / lastScale
            val page = renderer.openPage(this@PdfReaderActivity.page)
            val point = xFromLeft to (page.height - yFromTop)
            page.close()
            return point
        }
    }

    private inner class SignaturePadView : View(this) {
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = dp(4).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private var ink = false

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)
            canvas.drawPath(path, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> path.moveTo(event.x, event.y)
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    path.lineTo(event.x, event.y)
                    ink = true
                }
            }
            invalidate()
            return true
        }

        fun hasInk() = ink

        fun toBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawPath(path, paint)
            return bitmap
        }
    }
}
