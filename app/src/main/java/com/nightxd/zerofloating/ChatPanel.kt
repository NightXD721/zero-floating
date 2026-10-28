package com.nightxd.zerofloating

import android.content.Context
import android.graphics.Bitmap
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

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

    init {
        orientation = VERTICAL
        setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
        ctx.skin(this, Pal.panel, 5)

        val head = LinearLayout(ctx)
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

        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(8))

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

    private fun submit() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        if (busy) {
            Toast.makeText(context, "Zero masih menjawab", Toast.LENGTH_SHORT).show()
            return
        }
        input.setText("")
        ask(t, null, null)
    }

    fun ask(text: String, image: Bitmap?, shown: String?) {
        if (busy) {
            Toast.makeText(context, "Zero masih menjawab", Toast.LENGTH_SHORT).show()
            return
        }
        if (empty.parent != null) list.removeView(empty)
        addUser(shown ?: text, image)
        if (image != null) {
            for (t in turns) t.image = null
        }
        turns.add(Gemini.Turn("user", text, image))
        dispatch()
    }

    private fun dispatch() {
        busy = true
        val pending = addZero("Zero lagi mikir...", Pal.dim)
        Gemini.ask(prefs, Zero.system(prefs.lang), ArrayList(turns)) { r ->
            busy = false
            r.onSuccess { ans ->
                turns.add(Gemini.Turn("model", ans))
                pending.setTextColor(Pal.text)
                pending.text = Md.render(ans)
            }
            r.onFailure { e ->
                if (turns.isNotEmpty()) turns.removeAt(turns.size - 1)
                pending.setTextColor(Pal.danger)
                pending.text = "Gagal: " + (e.message ?: "tidak diketahui")
            }
            scrollDown()
        }
        scrollDown()
    }

    private fun maxBubble(): Int = (context.resources.displayMetrics.widthPixels * 0.78f).toInt()

    private fun addUser(text: String, image: Bitmap?) {
        val box = LinearLayout(context)
        box.orientation = VERTICAL
        box.setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        context.skin(box, Pal.hi, 3)
        if (image != null) {
            val iv = ImageView(context)
            iv.setImageBitmap(Pix.thumb(image))
            iv.adjustViewBounds = true
            iv.maxHeight = context.dp(180)
            iv.maxWidth = context.dp(200)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            box.addView(iv, context.lp(WRAP, WRAP, 0, 0, 0, 6))
        }
        val t = context.tv(text, 15f, Pal.text, Fonts.body)
        t.maxWidth = maxBubble()
        box.addView(t, LayoutParams(WRAP, WRAP))
        val p = context.lp(WRAP, WRAP, 24, 6, 0, 2)
        p.gravity = Gravity.END
        list.addView(box, p)
    }

    private fun addZero(text: String, color: Int): TextView {
        val t = context.tv(text, 15f, color, Fonts.body)
        t.maxWidth = maxBubble()
        t.setLineSpacing(0f, 1.12f)
        t.setTextIsSelectable(true)
        t.setPadding(context.dp(12), context.dp(9), context.dp(12), context.dp(9))
        context.skin(t, Pal.raised, 3)
        val p = context.lp(WRAP, WRAP, 0, 6, 24, 2)
        p.gravity = Gravity.START
        list.addView(t, p)
        return t
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
