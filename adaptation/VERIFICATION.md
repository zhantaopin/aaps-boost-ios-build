# 验证记录

产物：`Boost-6.3.0-api27-full-release.apk`（私有证书签名）
- SHA-256：`d49fb5931c968aca5b495e077b554957f57613764bceabd328848871d126194a`
- 证书 SHA-256：`ed5fce41021b7aeeff02d4766e7fd2bb510f1d0f691292be4bc3943a2758dfee`
- `minSdkVersion 27` / `targetSdkVersion 35`（aapt2 核对）

## 静态检查

| 项目 | 结果 |
|---|---|
| 三维 API 门控扫描（方法调用 / catch 高版本异常 / import 高版本类） | 0 命中 |
| `android.jar`（API 27）类清单比对 | 仅 `android.app.LocaleManager`、`android.bluetooth.BluetoothStatusCodes`，均 TIRAMISU 门控 |
| `RECEIVER_EXPORTED` | 全部经 `ContextCompat` 或 TIRAMISU 分支 |
| `android:foregroundServiceType` | 声明式，低版本忽略 |
| AAR minSdk 冲突（`processFullDebugMainManifest`） | 通过 |

## Android 8.1（API 27，x86_64）

| 项目 | 结果 |
|---|---|
| 安装 / 冷启动 | 成功 |
| Room 初始化、Compose 渲染 | 正常 |
| 导入真实配置（394 项变更） | 成功，进程未重启 |
| SAF 目录授权 | 通过 |
| 血糖注入（MM640g `NS_EMULATOR` JSON） | 正常入库 |
| 闭环 | `VirtualPumpPlugin.setTempBasalAbsolute: Success: true`、`Enacted: true`；SMB 成功下发 0.1 U |
| DeviceStatus | `status=Closed Loop`、`algorithm=SMB`、`runningDynamicIsf=true` |
| 1 小时压测（5 分钟注入一次） | 12/12 入库，12+ DeviceStatus，9 次临时基础率变更 |
| 崩溃 / ANR | 0 / 0 |

## Android 9.0（API 28，x86_64）

| 项目 | 结果 |
|---|---|
| 安装 / 启动 / 导入（400 项） | 成功 |
| 闭环与 SMB | 成功 |
| 屏幕熄灭 + 深 Doze 下广播唤醒 | 正常入库 |
| 1 小时压测 | 12/12 入库，13 DeviceStatus，9 次临时基础率变更 |
| 崩溃 / ANR / 进程重启 | 0 / 0 / 无（PID 不变） |
| PSS | 143–171 MB，末段 149 MB，无持续增长 |

## 新增设置项

在 8.1 release 包内通过设置搜索确认：

- `Step Count Settings` 摘要以「手机计步」开头（新 key 位于首项）
- `Heart Rate Integration` 摘要含「手表心率源、心率采样间隔」
- 中文（简/繁）文案已打包

## 已知缺口

- 模拟器 `sensorservice` 无 step counter 与 heart rate 传感器：步数准确性、心率采样与耗电均未验证
- 真实 Doze 闹钟唤醒、厂商保活、蓝牙泵/CGM 未验证
- Health Connect 在 Android 8.1 不可用（`isAvailable=false` 自动跳过）
- `http://` 的 Nightscout 会被 `network_security_config` 拒绝（仅允许 localhost 明文）
- Wear OS < API 30 的回退分支未在运行时验证（仅编译通过）
