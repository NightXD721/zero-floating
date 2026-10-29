package com.nightxd.zerofloating

object Tex {
    private val sup: Map<Char, Char> = mapOf(
        '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3', '4' to '\u2074',
        '5' to '\u2075', '6' to '\u2076', '7' to '\u2077', '8' to '\u2078', '9' to '\u2079',
        '+' to '\u207A', '-' to '\u207B', '=' to '\u207C', '(' to '\u207D', ')' to '\u207E',
        'n' to '\u207F', 'i' to '\u2071', 'x' to '\u02E3'
    )

    private val sub: Map<Char, Char> = mapOf(
        '0' to '\u2080', '1' to '\u2081', '2' to '\u2082', '3' to '\u2083', '4' to '\u2084',
        '5' to '\u2085', '6' to '\u2086', '7' to '\u2087', '8' to '\u2088', '9' to '\u2089',
        '+' to '\u208A', '-' to '\u208B', '=' to '\u208C', '(' to '\u208D', ')' to '\u208E',
        'a' to '\u2090', 'e' to '\u2091', 'o' to '\u2092', 'x' to '\u2093', 'h' to '\u2095',
        'k' to '\u2096', 'l' to '\u2097', 'm' to '\u2098', 'n' to '\u2099', 'p' to '\u209A',
        's' to '\u209B', 't' to '\u209C', 'i' to '\u1D62', 'j' to '\u2C7C', 'r' to '\u1D63',
        'u' to '\u1D64', 'v' to '\u1D65'
    )

    private val cmds: Map<String, String> = mapOf(
        "alpha" to "\u03B1", "beta" to "\u03B2", "gamma" to "\u03B3", "delta" to "\u03B4",
        "epsilon" to "\u03B5", "varepsilon" to "\u03B5", "zeta" to "\u03B6", "eta" to "\u03B7",
        "theta" to "\u03B8", "iota" to "\u03B9", "kappa" to "\u03BA", "lambda" to "\u03BB",
        "mu" to "\u03BC", "nu" to "\u03BD", "xi" to "\u03BE", "pi" to "\u03C0", "rho" to "\u03C1",
        "sigma" to "\u03C3", "tau" to "\u03C4", "upsilon" to "\u03C5", "phi" to "\u03C6",
        "varphi" to "\u03C6", "chi" to "\u03C7", "psi" to "\u03C8", "omega" to "\u03C9",
        "Gamma" to "\u0393", "Delta" to "\u0394", "Theta" to "\u0398", "Lambda" to "\u039B",
        "Xi" to "\u039E", "Pi" to "\u03A0", "Sigma" to "\u03A3", "Phi" to "\u03A6",
        "Psi" to "\u03A8", "Omega" to "\u03A9",
        "cdot" to "\u00B7", "times" to "\u00D7", "div" to "\u00F7", "pm" to "\u00B1", "mp" to "\u2213",
        "leq" to "\u2264", "le" to "\u2264", "geq" to "\u2265", "ge" to "\u2265", "neq" to "\u2260",
        "ne" to "\u2260", "approx" to "\u2248", "equiv" to "\u2261", "sim" to "\u223C",
        "propto" to "\u221D", "infty" to "\u221E", "to" to "\u2192", "rightarrow" to "\u2192",
        "leftarrow" to "\u2190", "leftrightarrow" to "\u2194", "Rightarrow" to "\u21D2",
        "Leftarrow" to "\u21D0", "Leftrightarrow" to "\u21D4", "implies" to "\u21D2", "iff" to "\u21D4",
        "in" to "\u2208", "notin" to "\u2209", "subset" to "\u2282", "subseteq" to "\u2286",
        "cup" to "\u222A", "cap" to "\u2229", "forall" to "\u2200", "exists" to "\u2203",
        "sum" to "\u2211", "prod" to "\u220F", "int" to "\u222B", "partial" to "\u2202",
        "nabla" to "\u2207", "circ" to "\u2218", "angle" to "\u2220", "degree" to "\u00B0",
        "ldots" to "\u2026", "dots" to "\u2026", "cdots" to "\u22EF", "therefore" to "\u2234",
        "because" to "\u2235", "emptyset" to "\u2205", "mid" to "|", "perp" to "\u22A5",
        "parallel" to "\u2225", "triangle" to "\u25B3", "ast" to "*", "star" to "\u2605",
        "lim" to "lim", "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot",
        "sec" to "sec", "csc" to "csc", "log" to "log", "ln" to "ln", "exp" to "exp",
        "max" to "max", "min" to "min", "gcd" to "gcd", "det" to "det", "mod" to "mod",
        "hbar" to "\u0127", "ell" to "\u2113", "rightleftharpoons" to "\u21CC", "longrightarrow" to "\u27F6",
        "uparrow" to "\u2191", "downarrow" to "\u2193", "lt" to "<", "gt" to ">", "ll" to "\u226A", "gg" to "\u226B",
        "cong" to "\u2245", "setminus" to "\u2216", "oplus" to "\u2295", "otimes" to "\u2297", "vee" to "\u2228",
        "wedge" to "\u2227", "neg" to "\u00AC", "lfloor" to "\u230A", "rfloor" to "\u230B", "lceil" to "\u2308",
        "rceil" to "\u2309", "langle" to "\u27E8", "rangle" to "\u27E9", "prime" to "\u2032", "nsubseteq" to "\u2288",
        "supset" to "\u2283", "supseteq" to "\u2287", "varnothing" to "\u2205", "Re" to "Re", "Im" to "Im",
        "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan", "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "left" to "", "right" to "", "displaystyle" to "", "limits" to "", "quad" to "  ",
        "qquad" to "    ", "big" to "", "Big" to "", "bigg" to ""
    )

    private fun group(s: String, from: Int): IntArray? {
        var i = from
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length || s[i] != '{') return null
        var depth = 0
        var j = i
        while (j < s.length) {
            if (s[j] == '{') {
                depth++
            } else if (s[j] == '}') {
                depth--
                if (depth == 0) return intArrayOf(i + 1, j, j + 1)
            }
            j++
        }
        return null
    }

    private fun scriptChar(c: Char): Boolean {
        return c in '\u2070'..'\u209F' || c == '\u00B2' || c == '\u00B3' || c == '\u00B9' || c == '\u02E3' || c == '\u1D62' || c == '\u1D63' || c == '\u1D64' || c == '\u1D65' || c == '\u2C7C'
    }

    private fun wrap(x: String): String {
        if (x.isNotEmpty() && x.all { it.isLetterOrDigit() || it == '.' || scriptChar(it) }) return x
        return "(" + x + ")"
    }

    private val fracRe = Regex("""\\([dt]?frac|binom)(?![a-zA-Z])""")
    private val wrapRe = Regex("""\\(text|mathrm|mathbf|mathit|textbf|textit|operatorname|mathbb|mathcal|boldsymbol|bm|mathsf|vec|hat|bar|overline|tilde|ce|underline|overrightarrow|overleftarrow|boxed|cancel|xcancel)(?![a-zA-Z])""")
    private val cmdRe = Regex("""\\([a-zA-Z]+)""")

    private fun fracs(s0: String): String {
        var s = s0
        while (true) {
            val m = fracRe.find(s) ?: break
            val a = group(s, m.range.last + 1) ?: break
            val b = group(s, a[2]) ?: break
            val num = conv(s.substring(a[0], a[1]), true)
            val den = conv(s.substring(b[0], b[1]), true)
            val joined = if (m.groupValues[1] == "binom") "C(" + num + ", " + den + ")" else wrap(num) + "/" + wrap(den)
            s = s.substring(0, m.range.first) + joined + s.substring(b[2])
        }
        return s
    }

    private fun roots(s0: String): String {
        var s = s0
        while (true) {
            val i = s.indexOf("\\sqrt")
            if (i < 0) break
            var p = i + 5
            var idx = ""
            if (p < s.length && s[p] == '[') {
                val e = s.indexOf(']', p)
                if (e < 0) break
                idx = s.substring(p + 1, e)
                p = e + 1
            }
            val g = group(s, p) ?: break
            val inner = conv(s.substring(g[0], g[1]), true)
            val root = when (idx) {
                "" -> "\u221A"
                "3" -> "\u221B"
                "4" -> "\u221C"
                else -> "root" + idx
            }
            s = s.substring(0, i) + root + wrap(inner) + s.substring(g[2])
        }
        return s
    }

    private fun wrappers(s0: String): String {
        var s = s0
        while (true) {
            val m = wrapRe.find(s) ?: break
            val g = group(s, m.range.last + 1) ?: break
            val name = m.groupValues[1]
            val raw = s.substring(g[0], g[1])
            val inner = if (name == "ce") chem(raw) else conv(raw, true)
            val tail = when (name) {
                "vec", "overrightarrow" -> "\u20D7"
                "overleftarrow" -> "\u20D6"
                "boxed" -> "]"
                "hat" -> "\u0302"
                "bar", "overline" -> "\u0305"
                "tilde" -> "\u0303"
                else -> ""
            }
            val head = if (name == "boxed") "[" else ""
            s = s.substring(0, m.range.first) + head + inner + tail + s.substring(g[2])
        }
        return s
    }

    private fun commands(s0: String): String {
        var s = s0.replace("\\,", " ").replace("\\;", " ").replace("\\:", " ").replace("\\!", "").replace("\\ ", " ")
        s = cmdRe.replace(s) { m -> cmds[m.groupValues[1]] ?: m.groupValues[1] }
        return s
    }

    private fun script(isSup: Boolean, t: String): String {
        val map = if (isSup) sup else sub
        if (t.isNotEmpty() && t.all { map.containsKey(it) }) {
            val sb = StringBuilder()
            for (ch in t) sb.append(map[ch])
            return sb.toString()
        }
        if (isSup && t == "\u00B0") return t
        val body = if (t.length == 1) t else "(" + t + ")"
        return (if (isSup) "^" else "_") + body
    }

    private fun scripts(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if ((c == '^' || c == '_') && i + 1 < s.length) {
                val token: String
                val next: Int
                if (s[i + 1] == '{') {
                    val g = group(s, i + 1)
                    if (g == null) {
                        out.append(c)
                        i++
                        continue
                    }
                    token = s.substring(g[0], g[1])
                    next = g[2]
                } else {
                    token = s[i + 1].toString()
                    next = i + 2
                }
                out.append(script(c == '^', token))
                i = next
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun chem(raw: String): String {
        var s = raw.trim()
        s = s.replace("<=>", "\u21CC").replace("<->", "\u2194").replace("->", "\u2192").replace("<-", "\u2190")
        s = chargeRe.replace(s) { m -> script(true, m.groupValues[1]) }
        s = indexRe.replace(s) { m -> m.groupValues[1] + script(false, m.groupValues[2]) }
        return s.replace("{", "").replace("}", "")
    }

    private val chargeRe = Regex("""\^\{?([0-9]*[+-])\}?""")
    private val indexRe = Regex("""([A-Za-z)\]])(\d+)""")
    private val envRe = Regex("""\\begin\{([a-zA-Z*]+)\}([\s\S]*?)\\end\{\1\}""")

    private fun envs(s: String): String {
        return envRe.replace(s) { m ->
            val name = m.groupValues[1].removeSuffix("*")
            val rows = ArrayList<String>()
            for (r in m.groupValues[2].trim().split("\\\\")) {
                val t = conv(r.replace("&", "  "), true)
                if (t.isNotEmpty()) rows.add(t)
            }
            when (name) {
                "pmatrix" -> rows.joinToString("\n") { "( " + it + " )" }
                "bmatrix" -> rows.joinToString("\n") { "[ " + it + " ]" }
                "vmatrix" -> rows.joinToString("\n") { "| " + it + " |" }
                "cases" -> rows.joinToString("\n") { "{ " + it }
                else -> rows.joinToString("\n")
            }
        }
    }

    private fun conv(s0: String, withScripts: Boolean): String {
        var s = fracs(envs(s0))
        s = roots(s)
        s = wrappers(s)
        s = commands(s)
        if (withScripts) s = scripts(s)
        s = s.replace("{", "").replace("}", "")
        s = s.replace("\\\\", "\n").replace("&", " ")
        s = s.replace("\\", "")
        return s.trim()
    }

    private val blockRe = Regex("""\$\$([\s\S]+?)\$\$""")
    private val brackRe = Regex("""\\\[([\s\S]+?)\\\]""")
    private val parenRe = Regex("""\\\(([\s\S]+?)\\\)""")
    private val inlineRe = Regex("""(?<![\\$])\$(?=\S)([^$\n]+?)(?<=\S)\$(?!\d)""")

    fun clean(src: String): String {
        if (src.indexOf('\\') < 0 && src.indexOf('$') < 0) return src
        var s = src
        s = blockRe.replace(s) { m -> conv(m.groupValues[1], true) }
        s = brackRe.replace(s) { m -> conv(m.groupValues[1], true) }
        s = parenRe.replace(s) { m -> conv(m.groupValues[1], true) }
        s = inlineRe.replace(s) { m -> conv(m.groupValues[1], true) }
        if (s.indexOf('\\') >= 0) {
            s = commands(roots(fracs(s)))
        }
        return s
    }
}
