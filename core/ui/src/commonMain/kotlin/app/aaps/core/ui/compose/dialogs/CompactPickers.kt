package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.NumericKeypadDialog
import app.aaps.core.ui.compose.stringResource
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Material3 date and time pickers are about 360 dp tall: on a 300-320 dp watch the calendar
 * loses its last week and the dial is squashed into an oval. These compact versions replace them
 * below [COMPACT_PICKER_MAX_SIDE_DP] with tappable number fields that open the in-app keypad.
 */
internal const val COMPACT_PICKER_MAX_SIDE_DP = 400

/**
 * One tappable number box of [CompactDatePickerDialog] / [CompactTimePickerDialog].
 *
 * The value is not editable in place: tapping opens [NumericKeypadDialog], the same keypad the
 * rest of the watch UI uses, so the system IME never appears.
 */
@Composable
private fun NumberBox(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** Two-digit padding for the date and time read-out. */
private fun Int.padded(): String = toString().padStart(2, '0')

private fun LocalDate.lastDay(): Int = plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth

/**
 * Compact date picker for small screens: year / month / day boxes that open the in-app keypad.
 *
 * Returns the same value as [DatePickerModal]: midnight UTC of the picked date.
 */
@Composable
internal fun CompactDatePickerDialog(
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
    initialDateMillis: Long?
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.UTC).date }
    val initialDate = remember(initialDateMillis) {
        initialDateMillis
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
            ?: today
    }
    var year by remember { mutableStateOf(initialDate.year) }
    var month by remember { mutableStateOf(initialDate.monthNumber) }
    var day by remember { mutableStateOf(initialDate.dayOfMonth) }
    var editing by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "$year-${month.padded()}-${day.padded()}",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberBox(
                        value = year.toString(),
                        label = "YYYY",
                        onClick = { editing = 1 },
                        modifier = Modifier.weight(1.4f)
                    )
                    NumberBox(
                        value = month.padded(),
                        label = "MM",
                        onClick = { editing = 2 },
                        modifier = Modifier.weight(1f)
                    )
                    NumberBox(
                        value = day.padded(),
                        label = "DD",
                        onClick = { editing = 3 },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(CoreUiStrings.cancel))
                    }
                    Button(
                        onClick = {
                            val safeMonth = month.coerceIn(1, 12)
                            val safeDay = day.coerceIn(1, LocalDate(year, safeMonth, 1).lastDay())
                            onDateSelected(
                                LocalDate(year, safeMonth, safeDay).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                            )
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(CoreUiStrings.ok))
                    }
                }
            }
        }
    }

    if (editing != 0) {
        val title = when (editing) {
            1    -> "YYYY"
            2    -> "MM"
            else -> "DD"
        }
        NumericKeypadDialog(
            title = title,
            initialText = when (editing) {
                1    -> year.toString()
                2    -> month.padded()
                else -> day.padded()
            },
            allowDecimal = false,
            allowNegative = false,
            onConfirm = { text ->
                val entered = text.toIntOrNull()
                if (entered != null) {
                    when (editing) {
                        1    -> year = entered.coerceIn(1900, 2200)
                        2    -> {
                            month = entered.coerceIn(1, 12)
                            day = day.coerceIn(1, LocalDate(year, month, 1).lastDay())
                        }
                        else -> day = entered.coerceIn(1, LocalDate(year, month, 1).lastDay())
                    }
                }
                editing = 0
            },
            onDismiss = { editing = 0 }
        )
    }
}

/**
 * Compact hour/minute picker for small screens; same contract as [TimePickerModal].
 */
@Composable
internal fun CompactTimePickerDialog(
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    initialHour: Int,
    initialMinute: Int
) {
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }
    var editing by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "${hour.padded()}:${minute.padded()}",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberBox(
                        value = hour.padded(),
                        label = "HH",
                        onClick = { editing = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    NumberBox(
                        value = minute.padded(),
                        label = "MM",
                        onClick = { editing = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(CoreUiStrings.cancel))
                    }
                    Button(
                        onClick = {
                            onTimeSelected(hour, minute)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(CoreUiStrings.ok))
                    }
                }
            }
        }
    }

    if (editing != 0) {
        NumericKeypadDialog(
            title = if (editing == 1) "HH" else "MM",
            initialText = if (editing == 1) hour.padded() else minute.padded(),
            allowDecimal = false,
            allowNegative = false,
            onConfirm = { text ->
                text.toIntOrNull()?.let { entered ->
                    if (editing == 1) hour = entered.coerceIn(0, 23) else minute = entered.coerceIn(0, 59)
                }
                editing = 0
            },
            onDismiss = { editing = 0 }
        )
    }
}

