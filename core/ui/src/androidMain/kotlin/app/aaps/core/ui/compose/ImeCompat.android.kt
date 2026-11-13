package app.aaps.core.ui.compose

import android.os.Build

actual val legacyImePlatform: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
