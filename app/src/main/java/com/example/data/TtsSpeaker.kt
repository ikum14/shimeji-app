package com.example.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Singleton pembungkus TextToSpeech Android, dipakai buat baca notifikasi
 * masuk secara suara. Dibuat singleton supaya engine TTS cuma di-init sekali
 * (bukan tiap ada notifikasi baru), dan bisa dipanggil dari Service manapun.
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
     * Daftar suara Bahasa Indonesia yang tersedia di HP ini (dari engine TTS yang terpasang).
     * Kalau kosong, berarti HP belum punya paket suara Indonesia — bisa ditambah lewat
     * Settings > System > Languages > Text-to-speech output > Install voice data.
     */
    fun getAvailableIndonesianVoices(): List<Voice> {
        if (!isReady) return emptyList()
        val allVoices = tts?.voices ?: return emptyList()
        val indonesianVoices = allVoices.filter {
            !it.isNetworkConnectionRequired &&
                (it.locale.language == "id" || it.locale.language == "in")
        }
        return indonesianVoices.sortedBy { it.name }
    }

    fun getCurrentVoiceName(): String? = tts?.voice?.name

    /** Pilih & simpan suara baru. Persist ke SharedPreferences supaya tetap kepilih walau app ditutup. */
    fun selectVoice(context: Context, voice: Voice) {
        tts?.voice = voice
        NotificationVoiceSettings.setSelectedVoiceName(context, voice.name)
    }

    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, System.currentTimeMillis().toString())
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
}
