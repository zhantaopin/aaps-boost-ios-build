# 官方 AndroidAPS 4.0.0 — 按 SKILL.md 全流程适配计划（含 3 个附加功能默认开启）

## 仓库调研结论（执行 SKILL.md 第 0-4 步之前先摸实底）

| 项 | 值 |
|-----|-----|
| 工作根目录 | `/home/kylin/桌面/AAPS/AndroidAPS-official-4.0.0/` |
| 源码形态 | zip 解压产物，**不是 git 仓库**（无 `.git`） |
| 嵌套子目录 | 有一个多余的 `AndroidAPS-4.0.0/` 子目录（180 MB，同一份源码镜像），**执行前删除** |
| minSdk | `buildSrc/.../Versions.kt` 里 `minSdk = 31`, `compileSdk = 37`, `targetSdk = 35` |
| wearMinSdk | 30（适配 API 27 时一并改 27） |
| Kotlin / Compose MP / AGP | 2.4.21 / 1.12.2 / 9.5.0（比 SKILL.md 实战用的 beta1 新） |
| 4.0.0 KMP 重构 | `SecureEncryptImpl` 里 **VERSION_CODES.P(28) 门控已随 KMP 重构消失**（SKILL.md 的隐藏坑在此版本不再是坑） |

### VERSION_CODES 分布（快速 grep 初扫，仅非 build 目录）

| VERSION_CODES | API | 典型文件 | 门槛 >27? |
|---|---|---|---|
| CINNAMON_BUN | 37 | `PersistentNotificationPlugin.kt:426` | ✅ |
| BAKLAVA | 36 | `PersistentNotificationPlugin.kt:421`, `ThreadUtil.kt:17` | ✅ |
| VANILLA_ICE_CREAM | 35 | `omnipod/common/.../LegacyBleDeviceManager.kt` | ✅ |
| UPSIDE_DOWN_CAKE | 34 | `AlarmSoundPlayerImpl.kt:129` | ✅ |
| TIRAMISU | 33 | MainApp / ConfigImpl / PluginPermissionsImpl / AlarmNotificationManager / AndroidSystemNotificationPlatform / IntentExtension / BluetoothAdapterExtension / PackageManagerExtension / Dana+Carelevo BLE / AndroidBluetoothInterface / WatchFaceComplication / ConfigurationActivity | ✅ |
| O | 26 | DummyServiceHelper / DataLayerListenerServiceMobileHelper / LocationServiceControllerImpl | ❌ |
| P / S | 28 / 31 | **4.0.0 KMP 重构后 S(31) 和 P(28) 的直接 VERSION_CODES 引用消失**；但蓝牙权限 API 31 专属语义没变 | — |

### 高风险 API 符号（lint 扫不到或易漏）

| 符号 | API | 位置 | 性质 |
|---|---|---|---|
| `VibratorManager` / `VIBRATOR_MANAGER_SERVICE` | 31 | `pump/insight/InsightAlertService.kt` | **编译级隐藏坑**：直接 import，老系统上编译不过 |
| `ForegroundServiceStartNotAllowedException` | 31 | `app/.../DummyService.kt`, `DummyServiceHelper.kt`（4 处 catch） | **异常类在 API 27 上不存在**，catch 父类 + 版本判断内引类 |
| `NotificationChannelGroup` | 26 | `AlarmNotificationManager.kt:181` | 27 上虽能编译但行为不一致，需 VERSION_CODES.O 门控 |
| `canScheduleExactAlarms()` | 31 | AlarmNotificationManager / PluginPermissionsImpl / AndroidEatReminderAlarm / ReminderSchedulerImpl | **API 31 以下方法不存在**，需 SDK_INT ≥ S 前置 |
| `setExpedited` | 31 | `core/utils/.../Inbox.kt` | **保留 setExpedited**，Worker 基类实现 getForegroundInfo |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` | 31 | 全仓 20+ 检查点 | **版本映射**：27 上查 BLUETOOTH + FINE_LOCATION |

### 附加功能来源（fork 在 beta1 基础上，手动移植到 4.0.0 正式版）

| 功能 | fork commit | 改动范围 | 复杂度 |
|---|---|---|---|
| SMB 放宽（解除 CGM 白名单） | `1efc0d5` | `core/data/.../SourceSensorExtensions.kt` 1 文件 | 🟢 低 |
| FixedOffsetPlugin（固定校准） | `14dd333` | **44 文件**，新增 3 个 KMP 文件 + 改 15 个 source worker + 设置 + strings + settings.gradle | 🔴 高 |
| 一键全部目标学习 | **新增，无 commit** | `plugins/constraints/.../ObjectivesPlugin.kt` + `ObjectivesViewModel.kt` + `ObjectivesComposeContent.kt`（改现有） | 🟡 中 |

---

## 计划总览

```
Phase 0  清理 + git 基线                    ← SKILL.md 第 0 步（额外：删嵌套子目录）
Phase 1  影响面评估（lint 探针 + 手动 grep） ← SKILL.md 第 1 步（产出完整改动清单）
Phase 2  API 门控 + 蓝牙权限映射             ← SKILL.md 第 2 步
Phase 3  中间 lint 验证                      ← SKILL.md 第 3 步
Phase 4  老 Android 踩坑修复                ← SKILL.md 第 4 步
Phase 5  功能：SMB 放宽 + FixedOffsetPlugin + 一键目标学习（**全部开启**）
Phase 6  省电优化（logback AsyncAppender）   ← SKILL.md 第 6 步
Phase 7  生产测试矩阵                       ← SKILL.md 第 7 步
Phase 8  签名 release 构建                  ← SKILL.md 第 8 步
Phase 9  adb 装机 + 设备调试                 ← SKILL.md 第 9 步
Phase 10 提交代码                           ← SKILL.md 第 10 步
```

---

## Files and Modules

### Phase 0 — 清理 + 基线
- 删除 `AndroidAPS-4.0.0/` 嵌套子目录
- 仓库级 git config + `git init && add -A && commit "Baseline: official AndroidAPS 4.0.0 tag"`

### Phase 1 — 影响面评估
- `.claude/tmp/minsdk.init.gradle`（SKILL.md 1.1 内容，KMP + android 扩展）
- `./gradlew -I .claude/tmp/minsdk.init.gradle :app:lintFullDebug`
- 手动 grep（SKILL.md 1.2）扫 VERSION_CODES 分布 + 高风险符号 + KMP 盲区
- **产出改动清单**：每个 VERSION_CODES > 27 + 每个高风险符号 + 每个蓝牙权限检查点

### Phase 2 — API 门控 + 蓝牙权限映射

**配置层**
- `buildSrc/src/main/kotlin/Versions.kt`：`minSdk 31→27`, `wearMinSdk 30→27`
- 所有 Manifest：蓝牙权限加 `android:maxSdkVersion="30"` + 补 BLUETOOTH / BLUETOOTH_ADMIN / FINE_LOCATION / COARSE_LOCATION

**编译级隐藏坑（lint 探针第一个发现）**
- `pump/insight/.../InsightAlertService.kt`：`VibratorManager` → VERSION_CODES.S 门控；27 上走 `VIBRATOR_SERVICE` + `Vibrator`
- `app/.../DummyService.kt` + `DummyServiceHelper.kt`：catch `ForegroundServiceStartNotAllowedException` → 改用 catch 父类 `RuntimeException`，块内 `if (SDK_INT >= S && e is ForegroundServiceStartNotAllowedException)`（**类引用放在版本判断内**）

**行为级门控（已编译但行为不一致）**
- `AlarmNotificationManager.kt:181`：`NotificationChannelGroup` → VERSION_CODES.O 门控，27 上跳过 group（直接建子渠道）
- `AlarmNotificationManager.kt`, `PluginPermissionsImpl.kt`（2 处）, `AndroidEatReminderAlarm.kt`, `ReminderSchedulerImpl.kt`：`canScheduleExactAlarms()` → 加 `SDK_INT >= S && !am.canScheduleExactAlarms()` 前置（**S 以下默认允许**）
- PersistentNotificationPlugin / ThreadUtil / IntentExtension / BluetoothAdapterExtension / PackageManagerExtension / LegacyBleDeviceManager / AndroidSystemNotificationPlatform / CarelevoBleTransportImpl / CarelevoObserveReceiver / AndroidBluetoothInterface / MainApp / ConfigImpl / WatchFaceComplication / ConfigurationActivity —— lint 探针确认 VERSION_CODES 33-37 是否已保护

**蓝牙权限全映射（SKILL.md 2.3）**
- `core/ui/.../BluetoothPermissionsHost.kt`：替换 31+ 硬编码
- `core/interfaces/.../BluetoothPermission.android.kt`：替换 31+ 硬编码
- 所有 pump BLE 驱动检查点（Dana danars/danar BLEComm + RealRfcommTransport + BleTransportImpl、combov2 comboctl AndroidBluetoothInterface 等）
- `PluginPermissionsImpl.kt` 权限向导蓝牙权限组：版本分支，27 上申请 BLUETOOTH + FINE_LOCATION（**不要**申请 BLUETOOTH_CONNECT，会被系统永久拒绝）

**JVM 测试缝隙**
- 所有涉及门控的类加 `internal var`（默认值即版本判断结果），让 `SDK_INT == 0` 的 JVM 测试也能测新系统路径（参考 SKILL.md 2.2 里 `AlarmNotificationManager.exactAlarmsNeedPermission`）

### Phase 3 — 中间 lint 验证
- `./gradlew :app:lintFullDebug --no-daemon`
- **判据**：无 `NewApi` / `ObsoleteSdkInt` Error；还有 → 回 Phase 2 补漏
- KMP androidMain 盲区再跑一遍手动 grep 确认

### Phase 4 — 老 Android 踩坑修复
- SAF `createFile` 显式带扩展名（`FileListProviderImpl.newPreferenceFile`）
- WorkManager 三步走：**保留** Inbox.setExpedited；新建 Worker 基类（如 `LoggingWorker` 或挂到 `core/utils` 里）实现 `getForegroundInfo()` 返回 IMPORTANCE_MIN 静默通知 + 渠道现场创建；**永远不降级成普通任务**
- `::class.simpleName` 用法扫（4.0.0 已 KMP 化，SKILL.md 5 步提到可能存在）
- Compose 搜索框卡死 —— **需 API 27 设备验证**（Phase 3 lint 通过后再看）

### Phase 5 — 附加功能（**默认全部开启**）

#### 5.1 SMB 放宽（fork commit `1efc0d5`）
- 改动：`core/data/.../SourceSensorExtensions.kt` 里 `advancedFilteringSupported()` 返回 true，`needsFlatBgCheck` 保留
- 4.0.0 KMP 重构后路径可能已变，Phase 1 grep 定位实际位置

#### 5.2 FixedOffsetPlugin（fork commit `14dd333`，**44 文件手动移植**）
> 注意：fork 基于 beta1，settings.gradle / core:keys / calibration 模块结构在 4.0.0 正式版可能已有变化，不直接 git cherry-pick，按 diff 手动移植。

- **新增 KMP 文件**（插件主体）：
  - `plugins/calibration/src/commonMain/.../FixedOffsetPlugin.kt`（76 行）
  - `plugins/calibration/src/commonMain/.../compose/FixedOffsetViewModel.kt`（42 行）
  - `plugins/calibration/src/commonMain/.../compose/FixedOffsetComposeContent.kt`（38 行）
  - `plugins/calibration/src/commonMain/.../compose/FixedOffsetScreen.kt`（140 行）
  - `core/keys/.../CalibrationOffsetKeys.kt`（24 行，从 calibration 移到 core:keys 避免 source→calib dep）
- **改 settings.gradle**：确认 calibration 模块 include（4.0.0 里 `plugins/calibration` 目录已存在，大概率已 include）
- **改 calibration 模块现有文件**：LinearCalibrationPlugin（剥离 UserOffset 耦合）、CalibrationComposeContent / CalibrationViewModel（加 FixedOffset tab）
- **新增 UserOffsetHelper**：`plugins/source/.../UserOffsetHelper.kt`（28 行）—— 源头写 DB 时应用 offset
- **改 15 个 BGSource Worker**：Aidex / Dexcom / Glimp / Glunovo / Intelligo / MM640g / PatchedSiApp / PatchedSinoApp / Poctech / RandomBg / Syai / Tomato / Xdrip / Instara / NotificationCollectorService —— 接线 UserOffsetHelper
- **strings.xml**：英文 + 中文（固定偏移 / 线性校准 / 无需校准）
- **改 ios probe + build.gradle.kts**（fork diff 里有 IosProbeGraph + calibration build.gradle.kts 小改动）
- **测试**：UserOffsetHelperTest.kt 新增 85 行；15 个 WorkerTest 小改动（适配）
- **金标文件**：`app/src/testFull/resources/prefs-schema.txt` 追加 FixedOffsetPlugin 新偏好 key

#### 5.3 一键全部目标学习（**新增功能**）
- 位置：`plugins/constraints/src/commonMain/kotlin/app/aaps/plugins/constraints/objectives/`
- **ObjectivesPlugin.kt**：新增 `fun learnAllObjectives()`（遍历所有 objectives.tasks，所有 task.learned RH.putBoolean(true)；注意 Exam answered 也要一并写）
- **ObjectivesViewModel.kt**：新增 `fun onLearnAll()` + 确认对话框触发
- **ObjectivesComposeContent.kt**：在现有 reset 按钮旁加"一键学习全部目标"按钮（或菜单项）
- **安全红线**：弹出确认对话框（"将跳过所有新手引导，包括闭环前置学习。确认？"）+ 隐藏开关 `developer_skip_objectives` 避免普通用户误点
- 可选：额外一键完成（accomplishedOn 时间戳 + 跳过 Exam），先做一键学习

### Phase 6 — 省电优化
- `find . -name "logback*.xml" -not -path "*/build/*"`：app + wear 两份
- 加 `AsyncAppender` 包裹（`neverBlock=true`, queue 4096）

### Phase 7 — 生产测试矩阵
- **7.1 Lint 生产复查**（Phase 3 已跑过的最终回归）
- **7.2 JVM 全量单测**（SKILL.md 7.3 命令，`--continue` 必加；记录 baseline failures vs 新引入）
- **7.3 Instrumented 测试**（SKILL.md 7.4，API 27 真机或模拟器）
- **7.4 长稳验证**（SKILL.md 7.5 脚本 6-12h；Doze whitelist + disable + stayon）
- **7.5 多 flavor Release 构建**（full / aapsclient / pumpcontrol / aapsclient2 / aapsclient3）
- **7.6 Checklist**（SKILL.md 7.8）

### Phase 8-10 — 签名 + adb + 提交
- 签名 release（keytool 生成 jks + keystore.properties + .gitignore + gradle 配置 + apksigner 验签）
- adb 装机（uiautomator dump + 权限预授 + 日志定位 + 偏好文件读取）
- 按主题分 commit（清理 / minSdk + 配置 / 编译级坑 / 门控 + 权限 / 蓝牙映射 / 踩坑 / SMB 放宽 / FixedOffsetPlugin / 一键目标学习 / 省电 / 签名设施）

---

## Dependencies and Considerations

- **Kotlin 2.4.21 + AGP 9.5.0**：比 SKILL.md 实战环境新，`kotlin.android` minSdk 配置方式 Phase 1 lint 探针先确认
- **Compose Multiplatform 1.12.2**：4.0.0 UI 大量重写，lint 探针是验证依赖图在 minSdk 27 下能合并的唯一办法
- **compileSdk 37** —— 不要降，编译时仍能看到所有 API 常量
- **JVM 单测 `SDK_INT == 0`** —— 所有门控代码要加 `internal var` 测试缝隙
- **FixedOffsetPlugin 风险**：4.0.0 calibration 模块 KMP 化后文件位置和 settings.gradle include 方式已变，先按 diff 手动移植，不直接 cherry-pick
- **iOS 构建**：FixedOffsetPlugin 改动触及 `IosProbeGraph.kt`，如果机器没配 Xcode 可以不跑 iOS 验证（Linux 上也跑不了）

---

## Validation（每步通过判据）

| Phase | 判据 |
|---|---|
| 0 | git log 有 "Baseline" commit；仓库里没有嵌套 `AndroidAPS-4.0.0/` |
| 1 | lint 探针跑完 + grep 脚本跑完；产出完整"改动清单"（预计 30-40 项门控 + 20+ 蓝牙检查点） |
| 3 | `grep -E ": Error " lint-mid.log` 空；KMP 盲区 grep 空 |
| 4 | SAF 导出的 `.json` 文件有扩展名；WorkManager 后台收数据无延迟 |
| 5 | FixedOffsetPlugin `:plugins:calibration:compileAndroidMain` BUILD SUCCESS；`advancedFilteringSupported()` 返回 true；一键学习按钮出现且点了能触发 |
| 7.1 | 同 Phase 3 判据 |
| 7.2 | `BUILD SUCCESSFUL` exit 0；baseline failures 已记录 |
| 7.3-7.4 | LoopTest / SetupWizardE2ETest 等 PASS；长稳进程存活 + treatments 持续增长 |
| 7.5 | 所有 flavor APK 在 `app/build/outputs/apk/<flavor>/release/` 生成 |
| 7.6 | checklist 全打勾 |

---

## Risks

| 风险 | 概率 | 处理 |
|---|---|---|
| Compose Multiplatform 1.12.2 有依赖硬卡 minSdk > 27 | 中 | Phase 1 lint 探针第一个发现；先跑探针再动手改 minSdk |
| 4.0.0 KMP 重构后蓝牙权限检查点位置大变 | 高 | Phase 1 grep 全扫；逐个映射，一个都不能漏（泵静默拒绝工作 = 医疗风险） |
| `ForegroundServiceStartNotAllowedException` 在 27 上**编译**不过 | 高 | Phase 2.3 按 SKILL.md 2.2 节"catch 父类 + 版本判断内引类"写法；lint 探针第一时间发现 |
| Worker 基类在 4.0.0 里不是单一一个 | 中 | Phase 1 grep `getForegroundInfo\|setExpedited\|class.*Worker` 定位；没有基类就新建一个放在 `core:utils` 里 |
| FixedOffsetPlugin 44 文件移植在 4.0.0 和 beta1 之间有结构冲突 | **高** | 不 git cherry-pick；按 diff 手动移植，跑 `:plugins:calibration:compileAndroidMain` 验证 |
| SMB 放宽位置在 4.0.0 KMP 重构后变了 | 高 | Phase 1 grep `advancedFilteringSupported\|FilteringSupported\|SourceSensor` 定位实际位置 |
| 无 API 27 真机导致 Compose 搜索框卡死等只能在 8.1 上出现的坑发现不了 | 中 | 先完成代码级适配 + lint + JVM 单测；真机/模拟器 instrumented 推到有设备时再跑 |
