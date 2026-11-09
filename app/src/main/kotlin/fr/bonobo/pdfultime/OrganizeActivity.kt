package fr.bonobo.pdfultime

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Organiser les pages : toucher pour sélectionner, appui long puis glisser pour réordonner,
 * pivoter / dupliquer / supprimer la sélection, puis enregistrer un nouveau PDF.
 */
class OrganizeActivity : ComponentActivity() {
    private val saveRequest = 91
    private lateinit var renderer: PdfRenderer
    private lateinit var descriptor: ParcelFileDescriptor
    private val items = mutableListOf<PageEdit>()
    private lateinit var adapter: PageAdapter
    private lateinit var info: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var selectAllButton: MaterialButton
    private val actionButtons = mutableListOf<MaterialButton>()
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var pending: File? = null

    private val cache = object : LruCache<Int, Bitmap>(24 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        try {
            descriptor = contentResolver.openFileDescriptor(uri, "r")!!
            renderer = PdfRenderer(descriptor)
        } catch (_: SecurityException) {
            Toast.makeText(this, "Ce PDF est protégé : utilisez d’abord « Retirer la protection ».", Toast.LENGTH_LONG).show()
            return finish()
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible d’ouvrir ce PDF", Toast.LENGTH_LONG).show()
            return finish()
        }
        for (i in 0 until renderer.pageCount) items.add(PageEdit(i))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.background(this@OrganizeActivity))
        }
        Ui.installInsets(root)
        root.addView(Ui.header(this, "Organiser les pages"))
        info = Ui.body(this, "", .75f).apply { setPadding(dp(20), 0, dp(20), dp(6)) }
        root.addView(info)

        adapter = PageAdapter()
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@OrganizeActivity, 2)
            clipToPadding = false
            setPadding(dp(11), dp(4), dp(11), dp(8))
        }
        recycler.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val moved = items.removeAt(from)
                items.add(to, moved)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Rafraîchit les numéros de page affichés après un déplacement.
                recyclerView.post { adapter.notifyDataSetChanged() }
            }
        }).attachToRecyclerView(recycler)
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            setPadding(dp(10), dp(4), dp(10), 0)
            gravity = Gravity.CENTER
        }
        selectAllButton = smallButton("Tout") { toggleSelectAll() }
        actions.addView(selectAllButton, weight())
        actions.addView(smallButton("↺") { rotateSelected(-90) }.also { actionButtons.add(it) }, weight())
        actions.addView(smallButton("↻") { rotateSelected(90) }.also { actionButtons.add(it) }, weight())
        actions.addView(smallButton("Dupliquer") { duplicateSelected() }.also { actionButtons.add(it) }, weight())
        actions.addView(smallButton("Supprimer") { deleteSelected() }.also { actionButtons.add(it) }, weight())
        root.addView(actions)

        saveButton = Ui.button(this, "Enregistrer le nouveau PDF") { save() }
        root.addView(saveButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(6), dp(16), dp(8)) })
        setContentView(root)
        refreshState()
    }

    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }

    private fun smallButton(label: String, click: () -> Unit): MaterialButton = Ui.button(this, label, click).apply {
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(2), 0, dp(2), 0)
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(16)
    }

    // ------------------------------------------------------------------ actions

    private fun selectedItems() = items.filter { it.selected }

    private fun refreshState() {
        val selected = selectedItems().size
        info.text = "${items.size} page${if (items.size > 1) "s" else ""} · $selected sélectionnée${if (selected > 1) "s" else ""}  —  appui long puis glisser pour déplacer"
        actionButtons.forEach {
            it.isEnabled = selected > 0
            it.alpha = if (selected > 0) 1f else .45f
        }
        selectAllButton.text = if (selected == items.size) "Aucune" else "Tout"
    }

    private fun toggleSelectAll() {
        val selectAll = selectedItems().size != items.size
        items.forEach { it.selected = selectAll }
        adapter.notifyDataSetChanged()
        refreshState()
    }

    private fun rotateSelected(delta: Int) {
        items.forEachIndexed { position, edit ->
            if (edit.selected) {
                edit.rotation = ((edit.rotation + delta) % 360 + 360) % 360
                adapter.notifyItemChanged(position)
            }
        }
    }

    private fun duplicateSelected() {
        var position = items.size - 1
        while (position >= 0) {
            val edit = items[position]
            if (edit.selected) items.add(position + 1, PageEdit(edit.index, edit.rotation))
            position--
        }
        items.forEach { it.selected = false }
        adapter.notifyDataSetChanged()
        refreshState()
    }

    private fun deleteSelected() {
        if (selectedItems().size >= items.size) {
            Toast.makeText(this, "Gardez au moins une page", Toast.LENGTH_LONG).show()
            return
        }
        items.removeAll { it.selected }
        adapter.notifyDataSetChanged()
        refreshState()
    }

    private fun save() {
        saveButton.isEnabled = false
        val edits = items.map { PageEdit(it.index, it.rotation) }
        val source = intent.data!!
        PdfOperations.organize(this, source, edits, { file ->
            pending = file
            startActivityForResult(Ui.createPdfIntent(Ui.baseName(this, source) + "_organise.pdf"), saveRequest)
        }, { message ->
            saveButton.isEnabled = true
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != saveRequest) return
        val source = pending
        pending = null
        val destination = if (resultCode == RESULT_OK) data?.data else null
        if (source == null) return
        if (destination == null) {
            source.delete()
            saveButton.isEnabled = true
            return
        }
        val saved = Ui.saveFile(this, source, destination)
        source.delete()
        if (saved) {
            Toast.makeText(this, "PDF enregistré", Toast.LENGTH_LONG).show()
            finish()
        } else {
            saveButton.isEnabled = true
            Toast.makeText(this, "Impossible d’enregistrer le PDF", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        pending?.delete()
        executor.shutdownNow()
        if (::renderer.isInitialized) {
            synchronized(renderer) { renderer.close() }
        }
        if (::descriptor.isInitialized) descriptor.close()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ miniatures

    private fun renderThumbnail(index: Int): Bitmap? = synchronized(renderer) {
        try {
            val page = renderer.openPage(index)
            val width = 360
            val height = (width * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun rotated(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees % 360 == 0) bitmap
        else Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) }, true
        )

    private fun loadThumbnail(index: Int, holder: PageHolder) {
        executor.execute {
            val bitmap = cache.get(index) ?: renderThumbnail(index)?.also { cache.put(index, it) }
            if (bitmap != null) {
                handler.post {
                    val position = holder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && items.getOrNull(position)?.index == index) {
                        holder.image.setImageBitmap(rotated(bitmap, items[position].rotation))
                    }
                }
            }
        }
    }

    private class PageHolder(val card: MaterialCardView, val image: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(card)

    private inner class PageAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val card = MaterialCardView(this@OrganizeActivity).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                setContentPadding(dp(6), dp(6), dp(6), dp(6))
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }
            }
            val column = LinearLayout(this@OrganizeActivity).apply { orientation = LinearLayout.VERTICAL }
            val image = ImageView(this@OrganizeActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
            }
            column.addView(image, LinearLayout.LayoutParams(-1, dp(190)))
            val label = TextView(this@OrganizeActivity).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(0, dp(6), 0, 0)
            }
            column.addView(label, LinearLayout.LayoutParams(-1, -2))
            card.addView(column)
            return PageHolder(card, image, label)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val edit = items[position]
            val context = this@OrganizeActivity
            holder.label.text = if (edit.index == position) "Page ${position + 1}" else "Page ${position + 1}  (orig. ${edit.index + 1})"
            holder.card.setCardBackgroundColor(if (edit.selected) ThemeManager.primary(context) else ThemeManager.surface(context))
            holder.label.setTextColor(if (edit.selected) ThemeManager.primaryText(context) else ThemeManager.text(context))
            val cached = cache.get(edit.index)
            if (cached != null) {
                holder.image.setImageBitmap(rotated(cached, edit.rotation))
            } else {
                holder.image.setImageDrawable(null)
                loadThumbnail(edit.index, holder)
            }
            holder.card.setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION) {
                    items[current].selected = !items[current].selected
                    notifyItemChanged(current)
                    refreshState()
                }
            }
        }
    }
}
