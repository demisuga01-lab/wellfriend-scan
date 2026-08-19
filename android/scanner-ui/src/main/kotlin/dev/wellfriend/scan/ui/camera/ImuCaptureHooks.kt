package dev.wellfriend.scan.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

data class ImuMotionSample(val timestampNanos: Long, val x: Float, val y: Float, val z: Float)

/**
 * Optional future input for MP3 temporal readiness. It records sensor samples only; it does not
 * make capture decisions or substitute for the perception engine's temporal state.
 */
class ImuCaptureHooks(context: Context, private val onMotion: (ImuMotionSample) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun start() { accelerometer?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { manager?.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) {
            onMotion(ImuMotionSample(event.timestamp, event.values[0], event.values[1], event.values[2]))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
