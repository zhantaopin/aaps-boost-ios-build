# API 门控扫描报告

目标：确认 `minSdk 27` 下不存在「编译通过但运行时抛 `NoSuchMethodError` / `NoClassDefFoundError`」的路径。

## 方法

### A. 方法调用门控

扫描所有 `Build.VERSION.SDK_INT` 分支中对高版本 API 的调用，确认调用点位于对应版本判断之内。

### B. 高版本异常类

扫描 `catch` 子句引用的、API 27 不存在的异常类（类加载期即失败）。

### C. 高版本类引用

用 API 27 的 `android.jar` 导出类清单，与代码中 `import android.*` 求差集，
逐一确认差集中的类是否被版本门控保护。

## 结果

| 检查 | 结果 |
|---|---|
| A 方法调用 | 0 命中 |
| B catch 异常类 | 0 命中 |
| C import 类差集 | 2 处，均已门控 |

### C 类差集明细

| 类 | 引入版本 | 使用位置 | 门控 |
|---|---|---|---|
| `android.app.LocaleManager` | 33 | 语言设置 | `SDK_INT >= TIRAMISU`，否则走旧 Locale API |
| `android.bluetooth.BluetoothStatusCodes` | 31 | 蓝牙连接结果 | `SDK_INT >= TIRAMISU` |

### 其他清单项

- `Context.RECEIVER_EXPORTED`（API 33）：全部通过 `ContextCompat.registerReceiver` 或 TIRAMISU 分支
- `android:foregroundServiceType`（API 29 属性）：低版本忽略，不影响安装与启动
- `POST_NOTIFICATIONS`（API 33）：仅在 33+ 请求，低版本走安装时授权
- Apache HTTP legacy：清单中已声明，低版本可用

## 局限

静态扫描只能证明「没有直接调用不存在的 API」，不能证明分支语义正确。
高版本分支的实际行为需要对应 API 级别的运行时验证（当前已覆盖 27 / 28）。
