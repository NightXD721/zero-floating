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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tr.reset()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                titleView.text = view?.title ?: url ?: ""
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
                Entry("Tanya Zero") { showChat().focusInput() },
                Entry("Model dan cara kerja") { showSettings(SettingsPanel.MODEL or SettingsPanel.MODE) },
                Entry("Tampilan bubble") { showSettings(SettingsPanel.BUBBLE) },
                Entry(if (prefs.quiet) "Mode senyap: nyala" else "Mode senyap: mati") { toggleQuiet() },
                Entry("Ganti mode") { pickMode() }
            )
        }
        return listOf(
            Entry("Terjemah halaman") { tr.run() },
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
        val selector: String
    )

    private fun detectQuestions() {
        hideMenu()
        toast("Mendeteksi soal...")
        val js = """
(function(){
  function clean(t){return (t||'').replace(/\s+/g,' ').trim();}
  var out = [];
  var seen = {};
  var num = 0;
  // Google Forms: listitems or data-item-id
  var items = document.querySelectorAll('[role="listitem"], [data-item-id], .freebirdFormviewerViewItemsItemItem, .Qr7Oae');
  if (items.length === 0) {
    // fallback: group by nearby inputs
    var inputs = document.querySelectorAll('input[type="radio"], input[type="checkbox"], input[type="text"], textarea, select');
    var groups = {};
    for (var i=0; i<inputs.length; i++) {
      var el = inputs[i];
      var name = el.name || el.id || ('i'+i);
      if (!groups[name]) groups[name] = {els:[], text:'', type: el.type||'text', opts:[]};
      groups[name].els.push(el);
      if (el.type === 'radio' || el.type === 'checkbox') {
        var lab = el.closest('label') || document.querySelector('label[for="'+el.id+'"]');
        var ot = clean(lab ? lab.innerText : el.value);
        if (ot) groups[name].opts.push(ot);
      }
      var q = el.closest('[role="listitem"], .freebirdFormviewerViewItemsItemItem, div') || el.parentElement;
      if (q) {
        var h = q.querySelector('[role="heading"], .freebirdFormviewerViewItemsItemItemTitle, h1, h2, h3, label');
        if (h) groups[name].text = clean(h.innerText || h.textContent);
      }
    }
    for (var k in groups) {
      var g = groups[k];
      if (!g.text && g.opts.length===0) continue;
      num++;
      var typ = g.type;
      if (typ === 'radio') typ = 'ganda';
      else if (typ === 'checkbox') typ = 'checkbox';
      else if (typ === 'textarea') typ = 'esai';
      else typ = 'esai';
      out.push({id:num, num:num, text:g.text||('Soal '+num), type:typ, options:g.opts, hasImage:false, selector:k});
    }
  } else {
    for (var i=0; i<items.length; i++) {
      var it = items[i];
      var txt = '';
      var h = it.querySelector('[role="heading"], .M7eMe, .freebirdFormviewerViewItemsItemItemTitle, .ItemTitle, h1, h2, h3');
      if (h) txt = clean(h.innerText || h.textContent);
      if (!txt) {
        var all = clean(it.innerText || '');
        if (all.length > 8) txt = all.substring(0, 200);
      }
      if (!txt || seen[txt]) continue;
      seen[txt] = 1;
      var radios = it.querySelectorAll('input[type="radio"]');
      var checks = it.querySelectorAll('input[type="checkbox"]');
      var texts = it.querySelectorAll('input[type="text"], input:not([type]), textarea');
      var imgs = it.querySelectorAll('img');
      var typ = 'esai';
      var opts = [];
      var sel = '';
      if (radios.length > 0) {
        typ = 'ganda';
        for (var r=0; r<radios.length; r++) {
          var lab = radios[r].closest('label') || it.querySelector('label[for="'+radios[r].id+'"]');
          opts.push(clean(lab ? lab.innerText : (radios[r].value||('Opsi '+(r+1)))));
          if (!sel) sel = radios[r].name || radios[r].id;
        }
      } else if (checks.length > 0) {
        typ = 'checkbox';
        for (var c=0; c<checks.length; c++) {
          var lab2 = checks[c].closest('label') || it.querySelector('label[for="'+checks[c].id+'"]');
          opts.push(clean(lab2 ? lab2.innerText : (checks[c].value||('Opsi '+(c+1)))));
          if (!sel) sel = checks[c].name || checks[c].id;
        }
      } else if (texts.length > 0) {
        typ = texts[0].tagName === 'TEXTAREA' ? 'esai' : 'esai';
        sel = texts[0].name || texts[0].id || ('t'+i);
      } else {
        continue;
      }
      num++;
      out.push({id:num, num:num, text:txt, type:typ, options:opts, hasImage:imgs.length>0, selector:sel});
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
                        val t = o.optString("type", "esai")
                        val hasImg = o.optBoolean("hasImage", false)
                        if (t == "ganda" || t == "checkbox") ganda++ else esai++
                        if (hasImg) gambar++
                        list.add(FormQuestion(
                            o.optInt("id", i + 1),
                            o.optInt("num", i + 1),
                            o.optString("text", "Soal ${i + 1}"),
                            t,
                            opts,
                            hasImg,
                            o.optString("selector", "")
                        ))
                    }
                    questions = list
                    selected.clear()
                    if (list.isEmpty()) {
                        toast("Tidak menemukan soal. Pastikan halaman Form/Docs sudah terbuka penuh.")
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

        val summary = tv("Terdeteksi: $ganda ganda/checkbox, $esai esai, $gambar gambar (total ${questions.size})", 14f, Pal.text, Fonts.head)
        summary.setPadding(0, 0, 0, dp(8))
        col.addView(summary)

        val note = tv("Centang nomor yang mau diisi, lalu tekan Isi.", 13f, Pal.dim, Fonts.note)
        note.setPadding(0, 0, 0, dp(8))
        col.addView(note)

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
            }
            row.addView(cb)

            val label = tv("${q.num}. [${q.type}] ${q.text.take(60)}${if (q.text.length > 60) "…" else ""}", 13f, Pal.text, Fonts.body)
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
        dock.addView(sc, FrameLayout.LayoutParams(MATCH, dp(320)))
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
        var done = 0
        fun next(i: Int) {
            if (i >= qs.size) {
                toast("Selesai mengisi $done soal")
                return
            }
            val q = qs[i]
            val ans = answers[q.num] ?: ""
            if (ans.isBlank()) {
                next(i + 1)
                return
            }
            toast("Mengisi soal ${q.num}...")
            val escaped = ans.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\"", "\\\"")
            val js = when (q.type) {
                "ganda", "checkbox" -> """
(function(){
  var name = '${q.selector}';
  var ans = '$escaped'.toLowerCase();
  var els = document.querySelectorAll('input[name="'+name+'"], input[id="'+name+'"]');
  if (els.length===0) els = document.querySelectorAll('input[type="radio"], input[type="checkbox"]');
  for (var i=0;i<els.length;i++){
    var el = els[i];
    var lab = el.closest('label') || document.querySelector('label[for="'+el.id+'"]');
    var t = (lab ? lab.innerText : (el.value||'')).toLowerCase();
    if (t.indexOf(ans) >= 0 || ans.indexOf(t) >= 0 || t === ans) {
      el.checked = true;
      el.click();
      el.dispatchEvent(new Event('change', {bubbles:true}));
      el.scrollIntoView({behavior:'smooth', block:'center'});
      return true;
    }
  }
  // try by index if answer is A/B/C or 1/2/3
  var idx = -1;
  if (/^[a-d]$/i.test(ans)) idx = ans.toUpperCase().charCodeAt(0) - 65;
  else if (/^\d+$/.test(ans)) idx = parseInt(ans,10) - 1;
  if (idx >= 0 && els[idx]) {
    els[idx].checked = true;
    els[idx].click();
    els[idx].dispatchEvent(new Event('change', {bubbles:true}));
    els[idx].scrollIntoView({behavior:'smooth', block:'center'});
    return true;
  }
  return false;
})();
""".trimIndent()
                else -> """
(function(){
  var name = '${q.selector}';
  var ans = '$escaped';
  var el = document.querySelector('textarea[name="'+name+'"], input[name="'+name+'"], textarea[id="'+name+'"], input[id="'+name+'"]');
  if (!el) el = document.querySelector('textarea, input[type="text"]');
  if (el) {
    el.focus();
    el.value = ans;
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
    el.scrollIntoView({behavior:'smooth', block:'center'});
    return true;
  }
  return false;
})();
""".trimIndent()
            }
            web.evaluateJavascript(js) { ok ->
                main.post {
                    if (ok == "true") done++
                    main.postDelayed({ next(i + 1) }, 600)
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
