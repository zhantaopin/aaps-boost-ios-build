package app.aaps.plugins.aps.openAPSBoost

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Heart-rate source for a standalone Android watch that runs the full app.
 *
 * The watch's own PPG sensor is the only HR source available on Android 8.1: Health Connect does
 * not exist there, and Garmin / AAPS Wear sources need a second device. Sampling is duty-cycled
 * because an open [Sensor.TYPE_HEART_RATE] listener keeps the LED on — a 60 s window every few
 * minutes keeps the 15-minute HR window fed at a fraction of the drain.
 *
 * Writes one aggregated [HR] row per sample so the database holds averages, not every beat. The
 * device tag is the manufacturer + model, which [HrSourceResolver] classifies as a worn source.
 *
 * The loop is started unconditionally at plugin start and reads the switch each iteration, so
 * toggling the preference takes effect without restarting the app. When off, the coroutine only
 * wakes once a minute and touches no sensor.
 */
class WatchHrSource(
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val scope: CoroutineScope
) {

    private var job: Job? = null
    private var permissionWarned = false

    private val sensorManager by lazy {
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val sensor: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) }

    fun start() {
        if (job?.isActive == true) return
        if (sensor == null) {
            aapsLogger.debug(LTag.APS, "Watch heart rate: no heart-rate sensor on this device")
            return
        }
        job = scope.launch {
            while (isActive) {
                if (!preferences.get(BooleanKey.ApsBoostWatchHrEnabled)) {
                    delay(IDLE_POLL_MS)
                    continue
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                    if (!permissionWarned) {
                        aapsLogger.warn(LTag.APS, "BODY_SENSORS permission not granted — watch heart rate source inactive")
                        permissionWarned = true
                    }
                    delay(IDLE_POLL_MS)
                    continue
                }
                permissionWarned = false
                val intervalMin = preferences.get(IntKey.ApsBoostWatchHrIntervalMin).coerceAtLeast(1)
                sampleOnce()
                delay((intervalMin * 60_000L - SAMPLE_MS).coerceAtLeast(IDLE_POLL_MS))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun sampleOnce() {
        val hrSensor = sensor ?: return
        val values = ArrayList<Double>(64)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val bpm = event.values.firstOrNull()?.toDouble() ?: return
                if (bpm in MIN_BPM..MAX_BPM) values.add(bpm)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        try {
            sensorManager.registerListener(listener, hrSensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(Looper.getMainLooper()))
            delay(SAMPLE_MS)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            aapsLogger.warn(LTag.APS, "Watch heart rate: sampling failed (${t.message})")
            return
        } finally {
            try {
                sensorManager.unregisterListener(listener)
            } catch (_: Throwable) {
            }
        }
        if (values.size < MIN_SAMPLES) {
            aapsLogger.debug(LTag.APS, "Watch heart rate: not enough samples (${values.size})")
            return
        }
        val average = values.average()
        try {
            persistenceLayer.insertOrUpdateHeartRates(
                listOf(
                    HR(
                        duration = SAMPLE_MS,
                        timestamp = System.currentTimeMillis(),
                        beatsPerMinute = average,
                        device = deviceName()
                    )
                )
            )
            aapsLogger.debug(LTag.APS, "Watch heart rate: ${average.toInt()} bpm from ${values.size} samples")
        } catch (t: Throwable) {
            aapsLogger.warn(LTag.APS, "Watch heart rate: persist failed (${t.message})")
        }
    }

    private fun deviceName(): String =
        listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim().ifEmpty { "Watch" }

    companion object {
        private const val SAMPLE_MS = 60_000L
        private const val IDLE_POLL_MS = 60_000L
        private const val MIN_SAMPLES = 5
        private const val MIN_BPM = 20.0
        private const val MAX_BPM = 250.0
    }
}
