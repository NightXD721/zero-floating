package com.nightxd.zerofloating

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

object Md {
    private val token = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    private val heading = Regex("^#{1,6}\\s+")
    private val bullet = Regex("^\\s*[*-]\\s+")

    fun render(src: String): CharSequence {
        val out = SpannableStringBuilder()
        val lines = src.replace("\r", "").split("\n")
        for (i in lines.indices) {
            var line = lines[i]
            var isHead = false
            val h = heading.find(line)
            if (h != null) {
                line = line.substring(h.value.length)
                isHead = true
            }
            val b = bullet.find(line)
            if (b != null) {
                line = "- " + line.substring(b.value.length)
            }
            val start = out.length
            var last = 0
            for (m in token.findAll(line)) {
                out.append(line, last, m.range.first)
                val s = out.length
                if (m.groupValues[1].isNotEmpty()) {
                    out.append(m.groupValues[1])
                    out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    out.append(m.groupValues[2])
                    out.setSpan(TypefaceSpan("monospace"), s, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                last = m.range.last + 1
            }
            out.append(line, last, line.length)
            if (isHead && out.length > start) {
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (i < lines.size - 1) out.append("\n")
        }
        return out
    }
}
