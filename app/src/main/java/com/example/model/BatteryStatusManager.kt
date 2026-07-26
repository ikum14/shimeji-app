package com.example.model

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryStateInfo(
    val isCharging: Boolean = false,
    val batteryLevel: Int = 100,
    val isLowBattery: Boolean = false
)

/**
 * Android Battery Status Manager for detecting charging & low battery states (<15%)
 */
object BatteryStatusManager {

    private val _batteryState = MutableStateFlow(BatteryStateInfo())
    val batteryState = _batteryState.asStateFlow()

    fun updateBatteryStatus(context: Context): BatteryStateInfo {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, filter)

            if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

                val pct = if (level >= 0 && scale > 0) {
                    (level * 100f / scale.toFloat()).toInt()
                } else {
                    100
                }

                val info = BatteryStateInfo(
                    isCharging = isCharging,
                    batteryLevel = pct,
                    isLowBattery = pct < 15 && !isCharging
                )

                _batteryState.value = info
                return info
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return _batteryState.value
    }

    const val CHARGING_QUOTE = "Asyik, energiku sedang diisi ulang! ⚡🔋"
    const val LOW_BATTERY_QUOTE = "Kak, aku lapar... tolong colokkan chasannya 🪫🥺"
}
