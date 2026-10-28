package com.nightxd.zerofloating

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.max

class BrowserActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var web: WebView
    private lateinit var host: FrameLayout
    private lateinit var dock: FrameLayout
    private lateinit var progress: View
    private lateinit var titleView: TextView
    private lateinit var bubble: Mark
    private var menu: LinearLayout? = null
    private var chat: ChatPanel? = null
    private var card: ResultCard? = null
    private var selection: SelectionView? = null
    private var autoJob: Runnable? = null
    private var lastAuto = 0L
    private var reader = false
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        reader = prefs.mode == Mode.READER
        if (prefs.url.isBlank()) {
            Toast.makeText(this, "Alamat web belum diisi", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(buildUi())
        web.loadUrl(prefs.url)
    }

    private fun barButton(label: String, click: () -> Unit): TextView {
        val t = tv(label, 18f, Pal.text, Fonts.head)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(12), dp(6), dp(12), dp(6))
        pressable(t, 8f)
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
        bar.setPadding(dp(6), dp(2), dp(6), dp(2))
        bar.addView(barButton("<") { if (web.canGoBack()) web.goBack() })
        bar.addView(barButton(">") { if (web.canGoForward()) web.goForward() })
        bar.addView(barButton("Muat") { web.reload() })
        titleView = tv("", 13f, Pal.dim, Fonts.medium)
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.setPadding(dp(8), 0, dp(8), 0)
        bar.addView(titleView, LinearLayout.LayoutParams(0, WRAP, 1f))
        val modeBtn = tv(Mode.short(prefs.mode), 14f, Pal.text, Fonts.head)
        modeBtn.setPadding(dp(12), dp(7), dp(12), dp(7))
        skin(modeBtn, Pal.hi, 3, true, 4f, 2)
        modeBtn.setOnClickListener { pickMode() }
        bar.addView(modeBtn)
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(rule(3), LinearLayout.LayoutParams(MATCH, dp(3)))

        progress = View(this)
        progress.setBackgroundColor(Pal.hi)
        val pw = FrameLayout(this)
        pw.setBackgroundColor(Pal.line)
        pw.addView(progress, FrameLayout.LayoutParams(0, MATCH))
        root.addView(pw, LinearLayout.LayoutParams(MATCH, dp(4)))

        web = WebView(this)
        configureWeb()
        host = FrameLayout(this)
        host.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        bubble = Mark(this)
        host.addView(bubble, FrameLayout.LayoutParams(dp(prefs.bubbleSize), dp(prefs.bubbleSize)))
        bubble.alpha = prefs.bubbleOpacity / 100f
        wireBubble()
        root.addView(host, LinearLayout.LayoutParams(MATCH, 0, 1f))

        dock = FrameLayout(this)
        dock.visibility = View.GONE
        dock.setBackgroundColor(Pal.bg)
        dock.setPadding(dp(6), dp(4), dp(6), dp(6))
        root.addView(dock, LinearLayout.LayoutParams(MATCH, WRAP))

        host.post {
            bubble.x = (host.width - dp(60)).toFloat()
            bubble.y = host.height * 0.5f
            dock.alpha = prefs.panelOpacity / 100f
            wake()
        }
        return root
    }

    private fun configureWeb() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = true
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                val sc = u.scheme
                if (sc == "http" || sc == "https") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (e: Exception) {
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                titleView.text = view?.title ?: url ?: ""
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val w = resources.displayMetrics.widthPixels
                val lp = progress.layoutParams
                lp.width = if (newProgress >= 100) 0 else w * newProgress / 100
                progress.layoutParams = lp
            }
        }

        web.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN && menu != null) hideMenu()
            false
        }

        web.setOnScrollChangeListener { _, _, _, _, _ -> scheduleAuto() }
    }

    private fun wireBubble() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        bubble.setOnTouchListener(object : View.OnTouchListener {
            var offX = 0f
            var offY = 0f
            var sx = 0f
            var sy = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        wake()
                        offX = v.x - e.rawX
                        offY = v.y - e.rawY
                        sx = e.rawX
                        sy = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(e.rawX - sx) > slop || abs(e.rawY - sy) > slop) {
                            if (!moved) hideMenu()
                            moved = true
                        }
                        if (moved) {
                            v.x = (e.rawX + offX).coerceIn(0f, max(0f, (host.width - v.width).toFloat()))
                            v.y = (e.rawY + offY).coerceIn(0f, max(0f, (host.height - v.height).toFloat()))
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            toggleMenu()
                        } else if (prefs.bubbleSnap) {
                            v.x = if (v.x + v.width / 2f < host.width / 2f) 0f else max(0f, (host.width - v.width).toFloat())
                        }
                        wake()
                    }
                }
                return true
            }
        })
    }

    private fun clampBubble() {
        bubble.x = bubble.x.coerceIn(0f, max(0f, (host.width - bubble.width).toFloat()))
        bubble.y = bubble.y.coerceIn(0f, max(0f, (host.height - bubble.height).toFloat()))
    }

    private fun toggleMenu() {
        if (menu != null) hideMenu() else showMenu()
    }

    private fun hideMenu() {
        val m = menu
        if (m != null) host.removeView(m)
        menu = null
    }

    private fun entries(): List<Entry> {
        return if (reader) {
            listOf(
                Entry("Terjemah layar") { translateScreen() },
                Entry("Terjemah teks halaman") { translatePageText() },
                Entry(if (prefs.auto) "Auto terjemah: nyala" else "Auto terjemah: mati") { toggleAuto() },
                Entry("Tanya Zero") { showChat().focusInput() },
                Entry("Model dan cara kerja") { showSettings(SettingsPanel.MODEL or SettingsPanel.MODE) },
                Entry("Tampilan bubble") { showSettings(SettingsPanel.BUBBLE) },
                Entry("Ganti mode") { pickMode() }
            )
        } else {
            listOf(
                Entry("Tanya Zero") { showChat().focusInput() },
                Entry("Crop lalu tanya") { startCrop(K_ASK) },
                Entry("Ringkas halaman") { summarizePage() },
                Entry("Terjemah layar") { translateScreen() },
                Entry("Crop lalu terjemah") { startCrop(K_TR) },
                Entry("Terjemah teks halaman") { translatePageText() },
                Entry("Model dan cara kerja") { showSettings(SettingsPanel.MODEL or SettingsPanel.MODE) },
                Entry("Tampilan bubble") { showSettings(SettingsPanel.BUBBLE) },
                Entry("Ganti mode") { pickMode() }
            )
        }
    }

    private fun showMenu() {
        hideMenu()
        val col = menuColumn(entries()) { hideMenu() }
        menu = col
        host.addView(col, FrameLayout.LayoutParams(WRAP, WRAP))
        col.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val mw = col.measuredWidth
        val mh = col.measuredHeight
        val gap = dp(8)
        val bw = if (bubble.width > 0) bubble.width else dp(52)
        val onRight = bubble.x + bw / 2f > host.width / 2f
        val rawX = if (onRight) bubble.x - mw - gap else bubble.x + bw + gap
        val maxX = max(gap.toFloat(), (host.width - mw - gap).toFloat())
        val maxY = max(gap.toFloat(), (host.height - mh - gap).toFloat())
        col.x = rawX.coerceIn(gap.toFloat(), maxX)
        col.y = bubble.y.coerceIn(gap.toFloat(), maxY)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(host.windowToken, 0)
    }

    private fun showChat(): ChatPanel {
        val c = chat ?: ChatPanel(this, prefs) { hideDock() }.also { chat = it }
        if (c.parent !== dock) {
            dock.removeAllViews()
            val h = (resources.displayMetrics.heightPixels * 0.46f).toInt()
            dock.addView(c, FrameLayout.LayoutParams(MATCH, h))
            dock.visibility = View.VISIBLE
        }
        return c
    }

    private fun showCard(): ResultCard {
        val c = card ?: ResultCard(this, reader) { hideDock() }.also { card = it }
        if (c.parent !== dock) {
            dock.removeAllViews()
            dock.addView(c, FrameLayout.LayoutParams(MATCH, WRAP))
            dock.visibility = View.VISIBLE
        }
        return c
    }

    private fun showSettings(parts: Int) {
        val v = SettingsPanel(this, prefs, parts, { applyBubble() }, { hideDock() })
        dock.removeAllViews()
        val h = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        dock.addView(v, FrameLayout.LayoutParams(MATCH, h))
        dock.visibility = View.VISIBLE
        applyBubble()
    }

    private fun wake() {
        main.removeCallbacks(dimJob)
        bubble.alpha = prefs.bubbleOpacity / 100f
        if (prefs.bubbleDim) main.postDelayed(dimJob, 2500)
    }

    private val dimJob = Runnable {
        val full = prefs.bubbleOpacity / 100f
        bubble.alpha = if (prefs.bubbleDim) minOf(full, 0.35f) else full
    }

    private fun applyBubble() {
        val lpb = bubble.layoutParams
        lpb.width = dp(prefs.bubbleSize)
        lpb.height = dp(prefs.bubbleSize)
        bubble.layoutParams = lpb
        dock.alpha = prefs.panelOpacity / 100f
        wake()
        host.post { clampBubble() }
    }

    private fun hideDock() {
        hideKeyboard()
        dock.removeAllViews()
        dock.visibility = View.GONE
    }

    private fun removeSelection() {
        val s = selection
        if (s != null) host.removeView(s)
        selection = null
    }

    private fun captureWeb(cb: (Bitmap?) -> Unit) {
        val w = host.width
        val h = host.height
        if (w <= 0 || h <= 0) {
            cb(null)
            return
        }
        hideMenu()
        bubble.visibility = View.INVISIBLE
        host.postDelayed({
            val loc = IntArray(2)
            host.getLocationInWindow(loc)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(
                    window,
                    Rect(loc[0], loc[1], loc[0] + w, loc[1] + h),
                    bmp,
                    { code ->
                        bubble.visibility = View.VISIBLE
                        cb(if (code == PixelCopy.SUCCESS) bmp else null)
                    },
                    main
                )
            } catch (e: Exception) {
                bubble.visibility = View.VISIBLE
                cb(null)
            }
        }, 130)
    }

    private fun deliver(kind: Int, img: Bitmap) {
        if (kind == K_ASK) {
            showChat().ask(Zero.askImage(), img, "Gambar dari halaman")
        } else {
            showCard().translateImage(prefs, img)
        }
    }

    private fun startCrop(kind: Int) {
        removeSelection()
        val sv = SelectionView(this) { r ->
            main.post { removeSelection() }
            if (r != null) {
                val rect = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                captureWeb { bmp ->
                    if (bmp == null) {
                        toast("Gagal menangkap halaman")
                    } else {
                        deliver(kind, Pix.crop(bmp, rect))
                    }
                }
            }
        }
        selection = sv
        host.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun translateScreen() {
        captureWeb { bmp ->
            if (bmp == null) toast("Gagal menangkap halaman") else showCard().translateImage(prefs, bmp)
        }
    }

    private fun pageText(cb: (String) -> Unit) {
        web.evaluateJavascript("(function(){var b=document.body;return b?b.innerText:''})()") { raw ->
            val s = try {
                JSONArray("[" + (raw ?: "null") + "]").optString(0, "")
            } catch (e: Exception) {
                ""
            }
            cb(if (s == "null") "" else s)
        }
    }

    private fun translatePageText() {
        pageText { t ->
            if (t.trim().length < 20) {
                toast("Teks halaman tidak ditemukan")
            } else {
                showCard().translateText(prefs, t.take(12000))
            }
        }
    }

    private fun summarizePage() {
        pageText { t ->
            if (t.isBlank()) {
                toast("Teks halaman tidak ditemukan")
            } else {
                showChat().ask(Zero.summarize(t.take(14000)), null, "Ringkas halaman ini")
            }
        }
    }

    private fun toggleAuto() {
        prefs.auto = !prefs.auto
        toast(if (prefs.auto) "Auto terjemah nyala. Berhenti scroll, Zero menerjemahkan." else "Auto terjemah mati")
    }

    private fun scheduleAuto() {
        if (!reader || !prefs.auto) return
        val old = autoJob
        if (old != null) main.removeCallbacks(old)
        val r = Runnable { runAuto() }
        autoJob = r
        main.postDelayed(r, 1600)
    }

    private fun runAuto() {
        if (!prefs.auto) return
        if (SystemClock.uptimeMillis() - lastAuto < 1500) return
        val c = card
        if (c != null && c.busy) {
            scheduleAuto()
            return
        }
        captureWeb { bmp ->
            lastAuto = SystemClock.uptimeMillis()
            if (bmp != null) showCard().translateImage(prefs, bmp)
        }
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
            toast("Izinkan tampil di atas aplikasi lain dulu")
            startActivity(Launch.overlaySettings(this))
            return
        }
        prefs.mode = m
        if (m == Mode.OVERLAY) {
            startActivity(Intent(this, CaptureRequestActivity::class.java))
            finish()
        } else {
            recreate()
        }
    }

    override fun onBackPressed() {
        if (selection != null) {
            removeSelection()
            return
        }
        if (menu != null) {
            hideMenu()
            return
        }
        if (dock.visibility == View.VISIBLE) {
            hideDock()
            return
        }
        if (web.canGoBack()) {
            web.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideMenu()
        host.post { clampBubble() }
    }

    override fun onPause() {
        super.onPause()
        if (::web.isInitialized) web.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val K_ASK = 0
        private const val K_TR = 1
    }
}
