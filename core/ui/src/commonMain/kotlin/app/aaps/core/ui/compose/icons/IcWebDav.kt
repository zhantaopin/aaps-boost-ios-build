package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A simple cloud icon for WebDAV.
 *
 * WebDAV has no logo of its own. A cloud is the same mark the app uses for other network storage.
 */
val IcWebDav: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcWebDav",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.5f, 19f)
            curveTo(4.01f, 19f, 2f, 16.99f, 2f, 14.5f)
            curveTo(2f, 12.22f, 3.72f, 10.32f, 5.93f, 10.05f)
            curveTo(6.5f, 6.84f, 9.36f, 4.5f, 12.75f, 4.5f)
            curveTo(16.38f, 4.5f, 19.35f, 7.22f, 19.8f, 10.73f)
            curveTo(21.55f, 11.15f, 23f, 12.83f, 23f, 14.75f)
            curveTo(23f, 17.1f, 21.1f, 19f, 18.75f, 19f)
            close()
        }
    }.build()
}
