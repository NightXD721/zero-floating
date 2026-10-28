package com.nightxd.zerofloating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

class Mark(ctx: Context) : View(ctx) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slashBack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slash = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        ink.style = Paint.Style.FILL
        ink.color = Pal.line
        fill.style = Paint.Style.FILL
        fill.color = Pal.accent
        edge.style = Paint.Style.STROKE
        edge.color = Pal.line
        ring.style = Paint.Style.STROKE
        ring.color = Pal.white
        slashBack.style = Paint.Style.STROKE
        slashBack.color = Pal.line
        slashBack.strokeCap = Paint.Cap.ROUND
        slash.style = Paint.Style.STROKE
        slash.color = Pal.hi
        slash.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = min(w, h) / 100f
        val cx = w / 2f - 3f * u
        val cy = h / 2f - 3f * u
        canvas.drawCircle(cx + 6f * u, cy + 6f * u, 42f * u, ink)
        canvas.drawCircle(cx, cy, 42f * u, fill)
        edge.strokeWidth = 5f * u
        canvas.drawCircle(cx, cy, 39.5f * u, edge)
        ring.strokeWidth = 8f * u
        canvas.drawCircle(cx, cy, 22f * u, ring)
        slashBack.strokeWidth = 15f * u
        canvas.drawLine(cx - 20f * u, cy + 20f * u, cx + 20f * u, cy - 20f * u, slashBack)
        slash.strokeWidth = 8f * u
        canvas.drawLine(cx - 20f * u, cy + 20f * u, cx + 20f * u, cy - 20f * u, slash)
    }
}
