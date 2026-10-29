package com.nightxd.zerofloating

import android.content.Context
import android.graphics.Bitmap
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private class ZeroMsg(val head: TextView, val think: TextView, val answer: TextView) {
    var open = false
    var started = false
}

class ChatPanel(
    ctx: Context,
    private val prefs: Prefs,
    private val onClose: () -> Unit
) : LinearLayout(ctx) {

    private val turns = ArrayList<Gemini.Turn>()
    private val list = LinearLayout(ctx)
    private val scroll = ScrollView(ctx)
    private val input = EditText(ctx)
    private val empty: TextView
    private var busy = false
    val handle = LinearLayout(ctx)
    val gripRow = GripRow(ctx)
    private val pending = ArrayList<Picked>()
    private val pendRow = LinearLayout(ctx)
    private val pendScroll = HorizontalScrollView(ctx)
    private val pickRow = LinearLayout(ctx)

    init {
        orientation = VERTICAL
        setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
        ctx.skin(this, Pal.panel, 5)

        val head = handle
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(8), ctx.dp(6))
        head.addView(Mark(ctx), ctx.lp(ctx.dp(26), ctx.dp(26), 0, 0, 8, 0))
        head.addView(ctx.tv("Zero", 20f, Pal.text, Fonts.black), LayoutParams(0, WRAP, 1f))
        head.addView(headAction("Hapus") { clear() })
        head.addView(headAction("Tutup") { onClose() })
        addView(head, LayoutParams(MATCH, WRAP))
        addView(ctx.rule(3), LayoutParams(MATCH, ctx.dp(3)))

        list.orientation = VERTICAL
        list.setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(10))
        empty = ctx.tv("Tanya apa saja. Zero menjawab dengan teliti dan jujur.", 15f, Pal.dim, Fonts.note)
        empty.setPadding(ctx.dp(6), ctx.dp(10), ctx.dp(6), ctx.dp(10))
        list.addView(empty)
        scroll.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        addView(scroll, LayoutParams(MATCH, 0, 1f))

        pendScroll.isHorizontalScrollBarEnabled = false
        pendScroll.visibility = View.GONE
        pendRow.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(2))
        pendScroll.addView(pendRow, FrameLayout.LayoutParams(WRAP, WRAP))
        addView(pendScroll, LayoutParams(MATCH, WRAP))

        pickRow.visibility = View.GONE
        pickRow.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(2))
        val kinds = listOf("image" to "Galeri", "camera" to "Kamera", "file" to "File")
        for (k in kinds) {
            val c = ctx.button(k.second, false) { pick(k.first, false) }
            c.setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
            pickRow.addView(c, ctx.lp(0, WRAP, 0, 0, 8, 0).also { it.weight = 1f })
        }
        addView(pickRow, LayoutParams(MATCH, WRAP))

        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(8))
        val plus = ctx.button("+", false) { togglePick() }
        plus.setPadding(ctx.dp(14), ctx.dp(8), ctx.dp(14), ctx.dp(8))
        row.addView(plus, ctx.lp(WRAP, WRAP, 0, 0, 8, 0))

        input.hint = "Tanya Zero"
        input.setHintTextColor(Pal.dim)
        input.setTextColor(Pal.text)
        input.typeface = Fonts.body
        input.textSize = 15f
        input.maxLines = 4
        input.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
        ctx.skin(input, Pal.bg, 3)
        row.addView(input, LayoutParams(0, WRAP, 1f))

        val send = ctx.button("Kirim", true) { submit() }
        send.setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(10))
        row.addView(send, ctx.lp(WRAP, WRAP, 8, 0, 0, 0))
        addView(row, LayoutParams(MATCH, WRAP))
        addView(gripRow, LayoutParams(MATCH, WRAP))
    }

    fun enableResize(): View {
        gripRow.visibility = View.VISIBLE
        return gripRow.grip
    }

    private fun headAction(label: String, click: () -> Unit): TextView {
        val t = context.tv(label, 13f, Pal.accent, Fonts.medium)
        t.setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        context.pressable(t, 8f)
        t.setOnClickListener { click() }
        return t
    }

    fun focusInput() {
        input.requestFocus()
    }

    fun clear() {
        if (busy) return
        turns.clear()
        list.removeAllViews()
        list.addView(empty)
    }

    private fun togglePick() {
        pickRow.visibility = if (pickRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun pick(kind: String, auto: Boolean) {
        pickRow.visibility = View.GONE
        Attach.request(context, kind) { items ->
            if (auto) sendItems("", items) else addPending(items)
        }
    }

    fun quickPick(kind: String) {
        pick(kind, true)
    }

    fun addPending(items: List<Picked>) {
        for (p in items) {
            if (pending.size < 6) pending.add(p)
        }
        renderPending()
    }

    private fun renderPending() {
        pendRow.removeAllViews()
        pendScroll.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        for (p in ArrayList(pending)) {
            val chip = LinearLayout(context)
            chip.gravity = Gravity.CENTER_VERTICAL
            chip.setPadding(context.dp(6), context.dp(5), context.dp(8), context.dp(5))
            if (p.bitmaps.isNotEmpty()) {
                val iv = ImageView(context)
                iv.setImageBitmap(Pix.thumb(p.bitmaps[0], 120))
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                chip.addView(iv, context.lp(context.dp(34), context.dp(34), 0, 0, 6, 0))
            }
            val label = context.tv(p.name, 12f, Pal.text, Fonts.medium)
            label.maxWidth = context.dp(120)
            label.maxLines = 1
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            chip.addView(label)
            val x = context.tv("x", 14f, Pal.danger, Fonts.head)
            x.setPadding(context.dp(10), context.dp(4), context.dp(4), context.dp(4))
            x.setOnClickListener {
                pending.remove(p)
                renderPending()
            }
            chip.addView(x)
            context.skin(chip, Pal.panel, 3, false, 4f, 2)
            pendRow.addView(chip, context.lp(WRAP, WRAP, 0, 0, 8, 0))
        }
    }

    private fun submit() {
        val t = input.text.toString().trim()
        if (t.isEmpty() && pending.isEmpty()) return
        if (busy) {
            Toast.makeText(context, "Zero masih menjawab", Toast.LENGTH_SHORT).show()
            return
        }
        input.setText("")
        if (pending.isEmpty()) {
            ask(t, null, null)
            return
        }
        val items = ArrayList(pending)
        pending.clear()
        renderPending()
        sendItems(t, items)
    }

    private fun sendItems(userText: String, items: List<Picked>) {
        val imgs = ArrayList<Bitmap>()
        val names = ArrayList<String>()
        var pdf: ByteArray? = null
        val sb = StringBuilder()
        sb.append(if (userText.isEmpty()) "Jelaskan dan analisis lampiran ini. Jawab pertanyaan atau tugas di dalamnya kalau ada." else userText)
        for (p in items) {
            names.add(p.name)
            imgs.addAll(p.bitmaps)
            val d = p.pdf
            if (d != null) pdf = d
            val tx = p.text
            if (tx != null) {
                sb.append("\n\nIsi file ").append(p.name).append(":\n").append(tx)
            }
        }
        send(sb.toString(), userText, imgs, pdf, names)
    }

    fun ask(text: String, image: Bitmap?, shown: String?) {
        val imgs = ArrayList<Bitmap>()
        if (image != null) imgs.add(image)
        send(text, shown ?: text, imgs, null, emptyList())
    }

    private fun send(text: String, shown: String, imgs: List<Bitmap>, pdf: ByteArray?, names: List<String>) {
        if (busy) {
            Toast.makeText(context, "Zero masih menjawab", Toast.LENGTH_SHORT).show()
            return
        }
        if (empty.parent != null) list.removeView(empty)
        addUser(shown, imgs, names)
        if (imgs.isNotEmpty() || pdf != null) {
            for (t in turns) {
                t.image = null
                t.more = emptyList()
                t.pdf = null
            }
        }
        val turn = Gemini.Turn("user", text, if (imgs.isNotEmpty()) imgs[0] else null)
        if (imgs.size > 1) turn.more = ArrayList(imgs.subList(1, imgs.size))
        turn.pdf = pdf
        turns.add(turn)
        dispatch()
    }

    private fun dispatch() {
        busy = true
        val msg = addZero()
        Gemini.stream(
            prefs,
            Zero.system(prefs.lang, prefs.level),
            ArrayList(turns),
            { part ->
                if (!msg.started) {
                    msg.started = true
                    if (msg.head.visibility == View.VISIBLE) setOpen(msg, false)
                }
                msg.answer.setTextColor(Pal.text)
                msg.answer.text = Md.render(part)
                scrollDown()
            },
            { r ->
                busy = false
                r.onSuccess { ans ->
                    turns.add(Gemini.Turn("model", ans))
                    msg.answer.setTextColor(Pal.text)
                    msg.answer.text = Md.render(ans + Gemini.note(prefs))
                }
                r.onFailure { e ->
                    if (turns.isNotEmpty()) turns.removeAt(turns.size - 1)
                    msg.answer.setTextColor(Pal.danger)
                    msg.answer.text = "Gagal: " + (e.message ?: "tidak diketahui")
                }
                scrollDown()
            },
            { t ->
                if (msg.head.visibility != View.VISIBLE) {
                    msg.head.visibility = View.VISIBLE
                    setOpen(msg, true)
                }
                msg.think.text = t
                scrollDown()
            }
        )
        scrollDown()
    }

    private fun setOpen(m: ZeroMsg, open: Boolean) {
        m.open = open
        m.think.visibility = if (open) View.VISIBLE else View.GONE
        m.head.text = if (open) "Proses berpikir (ketuk untuk tutup)" else "Proses berpikir (ketuk untuk lihat)"
    }

    private fun maxBubble(): Int = (context.resources.displayMetrics.widthPixels * 0.78f).toInt()

    private fun addUser(text: String, imgs: List<Bitmap>, names: List<String>) {
        val box = LinearLayout(context)
        box.orientation = VERTICAL
        box.setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        context.skin(box, Pal.hi, 3)
        if (imgs.isNotEmpty()) {
            val strip = LinearLayout(context)
            strip.gravity = Gravity.CENTER_VERTICAL
            val shown = if (imgs.size > 3) 3 else imgs.size
            for (k in 0 until shown) {
                val iv = ImageView(context)
                iv.setImageBitmap(Pix.thumb(imgs[k], 360))
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                strip.addView(iv, context.lp(context.dp(96), context.dp(96), 0, 0, 6, 0))
            }
            if (imgs.size > 3) strip.addView(context.tv("+" + (imgs.size - 3), 16f, Pal.text, Fonts.head))
            box.addView(strip, context.lp(WRAP, WRAP, 0, 0, 0, 6))
        }
        for (n in names) {
            box.addView(context.tv("File: " + n, 12f, Pal.dim, Fonts.mono), LayoutParams(WRAP, WRAP))
        }
        if (text.isNotEmpty()) {
            val t = context.tv(text, prefs.fontSize.toFloat(), Pal.text, Fonts.body)
            t.maxWidth = maxBubble()
            box.addView(t, LayoutParams(WRAP, WRAP))
        }
        val p = context.lp(WRAP, WRAP, 24, 6, 0, 2)
        p.gravity = Gravity.END
        list.addView(box, p)
    }

    private fun addZero(): ZeroMsg {
        val box = LinearLayout(context)
        box.orientation = VERTICAL
        box.setPadding(context.dp(12), context.dp(9), context.dp(12), context.dp(9))
        context.skin(box, Pal.raised, 3)
        val head = context.tv("", 12f, Pal.accent, Fonts.medium)
        head.visibility = View.GONE
        head.setPadding(0, 0, 0, context.dp(4))
        val think = context.tv("", (prefs.fontSize - 2).toFloat(), Pal.dim, Fonts.note)
        think.visibility = View.GONE
        think.maxWidth = maxBubble()
        think.setLineSpacing(0f, 1.1f)
        think.setPadding(context.dp(8), 0, 0, context.dp(8))
        val answer = context.tv("Zero lagi mikir...", prefs.fontSize.toFloat(), Pal.dim, Fonts.body)
        answer.maxWidth = maxBubble()
        answer.setLineSpacing(0f, 1.12f)
        answer.setTextIsSelectable(true)
        box.addView(head, LayoutParams(WRAP, WRAP))
        box.addView(think, LayoutParams(WRAP, WRAP))
        box.addView(answer, LayoutParams(WRAP, WRAP))
        val m = ZeroMsg(head, think, answer)
        head.setOnClickListener { setOpen(m, !m.open) }
        val p = context.lp(WRAP, WRAP, 0, 6, 24, 2)
        p.gravity = Gravity.START
        list.addView(box, p)
        return m
    }

    private fun scrollDown() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onClose()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
