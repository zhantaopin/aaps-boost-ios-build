package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.CoreUiStrings

/**
 * In-app numeric keypad, used on old Android (below 11) instead of the system IME. Some watch
 * ROMs ship an IME whose check mark only hides the keyboard without sending a Done action, and
 * whose window does not report the keyboard height, so the app can neither receive the
 * confirmation nor detect that the keyboard went away — the keyboard keeps bouncing back. This
 * keypad is drawn by the app, so its ✓ is always in the app's own hands.
 *
 * The card wraps its content and stays centred, so the page behind it remains visible; it must
 * never be stretched to the available height. On a 320 dp high screen the pad is ~306 dp, which
 * leaves the action row fully on screen.
 *
 * [allowDecimal] and [allowNegative] adapt the symbol keys to the field: an integer field
 * (age, grams) gets no decimal point, a range that cannot go negative gets no ±.
 *
 * The title is drawn on the same line as the value (left, one line, ellipsised): a separate title
 * line costs ~44 dp and pushed the action row off a small screen.
 *
 * Confirm passes the raw text; the caller's existing validation coerces and range-checks it.
 */
@Composable
fun NumericKeypadDialog(
    title: String?,
    initialText: String,
    allowDecimal: Boolean,
    allowNegative: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    fun appendDigit(digit: String) {
        if (text.length >= 10) return
        text = when (text) {
            // Retyping over a lone zero: "0" + "5" is "5", not "05".
            "0"  -> digit
            "-0" -> "-$digit"
            "-"  -> "-$digit"
            else -> text + digit
        }
    }

    fun appendDot() {
        if (!allowDecimal || text.contains('.')) return
        text = when (text) {
            ""  -> "0."
            "-" -> "-0."
            else -> text + "."
        }
    }

    fun toggleSign() {
        if (!allowNegative) return
        text = when {
            text.startsWith("-") -> text.removePrefix("-")
            text.isEmpty() || text == "0" -> "-"
            else -> "-$text"
        }
    }

    fun backspace() {
        if (text.isNotEmpty()) text = text.dropLast(1)
    }

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
            Column(Modifier.padding(8.dp)) {
                // Title and value share one row: two separate lines cost ~44 dp, which pushed the
                // action row off the bottom of a small screen. A long label must not wrap, so it
                // is capped to one line and ellipsised; the value stays right-aligned.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = text.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3")).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { digit ->
                            KeypadKey(label = digit, onClick = { appendDigit(digit) }, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Symbol row: ± and . appear only when the field can use them; otherwise the slot
                // stays empty so the key positions never shift between fields.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (allowNegative) {
                        KeypadKey(label = "±", onClick = ::toggleSign, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f).height(48.dp))
                    }
                    KeypadKey(label = "0", onClick = { appendDigit("0") }, modifier = Modifier.weight(1f))
                    if (allowDecimal) {
                        KeypadKey(label = ".", onClick = ::appendDot, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f).height(48.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(stringResource(CoreUiStrings.cancel))
                    }
                    KeypadKey(
                        label = "⌫",
                        onClick = ::backspace,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { onConfirm(text) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(stringResource(CoreUiStrings.ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp)
    ) {
        Text(label, style = style)
    }
}
