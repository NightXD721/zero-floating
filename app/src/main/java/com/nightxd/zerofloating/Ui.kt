package com.nightxd.zerofloating

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
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
    val bg = 0xFF07122B.toInt()
    val panel = 0xFF0E1F45.toInt()
    val raised = 0xFF152B5C.toInt()
    val line = 0xFF254080.toInt()
    val text = 0xFFF2F6FF.toInt()
    val dim = 0xFF8FA3CC.toInt()
    val accent = 0xFF2F7BFF.toInt()
    val onAccent = 0xFFFFFFFF.toInt()
    val danger = 0xFFFF5C5C.toInt()
    val ok = 0xFF4DE0A0.toInt()
    val scrim = 0x99030A18.toInt()
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

class Entry(val label: String, val danger: Boolean = false, val run: () -> Unit)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.box(fill: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.setColor(fill)
    d.cornerRadius = dpf(radius)
    if (strokeW > 0) d.setStroke(dp(strokeW).coerceAtLeast(1), stroke)
    return d
}

fun Context.corners(fill: Int, tl: Float, tr: Float, br: Float, bl: Float): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.setColor(fill)
    val a = dpf(tl)
    val b = dpf(tr)
    val c = dpf(br)
    val e = dpf(bl)
    d.cornerRadii = floatArrayOf(a, a, b, b, c, c, e, e)
    return d
}

fun Context.pressable(v: View, radius: Float) {
    v.foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, box(Pal.white, radius))
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
    t.letterSpacing = 0.03f
    t.setPadding(dp(18), dp(13), dp(18), dp(13))
    t.background = if (primary) box(Pal.accent, 10f) else box(Pal.raised, 10f, Pal.line, 1)
    pressable(t, 10f)
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
    e.background = box(Pal.panel, 8f, Pal.line, 1)
    e.setPadding(dp(14), dp(12), dp(14), dp(12))
    e.setSingleLine(true)
    return e
}

fun Context.lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h)
    p.setMargins(dp(l), dp(t), dp(r), dp(b))
    return p
}

fun Context.menuColumn(items: List<Entry>, after: () -> Unit): LinearLayout {
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.background = box(Pal.panel, 12f, Pal.line, 1)
    col.setPadding(dp(4), dp(4), dp(4), dp(4))
    col.minimumWidth = dp(190)
    for (e in items) {
        val row = tv(e.label, 14f, if (e.danger) Pal.danger else Pal.text, Fonts.medium)
        row.setPadding(dp(14), dp(11), dp(14), dp(11))
        pressable(row, 8f)
        row.setOnClickListener {
            after()
            e.run()
        }
        col.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }
    return col
}
