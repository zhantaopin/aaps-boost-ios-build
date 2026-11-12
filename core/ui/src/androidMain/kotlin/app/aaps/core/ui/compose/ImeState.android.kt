package app.aaps.core.ui.compose

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Android 实现：通过 [ViewTreeObserver.OnGlobalLayoutListener] 监听窗口可用区高度变化，
 * 用 `windowVisibleDisplayFrame.bottom` 与 root view 高度的差值判断软键盘是否可见。
 * 这是 API 27 上最可靠的检测方式——不依赖 Compose 的 WindowInsets.ime（它不订阅变化）。
 */
@Composable
actual fun rememberImeVisible(): () -> Boolean {
    val view = LocalView.current
    val state = remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val root = view.rootView
            if (root.width == 0 || root.height == 0) return@OnGlobalLayoutListener
            val screenHeight = view.resources.displayMetrics.heightPixels
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            // adjustResize 压缩窗口，root.height 变小，差值 = 键盘高度；
            // adjustPan 上移显示区，rect.bottom 变小，差值 = 键盘高度。取两者较大值，任一种都成立。
            val resized = screenHeight - root.height
            val panned = root.height - rect.bottom
            val keypadHeight = maxOf(resized, panned)
            state.value = keypadHeight > screenHeight * 0.15f
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return { state.value }
}