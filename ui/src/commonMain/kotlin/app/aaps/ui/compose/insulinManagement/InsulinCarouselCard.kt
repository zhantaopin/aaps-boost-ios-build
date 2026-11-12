package app.aaps.ui.compose.insulinManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.InsulinModel
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.longestScreenSideDp
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * Carousel card displaying an insulin configuration.
 * Shows name, peak/DIA summary, concentration badge, and active indicator.
 */
@Composable
fun InsulinCarouselCard(
    iCfg: ICfg,
    isActive: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val compactCard = longestScreenSideDp() < 400
    val containerColor = when {
        isActive   -> AapsTheme.generalColors.inProgress
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else       -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isActive   -> AapsTheme.generalColors.onInProgress
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compactCard) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Peak / DIA summary. On screen the place says which is which; a screen reader only got
            // the bare numbers ("45 min", "5 h"), so it is told the name of each value as well.
            val peakText = stringResource(CoreUiStrings.format_mins, iCfg.peak)
            val diaText = stringResource(CoreUiStrings.format_hours, iCfg.dia)
            val peakSpoken = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.peak_label), peakText)
            val diaSpoken = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.dia_label), diaText)

            val concentration = ConcentrationType.fromDouble(iCfg.concentration)
            val concentrationSpoken = stringResource(
                InterfacesStrings.confirmation_line,
                stringResource(CoreUiStrings.concentration_label),
                stringResource(concentration.label)
            )
            val badge: @Composable () -> Unit = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (concentration != ConcentrationType.U100)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(concentration.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (concentration != ConcentrationType.U100)
                            MaterialTheme.colorScheme.onError
                        else
                            MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = if (compactCard) 2.dp else 4.dp)
                            .semantics { contentDescription = concentrationSpoken }
                    )
                }
            }

            if (compactCard) {
                // An 86 dp card fits two lines only: the name, then peak, DIA and the concentration
                // badge sharing one line. The curve hint is dropped, the name already tells the insulin.
                Text(
                    text = iCfg.insulinLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = peakText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.semantics { contentDescription = peakSpoken }
                    )
                    Text(
                        text = diaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.semantics { contentDescription = diaSpoken }
                    )
                    badge()
                }
            } else {
                // Insulin name
                Text(
                    text = iCfg.insulinLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = peakText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.semantics { contentDescription = peakSpoken }
                    )
                    Text(
                        text = diaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.semantics { contentDescription = diaSpoken }
                    )
                }

                // The curve, when it is not the oref one that peak and DIA describe.
                if (iCfg.model == InsulinModel.LYUMJEV_PD) {
                    Text(
                        text = stringResource(UiStrings.insulin_lyumjev_pd_nickname),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                badge()

                if (isActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(CoreUiStrings.active).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = AapsTheme.generalColors.activeInsulinText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
