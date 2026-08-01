package com.example.data

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Singleton pembungkus TextToSpeech Android, dipakai buat baca notifikasi
 * masuk secara suara & celotehan pet sendiri. Dibuat singleton supaya engine TTS
 * cuma di-init sekali (bukan tiap ada notifikasi baru), dan bisa dipanggil dari
 * Service manapun.
 *
 * Suara diarahin paksa ke STREAM_NOTIFICATION (bukan STREAM_MUSIC default) supaya
 * TETAP keluar dari speaker HP walau ada headset Bluetooth (A2DP) yang lagi konek --
 * profil A2DP Bluetooth cuma nangkep STREAM_MUSIC, jadi stream lain kayak
 * notification/alarm biasanya tetap lewat speaker internal HP.
 *
 * Suara yang dipakai adalah suara yang SUDAH TERINSTALL di HP (dari Google TTS
 * atau engine TTS lain yang terpasang) — bukan suara custom/rekaman sendiri.
 */
object TtsSpeaker {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingContext: Context? = null

    fun init(context: Context) {
        if (tts != null) return
        pendingContext = context.applicationContext
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("id", "ID"))
                // Kalau bahasa Indonesia tidak tersedia di device, fallback ke default device
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                isReady = true

                // Terapkan suara yang sudah pernah dipilih Master sebelumnya (kalau ada)
                pendingContext?.let { ctx ->
                    val savedVoiceName = NotificationVoiceSettings.getSelectedVoiceName(ctx)
                    if (savedVoiceName != null) {
                        val match = tts?.voices?.firstOrNull { it.name == savedVoiceName }
                        if (match != null) {
                            tts?.voice = match
                        }
                    }
                }
            }
        }
    }

    /**
     * Daftar SEMUA suara yang tersedia di HP ini (dari engine TTS yang terpasang), apapun
     * bahasanya -- gak dibatesin cuma Bahasa Indonesia lagi, soalnya suara Bahasa Inggris/
     * Jepang/dll seringkali lebih natural kedengarannya, dan Master bisa aja mau pakai suara
     * itu buat baca teks Indonesia (aksennya beda, tapi banyak yang lebih suka).
     * Diurutin per bahasa dulu, baru per nama, biar suara Indonesia tetap gampang ditemuin
     * di antara yang lain.
     * Kalau kosong, berarti HP belum punya paket suara apapun -- bisa ditambah lewat
     * Settings > System > Languages > Text-to-speech output > Install voice data.
     */
    fun getAvailableVoices(): List<Voice> {
        if (!isReady) return emptyList()
        val allVoices = tts?.voices ?: return emptyList()
        return allVoices
            .filter { !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.displayLanguage }, { it.name }))
    }

    fun getCurrentVoiceName(): String? = tts?.voice?.name

    /** Pilih & simpan suara baru. Persist ke SharedPreferences supaya tetap kepilih walau app ditutup. */
    fun selectVoice(context: Context, voice: Voice) {
        tts?.voice = voice
        NotificationVoiceSettings.setSelectedVoiceName(context, voice.name)
    }

    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        val ctx = pendingContext
        if (ctx != null && PetVoiceSettings.isMuted(ctx)) return // Master lagi mute-in suara pet

        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) return

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
        }
        tts?.speak(cleanText, TextToSpeech.QUEUE_ADD, params, System.currentTimeMillis().toString())
    }

    /**
     * Bersihin simbol-simbol dekoratif dari teks sebelum dikirim ke TTS, soalnya kalau
     * gak dibersihin, engine TTS (apapun -- lokal, ElevenLabs, Google Cloud, dll) bakal
     * baca simbolnya literal (misal "~" dibaca "gelombang", "*" dibaca "asterisk", kaomoji
     * kayak "( •`.•` )" dibaca acak-acakan). Teks ASLI tetap dipakai buat tampilan bubble --
     * ini cuma versi yang dikirim ke suara doang.
     *
     * Strateginya WHITELIST (bukan buang satu-satu simbol yang ketauan bermasalah, soalnya
     * gak bakal pernah kekejar semua kemungkinan): cuma pertahanin huruf, angka, spasi, dan
     * tanda baca kalimat dasar. Apapun di luar itu (emoji, kaomoji, asterisk, tilde, dll)
     * otomatis kebuang, apapun bentuknya.
     */
    private fun sanitizeForSpeech(text: String): String {
        var cleaned = text
            .replace(Regex("[^\\p{L}\\p{N}\\s.,!?'-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        // Kalau abis dibersihin cuma nyisain tanda baca doang (misal sisa kaomoji yang
        // udah dikupas simbolnya), anggap kosong -- daripada TTS baca "titik" sendirian.
        if (cleaned.isNotBlank() && cleaned.none { it.isLetterOrDigit() }) {
            cleaned = ""
        }
        return cleaned
    }

    /** Coba baca 1 kalimat contoh pakai suara yang lagi aktif, buat preview di UI. */
    fun speakPreview(text: String = "Halo Master, ini contoh suaraku~") {
        speak(text)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    /**
     * Maksa putus & nyambung ulang ke engine TTS -- dipakai kalau Master baru aja ganti
     * "Preferred engine" TTS di Settings HP (misal ke NekoSpeak/Sherpa-ONNX), soalnya
     * koneksi TTS yang lama nempel terus ke engine LAMA (Google TTS bawaan HP) selama
     * service masih hidup di background, gak otomatis pindah sendiri.
     */
    fun reconnectToSystemEngine(context: Context) {
        shutdown()
        init(context)
    }
}
