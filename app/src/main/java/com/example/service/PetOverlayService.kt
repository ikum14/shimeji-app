package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.model.PetQuotes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class PetBehaviorState { IDLE, WALK_LEFT, WALK_RIGHT, CLIMB_UP, CLIMB_DOWN }

class PetOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val speechBubbleTextState = mutableStateOf("Halo Master! Seret aku ke atas ya~")
    private val bubbleMoodState = mutableStateOf("Senang")


    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var petImage: ImageView? = null
    private var speechCard: View? = null
    private var hideButton: TextView? = null
    private var showButtonPill: View? = null
    private var chatButton: TextView? = null
    private var petContainerRef: LinearLayout? = null

    /** speechCard sekarang jadi window WindowManager TERPISAH dari window pet utama
     * (bukan child di petContainer lagi) -- biar area "kotak lebar" bubble-nya gak
     * pernah nge-block sentuhan ke app di bawahnya. Window ini dikasih FLAG_NOT_TOUCHABLE,
     * jadi APAPUN ukurannya, sentuhan tembus lurus ke apapun di baliknya. */
    private var speechBubbleParams: WindowManager.LayoutParams? = null

    /** Panel chat -- window WindowManager TERPISAH dari pet (bukan nempel di petContainer),
     * supaya bisa FOCUSABLE (nerima keyboard) tanpa ngubah flag window pet utama sama sekali.
     * null kalau lagi ketutup. */
    private var chatOverlayView: View? = null
    private var chatOverlayParams: WindowManager.LayoutParams? = null
    private val chatMessagesState = mutableStateOf(listOf<String>())
    private val chatSendingState = mutableStateOf(false)

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var idleTimerJob: Job? = null
    private var behaviorJob: Job? = null

    private var isDragging = false
    private var isDraggingBerontak = false // udah ganti pose berontak apa belum (biar gak load ulang tiap ACTION_MOVE)
    private val DRAG_BERONTAK_THRESHOLD_MS = 3000L
    private val DRAG_HOLD_THRESHOLD_MS = 2500L // tahan sekian lama dulu baru drag beneran aktif -- biar tap sekilas gak ketriger drag
    private var isPetHidden = false

    /** Job & durasi buat mekanisme "intip" -- pet nongol sebentar pas disembunyiin, lalu balik sembunyi lagi. */
    private val HIDE_TRANSITION_VISIBLE_MS = 6_500L // jeda gif pintu (hide/muncul), sebelum baru intip nutup pakai onDone TTS (lihat closePeekIfNeeded)

    private var petSizePx = 0
    private var behaviorState = PetBehaviorState.IDLE

    /** Waktu terakhir berhasil kirim request ke Gemini, buat cooldown biar gak boros kuota. */
    private var lastSmartDialogRequestTime = 0L
    private val SMART_DIALOG_COOLDOWN_MS = 60_000L // 1 menit jarak minimal antar request AI

    /** Waktu terakhir pet ngoceh pakai kalimat template (gratis, gak manggil API). */
    private var lastIdleChatterTime = 0L

    /** True selagi pet lagi "bacain" isi file baru dari vault -- ngoceh mood biasa dijeda dulu
     * sampai kelar, biar gak keselip di tengah bacaan. */
    private var isReadingVaultFile = false
    private var lastVaultReadCheckTime = 0L
    private val VAULT_READ_CHECK_INTERVAL_MS = 15_000L // cek file baru tiap segini

    /** Waktu terakhir pet nyelipin trivia Wikipedia/headline RSS, biar gak keseringan. */
    private var lastKnowledgeShareTime = 0L
    // Interval-nya sekarang diatur user lewat slider di dashboard (IdleChatterSettings),
    // dibaca live tiap tick -- BUKAN angka mati lagi.

    /** Jendela waktu: Gemini cuma boleh dipanggil otomatis kalau pet DISENTUH dalam X ms terakhir. */
    private val RECENT_INTERACTION_WINDOW_MS = 60_000L // 1 menit
    // Jadwal mood (Bosan/Kesal/Marah/Ngantuk/Tidur/Bangun/Hide) sekarang diatur lewat
    // MoodTimingSettings, bukan konstanta mati -- bisa diubah langsung dari dashboard.

    private var behaviorTicksRemaining = 0

    // Leveling & Emotion Timer States for Overlay Pet (Timestamp based for Doze Mode safety)
    private var petLevel = 1
    private var petXp = 0
    private val maxXp = com.example.data.PetProgressStore.MAX_XP_PER_LEVEL
    private var petEmotion: String = "Senang" // "Senang", "Bosan", "Kesal"
        set(value) {
            field = value
            bubbleMoodState.value = value
            val slot = when (value) {
                "Bosan" -> com.example.model.PoseSpriteManager.PoseSlot.IDLE_NGANTUK
                "Kesal" -> com.example.model.PoseSpriteManager.PoseSlot.IDLE_TIDUR
                else -> com.example.model.PoseSpriteManager.PoseSlot.IDLE_DIAM
            }
            if (!isDragging && !isPetHidden) updatePetSpriteForPose(slot)
        }
    private var lastInteractionTimestamp = System.currentTimeMillis()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        com.example.data.BubbleSettings.init(applicationContext)

        // Jaring pengaman: minta rebind listener notifikasi tiap kali service utama ini nyala
        // (termasuk kalau sebelumnya proses app-nya mati total/ke-restart paksa) -- biar
        // fitur baca notif WA/Telegram gak perlu Master matiin-nyalain izin manual lagi.
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, com.example.service.PetNotificationListenerService::class.java)
            )
        } catch (e: Exception) {
            // Gak fatal kalau gagal -- listener biasanya tetap nyambung normal lewat jalur biasa
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        createFloatingPetOverlay()
        petLevel = com.example.data.PetProgressStore.getLevel(applicationContext)
        petXp = com.example.data.PetProgressStore.getXp(applicationContext)
        listenForNotificationBus()
        listenForPetDataBus()
        startIdleEmotionTimer()
        // Autonomous walk/climb dimatikan atas request user — pet diam di tempat, tetap bisa di-drag manual.
        // startAutonomousBehaviorLoop()
        com.example.data.TtsSpeaker.init(applicationContext)
        com.example.data.PetQuoteSettings.ensureTemplateExists()
        com.example.data.BubbleStyleSettings.init(applicationContext)
        com.example.model.PoseSpriteManager.init(applicationContext)
        updatePetSprite(held = false) // Terapkan kostum tersimpan begitu overlay muncul
        serviceScope.launch {
            com.example.model.CostumeManager.kostumAktif.collect {
                updatePetSprite(held = false) // Update langsung tiap kostum diganti dari dashboard
            }
        }
        serviceScope.launch {
            com.example.model.PetDataBus.syncFlow.collect { sync ->
                // Serap update dari dashboard (misal tombol "Elus Pet") biar overlay nggak nyimpen angka basi
                if (sync.petLevel != petLevel || sync.petXp != petXp) {
                    petLevel = sync.petLevel
                    petXp = sync.petXp
                    updatePetSprite(held = false)
                }
            }
        }
    }

    private fun syncToObsidian() {
        try {
            val data = com.example.data.PetProgressData(
                petName = com.example.data.PetProgressStore.getName(applicationContext),
                level = petLevel,
                currentXp = petXp,
                maxXp = maxXp,
                emotion = petEmotion,
                happinessLevel = if (petEmotion == "Senang") 95 else if (petEmotion == "Bosan") 50 else 25,
                energyLevel = 88,
                positionX = (params?.x ?: 200).toFloat(),
                positionY = (params?.y ?: 300).toFloat(),
                physicsMode = "STAIR_STEP",
                totalInteractions = 40
            )
            com.example.data.ObsidianPetExporter.saveProgressToFile(this, data)

            // Share data with main app (FlutterOverlayWindow.shareData equivalent)
            com.example.model.PetDataBus.shareData(
                level = petLevel,
                xp = petXp,
                emotion = petEmotion,
                speechMessage = speechBubbleTextState.value
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Bahasa kalimat pet yang lagi aktif ("id"/"en"), dibaca fresh tiap dipanggil biar langsung
     * kepakai begitu Master ganti di Setelan, tanpa perlu restart service. */
    private fun currentLanguage(): String = com.example.data.TtsVoiceSettings.getLanguage(applicationContext)

    /** Kategori custom quote (pet-quotes.md) dikasih akhiran "_en" pas mode Inggris, biar gak
     * nyampur sama kalimat Indonesia yang udah kesimpen di kategori dasar yang sama. */
    private fun quoteCategory(base: String): String =
        if (currentLanguage() == "en") "${base}_en" else base

    private fun handleUserInteraction(addedXp: Int = 5) {
        lastInteractionTimestamp = System.currentTimeMillis()
        petEmotion = "Senang"
        petXp += addedXp
        if (petXp >= maxXp) {
            petLevel++
            petXp %= maxXp
            speakBubble("🎉 LEVEL UP! Sekarang Level $petLevel!")
            android.widget.Toast.makeText(this, "🎉 LEVEL UP! Pet menjadi Level $petLevel!", android.widget.Toast.LENGTH_SHORT).show()
        }
        syncToObsidian()
        com.example.data.PetProgressStore.save(applicationContext, petLevel, petXp)
        // Broadcast juga ke dashboard biar kalau lagi dibuka bareng, langsung update tanpa perlu buka-tutup app
        com.example.model.PetDataBus.shareData(
            level = petLevel,
            xp = petXp,
            emotion = petEmotion,
            speechMessage = speechBubbleTextState.value
        )
    }

    /**
     * Minta Gemini bikin kalimat celotehan baru berdasarkan biodata.md + SEMUA file .md
     * lain di vault pet-virtual + status pet saat ini. Semua file dibaca ULANG dari disk
     * tiap kali fungsi ini jalan (real-time) — bukan dari cache lama — supaya perubahan
     * terbaru yang kamu tulis di Obsidian langsung kepakai tanpa perlu buka dashboard dulu.
     * Dipanggil dari 2 tempat: (1) tap langsung ke pet, (2) idle chatter timer kalau pet
     * baru aja disentuh. Ada cooldown 1 menit antar request biar kuota API gak cepet abis
     * (lihat SMART_DIALOG_COOLDOWN_MS).
     * Kalimat template (PetQuotes) tetap tampil dulu sebagai placeholder instan,
     * lalu ditimpa begitu balasan AI datang (butuh beberapa detik, ada koneksi internet).
     */
    /**
     * Minta Gemini bikin kalimat celotehan baru berdasarkan biodata.md + SEMUA file .md
     * lain di vault pet-virtual + status pet saat ini. Semua file dibaca ULANG dari disk
     * tiap kali fungsi ini jalan (real-time) — bukan dari cache lama — supaya perubahan
     * terbaru yang kamu tulis di Obsidian langsung kepakai tanpa perlu buka dashboard dulu.
     * Dipanggil dari 2 tempat: (1) tap langsung ke pet, (2) idle chatter timer kalau pet
     * baru aja disentuh. Ada cooldown 1 menit antar request biar kuota API gak cepet abis
     * (lihat SMART_DIALOG_COOLDOWN_MS).
     * Kalimat template (PetQuotes) tetap tampil dulu sebagai placeholder instan,
     * lalu ditimpa begitu balasan AI datang (butuh beberapa detik, ada koneksi internet).
     * Kalimat AI yang berhasil didapat juga otomatis DISIMPAN ke pet-quotes.md di kategori
     * `category`, supaya ke depannya ikut kepakai lagi sebagai template gratis.
     */
    /**
     * Cek berkala apa waktunya nyelipin trivia Wikipedia acak / headline RSS terbaru.
     * Dua-duanya GRATIS (gak kayak Google Search grounding yang berbayar). Kalau Gemini
     * dikonfigurasi, info-nya diselipin natural lewat AI; kalau enggak, dibacain apa
     * adanya pakai template sederhana.
     */
    private fun checkForKnowledgeShare() {
        val ctx = applicationContext
        val triviaOn = com.example.data.KnowledgeSettings.isTriviaEnabled(ctx)
        val rssOn = com.example.data.KnowledgeSettings.isRssEnabled(ctx)
        if (!triviaOn && !rssOn) return
        if (isPetHidden || isReadingVaultFile) return

        val now = System.currentTimeMillis()
        val intervalMs = com.example.data.KnowledgeSettings.getIntervalMinutes(ctx) * 60_000L
        if (now - lastKnowledgeShareTime < intervalMs) return
        lastKnowledgeShareTime = now

        serviceScope.launch(Dispatchers.IO) {
            val lang = currentLanguage()
            // Gantian antara trivia & RSS kalau dua-duanya aktif, biar variatif.
            val useRss = rssOn && (!triviaOn || (0..1).random() == 0)
            val fetched = if (useRss) {
                com.example.data.RssFeedReader.getLatestHeadline(com.example.data.KnowledgeSettings.getRssUrl(ctx))
            } else {
                com.example.data.WikipediaLookup.getRandomFact(lang)
            }
            if (fetched.isNullOrBlank()) return@launch // gagal fetch (offline dll) -- diem aja, coba lagi interval berikutnya

            val finalText = if (com.example.data.GeminiPetBrain.isConfigured()) {
                val memory = com.example.data.ObsidianMemoryManager.loadMemoryFromObsidian(ctx)
                com.example.data.GeminiPetBrain.generateDialog(
                    userName = memory.userName,
                    userHobby = memory.userHobby,
                    petLevel = petLevel,
                    petEmotion = petEmotion,
                    language = lang,
                    extraInfo = fetched
                )
            } else {
                // Gemini gak dikonfigurasi -- bacain apa adanya pakai template simpel.
                val prefix = if (lang == "en") {
                    if (useRss) "Hey Master, did you hear about this? " else "Ooh, random fact time! "
                } else {
                    if (useRss) "Master, denger gak berita ini? " else "Eh tau gak, fun fact nih! "
                }
                "$prefix$fetched"
            }

            withContext(Dispatchers.Main) {
                peekAndReveal()
                speakBubble(finalText) { closePeekIfNeeded() }
            }
        }
    }

    /**
     * Cek berkala apa ada file .md baru di vault yang belum dibacain. Kalau ada, langsung
     * masuk "mode baca" -- gak dicek tiap tick biar gak boros baca disk, dikasih jeda
     * VAULT_READ_CHECK_INTERVAL_MS antar pengecekan.
     */
    private fun checkForNewReadingMaterial() {
        if (isReadingVaultFile || isPetHidden) return
        val now = System.currentTimeMillis()
        if (now - lastVaultReadCheckTime < VAULT_READ_CHECK_INTERVAL_MS) return
        lastVaultReadCheckTime = now

        serviceScope.launch(Dispatchers.IO) {
            val file = com.example.data.VaultReadingManager.findNextUnreadFile(applicationContext)
            if (file != null) {
                withContext(Dispatchers.Main) {
                    startReadingVaultFile(file)
                }
            }
        }
    }

    /** Bacain 1 file penuh lewat TTS, tandain udah dibaca, baru balik ngoceh normal. */
    private fun startReadingVaultFile(file: java.io.File) {
        val content = try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
        val fileName = file.nameWithoutExtension
        if (content.isBlank()) {
            com.example.data.VaultReadingManager.markAsRead(applicationContext, file)
            return
        }

        isReadingVaultFile = true
        val lang = currentLanguage()
        speakBubble(
            if (lang == "en") "📖 Reading \"$fileName\" now, Master..."
            else "📖 Lagi baca \"$fileName\" nih, Master..."
        )

        // Beneran nunggu TTS ngasih sinyal "kelar ngomong" (bukan nebak durasi pakai jumlah
        // karakter) -- baru abis itu balik ngoceh normal. Kalau nebak durasi kependekan,
        // sistem ngoceh biasa bisa nyempil sebelum bacaan file-nya beneran abis.
        com.example.data.TtsSpeaker.speak(content) {
            com.example.data.VaultReadingManager.markAsRead(applicationContext, file)
            isReadingVaultFile = false
            speakBubble(
                if (lang == "en") "✅ Done reading \"$fileName\"! Thanks for the new reading material, Master~"
                else "✅ Selesai baca \"$fileName\"! Makasih udah kasih bacaan baru, Master~"
            )
        }
    }

    private fun requestSmartDialog(category: String) {
        if (!com.example.data.GeminiPetBrain.isConfigured()) return
        val now = System.currentTimeMillis()
        if (now - lastSmartDialogRequestTime < SMART_DIALOG_COOLDOWN_MS) {
            // Masih dalam masa cooldown, biarin kalimat template dari tap-nya tetap tampil,
            // gak usah nembak Gemini lagi biar kuota gak cepet abis.
            return
        }
        lastSmartDialogRequestTime = now
        serviceScope.launch {
            val lang = currentLanguage()
            val memory = com.example.data.ObsidianMemoryManager.loadMemoryFromObsidian(applicationContext)
            val vaultContext = com.example.data.ObsidianMemoryManager.readVaultContext(applicationContext)
            val memoryContext = com.example.data.PetMemoryLog.getRecentContext()
            val reply = com.example.data.GeminiPetBrain.generateDialog(
                userName = memory.userName,
                userHobby = memory.userHobby,
                petLevel = petLevel,
                petEmotion = petEmotion,
                vaultContext = vaultContext,
                language = lang,
                memoryContext = memoryContext
            )
            peekAndReveal()
            speakBubble(reply) { closePeekIfNeeded() }

            // Simpan ke pet-quotes.md HANYA kalau ini beneran balasan AI (bukan pesan error
            // fallback kayak "Kuota AI-ku abis" dll -- gak mau nyampah kalimat error jadi template).
            // Kategori dikasih akhiran "_en" pas mode Inggris, biar gak nyampur sama kalimat
            // Indonesia yang udah kesimpen sebelumnya di kategori yang sama.
            val looksLikeError = reply.startsWith("Kuota AI-ku") ||
                reply.startsWith("Aduh, otak AI-ku") ||
                reply.startsWith("Koneksi ke otak AI-ku") ||
                reply.startsWith("Ups, ada yang salah") ||
                reply.startsWith("Hmm, aku belum tahu")
            if (!looksLikeError) {
                com.example.data.PetQuoteSettings.appendGeneratedQuote(quoteCategory(category), reply)
                // Sekalian catat ke riwayat memori (pet-memory.md) biar diinget di request berikutnya.
                com.example.data.PetMemoryLog.append(applicationContext, reply)
            }
        }
    }

    /**
     * Idle timer yang udah ada dari awal (jalan tiap 3 detik): urus perubahan mood
     * (Bosan/Kesal, HANYA kalau pet lagi full ditampilin) DAN sekarang juga urus
     * "ngoceh berkala":
     * - Tiap interval dari IdleChatterSettings: pet SELALU ngomong sesuatu, gratis,
     *   pakai kalimat template (PetQuotes) -- gak nunggu network, gak manggil API.
     *   Ini tetap jalan WALAU pet lagi disembunyiin (bikin efek "intip" -- lihat peekAndReveal()).
     * - Tapi KALAU pet baru aja disentuh dalam RECENT_INTERACTION_WINDOW_MS (1 menit)
     *   terakhir DAN cooldown Gemini (SMART_DIALOG_COOLDOWN_MS, 1 menit) udah lewat,
     *   tick ini juga sekalian minta kalimat "pintar" dari Gemini (async, nimpa bubble
     *   begitu balasannya datang). Kalau pet dibiarin lama tanpa disentuh, gak ada
     *   request Gemini otomatis sama sekali -- kuota aman.
     */
    private fun startIdleEmotionTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = serviceScope.launch {
            while (true) {
                delay(3000L)
                // Skip semua ngoceh/perubahan mood kalau layar HP lagi mati -- gak ada
                // gunanya pet ngomong pas gak ada yang liat, cuma buang baterai & bikin
                // suara nyeletuk aneh keluar dari saku pas lagi tidur.
                val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                val isScreenOn = powerManager?.isInteractive ?: true
                if (!isScreenOn) continue
                if (isDragging) continue

                val now = System.currentTimeMillis()

                // Cek ada file bacaan baru di vault gak -- ini jalan terlepas dari status
                // hidden/mood, biar Master bisa nambah bacaan kapan aja.
                checkForNewReadingMaterial()

                // Cek waktunya nyelipin trivia Wikipedia / headline RSS (kalau diaktifin).
                checkForKnowledgeShare()

                // Perubahan mood cuma kalau pet lagi full ditampilin, DAN lagi gak mode baca --
                // gak mau ngoceh mood keselip di tengah pet lagi bacain sesuatu.
                // Jadwal mood 6 menit: 1) Ceria (default) 2) Bosan->Kesal 3) Marah->Ngantuk
                // 4) Tidur (ngelindur) 5) Bangun (masih ngelindur, bad mood) 6) Hide otomatis.
                if (!isPetHidden && !isReadingVaultFile) {
                    val elapsedSeconds = ((now - lastInteractionTimestamp) / 1000).toInt()
                    val ctx = applicationContext
                    val bosanSec = com.example.data.MoodTimingSettings.getBosanSec(ctx).toInt()
                    val kesalSec = com.example.data.MoodTimingSettings.getKesalSec(ctx).toInt()
                    val marahSec = com.example.data.MoodTimingSettings.getMarahSec(ctx).toInt()
                    val ngantukSec = com.example.data.MoodTimingSettings.getNgantukSec(ctx).toInt()
                    val tidurSec = com.example.data.MoodTimingSettings.getTidurSec(ctx).toInt()
                    val bangunSec = com.example.data.MoodTimingSettings.getBangunSec(ctx).toInt()
                    when {
                        elapsedSeconds >= bangunSec && petEmotion != "Bangun" -> {
                            petEmotion = "Bangun"
                            updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.IDLE_NGANTUK)
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("bangun"), PetQuotes.bangunQuotes(currentLanguage())))
                            syncToObsidian()
                        }
                        elapsedSeconds >= tidurSec && petEmotion != "Tidur" -> {
                            petEmotion = "Tidur"
                            updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.IDLE_TIDUR)
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("tidur"), PetQuotes.tidurQuotes(currentLanguage())))
                            syncToObsidian()
                        }
                        elapsedSeconds >= ngantukSec && petEmotion != "Ngantuk" -> {
                            petEmotion = "Ngantuk"
                            updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.IDLE_NGANTUK)
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("ngantuk"), PetQuotes.ngantukQuotes(currentLanguage())))
                            syncToObsidian()
                        }
                        elapsedSeconds >= marahSec && petEmotion != "Marah" -> {
                            petEmotion = "Marah"
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("marah"), PetQuotes.marahQuotes(currentLanguage())))
                            syncToObsidian()
                        }
                        elapsedSeconds >= kesalSec && petEmotion != "Kesal" -> {
                            petEmotion = "Kesal"
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("kesal"), PetQuotes.kesalQuotes(currentLanguage())))
                            syncToObsidian()
                        }
                        elapsedSeconds >= bosanSec && petEmotion == "Senang" -> {
                            petEmotion = "Bosan"
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("bosan"), PetQuotes.boredQuotes(currentLanguage())))
                            syncToObsidian()
                        }
                    }

                    // Kelamaan didiemin -> otomatis sembunyi sendiri (buka pintu/tutup pintu).
                    // Angka waktunya diatur lewat slider "Jadwal Mood" di dashboard. Tombol
                    // manual (hideButton/showButtonPill) tetap bisa dipencet kapan aja.
                    val hideSec = com.example.data.MoodTimingSettings.getHideSec(ctx).toInt()
                    if (elapsedSeconds >= hideSec) {
                        togglePetVisibility()
                    }
                }

                if (!isReadingVaultFile && now - lastIdleChatterTime >= com.example.data.IdleChatterSettings.getIntervalMs(applicationContext)) {
                    lastIdleChatterTime = now
                    val recentlyTouched = now - lastInteractionTimestamp <= RECENT_INTERACTION_WINDOW_MS
                    val geminiCooldownPassed = now - lastSmartDialogRequestTime >= SMART_DIALOG_COOLDOWN_MS
                    if (recentlyTouched && geminiCooldownPassed && com.example.data.GeminiPetBrain.isConfigured()) {
                        requestSmartDialog("idle")
                    } else {
                        peekAndReveal()
                        speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("idle"), PetQuotes.idleQuotes(currentLanguage()))) { closePeekIfNeeded() }
                    }
                }
            }
        }
    }

    private fun listenForPetDataBus() {
        serviceScope.launch {
            com.example.model.PetDataBus.syncFlow.collect { syncData ->
                petLevel = syncData.petLevel
                petXp = syncData.petXp
                if (syncData.speechMessage.isNotEmpty() && !isPetHidden) {
                    speechCard?.visibility = View.VISIBLE
                    speakBubble(syncData.speechMessage)
                }
            }
        }
    }

    /** Selalu panggil ini (bukan set TextView langsung) -- update state Compose, lalu paksa window luar resize. */
    /** Update teks bubble doang, TANPA suara — dipakai kalau TTS-nya mau diatur manual sendiri (misal notifikasi WA/Telegram, yang teksnya beda dari yang dibacain). */
    private fun updateBubbleUi(text: String) {
        speechBubbleTextState.value = text
        // Compose butuh 1 frame buat recompose+relayout dulu sebelum window WindowManager
        // di luar dipaksa resize -- makanya dikasih delay kecil, bukan langsung.
        serviceScope.launch {
            delay(50)
            clampWindowToScreen()
        }
    }

    /** Cegah TTS ngomong dobel buat kalimat yang sama gara-gara PetDataBus (overlay dengerin balik broadcast dari dirinya sendiri). */
    private var lastSpokenText: String? = null
    private var lastSpokenAt: Long = 0L

    /** Update teks bubble SEKALIGUS dibacain lewat TTS, pakai suara yang sudah dipilih Master di Settings. Ini yang dipakai di semua celotehan pet (tap, level up, mood, AI reply, dll). */
    private fun speakBubble(text: String, onDone: (() -> Unit)? = null) {
        updateBubbleUi(text)
        val now = System.currentTimeMillis()
        if (text != lastSpokenText || now - lastSpokenAt > 3000L) {
            com.example.data.TtsSpeaker.speak(text, onDone)
            lastSpokenText = text
            lastSpokenAt = now
        } else {
            onDone?.invoke() // teks sama baru aja diomongin, gak diulang -- tetep panggil onDone biar caller gak nyangkut
        }
    }

    /** Paksa window resize ulang tiap konten (misal teks bubble) berubah ukuran, sekalian jaga posisi biar nggak nabrak tepi layar. */
    private fun clampWindowToScreen() {
        val p = params ?: return
        val ov = overlayView ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val windowWidth = ov.width
        val windowHeight = ov.height
        if (windowWidth <= 0 || windowHeight <= 0) return

        val maxX = (screenWidth - windowWidth).coerceAtLeast(0)
        val maxY = (screenHeight - windowHeight).coerceAtLeast(0)
        if (p.x > maxX) p.x = maxX
        if (p.x < 0) p.x = 0
        if (p.y > maxY) p.y = maxY
        if (p.y < 0) p.y = 0

        updateOverlayWindowPosition(p)
    }

    /** Satu pintu buat update posisi window pet utama -- SEKALIAN sinkronin posisi window
     * bubble ngomong biar selalu ngikutin pet ke mana pun dia digeser/jalan. Ganti semua
     * pemanggilan windowManager.updateViewLayout(overlayView, p) langsung jadi lewat sini. */
    private fun updateOverlayWindowPosition(p: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(overlayView, p)
        } catch (e: Exception) {
            // Overlay lagi nggak siap/service berhenti, abaikan
        }
        syncSpeechBubblePosition(p)
    }

    /** Bikin window bubble ngomong TERPISAH dari window pet utama, sekali doang pas overlay
     * pertama kali di-spawn. FLAG_NOT_TOUCHABLE dipasang khusus di window ini -- apapun
     * ukuran bubble-nya (lebar 230dp dkk), sentuhan SELALU tembus ke app di bawahnya,
     * gak akan pernah lagi nge-block klik ke app lain kayak sebelumnya. */
    private fun createSpeechBubbleWindow() {
        val bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PetOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PetOverlayService)
            setContent {
                val text by remember { speechBubbleTextState }
                val mood by remember { bubbleMoodState }
                val fontSizeSp by com.example.data.BubbleSettings.fontSizeSp.collectAsState()
                val useMoodColor by com.example.data.BubbleStyleSettings.useMoodColor.collectAsState()
                val customBgColor by com.example.data.BubbleStyleSettings.bgColor.collectAsState()
                val customTextColor by com.example.data.BubbleStyleSettings.textColor.collectAsState()
                val bgColor = if (useMoodColor) {
                    Color(com.example.data.BubbleStyleSettings.getEffectiveBgColor(mood))
                } else {
                    Color(customBgColor)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .width(230.dp)
                        .heightIn(min = 56.dp, max = 110.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = text,
                            fontSize = fontSizeSp.sp,
                            color = Color(customTextColor)
                        )
                    }
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params?.x ?: 0
            y = params?.y ?: 0
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
            speechCard = bubbleView
            speechBubbleParams = bubbleParams
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Ikutin posisi window pet utama tiap kali pet-nya digeser/jalan/reposisi, taro bubble
     * pas di atas pet-nya (sedikit di atas titik y pet). */
    private fun syncSpeechBubblePosition(p: WindowManager.LayoutParams) {
        val bp = speechBubbleParams ?: return
        val bubbleView = speechCard ?: return
        bp.x = p.x
        bp.y = (p.y - 20).coerceAtLeast(0)
        try {
            windowManager.updateViewLayout(bubbleView, bp)
        } catch (e: Exception) {
            // Overlay lagi nggak siap/service berhenti, abaikan
        }
    }

    private fun resolveLocalCostumeDrawable(costumeId: String, held: Boolean): Int {
        return when (costumeId) {
            "baju_sekolah" -> R.drawable.img_costume_school
            "gaun_pesta" -> R.drawable.img_costume_dress
            "piyama" -> R.drawable.img_costume_pajamas
            else -> if (held) R.drawable.img_chibi_pet_held else R.drawable.img_chibi_pet_idle
        }
    }

    /** Selalu panggil ini (bukan setImageResource langsung) supaya kostum aktif ke-apply dengan benar. */
    private fun updatePetSprite(held: Boolean) {
        val iv = petImage ?: return
        val rawCostumeId = com.example.model.CostumeManager.kostumAktif.value
        val effectiveId = com.example.model.CostumeManager.getEffectiveCostumeUrlOrId(rawCostumeId, petLevel)

        if (effectiveId.startsWith("http") || effectiveId.contains("/")) {
            // Kostum kustom dari galeri HP atau URL -> load pakai Coil
            val loader = com.example.data.GifAwareImageLoader.get(applicationContext)
            val fallbackRes = resolveLocalCostumeDrawable("default", held)
            val request = coil.request.ImageRequest.Builder(applicationContext)
                .data(effectiveId)
                .target(iv)
                .crossfade(true)
                // Placeholder & error fallback -- petImage JANGAN PERNAH kosong sama sekali,
                // baik pas masih loading maupun kalau gagal load (file ilang/network gagal).
                .placeholder(fallbackRes)
                .error(fallbackRes)
                .build()
            loader.enqueue(request)
        } else {
            iv.setImageResource(resolveLocalCostumeDrawable(effectiveId, held))
        }

    }

    /**
     * Tampilin gambar buat SATU pose spesifik (idle/tap/drag/hide), kalau Master udah
     * upload gambar custom buat slot itu. Kalau belum diisi, otomatis fallback ke sprite
     * default (updatePetSprite biasa) -- jadi aman dipanggil kapan aja walau belum semua
     * slot keisi.
     */
    private fun updatePetSpriteForPose(slot: com.example.model.PoseSpriteManager.PoseSlot, fallbackHeld: Boolean = false) {
        val iv = petImage ?: return
        val customPath = com.example.model.PoseSpriteManager.getRandomPoseImagePath(slot)
        if (customPath != null && java.io.File(customPath).exists()) {
            val loader = com.example.data.GifAwareImageLoader.get(applicationContext)
            val fallbackRes = resolveLocalCostumeDrawable("default", fallbackHeld)
            val request = coil.request.ImageRequest.Builder(applicationContext)
                .data(customPath)
                .target(iv)
                .crossfade(true)
                // GAK dikasih .placeholder() di sini -- file-nya udah dipastiin ADA
                // (dicek File.exists() di atas), jadi gak perlu paksa "reset ke default"
                // dulu sebelum pose custom-nya kemuat. Gambar SEBELUMNYA (pose lama)
                // tetep nampil sampe yang baru selesai load, baru crossfade -- gak ada
                // lagi kelip balik ke default tiap kali pose berganti (tap/drag/dll).
                // .error() tetep dipasang, jaga-jaga kalau filenya somehow rusak/gagal baca.
                .error(fallbackRes)
                .build()
            loader.enqueue(request)
        } else {
            updatePetSprite(held = fallbackHeld)
        }
    }

    /**
     * Tampilin pose transisi pintu (kalau ada aset-nya) dan NUNGGU sampe gif/gambar itu
     * beneran kemuat & tampil (lewat listener Coil, bukan delay tebak-tebakan) sebelum
     * kasih jeda [afterVisibleMs] biar keliatan geraknya, baru panggil [onDone]. Kalau gak
     * ada aset custom, langsung panggil [onDone] tanpa nunggu apa-apa.
     */
    private fun playPintuTransitionThen(afterVisibleMs: Long = HIDE_TRANSITION_VISIBLE_MS, onDone: () -> Unit) {
        val iv = petImage ?: run { onDone(); return }
        val pintuPath = com.example.model.PoseSpriteManager.getRandomPoseImagePath(
            com.example.model.PoseSpriteManager.PoseSlot.HIDE_PINTU
        )
        if (pintuPath == null || !java.io.File(pintuPath).exists()) {
            onDone()
            return
        }
        val loader = com.example.data.GifAwareImageLoader.get(applicationContext)
        val request = coil.request.ImageRequest.Builder(applicationContext)
            .data(pintuPath)
            .target(iv)
            .crossfade(true)
            .listener(
                onSuccess = { _, _ ->
                    serviceScope.launch {
                        delay(afterVisibleMs)
                        onDone()
                    }
                },
                onError = { _, _ -> onDone() }
            )
            .build()
        loader.enqueue(request)
    }

    private fun listenForNotificationBus() {
        serviceScope.launch {
            NotificationBus.notifications.collect { incoming ->
                peekAndReveal()
                speechCard?.visibility = View.VISIBLE
                updateBubbleUi(incoming.toSpeechBubbleText())
                updatePetSprite(held = false)

                when (com.example.data.NotificationVoiceSettings.getMode(applicationContext)) {
                    com.example.data.VoiceReadMode.OFF -> {
                        // Gak ada suara buat nunggu -- tutup lagi abis beberapa detik biar
                        // Master sempet baca teksnya sekilas kalau lagi mode intip.
                        serviceScope.launch {
                            delay(4000L)
                            closePeekIfNeeded()
                        }
                    }
                    com.example.data.VoiceReadMode.SENDER_ONLY -> {
                        com.example.data.TtsSpeaker.speak("Pesan masuk dari ${incoming.senderName}") { closePeekIfNeeded() }
                    }
                    com.example.data.VoiceReadMode.FULL_MESSAGE -> {
                        com.example.data.TtsSpeaker.speak("Pesan dari ${incoming.senderName}: ${incoming.messageText}") { closePeekIfNeeded() }
                    }
                }

                // Share incoming notification data across overlay & main app
                com.example.model.PetDataBus.shareData(
                    level = petLevel,
                    xp = petXp,
                    emotion = "HAPPY",
                    speechMessage = incoming.toSpeechBubbleText(),
                    sender = incoming.senderName,
                    message = incoming.messageText
                )
            }
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "pet_overlay_channel"
        val channelName = "Desktop Pet Overlay Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Desktop Pet Shimeji Active")
            .setContentText("Pet Chibi Perempuan aktif di atas layar HP")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createFloatingPetOverlay() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        
        // Dynamically build layout or construct FrameLayout
        val rootLayout = FrameLayout(this)
        rootLayout.setViewTreeLifecycleOwner(this@PetOverlayService)
        rootLayout.setViewTreeSavedStateRegistryOwner(this@PetOverlayService)
        
        // Container Layout
        val petContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        petContainerRef = petContainer

        // speechCard SENGAJA gak dibikin di sini lagi -- sekarang jadi window terpisah,
        // dibikin sekali lewat createSpeechBubbleWindow() setelah window pet utama nempel.

        // Pet Image View
        petImage = ImageView(this).apply {
            setImageResource(R.drawable.img_chibi_pet_idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val sizePx = (110 * resources.displayMetrics.density).toInt()
            petSizePx = sizePx
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }

        // Hide Pill Button
        hideButton = TextView(this).apply {
            text = " Hide "
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE91E63.toInt())
            setPadding(12, 6, 12, 6)
            setOnClickListener {
                togglePetVisibility()
            }
        }

        // Chat Pill Button -- buka/tutup panel chat dua arah (window overlay terpisah)
        chatButton = TextView(this).apply {
            text = " 💬 Chat "
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4CAF50.toInt())
            setPadding(12, 6, 12, 6)
            setOnClickListener {
                toggleChatOverlay()
            }
        }

        // Show Pill Button (Visible when pet is hidden)
        showButtonPill = TextView(this).apply {
            text = " Show Pet "
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2196F3.toInt())
            setPadding(16, 10, 16, 10)
            visibility = View.GONE
            setOnClickListener {
                togglePetVisibility()
            }
        }

        // hideButton & chatButton ditaro LANGSUNG ke petContainer (vertikal), TANPA
        // dibungkus LinearLayout tambahan lagi -- versi sebelumnya (nested topButtonRow
        // horizontal) kena bug pengukuran window (WRAP_CONTENT dan bahkan tinggi piksel
        // pasti sama-sama kebaca gak wajar). Ini hilangin akar masalahnya total.
        // speechCard SENGAJA gak ditaro di sini lagi -- sekarang jadi window terpisah
        // (lihat createSpeechBubbleWindow()), biar lebar 230dp-nya gak pernah nge-block
        // sentuhan ke app di bawah pet.
        petContainer.addView(hideButton)
        petContainer.addView(chatButton)
        petContainer.addView(petImage)
        petContainer.addView(showButtonPill)

        rootLayout.addView(petContainer)
        overlayView = rootLayout

        // WindowManager LayoutParams for Overlay Window
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2 - 150
            y = screenHeight / 3
        }

        // Touch Listener for Drag & Drop + Stair Fall physics
        petImage?.setOnTouchListener(object : View.OnTouchListener {
            private var clickTime = 0L
            private var holdEngaged = false // udah lewat DRAG_HOLD_THRESHOLD_MS & drag beneran aktif?
            private var holdJob: Job? = null

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        clickTime = System.currentTimeMillis()
                        isDragging = false
                        holdEngaged = false
                        isDraggingBerontak = false
                        behaviorState = PetBehaviorState.IDLE
                        behaviorTicksRemaining = 0

                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        // Sinyal visual instan pas kesentuh -- biar keliatan sentuhannya "kehitung"
                        // sementara masih nunggu DRAG_HOLD_THRESHOLD_MS, bukan diem gak ada respon.
                        v?.animate()?.cancel()
                        v?.animate()?.scaleX(0.92f)?.scaleY(0.92f)?.setDuration(120L)?.withEndAction {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                        }?.start()

                        // Belum langsung dianggap drag -- tunggu ditahan DRAG_HOLD_THRESHOLD_MS
                        // dulu, baru pose & kalimat drag muncul + posisi mulai ikut gerak.
                        // Sebelum itu, sentuhan masih bisa berakhir jadi tap biasa.
                        holdJob?.cancel()
                        holdJob = serviceScope.launch {
                            delay(DRAG_HOLD_THRESHOLD_MS)
                            holdEngaged = true
                            isDragging = true
                            // Rekap ulang titik acuan pas drag beneran mulai, biar pet gak "loncat"
                            // ngikutin posisi jari yang mungkin udah geser selama masa tahan.
                            initialX = p.x
                            initialY = p.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.DRAG_PASRAH, fallbackHeld = true)
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("drag"), PetQuotes.dragQuotes(currentLanguage())))
                        }
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (holdEngaged && isDragging) {
                            p.x = initialX + (event.rawX - initialTouchX).toInt()
                            p.y = initialY + (event.rawY - initialTouchY).toInt()
                            updateOverlayWindowPosition(p)

                            // Kelamaan di-drag -> ganti pose jadi berontak (cuma sekali)
                            if (!isDraggingBerontak && System.currentTimeMillis() - clickTime > DRAG_BERONTAK_THRESHOLD_MS) {
                                isDraggingBerontak = true
                                updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.DRAG_BERONTAK, fallbackHeld = true)
                            }
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        holdJob?.cancel()
                        v?.animate()?.cancel()
                        v?.scaleX = 1f
                        v?.scaleY = 1f
                        val wasDragging = isDragging
                        isDragging = false
                        val duration = System.currentTimeMillis() - clickTime

                        val deltaX = abs(event.rawX - initialTouchX)
                        val deltaY = abs(event.rawY - initialTouchY)

                        if (!wasDragging && duration < 200 && deltaX < 15 && deltaY < 15) {
                            // Pet Tapped -- pose reaksi acak, ringan atau berlebihan
                            val tapSlot = if ((0..1).random() == 0) {
                                com.example.model.PoseSpriteManager.PoseSlot.TAP_RINGAN
                            } else {
                                com.example.model.PoseSpriteManager.PoseSlot.TAP_BERLEBIHAN
                            }
                            updatePetSpriteForPose(tapSlot, fallbackHeld = false)
                            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("tap"), PetQuotes.tapQuotes(currentLanguage())))
                            handleUserInteraction(5)
                            behaviorState = PetBehaviorState.IDLE
                            behaviorTicksRemaining = 0
                            requestSmartDialog("tap")
                        } else if (wasDragging) {
                            // Released after drag beneran aktif -> tetap diam di titik itu (nggak jatuh lagi, atas request Master)
                            updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.IDLE_DIAM, fallbackHeld = false)
                            handleUserInteraction(2)
                            behaviorState = PetBehaviorState.IDLE
                            behaviorTicksRemaining = 0
                        }
                        // else: ditahan tapi dilepas sebelum jadi tap valid & sebelum drag aktif -> dibiarin, gak ngapa-ngapain
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(overlayView, params)
            // FIX: window WRAP_CONTENT baru keukur bener sesudah petContainer (tombol +
            // petImage + showButtonPill) selesai ke-layout. Sebelum ini, clampWindowToScreen()
            // cuma kepanggil pas bubble teks berubah -- artinya sebelum pet pertama kali
            // "ngomong", window bisa lebih kecil dari seharusnya dan motong petImage di
            // luar area yang bisa digambar/disentuh.
            overlayView?.post {
                clampWindowToScreen()
                // Window bubble ngomong dibikin SEKALI di sini, setelah window pet utama
                // beres nempel & keukur -- biar posisi awalnya (params.x/y) udah bener.
                if (speechCard == null) {
                    createSpeechBubbleWindow()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PetDebug", "windowManager.addView GAGAL", e)
            e.printStackTrace()
        }
    }

    private fun startAutonomousBehaviorLoop() {
        behaviorJob?.cancel()
        behaviorJob = serviceScope.launch {
            while (true) {
                delay(TICK_MS)
                if (isDragging || isPetHidden) continue
                val p = params ?: continue
                val displayMetrics = resources.displayMetrics
                val floorY = displayMetrics.heightPixels - 300
                val topMarginY = (100 * displayMetrics.density).toInt()
                val rightEdgeX = displayMetrics.widthPixels - petSizePx

                if (behaviorState == PetBehaviorState.IDLE || behaviorState == PetBehaviorState.WALK_LEFT || behaviorState == PetBehaviorState.WALK_RIGHT) {
                    behaviorTicksRemaining--
                    if (behaviorTicksRemaining <= 0) {
                        decideNextBehavior()
                    }
                }

                applyBehaviorStep(p, floorY, topMarginY, rightEdgeX)
            }
        }
    }

    private fun decideNextBehavior() {
        behaviorState = listOf(
            PetBehaviorState.IDLE,
            PetBehaviorState.WALK_LEFT,
            PetBehaviorState.WALK_RIGHT
        ).random()
        // Idle sebentar (1-2 detik) atau jalan lebih lama (2.5-6 detik)
        behaviorTicksRemaining = if (behaviorState == PetBehaviorState.IDLE) {
            (20..40).random()
        } else {
            (50..120).random()
        }
        petImage?.scaleX = if (behaviorState == PetBehaviorState.WALK_LEFT) -1f else 1f
    }

    private fun applyBehaviorStep(p: WindowManager.LayoutParams, floorY: Int, topMarginY: Int, rightEdgeX: Int) {
        var moved = false
        when (behaviorState) {
            PetBehaviorState.WALK_LEFT -> {
                p.x = (p.x - WALK_SPEED_PX).coerceAtLeast(0)
                moved = true
                if (p.x <= 0) {
                    behaviorState = PetBehaviorState.CLIMB_UP
                    behaviorTicksRemaining = 0
                    speakBubble("Manjat ah~ 🧗")
                }
            }
            PetBehaviorState.WALK_RIGHT -> {
                p.x = (p.x + WALK_SPEED_PX).coerceAtMost(rightEdgeX)
                moved = true
                if (p.x >= rightEdgeX) {
                    behaviorState = PetBehaviorState.CLIMB_UP
                    behaviorTicksRemaining = 0
                    speakBubble("Manjat ah~ 🧗")
                }
            }
            PetBehaviorState.CLIMB_UP -> {
                p.y = (p.y - CLIMB_SPEED_PX).coerceAtLeast(topMarginY)
                moved = true
                if (p.y <= topMarginY) {
                    if ((0..2).random() == 0) {
                        behaviorState = PetBehaviorState.CLIMB_DOWN
                        behaviorTicksRemaining = 0
                        speakBubble("Turun lagi ah~")
                    } else {
                        decideNextBehavior()
                    }
                }
            }
            PetBehaviorState.CLIMB_DOWN -> {
                p.y = (p.y + CLIMB_SPEED_PX).coerceAtMost(floorY)
                moved = true
                if (p.y >= floorY) {
                    decideNextBehavior()
                }
            }
            PetBehaviorState.IDLE -> { /* diam di tempat */ }
        }

        if (moved) {
            updateOverlayWindowPosition(p)
        }
    }

    /**
     * Kalau pet lagi disembunyiin (isPetHidden), munculin sebentar (petImage + speechCard)
     * buat efek "intip" -- dipanggil sebelum ngoceh berkala / balasan AI / notifikasi masuk.
     * Nutupnya BUKAN pakai timer tetap lagi (dulu 6.5 detik, keburu nutup sebelum TTS-nya
     * beneran kelar ngomong buat kalimat yang agak panjang) -- sekarang ditutup lewat
     * closePeekIfNeeded(), dipanggil sebagai onDone abis speakBubble() beneran kelar.
     * Kalau pet lagi full ditampilin (!isPetHidden), fungsi ini gak ngapa-ngapain.
     */
    private fun peekAndReveal() {
        if (!isPetHidden) return
        updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.HIDE_NGINTIP)
        petImage?.visibility = View.VISIBLE
        speechCard?.visibility = View.VISIBLE
    }

    /** Pasangan peekAndReveal() -- panggil ini sebagai onDone abis speakBubble() kelar,
     * biar bubble/pet balik sembunyi PAS beneran abis ngomong, bukan nebak durasi. Aman
     * dipanggil kapan aja (no-op kalau pet lagi gak dalam status hidden). */
    private fun closePeekIfNeeded() {
        if (isPetHidden) {
            petImage?.visibility = View.GONE
            speechCard?.visibility = View.GONE
        }
    }

    /** Buka/tutup panel chat. Panel-nya window WindowManager BARU yang terpisah dari
     * window pet utama -- jadi bisa focusable (nerima keyboard) tanpa perlu ngubah
     * FLAG_NOT_FOCUSABLE window pet, biar drag/tap pet yang udah jalan gak kesenggol. */
    private fun toggleChatOverlay() {
        if (chatOverlayView != null) {
            closeChatOverlay()
        } else {
            openChatOverlay()
        }
    }

    private fun openChatOverlay() {
        // Baca histori chat di background -- JANGAN di main thread, ini bekas
        // penyebab overlay berat/nge-freeze pas dibuka.
        serviceScope.launch {
            val entries = withContext(Dispatchers.IO) {
                com.example.data.PetMemoryLog.getRawEntries()
            }
            chatMessagesState.value = entries
        }

        val chatView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PetOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PetOverlayService)
            setContent {
                val messages by remember { chatMessagesState }
                val isSending by remember { chatSendingState }
                var inputText by remember { mutableStateOf("") }
                val scrollState = rememberScrollState()

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A1A),
                    shadowElevation = 8.dp,
                    // FIX: dikasih height PASTI juga (bukan cuma width), sama kayak
                    // pola bug topButtonRow kemarin -- window WRAP_CONTENT tanpa batas
                    // tinggi jelas bisa kebaca lebih gede dari yang keliatan, area
                    // "hantu" di bawahnya nutupin sentuhan ke app lain di bawahnya.
                    modifier = Modifier.width(280.dp).heightIn(max = 360.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💬 Ngobrol Sama Pet", fontSize = 13.sp, color = Color.White)
                            Text(
                                "✕",
                                fontSize = 16.sp,
                                color = Color(0xFFA2A2A2),
                                modifier = Modifier.clickable { closeChatOverlay() }
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF101010),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp)
                                .padding(top = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                if (messages.isEmpty()) {
                                    Text("Sapa pet-nya dulu, Master~", fontSize = 11.sp, color = Color(0xFF858585))
                                } else {
                                    messages.forEach { line ->
                                        val isPet = line.contains("] Pet:")
                                        Text(
                                            text = line,
                                            fontSize = 11.sp,
                                            color = if (isPet) Color(0xFF80CBC4) else Color(0xFFCDCDCD),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                enabled = !isSending,
                                placeholder = { Text("Ketik pesan...", fontSize = 11.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
                            )
                            Text(
                                text = if (isSending) "..." else "Kirim",
                                fontSize = 12.sp,
                                color = Color(0xFF80CBC4),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable(enabled = !isSending && inputText.isNotBlank()) {
                                        val msg = inputText.trim()
                                        inputText = ""
                                        sendChatMessage(msg)
                                    }
                            )
                        }
                    }
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Posisi window di-clamp pake perkiraan lebar/tinggi MAKSIMAL (buat jaga-jaga
        // panel gak kepotong di pinggir layar) -- tapi ukuran window SENDIRI tetap
        // WRAP_CONTENT, biar ngikutin Compose yang udah dibatesin heightIn(max=360.dp)
        // di atas. Beda dari kasus topButtonRow kemarin (LinearLayout klasik nested,
        // rawan salah ukur) -- ini murni ComposeView, harusnya ngukur diri sendiri
        // lebih akurat selama batasnya dikasih di level Compose (heightIn).
        val chatDensity = resources.displayMetrics.density
        val chatPanelWidthPx = (280 * chatDensity).toInt()
        val chatMaxPanelHeightPx = (400 * chatDensity).toInt() // buat clamp posisi doang

        chatOverlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            // SENGAJA gak dikasih FLAG_NOT_FOCUSABLE -- panel ini emang butuh nerima
            // input keyboard. Window pet utama (petImage/hideButton dkk) gak kesentuh
            // sama sekali karena ini window WindowManager yang beda.
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val p = params
            // FIX: clamp posisi biar panel SELALU muat penuh di layar -- sebelumnya
            // ngikutin posisi pet apa adanya, kalau pet lagi deket pinggir layar,
            // tombol X-nya bisa kebawa keluar layar & gak kejangkau (nyaris freeze,
            // gak ada cara nutup chat selain restart HP).
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            val maxX = (screenW - chatPanelWidthPx).coerceAtLeast(0)
            val maxY = (screenH - chatMaxPanelHeightPx).coerceAtLeast(0)
            x = (p?.x ?: 0).coerceIn(0, maxX)
            y = ((p?.y ?: 0) - 20).coerceIn(0, maxY)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager.addView(chatView, chatOverlayParams)
            chatOverlayView = chatView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun closeChatOverlay() {
        chatOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        chatOverlayView = null
    }

    /** Kirim pesan chat ke Gemini, simpen hasilnya (dua arah) ke pet-memory.md yang sama
     * dipakai fitur "otak Obsidian" & ngoceh berkala -- biar semuanya nyambung satu memori. */
    private fun sendChatMessage(userMessage: String) {
        if (userMessage.isBlank() || chatSendingState.value) return
        chatSendingState.value = true
        // Tampilin pesan Master duluan (optimistic), biar responsif sebelum balasan dateng
        chatMessagesState.value = chatMessagesState.value + "[...] Master: $userMessage"
        serviceScope.launch {
            val lang = currentLanguage()
            val memory = com.example.data.ObsidianMemoryManager.loadMemoryFromObsidian(applicationContext)
            val vaultContext = com.example.data.ObsidianMemoryManager.readVaultContext(applicationContext)
            val memoryContext = com.example.data.PetMemoryLog.getRecentContext()
            val reply = com.example.data.GeminiPetBrain.generateChatReply(
                userMessage = userMessage,
                userName = memory.userName,
                userHobby = memory.userHobby,
                petLevel = petLevel,
                petEmotion = petEmotion,
                vaultContext = vaultContext,
                language = lang,
                memoryContext = memoryContext
            )
            com.example.data.PetMemoryLog.appendExchange(applicationContext, userMessage, reply)
            chatMessagesState.value = com.example.data.PetMemoryLog.getRawEntries()
            chatSendingState.value = false

            // Pet ikut nampilin balasannya di speech bubble juga, biar kerasa "hidup"
            // walau lagi mode chat (peek kalau lagi disembunyiin).
            peekAndReveal()
            speakBubble(reply) { closePeekIfNeeded() }
        }
    }

    private fun togglePetVisibility() {
        isPetHidden = !isPetHidden
        if (isPetHidden) {
            hideButton?.visibility = View.GONE
            showButtonPill?.visibility = View.VISIBLE
            playPintuTransitionThen {
                if (isPetHidden) {
                    speechCard?.visibility = View.GONE
                    petImage?.visibility = View.GONE
                }
            }
        } else {
            speechCard?.visibility = View.VISIBLE
            petImage?.visibility = View.VISIBLE
            hideButton?.visibility = View.VISIBLE
            showButtonPill?.visibility = View.GONE
            // Reset mood & timer idle -- biar pet yang baru dimunculin gak langsung ke-auto-hide
            // lagi di tick berikutnya cuma karena elapsedSeconds-nya masih nyisa dari sebelum disembunyiin.
            handleUserInteraction(0)
            playPintuTransitionThen {
                if (!isPetHidden) {
                    updatePetSpriteForPose(com.example.model.PoseSpriteManager.PoseSlot.IDLE_DIAM)
                }
            }
            speakBubble(com.example.data.PetQuoteSettings.getQuote(quoteCategory("reveal"), PetQuotes.revealQuotes(currentLanguage())))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        idleTimerJob?.cancel()
        behaviorJob?.cancel()
        com.example.data.TtsSpeaker.shutdown()
        closeChatOverlay()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        speechCard?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Cancel scope-nya sekalian, bukan cuma job yang di-track manual -- biar semua
        // coroutine nebeng (animasi kedip, transisi pintu, hold-drag job, dll) ikut berhenti
        // pas service beneran dimatiin, gak nyisa jalan nyoba akses view yang udah dilepas.
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 50L
        private const val WALK_SPEED_PX = 5
        private const val CLIMB_SPEED_PX = 5
    }
}
