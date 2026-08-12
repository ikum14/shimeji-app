package com.example.data

import android.util.Log
import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Baca item TERBARU dari 1 RSS feed -- GRATIS, gak butuh API key. Bisa dipasang ke feed
 * apa aja (berita, cuaca, blog, dll), Master yang nentuin URL-nya lewat setelan.
 */
object RssFeedReader {
    private const val TAG = "RssFeedReader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Judul + ringkasan singkat item paling atas (biasanya = paling baru) di RSS feed [feedUrl].
     * Return null kalau gagal fetch/parse -- caller wajib nangani null dengan baik. */
    fun getLatestHeadline(feedUrl: String): String? {
        if (feedUrl.isBlank()) return null
        return try {
            val request = Request.Builder().url(feedUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseFirstItem(body)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Gagal ambil RSS feed (kemungkinan lagi offline/URL salah)", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RSS feed", e)
            null
        }
    }

    /** Parser XML ringan (tanpa library tambahan) -- ambil <title> & <description> item pertama. */
    private fun parseFirstItem(xmlBody: String): String? {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlBody))

        var insideItem = false
        var currentTag = ""
        var title: String? = null
        var description: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag.equals("item", ignoreCase = true) || currentTag.equals("entry", ignoreCase = true)) {
                        insideItem = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideItem) {
                        val text = parser.text?.trim()
                        if (!text.isNullOrBlank()) {
                            when {
                                currentTag.equals("title", ignoreCase = true) && title == null -> title = text
                                currentTag.equals("description", ignoreCase = true) && description == null -> description = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true) || parser.name.equals("entry", ignoreCase = true)) {
                        // Item pertama udah kelar dibaca -- selesai, gak usah lanjut ke item berikutnya
                        if (title != null) {
                            val cleanDesc = description?.replace(Regex("<[^>]*>"), "")?.trim() // buang tag HTML kalau ada
                            return if (!cleanDesc.isNullOrBlank()) "$title -- $cleanDesc" else title
                        }
                        insideItem = false
                    }
                }
            }
            event = parser.next()
        }
        return title
    }
}
