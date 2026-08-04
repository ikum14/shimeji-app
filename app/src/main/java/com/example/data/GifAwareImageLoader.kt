package com.example.data

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * Satu ImageLoader Coil yang dipakai bareng-bareng di seluruh app, udah didaftarin
 * dukungan GIF ANIMASI (bukan cuma nampilin frame pertama doang kayak default Coil
 * tanpa decoder tambahan). Dipakai baik di overlay (PetOverlayService) maupun dashboard
 * (MainDashboardScreen) biar konsisten.
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
        return ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
