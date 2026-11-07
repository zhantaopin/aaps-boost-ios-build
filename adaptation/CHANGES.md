# 改动清单（相对上游 4.0.0-boost-6.3.0）

统计：91 个文件修改、8 个文件新增（`git diff --stat` 776 insertions / 253 deletions）。

## 1. 构建配置

- `buildSrc/src/main/kotlin/Versions.kt`
  - `minSdk = 27`（原 31）
  - `wearMinSdk = 27`（原 30）
  - `targetSdk = 35`、`compileSdk = 37` 保持不变

## 2. API 27 兼容补丁（沿用 AAPS 4.0.0 适配经验）

共 91 个文件，按以下类别处理：

- `Build.VERSION.SDK_INT` 门控：Activity Recognition、前台服务类型、`PendingIntent` 可变性、
  receiver 导出标志、`LocaleManager`、`BluetoothStatusCodes`
- 蓝牙权限：新增 `BluetoothPermissionCompat`，统一 `BLUETOOTH_CONNECT/SCAN` 与旧权限的版本分支
- 存储：SAF 目录授权路径兼容
- 通知与前台服务：低版本不声明高版本专用属性
- 其余为低版本 API 替代实现与空值/异常兜底

### 新增文件

| 文件 | 用途 |
|---|---|
| `core/interfaces/.../pump/BluetoothPermissionCompat.kt` | 蓝牙权限版本兼容 |
| `core/keys/.../CalibrationOffsetKeys.kt` | 校准偏移设置 key |
| `plugins/calibration/.../FixedOffsetPlugin.kt` | 固定偏移校准插件 |
| `plugins/calibration/.../compose/FixedOffsetComposeContent.kt` | 校准 UI |
| `plugins/calibration/.../compose/FixedOffsetScreen.kt` | 校准屏幕 |
| `plugins/calibration/.../compose/FixedOffsetViewModel.kt` | 校准 ViewModel |
| `plugins/source/.../UserOffsetHelper.kt` | 用户偏移辅助 |
| `plugins/aps/.../openAPSBoost/WatchHrSource.kt` | 独立手表本机心率源（本分支新增） |

> 固定偏移校准相关文件来自此前 AAPS 4.0.0 适配分支，Boost 上游不存在；如不需要该功能可以单独回退。

## 3. 运动权限在 Android 10 以下隐藏

- `OpenAPSBoostPlugin.requiredPermissions()`
  - API < 29 不再返回 `ACTIVITY_RECOGNITION`：该权限在 8.1/9.0 不存在，申请只会留下永远无法授予的条目
  - `BODY_SENSORS` 仅在「设备有 HR 传感器 + 心率源开关打开」时返回
- `wear/.../WatchfaceConfigurationActivity.kt`：同样的 `SDK >= Q` 门控

## 4. 手机步数开关（默认开）

- 新 key：`ApsBoostPhoneStepsEnabled`（默认 `true`）
- `BoostStepSensor.register(..., enabled)`：关闭时不注册传感器
- APS 循环内关闭时：
  - `recentSteps5/15/30/60Min` 置 0
  - `StepFeed.phoneLive = false`
  - `blendedSteps15Min` 与睡眠判断中的手机步数归零
- UI 位置：Boost → Exercise Settings → Step Count Settings 首项

关闭后的行为：`stepsAvailable = false`，活动降 profile、久坐加量、睡懒觉保护全部不介入，
Boost 退化为纯血糖驱动；Wear/Garmin 的 SC 数据不受影响。

## 5. 本机心率源（默认关）

- 新 key：`ApsBoostWatchHrEnabled`（默认 `false`）、`ApsBoostWatchHrIntervalMin`（默认 5，范围 1–30）
- `WatchHrSource`：
  - 每 `interval` 分钟注册一次 `TYPE_HEART_RATE`，采样 60 秒后取平均
  - 写入单条聚合 `HR` 记录（`duration=60s`，`device=厂商+型号`）
  - 关闭时只保留每分钟一次的轻量轮询，不触碰传感器；切换开关无需重启 App
  - 无传感器、无权限、样本不足时安静退出
- Manifest：`BODY_SENSORS` + `uses-feature android.hardware.sensor.heartrate required=false`
- UI 位置：Boost → Exercise Settings → Heart Rate Integration

注意：心率数据除活动分类外，也会被睡眠检测使用（`SleepStateDetector` 不读取
`hrIntegrationEnabled`）。开启心率源但关闭集成的效果仅限睡眠判定的改善。

## 6. 中英文案

- `core/keys`、`plugins/aps` 的 `values`、`values-zh-rCN`、`values-zh-rTW` 均已补齐
