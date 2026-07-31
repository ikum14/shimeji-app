package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Menyambungkan pet ke Gemini API supaya bisa generate dialog dinamis
 * berdasarkan biodata Master (dari Obsidian) + status pet saat ini,
 * bukan cuma kalimat template statis.
 *
 * Pakai Gemini Developer API langsung (generativelanguage.googleapis.com),
 * BUKAN Firebase AI — supaya tidak perlu setup google-services.json/Firebase project.
 * Key diambil dari BuildConfig.GEMINI_API_KEY (asalnya dari file .env, lihat secrets plugin).
 */
object GeminiPetBrain {

    private const val TAG = "GeminiPetBrain"
    private const val MODEL = "gemini-3.5-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** True kalau API key sudah diisi beneran (bukan placeholder dari .env.example). */
    fun isConfigured(): Boolean {
        val key = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Minta Gemini bikin 1 kalimat celotehan pet, sesuai biodata Master & status pet.
     * Selalu return String (fallback pesan error yang enak dibaca), tidak pernah throw ke caller.
     */
    suspend fun generateDialog(
        userName: String,
        userHobby: String,
        petLevel: Int,
        petEmotion: String,
        vaultContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext "Gemini API key belum terpasang, Master~"
        }
        try {
            val prompt = buildPrompt(userName, userHobby, petLevel, petEmotion, vaultContext)
            val requestBodyJson = JSONObject().apply {
                put(
                    "contents", JSONArray().put(
                        JSONObject().put(
                            "parts", JSONArray().put(
                                JSONObject().put("text", prompt)
                            )
                        )
                    )
                )
                put(
                    "generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 60)
                        put("temperature", 0.9)
                    }
                )
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API error ${response.code}: $bodyString")
                    return@withContext "Aduh, otak AI-ku lagi error, Master~ 😵"
                }
                val text = JSONObject(bodyString)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                text?.takeIf { it.isNotBlank() } ?: "Hmm, aku belum tahu mau bilang apa~"
            }
        } catch (e: IOException) {
            Log.e(TAG, "Gagal hubungi Gemini API (jaringan)", e)
            "Koneksi ke otak AI-ku gagal, Master~ 📡"
        } catch (e: Exception) {
            Log.e(TAG, "Error generate dialog", e)
            "Ups, ada yang salah pas aku mikir~ 🤔"
        }
    }

    private fun buildPrompt(
        userName: String,
        userHobby: String,
        petLevel: Int,
        petEmotion: String,
        vaultContext: String
    ): String {
        val contextBlock = if (vaultContext.isNotBlank()) {
            """

            Catatan/pengetahuan tambahan dari vault Obsidian Master (isi file-file yang dia tulis).
            Pakai ini KALAU relevan sama status/mood saat ini, jangan dibacain semua sekaligus:
            ---
            $vaultContext
            ---
            """.trimIndent()
        } else ""

        return """
            Kamu adalah "Chibi Shimeji", pet virtual perempuan yang imut, ceria, dan sedikit manja,
            hidup sebagai karakter overlay di HP Android milik Master-nya.
            Balas HANYA dengan 1 kalimat pendek (maksimal 20 kata), Bahasa Indonesia gaya santai/gemas,
            seolah kamu benar-benar sedang menyapa Master secara langsung. Jangan pakai tanda kutip.

            Data Master: Nama=$userName, Hobi=$userHobby
            Status pet saat ini: Level=$petLevel, Emosi=$petEmotion
            $contextBlock

            Berikan 1 kalimat sapaan/celotehan yang cocok dengan status di atas.
        """.trimIndent()
    }
}
