package com.nightxd.zerofloating

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Gemini {
    class Turn(val role: String, val text: String, var image: Bitmap? = null)

    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun ask(prefs: Prefs, system: String, turns: List<Turn>, done: (Result<String>) -> Unit) {
        val key = prefs.apiKey
        val model = prefs.model
        pool.execute {
            val r = runCatching { call(key, model, system, turns) }
            main.post { done(r) }
        }
    }

    private fun call(key: String, modelRaw: String, system: String, turns: List<Turn>): String {
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

        val target = Uri.Builder()
            .scheme("https")
            .authority("generativelanguage.googleapis.com")
            .appendEncodedPath("v1beta/models/" + model + ":generateContent")
            .build()
            .toString()

        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            val json = try {
                JSONObject(raw)
            } catch (e: Exception) {
                throw IOException("Respons tidak valid (HTTP " + code + ")")
            }

            if (json.has("error")) {
                val msg = json.optJSONObject("error")?.optString("message", "") ?: ""
                if (code == 429) throw IOException("Kuota API habis atau terlalu sering. Coba lagi sebentar.")
                if (msg.contains("API key", true)) throw IOException("API key tidak valid")
                throw IOException(if (msg.isNotEmpty()) msg else "HTTP " + code)
            }

            val cands = json.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val why = json.optJSONObject("promptFeedback")?.optString("blockReason", "") ?: ""
                throw IOException(if (why.isNotEmpty()) "Diblokir: " + why else "Respons kosong")
            }

            val first = cands.getJSONObject(0)
            val parts = first.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    if (p.optBoolean("thought", false)) continue
                    sb.append(p.optString("text", ""))
                }
            }
            val out = sb.toString().trim()
            if (out.isEmpty()) {
                throw IOException("Jawaban kosong (" + first.optString("finishReason", "tanpa alasan") + ")")
            }
            return out
        } finally {
            conn.disconnect()
        }
    }
}
