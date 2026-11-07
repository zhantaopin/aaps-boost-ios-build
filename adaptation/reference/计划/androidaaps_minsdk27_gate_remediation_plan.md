# minSdk 27 适配遗留问题修复 & 系统性 API 门控补扫计划

## Repository Research

### 为什么上次扫描漏掉了 `SecureEncryptImpl` 里的两个致命点

前次在实现阶段跑了两条后台扫描脚本，都**没覆盖到最隐蔽的那两个模式**：

1. **脚本 A**（只搜 `setIsStrongBoxBacked` 字符串）：能命中 line 165 的方法调用，但它根本没搜 **StrongBoxUnavailableException** 这个 API 28+ 的异常类引用。脚本输出 section 2-5 扫了一堆 AndroidKeyStore / Bluetooth / Manifest 权限字符串，却**跳过了 catch 子句和 import 语句**这两种更隐蔽的高版本引用。

2. **脚本 B**（只 grep `android.security` / `android.bluetooth` / `android.foreground` import）：能列出所有 import 行，包括 SecureEncryptImpl.kt 的 3 个 import（line 6/7/8），但脚本里管道截断了输出，最后只打印了一堆 KSP 生成的 Room DAO 代码（脚本本身是 `grep ... | head -1`？看输出末尾有 `grep: 写错误: 断开的管道`，确实是管道炸了）。即使管道不炸，脚本 B 也**没深入文件内部看 catch 语句和 Builder 链式调用**——它只做了 import 级的列名。

3. **根本的扫描模型缺陷**：
   - 只考虑了"方法调用 NoSuchMethodError"，**完全漏掉了"catch 高版本异常类在类加载期 NoClassDefFoundError"这一致命模式**。后者在 compileSdk 37 下**编译通过**，lint 默认也不报，只有真正加载这个类时才炸，比方法调用的 NoSuchMethodError 更隐蔽、更早触发。
   - 只 grep 了 `android.security` / `android.bluetooth` / `android.foreground` 三个包前缀，没扫 **所有 catch 子句里引用的 android.* 异常类**。
   - 没扫 **所有 `KeyGenParameterSpec.Builder` 链式调用的所有方法**，只扫了 import 行。

### SecureEncryptImpl.kt 当前真实状态（已读源码）

| 位置 | 代码 | 状态 |
|---|---|---|
| line 8 | `import android.security.keystore.StrongBoxUnavailableException` | ❌ **API 28+ 类**，编译通过但 API 27 加载就 NoClassDefFoundError |
| line 152 | `val tryStrongBox = strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` | ✅ 调用点 gate 已加 |
| line 165 | `.apply { if (strongBox) setIsStrongBoxBacked(true) }` | ❌ 函数内部**无防御 gate**，调用方漏门控就炸 |
| line 170 | `catch (e: StrongBoxUnavailableException)` | ❌ **类加载期致命点**，比 line 8 的 import 更直接触发 NoClassDefFoundError |
| line 173-174 | `if (strongBox) { log.info(...); null } else throw e` | 逻辑本身对，但 catch 的异常类不存在 |

### 其他 KeyStore 调用点全库验证（grep 结果）

- `KeystoreStoreCipher.kt` (app/src/main/...) ✓ 安全：只用 API 23+ 的 `.setBlockModes / .setEncryptionPaddings / .setKeySize`，不碰 StrongBox 也不 import 高版本异常
- 其他文件无 `android.security.keystore` 引用
- `implementation/src/androidMain/` 下无其他 android.* 引用（Grep 全库确认）

### Manifest 权限核查

前次正确清理了 BLUETOOTH_CONNECT/SCAN 的 `maxSdkVersion=30`（不能加，加了 API 31+ 设备就找不到权限声明）。BLUETOOTH / BLUETOOTH_ADMIN 的 `maxSdkVersion=30` **必须保留**（API 1+ 旧权限，API 31+ 自动忽略 maxSdkVersion 以外的属性）。

---

## Files and Modules

| 文件 | 预期改动 |
|---|---|
| `implementation/src/androidMain/kotlin/app/aaps/implementation/protection/SecureEncryptImpl.kt` | 3 处修改（见下方 Step 1） |
| `buildSrc/src/main/kotlin/Versions.kt` | 不动（minSdk 27 已设好） |
| `KeystoreStoreCipher.kt` | 不动（已验证安全） |
| AndroidManifest.xml 系列 | 不动（已清理完 maxSdkVersion） |

---

## Implementation Steps

### Step 1 — 修 SecureEncryptImpl.kt（P0 紧急，3 处改）

**改动 A：删除 line 8 的 `import StrongBoxUnavailableException`**

**改动 B：line 170 的 `catch (e: StrongBoxUnavailableException)` 改成 catch Exception + 字符串匹配**
```kotlin
} catch (e: Exception) {
    // StrongBoxUnavailableException is API 28; on API 27 catch'ing it directly would
    // throw NoClassDefFoundError at class-load time. Match by class name instead.
    val isStrongBoxUnavailable = e.javaClass.name == "android.security.keystore.StrongBoxUnavailableException"
    if (isStrongBoxUnavailable && strongBox) {
        log.info(LTag.CORE, "$MODULE: StrongBox unavailable, falling back to TEE-backed key for alias=$keyAlias")
        null
    } else {
        throw e
    }
}
```

**改动 C：line 165 函数内部加防御 gate（即使调用方漏门控也安全）**
```kotlin
.apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
```
（调用方 gate 已保证 API 27 不会把 strongBox=true 传进来，但内部 gate 是防御，防止将来有人绕过调用方或从别处直接调 generateAesKey）

### Step 2 — 重新编译

```bash
./gradlew :implementation:compileAndroidMainKotlinMetadata \
          :app:compileFullDebugKotlin \
          2>&1 | tail -50
```
预期：0 `Unresolved reference`、0 `No value passed to` 之类的 error。

### Step 3 — 构建 + 签名 + 推送 + 模拟器 5 分钟冒烟

1. `./gradlew :app:assembleFullRelease`
2. `bash /tmp/sign-apks-safe.sh`（demo keystore，alias key0，密码 androidaps）
3. 宿主机手动 `emulator -avd API27_Phone -no-snapshot-load`（sandbox 拿不到 /dev/kvm）
4. `adb -e push ...full-release-signed.apk /data/local/tmp/aaps-test.apk`
5. `adb -e shell pm install -r -d /data/local/tmp/aaps-test.apk`
6. `adb -e shell am force-stop info.nightscout.androidaps && adb -e shell am start -n info.nightscout.androidaps/app.aaps.ComposeMainActivity`
7. **等 5 分钟**（覆盖冷启动 + RunningConfigurationPublisher.publishCold() → MasterSigningKey.sign() 路径）
8. `adb -e logcat -d -b crash | tail -30` — 预期：**无 `NoSuchMethodError`、无 `NoClassDefFoundError`、无 `StrongBox`/`setIsStrongBoxBacked`/`SecureEncryptImpl` 相关条目**
9. 额外观察：`adb -e logcat -d | grep -iE "clientcontrol|MasterSigningKey|SecureEncrypt|StrongBox|falling back"` 应看到正常的 "falling back to TEE-backed key" INFO 日志

### Step 4 — 全库 API 28+ 门控系统性补扫（脚本化，避免再漏）

这步在 Step 3 通过后执行。用以下 4 条 grep 一次性扫全库，**不排除 build/，只排除测试目录**：

```bash
cd AndroidAPS-official-4.0.0

# 4-1) 所有 KeyGenParameterSpec.Builder 的 API 28+ 方法链
grep -rInE "\.setIsStrongBoxBacked|\.setUserAuthenticationValidityDurationSeconds" \
  --include="*.kt" --include="*.java" \
  implementation/src/androidMain app/src/main */src/androidMain \
  | grep -v "/test"

# 4-2) 所有 KeyProtection.Builder（API 23 本身 OK，但链式方法里可能有高版本）
grep -rIn "KeyProtection.Builder" --include="*.kt" --include="*.java" \
  implementation/src/androidMain app/src/main */src/androidMain

# 4-3) 所有 catch 子句里引用的 android.* 异常类（类加载期风险）
grep -rInE "catch\s*\(([^)]+android\.[^)]+)\)" \
  --include="*.kt" --include="*.java" \
  implementation/src/androidMain app/src/main */src/androidMain

# 4-4) 所有 import 的 android.security.keystore.* 高版本异常类
grep -rInE "import android\.security\.keystore\.(StrongBoxUnavailableException|KeyGenParameterSpec)" \
  --include="*.kt" --include="*.java" \
  implementation/src/androidMain app/src/main */src/androidMain
```

对每个命中点逐一核实：
- 是否已有 `Build.VERSION.SDK_INT >= X` 门控
- catch 子句里的异常类 API level 是否 ≥ 27（可查 https://developer.android.com/reference/android/security/keystore）
- import 的异常类如果 API level > minSdk 27，必须删除 import 并改用字符串类名匹配

### Step 5 — 沉淀规则，避免再犯（追加到 minSdk 适配 SKILL 清单）

在之前沉淀的 minSdk 27 适配 SKILL.md 里追加一条"运行前静态检查清单"：

> - [ ] 全库 `catch (XxxxHighLevelException)` 模式检查：任何 catch 的异常类如果 API level > minSdk，必须改用 `catch (e: Exception)` + `e.javaClass.name == "..."` 字符串匹配，**直接 catch 会在类加载期 NoClassDefFoundError**。
> - [ ] 全库 KeyGenParameterSpec.Builder 链式方法 API level 核查：`setIsStrongBoxBacked` = API 28；其他 Builder 方法（setBlockModes / setEncryptionPaddings / setKeySize / setUserAuthenticationRequired）= API 23，安全。
> - [ ] 扫描脚本必须覆盖**方法调用** + **catch 子句类型引用** + **import 语句里的异常类**三维，不能只扫 import 行。

---

## Dependencies and Considerations

- compileSdk 37 / targetSdk 35 **不动**，只降 minSdk 到 27
- build/ 目录（Room KSP 生成的 DAO 等）不扫——它们不直接调 Android API
- KeyGenParameterSpec.Builder 本身是 **API 23+**，安全；但它的方法有 API 分布：
  | 方法 | API level | minSdk 27 安全？ |
  |---|---|---|
  | `setBlockModes / setEncryptionPaddings / setKeySize / setUserAuthenticationRequired` | 23 | ✅ |
  | `setIsStrongBoxBacked` | **28** | ❌ 必须门控 |
- GCMParameterSpec（line 326 用到）= API 19+，安全
- `Cipher.getInstance("AES/GCM/NoPadding")` = API 1+，安全
- `KeyStore.getInstance("AndroidKeyStore")` = API 23+，安全

---

## Validation Matrix

| 检查项 | 命令 / 方法 | 预期结果 |
|---|---|---|
| 编译 | `./gradlew :implementation:compileAndroidMainKotlinMetadata :app:compileFullDebugKotlin` | 0 unresolved reference error |
| lint NewApi | `./gradlew :app:lintFullDebug 2>&1 | grep -E "NewApi|Error"` | 无新增 NewApi error |
| 模拟器启动 5 分钟后 crash 日志 | `adb -e logcat -d -b crash` | 空或只有 ANR，**无 StrongBox / NoClassDefFoundError / NoSuchMethodError** |
| clientcontrol 发布路径 | `adb -e logcat -d | grep -iE "clientcontrol|MasterSigningKey|falling back"` | 有 "falling back to TEE-backed key" INFO 日志 |
| 全库 grep 扫描 | Step 4 的 4 条 grep | 空集（除了已有的正确门控） |

---

## Risks

| 风险 | 处理 / 降级 |
|---|---|
| `catch (e: Exception)` + 字符串匹配的可靠性 | `e.javaClass.name` 是 JVM 规范，厂商 ROM 不会改这个值。StrongBoxUnavailableException 本身 API 27 不存在，所以这条字符串匹配在 API 27 上永远返回 false → 不会被误触发。安全。 |
| 模拟器 5 分钟冒烟覆盖不全 | RunningConfigurationPublisher.publishCold() 只触发一次 clientcontrol 加密；需要真机长时间运行观察其他 Worker/Publisher 路径。真机 logcat 命令：`adb logcat -d -b crash -t 200 > /tmp/aaps-crash.txt` |
| 将来有人绕开门控直接调 `generateAesKey(..., strongBox=true)` | Step 1C 在函数内部加了 `SDK_INT >= P` 防御 gate；Step 5 沉淀的检查清单 + Step 4 的脚本化扫描会在下次有人改代码时发现。 |
| 其他 Android API 28+ 方法（不限于 KeyStore） | Step 4 的 grep 脚本覆盖了 `android.security.keystore` 包的完整范围；Bluetooth 权限前次已覆盖；ForegroundServiceStartNotAllowedException 前次已门控。