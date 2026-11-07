---
name: minsdk-api-gate-scan
description: 在 Android 项目降 minSdk 时执行三维 API 门控静态扫描，捕捉 compileSdk 编译通过但运行时崩溃的 NoSuchMethodError 和类加载期 NoClassDefFoundError 风险。用户提到 minSdk 适配、API level 门控、StrongBox、ForegroundService、API 28/31+ 兼容性时使用。不要用于 lint 或编译任务。
---

# minSdk API Gate Scan（降 minSdk 三维门控扫描）

## 何时触发

- 用户说"降 minSdk"、"适配老版本 Android"、"API level 门控"
- 用户提到"StrongBox"、"ForegroundServiceStartNotAllowedException"、"KeyStore"、"KeyGenParameterSpec"
- 用户说"lint 不报但运行时崩"、"NoSuchMethodError"、"NoClassDefFoundError"
- 扫一遍刚改完的 minSdk 适配代码，发布前做静态检查

## 核心认知（StrongBox 教训）

降 minSdk 的最大坑不是"方法调用 NoSuchMethodError"，而是**更隐蔽的类加载期 NoClassDefFoundError**：

1. `import 高版本异常类` → compileSdk 37 编译过，但 minSdk 设备类加载时找不到这个类直接炸
2. `catch (高版本异常类)` → 同上，catch 类型里引用的类不存在，类验证阶段就炸
3. `高版本方法调用` → 编译过，运行到那一行才 NoSuchMethodError

lint 默认不报前两种。脚本如果只搜方法名或只 grep import 行**会漏掉致命点**——必须三维覆盖。

## 三维扫描脚本

把 `<repo>` 换成项目根目录，**不要 grep ... | head -N**（截断输出会漏掉后半段命中）。

### A. 方法调用（NoSuchMethodError 风险）

KeyGenParameterSpec.Builder 最危险，方法 API level 分布：

| 方法 | API | minSdk 27 安全？ |
|---|---|---|
| `setBlockModes / setEncryptionPaddings / setKeySize` | 23 | ✅ |
| `setUserAuthenticationRequired` | 23 | ✅ |
| `setUserAuthenticationValidityDurationSeconds` | 24 | ✅ |
| **`setIsStrongBoxBacked`** | **28** | **❌ 必须双重门控** |
| `setKeyValidityStart / setKeyValidityEnd` | 23-24 | ✅ |
| `setKeyAgreementParam / setAttestationChallenge` | 28-30 | ❌ 按 API 门控 |

```bash
grep -rInE "\.setIsStrongBoxBacked|\.setUserAuthenticationValidityDurationSeconds|\.setCertificateSerialNumber" \
  --include="*.kt" --include="*.java" <repo> \
  | grep -v "/build/\|/test/\|/generated/"
```

### B. catch 子句里的 android.* 异常类（类加载期致命风险！）

```bash
grep -rInE "catch\s*\([^)]*:?\s*android\.[^)]+\)" \
  --include="*.kt" --include="*.java" <repo> \
  | grep -v "/build/\|/test/\|/generated/"
```

**命中就改**：把 `catch (e: HighLevelException)` 改成 `catch (e: Exception)` + `e.javaClass.name == "完整类名"` 字符串匹配。字符串匹配在类加载期安全，运行时也安全（低版本设备上该类不存在时永远返回 false）。

### C. import 的 android.* 异常类（同上风险）

```bash
grep -rInE "import android\.security\.keystore\.(StrongBoxUnavailableException|KeyPermanentlyInvalidatedException|UserAuthenticationValidityDurationException)" \
  --include="*.kt" --include="*.java" <repo> \
  | grep -v "/build/\|/test/\|/generated/"
```

**命中就删**，改成字符串类名匹配。

### D. Build.VERSION_CODES 引用（最终核查）

对照项目里的门槛表（Android minSdk 适配文档里一般有），每个 VERSION_CODES.XXX 必须有真实的 SDK_INT 门控 + 旧路径 fallback。

```bash
grep -rInE "Build\.VERSION\.(SDK_INT|VERSION_CODES\.[A-Z_]+)" \
  --include="*.kt" --include="*.java" <repo> \
  | grep -v "/build/\|/test/\|/generated/"
```

## 修复模板

### StrongBox 异常类 → 字符串匹配（4 步）

```kotlin
// 1) 删除 API 28+ 异常类的 import
// import android.security.keystore.StrongBoxUnavailableException ← 删

// 2) 补 Build import（降 minSdk 时常漏）
import android.os.Build

// 3) catch 异常类 → catch Exception + 字符串类名匹配
} catch (e: Exception) {
    // StrongBoxUnavailableException is API 28; catching it directly would throw
    // NoClassDefFoundError at class-load time even before this code runs. Match by name.
    val isStrongBoxUnavailable = e.javaClass.name == "android.security.keystore.StrongBoxUnavailableException"
    if (isStrongBoxUnavailable && strongBox) { null } else { throw e }
}

// 4) 函数内防御 gate（即使调用方漏门控也安全）
.apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
```

### ForegroundServiceStartNotAllowedException（API 31+）

类名放在 SDK_INT 门控内、用内部 `e is` 匹配（避免类加载引用）：

```kotlin
catch (e: RuntimeException) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        e is android.app.ForegroundServiceStartNotAllowedException) { ... }
}
```

### Bluetooth 权限（API 31+）

API 31 以下 `BLUETOOTH_CONNECT/SCAN` **查询恒为 DENIED**，所有泵驱动会静默拒绝工作。minSdk 27 上永远走 `BLUETOOTH + ACCESS_FINE_LOCATION` 分支。

## 执行清单

降 minSdk 时按顺序跑：

1. **lint 探针**：`./gradlew lintFullDebug` 扫 NewApi / ObsoleteSdkInt
2. **三维 grep**：A / B / C / D 四条命令逐次执行，零未门控命中才算过
3. **编译验证**：`assemble<Flavor>Release` BUILD SUCCESSFUL
4. **签名 + 推模拟器**：apksigner verify 通过，pm install EXIT=0
5. **启动后等 5 分钟**（不要冷启动渲染完就停，加密路径 / Worker / Publisher 可能在 1-3 分钟后触发）
6. **crash log + 功能路径 grep**：`adb logcat -d -b crash` 空 + 加密/签名/连接等关键路径有正常 INFO 日志

## 已知坑位（从 AndroidAPS 4.0.0 minSdk 27 实战）

| 文件 | API | 风险 | 已修复？ |
|---|---|---|---|
| `SecureEncryptImpl.kt:8,170` | 28 | import + catch StrongBoxUnavailableException → NoClassDefFoundError | ✅ |
| `SecureEncryptImpl.kt:165` | 28 | setIsStrongBoxBacked → NoSuchMethodError | ✅ |
| `ComposeMainActivity.kt:259` | 31 | window.setHideOverlayWindows | ✅ |
| `DummyService(Helper).kt:81/66/91` | 31 | ForegroundServiceStartNotAllowedException | ✅ |
| Worker 三个 | 34/35 | setExpedited / getForegroundInfo | ✅ |
| Manifest 13 个 | 31 | BLUETOOTH_CONNECT/SCAN maxSdkVersion=30 误加 | ✅ |

## 反模式（照镜检查自己的扫描脚本）

1. ❌ 只搜方法名，不搜 import + catch
2. ❌ 管道截断：`grep ... | head -1` 或 `grep ... | head -N`，漏掉后半段致命命中
3. ❌ 只扫 `app/src/main`，不扫 `implementation/src/androidMain`（KMP 架构盲区）
4. ❌ 只 grep `android.security` 前缀，不看具体异常类
5. ❌ 把 lint 当成唯一防线（lint 默认不报类加载期风险）