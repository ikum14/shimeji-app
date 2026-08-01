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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class PetBehaviorState { IDLE, WALK_LEFT, WALK_RIGHT, CLIMB_UP, CLIMB_DOWN }

class PetOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val speechBubbleTextState = mutableStateOf("Halo Master! Seret aku ke atas ya~")


    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var petImage: ImageView? = null
    private var speechCard: View? = null
    private var hideButton: TextView? = null
    private var showButtonPill: View? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fallingJob: Job? = null
    private var idleTimerJob: Job? = null
    private var behaviorJob: Job? = null

    private var isDragging = false
    private var isFalling = false
    private var isPetHidden = false

    private var petSizePx = 0
    private var behaviorState = PetBehaviorState.IDLE

    /** Waktu terakhir berhasil kirim request ke Gemini, buat cooldown biar gak boros kuota. */
    private var lastSmartDialogRequestTime = 0L
    private val SMART_DIALOG_COOLDOWN_MS = 60_000L // 1 menit jarak minimal antar request AI

    /** Waktu terakhir pet ngoceh pakai kalimat template (gratis, gak manggil API). */
    private var lastIdleChatterTime = 0L
    // Interval-nya sekarang diatur user lewat slider di dashboard (IdleChatterSettings),
    // dibaca live tiap tick -- BUKAN angka mati lagi.

    /** Jendela waktu: Gemini cuma boleh dipanggil otomatis kalau pet DISENTUH dalam X ms terakhir. */
    private val RECENT_INTERACTION_WINDOW_MS = 60_000L // 1 menit

    private var behaviorTicksRemaining = 0

    // Leveling & Emotion Timer States for Overlay Pet (Timestamp based for Doze Mode safety)
    private var petLevel = 1
    private var petXp = 0
    private val maxXp = com.example.data.PetProgressStore.MAX_XP_PER_LEVEL
    private var petEmotion = "Senang" // "Senang", "Bosan", "Kesal"
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
                petName = "Chibi Girl Shimeji",
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
    private fun requestSmartDialog() {
        if (!com.example.data.GeminiPetBrain.isConfigured()) return
        val now = System.currentTimeMillis()
        if (now - lastSmartDialogRequestTime < SMART_DIALOG_COOLDOWN_MS) {
            // Masih dalam masa cooldown, biarin kalimat template dari tap-nya tetap tampil,
            // gak usah nembak Gemini lagi biar kuota gak cepet abis.
            return
        }
        lastSmartDialogRequestTime = now
        serviceScope.launch {
            val memory = com.example.data.ObsidianMemoryManager.loadMemoryFromObsidian(applicationContext)
            val vaultContext = com.example.data.ObsidianMemoryManager.readVaultContext(applicationContext)
            val reply = com.example.data.GeminiPetBrain.generateDialog(
                userName = memory.userName,
                userHobby = memory.userHobby,
                petLevel = petLevel,
                petEmotion = petEmotion,
                vaultContext = vaultContext
            )
            speakBubble(reply)
        }
    }

    /**
     * Idle timer yang udah ada dari awal (jalan tiap 3 detik): urus perubahan mood
     * (Bosan/Kesal) DAN sekarang juga urus "ngoceh berkala":
     * - Tiap IDLE_CHATTER_INTERVAL_MS (10 detik): pet SELALU ngomong sesuatu, gratis,
     *   pakai kalimat template (PetQuotes) -- gak nunggu network, gak manggil API.
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
                if (!isDragging && !isFalling && !isPetHidden) {
                    val now = System.currentTimeMillis()
                    val elapsedSeconds = ((now - lastInteractionTimestamp) / 1000).toInt()
                    if (elapsedSeconds >= 20 && petEmotion != "Kesal") {
                        petEmotion = "Kesal"
                        speakBubble(PetQuotes.kesalQuotes.random())
                        syncToObsidian()
                    } else if (elapsedSeconds >= 10 && petEmotion == "Senang") {
                        petEmotion = "Bosan"
                        speakBubble(PetQuotes.boredQuotes.random())
                        syncToObsidian()
                    }

                    if (now - lastIdleChatterTime >= com.example.data.IdleChatterSettings.getIntervalMs(applicationContext)) {
                        lastIdleChatterTime = now
                        val recentlyTouched = now - lastInteractionTimestamp <= RECENT_INTERACTION_WINDOW_MS
                        val geminiCooldownPassed = now - lastSmartDialogRequestTime >= SMART_DIALOG_COOLDOWN_MS
                        if (recentlyTouched && geminiCooldownPassed && com.example.data.GeminiPetBrain.isConfigured()) {
                            requestSmartDialog()
                        } else {
                            speakBubble(PetQuotes.tapQuotes.random())
                        }
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
    private fun speakBubble(text: String) {
        updateBubbleUi(text)
        val now = System.currentTimeMillis()
        if (text != lastSpokenText || now - lastSpokenAt > 3000L) {
            com.example.data.TtsSpeaker.speak(text)
            lastSpokenText = text
            lastSpokenAt = now
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

        // Selalu update (bukan cuma pas posisi berubah) -- window WRAP_CONTENT nggak auto resize
        // sendiri tiap konten berubah ukuran, harus dipaksa lewat updateViewLayout tiap saat.
        try {
            windowManager.updateViewLayout(overlayView, p)
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
            val loader = coil.ImageLoader(applicationContext)
            val request = coil.request.ImageRequest.Builder(applicationContext)
                .data(effectiveId)
                .target(iv)
                .crossfade(true)
                .build()
            loader.enqueue(request)
        } else {
            iv.setImageResource(resolveLocalCostumeDrawable(effectiveId, held))
        }
    }

    private fun listenForNotificationBus() {
        serviceScope.launch {
            NotificationBus.notifications.collect { incoming ->
                if (!isPetHidden) {
                    speechCard?.visibility = View.VISIBLE
                    updateBubbleUi(incoming.toSpeechBubbleText())
                    updatePetSprite(held = false)

                    when (com.example.data.NotificationVoiceSettings.getMode(applicationContext)) {
                        com.example.data.VoiceReadMode.OFF -> { /* diam, tidak bersuara */ }
                        com.example.data.VoiceReadMode.SENDER_ONLY -> {
                            com.example.data.TtsSpeaker.speak("Pesan masuk dari ${incoming.senderName}")
                        }
                        com.example.data.VoiceReadMode.FULL_MESSAGE -> {
                            com.example.data.TtsSpeaker.speak("Pesan dari ${incoming.senderName}: ${incoming.messageText}")
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

        // Speech Bubble View — pakai Compose, ukuran TETAP dari awal (bukan WRAP_CONTENT dinamis)
        // supaya window WindowManager di luar nggak pernah perlu resize ulang sama sekali.
        speechCard = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PetOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PetOverlayService)
            setContent {
                val text by remember { speechBubbleTextState }
                val fontSizeSp by com.example.data.BubbleSettings.fontSizeSp.collectAsState()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
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
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        }

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

        petContainer.addView(hideButton)
        petContainer.addView(speechCard)
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

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        clickTime = System.currentTimeMillis()
                        fallingJob?.cancel()
                        isDragging = true
                        isFalling = false
                        behaviorState = PetBehaviorState.IDLE
                        behaviorTicksRemaining = 0

                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        updatePetSprite(held = true)
                        speakBubble(PetQuotes.dragQuotes.random())
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            p.x = initialX + (event.rawX - initialTouchX).toInt()
                            p.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(overlayView, p)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        val duration = System.currentTimeMillis() - clickTime

                        val deltaX = abs(event.rawX - initialTouchX)
                        val deltaY = abs(event.rawY - initialTouchY)

                        if (duration < 200 && deltaX < 15 && deltaY < 15) {
                            // Pet Tapped
                            updatePetSprite(held = false)
                            speakBubble(PetQuotes.tapQuotes.random())
                            handleUserInteraction(5)
                            behaviorState = PetBehaviorState.IDLE
                            behaviorTicksRemaining = 0
                            requestSmartDialog()
                        } else {
                            // Released after drag -> tetap diam di titik itu (nggak jatuh lagi, atas request Master)
                            updatePetSprite(held = false)
                            handleUserInteraction(2)
                            behaviorState = PetBehaviorState.IDLE
                            behaviorTicksRemaining = 0
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAutonomousBehaviorLoop() {
        behaviorJob?.cancel()
        behaviorJob = serviceScope.launch {
            while (true) {
                delay(TICK_MS)
                if (isDragging || isFalling || isPetHidden) continue
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
            try {
                windowManager.updateViewLayout(overlayView, p)
            } catch (e: Exception) {
                // View belum siap / service sedang berhenti, abaikan
            }
        }
    }

    private fun startStairFallPhysics() {
        val p = params ?: return
        val displayMetrics = resources.displayMetrics
        val floorY = displayMetrics.heightPixels - 300
        val maxFallDistancePx = (250 * displayMetrics.density).toInt() // jatuh maksimal ~250dp, bukan selalu ke dasar layar
        val screenHeight = (p.y + maxFallDistancePx).coerceAtMost(floorY)
        val screenWidth = displayMetrics.widthPixels - 200

        fallingJob?.cancel()
        fallingJob = serviceScope.launch {
            isFalling = true
            updatePetSprite(held = true)
            speakBubble(PetQuotes.fallQuotes.random())

            var stepCount = 0
            val stepHeightPx = 22
            val stepWidthPx = 16

            while (p.y < screenHeight && isFalling && !isDragging) {
                stepCount++
                p.y = (p.y + stepHeightPx).coerceAtMost(screenHeight)

                // Stair-step horizontal shift (efek turun tangga / jatuh berayun)
                val horizontalShift = if (stepCount % 2 == 0) stepWidthPx else -stepWidthPx
                p.x = (p.x + horizontalShift).coerceIn(20, screenWidth)

                try {
                    windowManager.updateViewLayout(overlayView, p)
                } catch (e: Exception) {
                    break
                }

                delay(30L)
            }

            if (!isDragging && p.y >= screenHeight) {
                p.y = screenHeight
                try {
                    windowManager.updateViewLayout(overlayView, p)
                } catch (e: Exception) {
                    // Ignore
                }
                isFalling = false
                updatePetSprite(held = false)
                speakBubble("Sampai di bawah! ✨")
            }
        }
    }

    private fun togglePetVisibility() {
        isPetHidden = !isPetHidden
        if (isPetHidden) {
            speechCard?.visibility = View.GONE
            petImage?.visibility = View.GONE
            hideButton?.visibility = View.GONE
            showButtonPill?.visibility = View.VISIBLE
        } else {
            speechCard?.visibility = View.VISIBLE
            petImage?.visibility = View.VISIBLE
            hideButton?.visibility = View.VISIBLE
            showButtonPill?.visibility = View.GONE
            speakBubble("Halo lagi, Master!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fallingJob?.cancel()
        idleTimerJob?.cancel()
        behaviorJob?.cancel()
        com.example.data.TtsSpeaker.shutdown()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 50L
        private const val WALK_SPEED_PX = 5
        private const val CLIMB_SPEED_PX = 5
    }
}
