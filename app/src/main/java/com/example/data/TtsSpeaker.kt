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

    private const val PREFS_NAME = "pet_tts_engine_prefs"
    private const val KEY_ENGINE_PACKAGE = "selected_engine_package"

    fun init(context: Context) {
        if (tts != null) return
        pendingContext = context.applicationContext
        val savedEngine = getSelectedEnginePackage(context)
        connectToEngine(context, savedEngine)
    }

    /**
     * Bikin instance TextToSpeech baru, terhubung ke engine tertentu.
     * Kalau `enginePackage` null, biarin Android nebak default sendiri (perilaku lama) --
     * tapi ini TERBUKTI GAK RELIABLE di beberapa HP (misal HyperOS), makanya begitu Master
     * milih engine secara EKSPLISIT lewat dashboard, kita selalu pakai constructor 3-argumen
     * yang maksa Android connect ke package itu persis, gak nebak-nebak lagi.
     */
    private fun connectToEngine(context: Context, enginePackage: String?) {
        val ctx = context.applicationContext
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("id", "ID"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                isReady = true

                val savedVoiceName = NotificationVoiceSettings.getSelectedVoiceName(ctx)
                if (savedVoiceName != null) {
                    val match = tts?.voices?.firstOrNull { it.name == savedVoiceName }
                    if (match != null) tts?.voice = match
                }
            } else {
                isReady = false
            }
        }
        tts = if (enginePackage != null) {
            TextToSpeech(ctx, listener, enginePackage)
        } else {
            TextToSpeech(ctx, listener)
        }
    }

    /**
     * Daftar SEMUA engine TTS yang terpasang di HP ini (nama package + label yang kelihatan
     * di Settings, misal "VoxSherpa TTS", "Google Text-to-speech", dll). Perlu instance TTS
     * yang udah ke-init dulu (apapun engine-nya) buat query daftar ini -- jadi kalau belum
     * pernah init() sama sekali, hasilnya kosong.
     */
    fun getInstalledEngines(): List<TextToSpeech.EngineInfo> {
        return tts?.engines ?: emptyList()
    }

    fun getSelectedEnginePackage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ENGINE_PACKAGE, null)
    }

    /**
     * Pindah SECARA EKSPLISIT ke engine tertentu (by package name), disimpen permanen
     * biar dipakai lagi otomatis pas app dibuka ulang. Ini beda dari reconnectToSystemEngine()
     * yang cuma nebak ulang default sistem -- ini maksa connect ke package yang Master
     * pilih sendiri, jadi PASTI kepakai walau deteksi "default engine" Android lagi ngaco.
     */
    fun switchToEngine(context: Context, enginePackage: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE_PACKAGE, enginePackage)
            .apply()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        connectToEngine(context, enginePackage)
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
     * Maksa putus & nyambung ulang ke engine TTS -- kalau Master udah pernah milih engine
     * eksplisit lewat dashboard, ini bakal connect ke situ lagi. Kalau belum pernah milih
     * sama sekali, coba nebak default sistem (perilaku lama, gak selalu reliable).
     */
    fun reconnectToSystemEngine(context: Context) {
        val saved = getSelectedEnginePackage(context)
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        connectToEngine(context, saved)
    }
}
