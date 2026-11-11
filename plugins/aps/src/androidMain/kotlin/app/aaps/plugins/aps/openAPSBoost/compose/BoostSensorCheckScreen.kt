package app.aaps.plugins.aps.openAPSBoost.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSBoost.BoostStepSensor
import app.aaps.plugins.aps.openAPSBoost.StepService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Read-only sensor self-check for the standalone-watch setup: whether this device has a step and a
 * heart-rate sensor, whether Boost registered its listener, what the permissions say, and how old
 * the newest sample is. Answers "hardware, permission or code?" without a logcat session.
 *
 * Nothing here samples in the background: the snapshot is read once when the screen opens and again
 * on Refresh; only the explicit button duty-cycles the PPG for ten seconds. Results are displayed,
 * never written to the database.
 */
@Composable
fun BoostSensorCheckScreen(
    preferences: Preferences,
    persistenceLayer: PersistenceLayer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<BoostSensorSnapshot?>(null) }
    var sampling by remember { mutableStateOf(false) }
    var sampleDone by remember { mutableStateOf(false) }
    var sampleBpm by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { snapshot = loadSnapshot(context, preferences, persistenceLayer) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.boost_sensor_check_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { scope.launch { snapshot = loadSnapshot(context, preferences, persistenceLayer) } }) {
                Text(stringResource(R.string.boost_sensor_check_refresh), fontSize = 13.sp)
            }
        }

        val snap = snapshot
        if (snap == null) {
            CheckRow(stringResource(R.string.boost_sensor_check_step_sensor), "…")
        } else {
            val yes = stringResource(R.string.boost_sensor_check_yes)
            val no = stringResource(R.string.boost_sensor_check_no)
            val never = stringResource(R.string.boost_sensor_check_never)

            CheckRow(stringResource(R.string.boost_sensor_check_steps_enabled), if (snap.stepsEnabled) yes else no)
            CheckRow(
                stringResource(R.string.boost_sensor_check_step_sensor),
                stringResource(
                    when {
                        snap.hasStepCounter -> R.string.boost_sensor_check_step_kind_counter
                        snap.hasStepDetector -> R.string.boost_sensor_check_step_kind_detector
                        else -> R.string.boost_sensor_check_no
                    }
                )
            )
            CheckRow(stringResource(R.string.boost_sensor_check_other_step_sensors), snap.otherStepSensors ?: no)
            CheckRow(stringResource(R.string.boost_sensor_check_step_registered), if (snap.stepSensorRegistered) yes else no)
            CheckRow(
                stringResource(R.string.boost_sensor_check_step_feed),
                stringResource(if (snap.stepFeedLive) R.string.boost_sensor_check_feed_live else R.string.boost_sensor_check_feed_none)
            )
            CheckRow(
                stringResource(R.string.boost_sensor_check_step_last_event),
                ageText(snap.lastStepEventAgeMin, never)
            )
            CheckRow(stringResource(R.string.boost_sensor_check_steps_15m), snap.steps15Min.toString())
            CheckRow(stringResource(R.string.boost_sensor_check_hr_sensor), if (snap.hasHrSensor) yes else no)
            CheckRow(stringResource(R.string.boost_sensor_check_body_sensors), if (snap.bodySensorsGranted) yes else no)
            CheckRow(
                stringResource(R.string.boost_sensor_check_watch_hr),
                if (snap.watchHrEnabled) stringResource(R.string.boost_sensor_check_watch_hr_on, snap.watchHrIntervalMin) else no
            )
            CheckRow(
                stringResource(R.string.boost_sensor_check_last_hr),
                if (snap.lastHrAgeMin == null || snap.lastHrBpm == null) {
                    stringResource(R.string.boost_sensor_check_none)
                } else {
                    stringResource(
                        R.string.boost_sensor_check_hr_value,
                        snap.lastHrBpm.toInt(),
                        snap.lastHrDevice?.ifBlank { null } ?: ageText(snap.lastHrAgeMin, never)
                    )
                }
            )

            Button(
                enabled = !sampling && snap.hasHrSensor,
                onClick = {
                    sampling = true
                    sampleDone = false
                    sampleBpm = null
                    scope.launch {
                        sampleBpm = sampleHeartRate(context)
                        sampling = false
                        sampleDone = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(if (sampling) R.string.boost_sensor_check_sampling else R.string.boost_sensor_check_sample_hr), fontSize = 13.sp)
            }
            if (sampleDone && !sampling) {
                Text(
                    text = sampleBpm?.let { "$it bpm" } ?: stringResource(R.string.boost_sensor_check_none),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.padding(top = 6.dp)) {
            Text(stringResource(R.string.boost_sensor_check_back), fontSize = 13.sp)
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ageText(ageMin: Long?, never: String): String =
    if (ageMin == null) never else stringResource(R.string.boost_sensor_check_minutes_ago, ageMin)

/** One-shot snapshot: sensor presence, registration state and the newest stored values. No sampling. */
private suspend fun loadSnapshot(
    context: Context,
    preferences: Preferences,
    persistenceLayer: PersistenceLayer
): BoostSensorSnapshot {
    val now = System.currentTimeMillis()
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val hasStepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    val hasStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    // Anything else the vendor exposes with "step" in its name, so a private pedometer source shows up
    // instead of looking like "no hardware at all".
    val otherStepSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        .filter { it.type != Sensor.TYPE_STEP_COUNTER && it.type != Sensor.TYPE_STEP_DETECTOR && it.name.contains("step", ignoreCase = true) }
        .joinToString(", ") { it.name }
        .ifBlank { null }
    val lastHr = persistenceLayer.getHeartRatesFromTime(now - 6 * 60 * 60_000L)
        .filter { it.isValid }
        .maxByOrNull { it.timestamp }
    val lastStepEvent = StepService.lastEventMs
    return BoostSensorSnapshot(
        stepsEnabled = preferences.get(BooleanKey.ApsBoostPhoneStepsEnabled),
        hasStepCounter = hasStepCounter,
        hasStepDetector = hasStepDetector,
        otherStepSensors = otherStepSensors,
        stepSensorRegistered = BoostStepSensor.isRegistered(),
        stepFeedLive = StepService.feedState() == StepService.FeedState.LIVE,
        lastStepEventAgeMin = if (lastStepEvent > 0) (now - lastStepEvent) / 60_000L else null,
        steps15Min = StepService.getRecentStepCount15Min(),
        hasHrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null,
        bodySensorsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED,
        watchHrEnabled = preferences.get(BooleanKey.ApsBoostWatchHrEnabled),
        watchHrIntervalMin = preferences.get(IntKey.ApsBoostWatchHrIntervalMin),
        lastHrAgeMin = lastHr?.let { (now - it.timestamp) / 60_000L },
        lastHrBpm = lastHr?.beatsPerMinute,
        lastHrDevice = lastHr?.device
    )
}

/**
 * Ten seconds of PPG, on demand only: registers the heart-rate sensor, averages the readings and
 * unregisters. Nothing is persisted, so a test cannot pollute the heart-rate table.
 */
private suspend fun sampleHeartRate(context: Context, seconds: Int = 10): Int? = withContext(Dispatchers.Default) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return@withContext null
    val values = mutableListOf<Float>()
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isNotEmpty()) values.add(event.values[0])
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    try {
        delay(seconds * 1000L)
    } finally {
        sensorManager.unregisterListener(listener)
    }
    values.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toInt()
}

data class BoostSensorSnapshot(
    val stepsEnabled: Boolean,
    val hasStepCounter: Boolean,
    val hasStepDetector: Boolean,
    val otherStepSensors: String?,
    val stepSensorRegistered: Boolean,
    val stepFeedLive: Boolean,
    val lastStepEventAgeMin: Long?,
    val steps15Min: Int,
    val hasHrSensor: Boolean,
    val bodySensorsGranted: Boolean,
    val watchHrEnabled: Boolean,
    val watchHrIntervalMin: Int,
    val lastHrAgeMin: Long?,
    val lastHrBpm: Double?,
    val lastHrDevice: String?
)
