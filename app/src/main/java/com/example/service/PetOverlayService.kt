    override fun onDestroy() {
        super.onDestroy()
        // OPTIMIZATION: Cleanup coroutine scope + resources on service destroy
        // Prevents coroutine leaks when service is killed unexpectedly
        idleTimerJob?.cancel()
        behaviorJob?.cancel()
        serviceScope.cancel()
        
        // Cleanup TTS resources
        com.example.data.TtsSpeaker.shutdown()
        
        // Remove overlay windows gracefully
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView)
            }
            if (speechCard != null) {
                windowManager.removeView(speechCard)
            }
            if (chatOverlayView != null) {
                windowManager.removeView(chatOverlayView)
            }
        } catch (e: Exception) {
            // Ignore -- views might already be removed or window manager dead
        }
    }
