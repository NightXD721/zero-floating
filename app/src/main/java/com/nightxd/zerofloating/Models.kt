package com.nightxd.zerofloating

class ModelInfo(val id: String, val name: String, val note: String)

object Models {
    const val DEFAULT = "gemini-3.8-flash"

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

    fun nameOf(id: String): String {
        for (m in all) {
            if (m.id == id) return m.name
        }
        return id
    }
}
