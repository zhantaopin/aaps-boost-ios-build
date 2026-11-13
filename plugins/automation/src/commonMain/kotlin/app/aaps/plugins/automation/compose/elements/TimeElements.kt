package app.aaps.plugins.automation.compose.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * @see PreviewTime
 */
@Composable
fun InputTimeEditor(
    minutesSinceMidnight: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var current by rememberSaveable(minutesSinceMidnight) { mutableIntStateOf(minutesSinceMidnight) }
    val hour = current / 60
    val minute = current % 60
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Text(formatHHmm(hour, minute), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
    if (showPicker) {
        // On a watch this is the compact fill-in keypad (HH / MM), on a phone the dial.
        TimePickerModal(
            onTimeSelected = { h, m ->
                current = h * 60 + m
                onChange(current)
            },
            onDismiss = { showPicker = false },
            initialHour = hour,
            initialMinute = minute
        )
    }
}

/**
 * @see PreviewTimeRange
 */
@Composable
fun InputTimeRangeEditor(
    startMinutes: Int,
    endMinutes: Int,
    onChangeStart: (Int) -> Unit,
    onChangeEnd: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InputTimeEditor(minutesSinceMidnight = startMinutes, onChange = onChangeStart)
        Text("–")
        InputTimeEditor(minutesSinceMidnight = endMinutes, onChange = onChangeEnd)
    }
}

@Composable
fun InputDateTimeEditor(
    timeMillis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    // The default time zone, so what the user sees is their own wall clock. Seconds and nanoseconds
    // are carried through every edit, because setting only the date or only the hour and minute must
    // not quietly zero the rest.
    val zone = TimeZone.currentSystemDefault()
    val local = remember(timeMillis) { Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(zone) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
            Text(
                text = formatDate(local),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
            Text(
                text = formatHHmm(local.hour, local.minute),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
    if (showDate) {
        // Same UTC-midnight contract as the Material3 picker, so the read-back below is unchanged.
        // The initial value is the local day at UTC midnight, otherwise the compact picker could
        // show the previous day for a positive UTC offset.
        DatePickerModal(
            onDateSelected = { millis ->
                millis?.let { sel ->
                    // Read back in the same zone the display uses, so the day the user tapped is the
                    // day that gets stored. Keeps the time of day untouched.
                    val pickedDate = Instant.fromEpochMilliseconds(sel).toLocalDateTime(zone).date
                    onChange(LocalDateTime(pickedDate, local.time).toInstant(zone).toEpochMilliseconds())
                }
            },
            onDismiss = { showDate = false },
            initialDateMillis = local.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
    }
    if (showTime) {
        TimePickerModal(
            onTimeSelected = { hour, minute ->
                val pickedTime = LocalTime(hour, minute, local.second, local.nanosecond)
                onChange(LocalDateTime(local.date, pickedTime).toInstant(zone).toEpochMilliseconds())
            },
            onDismiss = { showTime = false },
            initialHour = local.hour,
            initialMinute = local.minute
        )
    }
}

private fun formatHHmm(hour: Int, minute: Int): String =
    "${pad(hour, 2)}:${pad(minute, 2)}"

// LocalDate already prints ISO yyyy-MM-dd, which is what the old "%04d-%02d-%02d" built by hand.
private fun formatDate(dateTime: LocalDateTime): String = dateTime.date.toString()

// String.format is JVM only, so pad by hand. Same output as the "%02d" it replaces.
private fun pad(value: Int, length: Int): String = value.toString().padStart(length, '0')
