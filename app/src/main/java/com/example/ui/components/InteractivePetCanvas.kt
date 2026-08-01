package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.FallPhysicsMode
import com.example.model.PetPose
import com.example.model.PetQuotes
import com.example.model.CostumeManager
import com.example.service.NotificationBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage

@Composable
fun InteractivePetCanvas(
    modifier: Modifier = Modifier,
    fallPhysicsMode: FallPhysicsMode = FallPhysicsMode.STAIR_STEP,
    stepHeightPx: Float = 20f,
    stepWidthPx: Float = 14f,
    fallSpeedMs: Long = 30L,
    onHidePetClicked: (() -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
    ) {
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()

        val petWidthDp = 110.dp
        val petHeightDp = 110.dp
        val petWidthPx = with(density) { petWidthDp.toPx() }
        val petHeightPx = with(density) { petHeightDp.toPx() }

        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        // Screen floor boundary (bottom of screen)
        val floorY = (containerHeightPx - petHeightPx - 20f).coerceAtLeast(0f)
        val maxX = (containerWidthPx - petWidthPx).coerceAtLeast(0f)

        // Coordinates & States
        var petX by remember { mutableFloatStateOf((containerWidthPx / 2f - petWidthPx / 2f).coerceAtLeast(0f)) }
        var petY by remember { mutableFloatStateOf(floorY / 3f) }
        var isDragging by remember { mutableStateOf(false) }
        var isFalling by remember { mutableStateOf(false) }
        var isHidden by remember { mutableStateOf(false) }
        var currentPose by remember { mutableStateOf(PetPose.IDLE) }
        var speechBubble by remember { mutableStateOf<String?>("Hai! Seret aku ke atas lalu lepas ya~") }
        var heartsCount by remember { mutableIntStateOf(0) }
        var stairStepIndex by remember { mutableIntStateOf(0) }

        // Costume State from CostumeManager
        val kostumAktif by CostumeManager.kostumAktif.collectAsState()

        // Leveling & Emotion Timer States (Timestamp based for Doze Mode safety)
        val context = LocalContext.current
        var petLevel by remember { mutableIntStateOf(com.example.data.PetProgressStore.getLevel(context)) }
        var petXp by remember { mutableIntStateOf(com.example.data.PetProgressStore.getXp(context)) }
        val maxXp = com.example.data.PetProgressStore.MAX_XP_PER_LEVEL
        var petEmotion by remember { mutableStateOf("Senang") } // "Senang", "Bosan", "Kesal"
        var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var idleSeconds by remember { mutableIntStateOf(0) }

        // Dengerin update live dari overlay pet / dashboard biar nggak nyimpen angka basi sendiri
        LaunchedEffect(Unit) {
            com.example.model.PetDataBus.syncFlow.collect { sync ->
                petLevel = sync.petLevel
                petXp = sync.petXp
            }
        }

        // Helper to save data automatically to Obsidian & share data with floating overlay window
        fun syncToObsidian() {
            try {
                val data = com.example.data.PetProgressData(
                    petName = "Chibi Girl Shimeji",
                    level = petLevel,
                    currentXp = petXp,
                    maxXp = maxXp,
                    emotion = petEmotion,
                    happinessLevel = if (petEmotion == "Senang") 95 else if (petEmotion == "Bosan") 50 else 25,
                    energyLevel = 88,
                    positionX = petX,
                    positionY = petY,
                    physicsMode = fallPhysicsMode.name,
                    totalInteractions = heartsCount
                )
                com.example.data.ObsidianPetExporter.saveProgressToFile(context, data)
                com.example.data.PetProgressStore.save(context, petLevel, petXp)

                // Share data live with Overlay Window (FlutterOverlayWindow.shareData equivalent)
                com.example.model.PetDataBus.shareData(
                    level = petLevel,
                    xp = petXp,
                    emotion = petEmotion,
                    speechMessage = speechBubble ?: ""
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Helper when user interacts (tap / drag)
        fun resetIdleTimerAndAddXp(addedXp: Int = 5) {
            lastInteractionTimestamp = System.currentTimeMillis()
            idleSeconds = 0
            val oldEmotion = petEmotion
            petEmotion = "Senang"

            petXp += addedXp
            if (petXp >= maxXp) {
                val oldLevel = petLevel
                petLevel++
                petXp %= maxXp

                if (oldLevel <= 10 && petLevel > 10) {
                    speechBubble = "✨ EVOLUSI DEWASA! Aku telah tumbuh dewasa (Level $petLevel)! Aku akan selalu menjagamu & mengikutimu ❤️"
                    Toast.makeText(
                        context,
                        "✨ EVOLUSI KEDEWASAAN! Chibi Pet bertransformasi ke versi Dewasa (Level 11+)! Aset gambar & perilaku otomatis diperbarui.",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (oldLevel <= 18 && petLevel > 18) {
                    speechBubble = "💖 DEWASA FASE TSUNDERE (Level $petLevel)! M-bukan berarti aku nungguin kamu ya... tapi seneng deh dekat Master! 😳💕"
                    Toast.makeText(
                        context,
                        "💖 FASE DEWASA TSUNDERE (Level 18+)! Gelembung pesan berubah ke merah muda & ekspresi wajah memerah blushing diaktifkan!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    speechBubble = "🎉 LEVEL UP! Level $petLevel Tercapai!"
                    Toast.makeText(context, "🎉 LEVEL UP! Chibi Pet naik ke Level $petLevel!", Toast.LENGTH_SHORT).show()
                }
            }

            syncToObsidian()
        }

        // DateTime Timestamp comparison loop (Doze Mode Safe)
        LaunchedEffect(Unit) {
            while (true) {
                delay(3000L)
                if (!isDragging && !isFalling && !isHidden) {
                    val elapsedSeconds = ((System.currentTimeMillis() - lastInteractionTimestamp) / 1000).toInt()
                    if (elapsedSeconds >= 20 && petEmotion != "Kesal") {
                        petEmotion = "Kesal"
                        speechBubble = PetQuotes.kesalQuotes.random()
                        syncToObsidian()
                    } else if (elapsedSeconds >= 10 && petEmotion == "Senang") {
                        petEmotion = "Bosan"
                        speechBubble = PetQuotes.boredQuotes.random()
                        syncToObsidian()
                    }
                }
            }
        }

        // Idle bounce animation scale
        val idleScaleAnim = remember { Animatable(1f) }

        // Battery status state monitoring
        val batteryStateInfo by com.example.model.BatteryStatusManager.batteryState.collectAsState()

        // Collect incoming notifications from WhatsApp/Telegram & Mini-Game rewards & Battery Status
        LaunchedEffect(Unit) {
            com.example.data.ObsidianMemoryManager.loadMemoryFromObsidian(context)

            launch {
                while (true) {
                    val info = com.example.model.BatteryStatusManager.updateBatteryStatus(context)
                    if (info.isCharging) {
                        if (speechBubble == null || speechBubble?.contains("energiku sedang diisi ulang") != true) {
                            speechBubble = com.example.model.BatteryStatusManager.CHARGING_QUOTE
                        }
                    } else if (info.isLowBattery) {
                        if (speechBubble == null || speechBubble?.contains("tolong colokkan chasannya") != true) {
                            speechBubble = com.example.model.BatteryStatusManager.LOW_BATTERY_QUOTE
                        }
                    }
                    delay(5000L)
                }
            }
            
            launch {
                NotificationBus.notifications.collect { incoming ->
                    speechBubble = incoming.toSpeechBubbleText()
                    currentPose = PetPose.HAPPY
                    heartsCount++
                    delay(2000)
                    if (!isDragging && !isFalling) {
                        currentPose = PetPose.IDLE
                    }
                }
            }

            launch {
                com.example.model.PetGameBus.events.collect { reward ->
                    speechBubble = reward.messageText
                    currentPose = PetPose.HAPPY
                    heartsCount++
                    resetIdleTimerAndAddXp(reward.xpGained)
                    delay(3000)
                    if (!isDragging && !isFalling) {
                        currentPose = PetPose.IDLE
                    }
                }
            }
        }

        LaunchedEffect(currentPose, isDragging, isFalling) {
            if (currentPose == PetPose.IDLE && !isDragging && !isFalling) {
                idleScaleAnim.animateTo(
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            } else {
                idleScaleAnim.snapTo(1f)
            }
        }

        // Floating Control Overlay (Top Left Level Badge & Top Right Hide/Show toggle button)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Level & Emotion Badge Pill on Top Left
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (badgeText, badgeColor) = when {
                            petLevel <= 10 -> "🌱 Anak Lvl $petLevel" to Color(0xFF636363)
                            petLevel in 11..18 -> "✨ Dewasa Lvl $petLevel" to Color(0xFF565656)
                            else -> "💖 Tsundere Lvl $petLevel" to Color(0xFF616161)
                        }

                        Surface(
                            shape = CircleShape,
                            color = badgeColor
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        val (emotionColor, emotionIcon) = when (petEmotion) {
                            "Bosan" -> Color(0xFFA5A5A5) to "🥱 Bosan"
                            "Kesal" -> Color(0xFF767676) to "😤 Kesal"
                            else -> Color(0xFF878787) to "😄 Senang"
                        }

                        Surface(
                            shape = CircleShape,
                            color = emotionColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = emotionIcon,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = emotionColor
                            )
                        }

                        // Battery status pill badge
                        val (battColor, battText) = when {
                            batteryStateInfo.isCharging -> Color(0xFF949494) to "⚡ ${batteryStateInfo.batteryLevel}%"
                            batteryStateInfo.isLowBattery -> Color(0xFF868686) to "🪫 ${batteryStateInfo.batteryLevel}%"
                            else -> Color(0xFF878787) to "🔋 ${batteryStateInfo.batteryLevel}%"
                        }

                        Surface(
                            shape = CircleShape,
                            color = battColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = battText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = battColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(130.dp)
                    ) {
                        Text("XP $petXp/$maxXp", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        LinearProgressIndicator(
                            progress = { (petXp.toFloat() / maxXp.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = Color(0xFF5A5A5A)
                        )
                    }
                }
            }

            // Hide / Show pill control
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .shadow(8.dp, CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isHidden = !isHidden
                            if (isHidden) {
                                speechBubble = PetQuotes.hiddenQuotes.random()
                            } else {
                                speechBubble = "Aku kembali! Woohoo~"
                            }
                            onHidePetClicked?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHidden) MaterialTheme.colorScheme.primary else Color(0xFF636363)
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Pet",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHidden) "Tampilkan Pet" else "Hide Pet",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Render Pet Character View when not hidden
        AnimatedVisibility(
            visible = !isHidden,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(petLevel) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                val position = change?.position
                                if (position != null && !isDragging && !isFalling && !isHidden) {
                                    val touchX = position.x
                                    val touchY = position.y
                                    val petCenterX = petX + petWidthPx / 2f
                                    val petCenterY = petY + petHeightPx / 2f
                                    val dx = touchX - petCenterX
                                    val dy = touchY - petCenterY
                                    val distSq = dx * dx + dy * dy

                                    if (petLevel <= 10) {
                                        // Phase 1: Level <= 10 (Anak-anak) - Pet Menghindar jika kursor/sentuhan mendekat (< 220px)
                                        if (distSq < 220f * 220f && distSq > 0f) {
                                            val dist = kotlin.math.sqrt(distSq)
                                            val shiftX = -(dx / dist) * 45f
                                            val shiftY = -(dy / dist) * 45f
                                            petX = (petX + shiftX).coerceIn(0f, maxX)
                                            petY = (petY + shiftY).coerceIn(0f, floorY)
                                            speechBubble = PetQuotes.getMotionQuote(petLevel, isDodging = true)
                                        }
                                    } else {
                                        // Phase 2: Level > 10 (Dewasa) - Pet Otomatis Berjalan Mendekati & Mengikuti Posisi Koordinat Kursor
                                        if (distSq > 25f * 25f) {
                                            val targetX = (touchX - petWidthPx / 2f).coerceIn(0f, maxX)
                                            val targetY = (touchY - petHeightPx / 2f).coerceIn(0f, floorY)
                                            val followSpeedFactor = when {
                                                batteryStateInfo.isLowBattery -> 0.03f // Jalan lemas ketika baterai < 15%
                                                batteryStateInfo.isCharging -> 0.08f   // Gerak tenang bertapa saat di-cas
                                                else -> 0.15f
                                            }
                                            petX += (targetX - petX) * followSpeedFactor
                                            petY += (targetY - petY) * followSpeedFactor
                                            if (idleSeconds % 2 == 0) {
                                                speechBubble = PetQuotes.getMotionQuote(petLevel, isDodging = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Main Pet Container with offset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset { IntOffset(petX.roundToInt(), petY.roundToInt()) }
                        .width(petWidthDp)
                ) {
                    // Speech Bubble over Pet head
                    speechBubble?.let { text ->
                        val bubbleBg = if (petLevel > 18) Color(0xFFF5F5F5) else Color.White
                        val bubbleText = if (petLevel > 18) Color(0xFF3A3A3A) else Color(0xFF333333)
                        val bubbleBorderStroke = if (petLevel > 18) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF818181)) else null

                        Card(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .shadow(6.dp, RoundedCornerShape(16.dp))
                                .clickable {
                                    speechBubble = PetQuotes.getTapQuote(petLevel)
                                    heartsCount++
                                    resetIdleTimerAndAddXp(5)
                                },
                            shape = RoundedCornerShape(16.dp),
                            border = bubbleBorderStroke,
                            colors = CardDefaults.cardColors(
                                containerColor = bubbleBg
                            )
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = if (petLevel > 18) FontWeight.Bold else FontWeight.Medium,
                                color = bubbleText
                            )
                        }
                    }

                    // Hearts animation overlay on tap
                    if (heartsCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Heart",
                                tint = Color(0xFF818181),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "+$heartsCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF818181)
                            )
                        }
                    }

                    // Visual Status Indicator over Pet Head (⚡ Charging / 🪫 Low Battery / 😳 Level > 18 Tsundere Blushing)
                    if (batteryStateInfo.isCharging) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFD2D2D2),
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⚡ Posisi Bertapa Cas Energy ⚡",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    } else if (batteryStateInfo.isLowBattery) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF868686),
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🪫 Lapar... Colok Chasannya... 💧",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else if (petLevel > 18) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF818181),
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "😳 Mode Blushing Tsundere 💖",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Pet Chibi Girl Image with Drag & Gesture Listener
                    val imageRes = when (currentPose) {
                        PetPose.HELD -> R.drawable.img_chibi_pet_held
                        PetPose.FALLING -> R.drawable.img_chibi_pet_held
                        else -> R.drawable.img_chibi_pet_idle
                    }

                    val rotationAngle by animateFloatAsState(
                        targetValue = when {
                            isDragging -> if (stairStepIndex % 2 == 0) -8f else 8f
                            isFalling -> if (stairStepIndex % 2 == 0) -12f else 12f
                            else -> 0f
                        },
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .size(petWidthDp, petHeightDp)
                            .scale(if (isDragging) 1.15f else idleScaleAnim.value)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        isFalling = false
                                        currentPose = PetPose.HELD
                                        speechBubble = PetQuotes.dragQuotes.random()
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        currentPose = PetPose.IDLE
                                        // Dulu ada trigger jatuh (Stair Fall) di sini, tapi fitur
                                        // itu udah dimatiin di pet asli (overlay) -- preview ini
                                        // sekarang disamain: pet cuma diem di posisi terakhir
                                        // pas dilepas, gak jatuh otomatis.
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        currentPose = PetPose.IDLE
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        // Update X & Y coordinates during finger drag
                                        petX = (petX + dragAmount.x).coerceIn(0f, maxX)
                                        petY = (petY + dragAmount.y).coerceIn(0f, floorY)
                                    }
                                )
                            }
                            .clickable {
                                speechBubble = PetQuotes.getTapQuote(petLevel)
                                currentPose = PetPose.HAPPY
                                heartsCount++
                                resetIdleTimerAndAddXp(5)
                                coroutineScope.launch {
                                    delay(1000)
                                    if (!isDragging && !isFalling) {
                                        currentPose = PetPose.IDLE
                                    }
                                }
                            }
                    ) {
                        // LAYER 1 & 2: Base Body + Costume Stack or Custom Gallery / Online URL Pet
                        val effectiveCostume = CostumeManager.getEffectiveCostumeUrlOrId(kostumAktif, petLevel)

                        if (effectiveCostume.startsWith("http://") || effectiveCostume.startsWith("https://")) {
                            // Online Image / GIF Network URL Character (Evolves automatically if level > 10)
                            AsyncImage(
                                model = effectiveCostume,
                                contentDescription = "Chibi Pet Avatar Network URL",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(if (isDragging) 12.dp else 4.dp, CircleShape)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (effectiveCostume.startsWith("/") || effectiveCostume.contains("custom_pet")) {
                            // Custom Gallery Image / GIF Character File
                            AsyncImage(
                                model = java.io.File(effectiveCostume),
                                contentDescription = "Custom Pet Avatar Galeri",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(if (isDragging) 12.dp else 4.dp, CircleShape)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // LAYER 1 (Layer Dasar): Body Base Layer (body_default)
                            Image(
                                painter = painterResource(id = imageRes),
                                contentDescription = "Tubuh Pet Dasar (body_default)",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(if (isDragging) 12.dp else 4.dp, CircleShape),
                                contentScale = ContentScale.Fit
                            )

                            // LAYER 2 (Layer Baju): Costume Overlay Layer stacked on top based on kostumAktif
                            val costumeRes = when (kostumAktif) {
                                "baju_sekolah" -> R.drawable.img_costume_school
                                "gaun_pesta" -> R.drawable.img_costume_dress
                                "piyama" -> R.drawable.img_costume_pajamas
                                else -> null
                            }

                            if (costumeRes != null) {
                                Image(
                                    painter = painterResource(id = costumeRes),
                                    contentDescription = "Pakaian Pet ($kostumAktif)",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        // LAYER 3: Blushing Cheeks Effect Overlay (Level > 18 Adult Phase)
                        if (petLevel > 18) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF818181).copy(alpha = 0.55f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF818181).copy(alpha = 0.55f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floor indicator line
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "── Floor Boundary ──",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                fontWeight = FontWeight.Light
            )
        }
    }
}
