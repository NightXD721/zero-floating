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
        READER -> "Web dibuka di dalam aplikasi. Gambar halaman diterjemahkan otomatis ke bahasa pilihanmu."
        OVERLAY -> "Menu mengambang muncul di atas aplikasi lain. Crop bagian layar mana saja, Zero langsung menjawab."
        else -> "Web dibuka di dalam aplikasi lengkap dengan menu Zero: tanya, crop, ringkas, dan terjemah."
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

    var apiKey: String
        get() = str("api_key", "")
        set(v) { put("api_key", v.trim()) }

    var model: String
        get() = str("model", DEFAULT_MODEL)
        set(v) { put("model", v.trim()) }

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
