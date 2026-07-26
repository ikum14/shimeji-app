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

    private var isDragging = false
    private var isFalling = false
    private var isPetHidden = false

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
            textSize = 11f
            setTextColor(0xFF333333.toInt())
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(16, 8, 16, 8)
            elevation = 12f
        }
        speechText = speechCard as TextView

        // Pet Image View
        petImage = ImageView(this).apply {
            setImageResource(R.drawable.img_chibi_pet_idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val sizePx = (110 * resources.displayMetrics.density).toInt()
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
                        } else {
                            // Released after drag -> Trigger Stair-fall physics!
                            handleUserInteraction(2)
                            startStairFallPhysics()
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

    private fun startStairFallPhysics() {
        val p = params ?: return
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels - 300
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
        serviceScope.launch {
            fallingJob?.cancel()
        }
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
    }
}
