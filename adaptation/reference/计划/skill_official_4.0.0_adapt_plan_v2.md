# AndroidAPS 官方 4.0.0 minSdk 27 适配实施计划

> 执行根目录：`/home/kylin/桌面/AAPS/AndroidAPS-official-4.0.0/`
> 技能来源：`/home/kylin/桌面/AAPS/SKILL.md`（实战沉淀的 minSdk 27 适配全流程）
> 基线 commit：`9cb9b9f Baseline: official AndroidAPS 4.0.0 tag`
> 当前工作区：24 个文件已修改（Phase 2 前半段已完成）

---

## Repository Research

### 4.0.0 技术栈
- Kotlin 2.4.21 + Compose Multiplatform 1.12.2 + AGP 9.5.0 + Gradle 9.8.0
- compileSdk 37 **不降**；targetSdk 35 **保持**
- 架构大幅 KMP 化：calibration / constraints / source / data / utils / interfaces / ui 都有 commonMain

### minSdk 从 31→27 的影响面

**VERSION_CODES 实际分布（已 grep 确认）**：

| 门槛 | 次数 | 27 上需保护 |
|---|---|---|
| TIRAMISU (33) | 18 | ✅ |
| S (31) | 10 | ✅ |
| VANILLA_ICE_CREAM (35) | 2 | ✅ |
| BAKLAVA (36) | 2 | ✅ |
| UPSIDE_DOWN_CAKE (34) | 1 | ✅ |
| CINNAMON_BUN (37) | 1 | ✅ |
| O (26) | 4 | ❌（27>26，过） |
| **P (28)** | 0 | —（4.0.0 KMP 重构后消失） |

### 已完成改动（工作区 24 个 M 文件）
- `buildSrc/.../Versions.kt`：minSdk 31→27、wearMinSdk 30→27
- `app/build.gradle.kts`：注释 uncommitted changes 检查（line 304-307）
- `gradle/wrapper/gradle-wrapper.properties`：临时改 9.8.0（本地已装）
- 编译级隐藏坑：`InsightAlertService` / `DummyService` / `DummyServiceHelper`（VibratorManager / ForegroundServiceStartNotAllowedException）
- 行为级门控：`AlarmNotificationManager` / `PluginPermissionsImpl` / `AndroidEatReminderAlarm` / `ReminderSchedulerImpl`（NotificationChannelGroup / canScheduleExactAlarms）
- 蓝牙权限映射层：`BluetoothPermission.android.kt` / `BluetoothPermissionsHost.kt`（版本分支已写好）
- Manifest 批量修补：14 个 pump + core:utils + plugins:automation + plugins:configuration

### 尚未完成的改动

**Phase 2 剩余 — 蓝牙权限运行时检查点替换**（~30+ 处，扫描清单见后）
**Phase 3 — 中间 lint 验证**
**Phase 4 — 老 Android 坑修复**（LoggingWorker / SAF / SearchBar）
**Phase 5 — 功能改造**（SMB 放宽 / FixedOffsetPlugin / 一键目标学习）
**Phase 6 — 省电优化**
**Phase 7 — 生产测试矩阵**
**Phase 8-10 — 签名 + adb + 提交**

### 关键路径定位（已 find 确认）
- WorkManager 基类：`core/objects/src/androidMain/kotlin/app/aaps/core/objects/workflow/LoggingWorker.kt`
- SAF 导出：`implementation/src/androidMain/kotlin/app/aaps/implementation/maintenance/FileListProviderImpl.kt`
- setExpedited 唯一调用点：`core/utils/src/androidMain/kotlin/app/aaps/core/utils/receivers/Inbox.kt:140`（保留）
- SMB 放宽目标：`core/data/src/commonMain/kotlin/app/aaps/core/data/model/SourceSensorExtensions.kt`（已 KMP）
- Objectives KMP 路径：`plugins/constraints/src/commonMain/kotlin/app/aaps/plugins/constraints/objectives/`
- calibration KMP commonMain：`plugins/calibration/src/commonMain/kotlin/app/aaps/plugins/calibration/`

---

## Files and Modules

### Phase 2 剩余 — 蓝牙权限检查点（逐一替换，27 上查 BLUETOOTH/ACCESS_FINE_LOCATION 而非 BLUETOOTH_CONNECT/SCAN）

需要加 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` 门控的运行时查询：

**泵 transport / service 层**
- `pump/dana/danars/src/main/kotlin/app/aaps/pump/danars/services/BleTransportImpl.kt:183,298`
- `pump/dana/danars/src/main/kotlin/app/aaps/pump/danars/services/BLEComm.kt:101,158`
- `pump/dana/danar/src/main/kotlin/app/aaps/pump/danar/services/RealRfcommTransport.kt:34,52`
- `pump/carelevo/src/main/kotlin/app/aaps/pump/carelevo/ble/CarelevoBleTransportImpl.kt:164,169,175,181,205,264`
- `pump/equil/src/main/kotlin/app/aaps/pump/equil/ble/EquilBleTransportImpl.kt:139,144,150,156,180,241`
- `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/ble/MedtrumBleTransportImpl.kt:140,182,427,472,477,482,487,507,549`
- `pump/rileylink/src/main/kotlin/app/aaps/pump/common/hw/rileylink/ble/RileyLinkBLE.kt:172`
- `pump/rileylink/src/main/kotlin/app/aaps/pump/common/compose/RileyLinkPairWizardViewModel.kt:91`
- `pump/diaconn/src/main/kotlin/app/aaps/pump/diaconn/compose/DiaconnPairWizardViewModel.kt:91`
- `pump/diaconn/src/main/kotlin/app/aaps/pump/diaconn/service/BLECommonService.kt:94,125,148`
- `pump/insight/src/main/kotlin/app/aaps/pump/insight/connection_service/InsightConnectionService.kt:280`
- `pump/insight/src/main/kotlin/app/aaps/pump/insight/compose/InsightPairState.kt:167`
- `pump/omnipod/common/src/main/kotlin/app/aaps/pump/omnipod/common/bledriver/comm/legacy/LegacyBleDeviceManager.kt:36,49`

**combov2（有自建权限检查库）**
- `pump/combov2/comboctl/src/androidMain/kotlin/info/nightscout/comboctl/android/BluetoothPermissionChecks.kt` — 自建常量 + 权限查询
- `pump/combov2/src/main/kotlin/info/nightscout/pump/combov2/ComboV2Plugin.kt:351,675,1609` — 三处 permissionsToCheckFor

**测试文件（可选，按 SKILL.md 2.2 的 SDK_INT==0 缝隙思路）**
- `BleTransportImplScanPermissionTest` / `CarelevoBleTransportBondStateTest` / `RileyLinkPairWizardViewModelTest` / `DiaconnPairWizardViewModelTest`

### Phase 3 — lint 验证
- 无代码改动；跑 lint 命令 + grep Error

### Phase 4 — 老 Android 坑
- `core/objects/.../LoggingWorker.kt`（androidMain）：override `getForegroundInfo()` 返回 IMPORTANCE_MIN 静默通知 + 渠道现场创建
- `implementation/.../FileListProviderImpl.kt:185,191,197`：3 处 `dir.createFile(mime, displayName)` 的 displayName 显式带 `.json` / `.csv` / `.cwf` 后缀
- `ui/.../search/SearchBar.kt`：grep 定位后，非激活态不渲染输入框占位

### Phase 5.1 — SMB 放宽（单文件）
- `core/data/src/commonMain/kotlin/app/aaps/core/data/model/SourceSensorExtensions.kt`：`advancedFilteringSupported()` 返回 true；`needsFlatBgCheck` 保留
- fork diff 参考：`cd opencode-work/AndroidAPS-4.0-watch && git show 1efc0d5`

### Phase 5.2 — FixedOffsetPlugin（约 42 文件，手动移植不 cherry-pick）
**新增 KMP 文件**：
- `plugins/calibration/src/commonMain/.../FixedOffsetPlugin.kt`（~76 行，IntKey 720）
- `plugins/calibration/src/commonMain/.../compose/FixedOffsetViewModel.kt`（~42 行）
- `plugins/calibration/src/commonMain/.../compose/FixedOffsetComposeContent.kt`（~38 行）
- `plugins/calibration/src/commonMain/.../compose/FixedOffsetScreen.kt`（~140 行）
- `core/keys/src/commonMain/.../CalibrationOffsetKeys.kt`（~24 行，从 calibration 移到 core:keys 避免 source→calib dep）
- `plugins/source/src/commonMain/.../UserOffsetHelper.kt`（~28 行）

**改现有 calibration 文件**：
- `plugins/calibration/src/commonMain/.../LinearCalibrationPlugin.kt`（剥离 UserOffset 耦合）
- `plugins/calibration/src/commonMain/.../compose/CalibrationComposeContent.kt`（加 FixedOffset tab）
- `plugins/calibration/src/commonMain/.../compose/CalibrationViewModel.kt`

**接线 15 个 BGSource Worker**：
- `plugins/source/src/androidMain/.../AidexPlugin.kt` / `DexcomPlugin.kt` / `GlimpPlugin.kt` / `GlunovoPlugin.kt` / `IntelligoPlugin.kt` / `MM640gPlugin.kt` / `PatchedSiAppPlugin.kt` / `PatchedSinoAppPlugin.kt` / `PoctechPlugin.kt` / `RandomBgPlugin.kt` / `SyaiPlugin.kt` / `TomatoPlugin.kt` / `XdripSourcePlugin.kt` / `instara/InstaraPlugin.kt` / `notificationreader/NotificationCollectorService.kt`

**资源 & 金标 & 测试**：
- `plugins/calibration/src/androidMain/res/values/strings.xml` + `values-zh-rCN/strings.xml`
- `app/src/testFull/resources/prefs-schema.txt` 追加新 key（排序保持）
- `plugins/source/src/androidHostTest/.../UserOffsetHelperTest.kt`（新增）
- 15 个 WorkerTest 适配

**fork diff 参考**：`cd opencode-work/AndroidAPS-4.0-watch && git show 14dd333`

### Phase 5.3 — 一键目标学习（4 文件）
- `plugins/constraints/.../ObjectivesPlugin.kt`：加 `fun learnAllObjectives()`
- `plugins/constraints/.../compose/ObjectivesViewModel.kt`：加 `onLearnAll()`
- `plugins/constraints/.../compose/ObjectivesComposeContent.kt`：加按钮/菜单项
- （可选）`developer_skip_objectives` 隐藏开关

### Phase 6 — 省电优化
- 2 个 logback XML：`find . -name "logback*.xml" -not -path "*/build/*"` 找到 app 和 wear 两份，加 AsyncAppender

### Phase 7 — 生产测试矩阵
- 无源码改动；跑 lint / JVM 单测 / Release 构建

### Phase 8 — 签名设施
- 新增 `app/release.jks`（keytool 生成）、`app/keystore.properties`、`app/build.gradle.kts` signingConfig、`.gitignore` 追加 `*.jks` / `keystore.properties`

---

## Implementation Steps

### Step 1 — 蓝牙权限运行时检查点替换（Phase 2 剩余）

**1.1 统一门控函数**：在 `core/interfaces/androidMain` 或 pump/common 的 androidMain 新建 `BluetoothPermissionCompat.kt`（已有的话直接复用），提供：
```kotlin
internal fun Context.bluetoothConnectGranted(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == GRANTED
    else ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == GRANTED

internal fun Context.bluetoothScanGranted(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == GRANTED
    else ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == GRANTED
```
（检查 `BluetoothPermission.android.kt` 是否已有等价能力，有就直接 call）

**1.2 批量替换**：对上面 Files 清单里每个 `Manifest.permission.BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` 的 `checkSelfPermission` 调用，改用上面 compat 函数；`@RequiresPermission` 注解同理（用 BLUETOOTH 或按版本分支，或删掉注解）。

**1.3 combov2**：其自建 `BluetoothPermissionChecks.kt` 走硬编码常量，加同样的版本分支。

**1.4 回归检查**：再跑一次 grep `BLUETOOTH_CONNECT|BLUETOOTH_SCAN`，看还有没有漏掉的非测试文件。

### Step 2 — Phase 3 中间 lint
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export KONAN_DATA_DIR=/home/kylin/桌面/AAPS/.konan
export ANDROID_HOME=/home/kylin/Android/Sdk
cd /home/kylin/桌面/AAPS/AndroidAPS-official-4.0.0
./gradlew :app:lintFullDebug --no-daemon --console=plain > /tmp/lint-mid.log 2>&1
grep -E ": Error " /tmp/lint-mid.log
```
判据：`grep` 输出空 → 进入 Step 3；非空 → 定位新 Error 回到 Step 1/Step 3 前加门控。

### Step 3 — LoggingWorker getForegroundInfo()
打开 `core/objects/.../LoggingWorker.kt`，override `CoroutineWorker.getForegroundInfo()`：
- 静态 channelId（例如 `"aaps_worker_expedited"`）
- 每次调用 `NotificationManager.getNotificationChannel(channelId)`，`== null` 就现场 `createNotificationChannel`（IMPORTANCE_MIN，无声音，锁屏隐藏）
- Notification 内容用低优先级简短文字
- 返回 `ForegroundInfo(NOTIFICATION_ID, notification)`，ID 必须唯一

**三条安全红线**（SKILL.md 第 4 步）：
1. ✅ `Inbox.kt:140` 的 `.setExpedited(...)` **保留**，不降级
2. ✅ 本步骤实现 `getForegroundInfo()`
3. ✅ **永远不要**为了省事把 setExpedited 删掉

### Step 4 — SAF createFile 显式扩展名
打开 `FileListProviderImpl.kt`，grep `createFile(` 的 displayName 参数：
- `createFile("application/json", timeLocal + "_" + config().FLAVOR)` → 改成 `(..., config().FLAVOR + ".json")`
- `createFile("application/csv", timeLocal + "_UserEntry.csv")` → 已带后缀，不动
- `createFile("application/${CWF_EXTENSION}", filename)` → 显式带 `.cwf`

同文件其他 createFile 调用同样扫（MaintenanceImpl.kt / ImportExportPrefsImpl.kt 的那些也扫一下，确认 displayName 已带扩展名）。

### Step 5 — Compose 搜索框卡死（如果仍存）
grep 定位 SearchBar / search composable；如果发现非激活态仍渲染输入框，按 M3 模式：非激活态只渲染一个可点击占位 Box，点击后才显示 TextField。

### Step 6 — SMB 放宽
直接按 fork `1efc0d5` 的改法改 `SourceSensorExtensions.kt`：
- `advancedFilteringSupported()` 返回 `true`
- `needsFlatBgCheck` 保留（医疗安全红线）

### Step 7 — FixedOffsetPlugin 手动移植
按 fork `14dd333` 的 diff 逐个拷贝/移植到 4.0.0 对应路径（不 git cherry-pick，beta1→4.0.0 KMP 结构大变直接 cherry-pick 必冲突）：
1. 先 `git show 14dd333` 拿到 patch 内容
2. `CalibrationOffsetKeys.kt` 先写（在 core:keys）
3. 然后 `UserOffsetHelper.kt`（commonMain，零 Android API）
4. 然后 FixedOffsetPlugin 主体 4 个文件
5. 再改 LinearCalibrationPlugin / CalibrationComposeContent / CalibrationViewModel
6. 最后 source Worker 接线（每个 Worker 写完 DB 后 `UserOffsetHelper.applyTo(...)`）
7. strings.xml 追加英文 + 中文
8. prefs-schema.txt 追加 key
9. 新增 UserOffsetHelperTest + 适配 15 个 WorkerTest

### Step 8 — 一键目标学习
4 个文件：
1. `ObjectivesPlugin.learnAllObjectives()`：遍历 `objectives.tasks`，每个 task.learned 写 true，考试 answered 一并写
2. `ObjectivesViewModel.onLearnAll()`：调 plugin + 发 `EventObjectivesUpdateGui`
3. `ObjectivesComposeContent`：reset 按钮旁加按钮（菜单项也行），弹确认对话框
4. （可选）`developer_skip_objectives` 隐藏 key + 守卫

### Step 9 — 省电优化
```bash
find . -name "logback*.xml" -not -path "*/build/*"
```
app + wear 两份，原有 `<appender>` 外层包一层 `<appender name="async..." class="ch.qos.logback.classic.AsyncAppender"><queueSize>4096</queueSize><neverBlock>true</neverBlock><appender-ref ref="原name"/></appender>`。

### Step 10 — 生产测试矩阵
```bash
# 10.1 Lint 最终
./gradlew :app:lintFullDebug --no-daemon > /tmp/lint-final.log 2>&1
grep -E ": Error " /tmp/lint-final.log
# 10.2 JVM 单测
./gradlew --console=plain --no-daemon --continue -PfirebaseDisable \
  testFullDebugUnitTest testDebugUnitTest jvmTest testAndroidHostTest :desktop:shell:test \
  > /tmp/jvm-test.log 2>&1
# 10.3 Release 构建
./gradlew --no-daemon -PfirebaseDisable \
  assembleFullRelease assembleAapsclientRelease assemblePumpcontrolRelease \
  assembleAapsclient2Release assembleAapsclient3Release \
  > /tmp/release-build.log 2>&1
```

### Step 11 — 签名设施（用户拍板后执行）
```bash
keytool -genkeypair -keystore app/release.jks -alias release -keyalg RSA -keysize 4096 \
  -validity 10950 -storepass "<pass>" -keypass "<pass>" -dname "CN=..."
```
+ `keystore.properties` + `app/build.gradle.kts` signingConfig + `.gitignore` 追加 `*.jks` / `keystore.properties`

### Step 12 — adb 装机 + 提交（用户拍板后执行）

---

## Dependencies and Considerations

1. **SKILL.md 是唯一写法权威**：canScheduleExactAlarms / ForegroundServiceStartNotAllowedException / 蓝牙权限映射 三条的写法必须严格按 SKILL.md 2.2 节，不能自创。
2. **KMP 模块任务名不同**：`:mod:compileAndroidMain` / `:mod:testAndroidHostTest` / `:mod:jvmTest`（SKILL.md 7.1 节）。
3. **compileSdk 不降**（仍 37），否则新 API 常量消失直接编译不过；targetSdk 35 保持。
4. **Gradle wrapper 9.8.0**：已改 wrapper properties，最终 commit 时要么恢复要么保留（看用户）。
5. **app/build.gradle.kts 的 uncommitted changes 检查**：line 304-307 保持注释状态（否则每个构建都会被 block）。
6. **Worker setExpedited 红线**：Inbox.kt 的 setExpedited 保留 + getForegroundInfo 实现到位 + 永远不降级成普通任务。
7. **JVM 单测里 SDK_INT == 0**：门控代码如果走 SDK_INT 判断，加 `internal var` 测试缝隙（参考 SKILL.md 2.2 节 AlarmNotificationManager.exactAlarmsNeedPermission 写法）。
8. **FixedOffsetPlugin 不 cherry-pick**：fork 是 beta1 结构，4.0.0 calibration 已 KMP 化，路径虽基本对齐但直接 cherry-pick 必冲突；按 diff 手动移植。
9. **SMB 改的是剂量安全**：`advancedFilteringSupported()` 返回 true 解除 CGM 白名单 — 虽然是功能改造但影响胰岛素决策，用户在 Summary 里已要求但仍要在实施前再确认一次。
10. **Combov2 有自建权限检查库**：不能只改 ComboV2Plugin.kt 的三处 `permissionsToCheckFor`，其底层 `BluetoothPermissionChecks.kt` 也要加版本门控。
11. **多 flavor Release 构建**：主控 + client + pumpcontrol 五 flavor 都要过（SKILL.md 7.6 节，客户端不重建会行为不一致）。

---

## Validation

| 阶段 | 命令 | 通过判据 |
|---|---|---|
| Phase 3 中间 lint | `./gradlew :app:lintFullDebug` | `grep -E ": Error " lint-mid.log` 空 |
| Phase 7.2 最终 lint | 同上 | 同上 |
| Phase 7.3 JVM 单测 | 见 Step 10.2 | `BUILD SUCCESSFUL` exit 0；区分 baseline vs 新引入 failure |
| Phase 7.5 Release | 见 Step 10.3 | 所有 flavor BUILD SUCCESSFUL |
| Phase 7.4 Instrumented | `:app:connectedFullDebugAndroidTest`（需 Android 8.1 真机/模拟器） | LoopTest / SetupWizardE2ETest 等 PASS |
| Phase 7.6 长稳 | adb install + Doze whitelist + 6-12h | 进程存活、treatments 持续增长、无 FATAL/ANR |
| Phase 7.8 人工检查 | grep VERSION_CODES + BLUETOOTH_CONNECT 扫描 | 无裸露（未加门控）的高版本 API |

---

## Risks

| 风险 | 处理 |
|---|---|
| 蓝牙权限替换后某泵模块仍静默拒连 | 先在 API 27 模拟器上逐个泵测；若仍拒，grep 该 pump 模块的 `BLUETOOTH_` 全部引用看还有漏网之鱼 |
| JVM 单测因 SDK_INT==0 门控走错分支 | 关键路径（蓝牙权限、setExpedited 分支判断）加 `internal var` 测试缝隙 |
| 6 个 O+ 权限的 ForegroundServiceStartNotAllowedException 写法不一致 | DummyService / DummyServiceHelper / InsightAlertService 三处已按 SKILL.md 写好（catch RuntimeException 父类 + 版本判断内引类）；新增同样场景严格照搬 |
| LoggingWorker.getForegroundInfo() 渠道重复创建 / 并发崩溃 | 渠道创建加 `synchronized` 或 `runBlocking` 锁；渠道已存在直接 return 不重建 |
| Saf 厂商差异（已加 .json 后缀但某些厂商仍不认） | 保留 createFile 扩展名显式追加 + 导入侧兜底：找不到时扫描同目录按时间戳匹配 |
| FixedOffsetPlugin 移植时 core:keys / calibration / source 三模块依赖环 | 先写 core:keys 的 CalibrationOffsetKeys → 再写 UserOffsetHelper（commonMain，零 calib 依赖）→ 再写 calibration 主体 → 最后接 source Worker |
| compileSdk 37 环境缺 Android 15/16/17 最新 platform | 只需 platform 34+，更高版本仅 CINNAMON_BUN(37) 一个门控引用；确认本机 SDK 装的 platform 版本是否够 |
| Gradle 9.8.0 运行时新 KMP DSL 冲突（wrapper properties 临时改） | 实施前先 `./gradlew --version` 验证能启动；若 9.8.0 也超时回退到 9.6.x |
| 签名阶段密钥丢失 / 换密钥导致装机清数据 | .gitignore 已忽略 jks + 必须本地备份；装机前先 backup.apk |

---

## 执行顺序总结（依赖顺序）

```
Phase 2 剩余（蓝牙权限检查点替换）
    ↓
Phase 3 中间 lint ──→ 若红，补漏后重跑
    ↓
Phase 4 老 Android 坑（LoggingWorker / SAF / SearchBar）
    ↓
Phase 5.1 SMB 放宽（单文件，快）
    ↓
Phase 5.2 FixedOffsetPlugin（42 文件，最肥）
    ↓
Phase 5.3 一键目标学习（4 文件）
    ↓
Phase 6 省电优化（logback）
    ↓
Phase 7 生产测试矩阵（lint / JVM / Release 三跑）
    ↓ （用户拍板）
Phase 8 签名设施
    ↓ （用户拍板）
Phase 9 adb 装机 + 调试
    ↓ （用户拍板）
Phase 10 按主题分 commit
```
