package com.nightxd.zerofloating

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Pal {
    val bg = 0xFFEAF1FF.toInt()
    val panel = 0xFFFFFFFF.toInt()
    val raised = 0xFFDCE8FF.toInt()
    val hi = 0xFFFFD93D.toInt()
    val line = 0xFF0A0A0A.toInt()
    val text = 0xFF0A0A0A.toInt()
    val dim = 0xFF475270.toInt()
    val accent = 0xFF0258F2.toInt()
    val onAccent = 0xFFFFFFFF.toInt()
    val danger = 0xFFD62828.toInt()
    val ok = 0xFF0B8F4D.toInt()
    val scrim = 0xAA0A0A0A.toInt()
    val white = 0xFFFFFFFF.toInt()
}

object Fonts {
    val head: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val black: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val body: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val note: Typeface = Typeface.create("serif", Typeface.ITALIC)
    val mono: Typeface = Typeface.create("monospace", Typeface.NORMAL)
}

class Grip(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.color = Pal.line
        p.strokeWidth = ctx.dpf(2.5f)
        p.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val d = context.dpf(1f)
        val w = width.toFloat()
        val h = height.toFloat()
        for (k in 1..3) {
            canvas.drawLine(w - 5f * d * k - 3f * d, h - 3f * d, w - 3f * d, h - 5f * d * k - 3f * d, p)
        }
    }
}

class GripRow(ctx: Context) : LinearLayout(ctx) {
    val grip = Grip(ctx)

    init {
        gravity = Gravity.END
        visibility = View.GONE
        addView(grip, LayoutParams(ctx.dp(34), ctx.dp(24)))
    }
}

class Entry(val label: String, val danger: Boolean = false, val run: () -> Unit)

class Brut(
    private val fill: Int,
    private val ink: Int,
    private val off: Float,
    private val radius: Float,
    private val stroke: Float,
    private val live: Boolean
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var down = false

    override fun isStateful(): Boolean = live

    override fun onStateChange(state: IntArray): Boolean {
        val d = live && state.contains(android.R.attr.state_pressed)
        if (d == down) return false
        down = d
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val shift = if (down) off * 0.75f else 0f
        if (off > 0f) {
            paint.style = Paint.Style.FILL
            paint.color = ink
            rect.set(b.left + off, b.top + off, b.right.toFloat(), b.bottom.toFloat())
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        rect.set(b.left + shift, b.top + shift, b.right - off + shift, b.bottom - off + shift)
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = ink
        rect.inset(stroke / 2f, stroke / 2f)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.brut(fill: Int, shadow: Int = 3, live: Boolean = false, radius: Float = 4f, stroke: Int = 3): Drawable {
    return Brut(fill, Pal.line, dpf(shadow.toFloat()), dpf(radius), dpf(stroke.toFloat()), live)
}

fun Context.skin(v: View, fill: Int, shadow: Int = 3, live: Boolean = false, radius: Float = 4f, stroke: Int = 3) {
    val l = v.paddingLeft
    val t = v.paddingTop
    val r = v.paddingRight
    val b = v.paddingBottom
    v.background = brut(fill, shadow, live, radius, stroke)
    v.setPadding(l, t, r + dp(shadow), b + dp(shadow))
}

fun Context.pressable(v: View, radius: Float) {
    val mask = GradientDrawable()
    mask.setColor(Pal.white)
    mask.cornerRadius = dpf(radius)
    v.foreground = RippleDrawable(ColorStateList.valueOf(0x22000000), null, mask)
}

fun Context.tv(text: CharSequence, sp: Float, color: Int = Pal.text, face: Typeface = Fonts.body): TextView {
    val t = TextView(this)
    t.text = text
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    t.setTextColor(color)
    t.typeface = face
    return t
}

fun Context.button(label: String, primary: Boolean, onClick: () -> Unit): TextView {
    val t = tv(label, 15f, if (primary) Pal.onAccent else Pal.text, Fonts.head)
    t.gravity = Gravity.CENTER
    t.letterSpacing = 0.04f
    t.setPadding(dp(18), dp(13), dp(18), dp(13))
    skin(t, if (primary) Pal.accent else Pal.panel, 4, true)
    t.setOnClickListener { onClick() }
    return t
}

fun Context.field(hint: String, value: String, face: Typeface = Fonts.body): EditText {
    val e = EditText(this)
    e.setText(value)
    e.hint = hint
    e.setHintTextColor(Pal.dim)
    e.setTextColor(Pal.text)
    e.typeface = face
    e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    e.setPadding(dp(14), dp(12), dp(14), dp(12))
    e.setSingleLine(true)
    skin(e, Pal.panel, 3)
    return e
}

fun Context.lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h)
    p.setMargins(dp(l), dp(t), dp(r), dp(b))
    return p
}

fun Context.rule(heightDp: Int = 3): View {
    val v = View(this)
    v.setBackgroundColor(Pal.line)
    v.minimumHeight = dp(heightDp)
    return v
}

fun Context.menuColumn(items: List<Entry>, after: () -> Unit): LinearLayout {
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(3), dp(3), dp(3), dp(3))
    skin(col, Pal.panel, 5)
    col.minimumWidth = dp(190)
    for (i in items.indices) {
        val e = items[i]
        val row = tv(e.label, 15f, if (e.danger) Pal.danger else Pal.text, Fonts.head)
        row.setPadding(dp(14), dp(11), dp(14), dp(11))
        pressable(row, 2f)
        row.setOnClickListener {
            after()
            e.run()
        }
        col.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        if (i < items.size - 1) col.addView(rule(2), LinearLayout.LayoutParams(MATCH, dp(2)))
    }
    return col
}
