package com.nightxd.zerofloating

import android.content.Context

object Mode {
    const val READER = "reader"
    const val OVERLAY = "overlay"
    const val NORMAL = "normal"

    fun label(m: String): String = when (m) {
        READER -> "Baca manhwa dan novel"
        OVERLAY -> "Overlay luar aplikasi"
        else -> "Mode biasa"
    }

    fun short(m: String): String = when (m) {
        READER -> "Baca"
        OVERLAY -> "Overlay"
        else -> "Biasa"
    }

    fun about(m: String): String = when (m) {
        READER -> "Web dibuka di dalam aplikasi. Teks dan gambar halaman langsung ditimpa dengan terjemahan bahasa pilihanmu."
        OVERLAY -> "Menu mengambang muncul di atas aplikasi lain. Crop bagian layar mana saja, Zero langsung menjawab."
        else -> "Obrolan layar penuh dengan Zero. Tanya apa saja atau kirim foto dan file, tanpa membuka web."
    }
}

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("zero_prefs", Context.MODE_PRIVATE)

    private fun str(k: String, d: String): String {
        val v = sp.getString(k, d)
        return if (v.isNullOrBlank()) d else v
    }

    private fun put(k: String, v: String) {
        sp.edit().putString(k, v).apply()
    }

    var provider: String
        get() = str("provider", "google")
        set(v) { put("provider", v) }

    fun keyOf(p: String): String = if (p == "groq") str("groq_key", "") else str("api_key", "")

    fun setKeyOf(p: String, v: String) {
        put(if (p == "groq") "groq_key" else "api_key", v.trim())
    }

    var apiKey: String
        get() = keyOf(provider)
        set(v) { setKeyOf(provider, v) }

    var model: String
        get() = if (provider == "groq") str("groq_model", Models.GROQ_DEFAULT) else str("model", Models.DEFAULT)
        set(v) { put(if (provider == "groq") "groq_model" else "model", v.trim()) }

    var groqVisionModel: String
        get() = str("groq_vision", Models.GROQ_VISION)
        set(v) { put("groq_vision", v.trim()) }

    var panelOpacity: Int
        get() = sp.getInt("panel_opacity", 100)
        set(v) { sp.edit().putInt("panel_opacity", v).apply() }

    var trMode: String
        get() = str("tr_mode", "fast")
        set(v) { put("tr_mode", v) }

    var panelWidth: Int
        get() = sp.getInt("panel_width", 80)
        set(v) { sp.edit().putInt("panel_width", v).apply() }

    var chatHeight: Int
        get() = sp.getInt("chat_height", 42)
        set(v) { sp.edit().putInt("chat_height", v).apply() }

    var cardHeight: Int
        get() = sp.getInt("card_height", 26)
        set(v) { sp.edit().putInt("card_height", v).apply() }

    var panelPos: String
        get() = str("panel_pos", "top")
        set(v) { put("panel_pos", v) }

    var cardPos: String
        get() = str("card_pos", "bottom")
        set(v) { put("card_pos", v) }

    var fontSize: Int
        get() = sp.getInt("font_size", 14)
        set(v) { sp.edit().putInt("font_size", v).apply() }

    var bubbleDim: Boolean
        get() = sp.getBoolean("bubble_dim", true)
        set(v) { sp.edit().putBoolean("bubble_dim", v).apply() }

    var url: String
        get() = str("url", "")
        set(v) { put("url", v.trim()) }

    var mode: String
        get() = str("mode", Mode.NORMAL)
        set(v) { put("mode", v) }

    var lang: String
        get() = str("lang", "Indonesia")
        set(v) { put("lang", v.trim()) }

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) { sp.edit().putBoolean("setup_done", v).apply() }

    var auto: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) { sp.edit().putBoolean("auto", v).apply() }

    var quiet: Boolean
        get() = sp.getBoolean("quiet", false)
        set(v) { sp.edit().putBoolean("quiet", v).apply() }

    var think: Boolean
        get() = sp.getBoolean("think", false)
        set(v) { sp.edit().putBoolean("think", v).apply() }

    var level: String
        get() = str("level", "low")
        set(v) { put("level", v) }

    var bubbleOpacity: Int
        get() = sp.getInt("bubble_opacity", 100)
        set(v) { sp.edit().putInt("bubble_opacity", v).apply() }

    var bubbleSize: Int
        get() = sp.getInt("bubble_size", 56)
        set(v) { sp.edit().putInt("bubble_size", v).apply() }

    var bubbleSnap: Boolean
        get() = sp.getBoolean("bubble_snap", true)
        set(v) { sp.edit().putBoolean("bubble_snap", v).apply() }

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) { sp.edit().putInt("bubble_x", v).apply() }

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) { sp.edit().putInt("bubble_y", v).apply() }

    companion object {
        const val DEFAULT_MODEL = Models.DEFAULT
    }
}
