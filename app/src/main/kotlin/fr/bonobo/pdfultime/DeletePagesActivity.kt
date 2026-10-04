package fr.bonobo.pdfultime

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.IOException
import kotlin.math.roundToInt
import java.io.File

class DeletePagesActivity : ComponentActivity() {
    private val savePdfRequest = 71
    private lateinit var renderer: PdfRenderer
    private lateinit var descriptor: ParcelFileDescriptor
    private val selectedPages = mutableSetOf<Int>()
    private lateinit var deleteButton: MaterialButton
    private var pendingOutput: File? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        try {
            descriptor = contentResolver.openFileDescriptor(uri, "r")!!
            renderer = PdfRenderer(descriptor)
        } catch (_: IOException) {
            Toast.makeText(this, "Impossible d’ouvrir ce PDF", Toast.LENGTH_LONG).show()
            return finish()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 247, 251))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(12), dp(4))
        }
        header.addView(Button(this).apply {
            text = "‹"
            textSize = 28f
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        header.addView(TextView(this).apply {
            text = "Supprimer des pages"
            textSize = 20f
            setTypeface(typeface, 1)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Touchez les pages à supprimer. L’original sera conservé."
            setPadding(dp(20), 0, dp(20), dp(12))
        })

        val scroll = ScrollView(this)
        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        for (index in 0 until renderer.pageCount) pages.addView(pageRow(index))
        scroll.addView(pages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        deleteButton = MaterialButton(this).apply {
            text = "Supprimer 0 page"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { confirmDeletion() }
        }
        root.addView(deleteButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), dp(8), dp(16), dp(8))
        })
        setContentView(root)
    }

    private fun pageRow(index: Int): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            setCardBackgroundColor(Color.WHITE)
            setContentPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val page = renderer.openPage(index)
        val width = dp(110)
        val height = (width * page.height.toFloat() / page.width).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        row.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            setBackgroundColor(Color.WHITE)
        }, LinearLayout.LayoutParams(width, height))
        row.addView(TextView(this).apply {
            text = "Page ${index + 1}"
            textSize = 17f
            setPadding(dp(18), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        card.setOnClickListener {
            if (!selectedPages.add(index)) selectedPages.remove(index)
            card.setCardBackgroundColor(if (selectedPages.contains(index)) 0xFFE9E7FF.toInt() else Color.WHITE)
            deleteButton.text = "Supprimer ${selectedPages.size} page${if (selectedPages.size > 1) "s" else ""}"
            deleteButton.isEnabled = selectedPages.isNotEmpty() && selectedPages.size < renderer.pageCount
        }
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        return card
    }

    private fun confirmDeletion() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer les pages ?")
            .setMessage("Le document original ne sera pas modifié.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                deleteButton.isEnabled = false
                PdfOperations.deletePages(this, intent.data!!, selectedPages, {
                    pendingOutput = it
                    val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "application/pdf"
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, "PDF_Ultime_sans_pages.pdf")
                    }
                    startActivityForResult(saveIntent, savePdfRequest)
                }, {
                    deleteButton.isEnabled = true
                    Toast.makeText(this, "La suppression a échoué", Toast.LENGTH_LONG).show()
                })
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != savePdfRequest) return
        val source = pendingOutput
        val destination = data?.data
        if (resultCode != RESULT_OK || source == null || destination == null) {
            deleteButton.isEnabled = true
            return
        }
        try {
            contentResolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Impossible d’enregistrer le fichier")
            source.delete()
            Toast.makeText(this, "PDF enregistré", Toast.LENGTH_LONG).show()
            finish()
        } catch (_: Exception) {
            deleteButton.isEnabled = true
            Toast.makeText(this, "Impossible d’enregistrer le PDF", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (::renderer.isInitialized) renderer.close()
        if (::descriptor.isInitialized) descriptor.close()
        super.onDestroy()
    }
}
