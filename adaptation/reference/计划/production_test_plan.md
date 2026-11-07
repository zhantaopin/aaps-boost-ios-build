# 满足生产需要补跑的测试 Plan

## 问题定义

你的 fork minSdk 改 27，移除了 wear，编译通过（16m35s，714 任务），跑了部分 JVM 单测。
但 AndroidAPS 是**控制胰岛素泵的医疗软件**，生产标准不能按普通 App 来。

这个 Plan 从三个维度回答"还需要跑什么"：
1. **官方生产基线**：AndroidAPS 原仓库在 CircleCI + GitHub Actions 上实际跑的全套
2. **minSdk 27 专属缺口**：只有你的 fork 需要额外关注的
3. **硬件/环境限制**：当前 Linux 机器能做什么、做不到什么

## 官方生产测试矩阵（CircleCI + GitHub Actions）

| # | 测试类型 | 任务 | 在哪跑 | 这个 fork 跑过吗 |
|---|---------|------|--------|----------------|
| 1 | **JVM 单元测试（全套）** | `testFullDebugUnitTest testDebugUnitTest jvmTest testAndroidHostTest :desktop:shell:test` | GitHub Actions ubuntu-latest（60 分钟超时） | ⚠️ **只跑了 2 个**（:ui:testAndroidHostTest 和 :implementation:testAndroidHostTest，是 WebDAV 工作时顺手跑的）。其他 ~20 模块的 jvmTest 和 app 的 testFullDebugUnitTest **没跑过** |
| 2 | **Android Instrumented 测试（三模拟器分片）** | `connectedFullDebugAndroidTest` 分 @ShardA/@ShardB/@ShardC 三片 | CircleCI self-hosted（3 个 API 31 模拟器并行） | ❌ **完全没跑过**。需要 Android 模拟器或真机 |
| 3 | **iOS 单元测试** | `:ios:shell:iosSimulatorArm64Test` + 3 个 commonTest 模块 | GitHub Actions macos-latest | ❌ 你这台 Linux 上**跑不了**（SKIPPED）。但 iOS KLIB 已经交叉编译成功，能编出 622 个 API |
| 4 | **覆盖率聚合** | `jacocoAllDebugReport` → Codecov | GitHub Actions（JVM）+ CircleCI（instrumented）合并 | ❌ 没跑过 |
| 5 | **Android Lint** | `:app:lint` + 各模块 lint | CircleCI | ❌ **完全没跑过**。Phase 0 只用了手动 grep |
| 6 | **签名 Release 构建** | `assembleFullRelease`（含签名） | AAPS CI workflow | ❌ 没跑过 release 签名构建 |
| 7 | **多 flavor 构建** | fullRelease / aapsclientRelease / aapsclient2Release / pumpcontrolRelease | AAPS CI | ❌ 只跑了 fullDebug |

## 你的 fork 的缺口（按生产优先级）

### 🔴 必须补的（影响安全性/功能正确性）

| # | 测试 | 为什么必须 | 命令 | 成本 |
|---|------|-----------|------|------|
| **A** | **完整 JVM 单元测试套件** | 官方生产跑全套，你只跑了 2 个模块。minSdk 改 27 不影响 JVM 测试，但 JVM 测试覆盖业务逻辑（循环算法、SMB、密码校验、WebDAV、保护检查等） | `./gradlew --no-daemon -Pcoverage -PfirebaseDisable testFullDebugUnitTest testDebugUnitTest jvmTest testAndroidHostTest :desktop:shell:test 2>&1 > /tmp/jvm-test.log` | **预计 30-60min**（全模块第一次） |
| **B** | **Android Lint + 手动 grep 补 KMP 盲区** | **Phase 0 手动 grep 是近似扫描，lint 能抓到 grep 抓不到的隐式 API 调用**。编译通过≠运行不崩 | `./gradlew :app:lintFullDebug --no-daemon 2>&1 > /tmp/lint.log` + grep VERSION_CODES | **10-15min** |
| **C** | **多 flavor Release 构建** | SKILL.md 第 8 步明确要求"多 flavor 全要建"。只跑 fullDebug 不代表 release 能编、不代表 aapsclient 不崩 | `./gradlew --no-daemon assembleFullRelease assembleAapsclientRelease assemblePumpcontrolRelease -PfirebaseDisable 2>&1 > /tmp/release-build.log` | **20-30min** |

### 🟡 有条件补的（需要硬件/环境）

| # | 测试 | 为什么需要 | 缺什么 |
|---|------|-----------|--------|
| **D** | **Android Instrumented 测试（真机/emulator）** | 官方生产跑 3 个模拟器分片。核心 LoopTest、SetupWizardE2ETest、Omnipod 历史测试都在这里。**真机上才能验证 minSdk 27 的真实行为**（蓝牙权限、WorkManager、ForegroundService） | 当前 Linux 机器没 Android SDK emulator 镜像，也没真机 USB 连接。但你之前说过有 Android 12 模拟器——需要确认能启动 |
| **E** | **覆盖度报告** | 生产级需要知道哪些代码行被测试覆盖。Codecov 是原仓库标配 | 先跑 A，再 `./gradlew jacocoAllDebugReport`，不需要额外硬件 |

### ⚪ 这台机器跑不了的

| # | 测试 | 为什么 | 替代方案 |
|---|------|--------|---------|
| **F** | iOS Simulator 测试 | `iosSimulatorArm64Test` 在 Linux 上 SKIPPED | 上传 GitHub Actions 让它在 macos-latest 上跑 |
| **G** | 真机蓝牙/泵连接验证 | 需要物理 Android 8.1 设备 + 泵 | 拿真机手动测（不是 CI 能解决的） |

## 执行顺序

```
第一步（纯 Gradle，不需要设备）：
  B. Android Lint + 手动 grep        ← 先做，最快出结果（10min）
  A. 完整 JVM 单元测试               ← 同时暴露业务逻辑回归（30-60min）
  C. 多 flavor Release 构建           ← 最后，最耗时（20-30min）
  E. 覆盖率聚合（可选，依赖 A）       ← 跑完 A 后加一步 jacocoAllDebugReport

第二步（需要设备，可选）：
  D. Android Instrumented 测试        ← 需要模拟器或真机
```

## 每个测试的"通过/失败"判据

| 测试 | 通过 | 失败 | 怎么读结果 |
|------|------|------|-----------|
| **Lint** | 无 API level 相关 Error | 有 `NewApi` / `ObsoleteSdkInt` / `VisibleForTests` 且不是已知 false positive | `grep -E ": Error" lint.log` + lint-results-*.txt |
| **JVM 单测** | exit code 0，全部 PASS | exit code ≠ 0 或有 FAIL | `grep -E "FAIL|PASS|BUILD" /tmp/jvm-test.log` + 各模块 build/test-results/*.xml |
| **Release 构建** | BUILD SUCCESSFUL，4 个 flavor APK 都生成 | 任何 flavor BUILD FAILED | `ls app/build/outputs/apk/*/release/*.apk` |
| **覆盖率** | jacocoAllDebugReport 生成 XML | gradle task 失败 | `cat build/reports/jacoco/jacocoAllDebugReport/html/index.html` |

## 风险

- **JVM 全量测试可能暴露出之前没跑过的 baseline failure**：20 模块如果有 2-3 个 baseline 失败（类似之前 WebDAV 那 2 个），需要区分"本来就坏的"和"你的改动引入的"
- **Lint 可能报大量 Warning**：Android 8.1 deprecation、Compose 建议等——只关注 API level Error 即可
- **多 flavor Release 构建可能因为签名/混淆失败**：混淆规则没配好、release signingConfig 没设等，需要分别排查
- **时间累计**：A+B+C 加起来约 60-105 分钟，最好分批跑，不要一次全部塞进去
