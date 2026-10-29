package com.nightxd.zerofloating

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

class ScreenGrabber(
    private val ctx: Context,
    code: Int,
    data: Intent,
    private val onStopped: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("zero-grab")
    private val handler: Handler
    private val projection: MediaProjection
    private val lock = Any()
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var held: Image? = null
    private var w = 0
    private var h = 0
    private var alive = true

    init {
        thread.start()
        handler = Handler(thread.looper)
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (alive) {
                    alive = false
                    main.post { onStopped() }
                }
            }
        }, handler)
        val s = Screen.real(ctx)
        build(s[0], s[1], s[2])
    }

    val active: Boolean
        get() = alive

    private fun build(rw: Int, rh: Int, dpi: Int) {
        val r = ImageReader.newInstance(rw, rh, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ src ->
            val img = try {
                src.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (img != null) {
                synchronized(lock) {
                    held?.close()
                    held = img
                }
            }
        }, handler)
        reader = r
        val d = display
        if (d == null) {
            display = projection.createVirtualDisplay(
                "zero-grab", rw, rh, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, handler
            )
        } else {
            d.resize(rw, rh, dpi)
            d.surface = r.surface
        }
        w = rw
        h = rh
    }

    private fun syncSize(): Boolean {
        val s = Screen.real(ctx)
        if (s[0] == w && s[1] == h) return false
        val old = reader
        synchronized(lock) {
            held?.close()
            held = null
        }
        build(s[0], s[1], s[2])
        old?.close()
        return true
    }

    fun grab(cb: (Bitmap?) -> Unit) {
        if (!alive) {
            cb(null)
            return
        }
        val resized = try {
            syncSize()
        } catch (e: Exception) {
            false
        }
        main.postDelayed({ cb(take()) }, if (resized) 500L else 220L)
    }

    private fun take(): Bitmap? {
        return synchronized(lock) {
            val img = held
            if (img == null) {
                null
            } else {
                try {
                    val plane = img.planes[0]
                    val pw = plane.rowStride / plane.pixelStride
                    val buf = plane.buffer
                    buf.rewind()
                    val raw = Bitmap.createBitmap(pw, img.height, Bitmap.Config.ARGB_8888)
                    raw.copyPixelsFromBuffer(buf)
                    if (pw == img.width) {
                        raw
                    } else {
                        val fit = Bitmap.createBitmap(raw, 0, 0, img.width, img.height)
                        raw.recycle()
                        fit
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun release() {
        alive = false
        synchronized(lock) {
            held?.close()
            held = null
        }
        try { display?.release() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        try { projection.stop() } catch (e: Exception) { }
        thread.quitSafely()
    }
}
