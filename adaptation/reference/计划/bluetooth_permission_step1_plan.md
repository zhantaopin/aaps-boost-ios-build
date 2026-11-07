# 蓝牙权限运行时检查点替换 Step 1 — 实施计划

## 背景
API 27 (Android 8.1) 上 `Manifest.permission.BLUETOOTH_CONNECT/SCAN` 常量存在但 `checkSelfPermission()` 恒返回 `DENIED`，导致泵静默拒连 = 医疗风险。本次替换将所有硬编码的 `BLUETOOTH_CONNECT/SCAN` 运行时检查替换为 compat 对象，在 API < 31 时自动映射到 `BLUETOOTH` / `ACCESS_FINE_LOCATION`。

## 新建文件

### 1. `pump/common/src/main/kotlin/app/aaps/pump/common/hw/ble/BluetoothPermissionCompat.kt`
- 包名: `app.aaps.pump.common.hw.ble`
- 放置于 pump/common 的 src/main/kotlin（pump/common 是纯 Android 模块，非 KMP）
- 提供 `connectGranted(ctx)` 和 `scanGranted(ctx)` 两个方法
- API >= 31 使用 `BLUETOOTH_CONNECT/SCAN`，API < 31 回退到 `BLUETOOTH` / `ACCESS_FINE_LOCATION`

## 修改文件清单（10 个 androidMain 非测试 .kt 文件）

### 1. `pump/dana/danars/src/main/kotlin/app/aaps/pump/danars/services/BleTransportImpl.kt`
- **L183**: `hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `BluetoothPermissionCompat.scanGranted(context)`
- **L298**: `hasPermission(permission: String = Manifest.permission.BLUETOOTH_CONNECT)` → 重写为 `BluetoothPermissionCompat.connectGranted(context)` 的包装
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`
- 移除不再需要的 import（如有）

### 2. `pump/dana/danars/src/main/kotlin/app/aaps/pump/danars/services/BLEComm.kt`
- **L101**: `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L158**: 同上替换
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`
- 检查并移除不再使用的 import

### 3. `pump/dana/danar/src/main/kotlin/app/aaps/pump/danar/services/RealRfcommTransport.kt`
- **L34**: `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L52**: 同上替换
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`

### 4. `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/ble/MedtrumBleTransportImpl.kt`
- **L140**: `hasPermission(Manifest.permission.BLUETOOTH_CONNECT) || !hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `!BluetoothPermissionCompat.connectGranted(context) || !BluetoothPermissionCompat.scanGranted(context)`
- **L182**: `hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `BluetoothPermissionCompat.connectGranted(context)`
- **L427**: `hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `BluetoothPermissionCompat.scanGranted(context)`
- **L472, 477, 482, 487**: `hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `BluetoothPermissionCompat.connectGranted(context)`
- **L507**: `hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `BluetoothPermissionCompat.scanGranted(context)`
- **L549**: `hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `BluetoothPermissionCompat.connectGranted(context)`
- 移除 `hasPermission()` 私有方法（不再需要）
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`

### 5. `pump/rileylink/src/main/kotlin/app/aaps/pump/common/hw/rileylink/ble/RileyLinkBLE.kt`
- **L172**: `ContextCompat.checkSelfPermission(context, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`

### 6. `pump/rileylink/src/main/kotlin/app/aaps/pump/common/compose/RileyLinkPairWizardViewModel.kt`
- **L91**: `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.scanGranted(context)`
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`

### 7. `pump/insight/src/main/kotlin/app/aaps/pump/insight/connection_service/InsightConnectionService.kt`
- **L280**: `ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED` → `BluetoothPermissionCompat.connectGranted(this)`
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`

### 8. `pump/insight/src/main/kotlin/app/aaps/pump/insight/compose/InsightPairState.kt`
- **L167**: `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.scanGranted(context)`
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`

### 9. `pump/insight/src/main/kotlin/app/aaps/pump/insight/utils/ConnectionEstablisher.kt`
- **L20**: 删除 `@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)` 注解
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`（为后续可能的运行时检查保留）
- 移除 import: `androidx.annotation.RequiresPermission`（如不再使用）

### 10. `pump/omnipod/common/src/main/kotlin/app/aaps/pump/omnipod/common/bledriver/comm/legacy/LegacyBleDeviceManager.kt`
- **L36**: `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED` → `BluetoothPermissionCompat.connectGranted(context)`
- **L49**: `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`
- 添加 import: `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`

## 验证
- 修改完成后 grep `BLUETOOTH_CONNECT|BLUETOOTH_SCAN` 确认这 10 个文件里都干净了
- 排除测试文件、commonMain、iosMain 中的出现（不在本次范围内）
