---
name: aaps-old-android-adaptation
description: Full workflow to adapt an AndroidAPS fork to an older Android (minSdk 27 = Android 8.1) - baseline, lint probe, API guards, per-version permissions, on-device verification, signed release build for ALL flavors, adb debugging, and feature work. Written from a completed 2026-10 session; commands are Linux/bash.
---

# AndroidAPS 适配老版本 Android 全流程（minSdk 27 = Android 8.1）

本 skill 是一次完整实战的沉淀：把 minSdk 31 的 AndroidAPS 4.0.0-rc1 源码全面适配到
Android 8.1（API 27），在 Android 8.1 模拟器和 Android 9 手表（CD_Watch_A）上验证，
构建签名 release（主控 full + client 两个 flavor）并装机调试，随后在设备上定位并修复
了多个"只在老系统上出现"的 bug。方法可推广到其他 minSdk 降级场景。

通用原则：
- 涉及胰岛素剂量安全的逻辑（SMB、泵重连、循环间隔、保护密码体系）必须有用户明确
  确认才改，改完要在真机验证。
- 改动先提案、用户确认后再动手（"修复它/解决/确认"即是确认）。
- 不主动 commit；用户说"提交"再按主题分 commit。
- 每步构建日志重定向到文件再 grep，不要用管道（管道会掩盖真实退出码）。

---

## 第 0 步：git 基线

源码目录往往不是 git 仓库。先建基线，所有改动可回退、可审查：

```
git init && git add -A && git commit -m "Baseline before adaptation"
```

（无全局 git 身份时先 `git config user.name/user.email`，仅仓库级。）

---

## 第 1 步：影响面评估（lint 探针 + 手动 grep，不要靠猜）

**两件事同一批次做**——先跑 lint 探针（扫普通 Android 模块），紧接着手动 grep
扫 KMP androidMain 盲区，一次性产出"全部需要改的点"清单。

### 1.1 lint 探针

用临时 init 脚本把 minSdk 强制降下来跑全仓 lint。KMP 模块没有 `android` 扩展，
minSdk 在 `kotlin.android` 上。

`.claude/tmp/minsdk.init.gradle`：

```groovy
allprojects { p ->
    afterEvaluate {
        def android = p.extensions.findByName('android')
        if (android != null) {
            try { android.defaultConfig.minSdk = 27; println "[PROBE] ${p.name}: ok" }
            catch (Throwable t) { println "[PROBE] ${p.name}: defaultConfig FAILED" }
        }
        def kotlin = p.extensions.findByName('kotlin')
        if (kotlin != null) {
            try {
                def kAndroid = kotlin.getProperty('android')
                if (kAndroid != null) kAndroid.setProperty('minSdk', 27)
            } catch (Throwable t) { println "[PROBE] ${p.name}: kotlin.android FAILED" }
        }
        if (p.name == 'app') {
            try { android.lint.checkDependencies = true } catch (Throwable t) { println "app lint fail" }
        }
    }
}
```

运行并读报告：

```
./gradlew -I .claude/tmp/minsdk.init.gradle :app:lintFullDebug > lint.log 2>&1
grep -E ": Error" app/build/intermediates/lint_intermediate_text_report/fullDebug/lintReportFullDebug/lint-results-fullDebug.txt
```

探针同时验证了依赖图在低 minSdk 下可合并（能跑到 lint 报告 = 库都兼容）。

### 1.2 手动 grep 扫 KMP androidMain 盲区（lint 扫不到）

```
grep -rn --include=*.kt -E "canScheduleExactAlarms|VibratorManager|ForegroundServiceStartNotAllowedException|writeCharacteristic\([^)]*,[^)]*,|FOREGROUND_SERVICE_TYPE|android\.window\.|NotificationChannelGroup" <repo> | grep -v /build/
grep -rn --include=*.kt -oE "VERSION_CODES\.[A-Z_]+" <repo> | grep -v /build/ | sort | uniq -c
```

产出的 VERSION_CODES 统计要对照第 2 步的门槛表，确认每个高于 API 27 的引用
都有真实的版本保护（有旧路径 fallback）。

---

## 第 2 步：API 门控修复 + 蓝牙权限映射

### 2.1 VERSION_CODES 门槛表（改代码前先对照）

| 版本 | API | 典型位置 | 27 < ? | 需要门控 |
|------|-----|---------|--------|---------|
| O | 26 | plugins/sync, plugins/automation | ❌ | 否 |
| **P** | **28** | **SecureEncryptImpl.kt:167** | **✅** | **是**（容易漏） |
| S | 31 | pump/*, implementation, core/* | ✅ | 是 |
| TIRAMISU | 33 | shared/impl, implementation, core/utils | ✅ | 是 |
| UPSIDE_DOWN_CAKE | 34 | AlarmSoundPlayerImpl.kt:119 | ✅ | 是 |
| VANILLA_ICE_CREAM | 35 | omnipod/common (2 处) | ✅ | 是 |
| BAKLAVA | 36 | PersistentNotificationPlugin (2 处) | ✅ | 是 |
| CINNAMON_BUN | 37 | PersistentNotificationPlugin | ✅ | 是 |

### 2.2 API 门控三种写法

- **直接调用**：`if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { newApi() } else { oldApi() }`
- **catch 不存在的异常类**：catch 父类（`RuntimeException`），在块内
  `if (SDK_INT >= S && e is NewException)`——类引用放在版本判断内是安全的。
- **精确闹钟权限**：API 31 以下根本没有该权限，默认视为允许
  （`SDK_INT >= S && !canScheduleExactAlarms()`）。

**JVM 单元测试里 `SDK_INT == 0`**，纯 `SDK_INT` 门控会让新系统路径测不到。给类加
`internal var` 测试缝隙，默认值即版本判断结果，测试里手动置 true 固定新系统行为
（参考 `AlarmNotificationManager.exactAlarmsNeedPermission`）。Robolectric 测试
`@Config(sdk = [35])` 不受影响，不需要缝隙。

### 2.2.1 三维 API 门控静态扫描（StrongBox 式隐蔽遗漏铁规则）

改代码前、跑 lint 后、提 release 前，**必须跑下面 4 条 grep**。
lint 默认不报"catch 高版本异常类 + import 高版本异常类"，这两类风险是类加载期
`NoClassDefFoundError`，compileSdk 37 下编译通过但 minSdk 设备运行时炸，比方法调用
`NoSuchMethodError` 更隐蔽更早触发。

**前次在 AndroidAPS 4.0.0 minSdk 27 适配中，两次后台扫描脚本都漏掉了**：
脚本 A 只搜 `setIsStrongBoxBacked` 方法名（命中了方法调用，却漏掉同文件的
`import StrongBoxUnavailableException` 和 `catch (e: StrongBoxUnavailableException)`
——这两处才是类加载期致命点）；脚本 B 只 grep android.* import 行（管道截断了输出，
也没深入文件内部看 catch 子句和 KeyGenParameterSpec.Builder 链式方法）。

**教训：扫描必须三维覆盖，缺一不可**：

```bash
# 扫描 A：方法调用（NoSuchMethodError 风险）
# KeyGenParameterSpec.Builder 高版本方法（注意方法名 → API level 映射表见下）
grep -rInE "\.setIsStrongBoxBacked|\.setUserAuthenticationValidityDurationSeconds|\.setCertificateSerialNumber" \
  --include="*.kt" --include="*.java" <repo>/implementation/src/androidMain <repo>/app/src/main

# 扫描 B：catch 子句里引用的 android.* 异常类（类加载期 NoClassDefFoundError 风险！）
# compileSdk 下编译过但运行时找不到这个类就直接炸。必须改成 catch Exception + 字符串类名匹配。
grep -rInE "catch\s*\([^)]*:?\s*android\.[^)]+\)" \
  --include="*.kt" --include="*.java" <repo>/implementation/src/androidMain <repo>/app/src/main

# 扫描 C：import 的 android.* 异常类（同上风险）
grep -rInE "import android\.security\.keystore\.(StrongBoxUnavailableException|KeyPermanentlyInvalidatedException)" \
  --include="*.kt" --include="*.java" <repo>/implementation/src/androidMain <repo>/app/src/main

# 扫描 D：所有 Build.VERSION.SDK_INT / VERSION_CODES 引用
# 对照 2.1 门槛表，确认每个 > minSdk 的 VERSION_CODES 都有真实的门控路径
grep -rInE "Build\.VERSION\.(SDK_INT|VERSION_CODES\.[A-Z_]+)" \
  --include="*.kt" --include="*.java" <repo>/implementation/src/androidMain <repo>/app/src/main
```

**KeyGenParameterSpec.Builder 方法 API level 映射表**：

| 方法 | API | minSdk 27 安全？ |
|---|---|---|
| `setBlockModes / setEncryptionPaddings / setKeySize` | 23 | ✅ |
| `setUserAuthenticationRequired` | 23 | ✅ |
| `setUserAuthenticationValidityDurationSeconds` | 24 | ✅ |
| **`setIsStrongBoxBacked`** | **28 (P)** | **❌ 必须双重门控（调用点 + 函数内防御）** |
| `setKeyValidityStart / setKeyValidityEnd / setKeyValidityForOriginationEnd` | 23-24 | ✅ |
| `setDigests / setSignaturePaddings / setBlockModes` | 23 | ✅ |
| `setKeyAgreementParam / setAttestationChallenge` | 28-30 | ❌ 按 API 门控 |

**`SecureEncryptImpl.kt` 四步修复模板（StrongBox 异常类 → 字符串匹配）**：

```kotlin
// 1) 删除 API 28+ 异常类的 import
// import android.security.keystore.StrongBoxUnavailableException ← 删

// 2) 补 Build import（之前可能也漏了）
import android.os.Build

// 3) catch 异常类 → catch Exception + 字符串类名匹配（类加载期安全）
} catch (e: Exception) {
    // StrongBoxUnavailableException is API 28; catching it directly would throw
    // NoClassDefFoundError at class-load time even before this code runs. Match by name.
    val isStrongBoxUnavailable = e.javaClass.name == "android.security.keystore.StrongBoxUnavailableException"
    if (isStrongBoxUnavailable && strongBox) { null } else { throw e }
}

// 4) 函数内防御 gate（即使调用方漏门控也安全）
.apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
```

**为什么前次扫描漏掉的三个根本原因**（照镜检查自己的扫描脚本）：

1. 只考虑了"方法调用 NoSuchMethodError"，**完全漏掉"catch / import 高版本异常类
   在类加载期 NoClassDefFoundError"**这一致命模式。后者 compileSdk 下编译通过、
   lint 默认不报，只有真正加载类才炸——比方法调用更隐蔽、更早触发。
2. 扫描粒度太窄：要么只搜一个方法名，要么只 grep import 行，**没深入文件内部
   看 catch 子句和 KeyGenParameterSpec.Builder 链式方法**。
3. 管道截断 / head 截断输出：扫描脚本不要 `grep ... | head -1` 或 `grep ... | head -N`，
   截断输出会让你漏掉后半段致命命中点。

### 2.3 蓝牙权限版本映射（API 27 上永远走旧路径）

Android 12 (API 31) 以下 `BLUETOOTH_CONNECT/SCAN` **不存在**，查询恒为 DENIED——
所有泵驱动会静默拒绝工作。

**关键认知**：在你的 app（minSdk=27）上，`SDK_INT` 永远 < 31，所以永远走
`BLUETOOTH` + `FINE_LOCATION` 分支。VERSION_CODES.S 门控在 27 上始终为 false。

Manifest 声明：`BLUETOOTH` + `BLUETOOTH_ADMIN` + `ACCESS_FINE_LOCATION` +
`ACCESS_COARSE_LOCATION`，全部 `android:maxSdkVersion="30"`；同时声明
`BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN`（31+ 设备上生效）。

版本映射助手（放在所有泵模块都依赖的模块里）：
- `connectGranted`：31+ 查 CONNECT；**27-30 查 `BLUETOOTH`**（普通权限，安装即授）
- `scanGranted`：31+ 查 SCAN；**27-30 查 `ACCESS_FINE_LOCATION`**
- `requestPermissions()` 同映射
- **替换所有检查点**：泵前置检查、命令队列、广播接收器、每个泵驱动、combov2 等自带
  检查的库、权限向导的权限组
- **权限向导**的蓝牙权限组必须同样按版本分支：在老系统上申请
  `BLUETOOTH_CONNECT` 会被系统直接以"永久拒绝"回复，**不弹对话框**

---

## 第 3 步：中间 lint 验证

改完第 2 步 API 门控 + 第 4 步踩坑修复后跑一次 lint，确认这批改动完整。
比等全部改完再跑能更早发现问题——此时 lint Error 应该清零。

```
./gradlew :app:lintFullDebug --no-daemon > lint-mid.log 2>&1
grep -E ": Error " lint-mid.log
find app/build -name "lint-results-*txt" | head -1 | xargs cat
```

通过判据：**无 `NewApi` / `ObsoleteSdkInt` / `VisibleForTests` Error**。
Warning 可忽略（deprecation、compose 建议等）。

如果还有 Error：回到第 2 步补漏，再跑 lint，直到清零。

---

## 第 4 步：老 Android 踩坑目录（全部实际触发过 + 隐藏坑）

| 症状（仅 8.1/9/10/11 出现） | 根因 | 修法 |
|---|---|---|
| Compose 输入框自动激活某功能、且每次关闭后立即复活，界面像卡死 | API 27 及以下旧 View 系统在窗口获得焦点时把焦点分给第一个可聚焦控件 | 非激活态完全不渲染输入框，只渲染可点击的占位元素（参考 `M3SearchBar`） |
| logcat 出现 `WM-WorkerWrapper: Work [...] failed ... IllegalStateException: Not implemented`，后台收到的数据被静默丢弃（如 CGM 广播） | `setExpedited` 在 API 31 以下以前台服务模拟，需要 `getForegroundInfo()`，`CoroutineWorker` 默认未实现 | 在 Worker 基类实现 `getForegroundInfo()`（参考 `LoggingWorker`），**保留** `setExpedited` |
| 改掉上一条后数据"能收到但延迟几分钟" | 图省事把 `setExpedited` 降级成了普通任务——普通任务由 JobScheduler 调度、可被系统推迟 | 不要用普通任务换正确性：实现 `getForegroundInfo()` 后**恢复全版本 `setExpedited`**（老系统上任务运行的那几秒会短暂显示一条最低优先级通知，这是加急模拟的工作方式） |
| 权限查询恒为 DENIED、申请直接"永久拒绝" | 申请的权限在该 API 级别不存在 | 见第 2 步的版本映射 |
| SAF `createFile(mime, name)` 导出的文件没有扩展名（进而让只认 `.json` 结尾的导入列表看不见它） | 部分**厂商** DocumentsProvider 不按 MIME 自动补扩展名 | 扩展名显式写进 display name（参考 `FileListProviderImpl.newPreferenceFile`）；同文件内其他 createFile 调用就是对照 |
| 任务计划名冲突/任务不存在 | KMP 模块任务名与普通 Android 模块不同 | 见第 7 步任务名表 |
| **隐藏坑**：`VibratorManager`（API 31）在 `InsightAlertService.kt` | 直接 import 并调用了 API 31 的 VibratorManager，老系统上编译不过 | 加 VERSION_CODES.S 门控，老系统用 VIBRATOR_SERVICE + Vibrator |
| **隐藏坑**：`NotificationChannelGroup`（API 26）在 `AlarmNotificationManager` | API 26 虽然存在，但 27 上 `createNotificationChannelGroup` 行为和 31+ 不同（老系统上不自动创建子渠道） | 加 VERSION_CODES.O 门控，27 上跳过 Group 创建（直接创建子渠道即可） |
| **隐藏坑**：VERSION_CODES.P(28) 在 `SecureEncryptImpl.kt:167` | 容易漏——比 S(31) 小但 27 还是不够 | 加 VERSION_CODES.P 门控 |

WorkManager 加急任务在老系统上的完整处理（三步走，少一步就是新 bug）：
1. `setExpedited` 保留在所有 API 级别——立即执行是数据实时性的保证；
2. Worker 基类实现 `getForegroundInfo()`——返回一条最低优先级（IMPORTANCE_MIN）、
   静默、短时的通知（老系统上前台服务模拟会用它）；通知渠道不存在就现场创建；
3. 永远不要用"降级为普通任务"来修第 1 步的崩溃——那是把数据实时性卖给了 JobScheduler。

---

## 第 5 步：功能改造（本次实例，模式可复用 + API 27 兼容性确认）

先确认哪些功能需要 API 27 适配，哪些天然兼容：

| 功能 | 位置 | 需要 API 27 适配？ | 原因 |
|------|------|-------------------|------|
| FixedOffsetPlugin（固定校准） | plugins/calibration/**commonMain** | **否** | 零 Android API 依赖，Kotlin multiplatform 标准库 |
| WebDAV 云存储导出 | implementation/**androidMain** | **否** | 用 Kotlin multiplatform HTTP client，不依赖 Android 31+ API |
| SAF createFile 导出无扩展名 | FileListProviderImpl | 是 | 见第 4 步踩坑表 |

（FixedOffset 和 WebDAV 如果涉及 androidMain UI 层，单独 grep VERSION_CODES 确认。）

本次实际改造项（模式可复用）：

- **设置页加确认按钮**：`IntentPreferenceKey.withClick(confirmationMessage) { ... }`
  （`IntentKeyWithClick` 支持可选确认语）；反馈用 `notificationManager.post` +
  新 `NotificationId`（**只能追加到枚举末尾**，fromOrdinal 依赖顺序）。
- **新增偏好 key 必须同步金标文件** `app/src/testFull/resources/prefs-schema.txt`
  （格式 `key|KotlinType|exportable`，保持排序），测试 `PreferenceKeySnapshotTest` 会比对。
- **目标页批量操作**：ViewModel 遍历 objectives 置 started/accomplished，并把考试/UI
  任务的 answered 标志一并写入（考试目标在构造时会自查未答题并清完成状态）。
- **UI 字符串**：只改英文 `values/`；需要中文现场验证时手动补
  `values-zh-rCN/`（会被 Crowdin 覆盖，需知悉）。
- **SMB 策略放宽**（本次：全数据源支持"始终启用 SMB"）：`SourceSensor.advanced
  FilteringSupported()` 单点返回 true，解除传感器白名单；保留 `needsFlatBgCheck`
  （卡死传感器平线检测）作为部分保护。改的是剂量安全，必须用户明确要求。
- **保护密码体系严格化**（本次：大剂量动作只认大剂量密码）：
  - `ProtectionCheckImpl.collectAuthMethods` 严格只取请求级别自己的凭证——
    **不要**把主密码当万能凭证加进每个请求；主密码只在某级保护类型显式选
    "主密码"时用于该级。
  - `UnifiedAuthDialog` 提示语按请求级别命名凭证（"输入大剂量密码"/"输入设置
    密码"/"输入PIN码"）。
  - 边界：纯生物识别配置在无生物识别设备上 → 主密码作恢复凭证（防锁死）。

---

## 第 6 步：省电优化（安全边界内）

- **日志异步写盘**：logback 文件 appender 包 `AsyncAppender`（`neverBlock=true`，
  queue 4096）——泵/循环/蓝牙线程不再被每条日志的同步写盘阻塞。app 和 wear 的
  `assets/logback.xml` 都改。
- 不动的（安全换电，需用户拍板）：循环频率、泵重连间隔、自动化检查间隔。
- 桌面小组件刷新已有"屏幕关闭即跳过"门控；KeepAlive 三重排程是报警基础，不动。
- 加急任务在老系统上的短暂通知（IMPORTANCE_MIN）是为数据实时性付的必要成本。

---

## 第 7 步：生产发布前检查矩阵

### 7.1 任务名表

KMP 模块任务名与普通模块不同（猜错名浪费一整轮 configure）：

| 内容 | KMP 模块 | 普通 Android 模块 |
|---|---|---|
| 编译 Android | `:mod:compileAndroidMain` | `:mod:compileDebugKotlin` |
| Android 单元测试 | `:mod:testAndroidHostTest` | `:mod:testDebugUnitTest` |
| JVM 测试 | `:mod:jvmTest` | — |

### 7.2 Android Lint 生产复查

第 1 步的 lint 探针是**改前评估**——告诉你要修什么。这里的 lint 是
**改完所有 API 门控、蓝牙权限映射、踩坑修复后的最终确认**。

重新跑一次（minSdk 已降到 27，直接跑即可，不需要 init 脚本）：

```
./gradlew :app:lintFullDebug --no-daemon > lint-final.log 2>&1
grep -E ": Error " lint-final.log
find app/build -name "lint-results-*fullDebug*txt" | head -1 | xargs cat
```

通过判据：**无 `NewApi` / `ObsoleteSdkInt` / `VisibleForTests` 级别的 Error**。
Warning 可以忽略（deprecation、compose 建议等）。

KMP 模块 androidMain 的盲区再跑一遍手动 grep（第 3 步相同命令），
确认所有 VERSION_CODES 引用都有真实的版本保护。

### 7.3 JVM 全量单元测试

官方 CI 套件（`.github/workflows/unit-tests.yml`）：

```
./gradlew --console=plain --no-daemon --continue -PfirebaseDisable \
  testFullDebugUnitTest testDebugUnitTest jvmTest testAndroidHostTest :desktop:shell:test
```

- `--continue` 必加，否则第一个红模块后不再调度其他模块。

**通过判据**：BUILD SUCCESSFUL，exit code 0。

**读结果**：

```
grep -E "FAIL|BUILD (SUCCESSFUL|FAILED)" /tmp/jvm-test.log
# 各模块具体 PASS/FAIL
find . -path "*/build/test-results/*" -name "*.xml" ! -path "*/build/intermediates/*" \
  | xargs grep -l "failures=\"[1-9]" 2>/dev/null
```

**区分 baseline failure vs 新引入**：某些模块（如 WebDAV 工作时的
`BTReceiverTest`）在 minSdk 改 27 之前就红，不是你的改动引入的。
跑完整套前先 `git stash` 到基线 commit 跑一次，记录 baseline failures。

dex/transform 报损坏输入错误 → `./gradlew --stop` 后重试。

### 7.4 Instrumented 测试（真机必需）

AndroidAPS 控制胰岛素泵，JVM 单测不能验证真实设备行为。
必须在 **Android 8.1 或以上真机**（或模拟器）上跑：

```
./gradlew :app:connectedFullDebugAndroidTest
```

核心测试：
- `LoopTest`（循环核心逻辑）
- `SetupWizardE2ETest`（设置向导端到端）
- `DetermineBasalAdapter`（基础率计算）
- Omnipod `DashHistoryTest` / `ErosHistoryTest`（泵历史）

官方 CircleCI 在 3 个 API 31 模拟器上分 @ShardA/@ShardB/@ShardC 三片并行。
minSdk 27 场景建议至少在 Android 8.1 真机上跑一遍完整套件。

Linux 机器无 Android 模拟器时，可以：
1. 用 `adb devices` 看是否有真机 USB 连接
2. 或者把 APK 上传 GitHub Actions，让 CircleCI/Android CI 跑 instrumented 套件

### 7.5 真机/模拟器长稳验证（医疗软件必需）

AndroidAPS 是**每 5 分钟循环一次、24×7 不间断**的后台系统。
Instrumented 测试跑几分钟验证"功能对不对"，长稳验证跑几小时到一夜
验证"进程不被杀、数据不丢、内存不炸"。

**minSdk 27 特别需要长稳验证的原因**：
- Android 8.1 内存管理更粗暴，低内存时直接杀后台
- `setExpedited` 在老系统上用**真实前台服务**模拟（会弹通知），
  长时间运行会不会因通知渠道问题被系统限流
- Doze 模式更激进，不加白名单闹钟和 WorkManager 都会被推迟
- BLE 连接在老系统上更不稳定，会不会悄悄断了没人重连
- 循环每 5 分钟一次，24 小时 2880 次——每次一点点泄漏就是 OOM

**前提**：Android 8.1 或以上真机（或模拟器）已连接 adb。

**准备**：

```
adb install -r app-full-debug.apk
# Doze 白名单（8.1 上必加，否则后台任务被推迟）
adb shell dumpsys deviceidle whitelist +app.aaps
# 关闭电池优化
adb shell dumpsys deviceidle disable
# 保持屏幕常亮（模拟器）
adb shell svc power stayon true
```

**运行**（用户手动配好泵 + CGM + 循环后，让它跑 6-12 小时）：

```
adb shell am start -n app.aaps/.MainActivity
```

**持续监控脚本**（每 10 分钟查一次，持续 12 小时）：

```bash
for i in $(seq 1 72); do
  echo "=== $(date +%H:%M) 检查 $i ===" | tee -a /tmp/stability.log
  if ! adb shell pidof app.aaps > /dev/null; then
    echo "❌ 进程已死！" | tee -a /tmp/stability.log
    adb logcat -d -t 2000 | grep -E "FATAL|AndroidRuntime|Watchdog|Killing|ANR" | tail -20 \
      >> /tmp/stability.log
  else
    echo "  ✅ 进程活着"
  fi
  # 数据库有新记录（行数应该持续增长）
  rows=$(adb shell run-as app.aaps sqlite3 /data/data/app.aaps/databases/treatments.db \
    "SELECT COUNT(*) FROM treatments;" 2>/dev/null)
  echo "  treatments 行数: $rows" >> /tmp/stability.log
  # 电池状态
  adb shell dumpsys battery | grep -E "level|temperature" >> /tmp/stability.log
  sleep 600
done
```

**通过判据**：
- 进程全程存活（`pidof` 每次都返回 PID）
- treatments 数据库行数持续增长（循环在正常工作）
- 无 FATAL / ANR / Watchdog 日志
- 电池温度正常（不过热）
- 结束后 dump logcat 检查有没有被系统 kill：
  `adb logcat -d | grep -i "killing\|force stopping\|lowmemorykiller"`

### 7.6 多 flavor Release 构建

给主控装了新构建、client 端不重建的话，两端行为不一致会当成新 bug
（本次实际发生：client 端弹旧版密码对话框）。

全量构建（假设已配 release signingConfig）：

```
./gradlew --no-daemon \
  assembleFullRelease assembleAapsclientRelease assemblePumpcontrolRelease \
  -PfirebaseDisable 2>&1 > /tmp/release-build.log
```

通过判据：所有 flavor BUILD SUCCESSFUL，APK 在
`app/build/outputs/apk/<flavor>/release/app-<flavor>-release.apk`。
（flavor 还有 aapsclient2/3）。

### 7.7 覆盖率（可选，生产级参考）

官方 CI 把 JVM（GitHub Actions）和 instrumented（CircleCI）的覆盖率合并后上传 Codecov。
本地只跑 JVM 的话：

```
./gradlew --no-daemon jacocoAllDebugReport
```

报告在 `build/reports/jacoco/jacocoAllDebugReport/html/index.html`。
不需要额外硬件，但只覆盖 JVM 能跑到的代码（不包含 androidTest）。

### 7.8 生产发布前 checklist

打 release 标签前扫一遍：

- [ ] `lint-fullDebug` 无 API level Error
- [ ] 三维 API 门控 grep 跑过（方法调用 + catch 类型引用 + import 异常类，详见 2.2.1）
- [ ] JVM 全量单测 BUILD SUCCESSFUL（baseline failures 已记录）
- [ ] Instrumented 测试在真机 Android 8.1 上 PASS
- [ ] 真机/模拟器长稳跑 6-12 小时无崩溃、数据持续到达
- [ ] 所有 flavor Release APK 成功生成
- [ ] 真机手动验证：蓝牙权限弹框正确、泵能连接、循环能跑
- [ ] Manifest 权限在 8.1 上无"永久拒绝"（权限向导弹框正确）
- [ ] WorkManager 加急任务不崩溃、数据实时到达
- [ ] 签名密钥库已备份（换密钥就无法覆盖安装）

---

## 第 8 步：签名 release 构建

仓库无签名配置时（release 默认不签名）：

1. 生成密钥库。Linux 无 Program Files 沙箱限制，但用 java 直接调 keytool 主类仍可避免 PATH 依赖：

```
java sun.security.tools.keytool.Main -genkeypair -keystore app/release.jks \
  -alias release -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "<pass>" -keypass "<pass>" -dname "CN=..."
```

2. `keystore.properties`（storeFile/storePassword/keyAlias/keyPassword）+ `.gitignore`
   加入 `*.jks` 和 `keystore.properties`（**密钥文件永不入库，且必须备份**——换密钥
   就无法覆盖安装，只能卸载清数据）。
3. `app/build.gradle.kts`：读 keystore.properties 建 `signingConfigs`，release
   buildType 挂上；文件缺失则保持不签名。
4. 验签。`jarsigner` 对 v2 签名 APK 会误报"未签名"；用 apksigner。Linux 上 SDK 路径直接可用，先 export ANDROID_HOME：

```
export ANDROID_HOME=/home/kylin/Android/Sdk
java -jar "$ANDROID_HOME/build-tools/36.0.0/lib/apksigner.jar" verify --print-certs <apk>
```

（build-tools 版本号 36.0.0 按本机实际路径调整，查 `ls $ANDROID_HOME/build-tools/`。）

---

## 第 9 步：adb 装机与设备调试

- `adb devices -l` 先看设备，所有命令带 `-s <serial>`。
- `adb install -r` 保留数据；**debug 与 release 签名不同，互装必须先卸载**（清数据，
  须用户明确同意）。卸载报 `DELETE_FAILED_INTERNAL_ERROR` = 本来就没装。
- 装完 `pm grant` 预授运行时权限（老系统上 BLE 扫描必需定位权限），再看
  `dumpsys package | grep versionName` 确认版本。
- 启动后 logcat 查 `FATAL|AndroidRuntime` 确认无崩溃。
- **多设备注意**：模拟器会中途关闭、手表 USB 会掉线重连（`device offline`，甚至
  `install` 执行到一半掉线——轮询 `get-state` 回 `device` 后重试），每条命令前确认
  设备在线；versionName 里的 commit 短哈希不能区分同 commit 的不同构建。

### 驱动 UI（uiautomator）

```
adb -s <serial> shell uiautomator dump /sdcard/ui.xml
adb -s <serial> pull /sdcard/ui.xml
grep -oE 'text="[^"]+"[^/]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' ui.xml
adb -s <serial> shell input tap <cx> <cy>
```

**小屏设备（手表）三坑**：
1. 屏幕几秒就灭——把唤醒、启动、所有点击串进**一条** shell 命令：
   `adb shell "input keyevent KEYCODE_WAKEUP; am start -n <pkg>/.MainActivity; sleep 2; input tap ..; sleep 2; input tap .."`
2. 表盘小组件可能**画在应用对话框之上**，截图会叠两层——以 `uiautomator dump` 为准。
3. dump 可能含 `AnimatedVisibility` 的过期节点（坐标点了没反应）——截图对照真实布局。

### 日志定位根因

```
adb -s <serial> logcat -c
# ...复现动作...
adb -s <serial> logcat -d -t 5000 > log.txt
```

必查模式：`FATAL|AndroidRuntime`（崩溃）、`WM-WorkerWrapper.*failed`（WorkManager
任务崩溃=数据被丢）、`onReceive <action>`（广播是否到达）、按 tag 过滤
（PUMPQUEUE/DATABASE/PUMP 默认开）。用户描述的现象经常与日志中的真实根因不同——
先 grep 再下结论。

### 读偏好文件（"应用到底认为什么"）

- debug 构建：`adb shell run-as <pkg> cat shared_prefs/<pkg>_preferences.xml | grep <key>`
- release 构建不可 run-as：`adb backup -noapk <pkg> -f backup.ab`（设备上要点确认，
  屏幕保持唤醒；.ab 去掉 24 字节头后 zlib 解压即 tar，取出 shared_prefs XML）。
- 改偏好文件风险高：运行中的进程会用内存态整份写回（编辑可能被静默还原），必须
  `am force-stop` 确认进程死透（`pidof` 为空）再改，改完重启后复查。

---

## 第 10 步：提交代码

按主题分 commit（适配 / 权限 / 各功能 / 省电 / 签名设施），每个提交信息说明"为什么"。
`.claude/tmp` 的会话临时文件不跟踪；密钥文件已在 .gitignore。

---

## 本次实战的修改清单（对照用）

| 主题 | 关键文件 |
|---|---|
| minSdk 27 + API 31+ 保护 | `buildSrc/.../Versions.kt`、InsightAlertService、DummyService(Helper)、AlarmNotificationManager、PluginPermissionsImpl、ReminderSchedulerImpl、eopatch AlarmRegistry |
| 蓝牙权限按版本 | `core:interfaces/.../BluetoothPermissions.kt`（新）、BluetoothPermission.android.kt、18 个检查点、app manifest |
| 丹纳 RS 清除密钥 | DanaIntentKey、IntentPreferenceKey(withClick+confirmation)、DanaRSPlugin、BLEComm（wrong-serial 不再自动清密钥） |
| 目标批量通过 | ObjectivesViewModel/Screen/ComposeContent/UiState、NotificationId 追加 |
| 8.1 搜索卡死 | `ui/.../search/SearchBar.kt`（非激活态不渲染输入框） |
| 菜单常驻 | `ui/.../main/MainScreen.kt`（删除 previewMode） |
| 蓝牙权限"永久拒绝" | BluetoothPermission.android.kt 版本分支（以下 12 请求定位权限） |
| 省电 | logback.xml ×2（AsyncAppender） |
| 全源 SMB | SourceSensorExtensions.kt（白名单解除，平线检测保留） |
| 大剂量密码独立验证 | ProtectionCheckImpl（严格按级取凭证）、UnifiedAuthDialog（按级提示语）、ProtectionHost（生物识别恢复） |
| 欧态血糖丢数据 | Inbox.kt（恢复全版本 setExpedited）+ LoggingWorker（实现 getForegroundInfo，含静默通知渠道创建） |
| 导出无 .json 后缀 | FileListProviderImpl.newPreferenceFile（display name 显式带后缀） |
