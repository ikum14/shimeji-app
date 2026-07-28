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

class PetOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var petImage: ImageView? = null
    private var speechText: TextView? = null
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
    private var behaviorTicksRemaining = 0

    // Leveling & Emotion Timer States for Overlay Pet (Timestamp based for Doze Mode safety)
    private var petLevel = 5
    private var petXp = 75
    private val maxXp = 100
    private var petEmotion = "Senang" // "Senang", "Bosan", "Kesal"
    private var lastInteractionTimestamp = System.currentTimeMillis()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        createFloatingPetOverlay()
        listenForNotificationBus()
        listenForPetDataBus()
        startIdleEmotionTimer()
        // Autonomous walk/climb dimatikan atas request user — pet diam di tempat, tetap bisa di-drag manual.
        // startAutonomousBehaviorLoop()
        com.example.data.TtsSpeaker.init(applicationContext)
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
                speechMessage = speechText?.text?.toString() ?: ""
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
            speechText?.text = "🎉 LEVEL UP! Sekarang Level $petLevel!"
            android.widget.Toast.makeText(this, "🎉 LEVEL UP! Pet menjadi Level $petLevel!", android.widget.Toast.LENGTH_SHORT).show()
        }
        syncToObsidian()
    }

    /**
     * Minta Gemini bikin kalimat celotehan baru berdasarkan biodata.md & status pet saat ini.
     * Kalimat template (PetQuotes) tetap tampil dulu sebagai placeholder instan,
     * lalu ditimpa begitu balasan AI datang (butuh beberapa detik, ada koneksi internet).
     */
    private fun requestSmartDialog() {
        if (!com.example.data.GeminiPetBrain.isConfigured()) return
        serviceScope.launch {
            val memory = com.example.data.ObsidianMemoryManager.userMemory.value
            val reply = com.example.data.GeminiPetBrain.generateDialog(
                userName = memory.userName,
                userHobby = memory.userHobby,
                petLevel = petLevel,
                petEmotion = petEmotion
            )
            speechText?.text = reply
        }
    }

    private fun startIdleEmotionTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = serviceScope.launch {
            while (true) {
                delay(3000L)
                if (!isDragging && !isFalling && !isPetHidden) {
                    val elapsedSeconds = ((System.currentTimeMillis() - lastInteractionTimestamp) / 1000).toInt()
                    if (elapsedSeconds >= 20 && petEmotion != "Kesal") {
                        petEmotion = "Kesal"
                        speechText?.text = PetQuotes.kesalQuotes.random()
                        syncToObsidian()
                    } else if (elapsedSeconds >= 10 && petEmotion == "Senang") {
                        petEmotion = "Bosan"
                        speechText?.text = PetQuotes.boredQuotes.random()
                        syncToObsidian()
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
                    speechText?.text = syncData.speechMessage
                }
            }
        }
    }

    private fun listenForNotificationBus() {
        serviceScope.launch {
            NotificationBus.notifications.collect { incoming ->
                if (!isPetHidden) {
                    speechCard?.visibility = View.VISIBLE
                    speechText?.text = incoming.toSpeechBubbleText()
                    petImage?.setImageResource(R.drawable.img_chibi_pet_idle)

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
        
        // Container Layout
        val petContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Speech Bubble View
        speechCard = TextView(this).apply {
            text = "Halo Master! Seret aku ke atas ya~"
            textSize = 17f
            setTextColor(0xFF333333.toInt())
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(26, 16, 26, 16)
            elevation = 12f
            maxWidth = (230 * resources.displayMetrics.density).toInt()
            setLineSpacing(6f, 1.1f)
        }
        speechText = speechCard as TextView

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

                        petImage?.setImageResource(R.drawable.img_chibi_pet_held)
                        speechText?.text = PetQuotes.dragQuotes.random()
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
                            petImage?.setImageResource(R.drawable.img_chibi_pet_idle)
                            speechText?.text = PetQuotes.tapQuotes.random()
                            handleUserInteraction(5)
                            behaviorState = PetBehaviorState.IDLE
                            behaviorTicksRemaining = 0
                            requestSmartDialog()
                        } else {
                            // Released after drag -> tetap diam di titik itu (nggak jatuh lagi, atas request Master)
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
                    speechText?.text = "Manjat ah~ 🧗"
                }
            }
            PetBehaviorState.WALK_RIGHT -> {
                p.x = (p.x + WALK_SPEED_PX).coerceAtMost(rightEdgeX)
                moved = true
                if (p.x >= rightEdgeX) {
                    behaviorState = PetBehaviorState.CLIMB_UP
                    behaviorTicksRemaining = 0
                    speechText?.text = "Manjat ah~ 🧗"
                }
            }
            PetBehaviorState.CLIMB_UP -> {
                p.y = (p.y - CLIMB_SPEED_PX).coerceAtLeast(topMarginY)
                moved = true
                if (p.y <= topMarginY) {
                    if ((0..2).random() == 0) {
                        behaviorState = PetBehaviorState.CLIMB_DOWN
                        behaviorTicksRemaining = 0
                        speechText?.text = "Turun lagi ah~"
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
            petImage?.setImageResource(R.drawable.img_chibi_pet_held)
            speechText?.text = PetQuotes.fallQuotes.random()

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
                petImage?.setImageResource(R.drawable.img_chibi_pet_idle)
                speechText?.text = "Sampai di bawah! ✨"
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
            speechText?.text = "Halo lagi, Master!"
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
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 50L
        private const val WALK_SPEED_PX = 5
        private const val CLIMB_SPEED_PX = 5
    }
}
