package com.nightxd.zerofloating

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Gemini {
    class Turn(val role: String, val text: String, var image: Bitmap? = null) {
        var small = false
        var json = false
        var more: List<Bitmap> = emptyList()
        var pdf: ByteArray? = null
    }

    class Busy(msg: String) : IOException(msg)

    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var lastModel = ""

    @Volatile
    var lastSwitched = false

    fun ask(prefs: Prefs, system: String, turns: List<Turn>, done: (Result<String>) -> Unit) {
        stream(prefs, system, turns, { }, done, null, true)
    }

    fun stream(
        prefs: Prefs,
        system: String,
        turns: List<Turn>,
        onText: (String) -> Unit,
        done: (Result<String>) -> Unit,
        onThought: ((String) -> Unit)? = null,
        fast: Boolean = false,
        translate: Boolean = false
    ) {
        val key = prefs.apiKey
        val groq = prefs.provider == "groq"
        val vision = turns.any { it.image != null || it.more.isNotEmpty() }
        var first = prefs.model
        if (translate && prefs.trMode == "fast") first = Models.quick(groq, first)
        if (groq && vision && !Models.isVision(first)) first = prefs.groqVisionModel
        val think = prefs.think && !fast
        val level = prefs.level
        val cb = if (think) onThought else null
        val start = first
        pool.execute {
            val r = runCatching { chain(key, start, system, turns, onText, cb, think, level, groq, vision) }
            main.post { done(r) }
        }
    }

    fun note(prefs: Prefs): String {
        val used = lastModel
        if (used.isEmpty() || !lastSwitched) return ""
        return "\n\nDijawab oleh " + Models.nameOf(used) + " (dialihkan otomatis)."
    }

    private fun chain(
        key: String,
        first: String,
        system: String,
        turns: List<Turn>,
        onText: (String) -> Unit,
        onThought: ((String) -> Unit)?,
        think: Boolean,
        level: String,
        groq: Boolean,
        vision: Boolean
    ): String {
        val order = ArrayList<String>()
        order.add(first)
        order.add(first)
        for (m in Models.fallbacks(first, groq, vision)) order.add(m)
        var last = "Server Gemini sedang sibuk. Coba lagi sebentar."
        var skipSame = false
        for (i in order.indices) {
            if (i == 1 && skipSame) continue
            try {
                if (i == 1) Thread.sleep(900)
                val out = if (groq) {
                    attemptGroq(key, order[i], system, turns, onText, onThought, think, level)
                } else {
                    attempt(key, order[i], system, turns, onText, onThought, think, level)
                }
                lastModel = order[i]
                lastSwitched = i >= 2
                return out
            } catch (e: Busy) {
                last = e.message ?: last
            } catch (e: SocketTimeoutException) {
                last = "Koneksi ke Gemini terlalu lama. Coba lagi."
                skipSame = true
            } catch (e: UnknownHostException) {
                throw IOException("Tidak ada koneksi internet")
            }
        }
        throw IOException(last)
    }

    private fun thinkOptions(model: String, think: Boolean, level: String, thoughts: Boolean): List<JSONObject> {
        val m = model.lowercase()
        val out = ArrayList<JSONObject>()
        if (m.startsWith("gemini-2.5")) {
            if (!think) {
                if (!m.startsWith("gemini-2.5-pro")) out.add(JSONObject().put("thinkingBudget", 0))
            } else {
                val b = if (level == "high") 8192 else if (level == "medium") 4096 else 1024
                out.add(JSONObject().put("thinkingBudget", b).put("includeThoughts", thoughts))
            }
            return out
        }
        if (think) {
            out.add(JSONObject().put("thinkingLevel", level).put("includeThoughts", thoughts))
        } else {
            out.add(JSONObject().put("thinkingLevel", "minimal"))
            out.add(JSONObject().put("thinkingLevel", "low"))
        }
        return out
    }

    private fun attempt(
        key: String,
        model: String,
        system: String,
        turns: List<Turn>,
        onText: (String) -> Unit,
        onThought: ((String) -> Unit)?,
        think: Boolean,
        level: String
    ): String {
        for (cfg in thinkOptions(model, think, level, onThought != null)) {
            try {
                return fetch(key, model, system, turns, cfg, onText, onThought)
            } catch (e: Busy) {
                throw e
            } catch (e: IOException) {
                val m = e.message ?: ""
                if (m.contains("thinking", true)) continue
                throw e
            }
        }
        return fetch(key, model, system, turns, null, onText, onThought)
    }

    private fun isBusy(code: Int, msg: String): Boolean {
        if (code == 429 || code >= 500) return true
        return msg.contains("high demand", true) ||
            msg.contains("overloaded", true) ||
            msg.contains("unavailable", true)
    }

    private fun errorText(code: Int, raw: String): String {
        val t = raw.trim()
        val o = try {
            if (t.startsWith("[")) JSONArray(t).optJSONObject(0) else JSONObject(t)
        } catch (e: Exception) {
            null
        }
        val msg = o?.optJSONObject("error")?.optString("message", "") ?: ""
        if (code == 429) return "Kuota API habis atau terlalu sering. Coba lagi sebentar."
        if (msg.contains("API key", true)) return "API key tidak valid"
        return if (msg.isNotEmpty()) msg else "HTTP " + code
    }

    private fun fetch(
        key: String,
        modelRaw: String,
        system: String,
        turns: List<Turn>,
        cfg: JSONObject?,
        onText: (String) -> Unit,
        onThought: ((String) -> Unit)?
    ): String {
        if (key.isBlank()) throw IOException("API key belum diisi")
        val model = modelRaw.trim().removePrefix("models/")

        val body = JSONObject()
        body.put(
            "systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
        )
        val contents = JSONArray()
        for (t in turns) {
            val parts = JSONArray()
            val pics = ArrayList<Bitmap>()
            val lead = t.image
            if (lead != null) pics.add(lead)
            pics.addAll(t.more)
            for (pic in pics) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", "image/jpeg").put("data", Pix.b64(pic, if (t.small) 1280 else 1600, if (t.small) 65 else 75))
                    )
                )
            }
            val doc = t.pdf
            if (doc != null) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", "application/pdf").put("data", Base64.encodeToString(doc, Base64.NO_WRAP))
                    )
                )
            }
            parts.put(JSONObject().put("text", t.text))
            contents.put(JSONObject().put("role", t.role).put("parts", parts))
        }
        body.put("contents", contents)
        val gen = JSONObject()
        if (cfg != null) gen.put("thinkingConfig", cfg)
        if (turns.any { it.json }) gen.put("responseMimeType", "application/json")
        if (gen.length() > 0) body.put("generationConfig", gen)

        val target = Uri.Builder()
            .scheme("https")
            .authority("generativelanguage.googleapis.com")
            .appendEncodedPath("v1beta/models/" + model + ":streamGenerateContent")
            .appendQueryParameter("alt", "sse")
            .build()
            .toString()

        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val raw = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val msg = errorText(code, raw)
                if (isBusy(code, msg)) throw Busy(msg)
                throw IOException(msg)
            }

            val sb = StringBuilder()
            val th = StringBuilder()
            var blocked = ""
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { rd ->
                while (true) {
                    val line = rd.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring(5).trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val j = try {
                        JSONObject(payload)
                    } catch (e: Exception) {
                        null
                    }
                    if (j == null) continue
                    if (j.has("error")) {
                        val msg = errorText(code, payload)
                        if (isBusy(0, msg)) throw Busy(msg)
                        throw IOException(msg)
                    }
                    val cands = j.optJSONArray("candidates")
                    if (cands == null || cands.length() == 0) {
                        val why = j.optJSONObject("promptFeedback")?.optString("blockReason", "") ?: ""
                        if (why.isNotEmpty()) blocked = why
                        continue
                    }
                    val first = cands.getJSONObject(0)
                    val parts = first.optJSONObject("content")?.optJSONArray("parts")
                    var grew = false
                    var thought = false
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.optJSONObject(i) ?: continue
                            val piece = p.optString("text", "")
                            if (piece.isEmpty()) continue
                            if (p.optBoolean("thought", false)) {
                                th.append(piece)
                                thought = true
                            } else {
                                sb.append(piece)
                                grew = true
                            }
                        }
                    }
                    if (thought && onThought != null) {
                        val snap = th.toString().trim()
                        main.post { onThought(snap) }
                    }
                    if (grew) {
                        val snap = sb.toString()
                        main.post { onText(snap) }
                    }
                }
            }

            val out = sb.toString().trim()
            if (out.isEmpty()) {
                throw IOException(if (blocked.isNotEmpty()) "Diblokir: " + blocked else "Jawaban kosong")
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun groqOptions(model: String, think: Boolean, level: String, thoughts: Boolean): List<JSONObject> {
        val m = model.lowercase()
        val out = ArrayList<JSONObject>()
        if (m.contains("gpt-oss")) {
            out.add(
                JSONObject()
                    .put("reasoning_effort", if (think) level else "low")
                    .put("include_reasoning", think && thoughts)
            )
        } else if (m.contains("qwen3")) {
            if (think) {
                out.add(
                    JSONObject()
                        .put("reasoning_effort", "default")
                        .put("reasoning_format", if (thoughts) "parsed" else "hidden")
                )
            } else {
                out.add(JSONObject().put("reasoning_effort", "none"))
            }
        } else if (m.contains("deepseek-r1")) {
            out.add(JSONObject().put("reasoning_format", if (think && thoughts) "parsed" else "hidden"))
        }
        return out
    }

    private fun attemptGroq(
        key: String,
        model: String,
        system: String,
        turns: List<Turn>,
        onText: (String) -> Unit,
        onThought: ((String) -> Unit)?,
        think: Boolean,
        level: String
    ): String {
        for (extra in groqOptions(model, think, level, onThought != null)) {
            try {
                return fetchGroq(key, model, system, turns, extra, onText, onThought)
            } catch (e: Busy) {
                throw e
            } catch (e: IOException) {
                val m = e.message ?: ""
                if (m.contains("reasoning", true)) continue
                throw e
            }
        }
        return fetchGroq(key, model, system, turns, null, onText, onThought)
    }

    private fun splitThink(acc: String): Array<String> {
        val a = acc.indexOf("<think>")
        if (a < 0) return arrayOf("", acc)
        val b = acc.indexOf("</think>", a)
        if (b < 0) return arrayOf(acc.substring(a + 7), acc.substring(0, a))
        return arrayOf(acc.substring(a + 7, b), acc.substring(0, a) + acc.substring(b + 8))
    }

    private fun fetchGroq(
        key: String,
        model: String,
        system: String,
        turns: List<Turn>,
        extra: JSONObject?,
        onText: (String) -> Unit,
        onThought: ((String) -> Unit)?
    ): String {
        if (key.isBlank()) throw IOException("API key belum diisi")
        val body = JSONObject()
        body.put("model", model.trim())
        body.put("stream", true)
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for (t in turns) {
            val role = if (t.role == "model") "assistant" else "user"
            val pics = ArrayList<Bitmap>()
            val lead = t.image
            if (lead != null) pics.add(lead)
            pics.addAll(t.more)
            if (pics.isNotEmpty()) {
                val arr = JSONArray()
                arr.put(JSONObject().put("type", "text").put("text", t.text))
                for (pic in pics.take(5)) {
                    arr.put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64," + Pix.b64(pic, if (t.small) 1280 else 1600, if (t.small) 65 else 75))
                        )
                    )
                }
                msgs.put(JSONObject().put("role", role).put("content", arr))
            } else {
                msgs.put(JSONObject().put("role", role).put("content", t.text))
            }
        }
        body.put("messages", msgs)
        if (extra != null) {
            val ks = extra.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                body.put(k, extra.get(k))
            }
        }

        val target = Uri.Builder()
            .scheme("https")
            .authority("api.groq.com")
            .appendEncodedPath("openai/v1/chat/completions")
            .build()
            .toString()

        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("User-Agent", "ZeroFloating/1.0")
            conn.setRequestProperty("Authorization", "Bearer " + key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val raw = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                var msg = errorText(code, raw)
                if (msg.contains("invalid api key", true)) msg = "API key tidak valid"
                if (isBusy(code, msg)) throw Busy(msg)
                throw IOException(msg)
            }

            val raw = StringBuilder()
            val th = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { rd ->
                while (true) {
                    val line = rd.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring(5).trim()
                    if (payload == "[DONE]") break
                    if (payload.isEmpty()) continue
                    val j = try {
                        JSONObject(payload)
                    } catch (e: Exception) {
                        null
                    }
                    if (j == null) continue
                    if (j.has("error")) {
                        val msg = errorText(code, payload)
                        if (isBusy(0, msg)) throw Busy(msg)
                        throw IOException(msg)
                    }
                    val choices = j.optJSONArray("choices")
                    if (choices == null || choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    var r1 = delta.optString("reasoning", "")
                    if (r1.isEmpty()) r1 = delta.optString("reasoning_content", "")
                    val c1 = delta.optString("content", "")
                    if (r1.isEmpty() && c1.isEmpty()) continue
                    th.append(r1)
                    raw.append(c1)
                    val parts = splitThink(raw.toString())
                    val thoughtNow = (th.toString() + parts[0]).trim()
                    if (thoughtNow.isNotEmpty() && onThought != null) {
                        main.post { onThought(thoughtNow) }
                    }
                    if (parts[1].isNotEmpty()) {
                        val ans = parts[1]
                        main.post { onText(ans) }
                    }
                }
            }

            val out = splitThink(raw.toString())[1].trim()
            if (out.isEmpty()) throw IOException("Jawaban kosong")
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun groqModels(key: String, cb: (Result<List<String>>) -> Unit) {
        pool.execute {
            val r = runCatching {
                val target = Uri.Builder()
                    .scheme("https")
                    .authority("api.groq.com")
                    .appendEncodedPath("openai/v1/models")
                    .build()
                    .toString()
                val conn = URL(target).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 20000
                    conn.setRequestProperty("User-Agent", "ZeroFloating/1.0")
                    conn.setRequestProperty("Authorization", "Bearer " + key)
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        val raw = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                        throw IOException(errorText(code, raw))
                    }
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
                    val skip = listOf("whisper", "tts", "guard", "orpheus", "playai", "embed")
                    val ids = ArrayList<String>()
                    for (i in 0 until data.length()) {
                        val id = data.getJSONObject(i).optString("id", "")
                        if (id.isEmpty()) continue
                        if (skip.any { id.contains(it, true) }) continue
                        ids.add(id)
                    }
                    ids.sort()
                    ids as List<String>
                } finally {
                    conn.disconnect()
                }
            }
            main.post { cb(r) }
        }
    }
}
