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
import android.text.InputType
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
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

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
    private lateinit var layer: TransLayer
    private lateinit var tr: PageTranslator
    private var autoJob: Runnable? = null
    private var lastAuto = 0L
    private var reader = false
    private var fillMode = false
    private var fillUrl = ""
    private val main = Handler(Looper.getMainLooper())
    private var questions: List<FormQuestion> = emptyList()
    private var selected: MutableSet<Int> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        fillMode = intent.getBooleanExtra("fill_mode", false) || prefs.mode == Mode.FILL
        reader = !fillMode && prefs.mode == Mode.READER
        if (fillMode) {
            fillUrl = intent.getStringExtra("start_url") ?: ""
            if (fillUrl.isBlank()) {
                promptFillUrl()
                return
            }
            setContentView(buildUi())
            web.loadUrl(fillUrl)
        } else {
            if (prefs.url.isBlank()) {
                Toast.makeText(this, "Alamat web belum diisi", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            setContentView(buildUi())
            web.loadUrl(prefs.url)
        }
    }

    private fun promptFillUrl() {
        val input = EditText(this)
        input.hint = "https://docs.google.com/forms/..."
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.setText("")
        input.setPadding(dp(16), dp(12), dp(16), dp(12))
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Alamat Form atau Docs")
            .setMessage("Masukkan alamat web setiap kali buka mode ini. Tidak disimpan.")
            .setView(input)
            .setPositiveButton("Buka") { _, _ ->
                val raw = input.text.toString().trim()
                val fixed = normalize(raw)
                if (fixed == null) {
                    Toast.makeText(this, "Alamat belum benar", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    fillUrl = fixed
                    setContentView(buildUi())
                    web.loadUrl(fillUrl)
                }
            }
            .setNegativeButton("Batal") { _, _ -> finish() }
            .setCancelable(false)
            .show()
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
        layer = TransLayer(this, web)
        tr = PageTranslator(this, prefs, web, layer, { cb -> captureWeb(cb) }, { busy -> showBusy(busy) })
        configureWeb()
        host = FrameLayout(this)
        host.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        host.addView(layer, FrameLayout.LayoutParams(MATCH, MATCH))
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

    private val adHosts = setOf(
        // global ad / tracker networks
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "googletagmanager.com", "google-analytics.com", "pagead2.googlesyndication.com",
        "adservice.google.com", "adservice.google.co.id", "ads.google.com",
        "adnxs.com", "adsrvr.org", "advertising.com", "adform.net", "adcolony.com",
        "admob.com", "amazon-adsystem.com", "media.net", "outbrain.com", "taboola.com",
        "criteo.com", "pubmatic.com", "openx.net", "rubiconproject.com", "indexww.com",
        "moatads.com", "scorecardresearch.com", "quantserve.com", "hotjar.com",
        "facebook.net", "connect.facebook.net", "fbcdn.net", "pixel.facebook.com",
        "ads-twitter.com", "analytics.twitter.com", "static.ads-twitter.com",
        "bidswitch.net", "casalemedia.com", "contextweb.com", "smartadserver.com",
        "serving-sys.com", "yieldmo.com", "sharethrough.com", "teads.tv",
        "exelator.com", "bluekai.com", "krxd.net", "demdex.net", "omtrdc.net",
        "2mdn.net", "adsafeprotected.com", "advertising.com", "adtechus.com",
        "appnexus.com", "rlcdn.com", "mathtag.com", "agkn.com", "tapad.com",
        "branch.io", "appsflyer.com", "adjust.com", "kochava.com", "singular.net",
        "chartbeat.com", "newrelic.com", "nr-data.net", "segment.io", "segment.com",
        "mixpanel.com", "amplitude.com", "sentry.io", "bugsnag.com",
        // Indonesia & regional common
        "ads.indosatooredoo.com", "ads.telkomsel.com", "ad.xl.co.id", "ad.smartfren.com",
        "ads.detik.com", "adserver.detik.com", "advertising.detik.com",
        "ads.kompas.com", "ad.kompas.com", "ads.tribunnews.com", "ads.grid.id",
        "ads.kaskus.co.id", "ad.kaskus.co.id", "ads.liputan6.com", "ads.viva.co.id",
        "ads.okezone.com", "ads.sindonews.com", "ads.idntimes.com", "ads.tempo.co",
        "ads.cnnindonesia.com", "ads.kumparan.com", "ads.tirto.id", "ads.republika.co.id",
        "ads.suara.com", "ads.merdeka.com", "ads.jawapos.com", "ads.pikiran-rakyat.com",
        "iklan.kompas.com", "iklan.detik.com", "banner.detik.com", "promo.detik.com",
        "ads.manhwa18.com", "ads.webtoons.com", "ads.mangadex.org", "ads.asurascans.com",
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com",
        "adsterra.com", "adsterra.org", "hilltopads.com", "clickadu.com", "exoclick.com",
        "juicyads.com", "trafficjunky.com", "ero-advertising.com", "admaven.com",
        "mgid.com", "revcontent.com", "content.ad", "zergnet.com",
        "adblade.com", "adskeeper.com", "buysellads.com", "carbonads.com",
        "ad4game.com", "adsensecustomsearchads.com", "pagead.l.doubleclick.net",
        "partner.googleadservices.com", "tpc.googlesyndication.com",
        "securepubads.g.doubleclick.net", "cm.g.doubleclick.net",
        "fundingchoicesmessages.google.com", "ep2.adtrafficquality.google"
    )

    private fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        if (adHosts.contains(h)) return true
        for (d in adHosts) {
            if (h == d || h.endsWith(".$d")) return true
        }
        // common patterns
        if (h.contains("ads.") || h.contains(".ads.") || h.startsWith("ad.") ||
            h.contains("banner") || h.contains("popup") || h.contains("popunder") ||
            h.contains("track.") || h.contains("tracker") || h.contains("pixel.") ||
            h.contains("analytics.") || h.contains("doubleclick") || h.contains("googlesyndication") ||
            h.contains("pagead") || h.contains("adserver") || h.contains("adserv") ||
            h.contains("advert") || h.contains("sponsor") || h.contains("promo.")
        ) {
            // avoid blocking main content sites that happen to have "ad" in name
            if (h.contains("google.com") && !h.contains("adservice") && !h.contains("pagead") &&
                !h.contains("doubleclick") && !h.contains("syndication") && !h.contains("adtraffic")
            ) return false
            if (h.endsWith(".google.com") && !h.contains("ads") && !h.contains("pagead")) return false
            return true
        }
        return false
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
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
                val host = u.host
                if (isAdHost(host)) return true
                val sc = u.scheme
                if (sc == "http" || sc == "https") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (e: Exception) {
                }
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val u = request?.url ?: return super.shouldInterceptRequest(view, request)
                if (isAdHost(u.host)) return emptyResponse()
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tr.reset()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                titleView.text = view?.title ?: url ?: ""
                injectAdBlock()
                if (reader && prefs.auto) main.postDelayed({ tr.run() }, 700)
            }

            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                tr.rescale()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val w = resources.displayMetrics.widthPixels
                val lp = progress.layoutParams
                lp.width = if (newProgress >= 100) 0 else w * newProgress / 100
                progress.layoutParams = lp
            }

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean {
                // block all popup / new window (ads often open these)
                return false
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?, defaultValue: String?,
                result: android.webkit.JsPromptResult?
            ): Boolean {
                result?.cancel()
                return true
            }
        }

        web.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN && menu != null) hideMenu()
            false
        }

        web.setOnScrollChangeListener { _, _, _, _, _ ->
            layer.invalidate()
            scheduleAuto()
        }
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
        if (fillMode) {
            return listOf(
                Entry("Deteksi soal") { detectQuestions() },
                Entry("Terjemah halaman") { tr.run() },
                Entry("Terjemah seluruh halaman") { tr.runFull() },
                Entry("Tampilkan asli") { showOriginal() },
                Entry(if (prefs.auto) "Auto terjemah: nyala" else "Auto terjemah: mati") { toggleAuto() },
                Entry("Tanya Zero") { showChat().focusInput() },
                Entry("Foto lalu tanya") { showChat().quickPick("camera") },
                Entry("Model dan cara kerja") { showSettings(SettingsPanel.MODEL or SettingsPanel.MODE) },
                Entry("Tampilan bubble") { showSettings(SettingsPanel.BUBBLE) },
                Entry(if (prefs.quiet) "Mode senyap: nyala" else "Mode senyap: mati") { toggleQuiet() },
                Entry("Ganti mode") { pickMode() }
            )
        }
        return listOf(
            Entry("Terjemah halaman") { tr.run() },
            Entry("Terjemah seluruh halaman") { tr.runFull() },
            Entry("Tampilkan asli") { showOriginal() },
            Entry(if (prefs.auto) "Auto terjemah: nyala" else "Auto terjemah: mati") { toggleAuto() },
            Entry("Tanya Zero") { showChat().focusInput() },
            Entry("Foto lalu tanya") { showChat().quickPick("camera") },
            Entry("Model dan cara kerja") { showSettings(SettingsPanel.MODEL or SettingsPanel.MODE) },
            Entry("Tampilan bubble") { showSettings(SettingsPanel.BUBBLE) },
            Entry(if (prefs.quiet) "Mode senyap: nyala" else "Mode senyap: mati") { toggleQuiet() },
            Entry("Ganti mode") { pickMode() }
        )
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

    private fun injectAdBlock() {
        val js = """
(function(){
  if(window.__zeroAd) return;
  window.__zeroAd=1;
  var css = [
    'iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]','iframe[src*="adservice"]',
    'iframe[src*="adnxs"]','iframe[src*="adsrvr"]','iframe[src*="popads"]','iframe[src*="popcash"]',
    'iframe[id*="google_ads"]','iframe[id*="ad-"]','iframe[class*="ads"]','iframe[class*="ad-"]',
    'div[id*="google_ads"]','div[id*="div-gpt-ad"]','div[class*="adsbygoogle"]',
    'div[class*="ad-container"]','div[class*="ad-wrapper"]','div[class*="ad-banner"]',
    'div[class*="advertisement"]','div[class*="sponsor"]','div[data-ad]','div[data-ads]',
    'ins.adsbygoogle','.adsbygoogle','[id^="ad-"]','[class^="ad-"]','[class*=" ad-"]',
    '.banner-ad','.ad-banner','.adbox','.ad-unit','.adslot','.adsbox',
    'a[href*="doubleclick"]','a[href*="googlesyndication"]','a[href*="popads"]',
    '[onclick*="pop"]','[onclick*="window.open"]'
  ].join(',') + '{display:none!important;visibility:hidden!important;height:0!important;width:0!important;max-height:0!important;overflow:hidden!important;pointer-events:none!important;}';
  var st=document.createElement('style');
  st.id='zero-adblock';
  st.textContent=css;
  (document.head||document.documentElement).appendChild(st);

  // remove common popup overlays
  function killPop(){
    var sels=['[class*="popup"]','[class*="modal"]','[id*="popup"]','[id*="overlay"]',
      '[class*="fancybox"]','[class*="lightbox"]','.fc-consent-root','.fc-dialog-container',
      '#google_ads_iframe','[id*="taboola"]','[id*="outbrain"]','[class*="taboola"]','[class*="outbrain"]'];
    for(var i=0;i<sels.length;i++){
      try{
        var nodes=document.querySelectorAll(sels[i]);
        for(var j=0;j<nodes.length;j++){
          var n=nodes[j];
          var t=(n.innerText||'').toLowerCase();
          // keep legitimate content modals that look like login/settings
          if(t.indexOf('login')>=0||t.indexOf('masuk')>=0||t.indexOf('daftar')>=0||t.indexOf('cookie')>=0) continue;
          if(n.offsetHeight>80||n.querySelector('iframe')) n.style.setProperty('display','none','important');
        }
      }catch(e){}
    }
  }
  killPop();
  setTimeout(killPop,800);
  setTimeout(killPop,2500);

  // stop window.open used by ads
  try{
    window.open=function(){return null;};
  }catch(e){}
})();
""".trimIndent()
        web.evaluateJavascript(js, null)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(host.windowToken, 0)
    }

    private fun showChat(): ChatPanel {
        val c = chat ?: ChatPanel(this, prefs) { hideDock() }.also { chat = it }
        if (c.parent !== dock) {
            dock.removeAllViews()
            val h = (resources.displayMetrics.heightPixels * prefs.chatHeight / 100f).toInt()
            dock.addView(c, FrameLayout.LayoutParams(MATCH, h))
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
        if (prefs.bubbleDim || prefs.quiet) main.postDelayed(dimJob, 2500)
    }

    private val dimJob = Runnable {
        val full = prefs.bubbleOpacity / 100f
        bubble.alpha = if (prefs.quiet) 0f else if (prefs.bubbleDim) minOf(full, 0.35f) else full
    }

    private fun applyBubble() {
        val lpb = bubble.layoutParams
        lpb.width = dp(prefs.bubbleSize)
        lpb.height = dp(prefs.bubbleSize)
        bubble.layoutParams = lpb
        dock.alpha = prefs.panelOpacity / 100f
        val cc = chat
        if (cc != null && cc.parent === dock) {
            val lpc = cc.layoutParams
            lpc.height = (resources.displayMetrics.heightPixels * prefs.chatHeight / 100f).toInt()
            cc.layoutParams = lpc
        }
        wake()
        host.post { clampBubble() }
    }

    private fun hideDock() {
        hideKeyboard()
        dock.removeAllViews()
        dock.visibility = View.GONE
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
        layer.visibility = View.INVISIBLE
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
                        layer.visibility = View.VISIBLE
                        cb(if (code == PixelCopy.SUCCESS) bmp else null)
                    },
                    main
                )
            } catch (e: Exception) {
                bubble.visibility = View.VISIBLE
                layer.visibility = View.VISIBLE
                cb(null)
            }
        }, 90)
    }

    private fun toggleQuiet() {
        prefs.quiet = !prefs.quiet
        Quiet.sync(this)
        wake()
    }

    private fun showBusy(busy: Boolean) {
        titleView.text = if (busy) "Zero menerjemahkan..." else (web.title ?: "")
    }

    private fun showOriginal() {
        prefs.auto = false
        tr.restore()
        toast("Halaman kembali ke asli. Auto terjemah mati.")
    }

    private fun toggleAuto() {
        prefs.auto = !prefs.auto
        toast(if (prefs.auto) "Auto terjemah nyala. Berhenti scroll, halaman langsung diterjemahkan." else "Auto terjemah mati")
        if (prefs.auto) tr.run()
    }

    private fun scheduleAuto() {
        if (!reader || !prefs.auto) return
        val old = autoJob
        if (old != null) main.removeCallbacks(old)
        val r = Runnable { runAuto() }
        autoJob = r
        main.postDelayed(r, 900)
    }

    private fun runAuto() {
        if (!prefs.auto) return
        if (SystemClock.uptimeMillis() - lastAuto < 700) return
        lastAuto = SystemClock.uptimeMillis()
        tr.run()
    }

    private fun pickMode() {
        val ids = arrayOf(Mode.READER, Mode.OVERLAY, Mode.FILL, Mode.NORMAL)
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
        } else if (m == Mode.NORMAL) {
            startActivity(Intent(this, ChatActivity::class.java))
            finish()
        } else if (m == Mode.FILL) {
            val i = Intent(this, BrowserActivity::class.java)
            i.putExtra("fill_mode", true)
            startActivity(i)
            finish()
        } else {
            if (prefs.url.isBlank()) {
                toast("Alamat web masih kosong")
                return
            }
            recreate()
        }
    }

    data class FormQuestion(
        val id: Int,
        val num: Int,
        val text: String,
        val type: String,
        val options: List<String>,
        val hasImage: Boolean,
        val selector: String,
        val index: Int = 0
    )

    private fun detectQuestions() {
        hideMenu()
        toast("Mendeteksi soal...")
        val js = """
(function(){
  function clean(t){return (t||'').replace(/\s+/g,' ').trim();}
  function isVisible(el){
    if(!el||!el.getBoundingClientRect)return false;
    var r=el.getBoundingClientRect();
    return r.width>0&&r.height>0;
  }
  var out=[];
  var seen={};
  var num=0;

  // 1. Prefer Google Forms listitem / data-item-id / freebird
  var items=document.querySelectorAll('[role="listitem"],[data-item-id],.freebirdFormviewerViewItemsItemItem,.Qr7Oae,.geS5n');
  if(items.length===0){
    // broader: any card-like container that has numbered text + choices
    items=document.querySelectorAll('div[jsname],div[data-params],.freebirdFormviewerViewNumberedItem,.SSPGKf,div');
  }

  function collectFrom(it, forceText){
    var txt=forceText||'';
    if(!txt){
      var h=it.querySelector('[role="heading"],.M7eMe,.freebirdFormviewerViewItemsItemItemTitle,.ItemTitle,h1,h2,h3,span[role="heading"]');
      if(h) txt=clean(h.innerText||h.textContent);
    }
    if(!txt){
      // look for "1. " or "1)" style at start of block
      var raw=clean(it.innerText||'');
      var m=raw.match(/^(\d+)[\.\)]\s+(.{10,220})/);
      if(m) txt=m[1]+'. '+m[2];
      else if(raw.length>12&&raw.length<400) txt=raw.substring(0,220);
    }
    if(!txt||seen[txt]) return null;
    seen[txt]=1;

    var opts=[];
    var sel='';
    var typ='esai';
    var hasImg=it.querySelectorAll('img').length>0;

    // native radios
    var radios=it.querySelectorAll('input[type="radio"]');
    // ARIA radios (Google Forms modern)
    var ariaRadios=it.querySelectorAll('[role="radio"]');
    // native + aria checkboxes
    var checks=it.querySelectorAll('input[type="checkbox"],[role="checkbox"]');
    var texts=it.querySelectorAll('textarea,input[type="text"],input:not([type])');

    if(radios.length>0||ariaRadios.length>0){
      typ='ganda';
      var nodes=radios.length>0?radios:ariaRadios;
      for(var r=0;r<nodes.length;r++){
        var el=nodes[r];
        var lab=el.closest('label')||el.closest('[role="listitem"]')||el.parentElement;
        var ot='';
        if(lab){
          ot=clean(lab.innerText||lab.textContent||'');
          // strip the question text if it leaked
          if(txt&&ot.indexOf(txt.substring(0,20))===0) ot=clean(ot.substring(txt.length));
        }
        if(!ot) ot=clean(el.getAttribute('aria-label')||el.getAttribute('data-value')||el.value||('Opsi '+(r+1)));
        // prefer short option text like "A. -26"
        if(ot.length>80) ot=ot.substring(0,80);
        if(ot) opts.push(ot);
        if(!sel) sel=el.name||el.id||el.getAttribute('data-value')||('r'+r);
      }
    } else if(checks.length>0){
      typ='checkbox';
      for(var c=0;c<checks.length;c++){
        var el2=checks[c];
        var lab2=el2.closest('label')||el2.parentElement;
        var ot2=clean(lab2?(lab2.innerText||lab2.textContent): (el2.getAttribute('aria-label')||el2.value||('Opsi '+(c+1))));
        if(ot2.length>80) ot2=ot2.substring(0,80);
        if(ot2) opts.push(ot2);
        if(!sel) sel=el2.name||el2.id||('c'+c);
      }
    } else if(texts.length>0){
      typ='esai';
      sel=texts[0].name||texts[0].id||('t'+num);
    } else {
      // still accept if it looks like a numbered question even without controls yet
      if(!/^\d+[\.\)]/.test(txt)) return null;
      typ='ganda';
    }
    num++;
    return {id:num,num:num,text:txt,type:typ,options:opts,hasImage:hasImg,selector:sel};
  }

  // First pass: proper containers
  for(var i=0;i<items.length;i++){
    var it=items[i];
    if(!isVisible(it)) continue;
    // skip tiny or header-only
    var r=it.getBoundingClientRect();
    if(r.height<30||r.width<50) continue;
    var q=collectFrom(it);
    if(q) out.push(q);
  }

  // Second pass: if still empty, scan whole body for numbered questions + following A/B/C/D
  if(out.length===0){
    var bodyText=document.body?document.body.innerText:'';
    var blocks=bodyText.split(/\n+/);
    var cur=null;
    for(var b=0;b<blocks.length;b++){
      var line=clean(blocks[b]);
      if(!line) continue;
      var qm=line.match(/^(\d+)[\.\)]\s+(.+)/);
      if(qm){
        if(cur&&cur.options.length>0){num++;out.push({id:num,num:num,text:cur.text,type:'ganda',options:cur.options,hasImage:false,selector:''});}
        cur={text:qm[1]+'. '+qm[2],options:[]};
      } else if(cur){
        var om=line.match(/^[A-Da-d][\.\)]\s*(.+)/);
        if(om) cur.options.push(line);
        else if(/^[A-Da-d]$/.test(line)) cur.options.push(line);
      }
    }
    if(cur&&cur.options.length>0){num++;out.push({id:num,num:num,text:cur.text,type:'ganda',options:cur.options,hasImage:false,selector:''});}
  }

  // Third pass: pure input groups if still empty
  if(out.length===0){
    var allRadios=document.querySelectorAll('input[type="radio"],[role="radio"]');
    var groups={};
    for(var i=0;i<allRadios.length;i++){
      var el=allRadios[i];
      var name=el.name||el.getAttribute('aria-labelledby')||('g'+i);
      if(!groups[name]) groups[name]={opts:[],text:''};
      var lab=el.closest('label')||el.parentElement;
      var ot=clean(lab?(lab.innerText||lab.textContent): (el.getAttribute('aria-label')||el.value||''));
      if(ot) groups[name].opts.push(ot.substring(0,80));
      var parent=el.closest('[role="listitem"],div');
      if(parent&&!groups[name].text){
        var h=parent.querySelector('[role="heading"],span,p,div');
        if(h) groups[name].text=clean(h.innerText||h.textContent).substring(0,200);
      }
    }
    for(var k in groups){
      var g=groups[k];
      if(g.opts.length<2) continue;
      num++;
      out.push({id:num,num:num,text:g.text||('Soal '+num),type:'ganda',options:g.opts,hasImage:false,selector:k});
    }
  }

  return JSON.stringify(out);
})();
""".trimIndent()
        web.evaluateJavascript(js) { raw ->
            main.post {
                try {
                    val s = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: "[]"
                    val arr = JSONArray(s)
                    val list = ArrayList<FormQuestion>()
                    var ganda = 0
                    var esai = 0
                    var gambar = 0
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val opts = ArrayList<String>()
                        val oa = o.optJSONArray("options")
                        if (oa != null) for (j in 0 until oa.length()) opts.add(oa.getString(j))
                        var t = o.optString("type", "esai").lowercase()
                        // normalize type from options
                        if (opts.size >= 2 && (t == "esai" || t.isBlank())) t = "ganda"
                        if (opts.isEmpty() && t == "ganda") t = "esai"
                        if (t == "radio") t = "ganda"
                        val hasImg = o.optBoolean("hasImage", false)
                        when {
                            t == "ganda" || t == "checkbox" -> ganda++
                            else -> {
                                t = "esai"
                                esai++
                            }
                        }
                        if (hasImg) gambar++
                        list.add(FormQuestion(
                            o.optInt("id", i + 1),
                            o.optInt("num", i + 1),
                            o.optString("text", "Soal ${i + 1}"),
                            t,
                            opts,
                            hasImg,
                            o.optString("selector", ""),
                            i
                        ))
                    }
                    questions = list
                    selected.clear()
                    if (list.isEmpty()) {
                        toast("Tidak menemukan soal. Scroll halaman penuh lalu coba deteksi lagi.")
                        return@post
                    }
                    showSelectPanel(ganda, esai, gambar)
                } catch (e: Exception) {
                    toast("Gagal deteksi: ${e.message}")
                }
            }
        }
    }

    private fun showSelectPanel(ganda: Int, esai: Int, gambar: Int) {
        dock.removeAllViews()
        val sc = android.widget.ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(10), dp(8), dp(10), dp(8))

        val summary = tv("Terdeteksi: $ganda ganda, $esai esai, $gambar gambar (total ${questions.size})", 14f, Pal.text, Fonts.head)
        summary.setPadding(0, 0, 0, dp(4))
        col.addView(summary)

        val countTv = tv("Dicentang: 0  |  Dilewati: ${questions.size}", 13f, Pal.accent, Fonts.medium)
        countTv.setPadding(0, 0, 0, dp(8))
        col.addView(countTv)

        fun refreshCount() {
            val n = selected.size
            countTv.text = "Dicentang: $n  |  Dilewati: ${questions.size - n}"
        }

        val note = tv("Centang nomor yang mau diisi. Yang tidak dicentang akan dilewati.", 13f, Pal.dim, Fonts.note)
        note.setPadding(0, 0, 0, dp(8))
        col.addView(note)

        val typeLabel = mapOf("ganda" to "Ganda", "checkbox" to "Centang", "esai" to "Esai")
        for (q in questions) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), dp(6), dp(8), dp(6))
            skin(row, Pal.panel, 3)

            val cb = android.widget.CheckBox(this)
            cb.isChecked = false
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(q.num) else selected.remove(q.num)
                refreshCount()
            }
            row.addView(cb)

            val kind = typeLabel[q.type] ?: q.type
            val img = if (q.hasImage) " +gambar" else ""
            val label = tv("${q.num}. [$kind$img] ${q.text.take(55)}${if (q.text.length > 55) "…" else ""}", 13f, Pal.text, Fonts.body)
            label.setPadding(dp(6), 0, 0, 0)
            row.addView(label, LinearLayout.LayoutParams(0, WRAP, 1f))
            col.addView(row, lp(MATCH, WRAP, 0, 0, 0, 6))
        }

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        val fillBtn = button("Isi yang dicentang", true) {
            if (selected.isEmpty()) {
                toast("Centang minimal satu soal")
                return@button
            }
            fillSelected()
        }
        btnRow.addView(fillBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        val closeBtn = button("Tutup", false) { hideDock() }
        btnRow.addView(closeBtn, lp(WRAP, WRAP, 8, 0, 0, 0))
        col.addView(btnRow, lp(MATCH, WRAP, 0, 10, 0, 0))

        sc.addView(col)
        dock.addView(sc, FrameLayout.LayoutParams(MATCH, dp(360)))
        dock.visibility = View.VISIBLE
        dock.alpha = prefs.panelOpacity / 100f
    }

    private fun fillSelected() {
        hideDock()
        val toFill = questions.filter { selected.contains(it.num) }
        if (toFill.isEmpty()) return
        toast("AI sedang menjawab ${toFill.size} soal...")
        val sb = StringBuilder()
        sb.append("Jawab soal berikut. Balas HANYA JSON array tanpa markdown, format:\n")
        sb.append("[{\"num\":1,\"type\":\"ganda\",\"answer\":\"teks opsi yang dipilih atau huruf\"},{\"num\":2,\"type\":\"esai\",\"answer\":\"jawaban esai\"}]\n\n")
        for (q in toFill) {
            sb.append("Soal ${q.num} (${q.type}): ${q.text}\n")
            if (q.options.isNotEmpty()) {
                sb.append("Opsi: ${q.options.joinToString(" | ")}\n")
            }
            if (q.hasImage) sb.append("(soal ini punya gambar)\n")
            sb.append("\n")
        }
        val turn = Gemini.Turn("user", sb.toString())
        Gemini.ask(prefs, "Kamu asisten yang mengisi form. Jawab akurat, singkat untuk pilihan ganda, lengkap untuk esai. Hanya output JSON array valid.", listOf(turn)) { result ->
            result.onSuccess { text ->
                main.post {
                    try {
                        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val arr = JSONArray(clean)
                        val answers = HashMap<Int, String>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            answers[o.optInt("num")] = o.optString("answer", "")
                        }
                        applyAnswers(toFill, answers)
                    } catch (e: Exception) {
                        toast("Gagal parse jawaban AI: ${e.message}")
                    }
                }
            }.onFailure { e ->
                main.post { toast("Gagal AI: ${e.message}") }
            }
        }
    }

    private fun applyAnswers(qs: List<FormQuestion>, answers: Map<Int, String>) {
        // only checked questions, already filtered; process one by one in order
        val ordered = qs.sortedBy { it.num }
        var done = 0
        fun next(i: Int) {
            if (i >= ordered.size) {
                toast("Selesai mengisi $done dari ${ordered.size} soal")
                return
            }
            val q = ordered[i]
            val ans = answers[q.num]?.trim().orEmpty()
            if (ans.isBlank()) {
                // skip empty answer, go to next checked
                main.postDelayed({ next(i + 1) }, 200)
                return
            }
            toast("Soal ${q.num}: mengisi...")
            val qText = q.text.take(40).replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
            val escaped = ans.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\"", "\\\"")
            val optsJson = q.options.joinToString(",") { "'${it.replace("'", "\\'").take(60)}'" }
            val js = """
(function(){
  var qNum = ${q.num};
  var qHint = '$qText'.toLowerCase();
  var ans = '$escaped';
  var ansLow = ans.toLowerCase().replace(/^\s*[a-d][\.\)]\s*/i,'').trim();
  var letter = '';
  var m = ans.match(/^([a-d])[\.\)]?\s*/i);
  if(m) letter = m[1].toUpperCase();
  var opts = [$optsJson];
  var typ = '${q.type}';

  function clickEl(el){
    if(!el) return false;
    try{ el.scrollIntoView({behavior:'smooth',block:'center'}); }catch(e){}
    try{
      if(el.focus) el.focus();
      if(el.click) el.click();
      if(el.checked !== undefined){ el.checked = true; }
      el.dispatchEvent(new Event('input',{bubbles:true}));
      el.dispatchEvent(new Event('change',{bubbles:true}));
      el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));
    }catch(e){}
    return true;
  }

  // find container that looks like this question (by number or text)
  function findBlock(){
    var items = document.querySelectorAll('[role="listitem"],[data-item-id],.freebirdFormviewerViewItemsItemItem,.Qr7Oae,.geS5n,div[jsname]');
    var best = null;
    for(var i=0;i<items.length;i++){
      var t = (items[i].innerText||'').toLowerCase().replace(/\s+/g,' ');
      if(!t) continue;
      if(t.indexOf(qNum+'.')===0 || t.indexOf(qNum+')')===0 || t.indexOf(qNum+'. ')>=0){
        if(t.length < 800) return items[i];
        if(!best) best = items[i];
      }
      if(qHint.length>8 && t.indexOf(qHint.substring(0,Math.min(24,qHint.length)))>=0){
        if(!best || t.length < (best.innerText||'').length) best = items[i];
      }
    }
    return best;
  }

  var block = findBlock();
  if(block){
    try{ block.scrollIntoView({behavior:'smooth',block:'center'}); }catch(e){}
  }
  var root = block || document.body;

  if(typ === 'ganda' || typ === 'checkbox'){
    // only search inside this question block
    var radios = root.querySelectorAll('input[type="radio"],input[type="checkbox"],[role="radio"],[role="checkbox"]');
    if(radios.length===0 && block) radios = block.querySelectorAll('label,div[role="listitem"] span,div[role="listitem"] div');
    // match by letter index within THIS block only
    if(letter && radios.length>0){
      var idx = letter.charCodeAt(0)-65;
      if(idx>=0 && idx<radios.length) return clickEl(radios[idx]);
    }
    for(var i=0;i<radios.length;i++){
      var el = radios[i];
      var lab = el.closest('label') || el.parentElement || el;
      var t = ((lab.innerText||lab.textContent||el.getAttribute('aria-label')||'')+'').toLowerCase().replace(/\s+/g,' ').trim();
      if(t.length<1) continue;
      if(letter && (t.indexOf(letter.toLowerCase()+'.')===0 || t.indexOf(letter.toLowerCase()+')')===0 || t===letter.toLowerCase())){
        return clickEl(el);
      }
      if(ansLow && (t.indexOf(ansLow)>=0 || ansLow.indexOf(t.substring(0,Math.min(15,t.length)))>=0)){
        return clickEl(el);
      }
      for(var o=0;o<opts.length;o++){
        var op = (opts[o]||'').toLowerCase();
        if(op && t.indexOf(op)>=0) return clickEl(el);
      }
    }
    // last resort: labels inside block matching A/B/C/D text
    var labs = root.querySelectorAll('label,span,div');
    for(var i=0;i<labs.length;i++){
      var t = ((labs[i].innerText||'')+'').toLowerCase().replace(/\s+/g,' ').trim();
      if(t.length<1||t.length>100) continue;
      if(letter && (t.indexOf(letter.toLowerCase()+'.')===0 || t.indexOf(letter.toLowerCase()+')')===0)){
        return clickEl(labs[i]);
      }
      if(ansLow && t.indexOf(ansLow)>=0) return clickEl(labs[i]);
    }
    return false;
  }

  // essay / text
  var fields = root.querySelectorAll('textarea,input[type="text"],input:not([type]),div[contenteditable="true"],div[role="textbox"]');
  if(fields.length===0) fields = document.querySelectorAll('textarea,input[type="text"]');
  for(var i=0;i<fields.length;i++){
    var el = fields[i];
    // prefer empty field near this question
    if(el.tagName==='TEXTAREA' || el.tagName==='INPUT' || el.isContentEditable){
      try{ el.scrollIntoView({behavior:'smooth',block:'center'}); }catch(e){}
      el.focus();
      if(el.isContentEditable){ el.innerText = ans; }
      else { el.value = ans; }
      el.dispatchEvent(new Event('input',{bubbles:true}));
      el.dispatchEvent(new Event('change',{bubbles:true}));
      return true;
    }
  }
  return false;
})();
""".trimIndent()
            web.evaluateJavascript(js) { ok ->
                main.post {
                    if (ok == "true") done++
                    // wait for scroll + click to settle before next question
                    main.postDelayed({ next(i + 1) }, 900)
                }
            }
        }
        next(0)
    }

    override fun onBackPressed() {
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
        tr.rescale()
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
}
