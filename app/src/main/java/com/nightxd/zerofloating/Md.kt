package com.nightxd.zerofloating

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import kotlin.math.max

object Md {
    private val token = Regex("""\*\*(.+?)\*\*|`([^`]+)`|(?<![*\w])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![*\w])|~~(.+?)~~""")
    private val link = Regex("""\[([^\]\n]+)\]\(([^)\s]+)\)""")
    private val heading = Regex("^#{1,6}\\s+")
    private val bullet = Regex("^(\\s*)[*-]\\s+")
    private val rule = Regex("^\\s*([-*_])\\1{2,}\\s*$")
    private val quote = Regex("^\\s*>\\s?")
    private val tableSep = Regex("^[\\s|:\\-]+$")

    private fun mono(out: SpannableStringBuilder, start: Int, bg: Boolean) {
        out.setSpan(TypefaceSpan("monospace"), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bg) out.setSpan(BackgroundColorSpan(Pal.panel), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun stripMd(s: String): String {
        return s.replace("**", "").replace("__", "").replace("`", "").replace("*", "").trim()
    }

    private fun table(block: List<String>, out: SpannableStringBuilder) {
        val rows = ArrayList<List<String>>()
        for (l in block) {
            val t = l.trim()
            // skip separator like | :--- | :--- |
            val bare = t.replace("|", "").replace(":", "").replace("-", "").replace(" ", "")
            if (bare.isEmpty() && t.contains("-")) continue
            if (tableSep.matches(t.replace("|", " "))) continue
            val cells = t.removePrefix("|").removeSuffix("|").split("|")
            rows.add(cells.map { stripMd(it) })
        }
        if (rows.isEmpty()) return
        var cols = 0
        for (r in rows) cols = max(cols, r.size)
        if (cols == 0) return
        val w = IntArray(cols)
        for (r in rows) {
            for (c in r.indices) w[c] = max(w[c], r[c].length.coerceAtMost(28))
        }
        val start = out.length
        var total = 0
        for (x in w) total += x
        total += 3 * (cols - 1)
        for (ri in rows.indices) {
            val sb = StringBuilder()
            for (c in 0 until cols) {
                val cell = if (c < rows[ri].size) rows[ri][c].take(28) else ""
                sb.append(cell.padEnd(w[c]))
                if (c < cols - 1) sb.append(" │ ")
            }
            out.append(sb.toString().trimEnd())
            if (ri == 0 && rows.size > 1) {
                out.append("\n")
                out.append("─".repeat(total.coerceAtMost(80)))
            }
            if (ri < rows.size - 1) out.append("\n")
        }
        mono(out, start, true)
    }

    fun render(raw: String): CharSequence {
        val src = Tex.clean(raw)
        val out = SpannableStringBuilder()
        val lines = src.replace("\r", "").split("\n")
        var fence = false
        var i = 0
        while (i < lines.size) {
            var line = lines[i]
            if (line.trim().startsWith("```")) {
                fence = !fence
                i++
                continue
            }
            if (fence) {
                val s = out.length
                out.append(line)
                mono(out, s, true)
                if (i < lines.size - 1) out.append("\n")
                i++
                continue
            }
            if (line.trimStart().startsWith("|")) {
                var j = i
                val block = ArrayList<String>()
                while (j < lines.size && lines[j].trimStart().startsWith("|")) {
                    block.add(lines[j])
                    j++
                }
                table(block, out)
                if (j < lines.size) out.append("\n")
                i = j
                continue
            }
            if (rule.matches(line)) {
                out.append("\u2500".repeat(18))
                if (i < lines.size - 1) out.append("\n")
                i++
                continue
            }
            var isHead = false
            val h = heading.find(line)
            if (h != null) {
                line = line.substring(h.value.length)
                isHead = true
            }
            val q = quote.find(line)
            if (q != null) {
                line = "\u2502 " + line.substring(q.value.length)
            }
            val b = bullet.find(line)
            if (b != null) {
                val pad = b.groupValues[1].length.coerceAtMost(8)
                line = " ".repeat(pad) + "\u2022 " + line.substring(b.value.length)
            }
            line = link.replace(line) { m -> m.groupValues[1] + " (" + m.groupValues[2] + ")" }
            val start = out.length
            var last = 0
            for (m in token.findAll(line)) {
                out.append(line, last, m.range.first)
                val s = out.length
                if (m.groupValues[1].isNotEmpty()) {
                    out.append(m.groupValues[1])
                    out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (m.groupValues[2].isNotEmpty()) {
                    out.append(m.groupValues[2])
                    mono(out, s, true)
                } else if (m.groupValues[3].isNotEmpty()) {
                    out.append(m.groupValues[3])
                    out.setSpan(StyleSpan(Typeface.ITALIC), s, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    out.append(m.groupValues[4])
                    out.setSpan(StrikethroughSpan(), s, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                last = m.range.last + 1
            }
            out.append(line, last, line.length)
            if (isHead && out.length > start) {
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (i < lines.size - 1) out.append("\n")
            i++
        }
        return out
    }
}
