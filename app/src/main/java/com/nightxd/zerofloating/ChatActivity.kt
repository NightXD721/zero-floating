package com.nightxd.zerofloating

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ChatActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private lateinit var dock: FrameLayout
    private lateinit var chat: ChatPanel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
        Quiet.window(this, prefs.quiet)
    }

    private fun barButton(label: String, click: () -> Unit): TextView {
        val t = tv(label, 14f, Pal.text, Fonts.head)
        t.setPadding(dp(12), dp(7), dp(12), dp(7))
        skin(t, Pal.hi, 3, true, 4f, 2)
        t.setOnClickListener { click() }
        return t
    }

    private fun buildUi(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Pal.bg)

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Pal.panel)
        bar.setPadding(dp(10), dp(6), dp(10), dp(6))
        bar.addView(tv("Zero", 20f, Pal.text, Fonts.black), LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(barButton("Model") { showSettings() }, lp(WRAP, WRAP, 0, 0, 8, 0))
        val quiet = barButton(if (prefs.quiet) "Senyap: nyala" else "Senyap: mati") { }
        quiet.setOnClickListener {
            prefs.quiet = !prefs.quiet
            Quiet.window(this, prefs.quiet)
            quiet.text = if (prefs.quiet) "Senyap: nyala" else "Senyap: mati"
        }
        bar.addView(quiet, lp(WRAP, WRAP, 0, 0, 8, 0))
        bar.addView(barButton(Mode.short(prefs.mode)) { pickMode() })
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(rule(3), LinearLayout.LayoutParams(MATCH, dp(3)))

        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(6), dp(6), dp(6), dp(6))
        chat = ChatPanel(this, prefs) { if (dock.visibility == View.VISIBLE) hideDock() else finish() }
        body.addView(chat, LinearLayout.LayoutParams(MATCH, 0, 1f))

        dock = FrameLayout(this)
        dock.visibility = View.GONE
        body.addView(dock, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return root
    }

    private fun showSettings() {
        val v = SettingsPanel(this, prefs, SettingsPanel.MODEL or SettingsPanel.MODE, { }, { hideDock() })
        dock.removeAllViews()
        val h = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        dock.addView(v, FrameLayout.LayoutParams(MATCH, h))
        dock.visibility = View.VISIBLE
    }

    private fun hideDock() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(body.windowToken, 0)
        dock.removeAllViews()
        dock.visibility = View.GONE
    }

    private fun pickMode() {
        val ids = arrayOf(Mode.READER, Mode.OVERLAY, Mode.NORMAL)
        val names = Array(ids.size) { Mode.label(ids[it]) }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Pilih mode")
            .setItems(names) { _, i -> applyMode(ids[i]) }
            .show()
    }

    private fun applyMode(m: String) {
        if (m == prefs.mode) return
        if (m == Mode.OVERLAY && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain dulu", Toast.LENGTH_SHORT).show()
            startActivity(Launch.overlaySettings(this))
            return
        }
        if (m == Mode.READER && prefs.url.isBlank()) {
            Toast.makeText(this, "Alamat web belum diisi. Isi lewat layar utama.", Toast.LENGTH_LONG).show()
            return
        }
        prefs.mode = m
        if (m == Mode.OVERLAY) {
            startActivity(Intent(this, CaptureRequestActivity::class.java))
        } else {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (dock.visibility == View.VISIBLE) {
            hideDock()
            return
        }
        super.onBackPressed()
    }
}
