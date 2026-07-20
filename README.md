# HyperTweaks

HyperTweaks 是一个面向 HyperOS 的个人 LSPosed 模块，用于实现一些系统行为微调和个人使用场景优化。

当前功能主要包括：

- AOSP IME 导航栏行为优化
- 输入法导航栏按钮自定义
- Android 16 输入法切换按钮行为调整
- 应用后台保活保护
- Miuix UI 动态取色设置页
- 中英文界面支持

> 本项目主要用于个人设备和个人需求，不保证适配所有 HyperOS / Android 版本。
>
> 测试设备：Xiaomi 15u，HyperOS 3.0.303.0

## 功能简介

### 功能开关

HyperTweaks 默认不会安装功能 Hook。需要在设置页中分别启用：

- 输入法微调
- 应用保活

修改开关后，Hook 安装状态通常需要重启目标进程或重启设备后完全生效。

### IME 导航栏优化

在 Android 16 / API 36 中，系统输入法切换按钮的短按行为变为直接切换到下一个输入法，长按才会弹出输入法选择器。
HyperTweaks 增加了一个额外选项，可以将短按行为改为直接弹出“选择输入法”列表。

当前 system_server 侧 IME Hook 的版本策略：

- Android 13 / API 33 到 Android 15 / API 35：使用 `InputMethodManagerServiceHook`
- Android 16 / API 36 及以上：使用 `InputMethodManagerServiceImplHook`
- Android 12 / API 32 及以下：不支持当前 system_server 侧 IME Hook

> 说明：`InputMethodManagerServiceHook` 依赖 AOSP 的 `InputMethodManagerService` IME 导航栏结构，
> 同时还会尝试调用 HyperOS 私有的 `InputMethodManagerServiceStub`。
> 因此 Android 版本满足条件不代表所有 ROM 都一定可用。

### 应用后台保活

HyperTweaks 支持通过包名列表保护指定应用，减少 HyperOS 后台清理导致的应用重载问题。

主要用于解决类似 Firefox / Fenix 在后台一段时间后返回前台页面重载的问题。

保活功能会尝试：

- 拦截部分系统后台清理路径
- 拦截部分 package cleanup / process kill 行为
- 保护目标应用主进程和子进程
- 调整目标进程的 `oom_score_adj`

示例包名：

```text
org.mozilla.fenix
org.mozilla.firefox
org.mozilla.firefox_beta
org.mozilla.focus
```

> 保活功能不能保证阻止所有系统级或内核级进程回收。它主要用于减少 HyperOS 主动后台清理导致的问题。

### 设置页

设置页使用 Kotlin、Jetpack Compose 和 Miuix UI 实现，并保留 Material 3 主题兼容层，支持：

- 系统 Monet 动态取色
- 深色模式
- 英文 / 简体中文
- 输入法导航栏配置
- 应用保活包名列表配置

当前固定使用 Miuix `0.9.3`。Miuix 仍属于实验性依赖，API 可能发生不兼容变更；
升级前必须查看 release notes 并验证项目适配层，不允许使用动态版本号。

## 构建

### 环境要求

建议使用：

- Android Studio 最新稳定版
- JDK 17 或更高版本
- Android SDK 36 / 37
- 已配置 Android SDK Build Tools
- 一台已安装 LSPosed 的 Android / HyperOS 设备

### 构建步骤

克隆项目后，在项目根目录执行：

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows PowerShell：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 启用模块

安装后：

1. 打开 LSPosed
2. 启用 HyperTweaks 模块
3. 勾选需要作用的范围
4. 重启手机

建议作用域至少包含：

```text
system
```

如果使用输入法相关功能，请勾选对应输入法包名，例如：

```text
com.google.android.inputmethod.latin
com.baidu.input_mi
com.sohu.inputmethod.sogou.xiaomi
com.iflytek.inputmethod.miui
com.tencent.wetype
```

如果使用保活功能，可以根据需要勾选：

```text
system
com.miui.powerkeeper
com.miui.securitycenter
com.miui.securitycenter.remote
com.lbe.security.miui
目标应用包名
```

例如 Firefox Nightly / Fenix：

```text
org.mozilla.fenix
```

## 调试日志

查看模块日志：

```bash
adb logcat | grep HyperTweaks
```

Windows PowerShell：

```powershell
adb logcat | rg HyperTweaks
```

保活功能常见关键日志：

```text
keep-alive packages updated
tracked protected process
write oom_score_adj
blocked package cleanup
```

查看目标应用进程的 `oom_score_adj`：

```powershell
adb shell 'ps -A | grep "[f]enix" | while read u p rest; do adj=$(cat "/proc/$p/oom_score_adj" 2>/dev/null); echo "$p $adj $rest"; done'
```

## 注意事项

- 本项目依赖 LSPosed / libxposed，仅适用于已安装并启用 LSPosed 的设备。
- Hook 系统和厂商私有 API 存在版本兼容风险。
- HyperOS 不同版本的后台管理实现可能不同，保活效果可能因设备和系统版本而异。
- 不建议把保活功能用于大量应用，可能增加内存占用和耗电。
- 本项目主要用于个人学习和个人设备，不建议在不了解风险的情况下用于主力机。

## 感谢

感谢以下项目和社区：

- [LSPosed](https://github.com/LSPosed/LSPosed)
- [libxposed](https://github.com/libxposed)
- [AOSP](https://android.googlesource.com/)
- [Jetpack Compose](https://developer.android.com/compose)
- [Material Design 3](https://m3.material.io/)
- [Miuix](https://github.com/compose-miuix-ui/miuix)
- [Mi_AOSP_IME](https://github.com/Howard20181/Mi_AOSP_IME)

特别感谢 `Mi_AOSP_IME` 项目提供的思路参考。本项目在其功能方向基础上重新设计为 Kotlin + Compose 的个人 HyperOS 微调模块。

## License

Copyright (c) 2026 togls. All rights reserved.

本项目公开发布仅用于个人学习、研究和参考目的。未经作者明确许可，不授权复制、修改、分发、再授权或商业使用。

本项目的部分功能方向参考了第三方开源项目。如果后续确认存在来源于第三方项目的派生代码，对应部分应遵守其上游开源协议。
