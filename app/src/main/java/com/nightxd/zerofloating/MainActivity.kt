package com.nightxd.zerofloating

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private var step = STEP_KEY
    private var fromHome = false
    private var testing = false
    private var notifAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        step = if (prefs.setupDone && prefs.apiKey.isNotBlank()) STEP_HOME else STEP_KEY
        render()
    }

    override fun onResume() {
        super.onResume()
        if (step == STEP_OVERLAY || step == STEP_HOME) render()
    }

    private fun go(next: Int) {
        step = if (fromHome) STEP_HOME else next
        if (step == STEP_HOME) fromHome = false
        render()
    }

    private fun edit(target: Int) {
        fromHome = true
        step = target
        render()
    }

    private fun render() {
        when (step) {
            STEP_KEY -> stepKey()
            STEP_OVERLAY -> stepOverlay()
            STEP_URL -> stepUrl()
            STEP_MODE -> stepMode()
            else -> stepHome()
        }
    }

    private fun page(stepNo: Int, title: String, note: String, content: List<View>, footer: List<View>) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Pal.bg)
        col.setPadding(dp(22), dp(18), dp(22), dp(16))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Mark(this), lp(dp(44), dp(44), 0, 0, 12, 0))
        val names = LinearLayout(this)
        names.orientation = LinearLayout.VERTICAL
        names.addView(tv("Zero Floating", 21f, Pal.text, Fonts.black))
        names.addView(tv("asisten yang melayang di atas layarmu", 13f, Pal.dim, Fonts.note))
        top.addView(names, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        if (stepNo > 0) {
            val bars = LinearLayout(this)
            for (i in 1..4) {
                val b = View(this)
                b.background = brut(if (i <= stepNo) Pal.accent else Pal.panel, 0, false, 2f, 2)
                bars.addView(b, lp(0, dp(12), 0, 0, if (i < 4) 6 else 0, 0).also { it.weight = 1f })
            }
            col.addView(bars, lp(MATCH, WRAP, 0, 20, 0, 0))
        }

        col.addView(tv(title, 30f, Pal.text, Fonts.black), lp(MATCH, WRAP, 0, 22, 0, 0))
        col.addView(tv(note, 15f, Pal.dim, Fonts.note).also { it.setLineSpacing(0f, 1.1f) }, lp(MATCH, WRAP, 0, 6, 0, 14))

        val sc = ScrollView(this)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        for (v in content) {
            if (v.layoutParams == null) inner.addView(v, lp(MATCH, WRAP, 0, 8, 0, 0)) else inner.addView(v)
        }
        sc.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
        col.addView(sc, LinearLayout.LayoutParams(MATCH, 0, 1f))

        for (f in footer) col.addView(f, lp(MATCH, WRAP, 0, 8, 0, 0))
        setContentView(col)
    }

    private fun siteIntent(host: String, path: String): Intent {
        val u = Uri.Builder().scheme("https").authority(host).path(path).build()
        return Intent(Intent.ACTION_VIEW, u)
    }

    private fun tutorial(provider: String) {
        val google = provider == "google"
        val title = if (google) "API key gratis Google AI Studio" else "API key gratis Groq"
        val steps = if (google) {
            "1. Buka aistudio.google.com/apikey lalu login dengan akun Google.\n" +
                "2. Tekan Create API key. Pilih atau buat project kalau diminta.\n" +
                "3. Salin key yang muncul, bentuknya diawali AIza.\n" +
                "4. Kembali ke Zero Floating, tempel di kolom API key.\n\n" +
                "Paket gratis ada batas permintaan per menit dan per hari. Kalau kena batas, tunggu sebentar atau ganti model."
        } else {
            "1. Buka console.groq.com/keys lalu daftar atau login (Google, GitHub, atau email).\n" +
                "2. Tekan Create API Key dan beri nama bebas.\n" +
                "3. Salin key yang muncul, bentuknya diawali gsk_. Key hanya tampil sekali, jadi salin sekarang.\n" +
                "4. Kembali ke Zero Floating, pilih penyedia Groq, lalu tempel di kolom API key.\n\n" +
                "Paket gratis ada batas permintaan per menit dan per hari. Model baca gambar hanya sebagian, Zero memilihkan otomatis."
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(title)
            .setMessage(steps)
            .setPositiveButton("Buka situs") { _, _ ->
                try {
                    startActivity(if (google) siteIntent("aistudio.google.com", "apikey") else siteIntent("console.groq.com", "keys"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Tidak bisa membuka browser", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun pickTutorial() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Tutorial API key gratis")
            .setItems(arrayOf("Google AI Studio (Gemini)", "Groq")) { _, i ->
                tutorial(if (i == 0) "google" else "groq")
            }
            .show()
    }

    private fun stepKey() {
        val groq = prefs.provider == "groq"
        val key = field(if (groq) "gsk_..." else "AIza...", prefs.apiKey, Fonts.mono)
        key.transformationMethod = PasswordTransformationMethod.getInstance()
        val model = field(Models.defaultFor(prefs.provider), prefs.model, Fonts.mono)
        val status = tv("", 13f, Pal.dim, Fonts.body)
        var hidden = true
        val show = tv("Tampilkan kunci", 13f, Pal.accent, Fonts.medium)
        show.setPadding(0, dp(8), dp(8), dp(8))
        show.setOnClickListener {
            hidden = !hidden
            key.transformationMethod = if (hidden) PasswordTransformationMethod.getInstance() else null
            show.text = if (hidden) "Tampilkan kunci" else "Sembunyikan kunci"
        }

        val content = ArrayList<View>()
        content.add(tv("Penyedia AI", 13f, Pal.dim, Fonts.medium))
        val prov = LinearLayout(this)
        val provs = listOf("google" to "Google AI Studio", "groq" to "Groq")
        for (i in provs.indices) {
            val c = tv(provs[i].second, 14f, Pal.text, Fonts.head)
            c.gravity = Gravity.CENTER
            c.setPadding(dp(12), dp(10), dp(12), dp(10))
            skin(c, if (provs[i].first == prefs.provider) Pal.hi else Pal.panel, 3, true, 4f, 2)
            c.setOnClickListener {
                prefs.apiKey = key.text.toString()
                prefs.provider = provs[i].first
                render()
            }
            prov.addView(c, lp(0, WRAP, 0, 0, if (i == 0) 8 else 0, 0).also { it.weight = 1f })
        }
        content.add(prov)
        content.add(tv(if (groq) "API key Groq" else "API key Google AI Studio", 13f, Pal.dim, Fonts.medium))
        content.add(key)
        content.add(show)
        content.add(button("Tutorial bikin API key gratis", false) { pickTutorial() })

        val rows = ArrayList<LinearLayout>()
        var items: List<ModelInfo> = Models.list(prefs.provider)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        fun paintModels() {
            val cur = model.text.toString().trim()
            for (i in rows.indices) {
                if (i < items.size) {
                    rows[i].background = brut(if (items[i].id == cur) Pal.hi else Pal.panel, 4)
                }
            }
        }

        fun fill() {
            box.removeAllViews()
            rows.clear()
            for (m in items) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.VERTICAL
                row.setPadding(dp(14), dp(10), dp(14), dp(10))
                row.addView(tv(m.name, 16f, Pal.text, Fonts.head))
                row.addView(tv(m.id, 12f, Pal.accent, Fonts.mono))
                row.addView(tv(m.note, 12f, Pal.dim, Fonts.body).also { it.setPadding(0, dp(2), 0, 0) })
                skin(row, Pal.panel, 4)
                row.setOnClickListener { model.setText(m.id) }
                rows.add(row)
                box.addView(row, lp(MATCH, WRAP, 0, 8, 0, 0))
            }
            paintModels()
        }

        content.add(tv("Pilih model (" + items.size + " di daftar)", 13f, Pal.dim, Fonts.medium))
        content.add(box)
        fill()

        if (groq) {
            content.add(button("Muat semua model dari akun Groq", false) {
                val k = key.text.toString().trim()
                if (k.length < 20) {
                    status.setTextColor(Pal.danger)
                    status.text = "Isi API key Groq dulu."
                } else {
                    status.setTextColor(Pal.dim)
                    status.text = "Memuat daftar model..."
                    Gemini.groqModels(k) { r ->
                        r.onSuccess { ids ->
                            val list = ArrayList<ModelInfo>()
                            for (id in ids) {
                                list.add(ModelInfo(id, id, if (Models.isVision(id)) "Bisa baca gambar." else "Teks saja."))
                            }
                            items = list
                            fill()
                            status.setTextColor(Pal.ok)
                            status.text = "Dimuat " + list.size + " model dari akunmu."
                        }
                        r.onFailure {
                            status.setTextColor(Pal.danger)
                            status.text = "Gagal: " + (it.message ?: "tidak diketahui")
                        }
                    }
                }
            })
        }

        content.add(tv("Atau ketik ID model sendiri", 13f, Pal.dim, Fonts.medium).also { it.setPadding(0, dp(10), 0, 0) })
        content.add(model)
        model.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                paintModels()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })
        if (groq) {
            content.add(
                tv("Model baca gambar di Groq hanya sebagian. Kalau kamu crop atau terjemah gambar, Zero otomatis memakai model baca gambar.", 12f, Pal.dim, Fonts.note)
                    .also { it.setPadding(0, dp(10), 0, 0) }
            )
        }
        content.add(
            tv("Kunci hanya tersimpan di perangkat ini dan dikirim langsung ke penyedia.", 13f, Pal.dim, Fonts.note)
                .also { it.setPadding(0, dp(12), 0, dp(8)) }
        )
        content.add(status)

        fun save(): Boolean {
            val k = key.text.toString().trim()
            if (k.length < 20) {
                status.setTextColor(Pal.danger)
                status.text = "API key terlalu pendek. Salin utuh dari penyedia."
                return false
            }
            prefs.apiKey = k
            val m = model.text.toString().trim()
            prefs.model = if (m.isEmpty()) Models.defaultFor(prefs.provider) else m
            return true
        }

        val test = button("Tes dan lanjut", true) {
            if (!testing && save()) {
                testing = true
                status.setTextColor(Pal.dim)
                status.text = "Mengetes " + Models.nameOf(prefs.model) + "..."
                val turn = Gemini.Turn("user", "Balas satu kata saja: siap")
                Gemini.ask(prefs, Zero.system(prefs.lang), listOf(turn)) { r ->
                    testing = false
                    r.onSuccess {
                        status.setTextColor(Pal.ok)
                        status.text = "Tersambung. Zero menjawab: " + it.take(40)
                        go(STEP_OVERLAY)
                    }
                    r.onFailure {
                        status.setTextColor(Pal.danger)
                        status.text = "Gagal: " + (it.message ?: "tidak diketahui")
                    }
                }
            }
        }
        val skip = button("Simpan tanpa tes", false) {
            if (save()) go(STEP_OVERLAY)
        }
        page(1, "Kunci dan model", "Pilih Google atau Groq, lalu tempel key gratismu.", content, listOf(test, skip))
    }

    private fun stepOverlay() {
        val granted = Settings.canDrawOverlays(this)
        if (!notifAsked && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifAsked = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        val status = tv(
            if (granted) "Izin sudah aktif" else "Izin belum diberikan",
            15f,
            if (granted) Pal.ok else Pal.danger,
            Fonts.medium
        )
        status.setPadding(0, dp(4), 0, dp(14))
        val content = listOf<View>(
            tv(
                "Dengan izin ini menu mengambang Zero bisa tampil di atas aplikasi lain, lalu kamu tinggal crop bagian layar yang mau ditanyakan.",
                15f, Pal.text, Fonts.body
            ).also { it.setLineSpacing(0f, 1.1f) },
            status,
            button("Buka pengaturan izin", false) {
                startActivity(Launch.overlaySettings(this))
            }
        )
        val next = button(if (fromHome) "Selesai" else "Lanjut", true) {
            if (Settings.canDrawOverlays(this)) {
                go(STEP_URL)
            } else {
                Toast.makeText(this, "Berikan izinnya dulu, atau pilih Lewati", Toast.LENGTH_SHORT).show()
            }
        }
        val skip = button("Lewati dulu", false) { go(STEP_URL) }
        page(2, "Izin overlay", "Supaya Zero bisa melayang di atas layar.", content, listOf(next, skip))
    }

    private fun normalize(raw: String): String? {
        var u = raw.trim()
        if (u.isEmpty()) return null
        val hasScheme = u.startsWith("http:", true) || u.startsWith("https:", true)
        if (!hasScheme) u = "https:" + "/".repeat(2) + u
        val host = Uri.parse(u).host
        if (host.isNullOrEmpty()) return null
        if (!host.contains(".") && host != "localhost") return null
        return u
    }

    private fun stepUrl() {
        val url = field("contoh: situsmanhwa.com", prefs.url, Fonts.mono)
        url.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        val status = tv("", 13f, Pal.danger, Fonts.body)
        status.setPadding(0, dp(10), 0, 0)
        val content = listOf<View>(
            tv("Alamat web", 13f, Pal.dim, Fonts.medium),
            url,
            tv("Boleh tanpa https. Zero menambahkannya sendiri.", 13f, Pal.dim, Fonts.note)
                .also { it.setPadding(0, dp(10), 0, 0) },
            status
        )
        val next = button(if (fromHome) "Simpan" else "Lanjut", true) {
            val fixed = normalize(url.text.toString())
            if (fixed == null) {
                status.text = "Alamat belum benar. Contoh: situsmanhwa.com"
            } else {
                prefs.url = fixed
                go(STEP_MODE)
            }
        }
        val skipUrl = button("Lewati, isi nanti", false) {
            prefs.url = ""
            go(STEP_MODE)
        }
        page(3, "Alamat web (opsional)", "Hanya untuk mode baca manhwa dan novel atau mode biasa. Mode overlay tidak butuh.", content, listOf(next, skipUrl))
    }

    private fun stepMode() {
        val ids = listOf(Mode.READER, Mode.OVERLAY, Mode.NORMAL)
        var chosen = if (prefs.url.isBlank() && prefs.mode != Mode.OVERLAY) Mode.OVERLAY else prefs.mode
        val cards = ArrayList<LinearLayout>()

        fun paint() {
            for (i in cards.indices) {
                val sel = ids[i] == chosen
                cards[i].background = brut(if (sel) Pal.hi else Pal.panel, 4)
            }
        }

        val content = ArrayList<View>()
        for (id in ids) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.setPadding(dp(16), dp(14), dp(16), dp(14))
            skin(c, Pal.panel, 4)
            c.addView(tv(Mode.label(id), 19f, Pal.text, Fonts.head))
            c.addView(tv(Mode.about(id), 13f, Pal.dim, Fonts.body).also {
                it.setPadding(0, dp(4), 0, 0)
                it.setLineSpacing(0f, 1.1f)
            })
            c.setOnClickListener {
                chosen = id
                paint()
            }
            cards.add(c)
            val holder = LinearLayout(this)
            holder.addView(c, LinearLayout.LayoutParams(MATCH, WRAP))
            content.add(holder)
            holder.setPadding(0, 0, 0, dp(10))
        }
        paint()

        val lang = field("Indonesia", prefs.lang)
        val chips = LinearLayout(this)
        for (name in listOf("Indonesia", "Inggris", "Jepang", "Korea", "Mandarin")) {
            val ch = tv(name, 13f, Pal.text, Fonts.medium)
            ch.setPadding(dp(12), dp(6), dp(12), dp(6))
            skin(ch, Pal.panel, 3, true, 4f, 2)
            ch.setOnClickListener { lang.setText(name) }
            chips.addView(ch, lp(WRAP, WRAP, 0, 0, 6, 0))
        }
        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chips)
        content.add(tv("Bahasa terjemahan", 13f, Pal.dim, Fonts.medium).also { it.setPadding(0, dp(8), 0, dp(6)) })
        content.add(lang)
        content.add(chipScroll.also { it.setPadding(0, dp(8), 0, dp(8)) })

        val next = button(if (fromHome) "Simpan" else "Simpan dan mulai", true) {
            prefs.mode = chosen
            val l = lang.text.toString().trim()
            prefs.lang = if (l.isEmpty()) "Indonesia" else l
            prefs.setupDone = true
            val wasHome = fromHome
            step = STEP_HOME
            fromHome = false
            render()
            if (!wasHome) Launch.go(this, prefs)
        }
        page(4, "Pilih mode", "Bisa diganti kapan saja dari layar utama.", content, listOf(next))
    }

    private fun stepHome() {
        val mode = prefs.mode
        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.setPadding(dp(16), dp(14), dp(16), dp(14))
        skin(info, Pal.panel, 5)
        info.addView(tv("Mode aktif", 12f, Pal.dim, Fonts.medium))
        info.addView(tv(Mode.label(mode), 22f, Pal.accent, Fonts.black))
        info.addView(tv(Mode.about(mode), 13f, Pal.text, Fonts.body).also {
            it.setPadding(0, dp(4), 0, dp(10))
            it.setLineSpacing(0f, 1.1f)
        })
        info.addView(tv("Penyedia: " + (if (prefs.provider == "groq") "Groq" else "Google AI Studio"), 13f, Pal.dim, Fonts.mono))
        info.addView(tv("Web: " + (if (prefs.url.isBlank()) "(belum diisi)" else prefs.url), 13f, Pal.dim, Fonts.mono))
        info.addView(tv("Model: " + Models.nameOf(prefs.model), 13f, Pal.dim, Fonts.mono))
        info.addView(tv("Terjemah ke: " + prefs.lang, 13f, Pal.dim, Fonts.mono))

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo)
        logo.adjustViewBounds = true
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        logo.setPadding(dp(6), dp(6), dp(6), dp(6))
        skin(logo, Pal.accent, 5)
        logo.layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dp(6)
        }

        val content = ArrayList<View>()
        content.add(logo)
        content.add(info)
        content.add(button("Ganti mode", false) { edit(STEP_MODE) }.also { lpTop(it) })
        content.add(button("API key dan model", false) { edit(STEP_KEY) }.also { lpTop(it) })
        content.add(button("Ubah alamat web", false) { edit(STEP_URL) }.also { lpTop(it) })
        content.add(button("Izin overlay", false) { edit(STEP_OVERLAY) }.also { lpTop(it) })

        val footer = ArrayList<View>()
        footer.add(button("Mulai Zero", true) { Launch.go(this, prefs) })
        if (FloatingService.running) {
            footer.add(button("Matikan bubble", false) {
                val i = Intent(this, FloatingService::class.java)
                i.action = FloatingService.ACTION_STOP
                startService(i)
                step = STEP_HOME
                render()
            })
        }
        page(0, "Siap dipakai", "Zero sudah terhubung. Tinggal mulai.", content, footer)
    }

    private fun lpTop(v: View) {
        v.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(10) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step != STEP_HOME && prefs.setupDone && prefs.apiKey.isNotBlank()) {
            fromHome = false
            step = STEP_HOME
            render()
            return
        }
        if (step > STEP_KEY && step != STEP_HOME) {
            step -= 1
            render()
            return
        }
        super.onBackPressed()
    }

    companion object {
        private const val STEP_KEY = 1
        private const val STEP_OVERLAY = 2
        private const val STEP_URL = 3
        private const val STEP_MODE = 4
        private const val STEP_HOME = 9
    }
}
