package com.example.data

import android.content.Context
import android.util.Log
import com.example.service.IncomingPetNotification
import com.example.service.NotificationBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Setelan pemantau n8n (disimpan di SharedPreferences, private ke app). */
object N8nSettings {
    private const val PREFS = "pet_n8n_monitor_prefs"
    const val DEFAULT_URL = "http://127.0.0.1:5678"
    const val DEFAULT_INTERVAL_SEC = 120
    val INTERVAL_OPTIONS_SEC = listOf(60, 120, 300)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBaseUrl(c: Context): String =
        (prefs(c).getString("base_url", DEFAULT_URL) ?: DEFAULT_URL).trim().trimEnd('/')
    fun getApiKey(c: Context): String = (prefs(c).getString("api_key", "") ?: "").trim()
    fun getIntervalSec(c: Context): Int = prefs(c).getInt("interval_sec", DEFAULT_INTERVAL_SEC)
    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean("enabled", true)
    fun isConfigured(c: Context): Boolean = getApiKey(c).isNotEmpty()
    fun getLastSeenId(c: Context): Long = prefs(c).getLong("last_seen_id", -1L)
    fun setLastSeenId(c: Context, id: Long) { prefs(c).edit().putLong("last_seen_id", id).apply() }

    fun save(c: Context, url: String, key: String, intervalSec: Int, enabled: Boolean) {
        prefs(c).edit()
            .putString("base_url", url.trim().trimEnd('/'))
            .putString("api_key", key.trim())
            .putInt("interval_sec", intervalSec)
            .putBoolean("enabled", enabled)
            .apply()
    }
}

/** Nge-poll API n8n, lalu ngasih tau pet lewat NotificationBus. */
object N8nMonitor {
    private const val TAG = "N8nMonitor"

    private data class Exec(val id: Long, val workflowId: String)

    private sealed class Fetch {
        class Ok(val items: List<Exec>) : Fetch()
        object AuthFailed : Fetch()
        object Unreachable : Fetch()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var job: Job? = null
    private val workflowNames = HashMap<String, String>()
    private var failStreak = 0
    private var downAlerted = false
    private var authAlerted = false

    fun start(context: Context) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                if (N8nSettings.isEnabled(app) && N8nSettings.isConfigured(app)) {
                    try {
                        pollOnce(app)
                    } catch (e: Exception) {
                        Log.w(TAG, "poll gagal", e)
                    }
                }
                delay(N8nSettings.getIntervalSec(app) * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun testConnection(baseUrl: String, apiKey: String): String = withContext(Dispatchers.IO) {
        when (val r = fetchErrors(baseUrl, apiKey)) {
            is Fetch.Ok -> "Nyambung ✅ (${r.items.size} eksekusi gagal terbaru kebaca)"
            Fetch.AuthFailed -> "API key ditolak ❌ (cek key dan scope-nya)"
            Fetch.Unreachable -> "Nggak bisa nyambung ❌ (URL salah atau n8n lagi mati)"
        }
    }

    private fun pollOnce(ctx: Context) {
        val base = N8nSettings.getBaseUrl(ctx)
        val key = N8nSettings.getApiKey(ctx)
        when (val r = fetchErrors(base, key)) {
            is Fetch.Ok -> {
                if (downAlerted) emit("n8n hidup lagi ✅")
                failStreak = 0
                downAlerted = false
                authAlerted = false
                handleExecutions(ctx, base, key, r.items)
            }
            Fetch.AuthFailed -> {
                if (!authAlerted) {
                    authAlerted = true
                    emit("API key n8n ditolak, cek setelan")
                }
            }
            Fetch.Unreachable -> {
                failStreak++
                if (failStreak >= 2 && !downAlerted) {
                    downAlerted = true
                    emit("n8n nggak bisa dihubungi ⚠️")
                }
            }
        }
    }

    private fun handleExecutions(ctx: Context, base: String, key: String, items: List<Exec>) {
        val maxId = items.maxOfOrNull { it.id } ?: return
        val last = N8nSettings.getLastSeenId(ctx)
        if (last == -1L) {
            // Pertama kali jalan: catat error lama sebagai patokan, jangan diumumin
            N8nSettings.setLastSeenId(ctx, maxId)
            return
        }
        val fresh = items.filter { it.id > last }
        if (fresh.isEmpty()) return
        N8nSettings.setLastSeenId(ctx, maxId)
        val names = fresh.map { it.workflowId }.distinct().map { workflowName(base, key, it) }
        val text = if (fresh.size == 1) "${names[0]} gagal"
        else "${fresh.size} eksekusi gagal: ${names.joinToString(", ")}"
        emit(text)
    }

    private fun fetchErrors(base: String, key: String): Fetch {
        return try {
            val req = Request.Builder()
                .url("$base/api/v1/executions?status=error&limit=10")
                .header("X-N8N-API-KEY", key)
                .build()
            val result: Fetch = client.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) return@use Fetch.AuthFailed
                if (!resp.isSuccessful) return@use Fetch.Unreachable
                val body = resp.body?.string() ?: return@use Fetch.Unreachable
                val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val items = ArrayList<Exec>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", -1L)
                    if (id >= 0) items.add(Exec(id, o.optString("workflowId", "")))
                }
                Fetch.Ok(items)
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "n8n tidak terjangkau", e)
            Fetch.Unreachable
        }
    }

    private fun workflowName(base: String, key: String, id: String): String {
        if (id.isBlank()) return "workflow"
        workflowNames[id]?.let { return it }
        return try {
            val req = Request.Builder()
                .url("$base/api/v1/workflows/$id")
                .header("X-N8N-API-KEY", key)
                .build()
            val name: String? = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else JSONObject(resp.body?.string() ?: "{}").optString("name", "").ifBlank { null }
            }
            if (name != null) {
                workflowNames[id] = name
                name
            } else "workflow"
        } catch (e: Exception) {
            "workflow"
        }
    }

    private fun emit(text: String) {
        NotificationBus.emitNotification(
            IncomingPetNotification(
                appName = "n8n",
                packageName = "n8n.monitor",
                senderName = "Pantau",
                messageText = text
            )
        )
    }
}
