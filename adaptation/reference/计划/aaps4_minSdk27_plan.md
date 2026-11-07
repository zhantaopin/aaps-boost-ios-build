# 官方 AndroidAPS 4.0.0 正式版 — 完整适配计划 v2

## 目标清单（全部要做）

| # | 目标 | 性质 | 来源 |
|---|------|------|------|
| **1** | minSdk 27 代码级适配（API 门控 + 权限映射） | 代码改动 | SKILL.md 第 1-4 步 |
| **2** | 双设备支持：API 27 手机 + API 28（Android 9）M53P 手表 | 代码 + 配置 | 设备级适配 Skill |
| **3** | Cherry-pick 附加功能：SMB 放宽（解除 CGM 白名单） | 代码移植 | 你 fork 的 commit `1efc0d5` |
| **4** | Cherry-pick 附加功能：FixedOffsetPlugin（固定校准插件） | 代码移植 | 你 fork 的 commit `14dd333` |
| **5** | **新增"一键全部目标学习"功能** | 新功能开发 | 你提出的需求 |
| **6** | 生产级测试矩阵 | 验证 | SKILL.md 第 7 步 |

---

## 仓库基线

| 项 | 值 |
|-----|-----|
| 官方仓库 | https://github.com/nightscout/AndroidAPS.git |
| 目标 tag | `4.0.0` (f77abd9b) |
| 工作目录 | `/home/kylin/桌面/AAPS/AndroidAPS-4.0.0-official/` |
| JDK | OpenJDK 21（已验证） |
| Kotlin | 需确认 4.0.0 版本 |
| Compose MP | 需确认 4.0.0 版本 |
| Gradle | wrapper 自动下载 |

### 你 fork 的附加功能（要 cherry-pick 的两个 commit）

```
1efc0d5  fix: SMB always enabled 解除 CGM 品牌白名单限制
14dd333  feat: FixedOffsetPlugin — independent CALIBRATION plugin for manual BG offset
```

---

## Phase 0：拉官方 4.0.0 + 建立基线

```bash
cd /home/kylin/桌面/AAPS
git clone --depth=1 --branch 4.0.0 https://github.com/nightscout/AndroidAPS.git AndroidAPS-4.0.0-official
cd AndroidAPS-4.0.0-official
git add -A && git commit -m "Baseline: official AndroidAPS 4.0.0 tag"
```

**兜底**：GitHub 拉不动时用 Gitee 镜像或手动下载 zip。

---

## Phase 1：影响面评估（SKILL.md 第 1 步，lint 探针 + 手动 grep）

### 1.1 lint 探针

```bash
mkdir -p .claude/tmp
# 写入 minsdk.init.gradle（SKILL.md 已有内容）
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export KONAN_DATA_DIR=/home/kylin/桌面/AAPS/.konan

./gradlew -I .claude/tmp/minsdk.init.gradle :app:lintFullDebug --no-daemon > /tmp/aaps4-lint1.log 2>&1
find app/build -name "lint-results-*txt" | head -1 | xargs cat 2>/dev/null
grep -E ": Error " /tmp/aaps4-lint1.log
```

### 1.2 手动 grep 扫 KMP androidMain 盲区

```bash
# VERSION_CODES 全量统计
grep -rn --include="*.kt" --include="*.java" -oE "VERSION_CODES\.[A-Z_]+" . --exclude-dir=build | sort | uniq -c | sort -rn

# 高风险 API 符号
grep -rn --include="*.kt" -E "canScheduleExactAlarms|VibratorManager|ForegroundServiceStartNotAllowedException|FOREGROUND_SERVICE_TYPE|android\.window\.|NotificationChannelGroup" . --exclude-dir=build
```

### 1.3 产出改动清单

对照 VERSION_CODES 门槛表（O=26, P=28, S=31, TIRAMISU=33, UPSIDE_DOWN_CAKE=34, VANILLA_ICE_CREAM=35, BAKLAVA=36, CINNAMON_BUN=37），标记所有 API > 27 的位置。

**预期**：~30-40 处 VERSION_CODES 门控 + 蓝牙权限映射助手 + 隐藏坑（VibratorManager、NotificationChannelGroup、VERSION_CODES.P）。

---

## Phase 2：API 门控修复 + 蓝牙权限映射（SKILL.md 第 2 步）

按 Phase 1 清单逐个改。你 fork 里已改好的代码可以作为模板参考：

| 改动 | 模板来源 |
|------|---------|
| VERSION_CODES.S 门控 | `plugins/calibration/FixedOffsetScreen.kt` 里你改的 `formatSign` |
| `::class.simpleName` 替代 `javaClass.simpleName` | `plugins/source/UserOffsetHelper.kt` |
| 蓝牙权限映射助手 | 2.3 节内容，BluetoothPermissionsHost.kt |
| Worker `getForegroundInfo()` 三步走 | LoggingWorker 基类 |
| SAF createFile 显式带扩展名 | FileListProviderImpl.newPreferenceFile |

### 关键认知提醒（每次改代码前默念）

- **API 27 上 `SDK_INT` 永远 < 31** → 蓝牙权限永远走 BLUETOOTH + FINE_LOCATION 分支
- **API 27 也 < 28（P）** → SecureEncryptImpl 里 VERSION_CODES.P 也要门控
- **JVM 单测里 SDK_INT == 0** → 门控代码要加 `internal var` 测试缝隙

---

## Phase 3：中间 lint 验证（SKILL.md 第 3 步）

```bash
./gradlew :app:lintFullDebug --no-daemon > /tmp/aaps4-lint-mid.log 2>&1
grep -E ": Error " /tmp/aaps4-lint-mid.log
```

**通过判据**：无 `NewApi` / `ObsoleteSdkInt` Error。还有 → 回 Phase 2 补漏。

---

## Phase 4：老 Android 踩坑修复（SKILL.md 第 4 步）

### 4.1 必查 6 坑 + 3 隐藏坑

| 坑 | 检查方法 |
|----|---------|
| Compose 搜索框卡死 | API 27 模拟器上试 |
| WorkManager getForegroundInfo | grep `getForegroundInfo\|setExpedited` |
| 数据延迟几分钟 | 确认没有降级成普通任务 |
| 权限查询恒 DENIED | Manifest 权限声明 + 版本映射 |
| SAF createFile 无扩展名 | grep `createFile` |
| 任务名找不到 | 7.1 任务名表 |
| VibratorManager | grep API 31 符号（Phase 1 已扫） |
| NotificationChannelGroup | grep（Phase 1 已扫） |
| VERSION_CODES.P | grep（Phase 1 已扫） |

### 4.2 两个 fork 附加功能 cherry-pick

**注意**：cherry-pick 在 beta1 和 4.0.0 之间可能有冲突——因为 4.0.0 做了 KMP 重构。
**策略**：不直接 `git cherry-pick`，而是**手动对比代码、手动移植**。

#### SMB 放宽（commit 1efc0d5）

```bash
# 看你 fork 里改了哪些文件
cd /home/kylin/桌面/AAPS/opencode-work/AndroidAPS-4.0-watch
git show 1efc0d5 --stat

# 在 4.0.0 里找对应位置
cd /home/kylin/桌面/AAPS/AndroidAPS-4.0.0-official
grep -rn "FilteringSupported\|needsFlatBgCheck\|SourceSensor" --include="*.kt" | grep -v build
```

改法（预期）：在 `SourceSensor.advancedFilteringSupported()` 里让它返回 true，解除 CGM 白名单限制。

#### FixedOffsetPlugin（commit 14dd333）

```bash
# 看你 fork 里这个插件的文件列表
git show 14dd333 --stat
# 大概率是 plugins/calibration/ 下新增文件
# 手动拷过去，然后在 settings.gradle.kts 里确认 calibration 模块已 include
```

### 4.3 新增功能：一键全部目标学习

**功能说明**：AndroidAPS 的 Objectives 系统有 10 个目标（Objective0-9），每个需要逐步学习/完成才能开某些功能。一键学习 = 把所有目标的所有任务标记为"已学习"。

#### 4.3.1 分析现有代码

```
ObjectivesPlugin.kt        → 主插件，有 objectives 列表、reset() 方法
ObjectivesViewModel.kt     → UI ViewModel，有 onReset()、onStart()
Objective.kt               → 单个目标基类
Objective0-9.kt            → 10 个具体目标
LearnedBottomSheet.kt      → "我学到了什么"底部弹窗
```

**关键发现**：
- `ObjectivesPlugin.reset()` 是"全部重置回零"
- 没有"一键学习全部"的现成方法
- Objectives 状态存在 SharedPreferences（从 ObjectivesBooleanComposedKey / ObjectivesLongComposedKey 推断）

#### 4.3.2 实现方案

在 **ObjectivesPlugin.kt** 里新增方法：

```kotlin
fun learnAllObjectives() {
    objectives.forEach { objective ->
        objective.tasks.forEach { task ->
            task.learned.forEach { rh.putBoolean(it.learned, true) }
        }
    }
}
```

在 **ObjectivesViewModel.kt** 里新增 UI 触发：

```kotlin
// 在 onReset() 旁边加
fun onLearnAll() {
    objectivesPlugin.learnAllObjectives()
    // 触发 UI 刷新
    rxBus.send(EventObjectivesUpdateGui())
}
```

在 **ObjectivesScreen.kt**（或设置页）里加一个按钮/菜单项：

```kotlin
// 在重置按钮旁边
Button(onClick = { viewModel.onLearnAll() }) {
    Text("一键学习全部目标")
}
```

**安全红线**（SKILL.md 第 1 条）：
> 胰岛素剂量安全相关的逻辑（SMB、泵重连、循环间隔）必须有用户明确确认。

Objectives 里有些是开循环的前置条件（Objective5 是闭环）。一键学习相当于跳过了这些安全前置。**实现时必须**：
1. 弹确认对话框："这将跳过所有新手引导，包括闭环前置学习。确认？"
2. 在 App 级设置里加一个隐藏开关：`developer_skip_objectives`（避免普通用户误点）
3. 在 SKILL.md 里加一条红线：**医疗部署不建议开启此功能**

#### 4.3.3 可选：目标也一键完成

如果用户想要更彻底（不止学习，还要把目标标记为 accomplished），需要额外处理：
- `Objective.isAccomplished` 检查所有任务 + gate
- 每个 Objective 的 accomplishedOn 时间戳
- 可能有 Exam（考试）要跳过

先只做"一键学习"，如果需要"一键完成"再说。

---

## Phase 5：功能兼容性 + 设备级适配

### 5.1 API 27 + API 28 双设备

| 检查项 | API 27（手机） | API 28（M53P 手表） | 处理 |
|--------|---------------|-------------------|------|
| minSdk 声明 | 27 | 27（向上兼容） | 27 覆盖两者 ✅ |
| VERSION_CODES.P 门控 | 27 < 28 ✅ 需要 | 28 >= 28 ❌ 不需要 | Phase 2 已加 |
| Doze 白名单 | 需要 | 需要 | adb 命令通用 |
| 蓝牙权限 | BLUETOOTH + FINE_LOCATION | 同上 | 同一套映射 ✅ |

### 5.2 M53P 手表特有（android-device-adaptation Skill）

| 项 | 做法 |
|----|------|
| 小屏幕 UI（2.14 寸） | expect/actual DeviceDimensions，字号缩到 11-12sp |
| 无 GMS/Play Services | FusedLocationProvider → LocationManager；Firebase → -PfirebaseDisable |
| 非 Wear OS | wear 模块处理（编译通过或排除） |
| 后台存活 | Doze 白名单 + 自启权限 + 前台通知常驻 |

### 5.3 FixedOffsetPlugin + WebDAV 显式验证

虽然代码审查认为这两个天然兼容 API 27，但还是要**显式验证**，不能跳。

#### 5.3.1 FixedOffsetPlugin 验证

```bash
cd /home/kylin/桌面/AAPS/AndroidAPS-4.0.0-official

# 1. cherry-pick 后确认文件都在
find . -path "*/calibration/*" -name "*.kt" -not -path "*/build/*"

# 2. grep 确认无 VERSION_CODES > 27
grep -rn --include="*.kt" -oE "VERSION_CODES\.[A-Z_]+" plugins/calibration/ --exclude-dir=build
# 预期：无输出 → 天然兼容

# 3. 编译确认
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export KONAN_DATA_DIR=/home/kylin/桌面/AAPS/.konan
./gradlew :plugins:calibration:compileAndroidMain --no-daemon

# 4. API 27 模拟器/真机上手动验证
# 打开 Settings → Calibration → Fixed Offset
# 输入一个 offset 值 → 保存 → 退出 → 重进确认值保留
```

#### 5.3.2 WebDAV 验证

```bash
# 1. 确认 WebDAV 代码位置
find . -path "*webdav*" -name "*.kt" -not -path "*/build/*" -o -path "*WebDav*" -name "*.kt" -not -path "*/build/*"

# 2. grep 确认无 Android 31+ API
grep -rn --include="*.kt" -E "canScheduleExactAlarms|VibratorManager|FOREGROUND_SERVICE_TYPE|NotificationChannelGroup|VERSION_CODES\.[STUV]" . --exclude-dir=build | grep -iE "webdav|dav|cloud"
# 预期：无输出 → 天然兼容

# 3. SAF createFile 扩展名验证（如果 WebDAV 有导出功能）
grep -rn "createFile" . --include="*.kt" --exclude-dir=build | grep -iE "webdav|cloud|export"
# 如果有 → 确认显式带扩展名（Phase 4 踩坑已修）

# 4. API 27 模拟器/真机上手动验证
# Settings → WebDAV → 填 URL/账号/密码 → 连接测试
# 导出一个文件 → 确认有扩展名 → 导入确认能读到
```

#### 5.3.3 其他可能新增功能的 API 27 验证

4.0.0 相比 beta1 新增了 Scenes（场景）等功能，逐个 grep：

```bash
# 全仓扫 VERSION_CODES 分布变化
grep -rn --include="*.kt" -oE "VERSION_CODES\.[A-Z_]+" . --exclude-dir=build | sort | uniq -c | sort -rn

# 和 Phase 1.2 的结果对比——如果有新出现的 VERSION_CODES > 27，补门控
diff <(cat /tmp/phase1-versions.txt) <(grep -rn --include="*.kt" -oE "VERSION_CODES\.[A-Z_]+" . --exclude-dir=build | sort | uniq -c | sort -rn)
```

---

## Phase 6：省电优化（SKILL.md 第 6 步）

```bash
# 改 logback.xml 加 AsyncAppender
find . -name "logback*.xml" -not -path "*/build/*"
# app/assets/logback.xml + wear/assets/logback.xml
```

---

## Phase 7：生产测试矩阵（SKILL.md 第 7 步）

### 7.1-7.5（和之前 v1 一样，加设备级检查）

#### 7.2 Lint 生产复查（最终）
#### 7.3 JVM 全量单测
#### 7.4 Instrumented 测试
#### 7.5 长稳验证（**双设备各跑一遍**）
- API 27 手机 6-12 小时
- API 28 M53P 手表 6-12 小时

#### 7.6 Release 构建（多 flavor）
#### 7.7 覆盖率
#### 7.8 Checklist（加设备级项）

新增 checklist 项：
- [ ] API 27 手机能安装、能跑循环
- [ ] API 28 M53P 手表能安装、能跑循环
- [ ] 手表小屏 UI 不溢出、可点击
- [ ] 无 GMS 设备上不 crash
- [ ] 一键学习全部目标功能生效（标记所有 learned = true）

---

## Phase 8-10：签名 + adb + 提交（SKILL.md 第 8-10 步）

---

## 执行顺序 + 时间估算

```
Phase 0  拉 4.0.0 + 基线              ← 10min
Phase 1  lint 探针 + 手动 grep         ← 15min
Phase 2  API 门控 + 蓝牙权限           ← 1-2h
Phase 3  中间 lint 验证                ← 5min
Phase 4  踩坑 + cherry-pick 2 功能      ← 2-4h（含 SMB + FixedOffset + 一键学习）
Phase 5  功能兼容 + 设备级适配          ← 1-2h
Phase 6  省电优化                      ← 15min
Phase 7  生产测试矩阵                  ← 3-6h（含双设备长稳）
Phase 8-10 签名 + 装机 + 提交          ← 1h
─────────────────────────────────────────────
总计                                     ≈ 10-17h
```

---

## 风险清单

| 风险 | 概率 | 处理 |
|------|------|------|
| GitHub 拉不动 | 中 | Gitee 镜像 / zip 手动下 |
| 4.0.0 KMP 重构大，cherry-pick 冲突 | **高** | 手动移植，不 git cherry-pick |
| SMB 放宽在 4.0.0 里位置变了 | 高 | grep 找 SourceSensor/FilteringSupported |
| FixedOffsetPlugin 在 4.0.0 calibration 模块结构变了 | 高 | 手动拷文件，settings.gradle 确认 include |
| 一键学习目标有循环前置保护不让开循环 | 中 | 学习只是标记 learned ≠ accomplished，gate 检查要跳过 |
| M53P 手表 adb 连不上 | 中 | 先 USB 调试开关 + stayon，WiFi adb 备选 |
| 4.0.0 Compose 重写引入新 API 31+ 调用 | 高 | lint 直接抓 |
| 双设备长稳需要两台实体 | 必须 | API 27 可以模拟器，M53P 必须真机 |

---

## 输出清单（最终交付）

1. `/home/kylin/桌面/AAPS/AndroidAPS-4.0.0-official/` — 完整源码
2. minSdk 27 适配通过 lint + JVM 全量单测 ✅
3. 2 个 fork 附加功能 cherry-pick ✅
4. 一键全部目标学习功能 ✅
5. API 27 + API 28 双设备验证 ✅
6. Release APK（所有 flavor）✅
7. git commit 历史按主题分 ✅
