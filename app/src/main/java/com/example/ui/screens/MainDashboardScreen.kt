package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pet Progress & Level Stats
    var petLevel by remember { mutableStateOf(5) }
    var petXp by remember { mutableStateOf(420) }
    val maxXp = 1000
    var totalInteractions by remember { mutableStateOf(38) }
    var happiness by remember { mutableStateOf(95) }
    var energy by remember { mutableStateOf(88) }

    // File Export State for Obsidian
    val targetFile = remember { ObsidianPetExporter.getTargetMarkdownFile(context) }
    var savedFilePath by remember { mutableStateOf(targetFile.absolutePath) }
    var filePreviewContent by remember {
        mutableStateOf(ObsidianPetExporter.readProgressFromFile(context))
    }

    // Function to trigger save to markdown
    fun savePetProgressToMarkdown() {
        val data = PetProgressData(
            petName = "Chibi Girl Shimeji",
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
    var biodataInputText by remember { mutableStateOf("") }

    // Mini-Game Suwit State
    var winStreak by remember { mutableIntStateOf(0) }
    var gameResultText by remember { mutableStateOf("Ayo tanding Suwit lawan Chibi Pet! Menang = +20 XP | Win Streak 3x = Unlock Kostum Baru! 🔥") }

    LaunchedEffect(Unit) {
        CostumeManager.initKarakter(context)
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

    // Re-check permission on resume & handle auto backup on app close/pause
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Physics Settings for Sandbox
    var physicsMode by remember { mutableStateOf(FallPhysicsMode.STAIR_STEP) }
    var stepHeightPx by remember { mutableFloatStateOf(20f) }
    var stepWidthPx by remember { mutableFloatStateOf(14f) }
    var fallSpeedMs by remember { mutableLongStateOf(30L) }

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
            // Permission Alert / Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasOverlayPermission)
                        Color(0xFFE8F5E9)
                    else
                        Color(0xFFFFF3E0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Permission Status",
                        tint = if (hasOverlayPermission) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasOverlayPermission) "Izin Floating Overlay Aktif" else "Membutuhkan Izin Overlay",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (hasOverlayPermission) Color(0xFF1B5E20) else Color(0xFFE65100)
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
                                containerColor = Color(0xFFEF6C00)
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
                                containerColor = Color(0xFFE91E63)
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
                    containerColor = Color(0xFFE8F5E9)
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
                                color = Color(0xFF25D366).copy(alpha = 0.2f),
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
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = "WhatsApp & Telegram Auto Bubble",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
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
                        color = Color(0xFF1B5E20)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
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
                    containerColor = Color(0xFFFFF0F5)
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
                                color = Color(0xFFE91E63).copy(alpha = 0.2f),
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
                                    color = Color(0xFF880E4F)
                                )
                                Text(
                                    text = "Multi-Layer Stack Overlay Structure",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC2185B)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE91E63).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = CostumeManager.getCostumeDisplayName(kostumAktif),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE91E63)
                            )
                        }
                    }

                    Text(
                        text = "Layer dasar: Tubuh Pet (body_default). Layer atas: Pakaian yang berganti secara instan saat gantiKostum(namaKostum) dipanggil!",
                        fontSize = 11.sp,
                        color = Color(0xFF4A148C)
                    )

                    // Add Custom Character from Gallery HP Button
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA))
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
                        color = Color(0xFF880E4F)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listKarakter) { item ->
                            val isSelected = kostumAktif == item.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFFE91E63) else Color.White,
                                shadowElevation = if (isSelected) 6.dp else 2.dp,
                                modifier = Modifier
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFC2185B) else Color.LightGray,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                Button(
                                    onClick = { gantiKostum(item.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFFE91E63) else Color.White
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
                                        Color(0xFFEBF3FA),
                                        Color(0xFFFDEEF4)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        InteractivePetCanvas(
                            fallPhysicsMode = physicsMode,
                            stepHeightPx = stepHeightPx,
                            stepWidthPx = stepWidthPx,
                            fallSpeedMs = fallSpeedMs
                        )
                    }
                }
            }

            // Physics Customization & Stair Fall Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logika Jatuh & Fisika Pet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    // Mode Selection Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = physicsMode == FallPhysicsMode.STAIR_STEP,
                            onClick = { physicsMode = FallPhysicsMode.STAIR_STEP },
                            label = { Text("Turun Tangga (Stair-Fall)") }
                        )
                        FilterChip(
                            selected = physicsMode == FallPhysicsMode.SMOOTH,
                            onClick = { physicsMode = FallPhysicsMode.SMOOTH },
                            label = { Text("Jatuh Halus (Smooth)") }
                        )
                    }

                    // Fall Speed Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kecepatan Jatuh (Interval Ms)", fontSize = 12.sp)
                            Text("${fallSpeedMs}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = fallSpeedMs.toFloat(),
                            onValueChange = { fallSpeedMs = it.toLong() },
                            valueRange = 10f..80f
                        )
                    }

                    // Step Height Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tinggi Langkah Tangga (Step Y)", fontSize = 12.sp)
                            Text("${stepHeightPx.toInt()}px", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = stepHeightPx,
                            onValueChange = { stepHeightPx = it },
                            valueRange = 8f..40f
                        )
                    }

                    // Step Width Swing Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ayunan Langkah Horizontal (Step X)", fontSize = 12.sp)
                            Text("${stepWidthPx.toInt()}px", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = stepWidthPx,
                            onValueChange = { stepWidthPx = it },
                            valueRange = 4f..30f
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
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4081))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Status & Progress Pet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF9C27B0).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Level $petLevel",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF9C27B0)
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
                            Text("$petXp / $maxXp XP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0))
                        }
                        LinearProgressIndicator(
                            progress = { (petXp.toFloat() / maxXp.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Color(0xFF9C27B0)
                        )
                    }

                    // Happiness
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kebahagiaan (Happiness)", fontSize = 12.sp)
                            Text("$happiness%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE91E63))
                        }
                        LinearProgressIndicator(
                            progress = { (happiness / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Color(0xFFE91E63)
                        )
                    }

                    // Energy
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Energi", fontSize = 12.sp)
                            Text("$energy%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                        }
                        LinearProgressIndicator(
                            progress = { (energy / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Color(0xFF2196F3)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Text("Simpan .md", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Obsidian Integration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D1B4E) // Obsidian Purple Dark Accent
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
                                color = Color(0xFF7C4DFF).copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Obsidian Sync",
                                    tint = Color(0xFFB388FF),
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
                                    color = Color(0xFFD1C4E9)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                savePetProgressToMarkdown()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C4DFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Export .md", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Text(
                        text = "File tersimpan otomatis di penyimpanan lokal HP:",
                        fontSize = 11.sp,
                        color = Color(0xFFB388FF)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1F1138),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = savedFilePath,
                            fontSize = 10.sp,
                            color = Color(0xFFE040FB),
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
                        color = Color(0xFF140A26),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Box(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = filePreviewContent,
                                fontSize = 10.sp,
                                color = Color(0xFFE1BEE7),
                                lineHeight = 14.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }

            // 🧠 Sistem Memori Pet (Obsidian Vault biodata.md)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A237E) // Deep Sapphire Blue Memory Theme
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
                                color = Color(0xFF3F51B5).copy(alpha = 0.4f),
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
                                    color = Color(0xFFC5CAE9)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00E676)
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
                        color = Color(0xFF0D1259),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👤 Nama Master: ", fontSize = 12.sp, color = Color(0xFF9FA8DA), fontWeight = FontWeight.Bold)
                                Text(userMemory.userName, fontSize = 13.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎨 Hobi: ", fontSize = 12.sp, color = Color(0xFF9FA8DA), fontWeight = FontWeight.Bold)
                                Text(userMemory.userHobby, fontSize = 13.sp, color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1A237E).copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = "💬 Contoh Dialog Pet: \"Semangat kodingnya hari ini, Kak ${userMemory.userName}! 💖\"",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF80AB),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "📌 Tulis 'Nama: [Nama]' dan 'Hobi: [Hobi]' di dalam file biodata.md pada Vault Obsidian Anda. Pet akan membaca data ini secara otomatis!",
                        fontSize = 10.sp,
                        color = Color(0xFFC5CAE9),
                        lineHeight = 14.sp
                    )

                    if (!com.example.data.VaultPathProvider.hasAllFilesAccess()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF3E0)
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
                                    color = Color(0xFFE65100)
                                )
                                Button(
                                    onClick = { com.example.data.VaultPathProvider.requestAllFilesAccess(context) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
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
                                    focusedBorderColor = Color(0xFF8C9EFF),
                                    unfocusedBorderColor = Color(0xFF3F51B5),
                                    focusedContainerColor = Color(0xFF0D1259),
                                    unfocusedContainerColor = Color(0xFF0D1259)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                            ) {
                                Text("💾 Simpan Memori Ke biodata.md", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // 🎮 Mini-Game Suwit Pet Card (+20 XP & Unlock Costume)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF311B92) // Deep Purple Arcade Theme
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
                                color = Color(0xFF7E57C2).copy(alpha = 0.4f),
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
                                    color = Color(0xFFD1C4E9)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFD54F)
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
                        color = Color(0xFFFFD54F),
                        trackColor = Color(0xFF512DA8)
                    )

                    // Result Display Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1A237E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = gameResultText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFF9C4),
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
                                    containerColor = Color(0xFF673AB7)
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
