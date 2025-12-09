override fun onScroll(
    e1: MotionEvent?,
    event: MotionEvent,
    distanceX: Float,
    distanceY: Float
): Boolean {
    minSwipeY += distanceY
    minSwipeX += distanceX

    val sWidth = Resources.getSystem().displayMetrics.widthPixels
    val sHeight = Resources.getSystem().displayMetrics.heightPixels

    val border = (10 * Resources.getSystem().displayMetrics.density).toInt()
    if (event.x < border || event.y < border || event.x > sWidth - border || event.y > sHeight - border)
        return false

    // horizontal swipe threshold
    val horizontalThreshold = 150 // px
    val verticalThreshold = 50    // px

    // detect horizontal swipe
    if (abs(minSwipeX) > horizontalThreshold && abs(minSwipeX) > abs(minSwipeY)) {
        if (minSwipeX > 0) {
            // swipe right → rewind 5s
            player.seekTo((player.currentPosition - 5000).coerceAtLeast(0))
        } else {
            // swipe left → forward 5s
            val duration = player.duration
            player.seekTo((player.currentPosition + 5000).coerceAtMost(duration))
        }
        minSwipeX = 0f
        minSwipeY = 0f
        return true
    }

    // detect vertical swipe
    if (abs(minSwipeY) > verticalThreshold && abs(minSwipeY) > abs(minSwipeX)) {
        val isLeftSide = event.x < sWidth / 2
        val increase = minSwipeY < 0 // swipe up → increase

        if (isLeftSide) {
            // brightness
            binding.brightnessIcon.visibility = View.VISIBLE
            binding.volumeIcon.visibility = View.GONE
            val newBrightness = (brightness + if (increase) 1 else -1).coerceIn(0, 15)
            brightness = newBrightness
            binding.brightnessIcon.text = brightness.toString()
            setScreenBrightness(brightness)
        } else {
            // volume
            binding.brightnessIcon.visibility = View.GONE
            binding.volumeIcon.visibility = View.VISIBLE
            val maxVol = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (volume + if (increase) 1 else -1).coerceIn(0, maxVol)
            volume = newVolume
            binding.volumeIcon.text = volume.toString()
            audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }

        minSwipeY = 0f
        minSwipeX = 0f
        return true
    }

    return false
}
