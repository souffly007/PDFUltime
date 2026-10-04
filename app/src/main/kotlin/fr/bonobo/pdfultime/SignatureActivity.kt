package fr.bonobo.pdfultime

import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class SignatureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                20,
                20 + bars.top,
                20,
                20 + bars.bottom
            )
            insets
        }
        root.addView(TextView(this).apply {
            text = "Créer une signature"
            textSize = 26f
            setTypeface(typeface, 1)
        })
        root.addView(TextView(this).apply {
            text = "Signez avec votre doigt dans la zone ci-dessous."
            setPadding(0, 8, 0, 18)
        })
        val pad = SignatureView(this)
        root.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this)
        row.addView(Button(this).apply {
            text = "Effacer"
            setOnClickListener { pad.clear() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply {
            text = "Enregistrer"
            setOnClickListener {
                val file = File(filesDir, "signature.png")
                pad.save(file)
                Toast.makeText(this@SignatureActivity, "Signature enregistrée", Toast.LENGTH_LONG).show()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        setContentView(root)
    }
    private class SignatureView(context: android.content.Context) : View(context) { private val path = Path(); private val paint = Paint(1).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND }; override fun onDraw(c: Canvas) { c.drawColor(Color.WHITE); c.drawPath(path, paint) }; override fun onTouchEvent(e: MotionEvent): Boolean { when (e.action) { MotionEvent.ACTION_DOWN -> path.moveTo(e.x, e.y); MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> path.lineTo(e.x, e.y) }; invalidate(); return true }; fun clear() { path.reset(); invalidate() }; fun save(file: File) { val b = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888); val c = Canvas(b); c.drawColor(Color.WHITE); c.drawPath(path, paint); file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
}
