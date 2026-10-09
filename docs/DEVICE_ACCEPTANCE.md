# 朝醒 · 设备验收

日期：2026-10-08。状态使用“待测 / 通过 / 失败 / 未覆盖”，通过项必须记录设备、版本、步骤及证据。**S25+ 已完成 Debug 安装、启动和深色主界面静默验证；声音、振动及新增权限未测。** 用户在图书馆，本轮手机不试听、不响铃、不振动。模拟器与真机结果分别记录。

## 设备与工具链

| 项目 | 当前记录 |
| --- | --- |
| 主设备 | 三星 Galaxy S25+，SM-S9360 |
| 系统 | Android 16，API 36；One UI 8.5，属性值 80500 |
| 固件 | S9360ZCSDCZH1 |
| 实机页大小 | `getconf PAGE_SIZE` 返回 4096 |
| 连接 | USB 调试已开启，ADB 连接已确认 |
| 工具链 | AGP 9.3.1、Gradle 9.5.0、Compose 插件 2.2.10、BOM 2026.02.01 |
| 关键依赖 | Room 2.8.4、DataStore 1.2.1、Graphics Path 1.1.0 |
| SDK / JDK | compile/target 37、min 29；开发机 JBR 25.0.2，字节码目标 17 |
| 模拟器条件 | Pixel_10，`android-37.1/google_apis_playstore_ps16k/x86_64` 系统镜像，16 KB 页；`-no-window -no-audio -no-snapshot` |
| 已交付 APK | [daybreak-0.1.0.apk](../artifacts/daybreak-0.1.0.apk)，Release，2,984,978 字节（约 2.85 MiB） |
| 签名及安装 | 本机 debug 密钥自用签名；apksigner v2 验证通过；S25+ `adb install -r` 成功 |
| 仍未覆盖 | 真机声音/振动、锁屏/Doze、三星多窗口、真机 Direct Boot；大字号与横屏 |

minSdk 29 表示安装下限，尚未覆盖 Android 10 至其他版本的兼容性测试。API 36 实机当前结论限于安装和静默界面显示。

界面初稿借鉴 `design-taste-frontend` 的配色、排版与层级，现按用户指定的 `gpt-taste` 复核按钮可读性、标题层级和留白。该 skill 主要规定网页 AIDA/GSAP 设计，本项目复核范围为原生 Compose Material 3 的视觉原则。已目视检查 [S25+ 深色主界面](screenshots/s25-main-dark.png)、[模拟器深色主界面](screenshots/emulator-main.png) 与 [模拟器浅色主界面](screenshots/emulator-main-light.png)，按钮、文字及状态/导航栏清楚；横屏、大字体和全部交互仍需后续检查。

## 构建与证据

在项目根目录按 [README](../README.md#构建与安装) 构建。保存以下报告和 APK 摘要，再填写结果。

```powershell
Get-FileHash -Algorithm SHA256 '.\artifacts\daybreak-0.1.0.apk'
# 查看当前设备；多设备时始终指定序列号。
$clockAdb = 'E:\AndroidSDK\platform-tools\adb.exe'
& $clockAdb devices -l
$clockDevice = '填写设备序列号'
& $clockAdb -s $clockDevice shell getprop ro.product.model
& $clockAdb -s $clockDevice shell getprop ro.build.version.release
& $clockAdb -s $clockDevice shell getprop ro.build.version.sdk
& $clockAdb -s $clockDevice shell getprop ro.build.version.oneui
& $clockAdb -s $clockDevice shell getprop ro.build.PDA
```

报告预期位置：`app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/reports/lint-results-debug.html`、`app/build/reports/androidTests/connected/debug/index.html`。目录不存在表示本次未产生报告，不能据此填写通过。

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 最终 Debug / 测试 APK / Release 构建 | 通过，1 分 18 秒 | [最终构建记录](../final-build.log) |
| `:app:testDebugUnitTest` | 9 项通过，0 失败/错误/跳过 | [JUnit XML](../app/build/test-results/testDebugUnitTest/TEST-dev.daybreak.clock.domain.TimeRulesTest.xml) |
| `:app:lintDebug` | 0 errors、20 warnings | [Lint 文本报告](../app/build/reports/lint-results-debug.txt) |
| S25+ Debug 安装和启动 | 通过，仅静默界面 | [实机主界面](screenshots/s25-main-dark.png) |
| 无声模拟器原 3 个集成测试 | 通过，8.461 秒 | [原测试记录](../instrumentation-output.log) |
| 扩展 4 项首次运行 | 失败，1 项测试观察器失败 | [失败记录](../expanded-instrumentation-output.log) |
| `FocusPlatformTest` 单独复测 | 通过，1 项，47.75 秒 | [修正后记录](../focus-platform-output.log) |
| 修正后完整 4 项回归（Debug） | 通过，15.419 秒，0 跳过；2 ExecutionTest + 1 SilentPlatformTest + 1 FocusPlatformTest | [最终回归记录](../final-regression.log) |
| PIN 重启后 Direct Boot（Debug） | 16 KB 无声模拟器首次解锁前触发、错答拒绝、正确答题结束通过 | [状态证据](../direct-boot-platform.log)、[题目界面](screenshots/emulator-direct-boot.png)、[错答界面](screenshots/emulator-boot-wrong.png) |
| 首次解锁后日志合并（Debug） | 通过，1 项，0.264 秒 | [合并验证记录](../direct-boot-verify.log) |
| Release 与最终 APK | 已交付；R8/资源收缩启用，本机 debug 密钥签名；S25+ 覆盖安装成功 | [自用 APK](../artifacts/daybreak-0.1.0.apk) |
| Release 主界面启动 | 16 KB 无声模拟器安装并启动 MainActivity；`am start -W` Status ok，1.071 秒；UI 显示朝醒，当次 crash buffer 空 | 现场 ADB 输出已检查；[截图](screenshots/emulator-main.png) 为 Debug 界面参考 |
| 最终 APK 签名/ZIP 对齐 | apksigner v2 通过；`zipalign -c -P 16 4` 退出码 0 | APK 摘要如下；ELF 对齐边界见后文 |

最终 Release SHA-256：`FFCAA04E574782F3569195EA9BF8B23B72B49F2A242B4BB08D35B7C35B4A305B`。大小 2,984,978 字节；使用本机 debug 密钥方便同签名更新，商店签名未配置。

Lint 保留版本与 UseKtx 提示，以及当前报告的 UnusedAttribute、DataExtractionRules 和 UnusedResources 提示；0 errors 不等于无警告。扩展测试失败来自覆盖层测试观察器，修正为 `WindowInspector` 检查并在测试中重连服务后，单项复测及最终完整 4 项回归均通过；失败那轮保留，不作为通过证据。

模拟器原 3 项包含旧回调失效/业务答案校验、重叠窗口/应急范围，以及精确回调启动前台服务和失效 URI 回退标记。模拟器禁用音频输出，因此只验证执行状态、通知及回退标记，不验证实际扬声器音量或听觉效果。新增覆盖层单项验证前台 Chrome、删除源规则后保护保留、自然到期移除。

功能 4 项回归和 Direct Boot 前后验证均使用 Debug APK；最终 Release 的额外验证范围是安装及主界面启动，不将 Debug 功能测试计作 Release 全流程测试。

Direct Boot 采用 PIN 后真实重启模拟器，首次解锁前用户状态为 `RUNNING_LOCKED`。系统 `setAlarmClock` 触发前台服务、通知 1001 和全屏题目 `68 − 50`；界面输入 17 被拒绝，输入 18 后服务结束并返回锁屏，此时用户仍为 `RUNNING_LOCKED`。解锁后的独立测试验证同题答案 18、设备保护日志中的 ENDED 结果合并至 Room、一次性闹钟禁用，并删除测试规则；模拟器 PIN 已清除。该结果验证 Debug APK 在无声模拟器的重启恢复链路，自选铃声实际音效、振动与 S25+ 重启行为仍未验收。

16 KB 静态检查：`zipalign -P 16` 通过，DataStore 所有 64 位 `.so` 的 LOAD/RELRO 对齐通过；Graphics Path 1.1.0 的 RELRO 未通过。当前使用 `android:pageSizeCompat="enabled"` 的系统兼容模式，16 KB 模拟器完整 4 项回归和 Direct Boot 前后验证通过，不能称全部原生对齐通过。参见 [Android 官方页大小说明](https://developer.android.com/guide/practices/page-sizes) 和 [Graphics 版本记录](https://developer.android.com/jetpack/androidx/releases/graphics)。

## 首次配置

本轮 S25+ 未新增授权、未改后台策略。下面是后续验收配置步骤，应在用户允许的环境进行。

先记录通知、精确闹钟、全屏提醒、无障碍服务、闹钟音量、勿扰及三星后台限制的实际状态。使用朝醒设置页进入权限界面；手动开启无障碍服务。侧载若出现受限设置提示，按系统应用详情页的入口允许后再开启，具体名称以手机为准，参见 [Android 官方说明](https://support.google.com/android/answer/12623953)。

检查三星电池设置的休眠、深度休眠和从不休眠列表，以及应用的后台电池选项。记录修改前后值，不把加入从不休眠当成测试结论。后台限制的含义见 [三星官方说明](https://developer.samsung.com/mobile/app-management.html)。

## 功能清单

每项先使用近期测试计划，记录预定时刻、实际时刻、手机状态、是否有声音/振动/通知/全屏和复现步骤。声音和振动检查留待允许声音的环境。下表为真机验收，相关模拟器证据仅记部分覆盖。

| ID | 操作与条件 | 预期与记录重点 | 结果 |
| --- | --- | --- | --- |
| A01 | 新建、编辑、开关、删除一次性及重复闹钟，重进应用 | 数据保留；下一次时间正确；旧调度取消；排程失败可见 | 待测 |
| A02 | 未选文件，近期闹钟触发 | 默认铃声、振动按配置执行；未选文件不等于静音 | 待测 |
| A03 | 选择系统铃声和本地 MP3；取消另一次选择；试听并停止 | 展示名与配置恢复正确；取消不覆盖旧配置；离开编辑页停止试听 | 待测 |
| A04 | 自选文件保存后重进应用及重启解锁 | 持久授权仍可读取；授权或解码失败时不虚假保存 | 待测 |
| A05 | 删除自选文件、撤销读取授权、选择无效音频或不可及时读取的提供器 | 记录错误、准备时间与回退；尝试默认铃声，再尝试内置备用音，不静默漏响 | 真机待测；模拟器失效 URI 回退标记通过 |
| A06 | 开启复健后输入错误、非整数、正确答案；返回、旋转、重进响铃页 | 错答继续响；题目不变；正常关闭入口均受业务验证；正确答案结束 | 真机待测；生成器及业务答案校验通过 |
| A07 | 响铃时修改/关闭/删除源闹钟；重复投递；两个同时到达 | 当前会话与题目保留；同次触发不重复；会话按顺序处理 | 真机待测；旧回调/去重/删除保留会话通过 |
| A08 | 熄屏锁屏、划走最近任务后触发；手机处于其他应用 | 记录实际精确性与声音；全屏或通知入口可用；退出页面不结束声音 | 待测 |
| A09 | 长待机及 Doze 下触发，分别记录三星电池策略 | 记录触发偏差、服务生存及是否需要手动设置 | 待测 |
| A10 | 拒绝/撤销精确、通知、全屏权限，再恢复 | 页面显示实际能力；不可排程有原因；全屏拒绝时通知入口可用；恢复可重建 | 待测 |
| A11 | 不同闹钟音量、静音、勿扰、来电/音频焦点中断 | 记录声音及中断恢复行为，不假定系统所有模式均有声音 | 待测 |
| A12 | 设闹钟后重启，保持未首次解锁至触发 | 不读取凭据保护 Room 或自选文件；备用音、持久题目及正常关闭可用 | 真机待测；16 KB 无声模拟器实际重启、固定题目与答题关闭通过，声音/振动未测 |
| A13 | 重启错过闹钟分别小于和大于 10 分钟；之后解锁 | 近期补响或记录错过；日志与 Room 合并，不重复执行 | 待测 |
| A14 | 修改时间/时区、APK 覆盖升级、恢复精确授权 | 重建未来调度；版本失效回调不响；当前会话不被误删除 | 待测 |
| F01 | 设置跨午夜、多个目标和重叠规则 | 起止正确；星期按开始日；受限包名取并集；相同时刻拒绝保存 | 待测 |
| F02 | 窗口开始前已处于目标应用；到期仍停留；切换允许应用 | 开始出现覆盖，到期和允许应用移除；记录是否需额外事件触发 | 真机待测；模拟器前台 Chrome 覆盖及到期移除通过 |
| F03 | 从桌面、最近任务、通知进入目标；频繁切换 | 覆盖生效、不循环；允许应用与系统设置可正常使用 | 待测 |
| F04 | 三星分屏、弹窗和画中画，各自进入与退出目标 | 分别写实测结论及未覆盖入口；不能统一填写“所有入口可拦截” | 待测 |
| F05 | 撤销/重启无障碍服务，重启手机后解锁恢复 | 撤销时显示不可用；恢复后重新计算窗口并正确移除旧覆盖 | 待测 |
| F06 | 有效窗口内编辑/关闭/删除规则 | 已开始会话继续；未来窗口按新规则执行 | 真机待测；模拟器删除源规则仍保护通过 |
| F07 | 发起应急等待，尝试提前确认、取消、重建页面/进程 | 提前确认拒绝；等待期间继续锁定；重建不跳过 60 秒；取消恢复 ACTIVE | 待测 |
| F08 | 等待期间重启；等待完二次确认；等待时新窗口开始 | 重启重新等待；仅释放原窗口；新窗口和未来重复仍生效；本地记录存在 | 待测 |
| F09 | 选择应用及使用桌面、电话、紧急呼叫、设置 | 受保护应用不进入目标；记录通话入口可用。紧急呼叫仅检查入口，不拨出测试呼叫 | 待测 |
| U01 | 深浅色、横屏、较大字体、键盘、系统导航栏与返回 | 时间/题目可读，按钮可触达，无遮挡或文本截断；反馈与禁用状态明确 | S25+ 深色及模拟器浅色主界面目视通过；大字号、横屏等其余待测 |

Direct Boot 验证需真实重启并保持未解锁；普通锁屏不能替代。设备保护存储的可用条件见 [Android 官方文档](https://developer.android.com/privacy-and-security/direct-boot)。

可选 Doze 复现命令如下，执行后用 `finally` 恢复测试状态；这只能覆盖强制空闲场景，仍需真实长待机观察。

```powershell
try {
    & $clockAdb -s $clockDevice shell dumpsys battery unplug
    & $clockAdb -s $clockDevice shell dumpsys deviceidle force-idle
    # 在手机上观察预先设置的近期闹钟并记录实际触发。
    Read-Host '观察和记录完成后按 Enter 恢复测试状态'
} finally {
    & $clockAdb -s $clockDevice shell dumpsys deviceidle unforce
    & $clockAdb -s $clockDevice shell dumpsys battery reset
}
```

## 阶段结论与限制

| 阶段 | 当前结论 | 尚需证据 |
| --- | --- | --- |
| P0 工程 | 通过；最终构建、自用 Release 交付及 S25+ 更新安装完成 | 工具链与 APK 摘要已记录 |
| P1 风险原型 | 未通过 | 锁屏/待机响铃及全屏降级、应用锁起止、多窗口结论 |
| P2–P4 功能 | 代码已编写，待验收 | 基础闹钟、算术、应用锁及应急行为 |
| P5 恢复回归 | 未通过；自动回归和模拟器恢复验证已完成 | 声音/振动及实机恢复回归仍未验收 |

强停、卸载、撤销权限、关机、调低音量和修改系统时间属于平台边界，不将“系统终止后仍保证运行”作为退出标准。应用锁不是系统级永久禁开。其他实机型号、API 29–35、API 37 实机、三星分屏/弹窗/画中画目前均无通过结论；模拟器结果只适用于明确记录的镜像、APK 和条件。

问题记录采用：`ID / APK 摘要 / 设备与系统 / 配置 / 复现步骤 / 预期 / 实际 / 日志或截图 / 复测结果`。后续验证完成后逐项更新，保留失败及未覆盖条件。
