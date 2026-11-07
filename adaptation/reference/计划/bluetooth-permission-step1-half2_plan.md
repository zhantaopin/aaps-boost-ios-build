# 蓝牙权限运行时检查点替换 — Step 1 第二半

## 背景
SKILL.md 指出：API 27（Android 8.1）上 `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` 常量虽然存在（API 定义在 Build.VERSION_CODES.S = 31，但 AndroidX 的 Manifest.permission 常量从 API 27 起就有），但运行时 `checkSelfPermission` 恒返回 DENIED，因为系统实际还没引入这两个运行时权限（旧系统只有 `BLUETOOTH` 普通权限）。直接检查 `Manifest.permission.BLUETOOTH_CONNECT` 会让 pump 静默拒连，是医疗风险。

## 模块依赖分析
- `pump/common` = Android library（非 KMP），无 `androidMain` 目录，源码在 `src/main/kotlin/`
- `pump/combov2` 依赖: core:data, core:interfaces, core:keys, core:objects, core:ui, comboctl — **不依赖 pump/common**
- `pump/combov2/comboctl` = KMP 库（commonMain + androidMain）— 也不能引用 pump/common

→ **ComboV2Plugin.kt 不能复用 pump/common 的 compat 对象**，需要独立处理。

---

## 修改清单（8 步，共 8 个文件）

### 1. 创建 BluetoothPermissionCompat.kt（新文件）
- 路径: `pump/common/src/main/kotlin/app/aaps/pump/common/hw/ble/BluetoothPermissionCompat.kt`
- 包名: `app.aaps.pump.common.hw.ble`
- 内容: 按任务描述提供的代码原样写入（`connectGranted(ctx)` 和 `scanGranted(ctx)`）

### 2. CarelevoBleTransportImpl.kt（替换 6 处）
- 路径: `pump/carelevo/src/main/kotlin/app/aaps/pump/carelevo/ble/CarelevoBleTransportImpl.kt`
- 修改：
  - import 加 `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`
  - 删除 import `android.content.pm.PackageManager` 和 `androidx.core.app.ActivityCompat`（如果不再需要）
  - 替换所有 `hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `BluetoothPermissionCompat.connectGranted(context)`
  - 替换所有 `hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `BluetoothPermissionCompat.scanGranted(context)`
  - 删除 `private fun hasPermission(...)` 私有函数（已无用）
- 涉及行: 164, 169, 175, 181, 205, 264, 343-344

### 3. EquilBleTransportImpl.kt（替换 6 处）
- 路径: `pump/equil/src/main/kotlin/app/aaps/pump/equil/ble/EquilBleTransportImpl.kt`
- 修改：
  - import 加 `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`
  - 删除 import `android.content.pm.PackageManager` 和 `androidx.core.app.ActivityCompat`
  - 替换所有 `hasPermission(Manifest.permission.BLUETOOTH_CONNECT)` → `BluetoothPermissionCompat.connectGranted(context)`
  - 替换所有 `hasPermission(Manifest.permission.BLUETOOTH_SCAN)` → `BluetoothPermissionCompat.scanGranted(context)`
  - 删除 `private fun hasPermission(...)` 私有函数
- 涉及行: 139, 144, 150, 156, 180, 241, 302-303

### 4. DiaconnPairWizardViewModel.kt（替换 1 处）
- 路径: `pump/diaconn/src/main/kotlin/app/aaps/pump/diaconn/compose/DiaconnPairWizardViewModel.kt`
- 修改：
  - import 加 `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`
  - 第 91 行: `if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)` → `if (!BluetoothPermissionCompat.scanGranted(context))`
  - 第 92 行日志也可改为更准确的文案（可选）
- 注意：原文件还 import 了 `android.Manifest` 和 `android.content.pm.PackageManager` 和 `androidx.core.app.ActivityCompat`，替换后这些 import 可能不再需要，要清理

### 5. BLECommonService.kt（替换 3 处）
- 路径: `pump/diaconn/src/main/kotlin/app/aaps/pump/diaconn/service/BLECommonService.kt`
- 修改：
  - import 加 `app.aaps.pump.common.hw.ble.BluetoothPermissionCompat`
  - 第 94 行: `if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)` → `if (!BluetoothPermissionCompat.connectGranted(context))`
  - 第 125 行: `if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)` → `if (BluetoothPermissionCompat.connectGranted(context))`
  - 第 148 行: 同上 connect
  - 清理不再需要的 import

### 6. BluetoothPermissionChecks.kt（版本门控）
- 路径: `pump/combov2/comboctl/src/androidMain/kotlin/info/nightscout/comboctl/android/BluetoothPermissionChecks.kt`
- 问题：硬编码 `"android.permission.BLUETOOTH_CONNECT"` / `"android.permission.BLUETOOTH_SCAN"` 字符串常量，低版本系统查恒 DENIED
- 修改方案：
  - 删掉 private const val 硬编码
  - 添加版本门控：
    ```kotlin
    private fun connectPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
    private fun scanPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
    ```
  - 各函数改用这些动态函数
  - 注意：comboctl 是 KMP 库，androidMain 可以引用 `android.os.Build` 和 `android.Manifest`
  - 加 `@SuppressLint("InlinedApi")` 避免 Lint 抱怨 BLUETOOTH_CONNECT/SCAN 在低版本 API 不存在

### 7. ComboV2Plugin.kt（3 处 permissionsToCheckFor）
- 路径: `pump/combov2/src/main/kotlin/info/nightscout/pump/combov2/ComboV2Plugin.kt`
- 问题: 3 处 `permissionsToCheckFor = listOf("android.permission.BLUETOOTH_CONNECT")` 硬编码字符串
- 模块依赖限制：combov2 不依赖 pump/common，不能引用 BluetoothPermissionCompat
- 修改方案：在同模块的 `Utility.kt` 中加 helper
  - `Utility.kt` 添加：
    ```kotlin
    private fun connectPermissionName(): String =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            android.Manifest.permission.BLUETOOTH_CONNECT
        else
            android.Manifest.permission.BLUETOOTH
    internal fun bluetoothPermissionList(): List<String> = listOf(connectPermissionName())
    ```
  - ComboV2Plugin.kt 3 处改为 `permissionsToCheckFor = bluetoothPermissionList()`
  - 加 `@SuppressLint("InlinedApi")`（如果用 Manifest 常量）

### 8. grep 验证
在 `/home/kylin/桌面/AAPS/AndroidAPS-official-4.0.0/` 下 grep：
- `BLUETOOTH_CONNECT|BLUETOOTH_SCAN` — 只允许在 BluetoothPermissionCompat.kt 和 BluetoothPermissionChecks.kt 中出现（其他文件应已清理）

---

## 明确排除范围
- 不改 commonMain、iosMain
- 不改 *Test.kt 文件
- 不改 build.gradle.kts
- 不改 AndroidManifest.xml

## 风险/注意
- import 清理要小心：有些文件可能 import 多个 Manifest 权限，只去掉不再用的
- CarelevoBleTransportImpl.kt 的 `Manifest.permission.BLUETOOTH` 可能通过 Manifest 类引用其他东西 — 检查 Manifest import 是否可以完全删除
- combov2 Utility.kt 已存在，追加 helper 不冲突
