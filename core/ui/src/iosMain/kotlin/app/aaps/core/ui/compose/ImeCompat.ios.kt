package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable

/**
 * iOS 没有 Android 11 之前那套 IME 问题：键盘由系统管理，输入框会随键盘平移，
 * 输入法上的完成键也走系统的 return/continue 动作，不会出现“关掉键盘焦点还在”的循环。
 * 因此旧的兜底逻辑在这里一律关闭。
 */
actual val legacyImePlatform: Boolean = false

/** 只在 [legacyImePlatform] 为真时才会被读取；iOS 按需返回常量，不订阅任何平台信号。 */
@Composable
actual fun rememberImeVisible(): () -> Boolean = { false }
