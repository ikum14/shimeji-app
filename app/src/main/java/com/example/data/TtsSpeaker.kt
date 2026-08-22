package com.example.data

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    // Callback per utterance ID -- pakai Map (bukan 1 variabel) biar aman kalau ada beberapa
    // speak() jalan "bersamaan" (misal notifikasi WA masuk pas lagi bacain file panjang),
    // gak saling numpuk/ketiban satu sama lain.
    private val pendingDoneCallbacks = mutableMapOf<String, () -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

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
                ensureUtteranceListener() // pasang ulang tiap kali instance TTS baru kebentuk

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
     * Daftar SEMUA engine TTS yang terpasang di HP ini. Query LANGSUNG ke PackageManager
     * (bukan lewat `tts.engines` bawaan API TextToSpeech) -- soalnya `tts.engines` ternyata
     * GAK RELIABLE nemuin sebagian engine third-party (misal VoxSherpa) yang metadata-nya
     * gak lengkap sesuai yang diharapkan API tinggi itu, walau enginenya beneran terinstall
     * & jalan normal kalau ditest langsung dari Settings HP.
     */
    fun getInstalledEngines(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = android.content.Intent("android.intent.action.TTS_SERVICE")
        val resolveInfos = try {
            pm.queryIntentServices(intent, android.content.pm.PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }
        return resolveInfos
            .mapNotNull { info ->
                val packageName = info.serviceInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString() ?: packageName
                label to packageName
            }
            .distinctBy { it.second }
            .sortedBy { it.first }
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

    /** Pasang listener yang beneran dengerin sinyal "kelar ngomong" dari engine TTS -- dipasang
     * sekali aja (persist walau ganti engine, soalnya tts instance-nya bisa diganti tapi
     * listener-nya kita pasang ulang tiap kali connectToEngine() jalan). */
    private fun ensureUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) = fireDoneCallback(utteranceId)

            @Deprecated("Deprecated in Java, tapi tetap wajib di-override buat API lama")
            override fun onError(utteranceId: String?) = fireDoneCallback(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = fireDoneCallback(utteranceId)
        })
    }

    private fun fireDoneCallback(utteranceId: String?) {
        if (utteranceId == null) return
        val callback = synchronized(pendingDoneCallbacks) { pendingDoneCallbacks.remove(utteranceId) } ?: return
        // Listener TTS jalan di thread background-nya sendiri -- lempar ke main thread
        // biar aman dipakai caller buat sentuh UI/state Compose tanpa mikirin threading.
        mainHandler.post { callback() }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady || text.isBlank()) {
            onDone?.invoke()
            return
        }
        val ctx = pendingContext
        if (ctx == null || PetVoiceSettings.isMuted(ctx)) {
            onDone?.invoke() // tetep panggil onDone walau mute, biar caller (misal mode baca) gak nyangkut nunggu selamanya
            return
        }

        tts?.setPitch(TtsVoiceSettings.getPitch(ctx))
        tts?.setSpeechRate(TtsVoiceSettings.getSpeed(ctx))

        // FIX: locale suara sebelumnya di-set SEKALI doang pas engine connect (hardcode
        // Indonesia), gak pernah ke-update lagi walau toggle bahasa diubah -- makanya
        // teks Inggris tetep dibacain pake "logat" Indonesia. Sekarang dicek ULANG tiap
        // kali speak() dipanggil, ngikutin toggle bahasa yang aktif saat itu.
        val targetLocale = if (TtsVoiceSettings.getLanguage(ctx) == "en") {
            Locale.US
        } else {
            Locale("id", "ID")
        }
        val langResult = tts?.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Data suara buat bahasa itu belum ada di HP -- biarin pakai locale
            // sebelumnya (device default) daripada gagal total ngomong.
            tts?.language = Locale.getDefault()
        }

        val pauseAtEmoji = TtsVoiceSettings.getPauseAtEmoji(ctx)
        val segments = buildSpeechSegments(text, pauseAtEmoji)
        if (segments.isEmpty()) {
            onDone?.invoke()
            return
        }

        val pauseMs = TtsVoiceSettings.getPauseMs(ctx)
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
        }
        val baseId = System.currentTimeMillis().toString()
        val lastSegmentId = "$baseId-${segments.lastIndex}"
        if (onDone != null) {
            synchronized(pendingDoneCallbacks) { pendingDoneCallbacks[lastSegmentId] = onDone }
        }

        segments.forEachIndexed { index, segment ->
            tts?.speak(segment, TextToSpeech.QUEUE_ADD, params, "$baseId-$index")
            if (pauseMs > 0 && index != segments.lastIndex) {
                tts?.playSilentUtterance(pauseMs, TextToSpeech.QUEUE_ADD, "$baseId-$index-pause")
            }
        }
    }

    /**
     * Pecah teks jadi beberapa segmen yang dipisah tempat-tempat yang butuh jeda:
     * (1) tiap ketemu akhir kalimat (. ! ?), (2) tiap ketemu simbol/emoji yang dibuang
     * (kalau `pauseAtEmoji` aktif) -- biar "beat" emosinya masih kerasa walau emoji-nya
     * sendiri gak dibaca literal (gak ada TTS yang bisa baca emoji dengan benar).
     * Ini WHITELIST filter: cuma huruf, angka, spasi, & tanda baca dasar yang dipertahanin,
     * sisanya (simbol/emoji/kaomoji apapun bentuknya) otomatis jadi titik pisah segmen.
     */
    private fun buildSpeechSegments(rawText: String, pauseAtEmoji: Boolean): List<String> {
        val sentenceEnders = setOf('.', '!', '?')
        val allowedPunctuation = setOf(',', '.', '!', '?', '\'', '-')
        val segments = mutableListOf<String>()
        var current = StringBuilder()

        for (ch in rawText) {
            // Cuma huruf Latin (a-z/A-Z) & angka 0-9 yang dianggap "huruf beneran" -- huruf dari
            // alfabet lain (Yunani/omega di kaomoji "(• ̀ω•́ )", Cyrillic, dll) TETAP dianggap
            // simbol dekoratif yang harus dibuang, walau secara Unicode itu masih "isLetter()".
            val isAllowed = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch.isWhitespace() || ch in allowedPunctuation
            if (isAllowed) {
                current.append(ch)
                if (ch in sentenceEnders) {
                    segments.add(current.toString())
                    current = StringBuilder()
                }
            } else if (pauseAtEmoji && current.isNotBlank()) {
                segments.add(current.toString())
                current = StringBuilder()
            }
        }
        if (current.isNotBlank()) segments.add(current.toString())

        return segments
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
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
