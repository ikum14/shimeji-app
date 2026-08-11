package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlin.math.roundToInt
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.model.FallPhysicsMode
import com.example.service.PetOverlayService
import com.example.ui.components.InteractivePetCanvas

import com.example.data.ObsidianPetExporter
import com.example.data.PetProgressData
import com.example.service.IncomingPetNotification
import com.example.service.NotificationBus
import com.example.model.CostumeManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

/**
 * 1 baris slider buat 1 titik waktu mood (Bosan/Kesal/Marah/dst). Dipakai berkali-kali di
 * card "Jadwal Mood" biar gak nulis blok Slider yang sama 7x.
 */
@Composable
private fun MoodTimingSliderRow(
    label: String,
    context: android.content.Context,
    getSec: (android.content.Context) -> Float,
    setSec: (android.content.Context, Float) -> Unit
) {
    var sliderIndex by remember {
        mutableFloatStateOf(com.example.data.MoodTimingSettings.indexOf(getSec(context)).toFloat())
    }
    val steps = com.example.data.MoodTimingSettings.STEPS_SEC
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 12.sp, color = Color.White)
            val currentIdx = sliderIndex.roundToInt().coerceIn(0, steps.size - 1)
            val totalSec = steps[currentIdx].toInt()
            val displayText = if (totalSec < 60) "$totalSec detik" else "${totalSec / 60} menit"
            Text(text = displayText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4))
        }
        Slider(
            value = sliderIndex,
            onValueChange = {
                sliderIndex = it
                val idx = it.roundToInt().coerceIn(0, steps.size - 1)
                setSec(context, steps[idx])
            },
            valueRange = 0f..(steps.size - 1).toFloat(),
            steps = steps.size - 2
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pet Progress & Level Stats — dibaca dari store yang sama dipakai PetOverlayService, biar nggak beda angka lagi
    var petName by remember { mutableStateOf(com.example.data.PetProgressStore.getName(context)) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }
    var petLevel by remember { mutableStateOf(com.example.data.PetProgressStore.getLevel(context)) }
    var petXp by remember { mutableStateOf(com.example.data.PetProgressStore.getXp(context)) }
    val maxXp = com.example.data.PetProgressStore.MAX_XP_PER_LEVEL
    var showResetLevelDialog by remember { mutableStateOf(false) }
    var totalInteractions by remember { mutableStateOf(38) }
    var happiness by remember { mutableStateOf(95) }
    var energy by remember { mutableStateOf(88) }

    // Dengerin update live dari overlay pet (misal di-tap atau dapet notifikasi pas dashboard lagi dibuka)
    LaunchedEffect(Unit) {
        com.example.model.PetDataBus.syncFlow.collect { sync ->
            petLevel = sync.petLevel
            petXp = sync.petXp
        }
    }

    // File Export State for Obsidian
    val targetFile = remember { ObsidianPetExporter.getTargetMarkdownFile(context) }
    var savedFilePath by remember { mutableStateOf(targetFile.absolutePath) }
    var filePreviewContent by remember {
        mutableStateOf(ObsidianPetExporter.readProgressFromFile(context))
    }

    // Function to trigger save to markdown
    fun savePetProgressToMarkdown() {
        val data = PetProgressData(
            petName = petName,
            level = petLevel,
            currentXp = petXp,
            maxXp = maxXp,
            emotion = if (happiness > 70) "HAPPY" else "IDLE",
            happinessLevel = happiness,
            energyLevel = energy,
            positionX = 220f,
            positionY = 680f,
            physicsMode = "STAIR_STEP",
            totalInteractions = totalInteractions
        )
        val file = ObsidianPetExporter.saveProgressToFile(context, data)
        savedFilePath = file.absolutePath
        filePreviewContent = file.readText()
        Toast.makeText(context, "Progress tersimpan ke ${file.name}!", Toast.LENGTH_SHORT).show()
    }

    // Costume State & Handler
    val kostumAktif by CostumeManager.kostumAktif.collectAsState()
    val listKarakter by CostumeManager.listKarakter.collectAsState()

    // Obsidian Memory System State
    val userMemory by com.example.data.ObsidianMemoryManager.userMemory.collectAsState()
    var isEditingBiodata by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0=Home, 1=Obsidian, 2=TTS
    var biodataInputText by remember { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(com.example.data.NotificationVoiceSettings.getMode(context)) }
    var isPetVoiceMuted by remember { mutableStateOf(com.example.data.PetVoiceSettings.isMuted(context)) }
    var chatterIntervalIndex by remember {
        mutableFloatStateOf(
            com.example.data.IdleChatterSettings.indexOf(
                com.example.data.IdleChatterSettings.getIntervalSeconds(context)
            ).toFloat()
        )
    }
    com.example.data.BubbleStyleSettings.init(context)
    var useMoodColor by remember { mutableStateOf(com.example.data.BubbleStyleSettings.useMoodColor.value) }
    var bubbleBgColor by remember { mutableStateOf(com.example.data.BubbleStyleSettings.bgColor.value) }
    var bubbleTextColor by remember { mutableStateOf(com.example.data.BubbleStyleSettings.textColor.value) }
    com.example.data.BubbleSettings.init(context)
    var bubbleFontSize by remember { mutableFloatStateOf(com.example.data.BubbleSettings.fontSizeSp.value) }
    var availableVoices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var selectedVoiceName by remember { mutableStateOf(com.example.data.NotificationVoiceSettings.getSelectedVoiceName(context)) }
    var availableEngines by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedEnginePackage by remember { mutableStateOf(com.example.data.TtsSpeaker.getSelectedEnginePackage(context)) }
    val ttsCoroutineScope = rememberCoroutineScope()
    var ttsPitch by remember { mutableFloatStateOf(com.example.data.TtsVoiceSettings.getPitch(context)) }
    var ttsSpeed by remember { mutableFloatStateOf(com.example.data.TtsVoiceSettings.getSpeed(context)) }
    var ttsPauseMs by remember { mutableFloatStateOf(com.example.data.TtsVoiceSettings.getPauseMs(context).toFloat()) }
    var ttsPauseAtEmoji by remember { mutableStateOf(com.example.data.TtsVoiceSettings.getPauseAtEmoji(context)) }
    var petQuoteLanguage by remember { mutableStateOf(com.example.data.TtsVoiceSettings.getLanguage(context)) }

    LaunchedEffect(Unit) {
        com.example.data.TtsSpeaker.init(context)
        availableEngines = com.example.data.TtsSpeaker.getInstalledEngines(context)
    }

    LaunchedEffect(Unit) {
        com.example.data.TtsSpeaker.init(context)
        repeat(10) {
            val voices = com.example.data.TtsSpeaker.getAvailableVoices()
            if (voices.isNotEmpty()) {
                availableVoices = voices
                if (selectedVoiceName == null) {
                    selectedVoiceName = com.example.data.TtsSpeaker.getCurrentVoiceName()
                }
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(400)
        }
    }

    // Mini-Game Suwit State
    var winStreak by remember { mutableIntStateOf(0) }
    var gameResultText by remember { mutableStateOf("Ayo tanding Suwit lawan Chibi Pet! Menang = +20 XP | Win Streak 3x = Unlock Kostum Baru! 🔥") }

    LaunchedEffect(Unit) {
        CostumeManager.initKarakter(context)
        CostumeManager.initUnlockSlots(context)
        val mem = com.example.data.ObsidianMemoryManager.loadMemoryFromObsidian(context)
        biodataInputText = mem.rawContent
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val newItem = CostumeManager.tambahKarakterDariGaleri(context, uri)
            if (newItem != null) {
                Toast.makeText(
                    context,
                    "🖼️ Karakter '${newItem.name}' dari galeri berhasil disalin & ditambahkan!",
                    Toast.LENGTH_LONG
                ).show()
                savePetProgressToMarkdown()
            } else {
                Toast.makeText(context, "Gagal mengimpor gambar/GIF dari galeri.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        com.example.model.PoseSpriteManager.init(context)
    }
    val poseFiles by com.example.model.PoseSpriteManager.poseFiles.collectAsState()
    var pendingPoseSlot by remember { mutableStateOf<com.example.model.PoseSpriteManager.PoseSlot?>(null) }
    val poseSpriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val slot = pendingPoseSlot
        if (uri != null && slot != null) {
            val success = com.example.model.PoseSpriteManager.addPoseImage(context, slot, uri)
            Toast.makeText(
                context,
                if (success) "✅ Gambar ditambahin ke '${slot.label}'!" else "Gagal upload gambar.",
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingPoseSlot = null
    }

    // 🎁 Hadiah Unlock -- 3 slot gambar buat evolusi kostum & Mahkota Juara
    val unlockImages by CostumeManager.unlockImages.collectAsState()
    var pendingUnlockSlot by remember { mutableStateOf<CostumeManager.UnlockSlot?>(null) }
    val unlockSlotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val slot = pendingUnlockSlot
        if (uri != null && slot != null) {
            val success = CostumeManager.setUnlockImage(context, slot, uri)
            Toast.makeText(
                context,
                if (success) "✅ Gambar hadiah '${slot.label}' disimpan!" else "Gagal upload gambar.",
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingUnlockSlot = null
    }

    fun gantiKostum(namaKostum: String) {
        CostumeManager.gantiKostum(namaKostum)
        Toast.makeText(
            context,
            "👗 Pakaian diubah: ${CostumeManager.getCostumeDisplayName(namaKostum)}!",
            Toast.LENGTH_SHORT
        ).show()
        savePetProgressToMarkdown()
    }

    // Check overlay permission
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        )
    }

    var isServiceRunning by remember { mutableStateOf(false) }

    // Status izin Notification Access -- disimpan sebagai state biar bisa di-refresh pas
    // resume dari Settings, bukan dicek ulang manual tiap recompose (itu penyebab UI-nya
    // "nyangkut" nunjukkin belum diizinin padahal di system settings udah aktif).
    var hasNotificationAccess by remember {
        mutableStateOf(com.example.data.NotificationVoiceSettings.hasNotificationAccess(context))
    }

    // Status izin "Semua Akses File" (buat baca/tulis folder Download/Obsidian) -- pola sama
    // kayak hasNotificationAccess di atas, biar konsisten & gak nyangkut pas balik dari Settings.
    var hasAllFilesAccess by remember {
        mutableStateOf(com.example.data.VaultPathProvider.hasAllFilesAccess())
    }

    // Re-check permission on resume & handle auto backup on app close/pause
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                }
                hasNotificationAccess = com.example.data.NotificationVoiceSettings.hasNotificationAccess(context)
                hasAllFilesAccess = com.example.data.VaultPathProvider.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_chibi_pet_idle),
                                contentDescription = "Pet Logo",
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Desktop Pet Shimeji",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Karakter Chibi Perempuan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Obsidian") },
                    label = { Text("Obsidian") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "TTS") },
                    label = { Text("TTS") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedTab == 0) {
            // Permission Alert / Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasOverlayPermission)
                        Color(0xFFF0F0F0)
                    else
                        Color(0xFFF4F4F4)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Permission Status",
                        tint = if (hasOverlayPermission) Color(0xFF5D5D5D) else Color(0xFF747474),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasOverlayPermission) "Izin Floating Overlay Aktif" else "Membutuhkan Izin Overlay",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (hasOverlayPermission) Color(0xFF434343) else Color(0xFF747474)
                        )
                        Text(
                            text = if (hasOverlayPermission)
                                "Pet dapat dimunculkan mengambang di atas semua aplikasi HP Android kamu."
                            else
                                "Aktifkan \"Tampilkan di atas aplikasi lain\" agar Chibi Pet bisa muncul di layar HP.",
                            fontSize = 12.sp,
                            color = Color(0xFF424242)
                        )
                    }

                    if (!hasOverlayPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF878787)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Izinkan", fontSize = 12.sp)
                        }
                    }
                }
            }

            // System Floating Overlay Controller Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Overlay Control",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "System Floating Overlay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { start ->
                                if (hasOverlayPermission) {
                                    val serviceIntent = Intent(context, PetOverlayService::class.java)
                                    if (start) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                        isServiceRunning = true
                                        Toast.makeText(context, "Pet overlay dimunculkan di atas layar!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        context.stopService(serviceIntent)
                                        isServiceRunning = false
                                        Toast.makeText(context, "Pet overlay dihentikan.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Izinkan Overlay terlebih dahulu di pengaturan!", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (hasOverlayPermission) {
                                    val serviceIntent = Intent(context, PetOverlayService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                    isServiceRunning = true
                                    Toast.makeText(context, "Chibi Pet diaktifkan!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Mohon beri izin Overlay di atas layar!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF636363)
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Spawn Pet")
                        }

                        OutlinedButton(
                            onClick = {
                                val serviceIntent = Intent(context, PetOverlayService::class.java)
                                context.stopService(serviceIntent)
                                isServiceRunning = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hentikan")
                        }
                    }
                }
            }

            // WhatsApp & Telegram Notification Listener Service Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F0F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF939393).copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "💬",
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Listener Notifikasi Chat",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF434343)
                                )
                                Text(
                                    text = "WhatsApp & Telegram Auto Bubble",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5D5D5D)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Buka Pengaturan > Akses Notifikasi", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF939393))
                        ) {
                            Text("Akses Notifikasi", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Text(
                        text = "Ketika ada notifikasi pesan WhatsApp / Telegram masuk, pet akan memunculkan gelembung percakapan berisi pesan tersebut di atas layar!",
                        fontSize = 11.sp,
                        color = Color(0xFF333333)
                    )

                    // Test Simulator Buttons
                    Text(
                        text = "⚡ Uji Simulasi Notifikasi Chat:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF434343)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val simulated = IncomingPetNotification(
                                    appName = "WhatsApp",
                                    packageName = "com.whatsapp",
                                    senderName = "Master Budi",
                                    messageText = "Halo! Jangan lupa istirahat ya, Pet Shimeji~"
                                )
                                NotificationBus.emitNotification(simulated)
                                Toast.makeText(context, "Notifikasi WhatsApp Disimulasikan!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF939393))
                        ) {
                            Text("Tes WA", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val simulated = IncomingPetNotification(
                                    appName = "Telegram",
                                    packageName = "org.telegram.messenger",
                                    senderName = "Anime Club",
                                    messageText = "Update episode baru sudah rilis hari ini!"
                                )
                                NotificationBus.emitNotification(simulated)
                                Toast.makeText(context, "Notifikasi Telegram Disimulasikan!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF676767))
                        ) {
                            Text("Tes Telegram", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Chibi Pet Costume & Outfit Wardrobe Card (Multi-Layer Stack Customization)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF636363).copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "👗",
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Lemari Kostum & Pakaian Chibi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF3A3A3A)
                                )
                                Text(
                                    text = "Multi-Layer Stack Overlay Structure",
                                    fontSize = 11.sp,
                                    color = Color(0xFF525252)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF636363).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = CostumeManager.getCostumeDisplayName(kostumAktif),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF636363)
                            )
                        }
                    }

                    Text(
                        text = "Layer dasar: Tubuh Pet (body_default). Layer atas: Pakaian yang berganti secara instan saat gantiKostum(namaKostum) dipanggil!",
                        fontSize = 11.sp,
                        color = Color(0xFF323232)
                    )

                    // Add Custom Character from Gallery HP Button
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF535353))
                    ) {
                        Text(
                            text = "🖼️ + Tambah Gambar / GIF Karakter dari Galeri HP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Dynamic Costume & Custom Character Selection Row (listKarakter)
                    Text(
                        text = "Daftar Karakter & Kostum Aktif (${listKarakter.size} Pilihan):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3A3A3A)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listKarakter) { item ->
                            val isSelected = kostumAktif == item.id
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) Color(0xFF636363) else Color.White,
                                    shadowElevation = if (isSelected) 6.dp else 2.dp,
                                    modifier = Modifier
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF525252) else Color.LightGray,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                ) {
                                    Button(
                                        onClick = { gantiKostum(item.id) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) Color(0xFF636363) else Color.White
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (item.isCustom) "🖼️" else when (item.id) {
                                                    "baju_sekolah" -> "🏫"
                                                    "gaun_pesta" -> "👑"
                                                    "piyama" -> "🌙"
                                                    else -> "🌸"
                                                },
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = item.name,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color.DarkGray
                                            )
                                        }
                                    }
                                }
                                // Tombol hapus -- semua item lemari boleh dihapus (baik upload galeri
                                // maupun kostum bawaan) KECUALI "Default Chibi" (fallback utama, wajib ada)
                                if (item.id != "default") {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-6).dp)
                                            .size(20.dp)
                                            .background(Color(0xFFD32F2F), RoundedCornerShape(50))
                                            .clickable {
                                                val wasDeleted = com.example.model.CostumeManager.hapusKarakter(context, item)
                                                if (wasDeleted) {
                                                    Toast.makeText(context, "Karakter '${item.name}' dihapus", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "'${item.name}' gak bisa dihapus dari sini", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 🎬 Pose Kustom Card -- upload gambar/GIF per pose (idle/hide/tap/drag),
            // beda dari kostum biasa (yang cuma satu gambar buat semuanya).
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎬 Pose Kustom",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Upload beberapa gambar per momen (idle/hide/tap/drag) -- SATU dipilih RANDOM tiap dipakai (bukan slideshow/animasi jalan berurutan), biar gak itu-itu terus. Slot kosong otomatis pakai sprite default.\n\n💡 Mau ada gerakan (kedip/ekor gerak dll)? Upload .gif, bukan gambar diam -- .gif yang kepilih random bakal jalan sendiri. Gambar diam sebanyak apa pun tetap diam.",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )

                    com.example.model.PoseSpriteManager.PoseSlot.entries
                        .groupBy { it.group }
                        .forEach { (groupName, slots) ->
                            Text(
                                text = groupName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80CBC4)
                            )
                            slots.forEach { slot ->
                                val filledPaths = poseFiles[slot.key] ?: emptyList()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${slot.label} (${filledPaths.size})",
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                pendingPoseSlot = slot
                                                poseSpriteLauncher.launch("image/*")
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("+ Tambah", fontSize = 10.sp)
                                        }
                                    }
                                    if (filledPaths.isNotEmpty()) {
                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filledPaths) { path ->
                                                Box {
                                                    coil.compose.AsyncImage(
                                                        model = path,
                                                        contentDescription = slot.label,
                                                        imageLoader = com.example.data.GifAwareImageLoader.get(context),
                                                        modifier = Modifier
                                                            .size(56.dp)
                                                            .background(Color(0xFF37474F), RoundedCornerShape(8.dp))
                                                    )
                                                    if (path.endsWith(".gif", ignoreCase = true)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomStart)
                                                                .background(Color(0xCC2E7D32), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("GIF", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .size(20.dp)
                                                            .background(Color(0xCC000000), RoundedCornerShape(50))
                                                            .clickable {
                                                                com.example.model.PoseSpriteManager.removePoseImage(context, slot, path)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("✕", fontSize = 10.sp, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            }

            // 🎁 Hadiah Unlock Card -- 3 slot: 2 evolusi kostum (level 11-18 & 19+) + Mahkota Juara.
            // Satu slot cuma nampung 1 gambar (beda dari Pose Kustom yang bisa banyak & random).
            // Slot kosong otomatis fallback ke foto stok bawaan (lihat CostumeManager.getEffectiveCostumeUrlOrId).
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎁 Hadiah Unlock",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Gambar yang muncul otomatis pas Chibi Pet naik level / menang Suwit 3x beruntun. Upload buat ganti foto stok bawaan yang gak sesuai.",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )

                    CostumeManager.UnlockSlot.entries.forEach { slot ->
                        val currentPath = unlockImages[slot.key]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = slot.label,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        pendingUnlockSlot = slot
                                        unlockSlotLauncher.launch("image/*")
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(if (currentPath != null) "Ganti" else "+ Upload", fontSize = 10.sp)
                                }
                            }
                            if (currentPath != null) {
                                Box {
                                    coil.compose.AsyncImage(
                                        model = currentPath,
                                        contentDescription = slot.label,
                                        imageLoader = com.example.data.GifAwareImageLoader.get(context),
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color(0xFF37474F), RoundedCornerShape(8.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .background(Color(0xCC000000), RoundedCornerShape(50))
                                            .clickable {
                                                CostumeManager.clearUnlockImage(context, slot)
                                                Toast.makeText(context, "Gambar '${slot.label}' dihapus, balik ke foto bawaan.", Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✕", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            } else {
                                Text(
                                    text = "Belum diupload -- pakai foto stok bawaan",
                                    fontSize = 10.sp,
                                    color = Color(0xFF78909C)
                                )
                            }
                        }
                    }
                }
            }

            // In-App Interactive Canvas / Playground Sandbox Frame
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Interactive Sandbox Playground",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Seret & Lepas Karakter",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Interactive Pet Playground Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFF1F1F1),
                                        Color(0xFFF3F3F3)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        InteractivePetCanvas(
                            fallPhysicsMode = FallPhysicsMode.STAIR_STEP,
                            stepHeightPx = 20f,
                            stepWidthPx = 14f,
                            fallSpeedMs = 30L
                        )
                    }
                }
            }

            // Pet Status & Care Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF818181))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Status & Progress Pet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF5A5A5A).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Level $petLevel",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF5A5A5A)
                            )
                        }
                    }

                    // Level XP Bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pengalaman (XP)", fontSize = 12.sp)
                            Text("$petXp / $maxXp XP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A5A5A))
                        }
                        LinearProgressIndicator(
                            progress = { (petXp.toFloat() / maxXp.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Color(0xFF5A5A5A)
                        )
                    }

                    // Happiness
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kebahagiaan (Happiness)", fontSize = 12.sp)
                            Text("$happiness%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF636363))
                        }
                        LinearProgressIndicator(
                            progress = { (happiness / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Color(0xFF636363)
                        )
                    }

                    // Energy
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Energi", fontSize = 12.sp)
                            Text("$energy%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7E7E7E))
                        }
                        LinearProgressIndicator(
                            progress = { (energy / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Color(0xFF7E7E7E)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                petXp += 60
                                totalInteractions++
                                happiness = (happiness + 5).coerceAtMost(100)
                                if (petXp >= maxXp) {
                                    petLevel++
                                    petXp = 0
                                    Toast.makeText(context, "🎉 LEVEL UP! Pet menjadi Level $petLevel!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "+60 XP dari Elusan Master!", Toast.LENGTH_SHORT).show()
                                }
                                savePetProgressToMarkdown()
                                com.example.data.PetProgressStore.save(context, petLevel, petXp)
                                com.example.model.PetDataBus.shareData(
                                    level = petLevel,
                                    xp = petXp,
                                    emotion = "Senang",
                                    speechMessage = "Makasih udah dielus, Master~"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Elus Pet (+60 XP)", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                savePetProgressToMarkdown()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF565656))
                        ) {
                            Text("Simpan .md", fontSize = 11.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            editNameInput = petName
                            showEditNameDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ganti Nama Pet", fontSize = 11.sp, color = Color(0xFF4A90D9))
                    }

                    TextButton(
                        onClick = { showResetLevelDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Level ke 1", fontSize = 11.sp, color = Color(0xFFB33A3A))
                    }
                }
            }

            if (showEditNameDialog) {
                AlertDialog(
                    onDismissRequest = { showEditNameDialog = false },
                    title = { Text("Ganti Nama Pet") },
                    text = {
                        OutlinedTextField(
                            value = editNameInput,
                            onValueChange = { editNameInput = it },
                            singleLine = true,
                            label = { Text("Nama baru") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val newName = editNameInput.trim()
                            if (newName.isNotBlank()) {
                                petName = newName
                                com.example.data.PetProgressStore.saveName(context, newName)
                                Toast.makeText(context, "Nama pet diganti jadi \"$newName\"", Toast.LENGTH_SHORT).show()
                            }
                            showEditNameDialog = false
                        }) {
                            Text("Simpan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditNameDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }

            if (showResetLevelDialog) {
                AlertDialog(
                    onDismissRequest = { showResetLevelDialog = false },
                    title = { Text("Reset Level Pet?") },
                    text = { Text("Level & XP pet bakal balik ke Level 1 / 0 XP. Data lain (kostum, pose kustom, hadiah unlock, dll) gak kesentuh sama sekali. Gak bisa dibatalin.") },
                    confirmButton = {
                        TextButton(onClick = {
                            petLevel = 1
                            petXp = 0
                            com.example.data.PetProgressStore.reset(context)
                            com.example.model.PetDataBus.shareData(
                                level = petLevel,
                                xp = petXp,
                                emotion = "Senang",
                                speechMessage = "Level ku direset, mulai dari awal lagi~"
                            )
                            showResetLevelDialog = false
                            Toast.makeText(context, "Level pet direset ke Level 1", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Reset", color = Color(0xFFB33A3A))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetLevelDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }

            }
            if (selectedTab == 1) {
            // Obsidian Integration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF262626) // Obsidian Purple Dark Accent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF6F6F6F).copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Obsidian Sync",
                                    tint = Color(0xFFA2A2A2),
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Integrasi Obsidian Vault",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Format YAML Front-Matter (.md)",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCCCCCC)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                savePetProgressToMarkdown()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6F6F6F)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Export .md", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Text(
                        text = "File tersimpan otomatis di penyimpanan lokal HP:",
                        fontSize = 11.sp,
                        color = Color(0xFFA2A2A2)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = savedFilePath,
                            fontSize = 10.sp,
                            color = Color(0xFF858585),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = "Preview Isi pet_progress.md:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF101010),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, Color(0xFF6F6F6F).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Box(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = filePreviewContent,
                                fontSize = 10.sp,
                                color = Color(0xFFCDCDCD),
                                lineHeight = 14.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }

            // 📖 Bacaan Pet -- file .md baru di vault yang belum/udah dibacain lewat TTS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📖 Bacaan Pet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Taro file .md baru (misal hasil copy-paste artikel) di folder vault utama atau subfolder-nya (kecuali biodata.md/pet-quotes.md/pet_progress.md) -- pet bakal bacain otomatis lewat TTS pas lagi idle, terus balik ngoceh biasa. File yang udah dibaca gak diulang lagi, kecuali diedit ulang.",
                        fontSize = 11.sp,
                        color = Color(0xFFA2A2A2)
                    )

                    val nextUnreadFile = remember(selectedTab) {
                        com.example.data.VaultReadingManager.findNextUnreadFile(context)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (nextUnreadFile != null) {
                                "⏳ Antrian: \"${nextUnreadFile.nameWithoutExtension}\""
                            } else {
                                "✅ Gak ada bacaan baru yang nunggu"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFFCDCDCD),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // 🧠 Sistem Memori Pet (Obsidian Vault biodata.md)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2B2B2B) // Deep Sapphire Blue Memory Theme
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF575757).copy(alpha = 0.4f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(
                                    text = "🧠",
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sistem Memori Pet (Obsidian Vault)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Membaca biodata.md & Memanggil Nama Anda",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCCCCCC)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF949494)
                        ) {
                            Text(
                                text = if (userMemory.isLoadedFromObsidian) "MEMORI AKTIF" else "SIAP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Display current extracted user memory info
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF191919),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👤 Nama Master: ", fontSize = 12.sp, color = Color(0xFFABABAB), fontWeight = FontWeight.Bold)
                                Text(userMemory.userName, fontSize = 13.sp, color = Color(0xFFD2D2D2), fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎨 Hobi: ", fontSize = 12.sp, color = Color(0xFFABABAB), fontWeight = FontWeight.Bold)
                                Text(userMemory.userHobby, fontSize = 13.sp, color = Color(0xFFC3C3C3), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2B2B2B).copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = "💬 Contoh Dialog Pet (ini teks tetap, bukan hasil AI — respons asli Gemini muncul kalau kamu tap langsung pet-nya di layar): \"Semangat kodingnya hari ini, Kak ${userMemory.userName}! 💖\"",
                                    fontSize = 11.sp,
                                    color = Color(0xFFABABAB),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "📌 Tulis 'Nama: [Nama]' dan 'Hobi: [Hobi]' di dalam file biodata.md pada Vault Obsidian Anda. Pet akan membaca data ini secara otomatis!",
                        fontSize = 10.sp,
                        color = Color(0xFFCCCCCC),
                        lineHeight = 14.sp
                    )

                    if (!hasAllFilesAccess) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF4F4F4)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "⚠️ Izin \"Semua Akses File\" belum diberikan. Tanpa ini, pet masih membaca/menulis di folder privat app, bukan Download/Obsidian.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF747474)
                                )
                                Button(
                                    onClick = { com.example.data.VaultPathProvider.requestAllFilesAccess(context) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA5A5A5))
                                ) {
                                    Text("Aktifkan Akses ke Download/Obsidian", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val mem = com.example.data.ObsidianMemoryManager.loadMemoryFromObsidian(context)
                                biodataInputText = mem.rawContent
                                Toast.makeText(context, "🧠 Memori berhasil di-scan dari biodata.md (Nama: ${mem.userName})", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF575757))
                        ) {
                            Text("⚡ Scan biodata.md", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                isEditingBiodata = !isEditingBiodata
                                if (isEditingBiodata) {
                                    val file = com.example.data.ObsidianMemoryManager.getBiodataFile(context)
                                    if (file.exists()) {
                                        biodataInputText = file.readText()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF707070))
                        ) {
                            Text(if (isEditingBiodata) "Tutup Editor" else "✏️ Edit Biodata", fontSize = 11.sp)
                        }
                    }

                    // Direct Editor for biodata.md
                    if (isEditingBiodata) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Isi File biodata.md (Obsidian Vault):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            OutlinedTextField(
                                value = biodataInputText,
                                onValueChange = { biodataInputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFA4A4A4),
                                    unfocusedBorderColor = Color(0xFF575757),
                                    focusedContainerColor = Color(0xFF191919),
                                    unfocusedContainerColor = Color(0xFF191919)
                                )
                            )

                            Button(
                                onClick = {
                                    com.example.data.ObsidianMemoryManager.saveBiodataToFile(context, biodataInputText)
                                    isEditingBiodata = false
                                    Toast.makeText(context, "💾 File biodata.md tersimpan! Memori pet diperbarui.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F7F7F))
                            ) {
                                Text("💾 Simpan Memori Ke biodata.md", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            }
            if (selectedTab == 2) {
            // 🔊 Voice Notification Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2F2F2F)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔊 Suara Notifikasi Masuk",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // 🔇 Master mute -- matiin/nyalain SEMUA suara pet sekaligus
                    // (dialog pet sendiri + notifikasi WA/Telegram), enak dipakai
                    // pas lagi headset-an biar orang di sekitar gak ke-ganggu.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPetVoiceMuted) "🔇 Suara Pet: Mati" else "🔈 Suara Pet: Nyala",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Matiin semua suara pet (ngobrol + notif), tanpa ganggu orang sekitar",
                                fontSize = 11.sp,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                        Switch(
                            checked = !isPetVoiceMuted,
                            onCheckedChange = { checkedOn ->
                                isPetVoiceMuted = !checkedOn
                                com.example.data.PetVoiceSettings.setMuted(context, isPetVoiceMuted)
                            }
                        )
                    }

                    // ⏱️ Interval ngoceh -- seberapa sering pet ganti kalimat template pas idle.
                    // Pilihan TETAP (bukan slider bebas detik demi detik): 30 detik, lalu
                    // loncat per menit. Angka LANGSUNG kepakai tanpa restart app.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️ Interval Ngoceh Pet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val steps = com.example.data.IdleChatterSettings.INTERVAL_STEPS_SEC
                            val currentIdx = chatterIntervalIndex.roundToInt().coerceIn(0, steps.size - 1)
                            val totalSec = steps[currentIdx].toInt()
                            val displayText = if (totalSec < 60) "$totalSec detik" else "${totalSec / 60} menit"
                            Text(
                                text = displayText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80CBC4)
                            )
                        }
                        Slider(
                            value = chatterIntervalIndex,
                            onValueChange = {
                                chatterIntervalIndex = it
                                val steps = com.example.data.IdleChatterSettings.INTERVAL_STEPS_SEC
                                val idx = it.roundToInt().coerceIn(0, steps.size - 1)
                                com.example.data.IdleChatterSettings.setIntervalSeconds(context, steps[idx])
                            },
                            valueRange = 0f..(com.example.data.IdleChatterSettings.INTERVAL_STEPS_SEC.size - 1).toFloat(),
                            steps = com.example.data.IdleChatterSettings.INTERVAL_STEPS_SEC.size - 2
                        )
                        Text(
                            text = "30 detik - 30 menit. Seberapa sering pet ganti kalimat pas lagi idle (gratis, gak manggil AI)",
                            fontSize = 11.sp,
                            color = Color(0xFFB0BEC5)
                        )
                    }

                    // ⏰ Jadwal Mood -- kapan tiap tahap mood pet berubah kalau didiemin.
                    // Angka LANGSUNG kepakai tanpa restart app.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⏰ Jadwal Mood",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Kapan tiap tahap mood pet berubah kalau didiemin. Urutannya: Bosan → Kesal → Marah → Ngantuk → Tidur → Bangun → Hide.",
                            fontSize = 11.sp,
                            color = Color(0xFFB0BEC5)
                        )
                        MoodTimingSliderRow("😐 Bosan", context, com.example.data.MoodTimingSettings::getBosanSec, com.example.data.MoodTimingSettings::setBosanSec)
                        MoodTimingSliderRow("😤 Kesal", context, com.example.data.MoodTimingSettings::getKesalSec, com.example.data.MoodTimingSettings::setKesalSec)
                        MoodTimingSliderRow("😠 Marah", context, com.example.data.MoodTimingSettings::getMarahSec, com.example.data.MoodTimingSettings::setMarahSec)
                        MoodTimingSliderRow("😪 Ngantuk", context, com.example.data.MoodTimingSettings::getNgantukSec, com.example.data.MoodTimingSettings::setNgantukSec)
                        MoodTimingSliderRow("😴 Tidur", context, com.example.data.MoodTimingSettings::getTidurSec, com.example.data.MoodTimingSettings::setTidurSec)
                        MoodTimingSliderRow("🥱 Bangun", context, com.example.data.MoodTimingSettings::getBangunSec, com.example.data.MoodTimingSettings::setBangunSec)
                        MoodTimingSliderRow("🚪 Hide", context, com.example.data.MoodTimingSettings::getHideSec, com.example.data.MoodTimingSettings::setHideSec)
                    }

                    if (!hasNotificationAccess) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF4F4F4)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "⚠️ Izin \"Notification Access\" belum diberikan. Tanpa ini, pet tidak bisa membaca notifikasi WhatsApp/Telegram/dll.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF747474)
                                )
                                Button(
                                    onClick = { com.example.data.NotificationVoiceSettings.requestNotificationAccess(context) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA5A5A5))
                                ) {
                                    Text("Aktifkan Notification Access", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Pilih apa yang dibacakan pet pas ada pesan masuk:",
                        fontSize = 11.sp,
                        color = Color(0xFFBBBBBB)
                    )

                    val voiceOptions = listOf(
                        Triple(com.example.data.VoiceReadMode.OFF, "🔇 Mati", "Nggak bersuara sama sekali"),
                        Triple(com.example.data.VoiceReadMode.SENDER_ONLY, "👤 Nama pengirim saja", "\"Pesan masuk dari [nama]\""),
                        Triple(com.example.data.VoiceReadMode.FULL_MESSAGE, "📖 Baca semua pesan", "Nama pengirim + isi pesan lengkap")
                    )

                    voiceOptions.forEach { (mode, label, desc) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    voiceMode = mode
                                    com.example.data.NotificationVoiceSettings.setMode(context, mode)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (voiceMode == mode) Color(0xFF575757) else Color(0xFF434343)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = voiceMode == mode,
                                    onClick = {
                                        voiceMode = mode
                                        com.example.data.NotificationVoiceSettings.setMode(context, mode)
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color(0xFF9F9F9F))
                                )
                                Column {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(desc, fontSize = 10.sp, color = Color(0xFFD6D6D6))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF555555))

                    // 🔌 Engine TTS -- pilih SECARA EKSPLISIT engine mana yang dipakai
                    // (misal VoxSherpa/NekoSpeak), bukan ngandelin deteksi "default sistem"
                    // yang ternyata gak reliable di beberapa HP (misal HyperOS).
                    Text(
                        text = "🔌 Engine TTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (availableEngines.isEmpty()) {
                        Text(
                            text = "Lagi nyari engine TTS yang terpasang...",
                            fontSize = 10.sp,
                            color = Color(0xFFBBBBBB)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableEngines.forEach { engine ->
                                val (engineLabel, enginePackage) = engine
                                val isSelected = enginePackage == selectedEnginePackage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFF37474F) else Color(0xFF1E1E1E),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            com.example.data.TtsSpeaker.switchToEngine(context, enginePackage)
                                            selectedEnginePackage = enginePackage
                                            availableVoices = emptyList()
                                            selectedVoiceName = null
                                            ttsCoroutineScope.launch {
                                                repeat(10) {
                                                    kotlinx.coroutines.delay(400)
                                                    val voices = com.example.data.TtsSpeaker.getAvailableVoices()
                                                    if (voices.isNotEmpty()) {
                                                        availableVoices = voices
                                                        return@launch
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(engineLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(enginePackage, fontSize = 9.sp, color = Color(0xFFB0BEC5))
                                    }
                                    if (isSelected) {
                                        Text("✓ Aktif", fontSize = 11.sp, color = Color(0xFF80CBC4))
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Pilih engine di sini dulu (misal VoxSherpa/NekoSpeak), baru pilih suaranya di bawah. App bakal INGET pilihan ini walau HP di-restart.",
                            fontSize = 9.sp,
                            color = Color(0xFF80CBC4)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF555555))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🗣️ Pilih Suara (dari yang sudah terpasang di HP)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                com.example.data.TtsSpeaker.reconnectToSystemEngine(context)
                                availableVoices = com.example.data.TtsSpeaker.getAvailableVoices()
                                availableEngines = com.example.data.TtsSpeaker.getInstalledEngines(context)
                                selectedEnginePackage = com.example.data.TtsSpeaker.getSelectedEnginePackage(context)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = "Baru ganti engine TTS di Settings HP (misal ke NekoSpeak/Sherpa-ONNX)? Tekan tombol refresh di atas biar app ini nyambung ulang, gak perlu force-stop app.",
                        fontSize = 9.sp,
                        color = Color(0xFF80CBC4)
                    )

                    if (availableVoices.isEmpty()) {
                        Text(
                            text = "Belum ada suara Bahasa Indonesia terdeteksi di HP ini. Cek Settings > System > Languages > Text-to-speech output > Install voice data.",
                            fontSize = 10.sp,
                            color = Color(0xFFBBBBBB)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableVoices.forEach { voice ->
                                val isSelected = voice.name == selectedVoiceName
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            com.example.data.TtsSpeaker.selectVoice(context, voice)
                                            selectedVoiceName = voice.name
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF5E5E5E) else Color(0xFF434343)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(voice.name, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(voice.locale.displayName, fontSize = 9.sp, color = Color(0xFFD6D6D6))
                                        }
                                        if (isSelected) {
                                            Text("✓ Aktif", fontSize = 10.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { com.example.data.TtsSpeaker.speakPreview() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E5E5E))
                        ) {
                            Text("▶️ Coba Dengar Suara Ini", fontSize = 11.sp)
                        }
                    }
                }
            }

            // 🎚️ Pengaturan Suara TTS Card (pitch, speed, jeda, emoji) -- berlaku ke SEMUA
            // engine (Google TTS, VoxSherpa, NekoSpeak, dll), soalnya ini API standar Android.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎚️ Pengaturan Suara",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Berlaku ke semua engine TTS (bukan cuma satu tertentu)",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )

                    // Pitch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🎵 Tinggi Nada (Pitch)", fontSize = 12.sp, color = Color.White)
                        Text("%.2fx".format(ttsPitch), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4))
                    }
                    Slider(
                        value = ttsPitch,
                        onValueChange = {
                            ttsPitch = it
                            com.example.data.TtsVoiceSettings.setPitch(context, it)
                        },
                        valueRange = com.example.data.TtsVoiceSettings.MIN_PITCH..com.example.data.TtsVoiceSettings.MAX_PITCH
                    )

                    // Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("⏩ Kecepatan Bicara (Speed)", fontSize = 12.sp, color = Color.White)
                        Text("%.2fx".format(ttsSpeed), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4))
                    }
                    Slider(
                        value = ttsSpeed,
                        onValueChange = {
                            ttsSpeed = it
                            com.example.data.TtsVoiceSettings.setSpeed(context, it)
                        },
                        valueRange = com.example.data.TtsVoiceSettings.MIN_SPEED..com.example.data.TtsVoiceSettings.MAX_SPEED
                    )

                    // Jeda antar kalimat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("⏸️ Jeda Antar Kalimat", fontSize = 12.sp, color = Color.White)
                        Text("${ttsPauseMs.toInt()} ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4))
                    }
                    Slider(
                        value = ttsPauseMs,
                        onValueChange = {
                            ttsPauseMs = it
                            com.example.data.TtsVoiceSettings.setPauseMs(context, it)
                        },
                        valueRange = com.example.data.TtsVoiceSettings.MIN_PAUSE_MS..com.example.data.TtsVoiceSettings.MAX_PAUSE_MS
                    )
                    Text(
                        text = "Jeda ditambahin tiap ketemu titik/tanya/seru pas pet ngomong",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )

                    HorizontalDivider(color = Color(0xFF555555))

                    // Toggle jeda di posisi emoji
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("😊 Jeda di Posisi Emoji", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = "Emoji/kaomoji selalu dibuang (gak ada TTS yang bisa bacanya bener), tapi bisa dikasih jeda kecil di posisinya biar 'beat' emosinya masih kerasa",
                                fontSize = 10.sp,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                        Switch(
                            checked = ttsPauseAtEmoji,
                            onCheckedChange = {
                                ttsPauseAtEmoji = it
                                com.example.data.TtsVoiceSettings.setPauseAtEmoji(context, it)
                            }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF555555))

                    // Bahasa kalimat pet -- ganti ini biar cocok sama voice TTS yang dipilih
                    // (voice Inggris baca teks Indonesia atau sebaliknya kedengeran aneh)
                    Text("🌐 Bahasa Kalimat Pet", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = "Samain sama bahasa voice TTS yang dipilih di atas, biar gak kedengeran aneh pas dibaca",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = petQuoteLanguage == "id",
                            onClick = {
                                petQuoteLanguage = "id"
                                com.example.data.TtsVoiceSettings.setLanguage(context, "id")
                            },
                            label = { Text("Indonesia") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = petQuoteLanguage == "en",
                            onClick = {
                                petQuoteLanguage = "en"
                                com.example.data.TtsVoiceSettings.setLanguage(context, "en")
                            },
                            label = { Text("English") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 🔤 Ukuran Teks Bubble Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔤 Ukuran Teks Bubble",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${bubbleFontSize.toInt()} sp",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80CBC4)
                        )
                    }
                    Slider(
                        value = bubbleFontSize,
                        onValueChange = {
                            bubbleFontSize = it
                            com.example.data.BubbleSettings.setFontSize(context, it)
                        },
                        valueRange = com.example.data.BubbleSettings.MIN_FONT_SIZE..com.example.data.BubbleSettings.MAX_FONT_SIZE,
                        steps = (com.example.data.BubbleSettings.MAX_FONT_SIZE - com.example.data.BubbleSettings.MIN_FONT_SIZE).toInt() - 1
                    )
                    Text(
                        text = "Geser buat atur ukuran teks di bubble dialog pet (angka pasti, bukan cuma kecil/sedang/besar). Langsung kepake di overlay.",
                        fontSize = 10.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }
            }

            // 🎨 Warna Bubble Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎨 Warna Bubble",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Warna otomatis ikut mood",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Senang=pink, Bosan=biru, Kesal=merah",
                                fontSize = 10.sp,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                        Switch(
                            checked = useMoodColor,
                            onCheckedChange = { checked ->
                                useMoodColor = checked
                                com.example.data.BubbleStyleSettings.setUseMoodColor(context, checked)
                            }
                        )
                    }

                    if (!useMoodColor) {
                        Text(
                            text = "Warna Background",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0BEC5)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.example.data.BubbleStyleSettings.BG_COLOR_PRESETS.forEach { presetColor ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(presetColor), shape = RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (bubbleBgColor == presetColor) 2.dp else 1.dp,
                                            color = if (bubbleBgColor == presetColor) Color(0xFF80CBC4) else Color(0xFF555555),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            bubbleBgColor = presetColor
                                            com.example.data.BubbleStyleSettings.setBgColor(context, presetColor)
                                        }
                                )
                            }
                        }

                        Text(
                            text = "Warna Teks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0BEC5)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.example.data.BubbleStyleSettings.TEXT_COLOR_PRESETS.forEach { presetColor ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(presetColor), shape = RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (bubbleTextColor == presetColor) 2.dp else 1.dp,
                                            color = if (bubbleTextColor == presetColor) Color(0xFF80CBC4) else Color(0xFF555555),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            bubbleTextColor = presetColor
                                            com.example.data.BubbleStyleSettings.setTextColor(context, presetColor)
                                        }
                                )
                            }
                        }
                    }
                }
            }

            }
            if (selectedTab == 0) {
            // 🎮 Mini-Game Suwit Pet Card (+20 XP & Unlock Costume)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2F2F2F) // Deep Purple Arcade Theme
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF6F6F6F).copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "🎮",
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Mini-Game Suwit Pet (+20 XP)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Menang = +20 XP | Win Streak 3x = Unlock Kostum!",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCCCCCC)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFD2D2D2)
                        ) {
                            Text(
                                text = "🔥 Streak $winStreak/3",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Win streak progress indicator
                    LinearProgressIndicator(
                        progress = { (winStreak / 3f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFD2D2D2),
                        trackColor = Color(0xFF464646)
                    )

                    // Result Display Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2B2B2B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = gameResultText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF5F5F5),
                            modifier = Modifier.padding(12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Interactive Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val choices = listOf("✊ Batu", "✋ Kertas", "✌️ Gunting")

                        choices.forEach { choice ->
                            Button(
                                onClick = {
                                    val petChoice = choices.random()
                                    val result: String

                                    if (choice == petChoice) {
                                        result = "SERI! Kamu: $choice vs Pet: $petChoice 🤝"
                                        gameResultText = "$result\nSama-sama imbang! Coba lagi ya Master!"
                                        com.example.model.PetGameBus.emitWinReward(0, "Sama-sama pilih $choice! KITA SERI! 🤝")
                                    } else if (
                                        (choice == "✊ Batu" && petChoice == "✌️ Gunting") ||
                                        (choice == "✋ Kertas" && petChoice == "✊ Batu") ||
                                        (choice == "✌️ Gunting" && petChoice == "✋ Kertas")
                                    ) {
                                        winStreak++
                                        result = "MENANG! Kamu: $choice vs Pet: $petChoice 🎉 (+20 XP)"

                                        if (winStreak >= 3) {
                                            val unlockedCostume = CostumeManager.unlockChampionCostume()
                                            gameResultText = "$result\n🏆 WIN STREAK 3X! BUKA KUNCI KOSTUM MAHKOTA EMAAS! ✨"
                                            com.example.model.PetGameBus.emitWinReward(
                                                xp = 20,
                                                speechMessage = "🏆 LUAR BIASA! Win Streak 3x! Kostum '${unlockedCostume.name}' telah BUKA KUNCI! ✨",
                                                isWinStreak = true
                                            )
                                            Toast.makeText(
                                                context,
                                                "🏆 BONANZA! Win Streak 3x! Kostum 'Mahkota Emas Juara' Buka Kunci & Dipakai! ✨",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            gameResultText = "$result\nPet: \"Hebat banget Master! +20 XP untukku! 🎉\""
                                            com.example.model.PetGameBus.emitWinReward(
                                                xp = 20,
                                                speechMessage = "Waaah Master menang! $choice ngalahin $petChoice! +20 XP untukku! 🎉"
                                            )
                                            Toast.makeText(context, "🎉 MENANG LAWAN PET! +20 XP diperoleh!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        winStreak = 0
                                        result = "KALAH! Kamu: $choice vs Pet: $petChoice 😜"
                                        gameResultText = "$result\nPet: \"Hehe aku menang! Win Streak reset ke 0. Coba lagi Master~\""
                                        com.example.model.PetGameBus.emitWinReward(
                                            xp = 0,
                                            speechMessage = "Hehe aku pilih $petChoice lawan $choice. Aku menang! Coba lagi ya Master~ 😜"
                                        )
                                        Toast.makeText(context, "Pet Menang! Win streak di-reset.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF565656)
                                )
                            ) {
                                Text(
                                    text = choice,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
            }

        }
    }
}
