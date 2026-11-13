package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
import dev.zacsweers.metro.Inject

@Inject
class SWInfoText(aapsLogger: AAPSLogger, rh: TextResolver, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textLabel: String? = null
    private var visibilityValidator: (() -> Boolean)? = null
    private var maxHeight: Dp? = null

    override fun label(label: TextRef): SWInfoText {
        this.label = label
        return this
    }


    fun label(newLabel: String): SWInfoText {
        textLabel = newLabel
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWInfoText {
        this.visibilityValidator = visibilityValidator
        return this
    }

    /**
     * Caps the visible height and scrolls the text inside it. Needed on small watches: a long text
     * otherwise pushes the buttons that follow it out of the wizard's viewport, and the user sees
     * only Prev with no way to continue.
     */
    fun maxHeight(maxHeight: Dp): SWInfoText {
        this.maxHeight = maxHeight
        return this
    }

    @Composable
    override fun Compose() {
        if (visibilityValidator?.invoke() == false) return
        val text = textLabel ?: label?.let { stringResource(it) } ?: return
        val maxHeight = maxHeight
        if (maxHeight != null) {
            Text(
                text = text,
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
            )
        } else {
            Text(text = text)
        }
    }
}
