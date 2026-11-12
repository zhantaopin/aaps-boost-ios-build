package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Android 11 以下没有可靠的 IME insets，也没有统一的 Done 动作，需要额外兜底。 */
expect val legacyImePlatform: Boolean

/** 返回一个可订阅的键盘可见性读取函数；androidMain 用 View 布局监听实现。 */
@Composable
expect fun rememberImeVisible(): () -> Boolean

/**
 * 旧系统上输入法自带的 ✓ 只关闭键盘、不发 Done，焦点仍在输入框，系统会立刻把键盘弹回来。
 * 这里在“键盘曾经出现又消失、且输入框仍有焦点”时触发 [onImeClosedOnce]，由调用方提交并放弃焦点。
 * Android 11 起该信号由系统 Done 动作覆盖，直接返回，不改变现有行为。
 */
@Composable
fun LegacyImeCloseEffect(isFocused: Boolean, onImeClosedOnce: () -> Unit) {
    if (!legacyImePlatform) return
    val isImeVisible = rememberImeVisible()
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isImeVisible(), isFocused) {
        if (isImeVisible()) {
            wasVisible = true
        } else if (wasVisible && isFocused) {
            wasVisible = false
            onImeClosedOnce()
        }
    }
}