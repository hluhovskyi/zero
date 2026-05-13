package com.hluhovskyi.zero.feedback

import android.hardware.SensorManager
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.navigateTo
import java.io.Closeable

internal class ShakeFeedbackEntry(
    private val sensorManager: SensorManager,
    private val navigator: Navigator,
) {

    fun start(): Closeable {
        val detector = ShakeDetector(sensorManager) {
            navigator.navigateTo(Destinations.Feedback)
        }
        detector.start()
        return Closeable { detector.stop() }
    }
}
