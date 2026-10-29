package com.nightxd.zerofloating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.max

class Patch(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val text: String,
    val fill: Int,
    val ink: Int
) {
    var layout: StaticLayout? = null
}

class TransLayer(ctx: Context, private val web: View) : View(ctx) {

    private val items = ArrayList<Patch>()
    private val box = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad = ctx.dp(3).toFloat()
    private val rad = ctx.dp(4).toFloat()
    private val minSize = ctx.dp(8).toFloat()
    private val maxSize = ctx.dp(22).toFloat()

    fun clear() {
        items.clear()
        invalidate()
    }

    fun put(list: List<Patch>, area: RectF) {
        val it = items.iterator()
        while (it.hasNext()) {
            val p = it.next()
            val cx = (p.l + p.r) / 2f
            val cy = (p.t + p.b) / 2f
            if (area.contains(cx, cy)) it.remove()
        }
        items.addAll(list)
        invalidate()
    }

    private fun fit(p: Patch): StaticLayout {
        val w = max((p.r - p.l - pad * 2).toInt(), ctxMin())
        val h = p.b - p.t - pad * 2
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = p.ink
        var size = if (h < maxSize) h else maxSize
        if (size < minSize) size = minSize
        var lay = build(p.text, paint, size, w)
        while (lay.height > h && size > minSize) {
            size -= 1f
            lay = build(p.text, paint, size, w)
        }
        return lay
    }

    private fun ctxMin(): Int = (pad * 8).toInt()

    private fun build(text: String, paint: TextPaint, size: Float, w: Int): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    override fun onDraw(c: Canvas) {
        val sx = web.scrollX.toFloat()
        val sy = web.scrollY.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        for (p in items) {
            val l = p.l - sx
            val t = p.t - sy
            val r = p.r - sx
            val b = p.b - sy
            if (b < 0f || t > vh || r < 0f || l > vw) continue
            box.color = p.fill
            c.drawRoundRect(l, t, r, b, rad, rad, box)
            val lay = p.layout ?: fit(p).also { p.layout = it }
            val room = (b - t) - pad * 2
            val top = if (lay.height < room) t + pad + (room - lay.height) / 2f else t + pad
            c.save()
            c.translate(l + pad, top)
            lay.draw(c)
            c.restore()
        }
    }
}
