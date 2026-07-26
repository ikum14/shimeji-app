package com.example.model

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PetGameRewardEvent(
    val xpGained: Int = 20,
    val messageText: String = "",
    val isWinStreakBonus: Boolean = false
)

/**
 * Global event bus for Mini-Game rewards (XP, speech bubble updates, costume unlock notifications)
 */
object PetGameBus {
    private val _events = MutableSharedFlow<PetGameRewardEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emitWinReward(xp: Int, speechMessage: String, isWinStreak: Boolean = false) {
        _events.tryEmit(PetGameRewardEvent(xpGained = xp, messageText = speechMessage, isWinStreakBonus = isWinStreak))
    }
}
