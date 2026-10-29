package com.nightxd.zerofloating

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

class PageTranslator(
    private val act: Activity,
    private val prefs: Prefs,
    private val web: WebView,
    private val layer: TransLayer,
    private val capture: ((Bitmap?) -> Unit) -> Unit,
    private val onState: (Boolean) -> Unit
) {
    private var epoch = 0
    private var jobs = 0
    private var textBusy = false
    private var imgBusy = false
    private var fullBusy = false
    private var lastErr = 0L
    private var warned = false

    fun reset() {
        epoch++
        jobs = 0
        textBusy = false
        imgBusy = false
        fullBusy = false
        layer.clear()
        onState(false)
    }

    fun restore() {
        reset()
        script("window.__zero.back()")
    }

    fun rescale() {
        layer.clear()
        script("window.__zero.ri()")
    }

    fun run() {
        text(0)
        images()
    }

    /** Translate every text node on the page (not just near viewport). Multi-pass until empty. */
    fun runFull() {
        fullRound(0)
    }

    private fun fullRound(round: Int) {
        if (fullBusy && round == 0) return
        fullBusy = true
        val ep = epoch
        web.evaluateJavascript(PageJs.INSTALL + "window.__zero.scanAll(" + FULL_BUDGET + ")") { raw ->
            if (ep != epoch) {
                fullBusy = false
                return@evaluateJavascript
            }
            val arr = try {
                val s = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: "[]"
                JSONArray(s)
            } catch (e: Exception) {
                try { JSONArray(raw ?: "[]") } catch (_: Exception) { null }
            }
            if (arr == null || arr.length() == 0) {
                fullBusy = false
                act.runOnUiThread {
                    Toast.makeText(
                        act,
                        if (round > 0) "Terjemah seluruh halaman selesai" else "Tidak ada teks untuk diterjemahkan",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                // still try images once at the end
                if (round > 0) images()
                return@evaluateJavascript
            }
            act.runOnUiThread {
                Toast.makeText(act, "Menerjemahkan seluruh halaman (${round + 1})...", Toast.LENGTH_SHORT).show()
            }
            onFullScan(ep, round, arr)
        }
    }

    private fun onFullScan(ep: Int, round: Int, arr: JSONArray) {
        if (ep != epoch) {
            fullBusy = false
            return
        }
        val lang = prefs.lang
        val batches = ArrayList<ArrayList<Int>>()
        var cur = ArrayList<Int>()
        var size = 0
        for (k in 0 until arr.length()) {
            val len = try { arr.getJSONArray(k).getString(1).length } catch (_: Exception) { 0 }
            if (cur.isNotEmpty() && (size + len > BATCH_CHARS || cur.size >= BATCH_NODES)) {
                batches.add(cur)
                cur = ArrayList()
                size = 0
            }
            cur.add(k)
            size += len
        }
        if (cur.isNotEmpty()) batches.add(cur)
        if (batches.isEmpty()) {
            fullBusy = false
            return
        }
        // sequential batches to avoid rate-limit and race
        fun runBatch(bi: Int, allOk: Boolean) {
            if (ep != epoch) {
                fullBusy = false
                return
            }
            if (bi >= batches.size) {
                if (allOk && round < FULL_MAX_ROUNDS) {
                    fullRound(round + 1)
                } else {
                    fullBusy = false
                    act.runOnUiThread {
                        Toast.makeText(act, "Terjemah seluruh halaman selesai", Toast.LENGTH_SHORT).show()
                    }
                    images()
                }
                return
            }
            val b = batches[bi]
            val ids = JSONArray()
            val src = JSONArray()
            for (k in b) {
                val row = arr.getJSONArray(k)
                ids.put(row.getInt(0))
                src.put(row.getString(1))
            }
            send(ep, lang, ids, src, 0, BooleanArray(ids.length())) { ok ->
                runBatch(bi + 1, allOk && ok)
            }
        }
        runBatch(0, true)
    }

    private fun script(body: String) {
        web.evaluateJavascript(PageJs.INSTALL + body, null)
    }

    private fun begin(ep: Int) {
        if (ep != epoch) return
        jobs++
        onState(true)
    }

    private fun end(ep: Int) {
        if (ep != epoch) return
        if (jobs > 0) jobs--
        onState(jobs > 0)
    }

    private fun fail(msg: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastErr < 4000) return
        lastErr = now
        Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
    }

    private fun parseArray(s: String): JSONArray? {
        val a = s.indexOf('[')
        val b = s.lastIndexOf(']')
        if (a < 0 || b <= a) return null
        return try {
            JSONArray(s.substring(a, b + 1))
        } catch (e: Exception) {
            null
        }
    }

    private fun text(round: Int) {
        if (textBusy) return
        textBusy = true
        val ep = epoch
        web.evaluateJavascript(PageJs.INSTALL + "window.__zero.scan(" + TEXT_BUDGET + ")") { raw ->
            onScan(ep, round, raw)
        }
    }

    private fun onScan(ep: Int, round: Int, raw: String?) {
        if (ep != epoch) return
        val arr = try {
            JSONArray(raw ?: "[]")
        } catch (e: Exception) {
            null
        }
        if (arr == null || arr.length() == 0) {
            textBusy = false
            return
        }
        val lang = prefs.lang
        val batches = ArrayList<ArrayList<Int>>()
        var cur = ArrayList<Int>()
        var size = 0
        for (k in 0 until arr.length()) {
            val len = arr.getJSONArray(k).getString(1).length
            val limChars = if (batches.isEmpty()) FIRST_CHARS else BATCH_CHARS
            val limNodes = if (batches.isEmpty()) FIRST_NODES else BATCH_NODES
            if (cur.isNotEmpty() && (size + len > limChars || cur.size >= limNodes)) {
                batches.add(cur)
                cur = ArrayList()
                size = 0
            }
            cur.add(k)
            size += len
        }
        if (cur.isNotEmpty()) batches.add(cur)
        textBusy = false
        var left = batches.size
        var allOk = true
        for (b in batches) {
            val ids = JSONArray()
            val src = JSONArray()
            for (k in b) {
                val row = arr.getJSONArray(k)
                ids.put(row.getInt(0))
                src.put(row.getString(1))
            }
            send(ep, lang, ids, src, 0, BooleanArray(ids.length())) { ok ->
                if (ep == epoch) {
                    if (!ok) allOk = false
                    left--
                    if (left == 0 && allOk && round < 2) text(round + 1)
                }
            }
        }
    }

    private fun send(
        ep: Int,
        lang: String,
        ids: JSONArray,
        src: JSONArray,
        tries: Int,
        got: BooleanArray,
        done: (Boolean) -> Unit
    ) {
        val turn = Gemini.Turn("user", Zero.translateBatch(lang, src.toString()))
        turn.json = true
        begin(ep)
        Gemini.stream(prefs, Zero.translateSystem(lang), listOf(turn), { snap ->
            if (ep == epoch) live(ids, src, snap, got)
        }, { r ->
            end(ep)
            if (ep == epoch) onBatch(ep, lang, ids, src, tries, got, r, done)
        }, null, true, true)
    }

    private fun live(ids: JSONArray, src: JSONArray, snap: String, got: BooleanArray) {
        val list = Partial.strings(snap)
        val put = JSONArray()
        val lim = min(list.size, ids.length())
        for (k in 0 until lim) {
            if (got[k]) continue
            got[k] = true
            val t = list[k].trim()
            if (t.isNotEmpty() && t != src.optString(k, "")) {
                put.put(JSONArray().put(ids.getInt(k)).put(t))
            }
        }
        if (put.length() > 0) script("window.__zero.put(" + put.toString() + ")")
    }

    private fun onBatch(
        ep: Int,
        lang: String,
        ids: JSONArray,
        src: JSONArray,
        tries: Int,
        got: BooleanArray,
        r: Result<String>,
        done: (Boolean) -> Unit
    ) {
        val out = r.getOrNull()
        val res = if (out == null) null else parseArray(out)
        if (res != null && res.length() == ids.length()) {
            val put = JSONArray()
            for (k in 0 until ids.length()) {
                if (got[k]) continue
                got[k] = true
                val t = res.optString(k, "").trim()
                if (t.isNotEmpty() && t != src.optString(k, "")) {
                    put.put(JSONArray().put(ids.getInt(k)).put(t))
                }
            }
            if (put.length() > 0) script("window.__zero.put(" + put.toString() + ")")
            done(true)
            return
        }
        if (out != null && tries < 1) {
            java.util.Arrays.fill(got, false)
            send(ep, lang, ids, src, tries + 1, got, done)
            return
        }
        script("window.__zero.rs(" + ids.toString() + ")")
        fail(r.exceptionOrNull()?.message ?: "Hasil terjemah tidak lengkap")
        done(false)
    }

    private fun images() {
        if (imgBusy) return
        imgBusy = true
        val ep = epoch
        web.evaluateJavascript(PageJs.INSTALL + "window.__zero.im()") { raw ->
            onImages(ep, raw)
        }
    }

    private fun onImages(ep: Int, raw: String?) {
        if (ep != epoch) return
        val o = try {
            JSONObject(raw ?: "")
        } catch (e: Exception) {
            null
        }
        val list = o?.optJSONArray("list")
        val vw = o?.optDouble("vw", 0.0) ?: 0.0
        if (list == null || list.length() == 0) {
            imgBusy = false
            return
        }
        val sx = web.scrollX
        val sy = web.scrollY
        begin(ep)
        capture { bmp -> onShot(ep, list, vw, sx, sy, bmp) }
    }

    private fun release(item: JSONArray) {
        script("window.__zero.uf(" + item.getInt(0) + "," + item.getDouble(5) + "," + item.getDouble(6) + ")")
    }

    private fun onShot(ep: Int, list: JSONArray, vw: Double, sx: Int, sy: Int, bmp: Bitmap?) {
        end(ep)
        if (ep != epoch) return
        val moved = abs(web.scrollX - sx) > 4 || abs(web.scrollY - sy) > 4
        if (bmp == null || vw <= 0.0 || moved) {
            for (i in 0 until list.length()) release(list.getJSONArray(i))
            imgBusy = false
            return
        }
        if (prefs.provider == "groq" && !warned) {
            warned = true
            Toast.makeText(act, "Terjemah gambar di Groq kurang stabil dan posisi teks bisa meleset. Disarankan pakai Gemini.", Toast.LENGTH_LONG).show()
        }
        val k = bmp.width / vw
        val lang = prefs.lang
        var left = list.length()
        for (i in 0 until list.length()) {
            val item = list.getJSONArray(i)
            val cl = (item.getDouble(1) * k).toInt().coerceIn(0, bmp.width - 2)
            val ct = (item.getDouble(2) * k).toInt().coerceIn(0, bmp.height - 2)
            val cr = ((item.getDouble(1) + item.getDouble(3)) * k).toInt().coerceIn(cl + 1, bmp.width)
            val cb = ((item.getDouble(2) + item.getDouble(4)) * k).toInt().coerceIn(ct + 1, bmp.height)
            val area = Rect(cl, ct, cr, cb)
            val crop = Pix.crop(bmp, area)
            val turn = Gemini.Turn("user", Zero.translateBoxes(lang), crop)
            turn.json = true
            var shown = 0
            var drawn = false
            begin(ep)
            Gemini.stream(prefs, Zero.translateSystem(lang), listOf(turn), { snap ->
                if (ep == epoch) {
                    val objs = Partial.objects(snap)
                    val lim = min(objs.size, MAX_BOXES)
                    val fresh = ArrayList<Patch>()
                    while (shown < lim) {
                        val p = patchOf(objs[shown], bmp, area, sx, sy)
                        if (p != null) fresh.add(p)
                        shown++
                    }
                    if (fresh.isNotEmpty()) {
                        if (drawn) {
                            layer.add(fresh)
                        } else {
                            drawn = true
                            layer.put(fresh, zoneOf(area, sx, sy))
                        }
                    }
                }
            }, { r ->
                end(ep)
                if (ep == epoch) {
                    onBoxes(r, bmp, area, sx, sy, item, drawn)
                    left--
                    if (left == 0) imgBusy = false
                }
            }, null, true, true)
        }
    }

    private fun zoneOf(area: Rect, sx: Int, sy: Int): RectF {
        return RectF(
            (area.left + sx).toFloat(),
            (area.top + sy).toFloat(),
            (area.right + sx).toFloat(),
            (area.bottom + sy).toFloat()
        )
    }

    private fun patchOf(o: JSONObject, bmp: Bitmap, area: Rect, sx: Int, sy: Int): Patch? {
        val bx = o.optJSONArray("box_2d") ?: o.optJSONArray("box") ?: return null
        if (bx.length() < 4) return null
        val y0 = bx.optDouble(0, 0.0).coerceIn(0.0, 1000.0)
        val x0 = bx.optDouble(1, 0.0).coerceIn(0.0, 1000.0)
        val y1 = bx.optDouble(2, 0.0).coerceIn(0.0, 1000.0)
        val x1 = bx.optDouble(3, 0.0).coerceIn(0.0, 1000.0)
        if (y1 - y0 < 4.0 || x1 - x0 < 4.0) return null
        val tx = o.optString("text", o.optString("translation", "")).trim()
        if (tx.isEmpty()) return null
        val cw = area.width()
        val ch = area.height()
        val l = area.left + (x0 / 1000.0 * cw).toInt()
        val t = area.top + (y0 / 1000.0 * ch).toInt()
        val rr = area.left + (x1 / 1000.0 * cw).toInt()
        val b = area.top + (y1 / 1000.0 * ch).toInt()
        val fill = background(bmp, l, t, rr, b)
        val lum = Color.red(fill) * 0.299 + Color.green(fill) * 0.587 + Color.blue(fill) * 0.114
        val ink = if (lum > 140) 0xFF111111.toInt() else 0xFFF5F5F5.toInt()
        return Patch((l + sx).toFloat(), (t + sy).toFloat(), (rr + sx).toFloat(), (b + sy).toFloat(), tx, fill, ink)
    }

    private fun onBoxes(r: Result<String>, bmp: Bitmap, area: Rect, sx: Int, sy: Int, item: JSONArray, drawn: Boolean) {
        val out = r.getOrNull()
        val arr = if (out == null) null else parseArray(out)
        if (arr == null) {
            if (!drawn) {
                release(item)
                fail(r.exceptionOrNull()?.message ?: "Hasil terjemah gambar tidak terbaca")
            }
            return
        }
        val patches = ArrayList<Patch>()
        for (j in 0 until min(arr.length(), MAX_BOXES)) {
            val o = arr.optJSONObject(j) ?: continue
            val p = patchOf(o, bmp, area, sx, sy)
            if (p != null) patches.add(p)
        }
        layer.put(patches, zoneOf(area, sx, sy))
    }

    private fun background(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): Int {
        val n = 7
        val rs = IntArray(n * n)
        val gs = IntArray(n * n)
        val bs = IntArray(n * n)
        var c = 0
        for (iy in 0 until n) {
            for (ix in 0 until n) {
                val x = (l + (r - l) * (ix + 0.5) / n).toInt().coerceIn(0, bmp.width - 1)
                val y = (t + (b - t) * (iy + 0.5) / n).toInt().coerceIn(0, bmp.height - 1)
                val p = bmp.getPixel(x, y)
                rs[c] = Color.red(p)
                gs[c] = Color.green(p)
                bs[c] = Color.blue(p)
                c++
            }
        }
        rs.sort()
        gs.sort()
        bs.sort()
        return Color.rgb(rs[c / 2], gs[c / 2], bs[c / 2])
    }

    companion object {
        private const val TEXT_BUDGET = 8000
        private const val FULL_BUDGET = 14000
        private const val FULL_MAX_ROUNDS = 8
        private const val FIRST_CHARS = 700
        private const val FIRST_NODES = 14
        private const val BATCH_CHARS = 2200
        private const val BATCH_NODES = 45
        private const val MAX_BOXES = 40
    }
}
