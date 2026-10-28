package com.nightxd.zerofloating

object Zero {
    fun system(lang: String): String {
        return "Kamu adalah Zero, asisten AI yang pintar, teliti, dan jujur. " +
            "Jawab pertanyaan apa pun dengan akurat dan langsung ke inti. " +
            "Kalau tidak yakin atau datanya tidak ada, katakan terus terang dan jangan mengarang. " +
            "Balas dalam bahasa yang dipakai pengguna. " +
            "Kalau ada gambar, baca isinya dengan teliti sebelum menjawab. " +
            "Untuk soal hitungan atau logika, kerjakan langkah demi langkah lalu beri hasil akhir yang jelas. " +
            "Gunakan teks biasa, boleh **tebal** dan daftar dengan tanda minus. Jangan pakai emoji. " +
            "Bahasa terjemahan pilihan pengguna adalah " + lang + "."
    }

    fun askImage(): String {
        return "Lihat gambar ini. Jelaskan isinya dengan singkat dan akurat. " +
            "Kalau ada pertanyaan, soal, atau tugas di dalam gambar, jawab langsung."
    }

    fun translateImage(lang: String): String {
        return "Terjemahkan semua teks yang terlihat pada gambar ini ke bahasa " + lang + ". " +
            "Ikuti urutan baca yang wajar. Untuk dialog tulis satu baris per ucapan, " +
            "sebutkan pembicara hanya kalau jelas. Jangan menambah komentar sendiri. " +
            "Kalau tidak ada teks sama sekali, tulis: Tidak ada teks."
    }

    fun translateText(lang: String, text: String): String {
        return "Terjemahkan teks berikut ke bahasa " + lang + ". " +
            "Pertahankan paragraf dan urutan, jaga gaya bahasa asli, dan jangan menambah komentar.\n\n" + text
    }

    fun summarize(text: String): String {
        return "Ringkas halaman web ini dengan jelas dan akurat. Isi halaman:\n\n" + text
    }
}
