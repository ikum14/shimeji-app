package com.example.model

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data packet for sharing Pet state (Level, XP, Speech Text, Emotion) across
 * Main App, Interactive Canvas, and Floating Overlay Service (FlutterOverlayWindow.shareData equivalent).
 */
data class PetSyncData(
    val petLevel: Int = 5,
    val petXp: Int = 75,
    val emotion: String = "Senang",
    val speechMessage: String = "",
    val notificationSender: String? = null,
    val notificationMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object PetDataBus {
    private val _syncFlow = MutableSharedFlow<PetSyncData>(extraBufferCapacity = 64)
    val syncFlow = _syncFlow.asSharedFlow()

    fun shareData(
        level: Int,
        xp: Int,
        emotion: String,
        speechMessage: String,
        sender: String? = null,
        message: String? = null
    ) {
        _syncFlow.tryEmit(
            PetSyncData(
                petLevel = level,
                petXp = xp,
                emotion = emotion,
                speechMessage = speechMessage,
                notificationSender = sender,
                notificationMessage = message,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
