package com.nightxd.zerofloating

class ModelInfo(val id: String, val name: String, val note: String)

object Models {
    const val DEFAULT = "gemini-3.8-flash"
    const val GROQ_DEFAULT = "llama-3.3-70b-versatile"
    const val GROQ_VISION = "meta-llama/llama-4-scout-17b-16e-instruct"

    val groq: List<ModelInfo> = listOf(
        ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", "Serba bisa dan stabil. Teks saja."),
        ModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B Instant", "Paling cepat dan hemat. Teks saja."),
        ModelInfo("openai/gpt-oss-120b", "GPT-OSS 120B", "Besar dan bisa berpikir. Teks saja."),
        ModelInfo("openai/gpt-oss-20b", "GPT-OSS 20B", "Ringan dan bisa berpikir. Teks saja."),
        ModelInfo("qwen/qwen3-32b", "Qwen3 32B", "Bisa berpikir. Teks saja."),
        ModelInfo("meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout", "Bisa baca gambar."),
        ModelInfo("meta-llama/llama-4-maverick-17b-128e-instruct", "Llama 4 Maverick", "Bisa baca gambar. Bisa saja sudah dihapus Groq.")
    )

    fun list(provider: String): List<ModelInfo> = if (provider == "groq") groq else all

    fun defaultFor(provider: String): String = if (provider == "groq") GROQ_DEFAULT else DEFAULT

    fun isVision(id: String): Boolean {
        val m = id.lowercase()
        return m.contains("llama-4") || m.contains("scout") || m.contains("maverick") ||
            m.contains("vision") || m.contains("-vl")
    }

    val all: List<ModelInfo> = listOf(
        ModelInfo("gemini-3.8-flash", "Gemini 3.8 Flash", "Terbaru. Cepat, pintar, paling pas buat harian. Default."),
        ModelInfo("gemini-4-argon", "Gemini 4 Argon", "Flagship generasi 4. Penalaran paling dalam, agak lebih lambat."),
        ModelInfo("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Seri Pro untuk soal berat. Status preview."),
        ModelInfo("gemini-3.6-flash", "Gemini 3.6 Flash", "Flash generasi sebelumnya. Stabil dan seimbang."),
        ModelInfo("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Paling cepat dan hemat di seri 3.5."),
        ModelInfo("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "Ringan dan murah buat tanya singkat."),
        ModelInfo("gemini-3-flash-preview", "Gemini 3 Flash", "Flash seri 3 versi preview."),
        ModelInfo("gemini-3.5-flash", "Gemini 3.5 Flash", "Lama. Google mengalihkannya otomatis ke 3.6 Flash."),
        ModelInfo("gemini-flash-latest", "Flash Latest", "Alias yang selalu menunjuk ke Flash terbaru."),
        ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", "Lama. Akses dibatasi untuk akun yang pernah memakainya."),
        ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", "Lama. Akses dibatasi untuk akun yang pernah memakainya."),
        ModelInfo("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "Lama. Akses dibatasi untuk akun yang pernah memakainya.")
    )

    fun fallbacks(id: String, groq: Boolean, vision: Boolean): List<String> {
        val pool = if (groq) {
            if (vision) listOf(GROQ_VISION, "meta-llama/llama-4-maverick-17b-128e-instruct")
            else listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
        } else {
            listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")
        }
        val out = ArrayList<String>()
        for (m in pool) {
            if (m != id && out.size < 2) out.add(m)
        }
        return out
    }

    fun nameOf(id: String): String {
        for (m in all) {
            if (m.id == id) return m.name
        }
        for (m in groq) {
            if (m.id == id) return m.name
        }
        return id
    }
}
