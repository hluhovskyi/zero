package com.hluhovskyi.zero.feedback

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

private const val THRESHOLD_MS2 = 13f
private const val WINDOW_MS = 500L
private const val REQUIRED_SAMPLES = 3
private const val DEBOUNCE_MS = 1_500L

internal class ShakeDetector(
    private val sensorManager: SensorManager,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val timestamps = ArrayDeque<Long>()
    private var lastFireMs: Long = 0L

    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        timestamps.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        if (magnitude < THRESHOLD_MS2) return

        val now = System.currentTimeMillis()
        while (timestamps.isNotEmpty() && now - timestamps.first() > WINDOW_MS) {
            timestamps.removeFirst()
        }
        timestamps.addLast(now)

        if (timestamps.size >= REQUIRED_SAMPLES && now - lastFireMs > DEBOUNCE_MS) {
            lastFireMs = now
            timestamps.clear()
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
