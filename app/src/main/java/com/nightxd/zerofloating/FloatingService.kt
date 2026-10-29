package com.nightxd.zerofloating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var ui: Context
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())

    private var grabber: ScreenGrabber? = null
    private var bubble: Mark? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var menu: LinearLayout? = null
    private var chat: ChatPanel? = null
    private var card: ResultCard? = null
    private var selection: SelectionView? = null
    private var settings: SettingsPanel? = null
    private var settingsOn = false
    private var panelLp: WindowManager.LayoutParams? = null
    private var cardLp: WindowManager.LayoutParams? = null
    private var settingsLp: WindowManager.LayoutParams? = null
    private val dimJob = Runnable {
        val full = prefs.bubbleOpacity / 100f
        bubble?.alpha = if (prefs.bubbleDim) minOf(full, 0.35f) else full
    }
    private var panelOn = false
    private var cardOn = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ui = ContextThemeWrapper(this, R.style.Theme_Zero)
        prefs = Prefs(this)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        val note = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTE_ID, note)
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izin overlay belum aktif", Toast.LENGTH_LONG).show()
            shutdown()
            return START_NOT_STICKY
        }

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (intent == null) {
            null
        } else if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (data != null && grabber == null) {
            try {
                grabber = ScreenGrabber(this, code, data) {
                    Toast.makeText(this, "Tangkap layar berhenti. Mulai lagi dari aplikasi Zero.", Toast.LENGTH_LONG).show()
                    shutdown()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memulai tangkap layar", Toast.LENGTH_LONG).show()
                shutdown()
                return START_NOT_STICKY
            }
        }

        if (grabber == null) {
            shutdown()
            return START_NOT_STICKY
        }

        if (bubble == null) showBubble()
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Zero Floating", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stopIntent = Intent(this, FloatingService::class.java)
        stopIntent.action = ACTION_STOP
        val stop = PendingIntent.getService(this, 1, stopIntent, flags)
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_zero), "Matikan", stop
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_zero)
            .setContentTitle("Zero Floating aktif")
            .setContentText("Menu mengambang siap dipakai")
            .setContentIntent(open)
            .addAction(action)
            .setOngoing(true)
            .build()
    }

    private fun params(w: Int, h: Int, focusable: Boolean, watchOutside: Boolean = false): WindowManager.LayoutParams {
        var f = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!focusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (watchOutside) f = f or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val p = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, f, PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        return p
    }

    private fun safeAdd(v: View, p: WindowManager.LayoutParams): Boolean {
        return try {
            wm.addView(v, p)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun safeRemove(v: View?) {
        if (v == null) return
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) {
        }
    }

    private fun clampBubble(lp: WindowManager.LayoutParams) {
        val s = Screen.real(this)
        lp.x = lp.x.coerceIn(0, maxOf(0, s[0] - lp.width))
        lp.y = lp.y.coerceIn(0, maxOf(0, s[1] - lp.height))
    }

    private fun showBubble() {
        val size = ui.dp(prefs.bubbleSize)
        val v = Mark(ui)
        val s = Screen.real(this)
        val lp = params(size, size, false)
        lp.x = if (prefs.bubbleX < 0) s[0] - size - ui.dp(8) else prefs.bubbleX
        lp.y = if (prefs.bubbleY < 0) s[1] / 3 else prefs.bubbleY
        clampBubble(lp)
        attachDrag(v, lp)
        if (safeAdd(v, lp)) {
            bubble = v
            bubbleLp = lp
            wake()
        } else {
            Toast.makeText(this, "Gagal menampilkan bubble", Toast.LENGTH_LONG).show()
            shutdown()
        }
    }

    private fun attachDrag(v: View, lp: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    ox = lp.x
                    oy = lp.y
                    moved = false
                    wake()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        hideMenu()
                    }
                    if (moved) {
                        lp.x = ox + dx.toInt()
                        lp.y = oy + dy.toInt()
                        clampBubble(lp)
                        try {
                            wm.updateViewLayout(v, lp)
                        } catch (ex: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        if (prefs.bubbleSnap) {
                            val sc = Screen.real(this)
                            lp.x = if (lp.x + lp.width / 2 < sc[0] / 2) 0 else sc[0] - lp.width
                            try {
                                wm.updateViewLayout(v, lp)
                            } catch (ex: Exception) {
                            }
                        }
                        prefs.bubbleX = lp.x
                        prefs.bubbleY = lp.y
                    } else {
                        toggleMenu()
                    }
                    wake()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleMenu() {
        if (menu != null) hideMenu() else showMenu()
    }

    private fun hideMenu() {
        safeRemove(menu)
        menu = null
    }

    private fun showMenu() {
        val lpB = bubbleLp ?: return
        val items = listOf(
            Entry("Tanya Zero") { showPanel() },
            Entry("Foto lalu tanya") { showPanel().quickPick("camera") },
            Entry("Crop lalu tanya") { beginCapture(true, K_ASK) },
            Entry("Tanya layar penuh") { beginCapture(false, K_ASK) },
            Entry("Crop lalu terjemah") { beginCapture(true, K_TR) },
            Entry("Terjemah layar penuh") { beginCapture(false, K_TR) },
            Entry("Model dan cara kerja") { showSettings(SettingsPanel.MODEL or SettingsPanel.MODE) },
            Entry("Tampilan bubble") { showSettings(SettingsPanel.BUBBLE) },
            Entry("Matikan bubble", true) { shutdown() }
        )
        val col = ui.menuColumn(items) { hideMenu() }
        col.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideMenu()
                true
            } else {
                false
            }
        }
        col.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val s = Screen.real(this)
        val gap = ui.dp(8)
        val mw = col.measuredWidth
        val mh = col.measuredHeight
        val onRight = lpB.x + lpB.width / 2 > s[0] / 2
        val x = if (onRight) lpB.x - mw - gap else lpB.x + lpB.width + gap
        val p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false, true)
        p.x = x.coerceIn(gap, maxOf(gap, s[0] - mw - gap))
        p.y = lpB.y.coerceIn(gap, maxOf(gap, s[1] - mh - gap))
        if (safeAdd(col, p)) menu = col
    }

    private fun widthPx(sc: IntArray): Int {
        val lo = ui.dp(220)
        val w = (sc[0] * prefs.panelWidth / 100f).toInt()
        return w.coerceIn(lo, maxOf(lo, minOf(sc[0], ui.dp(640))))
    }

    private fun chatH(sc: IntArray): Int {
        val h = (sc[1] * prefs.chatHeight / 100f).toInt()
        return h.coerceIn(ui.dp(160), maxOf(ui.dp(160), (sc[1] * 0.9f).toInt()))
    }

    private fun yFor(pos: String, h: Int, sc: IntArray): Int {
        return when (pos) {
            "mid" -> ((sc[1] - h) / 2).coerceAtLeast(0)
            "bottom" -> (sc[1] - h - ui.dp(72)).coerceAtLeast(0)
            else -> ui.dp(44)
        }
    }

    private fun panelParams(w: Int, h: Int, y: Int, focusable: Boolean): WindowManager.LayoutParams {
        val sc = Screen.real(this)
        val p = params(w, h, focusable)
        p.x = (sc[0] - w) / 2
        p.y = y
        return p
    }

    private fun upd(v: View, lp: WindowManager.LayoutParams) {
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) {
        }
    }

    private fun dragPanel(handle: View, panel: View, lp: WindowManager.LayoutParams) {
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    ox = lp.x
                    oy = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val sc = Screen.real(this)
                    lp.x = (ox + (e.rawX - sx)).toInt().coerceIn(0, maxOf(0, sc[0] - lp.width))
                    lp.y = (oy + (e.rawY - sy)).toInt().coerceIn(0, maxOf(0, sc[1] - ui.dp(80)))
                    try {
                        wm.updateViewLayout(panel, lp)
                    } catch (ex: Exception) {
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun applyPanels() {
        val a = prefs.panelOpacity / 100f
        chat?.alpha = a
        card?.alpha = a
        settings?.alpha = a
        relayout()
    }

    private fun relayout() {
        val sc = Screen.real(this)
        val w = widthPx(sc)
        val cl = panelLp
        val c = chat
        if (panelOn && cl != null && c != null) {
            cl.width = w
            cl.height = chatH(sc)
            cl.x = (sc[0] - w) / 2
            cl.y = yFor(prefs.panelPos, cl.height, sc)
            upd(c, cl)
        }
        val kl = cardLp
        val k = card
        if (cardOn && kl != null && k != null) {
            k.applyStyle()
            kl.width = w
            kl.x = (sc[0] - w) / 2
            kl.y = yFor(prefs.cardPos, k.capPx() + ui.dp(64), sc)
            upd(k, kl)
        }
        val sl = settingsLp
        val st = settings
        if (settingsOn && sl != null && st != null) {
            sl.width = w
            sl.x = (sc[0] - w) / 2
            upd(st, sl)
        }
    }

    private fun resizePanel(grip: View, panel: View, lp: WindowManager.LayoutParams, kind: Int) {
        var sx = 0f
        var sy = 0f
        var w0 = 0
        var h0 = 0
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    w0 = lp.width
                    h0 = if (kind == 2) (card?.capPx() ?: 0) else lp.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val sc = Screen.real(this)
                    val lo = ui.dp(200)
                    lp.width = (w0 + (e.rawX - sx)).toInt().coerceIn(lo, maxOf(lo, sc[0] - lp.x))
                    val nh = (h0 + (e.rawY - sy)).toInt()
                    if (kind == 2) {
                        card?.setCapPx(nh.coerceIn(ui.dp(60), maxOf(ui.dp(60), sc[1] / 2)))
                    } else {
                        val lh = ui.dp(160)
                        lp.height = nh.coerceIn(lh, maxOf(lh, sc[1] - lp.y))
                    }
                    upd(panel, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val sc = Screen.real(this)
                    prefs.panelWidth = (lp.width * 100 / sc[0]).coerceIn(40, 100)
                    if (kind == 0) prefs.chatHeight = (lp.height * 100 / sc[1]).coerceIn(25, 90)
                    if (kind == 2) prefs.cardHeight = ((card?.capPx() ?: 0) * 100 / sc[1]).coerceIn(10, 60)
                    true
                }
                else -> true
            }
        }
    }

    private fun wake() {
        main.removeCallbacks(dimJob)
        val full = prefs.bubbleOpacity / 100f
        bubble?.alpha = full
        if (prefs.bubbleDim) main.postDelayed(dimJob, 2500)
    }

    private fun applyBubble() {
        val v = bubble
        val lp = bubbleLp
        if (v != null && lp != null) {
            val size = ui.dp(prefs.bubbleSize)
            lp.width = size
            lp.height = size
            clampBubble(lp)
            try {
                wm.updateViewLayout(v, lp)
            } catch (e: Exception) {
            }
        }
        wake()
        applyPanels()
    }

    private fun chatPanel(): ChatPanel {
        val existing = chat
        if (existing != null) return existing
        val c = ChatPanel(ui, prefs) { hidePanel() }
        chat = c
        return c
    }

    private fun showPanel(): ChatPanel {
        hideResult()
        hideSettings()
        val c = chatPanel()
        if (!panelOn) {
            val sc = Screen.real(this)
            val h = chatH(sc)
            val p = panelParams(widthPx(sc), h, yFor(prefs.panelPos, h, sc), true)
            p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (safeAdd(c, p)) {
                panelOn = true
                panelLp = p
                dragPanel(c.handle, c, p)
                resizePanel(c.enableResize(), c, p, 0)
                applyPanels()
            }
        }
        return c
    }

    private fun hidePanel() {
        if (!panelOn) return
        safeRemove(chat)
        panelOn = false
    }

    private fun resultCard(): ResultCard {
        val existing = card
        if (existing != null) return existing
        val c = ResultCard(ui, false) { hideResult() }
        card = c
        return c
    }

    private fun showResult(): ResultCard {
        hidePanel()
        hideSettings()
        val c = resultCard()
        if (!cardOn) {
            c.applyStyle()
            val sc = Screen.real(this)
            val y = yFor(prefs.cardPos, c.capPx() + ui.dp(64), sc)
            val p = panelParams(widthPx(sc), WindowManager.LayoutParams.WRAP_CONTENT, y, false)
            if (safeAdd(c, p)) {
                cardOn = true
                cardLp = p
                dragPanel(c.handle, c, p)
                resizePanel(c.enableResize(), c, p, 2)
                applyPanels()
            }
        }
        return c
    }

    private fun hideResult() {
        if (!cardOn) return
        safeRemove(card)
        cardOn = false
    }

    private fun showSettings(parts: Int) {
        hidePanel()
        hideResult()
        hideSettings()
        val sc = Screen.real(this)
        val h = (sc[1] * (if (sc[0] > sc[1]) 0.85f else 0.6f)).toInt()
        val v = SettingsPanel(ui, prefs, parts, { applyBubble() }, { hideSettings() })
        val p = panelParams(widthPx(sc), h, ui.dp(44), false)
        if (safeAdd(v, p)) {
            settings = v
            settingsLp = p
            settingsOn = true
            dragPanel(v.handle, v, p)
            resizePanel(v.enableResize(), v, p, 1)
            applyPanels()
        }
    }

    private fun hideSettings() {
        if (!settingsOn) return
        safeRemove(settings)
        settings = null
        settingsOn = false
    }

    private fun beginCapture(area: Boolean, kind: Int) {
        val g = grabber
        if (g == null || !g.active) {
            Toast.makeText(this, "Tangkap layar belum aktif. Mulai ulang dari aplikasi Zero.", Toast.LENGTH_LONG).show()
            return
        }
        hidePanel()
        hideResult()
        hideSettings()
        if (area) selectArea(kind) else grab(null, kind)
    }

    private fun selectArea(kind: Int) {
        safeRemove(selection)
        selection = null
        bubble?.visibility = View.INVISIBLE
        val sv = SelectionView(ui) { r: RectF? ->
            val v = selection
            val loc = IntArray(2)
            v?.getLocationOnScreen(loc)
            main.post {
                safeRemove(selection)
                selection = null
            }
            if (r == null) {
                bubble?.visibility = View.VISIBLE
            } else {
                val rect = Rect(
                    r.left.toInt() + loc[0],
                    r.top.toInt() + loc[1],
                    r.right.toInt() + loc[0],
                    r.bottom.toInt() + loc[1]
                )
                grab(rect, kind)
            }
        }
        val p = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false)
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (safeAdd(sv, p)) {
            selection = sv
        } else {
            bubble?.visibility = View.VISIBLE
            Toast.makeText(this, "Gagal membuka pemilih area", Toast.LENGTH_LONG).show()
        }
    }

    private fun grab(rect: Rect?, kind: Int) {
        val g = grabber
        if (g == null) {
            bubble?.visibility = View.VISIBLE
            return
        }
        bubble?.visibility = View.INVISIBLE
        g.grab { bmp ->
            bubble?.visibility = View.VISIBLE
            if (bmp == null) {
                Toast.makeText(this, "Gagal menangkap layar. Coba lagi.", Toast.LENGTH_LONG).show()
            } else {
                val out: Bitmap = if (rect != null) Pix.crop(bmp, rect) else bmp
                if (kind == K_ASK) {
                    showPanel().ask(Zero.askImage(), out, "Gambar dari layar")
                } else {
                    showResult().translateImage(prefs, out)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideMenu()
        val v = bubble
        val lp = bubbleLp
        if (v != null && lp != null) {
            clampBubble(lp)
            try {
                wm.updateViewLayout(v, lp)
            } catch (e: Exception) {
            }
        }
    }

    private fun shutdown() {
        hideMenu()
        hidePanel()
        hideResult()
        hideSettings()
        safeRemove(selection)
        selection = null
        safeRemove(bubble)
        bubble = null
        grabber?.release()
        grabber = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(dimJob)
        hideMenu()
        hidePanel()
        hideResult()
        hideSettings()
        safeRemove(selection)
        safeRemove(bubble)
        grabber?.release()
        grabber = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nightxd.zerofloating.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "zero_floating"
        private const val NOTE_ID = 4207
        private const val K_ASK = 0
        private const val K_TR = 1

        @Volatile
        var running = false
    }
}
