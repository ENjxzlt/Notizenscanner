package de.ennio.notizscanner

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.net.Uri
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private var asPdf = true

    private fun save(src: Uri, name: String, mime: String): Boolean {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (asPdf) "Documents/Notizen" else "Pictures/Notizen")
        }
        val table = if (asPdf) MediaStore.Files.getContentUri("external") else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = contentResolver.insert(table, v) ?: return false
        contentResolver.openOutputStream(out)!!.use { o -> contentResolver.openInputStream(src)!!.use { it.copyTo(o) } }
        return true
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data) ?: return@registerForActivityResult
        val base = "Notiz_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).format(Date())
        val ok = if (asPdf) {
            val pdf = res.pdf?.uri
            pdf != null && save(pdf, "$base.pdf", "application/pdf")
        } else {
            val pages = res.pages.orEmpty()
            pages.isNotEmpty() && pages.withIndex().all { (i, p) -> save(p.imageUri, "${base}_${i + 1}.jpg", "image/jpeg") }
        }
        status.text = if (ok) "Gespeichert: " + (if (asPdf) "Documents/Notizen/$base.pdf" else "Pictures/Notizen/$base") else "Speichern fehlgeschlagen"
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val btn = Button(this).apply { text = "Notiz scannen"; setOnClickListener { scan() } }
        val rg = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            val pdfB = RadioButton(this@MainActivity).apply { id = 1; text = "PDF" }
            val imgB = RadioButton(this@MainActivity).apply { id = 2; text = "Bild (JPG)" }
            addView(pdfB); addView(imgB); check(1)
            setOnCheckedChangeListener { _, id -> asPdf = id == 1 }
        }
        status = TextView(this).apply { text = "Bereit"; gravity = Gravity.CENTER; setPadding(0, 48, 0, 0) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48)
            addView(rg); addView(btn); addView(status)
        })
    }

    private fun scan() {
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(if (asPdf) GmsDocumentScannerOptions.RESULT_FORMAT_PDF else GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opts).getStartScanIntent(this)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { status.text = "Fehler: ${it.message}" }
    }
}
