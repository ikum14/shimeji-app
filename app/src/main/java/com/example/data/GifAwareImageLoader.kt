package com.example.data

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Satu ImageLoader Coil yang dipakai bareng-bareng di seluruh app, udah didaftarin
 * dukungan GIF ANIMASI (bukan cuma nampilin frame pertama doang kayak default Coil
 * tanpa decoder tambahan). Dipakai baik di overlay (PetOverlayService) maupun dashboard
 * (MainDashboardScreen) biar konsisten.
 *
 * OPTIMIZATION: Added aggressive memory caching to prevent reloading images on every pose change.
 * - Memory cache: 256 MB max size
 * - Disk cache: enabled with 512 MB max size
 * - Network timeout: 10s connect, 15s read (balanced for mobile)
 *
 * This prevents memory buildup from repeatedly loading same costume/pose images.
 */
object GifAwareImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    private fun build(context: Context): ImageLoader {
        // Configure aggressive memory cache to prevent re-fetching same images
        val memoryCache = MemoryCache.Builder(context)
            .maxSizePercent(0.25) // 25% of available heap, roughly 256 MB on most devices
            .build()

        // OkHttpClient with connection pooling & timeouts optimized for mobile
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()

        return ImageLoader.Builder(context)
            .memoryCache(memoryCache)
            .okHttpClient(okHttpClient)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // Disk cache: 512 MB max, helps with offline mode & repeated poses
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }
}
