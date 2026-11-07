# Boost 6.3.0 — Android 8.1 (API 27) 适配

本仓库是 [tim2000s/Boost](https://github.com/tim2000s/Boost) 6.3.0（基于 AAPS 4.0.0）的 API 27 适配分支，
目标是在 Android 8.1（含独立安卓手表）上生产运行。

## 版本基线

| 项目 | 值 |
|---|---|
| 上游 | `tim2000s/Boost`，tag `4.0.0-boost-6.3.0`，commit `80f0d02` |
| Boost 引擎 | `6.3.0+HEAD.80f0d02e3a` |
| applicationId | `info.nightscout.androidaps` |
| minSdk | **27**（上游 31） |
| wearMinSdk | **27**（上游 30） |
| targetSdk | 35（未改） |
| compileSdk | 37（未改） |
| 构建环境 | JDK 21 / AGP 9.5.0 / Gradle 9.8.x / Kotlin 2.4.21 |

## 构建

```bash
# 需要 Android SDK（local.properties 中 sdk.dir 指向本机）
./gradlew :app:assembleFullRelease        # 未签名 release
./gradlew :app:assembleFullDebug          # debug
./gradlew :wear:compileFullDebugKotlin    # 手表模块编译
```

签名流程见 [SIGNING.md](SIGNING.md)。仓库不包含任何密钥。

## 文档

| 文件 | 内容 |
|---|---|
| [CHANGES.md](CHANGES.md) | 相对上游的全部改动 |
| [VERIFICATION.md](VERIFICATION.md) | 模拟器验证矩阵、闭环证据、压测结果 |
| [DEVICE-CHECKLIST.md](DEVICE-CHECKLIST.md) | 真机验证与生产门槛 |
| [SCAN-REPORT.md](SCAN-REPORT.md) | API 门控静态扫描结论 |
| [SIGNING.md](SIGNING.md) | 签名步骤（不含密钥） |
| `reference/` | 适配方法论、计划与测试文档（AAPS 4.0.0 基线参考） |

## 结论摘要

- Android 8.1 / 9.0 模拟器均完成安装、配置导入、Boost 闭环与 1 小时压测，0 崩溃 / 0 ANR
- 三维 API 门控扫描零命中；`android.jar`（API 27）对照仅两处 API 33 类，且都已 TIRAMISU 门控
- 新增两个安全开关：手机步数（默认开）、本机心率源（默认关）
- 真机（蓝牙泵、CGM、厂商保活、真实 Doze）仍需按清单验证，尚未完成
