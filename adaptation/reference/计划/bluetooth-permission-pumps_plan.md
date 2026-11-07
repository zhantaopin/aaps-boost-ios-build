# Bluetooth Permission Runtime Check Migration — Pump Modules

## 背景

`core/interfaces/.../BluetoothPermissionCompat.kt` 已存在，提供：
- `connectGranted(ctx: Context): Boolean` — pre-31 查 BLUETOOTH，31+ 查 BLUETOOTH_CONNECT
- `scanGranted(ctx: Context): Boolean` — pre-31 查 ACCESS_FINE_LOCATION，31+ 查 BLUETOOTH_SCAN
- `requestedPermissions(): Array<String>` — 收集运行时请求用的字符串数组

现在把 pump 模块里对 `Manifest.permission.BLUETOOTH_CONNECT` / `Manifest.permission.BLUETOOTH_SCAN` 的运行时查询替换成 Compat 调用。**不要修改测试文件**。

---

## 逐文件修改计划

### 1. `pump/medtrum/.../MedtrumBleTransportImpl.kt`

**现状**：有私有 `hasPermission(permission: String): Boolean` 包装函数（L462-463），内部用 `ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED`。所有权限查询走 `hasPermission(Manifest.permission.BLUETOOTH_CONNECT/SCAN)`。

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L140** `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) || !hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `!BluetoothPermissionCompat.connectGranted(context) || !BluetoothPermissionCompat.scanGranted(context)`
- **L182** `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L427** `!hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `!BluetoothPermissionCompat.scanGranted(context)`
- **L472** `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L477** `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L482** `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L487** `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L507** `!hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `!BluetoothPermissionCompat.scanGranted(context)`
- **L549** `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L462-463** 整段私有 `hasPermission` 函数 → 删除

---

### 2. `pump/carelevo/.../CarelevoBleTransportImpl.kt`

**现状**：私有 `hasPermission` 函数（L343-344）；`Build` import 仍存在（用于 enableNotifications 的 TIRAMISU 分支），**不要删**。

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；保留 `android.os.Build`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L164, 169, 175, 181, 264** 的 `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L205** 的 `!hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `!BluetoothPermissionCompat.scanGranted(context)`
- **L343-344** 私有 `hasPermission` → 删除

---

### 3. `pump/equil/.../EquilBleTransportImpl.kt`

**现状**：私有 `hasPermission` 函数（L302-303）。

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L139, 144, 150, 156, 241** 的 `!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L180** 的 `!hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `!BluetoothPermissionCompat.scanGranted(context)`
- **L302-303** 私有 `hasPermission` → 删除

---

### 4. `pump/diaconn/.../BLECommonService.kt`

**现状**：无私有包装函数，直接 `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) [!=/==] PackageManager.PERMISSION_GRANTED`。

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L94** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`
- **L125** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED` → `BluetoothPermissionCompat.connectGranted(context)`
- **L148** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`

---

### 5. `pump/diaconn/.../DiaconnPairWizardViewModel.kt`

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L91** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.scanGranted(context)`

---

### 6. `pump/insight/.../InsightConnectionService.kt`

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L280** `if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)` → `if (BluetoothPermissionCompat.connectGranted(this))`

---

### 7. `pump/insight/.../InsightPairState.kt`

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L167** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.scanGranted(context)`

---

### 8. `pump/insight/.../ConnectionEstablisher.kt`

**现状**：L20 有 `@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)`，import `android.Manifest` 和 `androidx.annotation.RequiresPermission`。

**修改**：
- **import**：删除 `android.Manifest`、`androidx.annotation.RequiresPermission`
- **L20** `@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)` 整行 → 删除

---

### 9. `pump/omnipod/common/.../LegacyBleDeviceManager.kt`

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；保留 `android.os.Build`（L35/48 的 VANILLA_ICE_CREAM 分支用）；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L36** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED` → `BluetoothPermissionCompat.connectGranted(context)`
- **L49** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`

---

### 10. `pump/rileylink/.../RileyLinkBLE.kt`

**现状**：L172 用的是字符串字面量 `"android.permission.BLUETOOTH_CONNECT"` + `ContextCompat.checkSelfPermission`（不是 ActivityCompat）。

**修改**：
- **import**：删除 `android.content.pm.PackageManager`、`androidx.core.content.ContextCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L172** `ContextCompat.checkSelfPermission(context, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.connectGranted(context)`

---

### 11. `pump/rileylink/.../RileyLinkPairWizardViewModel.kt`

**修改**：
- **import**：删除 `android.Manifest`、`android.content.pm.PackageManager`、`androidx.core.app.ActivityCompat`；新增 `app.aaps.core.interfaces.pump.BluetoothPermissionCompat`
- **L91** `ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED` → `!BluetoothPermissionCompat.scanGranted(context)`

---

## 验证

改完后在 `AndroidAPS-official-4.0.0/` 下跑：
```
grep -rn --include="*.kt" -E "BLUETOOTH_CONNECT|BLUETOOTH_SCAN" pump/*/src/main/ pump/omnipod/common/src/main/ pump/rileylink/src/main/
```
确认字符串已消失（BLUETOOTH 本身、BLUETOOTH_ADMIN 等其他常量不在本次范围内）。

再检查这些文件里是否还有遗留的 `ActivityCompat.checkSelfPermission.*BLUETOOTH` / `ContextCompat.checkSelfPermission.*BLUETOOTH`。
