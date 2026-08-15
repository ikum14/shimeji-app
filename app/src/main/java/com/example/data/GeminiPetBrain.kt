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
    private const val MODEL = "gemini-flash-lite-latest"
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
        vaultContext: String = "",
        language: String = "id",
        memoryContext: String = "",
        extraInfo: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext "Gemini API key belum terpasang, Master~"
        }
        try {
            val prompt = buildPrompt(userName, userHobby, petLevel, petEmotion, vaultContext, language, memoryContext, extraInfo)
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
                    return@withContext if (response.code == 429) {
                        "Kuota AI-ku abis buat hari ini, Master~ 😴 (coba lagi nanti ya)"
                    } else {
                        "Aduh, otak AI-ku lagi error, Master~ 😵"
                    }
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

    /**
     * Minta Gemini balas PESAN yang beneran diketik Master (chat dua arah) -- beda dari
     * generateDialog() yang cuma generate celotehan sepihak. Balasan boleh lebih panjang
     * (gak dibatesin 20 kata kayak celotehan biasa).
     */
    suspend fun generateChatReply(
        userMessage: String,
        userName: String,
        userHobby: String,
        petLevel: Int,
        petEmotion: String,
        vaultContext: String = "",
        language: String = "id",
        memoryContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext "Gemini API key belum terpasang, Master~"
        }
        try {
            val prompt = buildChatPrompt(userMessage, userName, userHobby, petLevel, petEmotion, vaultContext, language, memoryContext)
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
                        put("maxOutputTokens", 150)
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
                    return@withContext if (response.code == 429) {
                        "Kuota AI-ku abis buat hari ini, Master~ 😴 (coba lagi nanti ya)"
                    } else {
                        "Aduh, otak AI-ku lagi error, Master~ 😵"
                    }
                }
                val text = JSONObject(bodyString)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                text?.takeIf { it.isNotBlank() } ?: "Hmm, aku bingung mau jawab apa~"
            }
        } catch (e: IOException) {
            Log.e(TAG, "Gagal hubungi Gemini API (jaringan)", e)
            "Koneksi ke otak AI-ku gagal, Master~ 📡"
        } catch (e: Exception) {
            Log.e(TAG, "Error generate chat reply", e)
            "Ups, ada yang salah pas aku mikir~ 🤔"
        }
    }

    private fun buildChatPrompt(
        userMessage: String,
        userName: String,
        userHobby: String,
        petLevel: Int,
        petEmotion: String,
        vaultContext: String,
        language: String,
        memoryContext: String
    ): String {
        val contextBlock = if (vaultContext.isNotBlank()) {
            """

            Catatan/pengetahuan tambahan dari vault Obsidian Master:
            ---
            $vaultContext
            ---
            """.trimIndent()
        } else ""

        val memoryBlock = if (memoryContext.isNotBlank()) {
            """

            Riwayat obrolan sebelumnya (biar nyambung, jangan ngulang topik yang sama):
            ---
            $memoryContext
            ---
            """.trimIndent()
        } else ""

        val languageInstruction = if (language == "en") {
            "Balas HANYA dalam Bahasa Inggris (English), gaya santai/casual & gemas."
        } else {
            "Balas HANYA dalam Bahasa Indonesia, gaya santai/gemas."
        }

        return """
            Kamu adalah "Chibi Shimeji", pet virtual perempuan yang imut, ceria, dan sedikit manja,
            hidup sebagai karakter overlay di HP Android milik Master-nya. Master barusan ngirim
            pesan chat ke kamu, balas kayak lagi ngobrol beneran -- boleh 1-3 kalimat pendek,
            gak usah kaku, jangan pakai tanda kutip.
            $languageInstruction

            Data Master: Nama=$userName, Hobi=$userHobby
            Status pet saat ini: Level=$petLevel, Emosi=$petEmotion
            $contextBlock
            $memoryBlock

            Pesan dari Master: "$userMessage"

            Balas pesan itu sebagai Chibi Shimeji.
        """.trimIndent()
    }

    private fun buildPrompt(
        userName: String,
        userHobby: String,
        petLevel: Int,
        petEmotion: String,
        vaultContext: String,
        language: String,
        memoryContext: String,
        extraInfo: String
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

        val memoryBlock = if (memoryContext.isNotBlank()) {
            """

            Riwayat singkat apa yang barusan kamu omongin ke Master (biar kamu inget, jangan
            ngulang topik yang sama persis atau nanya hal yang udah kejawab):
            ---
            $memoryContext
            ---
            """.trimIndent()
        } else ""

        val extraInfoBlock = if (extraInfo.isNotBlank()) {
            """

            Info tambahan yang bisa kamu selipin natural ke kalimat kamu KALAU pas/nyambung
            (gak wajib dipake semua, boleh diabaikan kalau kurang cocok sama mood saat ini):
            ---
            $extraInfo
            ---
            """.trimIndent()
        } else ""

        val languageInstruction = if (language == "en") {
            "Balas HANYA dalam Bahasa Inggris (English), gaya santai/casual & gemas. JANGAN campur Bahasa Indonesia sama sekali, walau data Master atau catatan vault di bawah ini dalam Bahasa Indonesia -- tetap terjemahkan/balas full Inggris."
        } else {
            "Balas HANYA dalam Bahasa Indonesia, gaya santai/gemas. JANGAN campur Bahasa Inggris sama sekali, walau data Master atau catatan vault di bawah ini dalam Bahasa Inggris -- tetap terjemahkan/balas full Indonesia."
        }

        return """
            Kamu adalah "Chibi Shimeji", pet virtual perempuan yang imut, ceria, dan sedikit manja,
            hidup sebagai karakter overlay di HP Android milik Master-nya.
            Balas HANYA dengan 1 kalimat pendek (maksimal 20 kata), seolah kamu benar-benar sedang
            menyapa Master secara langsung. Jangan pakai tanda kutip.
            $languageInstruction

            Data Master: Nama=$userName, Hobi=$userHobby
            Status pet saat ini: Level=$petLevel, Emosi=$petEmotion
            $contextBlock
            $memoryBlock
            $extraInfoBlock

            Berikan 1 kalimat sapaan/celotehan yang cocok dengan status di atas.
        """.trimIndent()
    }
}
