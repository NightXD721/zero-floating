package com.nightxd.zerofloating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CappedScroll(ctx: Context, private val cap: Int, private val fixed: Boolean) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = if (fixed) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, mode))
    }
}

class ResultCard(
    ctx: Context,
    fixed: Boolean,
    private val onClose: () -> Unit
) : LinearLayout(ctx) {

    private val head = ctx.tv("Terjemahan", 15f, Pal.accent, Fonts.head)
    private val body = ctx.tv("", 15f, Pal.text, Fonts.body)
    private val shrink: TextView
    private val scroll: CappedScroll
    private var collapsed = false
    private var text = ""

    var busy = false
        private set

    init {
        orientation = VERTICAL
        setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
        ctx.skin(this, Pal.panel, 5)

        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(14), ctx.dp(4), ctx.dp(6), ctx.dp(0))
        row.addView(head, LayoutParams(0, WRAP, 1f))
        row.addView(action("Salin") { copy() })
        shrink = action("Kecil") { toggle() }
        row.addView(shrink)
        row.addView(action("Tutup") { onClose() })
        addView(row, LayoutParams(MATCH, WRAP))
        addView(ctx.rule(3), LayoutParams(MATCH, ctx.dp(3)))

        val cap = (ctx.resources.displayMetrics.heightPixels * (if (fixed) 0.24f else 0.36f)).toInt()
        scroll = CappedScroll(ctx, cap, fixed)
        body.setLineSpacing(0f, 1.15f)
        body.setTextIsSelectable(true)
        body.setPadding(ctx.dp(14), ctx.dp(4), ctx.dp(14), ctx.dp(14))
        scroll.addView(body, FrameLayout.LayoutParams(MATCH, WRAP))
        addView(scroll, LayoutParams(MATCH, WRAP))
    }

    private fun action(label: String, click: () -> Unit): TextView {
        val t = context.tv(label, 13f, Pal.dim, Fonts.medium)
        t.setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(10))
        context.pressable(t, 8f)
        t.setOnClickListener { click() }
        return t
    }

    private fun toggle() {
        collapsed = !collapsed
        scroll.visibility = if (collapsed) View.GONE else View.VISIBLE
        shrink.text = if (collapsed) "Besar" else "Kecil"
    }

    private fun expand() {
        if (collapsed) toggle()
    }

    private fun copy() {
        if (text.isEmpty()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("zero", text))
        Toast.makeText(context, "Tersalin", Toast.LENGTH_SHORT).show()
    }

    private fun loading(label: String) {
        busy = true
        expand()
        head.text = label
        body.setTextColor(Pal.dim)
        body.text = "Zero lagi bekerja..."
    }

    private fun partial(lang: String, part: String) {
        head.text = "Menerjemahkan ke " + lang
        body.setTextColor(Pal.text)
        body.text = Md.render(part)
    }

    private fun done(prefs: Prefs, lang: String, r: Result<String>) {
        busy = false
        r.onSuccess { out ->
            text = out
            head.text = "Terjemahan ke " + lang
            body.setTextColor(Pal.text)
            body.text = Md.render(out + Gemini.note(prefs))
            expand()
            scroll.scrollTo(0, 0)
        }
        r.onFailure { e ->
            head.text = "Gagal"
            body.setTextColor(Pal.danger)
            body.text = e.message ?: "Terjadi kesalahan"
            expand()
        }
    }

    fun translateImage(prefs: Prefs, bmp: Bitmap) {
        if (busy) return
        val lang = prefs.lang
        loading("Menerjemahkan ke " + lang)
        val turn = Gemini.Turn("user", Zero.translateImage(lang), bmp)
        Gemini.stream(prefs, Zero.system(lang), listOf(turn), { p -> partial(lang, p) }, { r -> done(prefs, lang, r) })
    }

    fun translateText(prefs: Prefs, source: String) {
        if (busy) return
        val lang = prefs.lang
        loading("Menerjemahkan ke " + lang)
        val turn = Gemini.Turn("user", Zero.translateText(lang, source))
        Gemini.stream(prefs, Zero.system(lang), listOf(turn), { p -> partial(lang, p) }, { r -> done(prefs, lang, r) })
    }
}
