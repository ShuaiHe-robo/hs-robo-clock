<p align="center">
  <img src="app/src/main/res/mipmap-nodpi/daybreak_icon.png" alt="朝醒应用图标" width="160" height="160" />
</p>

# 朝醒（hs-robo-clock）

原生 Android 本地闹钟、计时器与定时应用锁，使用 Kotlin、Jetpack Compose 和 Material 3 构建，面向日常自用。

应用无需账号，没有云同步。规则与会话保存在设备本地，使用 Room、DataStore 和设备保护存储；当前 Manifest 未声明网络权限。

## 核心功能

### 闹钟

- 支持一次性和按星期重复，提供标签、振动及独立的“复健”开关。
- 启用“复健”后，需要答对一道两位数加减题才能正常关闭响铃。
- 支持默认闹钟铃声、系统铃声和本地音频文件，提供试听；文件无法读取或解码时尝试备用铃声。
- 提供下一次闹钟倒计时，并在重启、时间或时区变化后恢复调度。

### 计时器

- 同一时间运行一个计时器，时长范围为 1 秒至 24 小时。
- 提供 1 / 5 / 10 / 25 分钟快捷时长，支持暂停、继续和确认取消。
- 到时进入响铃流程，可选振动和“复健”；已经响铃的复健会话需答题关闭。

### 定时应用锁

- 按星期和时间段限制多个应用，支持跨午夜；重复日期按时段开始日解释。
- 通过无障碍服务识别前台应用并显示拦截蒙版，重叠时段合并受限应用。
- 提供应急解除：等待 60 秒后再次确认，等待期间继续限制；未来时段仍按计划执行。

### 界面

- 闹钟、专注、计时器、设置四个页面，支持系统深浅色主题。
- 薄荷配色、山景过渡、霞鹜文楷中文与 Gelasio 时间数字。
- 针对窄窗口、大字体和横屏调整布局，设置页集中提供运行权限与使用说明入口。

## 运行要求与首次使用

设备要求：Android 10（API 29）或更高版本。

1. 在“设置”中授予精确闹钟和通知权限，并检查锁屏全屏提醒状态。系统可能展示通知横幅，可从通知进入响铃页。
2. 使用应用锁时，手动开启“朝醒 · 定时应用锁”无障碍服务。侧载安装若出现“受限设置”，按系统提示允许受限设置后再开启服务。
3. 检查应用的后台运行与电池管理设置，避免被休眠或后台限制；不同设备的设置入口可能不同。
4. 在允许声音的环境创建短时闹钟与计时器，确认铃声、振动、锁屏提醒和关闭流程，再保存日常计划。

未授予精确闹钟权限时会显示未排程；恢复权限后可使用“重建待执行计划”。勿扰模式、闹钟音量、通知渠道与后台策略也会影响提醒效果。

## 构建与安装

### 工具链

| 项目 | 当前配置 |
| --- | --- |
| Android Gradle Plugin / Gradle Wrapper | 9.3.1 / 9.5.0 |
| Kotlin / Compose 编译插件 | AGP 内置 Kotlin / 2.2.10 |
| Compose BOM / KSP | 2026.02.01 / 2.3.6 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29 |
| 开发使用的 JDK / 字节码目标 | JDK 25 / Java 17 |
| 应用包名 / 版本 | `dev.daybreak.clock` / `0.1.0` |

安装 Android SDK Platform 37，并将 `JAVA_HOME` 指向本机 JDK。Java 17 是字节码目标，不代表当前 AGP / Gradle 工具链可用 JDK 17 构建。首次构建需要联网下载依赖。

在项目根目录创建本机 `local.properties`，将以下示例替换为实际 SDK 路径；该文件不提交到仓库：

```properties
sdk.dir=C:/Android/Sdk
```

### 生成 APK

Windows（PowerShell）：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

构建输出：

| 类型 | APK 路径 |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

Release 启用 R8 与资源收缩，当前使用本机 debug 密钥签名，适用于自用构建。正式发布前需要单独配置发布签名。

### 安装到设备

启用设备 USB 调试并确认电脑授权，将 SDK 的 `platform-tools` 加入 `PATH`，然后执行：

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

连接多个设备时，使用 `adb -s <设备序列号> install -r ...` 指定目标设备。

## 测试

在项目根目录运行；Windows 使用 `gradlew.bat`，macOS / Linux 使用 `./gradlew`：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

单元测试覆盖时间规则、计时器、倒计时文本和服务状态等逻辑；设备测试覆盖执行、恢复与界面流程。`connectedDebugAndroidTest` 需要已连接的设备或模拟器。真实声音、振动、锁屏和长待机效果仍需在目标手机上验证。

## 源码结构

主代码位于 `app/src/main/java/dev/daybreak/clock/`：

| 目录 | 职责 |
| --- | --- |
| `ui/`、`feature/` | Compose 界面、编辑流程与 ViewModel |
| `domain/` | 时间计算、跨午夜规则、算术题与应急等待 |
| `data/` | Room 数据库、DataStore 偏好及设备保护执行日志 |
| `platform/alarm/` | 精确调度、响铃、通知、铃声与恢复 |
| `platform/focus/` | 专注会话、应用筛选、无障碍拦截与边界刷新 |
| `app/src/test/`、`app/src/androidTest/` | 单元测试与设备测试 |

## 现有限制

- 当前没有贪睡功能。算术题约束应用内的正常关闭流程，无法阻止关机、系统终止或调低音量。
- 应用锁依赖无障碍服务，强停、卸载、撤销权限、修改系统时间及系统后台限制均可能影响运行；分屏、弹窗和画中画尚未完整验证。
- 应用锁依赖解锁后的数据，暂不支持设备重启后首次解锁前的拦截。
- 闹钟提供 Direct Boot 恢复路径，但首次解锁前响铃、Doze 与长待机行为尚未完成完整真机验证。
- 当前启用 16 KB 页大小兼容模式；不能视为全部原生库均已满足 16 KB 对齐或已完成真机兼容验证。

## 项目文档与字体许可

- [实现计划](docs/CODE_PLAN.md)
- [计时器行为与验证记录](docs/TIMER_ACCEPTANCE.md)
- [应用锁恢复记录](docs/FOCUS_RECOVERY.md)
- [设备验收范围](docs/DEVICE_ACCEPTANCE.md)

验收文档属于历史记录，不代表当前提交已重新执行全部测试；其中部分截图与日志仅保存在本地，未随仓库上传。

内置字体使用 SIL Open Font License 1.1，许可文本随项目保留：[Gelasio](app/src/main/assets/fonts/Gelasio-OFL.txt)、[霞鹜文楷 GB Lite](app/src/main/assets/fonts/LXGWWenKaiGBLite-OFL.txt)。字体许可仅适用于对应字体；项目尚未提供独立的代码许可证。
