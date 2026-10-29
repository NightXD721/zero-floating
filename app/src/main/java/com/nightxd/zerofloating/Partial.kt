package com.nightxd.zerofloating

import org.json.JSONArray
import org.json.JSONObject

object Partial {
    fun strings(s: String): List<String> {
        val out = ArrayList<String>()
        var i = s.indexOf('[')
        if (i < 0) return out
        i++
        val n = s.length
        while (i < n) {
            val c = s[i]
            if (c == ']') break
            if (c != '"') {
                i++
                continue
            }
            var j = i + 1
            var closed = false
            while (j < n) {
                val d = s[j]
                if (d == '\\') {
                    j += 2
                    continue
                }
                if (d == '"') {
                    closed = true
                    break
                }
                j++
            }
            if (!closed) break
            val raw = s.substring(i, j + 1)
            val text = try {
                JSONArray("[" + raw + "]").getString(0)
            } catch (e: Exception) {
                raw.substring(1, raw.length - 1)
            }
            out.add(text)
            i = j + 1
        }
        return out
    }

    fun objects(s: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var i = s.indexOf('[')
        if (i < 0) return out
        i++
        val n = s.length
        var depth = 0
        var start = -1
        var inStr = false
        while (i < n) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') {
                    i++
                } else if (c == '"') {
                    inStr = false
                }
            } else if (c == '"') {
                inStr = true
            } else if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        try {
                            out.add(JSONObject(s.substring(start, i + 1)))
                        } catch (e: Exception) {
                        }
                        start = -1
                    }
                }
            }
            i++
        }
        return out
    }
}
