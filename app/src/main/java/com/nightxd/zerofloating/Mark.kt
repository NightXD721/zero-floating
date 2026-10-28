package com.nightxd.zerofloating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

class Mark(ctx: Context) : View(ctx) {
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slash = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        halo.style = Paint.Style.FILL
        halo.color = 0x402F7BFF
        disc.style = Paint.Style.FILL
        disc.color = Pal.bg
        ring.style = Paint.Style.STROKE
        ring.color = Pal.accent
        slash.style = Paint.Style.STROKE
        slash.color = Pal.text
        slash.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        canvas.drawCircle(cx, cy, s / 2f, halo)
        canvas.drawCircle(cx, cy, s * 0.44f, disc)
        ring.strokeWidth = s * 0.09f
        canvas.drawCircle(cx, cy, s * 0.30f, ring)
        slash.strokeWidth = s * 0.075f
        canvas.drawLine(cx - s * 0.18f, cy + s * 0.18f, cx + s * 0.18f, cy - s * 0.18f, slash)
    }
}
