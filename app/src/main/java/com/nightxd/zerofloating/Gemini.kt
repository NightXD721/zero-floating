package com.nightxd.zerofloating

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Gemini {
    class Turn(val role: String, val text: String, var image: Bitmap? = null)

    class Busy(msg: String) : IOException(msg)

    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun ask(prefs: Prefs, system: String, turns: List<Turn>, done: (Result<String>) -> Unit) {
        stream(prefs, system, turns, { }, done)
    }

    fun stream(
        prefs: Prefs,
        system: String,
        turns: List<Turn>,
        onText: (String) -> Unit,
        done: (Result<String>) -> Unit
    ) {
        val key = prefs.apiKey
        val first = prefs.model
        pool.execute {
            val r = runCatching { chain(key, first, system, turns, onText) }
            main.post { done(r) }
        }
    }

    @Volatile
    var lastModel = ""

    fun note(prefs: Prefs): String {
        val used = lastModel
        if (used.isEmpty() || used == prefs.model) return ""
        return "\n\nDijawab oleh " + Models.nameOf(used) + " karena " + Models.nameOf(prefs.model) + " sedang sibuk."
    }

    private fun chain(
        key: String,
        first: String,
        system: String,
        turns: List<Turn>,
        onText: (String) -> Unit
    ): String {
        val order = ArrayList<String>()
        order.add(first)
        order.add(first)
        for (m in Models.fallbacks(first)) order.add(m)
        var last = "Server Gemini sedang sibuk. Coba lagi sebentar."
        var skipSame = false
        for (i in order.indices) {
            if (i == 1 && skipSame) continue
            try {
                if (i == 1) Thread.sleep(900)
                val out = attempt(key, order[i], system, turns, onText)
                lastModel = order[i]
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

    private fun attempt(
        key: String,
        model: String,
        system: String,
        turns: List<Turn>,
        onText: (String) -> Unit
    ): String {
        return try {
            fetch(key, model, system, turns, true, onText)
        } catch (e: Busy) {
            throw e
        } catch (e: IOException) {
            if (e.message?.contains("thinking", true) == true) {
                fetch(key, model, system, turns, false, onText)
            } else {
                throw e
            }
        }
    }

    private fun isBusy(code: Int, msg: String): Boolean {
        if (code == 429 || code >= 500) return true
        return msg.contains("high demand", true) ||
            msg.contains("overloaded", true) ||
            msg.contains("unavailable", true)
    }

    private fun thinkingConfig(model: String): JSONObject? {
        val m = model.lowercase()
        if (m.startsWith("gemini-2.5-pro")) return null
        if (m.startsWith("gemini-2.5")) return JSONObject().put("thinkingBudget", 0)
        return JSONObject().put("thinkingLevel", "low")
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
        thinking: Boolean,
        onText: (String) -> Unit
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
            val img = t.image
            if (img != null) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", "image/jpeg").put("data", Pix.b64(img))
                    )
                )
            }
            parts.put(JSONObject().put("text", t.text))
            contents.put(JSONObject().put("role", t.role).put("parts", parts))
        }
        body.put("contents", contents)
        if (thinking) {
            val cfg = thinkingConfig(model)
            if (cfg != null) {
                body.put("generationConfig", JSONObject().put("thinkingConfig", cfg))
            }
        }

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
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.optJSONObject(i) ?: continue
                            if (p.optBoolean("thought", false)) continue
                            val piece = p.optString("text", "")
                            if (piece.isNotEmpty()) {
                                sb.append(piece)
                                grew = true
                            }
                        }
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
}
