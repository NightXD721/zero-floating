package com.nightxd.zerofloating

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.io.ByteArrayOutputStream
import kotlin.math.max

object Screen {
    fun real(ctx: Context): IntArray {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dpi = ctx.resources.displayMetrics.densityDpi
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            return intArrayOf(b.width(), b.height(), dpi)
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels, dm.densityDpi)
    }
}

object Pix {
    fun b64(src: Bitmap, maxSide: Int = 1600, quality: Int = 75): String {
        val big = max(src.width, src.height)
        val bmp = if (big > maxSide) {
            val k = maxSide.toFloat() / big.toFloat()
            Bitmap.createScaledBitmap(
                src,
                (src.width * k).toInt().coerceAtLeast(1),
                (src.height * k).toInt().coerceAtLeast(1),
                true
            )
        } else {
            src
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun crop(src: Bitmap, r: Rect): Bitmap {
        val l = r.left.coerceIn(0, src.width - 1)
        val t = r.top.coerceIn(0, src.height - 1)
        val rr = r.right.coerceIn(l + 1, src.width)
        val bb = r.bottom.coerceIn(t + 1, src.height)
        return Bitmap.createBitmap(src, l, t, rr - l, bb - t)
    }

    fun thumb(src: Bitmap, side: Int = 480): Bitmap {
        val big = max(src.width, src.height)
        if (big <= side) return src
        val k = side.toFloat() / big.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            (src.width * k).toInt().coerceAtLeast(1),
            (src.height * k).toInt().coerceAtLeast(1),
            true
        )
    }
}

object Launch {
    fun overlaySettings(a: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.packageName))
    }

    fun go(a: Activity, p: Prefs) {
        if (p.mode == Mode.OVERLAY) {
            if (!Settings.canDrawOverlays(a)) {
                Toast.makeText(a, "Izinkan tampil di atas aplikasi lain dulu", Toast.LENGTH_LONG).show()
                a.startActivity(overlaySettings(a))
                return
            }
            a.startActivity(Intent(a, CaptureRequestActivity::class.java))
        } else if (p.mode == Mode.READER) {
            if (p.url.isBlank()) {
                Toast.makeText(a, "Alamat web masih kosong. Isi lewat Ubah alamat web, atau pakai mode lain.", Toast.LENGTH_LONG).show()
                return
            }
            a.startActivity(Intent(a, BrowserActivity::class.java))
        } else {
            a.startActivity(Intent(a, ChatActivity::class.java))
        }
    }
}
