package com.example.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Ambil 1 fakta acak dari Wikipedia -- GRATIS, gak butuh API key sama sekali (beda dari
 * Gemini). Dipakai buat nyelipin trivia pas pet lagi ngoceh idle, bikin dia keliatan
 * "pinter" tanpa nambah biaya. Endpoint ini bagian dari REST API resmi Wikimedia.
 */
object WikipediaLookup {
    private const val TAG = "WikipediaLookup"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Ambil ringkasan 1 artikel Wikipedia acak. [language] "id" buat Wikipedia Indonesia,
     * "en" buat Wikipedia Inggris -- disamain sama toggle bahasa kalimat pet biar nyambung.
     * Return null kalau gagal (offline, timeout, dll) -- caller wajib nangani null dengan baik.
     */
    fun getRandomFact(language: String): String? {
        val langCode = if (language == "en") "en" else "id"
        val url = "https://$langCode.wikipedia.org/api/rest_v1/page/random/summary"
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val title = json.optString("title", "")
                val extract = json.optString("extract", "")
                if (extract.isBlank()) return null
                if (title.isNotBlank()) "$title: $extract" else extract
            }
        } catch (e: IOException) {
            Log.w(TAG, "Gagal ambil fakta Wikipedia (kemungkinan lagi offline)", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Wikipedia response", e)
            null
        }
    }
}
