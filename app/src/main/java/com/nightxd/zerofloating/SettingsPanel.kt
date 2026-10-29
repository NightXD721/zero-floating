package com.nightxd.zerofloating

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class SettingsPanel(
    ctx: Context,
    private val prefs: Prefs,
    parts: Int,
    private val onApply: () -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(ctx) {

    val handle = LinearLayout(ctx)
    val gripRow = GripRow(ctx)
    private val modelBox = LinearLayout(ctx)
    private var live: List<ModelInfo>? = null

    init {
        orientation = VERTICAL
        setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
        ctx.skin(this, Pal.panel, 5)

        handle.gravity = Gravity.CENTER_VERTICAL
        handle.setPadding(ctx.dp(14), ctx.dp(10), ctx.dp(8), ctx.dp(8))
        val title = if ((parts and BUBBLE) != 0) "Tampilan dan ukuran" else "Model dan cara kerja"
        handle.addView(ctx.tv(title, 19f, Pal.text, Fonts.black), LayoutParams(0, WRAP, 1f))
        val close = ctx.tv("Tutup", 13f, Pal.accent, Fonts.medium)
        close.setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(8))
        close.setOnClickListener { onClose() }
        handle.addView(close)
        addView(handle, LayoutParams(MATCH, WRAP))
        addView(ctx.rule(3), LayoutParams(MATCH, ctx.dp(3)))

        val col = LinearLayout(ctx)
        col.orientation = VERTICAL
        col.setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(14))
        if ((parts and MODEL) != 0) {
            providerSection(col)
            col.addView(label("Model"))
            modelBox.orientation = VERTICAL
            col.addView(modelBox, LayoutParams(MATCH, WRAP))
            fillModels()
        }
        if ((parts and MODE) != 0) modeSection(col)
        if ((parts and BUBBLE) != 0) bubbleSection(col)

        val sc = ScrollView(ctx)
        sc.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        addView(sc, LayoutParams(MATCH, 0, 1f))
        addView(gripRow, LayoutParams(MATCH, WRAP))
    }

    fun enableResize(): View {
        gripRow.visibility = View.VISIBLE
        return gripRow.grip
    }

    private fun label(text: String): TextView {
        val t = context.tv(text, 13f, Pal.dim, Fonts.medium)
        t.setPadding(0, context.dp(12), 0, context.dp(4))
        return t
    }

    private fun chip(text: String): TextView {
        val t = context.tv(text, 14f, Pal.text, Fonts.head)
        t.gravity = Gravity.CENTER
        t.setPadding(context.dp(12), context.dp(9), context.dp(12), context.dp(9))
        context.skin(t, Pal.panel, 3, true, 4f, 2)
        return t
    }

    private fun paintChip(t: TextView, on: Boolean) {
        t.background = context.brut(if (on) Pal.hi else Pal.panel, 3, true, 4f, 2)
    }

    private fun providerSection(col: LinearLayout) {
        val g = prefs.keyOf("google").isNotBlank()
        val q = prefs.keyOf("groq").isNotBlank()
        col.addView(label("Penyedia aktif"))
        if (g && q) {
            val row = LinearLayout(context)
            val names = listOf("google" to "Google AI Studio", "groq" to "Groq")
            val chips = ArrayList<TextView>()
            for (i in names.indices) {
                val c = chip(names[i].second)
                chips.add(c)
                row.addView(c, context.lp(0, WRAP, 0, 0, if (i == 0) 8 else 0, 0).also { it.weight = 1f })
            }
            fun paint() {
                for (i in chips.indices) paintChip(chips[i], names[i].first == prefs.provider)
            }
            for (i in chips.indices) {
                chips[i].setOnClickListener {
                    prefs.provider = names[i].first
                    paint()
                    fillModels()
                    onApply()
                }
            }
            paint()
            col.addView(row, LayoutParams(MATCH, WRAP))
        } else {
            val name = if (prefs.provider == "groq") "Groq" else "Google AI Studio"
            col.addView(context.tv(name + ". Tambah key penyedia lain lewat menu utama aplikasi.", 13f, Pal.text, Fonts.body))
        }
    }

    private fun modelRow(m: ModelInfo, repaint: () -> Unit): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = VERTICAL
        row.setPadding(context.dp(14), context.dp(9), context.dp(14), context.dp(9))
        context.skin(row, Pal.panel, 3)
        row.addView(context.tv(m.name, 15f, Pal.text, Fonts.head))
        row.addView(context.tv(m.id, 11f, Pal.accent, Fonts.mono))
        row.addView(context.tv(m.note, 12f, Pal.dim, Fonts.body))
        row.setOnClickListener {
            prefs.model = m.id
            repaint()
            onApply()
        }
        return row
    }

    private fun fillModels() {
        modelBox.removeAllViews()
        val groq = prefs.provider == "groq"
        val items: List<ModelInfo> = if (groq) (live ?: Models.groq) else Models.all
        val rows = ArrayList<LinearLayout>()
        fun repaint() {
            for (i in rows.indices) {
                rows[i].background = context.brut(if (items[i].id == prefs.model) Pal.hi else Pal.panel, 3)
            }
        }
        for (m in items) {
            val r = modelRow(m) { repaint() }
            rows.add(r)
            modelBox.addView(r, context.lp(MATCH, WRAP, 0, 6, 0, 0))
        }
        repaint()
        if (groq) {
            val load = chip("Muat semua model dari akun Groq")
            load.setOnClickListener {
                val key = prefs.keyOf("groq")
                if (key.isBlank()) {
                    Toast.makeText(context, "Isi API key Groq dulu", Toast.LENGTH_SHORT).show()
                } else {
                    load.text = "Memuat..."
                    Gemini.groqModels(key) { r ->
                        r.onSuccess { ids ->
                            val list = ArrayList<ModelInfo>()
                            for (id in ids) {
                                list.add(ModelInfo(id, id, if (Models.isVision(id)) "Bisa baca gambar." else "Teks saja."))
                            }
                            live = list
                            fillModels()
                        }
                        r.onFailure { e ->
                            load.text = "Gagal: " + (e.message ?: "tidak diketahui")
                        }
                    }
                }
            }
            modelBox.addView(load, context.lp(MATCH, WRAP, 0, 10, 0, 0))
        }
    }

    private fun modeSection(col: LinearLayout) {
        col.addView(label("Cara kerja (usaha berpikir dan gaya jawaban)"))
        val levels = listOf("low" to "Cepat", "medium" to "Sedang", "high" to "Tinggi")
        val row = LinearLayout(context)
        val chips = ArrayList<TextView>()
        for (i in levels.indices) {
            val c = chip(levels[i].second)
            chips.add(c)
            row.addView(c, context.lp(0, WRAP, 0, 0, if (i < 2) 8 else 0, 0).also { it.weight = 1f })
        }
        fun paint() {
            for (i in chips.indices) paintChip(chips[i], levels[i].first == prefs.level)
        }
        for (i in chips.indices) {
            chips[i].setOnClickListener {
                prefs.level = levels[i].first
                paint()
                onApply()
            }
        }
        paint()
        col.addView(row, LayoutParams(MATCH, WRAP))
        col.addView(
            context.tv(
                "Cepat: jawab singkat, usaha berpikir kecil. Sedang: seimbang. Tinggi: menimbang lebih lama, jawaban lebih lengkap dan teliti.",
                12f, Pal.dim, Fonts.note
            ).also { it.setPadding(0, context.dp(6), 0, 0) }
        )
        col.addView(label("Mode berpikir"))
        col.addView(toggle("Berpikir dulu", { prefs.think }, { prefs.think = it }))
        col.addView(
            context.tv(
                "Nyala: Zero berpikir dulu sesuai cara kerja, prosesnya tampil di chat dan bisa ditutup. Mati: Zero langsung menjawab.",
                12f, Pal.dim, Fonts.note
            ).also { it.setPadding(0, context.dp(6), 0, 0) }
        )
        col.addView(label("Terjemah"))
        col.addView(toggle("Terjemah instan", { prefs.trMode == "fast" }, { prefs.trMode = if (it) "fast" else "best" }))
        col.addView(
            context.tv(
                "Nyala: pakai model tercepat, berpikir dimatikan, gambar dikecilkan. Mati: pakai model pilihanmu, lebih teliti tapi lebih lambat.",
                12f, Pal.dim, Fonts.note
            ).also { it.setPadding(0, context.dp(6), 0, 0) }
        )
    }

    private fun toggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit): TextView {
        val t = chip("")
        fun paint() {
            t.text = label + ": " + (if (get()) "NYALA" else "MATI")
            paintChip(t, get())
        }
        t.setOnClickListener {
            set(!get())
            paint()
            onApply()
        }
        paint()
        return t
    }

    private fun seek(col: LinearLayout, text: (Int) -> String, min: Int, max: Int, cur: Int, set: (Int) -> Unit) {
        val t = context.tv(text(cur), 13f, Pal.dim, Fonts.medium)
        t.setPadding(0, context.dp(12), 0, 0)
        val sb = SeekBar(context)
        sb.max = max - min
        sb.progress = cur - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + min
                t.text = text(v)
                if (fromUser) {
                    set(v)
                    onApply()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
            }
        })
        col.addView(t, LayoutParams(MATCH, WRAP))
        col.addView(sb, LayoutParams(MATCH, WRAP))
    }

    private fun posRow(col: LinearLayout, title: String, get: () -> String, set: (String) -> Unit) {
        col.addView(label(title))
        val opts = listOf("top" to "Atas", "mid" to "Tengah", "bottom" to "Bawah")
        val row = LinearLayout(context)
        val chips = ArrayList<TextView>()
        for (i in opts.indices) {
            val c = chip(opts[i].second)
            chips.add(c)
            row.addView(c, context.lp(0, WRAP, 0, 0, if (i < 2) 8 else 0, 0).also { it.weight = 1f })
        }
        fun paint() {
            for (i in chips.indices) paintChip(chips[i], opts[i].first == get())
        }
        for (i in chips.indices) {
            chips[i].setOnClickListener {
                set(opts[i].first)
                paint()
                onApply()
            }
        }
        paint()
        col.addView(row, LayoutParams(MATCH, WRAP))
    }

    private fun bubbleSection(col: LinearLayout) {
        seek(col, { "Transparansi bubble: " + it + "%" }, 30, 100, prefs.bubbleOpacity) { prefs.bubbleOpacity = it }
        seek(col, { "Ukuran bubble: " + it + " dp" }, 40, 88, prefs.bubbleSize) { prefs.bubbleSize = it }
        seek(col, { "Transparansi panel dan kartu hasil: " + it + "%" }, 40, 100, prefs.panelOpacity) { prefs.panelOpacity = it }
        seek(col, { "Lebar panel: " + it + "%" }, 40, 100, prefs.panelWidth) { prefs.panelWidth = it }
        seek(col, { "Tinggi panel chat: " + it + "%" }, 25, 90, prefs.chatHeight) { prefs.chatHeight = it }
        seek(col, { "Tinggi kartu terjemah: " + it + "%" }, 10, 60, prefs.cardHeight) { prefs.cardHeight = it }
        seek(col, { "Ukuran teks: " + it + " sp" }, 12, 22, prefs.fontSize) { prefs.fontSize = it }
        posRow(col, "Posisi panel chat", { prefs.panelPos }, { prefs.panelPos = it })
        posRow(col, "Posisi kartu terjemah", { prefs.cardPos }, { prefs.cardPos = it })
        col.addView(toggle("Tempel ke tepi", { prefs.bubbleSnap }, { prefs.bubbleSnap = it }), context.lp(MATCH, WRAP, 0, 14, 0, 0))
        col.addView(toggle("Redup saat diam", { prefs.bubbleDim }, { prefs.bubbleDim = it }), context.lp(MATCH, WRAP, 0, 8, 0, 0))
    }

    companion object {
        const val MODEL = 1
        const val MODE = 2
        const val BUBBLE = 4
    }
}
