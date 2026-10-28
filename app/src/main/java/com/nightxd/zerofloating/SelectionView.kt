package com.nightxd.zerofloating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SelectionView(ctx: Context, private val onDone: (RectF?) -> Unit) : View(ctx) {
    private val dim = Paint()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cancelFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cancelText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cancelRect = RectF()
    private val sel = RectF()
    private var startX = 0f
    private var startY = 0f
    private var drawing = false
    private var hasSel = false
    private var onCancel = false
    private val minSide = ctx.dpf(28f)

    init {
        dim.color = Pal.scrim
        edge.style = Paint.Style.STROKE
        edge.color = Pal.accent
        edge.strokeWidth = ctx.dpf(2f)
        hint.color = Pal.text
        hint.textSize = ctx.dpf(16f)
        hint.typeface = Fonts.note
        cancelFill.color = Pal.raised
        cancelText.color = Pal.text
        cancelText.textSize = ctx.dpf(14f)
        cancelText.typeface = Fonts.head
        cancelText.textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bw = context.dpf(86f)
        val bh = context.dpf(40f)
        val top = context.dpf(36f)
        cancelRect.set(w - bw - context.dpf(14f), top, w - context.dpf(14f), top + bh)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (!hasSel) {
            canvas.drawRect(0f, 0f, w, h, dim)
            canvas.drawText("Seret untuk memilih area", context.dpf(18f), cancelRect.centerY() + context.dpf(5f), hint)
        } else {
            canvas.drawRect(0f, 0f, w, sel.top, dim)
            canvas.drawRect(0f, sel.bottom, w, h, dim)
            canvas.drawRect(0f, sel.top, sel.left, sel.bottom, dim)
            canvas.drawRect(sel.right, sel.top, w, sel.bottom, dim)
            canvas.drawRect(sel, edge)
        }
        val r = context.dpf(10f)
        canvas.drawRoundRect(cancelRect, r, r, cancelFill)
        canvas.drawText("Batal", cancelRect.centerX(), cancelRect.centerY() + context.dpf(5f), cancelText)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (cancelRect.contains(e.x, e.y)) {
                    onCancel = true
                } else {
                    onCancel = false
                    drawing = true
                    hasSel = false
                    startX = e.x
                    startY = e.y
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (drawing) {
                    sel.set(min(startX, e.x), min(startY, e.y), max(startX, e.x), max(startY, e.y))
                    hasSel = true
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (onCancel) {
                    onCancel = false
                    if (cancelRect.contains(e.x, e.y)) onDone(null)
                } else if (drawing) {
                    drawing = false
                    if (hasSel && sel.width() >= minSide && sel.height() >= minSide) {
                        onDone(RectF(sel))
                    } else {
                        hasSel = false
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                drawing = false
                onCancel = false
                hasSel = false
                invalidate()
            }
        }
        return true
    }
}
