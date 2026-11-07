# 补跑 lint 探针 Plan

## Repository Research

### 真相还原

| 阶段 | 做了什么 | 没做什么 |
|------|---------|---------|
| **Phase 0**（02919b8） | 手动 grep 清点 API 31+ 调用点（VERSION_CODES 12 文件 17 处、SDK_INT 判断 8 文件 10 处、Manifest 权限 40 行） | **没跑 `./gradlew lint`** |
| **Phase 1**（2da4f11） | minSdk 改 27、加 desugaring、移除 wear、`assembleFullDebug` 编译通过 | **没跑 lint** |
| **Phase 2-4** | API 门控守卫、蓝牙权限映射、功能修复 | **没跑 lint** |

**Skill.md 里写的 lint 探针流程**是事后总结的改进方案，不是当时实际执行的步骤。

### "编译通过" vs "lint 通过" 的区别

Phase 1 那次 `assembleFullDebug BUILD SUCCESSFUL` 只说明：
- ✅ 所有代码语法正确、能编出 dex
- ✅ 依赖库都兼容 minSdk 27
- ❌ **不检查**"你用了 API 31 的方法但没加版本判断"（编译照样过，运行时崩溃）
- ❌ **不检查** Manifest 里声明了不存在的属性
- ❌ **不检查**代码风格、性能、安全问题

**lint 能额外发现**：
- 某文件调用了 `VibratorManager`（API 31）却没 `SDK_INT >= S` 判断
- Manifest 用了 `foregroundServiceType="specialUse"` 却没 `SDK_INT >= S`
- 直接 `startForegroundService`（API 26+）没判断 8.1 上的降级路径

### 探针 init 脚本需要更新

Phase 0 已经把 minSdk 改成 27 了。要重新跑 lint 探针，需要一个 **临时** init 脚本把 minSdk 先改回 31 再跑 lint（让 lint 认为项目是 API 31，然后报错说"这些 API 你没加版本判断"），跑完后恢复。

不对——实际上反过来：**现在 minSdk 已经是 27 了**，直接跑 lint，lint 就会报告"这些 API 你用了但 minSdk 只有 27"。不需要临时改回去。

### 跑哪些模块

Phase 0 清点时已经**排除了 wear**（"wear 3 文件 3 处忽略"），所以探针也只跑非 wear 模块：
- `:app:lintFullDebug`（主 app，多 flavor 只跑 full）
- KMP 模块 lint 扫不到 androidMain（SKILL.md 第 3 步说的盲区），所以需要手动 grep 补漏

### 现状

- 无 `.claude/tmp/minsdk.init.gradle`（当时没创建过）
- 无 lint-results 历史文件
- minSdk = 27（已在 Versions.kt）
- wear 已从 settings.gradle 移除

## Files and Modules

- **只读** 现有源码，不改任何业务代码
- **临时创建** `.trae/tmp/minsdk_linux.init.gradle`（和原 Skill 思路一样，但是用 bash/Linux 路径）
- **临时创建** lint 日志文件（`/tmp/wear-lint-probe/`）
- 跑完不保留 init 脚本

## Implementation Steps

### Step 1: 创建 lint 探针 init 脚本

`.trae/tmp/minsdk_linux.init.gradle`（比原 Skill 简化——minSdk 已经是 27 了，不需要强制降，lint 自己就会检查）：

```groovy
// 简化版：minSdk 已经是 27，lint 直接扫就行
// 这个 init 脚本只做一件事：对所有 android 模块开 lint
allprojects { p ->
    afterEvaluate {
        def android = p.extensions.findByName('android')
        if (android != null) {
            try { android.lint.checkDependencies = true } catch (Throwable t) {}
        }
    }
}
```

### Step 2: 跑主 app 的 lint

```bash
cd /home/kylin/桌面/AAPS/opencode-work/AndroidAPS-4.0-watch
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export KONAN_DATA_DIR=/home/kylin/桌面/AAPS/.konan
./gradlew :app:lintFullDebug --no-daemon -I .trae/tmp/minsdk_linux.init.gradle 2>&1 > /tmp/lint-probe.log
```

### Step 3: 读 lint 报告

```bash
# 错误
grep -E "^e:|: Error " /tmp/lint-probe.log
# 查看具体报告
find app/build -name "lint-results*" -name "*.txt"
cat app/build/intermediates/lint_intermediate_text_report/fullDebug/lintReportFullDebug/lint-results-fullDebug.txt
```

### Step 4: 手动 grep 扫 KMP 盲区（和 Phase 0 一样的命令）

```bash
grep -rn --include=*.kt -E "canScheduleExactAlarms|VibratorManager|ForegroundServiceStartNotAllowedException|writeCharacteristic\([^)]*,[^)]*,|FOREGROUND_SERVICE_TYPE|android\.window\.|UserManager|DeviceManager|NotificationManagerCompat.*IMPORTANCE" --exclude-dir=build --exclude-dir=.git --exclude-dir=.gradle
grep -rn --include=*.kt -oE "VERSION_CODES\.[A-Z_]+" --exclude-dir=build | sort | uniq -c
```

### Step 5: 对比 Phase 0 清点记录 + 现在 lint 发现的

Phase 0 记录了：
- VERSION_CODES：12 文件 17 处
- SDK_INT 判断：8 文件 10 处
- Manifest 权限：40 行

跑 lint + 手动 grep 后，看：
- 这些 Phase 0 记录的点是否都加了版本守卫
- lint 有没有发现 Phase 0 没查到的新问题
- 手动 grep 有没有新冒出来的 API 31+ 调用

### Step 6: 清理

- 删除 `.trae/tmp/minsdk_linux.init.gradle`
- 不提交 lint 日志
- 给用户一份报告

## Dependencies and Considerations

- **lint 是 Android Lint**：只扫 Android 模块，不扫 KMP commonMain（但能扫 KMP 的 androidMain）
- **编译通过 ≠ lint 通过**：lint 报的 Warning 很多可以忽略，只关注 `Error` 级别的
- **Phase 0 清点可能不完整**：当时可能漏了一些 API 31+ 调用，现在 lint 能补

## Validation

- `./gradlew :app:lintFullDebug` 能执行完（即使有 error 也是 lint error，不是 configure/build error）
- lint 报告 txt 文件生成
- 手动 grep 输出和 Phase 0 清点做对比

## Risks

- **lint 跑很久**：第一次跑可能 10-20 分钟，机器上 714 任务那次编了 16 分钟
- **lint 报错很多是噪音**：Android 8.1 的 deprecation、compose 的建议等——只关注 API level 相关的
- **KMP 模块 lint 盲区**：必须手动 grep 补，只跑 lint 不够
