package com.nightxd.zerofloating

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

class Picked(val name: String, val bitmaps: List<Bitmap>, val text: String?, val pdf: ByteArray?)

object Attach {
    private var listener: ((List<Picked>) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun request(ctx: Context, kind: String, cb: (List<Picked>) -> Unit) {
        listener = cb
        val i = Intent(ctx, AttachActivity::class.java)
        i.putExtra("kind", kind)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            listener = null
            Toast.makeText(ctx, "Tidak bisa membuka pemilih lampiran", Toast.LENGTH_LONG).show()
        }
    }

    fun deliver(items: List<Picked>) {
        val cb = listener
        listener = null
        if (cb != null && items.isNotEmpty()) cb(items)
    }

    fun warn(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}

object Pick {
    private val imgExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val textExt = setOf(
        "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java",
        "py", "c", "cpp", "h", "cs", "go", "rs", "php", "rb", "sh", "yml", "yaml", "toml", "ini",
        "log", "sql", "tex", "srt", "gradle", "properties", "swift", "lua", "r", "bat"
    )

    private fun displayName(ctx: Context, uri: Uri): String {
        var name = ""
        var c: Cursor? = null
        try {
            c = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (c != null && c.moveToFirst()) name = c.getString(0) ?: ""
        } catch (e: Exception) {
        } finally {
            c?.close()
        }
        if (name.isEmpty()) name = uri.lastPathSegment ?: "lampiran"
        return name
    }

    private fun rotation(s: InputStream): Int {
        return try {
            when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun decode(open: () -> InputStream?, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        open()?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > maxSide * 2) sample *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sample
        val raw = open()?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val rot = open()?.use { rotation(it) } ?: 0
        val big = max(raw.width, raw.height)
        val m = Matrix()
        if (rot != 0) m.postRotate(rot.toFloat())
        if (big > maxSide) {
            val k = maxSide.toFloat() / big.toFloat()
            m.postScale(k, k)
        }
        if (m.isIdentity) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    fun fromFile(f: File): Picked? {
        val bmp = decode({ FileInputStream(f) }, 1600) ?: return null
        return Picked("Foto", listOf(bmp), null, null)
    }

    fun fromUri(ctx: Context, uri: Uri): Picked? {
        val name = displayName(ctx, uri)
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val ext = name.substringAfterLast('.', "").lowercase()
        if (mime.startsWith("image/") || ext in imgExt) {
            val bmp = decode({ ctx.contentResolver.openInputStream(uri) }, 1600)
            if (bmp == null) {
                Attach.warn(ctx, "Gambar " + name + " tidak bisa dibaca")
                return null
            }
            return Picked(name, listOf(bmp), null, null)
        }
        if (mime == "application/pdf" || ext == "pdf") return pdf(ctx, uri, name)
        if (mime.startsWith("text/") || ext in textExt || mime == "application/json" || mime == "application/xml") {
            return text(ctx, uri, name)
        }
        Attach.warn(ctx, "Format " + name + " belum didukung. Pakai gambar, PDF, atau file teks.")
        return null
    }

    private fun text(ctx: Context, uri: Uri, name: String): Picked? {
        val sb = StringBuilder()
        try {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { r ->
                val buf = CharArray(8192)
                while (sb.length < 60000) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
            }
        } catch (e: Exception) {
            Attach.warn(ctx, "File " + name + " tidak bisa dibaca")
            return null
        }
        if (sb.isEmpty()) {
            Attach.warn(ctx, "File " + name + " kosong")
            return null
        }
        var t = sb.toString()
        if (t.length >= 60000) t = t.substring(0, 60000) + "\n[dipotong]"
        return Picked(name, emptyList(), t, null)
    }

    private fun pdf(ctx: Context, uri: Uri, name: String): Picked? {
        if (Prefs(ctx).provider == "groq") return pdfPages(ctx, uri, name)
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
        if (bytes == null) {
            Attach.warn(ctx, "PDF " + name + " tidak bisa dibaca")
            return null
        }
        if (bytes.size > 14 * 1024 * 1024) {
            Attach.warn(ctx, "PDF terlalu besar. Maksimal 14 MB.")
            return null
        }
        return Picked(name, emptyList(), null, bytes)
    }

    private fun pdfPages(ctx: Context, uri: Uri, name: String): Picked? {
        val tmp = File(ctx.cacheDir, "pdf_" + System.currentTimeMillis() + ".pdf")
        try {
            val src = ctx.contentResolver.openInputStream(uri)
            if (src == null) return null
            src.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            val pages = ArrayList<Bitmap>()
            val n = minOf(r.pageCount, 5)
            for (i in 0 until n) {
                val pg = r.openPage(i)
                val w = 1100
                val h = (pg.height * (w.toFloat() / pg.width.toFloat())).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pg.close()
                pages.add(bmp)
            }
            val total = r.pageCount
            r.close()
            pfd.close()
            val label = if (total > n) name + " (" + n + " dari " + total + " hal.)" else name + " (" + n + " hal.)"
            return Picked(label, pages, null, null)
        } catch (e: Exception) {
            Attach.warn(ctx, "PDF " + name + " tidak bisa dibuka")
            return null
        } finally {
            tmp.delete()
        }
    }
}
