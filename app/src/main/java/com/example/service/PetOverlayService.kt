    private fun syncToObsidian() {
        try {
            val data = com.example.data.PetProgressData(
                petName = com.example.data.PetProgressStore.getName(applicationContext),
                level = petLevel,
                currentXp = petXp,
                maxXp = maxXp,
                emotion = petEmotion,
                happinessLevel = if (petEmotion == "Senang") 95 else if (petEmotion == "Bosan") 50 else 25,
                energyLevel = 88,
                positionX = (params?.x ?: 200).toFloat(),
                positionY = (params?.y ?: 300).toFloat(),
                physicsMode = "STAIR_STEP",
                totalInteractions = 40
            )
            
            // OPTIMIZATION: Use debouncer instead of direct file write
            // Batches updates and only writes to disk every 30 seconds instead of on every change
            com.example.data.ObsidianSyncDebouncer.queueSync(applicationContext, data)

            // Share data with main app (FlutterOverlayWindow.shareData equivalent)
            com.example.model.PetDataBus.shareData(
                level = petLevel,
                xp = petXp,
                emotion = petEmotion,
                speechMessage = speechBubbleTextState.value
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
