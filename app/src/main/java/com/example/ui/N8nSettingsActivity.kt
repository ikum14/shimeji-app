package com.example.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.N8nMonitor
import com.example.data.N8nSettings
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class N8nSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        N8nMonitor.start(applicationContext)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    N8nSettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun N8nSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(N8nSettings.getBaseUrl(context)) }
    var key by remember { mutableStateOf(N8nSettings.getApiKey(context)) }
    var interval by remember { mutableStateOf(N8nSettings.getIntervalSec(context)) }
    var enabled by remember { mutableStateOf(N8nSettings.isEnabled(context)) }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Pantau n8n", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pet nge-poll API n8n dan ngabarin kalau ada workflow gagal atau n8n mati.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pemantauan aktif")
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Alamat n8n") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API key n8n") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Cek tiap:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            N8nSettings.INTERVAL_OPTIONS_SEC.forEach { sec ->
                val label = "${sec / 60} menit"
                if (interval == sec) {
                    Button(onClick = { interval = sec }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { interval = sec }) { Text(label) }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                N8nSettings.save(context, url, key, interval, enabled)
                N8nMonitor.start(context.applicationContext)
                status = "Tersimpan ✅"
            }
        ) { Text("Simpan") }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                status = "Mengetes..."
                scope.launch {
                    status = N8nMonitor.testConnection(url.trim().trimEnd('/'), key.trim())
                }
            }
        ) { Text("Tes koneksi") }

        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
