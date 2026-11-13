package app.aaps.ui.compose.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.overview.BoostV5Brake
import app.aaps.core.interfaces.overview.BoostV5State
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Boost V5 meal-hypothesis state machine, as one compact strip on the generic overview.
 *
 * ## Why it lives in commonMain
 *
 * The engine stamps its V5 decision onto seven `RT` fields every cycle (`boostV5_state`,
 * `boostV5_score`, …), which ride the existing NS devicestatus upload and are decoded back into `RT`
 * on any client. So a follower already *has* the state machine in memory - it just had nowhere to
 * show it, because the Android Boost V2 screen that renders the same values is androidMain-only.
 *
 * The [BoostV5State] enum is in commonMain already; [BoostV5Brake] too. Only the colour palette of
 * the Android screen ([app.aaps.ui.compose.overview.boost.BoostV2Colors]) is not, so the four
 * surface/text colours are repeated here - the strip is deliberately always-dark, like the Boost
 * card, and a light theme that followed the system would make it read as a different component.
 *
 * ## What it does not show
 *
 * Only the headline numbers. No history, no per-cycle list, no dose provenance - this is a glance
 * at "which state is the engine in and what is holding it back", not a replacement for the full V5
 * dialog. Nothing here is computed: every value is read straight off `RT`.
 */
data class BoostV5StripData(
    val state: BoostV5State,
    val actionMult: Double,
    val ageCycles: Int,
    val dose: Double,
    val budget: Double,
    val score: Double,
    val brakes: List<BoostV5Brake>
) {

    companion object {

        /**
         * The strip for [rt], or null when V5 is not the engine that decided this cycle.
         *
         * `boostV5_active` is stamped by the engine only when V5 was the *active* doser, not a shadow
         * run. Rendering the fields without it would show last cycle's decision as if it were current
         * after the user switched to AMA or SMB, which is worse than an empty screen.
         */
        fun fromRt(rt: RT?): BoostV5StripData? {
            if (rt?.boostV5_active != true) return null
            return BoostV5StripData(
                state = BoostV5State.fromName(rt.boostV5_state),
                actionMult = rt.boostV5_actionMult ?: 1.0,
                ageCycles = rt.boostV5_age ?: 0,
                dose = rt.boostV5_finalDose ?: 0.0,
                budget = rt.boostV5_budget ?: 0.0,
                score = rt.boostV5_score ?: 0.0,
                brakes = parseGateReductions(rt.boostV5_gateReduction)
            )
        }
    }
}

/** Colours of the always-dark card. Same values as the Android Boost V2 palette. */
private val stripBackground = Color(0xFF10121A)
private val stripStroke = Color(0xFF1A1D28)
private val stripText = Color(0xFFE8EAF0)
private val stripTextSecondary = Color(0xFFAAAAAA)
private val stripBrakeHard = Color(0xFFFF5252)
private val stripBrakeSoft = Color(0xFFFB923C)

/**
 * State, multiplier, age, dose, budget, meal score and the brakes that held the dose back.
 *
 * Every text is plain ASCII apart from the two Chinese/unit literals, because the values are engine
 * identifiers (`min_guard_bg`) that must stay searchable against the script log.
 */
@Composable
fun BoostV5Strip(data: BoostV5StripData, modifier: Modifier = Modifier) {
    val stateColor = Color(data.state.colorHex)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(stripBackground)
            .border(1.dp, stripStroke, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${data.state.zhLabel}（${data.state.name}）",
                color = stateColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "dose ${fixed(data.dose, 2)}U · budget ${fixed(data.budget, 2)}U",
                color = stripTextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.End
            )
        }

        Text(
            text = "×${fixed(data.actionMult, 1)} · 第 ${data.ageCycles} 轮",
            color = stripTextSecondary,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(top = 3.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "评分",
                color = stripText,
                fontSize = 10.5.sp
            )
            LinearProgressIndicator(
                progress = { data.score.toFloat().coerceIn(0f, 1f) },
                color = stateColor,
                trackColor = stripStroke,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(6.dp)
            )
            Text(
                text = fixed(data.score, 2),
                color = stripText,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        if (data.brakes.isNotEmpty()) {
            val hasHard = data.brakes.any { it.isHard }
            Text(
                text = data.brakes.joinToString("   ·   ") { brakeText(it) },
                color = if (hasHard) stripBrakeHard else stripBrakeSoft,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** `⛔ min_guard_bg` for a hard gate, `iob 0.62` for a soft one — the same shape the Android screen uses. */
private fun brakeText(brake: BoostV5Brake): String =
    if (brake.isHard) "⛔ ${brake.label}"
    else "${brake.label} ${fixed(brake.factor, 2)}"

/**
 * `boostV5_gateReduction` is a compact comma list: `HARD:name` for a gate that zeroed the dose,
 * `name:factor` for a soft brake that scaled it, and a bare `maxIOB` / `spike` for the two historic
 * hard gates. Same grammar and same ordering as the Android provider's parser (hard first, then the
 * strongest soft brake); kept as a copy rather than shared because that one is androidMain.
 */
internal fun parseGateReductions(raw: String?): List<BoostV5Brake> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text.equals("none", ignoreCase = true)) return emptyList()
    val brakes = mutableListOf<BoostV5Brake>()
    for (tokenRaw in text.split(',')) {
        val token = tokenRaw.trim()
        if (token.isEmpty() || token.equals("none", ignoreCase = true)) continue
        when {
            token.startsWith("HARD:", ignoreCase = true) ->
                brakes += BoostV5Brake(token.substringAfter(':').trim(), 0.0, isHard = true)

            token.equals("maxIOB", ignoreCase = true) ->
                brakes += BoostV5Brake("maxIOB", 0.0, isHard = true)

            token.equals("spike", ignoreCase = true) ->
                brakes += BoostV5Brake("spike", 0.0, isHard = true)

            token.contains(':') -> {
                val name = token.substringBefore(':').trim()
                val factor = token.substringAfter(':').trim().toDoubleOrNull() ?: continue
                if (factor < 0.999) brakes += BoostV5Brake(name, factor, isHard = false)
            }
        }
    }
    return brakes.sortedWith(compareByDescending<BoostV5Brake> { it.isHard }.thenBy { it.factor })
}

/**
 * `0.3` / `0.00` with a fixed number of decimals.
 *
 * commonMain has no `String.format`, and the strip shows three different precisions side by side, so
 * rounding to a scaled integer is the whole job. Values are non-negative (doses, budgets, a 0..1
 * score), which is why the sign is only reattached for safety rather than handled.
 */
private fun fixed(value: Double, decimals: Int): String {
    val factor = if (decimals == 1) 10 else 100
    val scaled = (value * factor).roundToInt()
    val whole = scaled / factor
    val fraction = abs(scaled % factor)
    val fractionText = if (decimals == 1) fraction.toString() else fraction.toString().padStart(2, '0')
    return "$whole.$fractionText"
}
