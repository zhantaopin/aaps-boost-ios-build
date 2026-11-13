package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable

/**
 * Desktop windows have no IME overlay to dismiss: the keyboard is the physical one, and Compose
 * Desktop does not report insets for it. The legacy fallback is therefore never used here.
 */
actual val legacyImePlatform: Boolean = false

/** Only read when [legacyImePlatform] is true; a constant costs nothing on desktop. */
@Composable
actual fun rememberImeVisible(): () -> Boolean = { false }
