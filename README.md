# 朝醒

原生 Android 本地闹钟、计时器与定时应用锁，自用 APK。界面使用 Kotlin、Jetpack Compose 和 Material 3，规则、题目及应急记录保存在手机，没有账户或云同步。

**本轮计时器一屏布局（2026-10-09）：[最终 APK](artifacts/daybreak-0.1.0-timer-fit.apk) 已再次覆盖安装到 Galaxy S25+。** 空闲页移除重复大号时钟，时长与提醒卡片压缩间距，四个快捷时长并排；宽矮窗口改为左右两栏，矮屏省略空闲页重复标题并保留完整山景与时间卡标题。[claude-design 交互稿](design/朝醒%20·%20计时器一屏布局.html)、[实机竖屏](docs/screenshots/ui-timer-fit/phone-portrait.png)、[实机横屏](docs/screenshots/ui-timer-fit/phone-landscape.png) 与 [验收记录](docs/TIMER_LAYOUT_ACCEPTANCE.md) 已保存。手机全卡可见，10 分钟快捷开始、暂停、继续、取消通过；最终 Debug / Release / Lint 成功（0 错误、23 警告），本轮 23 项单元测试、3 次不同配置的计时器流程回归及 3 项动效测试通过。常规窗口设置区首屏完整可见；极端大字号、键盘或权限提示需要更多高度时仍允许滚动。

**此前添加入口（2026-10-09）：[新版 APK](artifacts/daybreak-0.1.0-section-add.apk) 已覆盖安装到 Galaxy S25+。** 添加闹钟、添加计划分别放在“你的闹钟”“定时计划”标题右侧，页底悬浮按钮已移除；窄窗口或大字体时按钮换行靠右，保持当前配色和字体。[claude-design 交互稿](design/朝醒%20·%20卡片区域添加入口.html) 与 [验收记录](docs/SECTION_ADD_ACCEPTANCE.md) 已保存。Debug / Release / AndroidTest / Lint 成功（0 错误、23 警告），1 项编辑流程测试通过；手机两个入口均能打开正确编辑页，返回未保存，原有 1 个闹钟、1 条专注计划保持。

**此前计时器山景（2026-10-09）：[新版 APK](artifacts/daybreak-0.1.0-timer-landscape.apk) 已覆盖安装到 Galaxy S25+ 并完成计时器实机测试。** 按用户指定的原生成 prompt 与专注页山景风格生成同系透明素材；首版“溪湾”已废弃。背景按屏幕全宽绘制，正文单独居中限宽，修正横屏裁切；太阳沿闹钟、专注、计时器、设置依次向左移动并缩小。[claude-design 交互稿](design/朝醒%20·%20计时器%20·%20同系山景.html)、[横屏实屏](docs/screenshots/ui-timer-art/landscape-after.png)、[浅色竖屏](docs/screenshots/ui-timer-art/portrait-light.png) 与 [完整 prompt 及验收记录](docs/TIMER_LANDSCAPE_ACCEPTANCE.md) 已保存。最终 Debug / Release / AndroidTest、23 项单元测试及 Lint 通过（0 错误、23 警告），8 项不同模拟器仪器测试及横竖屏四页检查通过；手机安装包哈希与本地一致。实机验证输入、暂停/跨页/旋转、横屏四页、后台到时、错误答案保持响铃、正确答案关闭与取消确认，用户实听确认“铃声和振动都正常”；[实机记录及截图](docs/TIMER_PHONE_ACCEPTANCE.md) 已保存。锁屏、Doze 与真实重启尚未测试。

**此前计时器功能（2026-10-09）：[计时器 APK](artifacts/daybreak-0.1.0-timer.apk) 已生成，新增“计时器”页面，沿用 `claude-design` 与当前视觉语言。** 支持 1 秒至 24 小时的时分秒设置、1 / 5 / 10 / 25 分钟快捷时长、暂停/继续及确认取消；到时触发现有闹钟响铃流程，可选振动与“复健”（答对两位数加减题才能关闭）。采用 A 居中时间卡片，保留文楷中文、Gelasio 数字、薄荷配色与山景。[三方向交互设计](design/朝醒%20·%20计时器.html)、[原生实屏及验收范围](docs/TIMER_ACCEPTANCE.md) 已保存。最终 Debug / Release、21 项单元测试及 Lint 通过（0 错误、23 警告）；10 项不同的模拟器仪器测试通过，并补查深浅色及 320 dp / 2 倍字体。此功能版本随后已安装到 Galaxy S25+；声音、振动与长待机未作真机验收。

**此前专注时段延长修复（2026-10-09）：[专注时段延长修复 APK](artifacts/daybreak-0.1.0-focus-extension-fix.apk) 已安装到 Galaxy S25+。** 修复旧过期快照阻止延长时段生效的问题；用户现有每天 01:00—15:30、5 个应用的同一计划恢复 X 拦截，移除朝醒最近任务后仍受限。10 项专注仪器测试、18 项单元测试与 Debug / Release / AndroidTest / Lint 通过（0 错误、21 警告）。屏幕超时保持用户要求的 10 分钟；原因、验证范围与截图见 [延长时段修复记录](docs/FOCUS_EXTENSION_FIX.md)。

**此前蒙版轮次（2026-10-09）：[专注蒙版 APK](artifacts/daybreak-0.1.0-focus-overlay.apk) 已覆盖安装到 Galaxy S25+。** 采用 A“晨间留白”：透明纸感山间小路、应用名、衬线时分秒倒计时与桌面/应急操作，支持系统深浅色、实时配置更新及大字体滚动。[三方向设计](design/朝醒%20·%20专注蒙版-v1.html) 与 [原生实屏及验证记录](docs/FOCUS_OVERLAY_DESIGN.md) 已保存。18 项单元测试、5 项不同仪器用例和最终 Debug 构建的交互用例重跑通过，Lint 为 0 错误、21 警告。拦截与服务恢复规则沿用；该轮蒙版 UI/交互在模拟器验证，锁屏手机未操作 X 蒙版。

**此前专注恢复轮次（2026-10-09）：[修复版 APK](artifacts/daybreak-0.1.0-focus-recovery.apk) 已覆盖安装到 Galaxy S25+ 并验收 X 拦截。** 修复无障碍服务重连漏拦、断开误报可用及检查异常恢复；保留现有界面。首轮在“受限制”配置、服务正常时，打开 X 与回桌面重开均被拦截，随后发生三星 MARs 强停；改为“不受限制”并重启服务后，第二个临时窗口内打开 X 与划掉朝醒后重开 X 均被拦截，应急解除正常。[最终权限实屏](docs/screenshots/focus-recovery/phone-permissions-running.png) 显示服务已连接、检查正常，至 13:30 左右最终检查无新增进程退出。18 项单元测试和 6 项模拟器回归通过，Debug / Release / Lint 成功（0 错误、20 警告）。真机自然到期、锁屏与长待机等仍待验证，原因、证据与范围见 [专注恢复记录](docs/FOCUS_RECOVERY.md)。

上一轮 **[设置抽屉版 APK](artifacts/daybreak-0.1.0-settings-drawers.apk)** 当时已生成并覆盖安装到 Galaxy S25+，约 11.25 MiB。设置主页保留运行权限摘要、外观与计划、使用说明入口；四项系统权限与原有说明/应急记录分别收进两个底部抽屉。历史入口：[交互设计稿](design/朝醒%20·%20设置抽屉.html)、[模拟器实屏总览](docs/screenshots/ui-settings-drawers/settings-drawers-preview.png)、[界面更新记录](docs/UI_REFRESH.md)。当时 Debug / Release / Lint 成功，已人工检查最终版抽屉交互、深浅色及 320 dp / 2 倍字体；没有新增真机截图，未重跑业务、动画或 Direct Boot 测试。

上一轮 [平滑切页版 APK](artifacts/daybreak-0.1.0-smooth-motion.apk)、[交互稿](design/朝醒%20·%20平滑切页.html)、[正常速度 GIF](docs/screenshots/ui-motion-interruption/emulator-normal-rapid.gif) / [视频](docs/screenshots/ui-motion-interruption/emulator-normal-rapid.mp4)、[5 倍慢动画证据](docs/screenshots/ui-motion-interruption/emulator-slow-rapid.mp4) 保留。当时三项动画回归通过，修复中途切页加速；该修复源码保持；本轮计时器重跑了 3 项山景动画回归，未重录动效。

上一轮 [低日山景版 APK](artifacts/daybreak-0.1.0-low-sun.apk)、[交互稿](design/朝醒%20·%20低日山景.html)、[设置太阳前后对比](docs/screenshots/ui-low-sun/settings-sun-comparison.png) 保留。当时完成 Release / Lint、签名对齐、真机覆盖安装及模拟器位置检查，未重跑逻辑测试或新增真机截图；三页山脊锚点保持在当前版本中。

上一轮 [山景流转版 APK](artifacts/daybreak-0.1.0-landscape-motion.apk)、[交互稿](design/朝醒%20·%20山景流转.html)、[真机三页](docs/screenshots/ui-landscape-motion/phone-three-scenes.png)、[模拟器动效视频](docs/screenshots/ui-landscape-motion/emulator-motion.mp4) / [GIF](docs/screenshots/ui-landscape-motion/emulator-motion.gif) 与 [GPT Image 生成记录](design/landscape-motion-prompts.md) 保留。当时完成构建、Lint、1 项倒计时概览测试和真机三页检查；视频与该轮验收均为上一版证据。

上一轮 [专注卡片版 APK](artifacts/daybreak-0.1.0-focus-card.apk)、[三方案设计预览（采用 A）](design/朝醒%20·%20专注卡片.html)、[真机卡片预览](docs/screenshots/ui-focus-card/phone-focus-card-preview.png)、[完整真机截图](docs/screenshots/ui-focus-card/phone-focus-dark.png) 与 [模拟器 Release 预览](docs/screenshots/ui-focus-card/focus-card-preview.png) 保留为历史版本。当时完成构建、Lint、1 项概览测试与 S25+ 安装检查；名称与开关同行、开始/结束两列及大字号自动纵排保持在当前版本中。

上一轮 [居中倒计时版 APK](artifacts/daybreak-0.1.0-countdown.apk)、[HTML 预览](design/朝醒%20·%20居中倒计时.html)、[示例总览](docs/screenshots/ui-useful-overview/overview.png) 与 [真机三页](docs/screenshots/ui-useful-overview/phone-overview.png) 保留为历史版本。当时覆盖安装到 S25+，15 项单元测试与 1 项概览测试通过，并检查了真机三页和模拟器闹钟/设置 2 倍字体布局；居中倒计时保持在当前版本中。

上一轮 [楷体版 APK](artifacts/daybreak-0.1.0-kai.apk)、[预览](design/朝醒%20·%20楷体版.html)、[真机预览图](docs/screenshots/ui-kai/phone-kai-preview.png) 和 [完整截图](docs/screenshots/ui-kai/phone-after.png) 保留为历史版本。当时确认了中文文楷、名称/重复日期基线及真机更新，模拟器浅色与 2 倍字体检查也保持历史范围；字体和闹钟卡样式保留在当前版本中。

上一轮 [紧凑底栏版 APK](artifacts/daybreak-0.1.0-compact-navigation.apk)、[预览](design/朝醒%20·%20紧凑底栏.html) 与 [导航对比图](docs/screenshots/ui-compact-navigation/navigation-comparison.png) 保留为历史版本。当时检查了三页切换、大字号纵排与系统三键导航，未操作真机；紧凑底栏布局保留在当前版本中。

上一轮 [衬线日出版 APK](artifacts/daybreak-0.1.0-serif-sunrise.apk)、[预览](design/朝醒%20·%20衬线日出版.html) 和 [截图总览](docs/screenshots/ui-serif-sunrise/overview.png) 保留为历史版本。当时 Debug / Release 构建、9 项单元测试和 1 项编辑流程测试通过。Gelasio 衬线时间、清晨薄荷配色与列表滚入日出轮廓下方的呈现保持在当前版本中。

更早的 [清晨薄荷 APK](artifacts/daybreak-0.1.0-ui-refresh.apk) 与 [截图总览](docs/screenshots/ui-refresh/overview.png) 保留为历史版本。当时 Debug / Release 构建、9 项单元测试、4 项原有平台回归及 1 项编辑流程测试通过，并检查了深浅色、窄屏大字号与横屏编辑布局；原 4 项平台套件本轮未整套重跑，当前计时器验证范围见计时器记录。

历史首版 [自用 APK](artifacts/daybreak-0.1.0.apk) 曾成功更新到 Galaxy S25+，也在 16 KB 无声模拟器启动主界面；当时 9 个单元测试、4 项模拟器回归及重启前后 Direct Boot 验证通过。首版手机验收只做静默界面验证，未授予新权限，也未试听、响铃或振动。真机声音、Doze 和三星多窗口仍需验收；此前证据见 [设备验收记录](docs/DEVICE_ACCEPTANCE.md)，原始需求见 [代码计划](docs/CODE_PLAN.md)。

## 首版行为

- 本轮新增独立计时器页面，导航顺序为“闹钟 / 专注 / 计时器 / 设置”。同一时间运行一个计时器；复健在开始前选择，到时后必须答对题目，取消操作不能绕过正在响铃的复健会话。持久化恢复与本轮验收见 [计时器记录](docs/TIMER_ACCEPTANCE.md)。
- 闹钟支持一次性、按星期重复、标签、振动和独立的“复健”开关。启用复健后，答对一道两位数加减题才能正常关闭；结果为非负整数，题目随本次响铃保存。首版没有贪睡。
- 铃声可用默认闹钟铃声、系统铃声或系统文件选择器选取的音频，包括可解码的 MP3。自选文件保存 `content URI` 和持久读取授权，支持试听/停止；取消选择保持原配置。读取或解码失败尝试系统默认铃声，再尝试内置备用音。
- 专注规则支持星期、多个应用和跨午夜窗口。星期按开始日解释；开始与结束相同会被拒绝。重叠窗口取受限应用并集。
- 应急解除绑定发起时的有效窗口，等待 60 秒后再次确认；等待期间继续锁定。取消等待恢复锁定，重启后重新完整等待。之后才开始的窗口和未来重复计划仍生效。
- 修改、关闭或删除规则影响未来执行；已经开始的响铃和专注会话仍按快照执行。删除闹钟不会结束正在响铃的会话。

界面按用户指定的 `claude-design` 优化，采用清晨薄荷配色、Gelasio 时间数字、霞鹜文楷中文与底部山景。首页以居中倒计时和真实下一次日期时间为主；专注保留开始/结束倒计时，计划卡片将开始与结束分别呈现，跨午夜在结束标题标注“次日”，设置以两个底部抽屉收纳运行权限与使用说明，主页显示真实权限小计。导航使用横向胶囊，常规主体高 56 dp，窄窗口或大字号自动纵排。四页独立山景与共用太阳的 GPT Image 透明资源离线打包，山景以 520 ms 交叠；正文与全宽装饰背景分别布局，同一太阳以 760 ms 向左移动并缩小；列表继续滚入自然轮廓下方，末项可完整滚出。原 [三种界面方向](design/朝醒%20·%20界面方向.html)、[衬线日出](design/朝醒%20·%20衬线日出版.html)、[紧凑底栏](design/朝醒%20·%20紧凑底栏.html)、[楷体](design/朝醒%20·%20楷体版.html)、[居中倒计时](design/朝醒%20·%20居中倒计时.html) 与 [专注卡片预览](design/朝醒%20·%20专注卡片.html) 均保留。实际应用继续使用原生 Jetpack Compose / Material 3，既有执行规则与存储保持原有实现。

设置抽屉当轮已查看最终版模拟器 [设置主页](docs/screenshots/ui-settings-drawers/emulator-settings.png)、[权限抽屉](docs/screenshots/ui-settings-drawers/emulator-permissions.png)、[说明抽屉](docs/screenshots/ui-settings-drawers/emulator-guide.png) 与 [浅色说明](docs/screenshots/ui-settings-drawers/emulator-guide-light.png)。主页不再展示长说明及应急记录；四个系统入口及返回、关闭按钮、Back 和标题区下滑关闭均已检查，未改变授权。320 dp / 2 倍字体时，主页可滚到说明入口，说明可滚到第 5 条记录。这些截图与界面验收保留为历史证据。

平滑切页当轮已查看模拟器 [正常速度过渡帧](docs/screenshots/ui-motion-interruption/normal-motion-frames.png) 与 [5 倍慢动画过渡帧](docs/screenshots/ui-motion-interruption/motion-frames.png)，动画未结束时多次反向/跨页，最终 [专注页](docs/screenshots/ui-motion-interruption/emulator-focus.png) 的低太阳、卡片与导航一致。当时临时系统动画倍率已恢复原未设置值，模拟器已关闭；没有新增真机截图，业务规则与素材保持。这些媒体为历史证据。

低日修订当轮已查看同版 Release 的模拟器 [设置](docs/screenshots/ui-low-sun/emulator-settings.png) 与 [专注](docs/screenshots/ui-low-sun/emulator-focus.png)，太阳中心落到山脊，下半隐藏。前后对比来自山景流转版与低日版同为 1080 × 2424 的实屏截图，仅裁切和等比缩放，保留为历史证据。

山景流转当轮已查看 [真机闹钟](docs/screenshots/ui-landscape-motion/phone-alarm.png)、[专注](docs/screenshots/ui-landscape-motion/phone-focus.png) 与 [设置](docs/screenshots/ui-landscape-motion/phone-settings.png)：当时现有 20:05 每日启用闹钟、17:30—17:34 / 工作日 / 2 个应用 / 关闭的专注计划及 4/4 权限保持，未操作改变数据。当时模拟器已检查 [320 dp / 2 倍字体](docs/screenshots/ui-landscape-motion/emulator-focus-320dp-2x.png)、浅深色和 [设置末项滚出山景](docs/screenshots/ui-landscape-motion/emulator-settings-light-bottom.png)。这些截图保留为历史证据。

专注卡片当轮已检查模拟器 [Release 深色卡片](docs/screenshots/ui-focus-card/emulator-release-dark.png)、[关闭跨午夜](docs/screenshots/ui-focus-card/emulator-disabled-overnight.png)、[浅色卡片](docs/screenshots/ui-focus-card/emulator-light.png)、[2 倍字体](docs/screenshots/ui-focus-card/emulator-large-type.png) 与 [滚动后的完整卡片](docs/screenshots/ui-focus-card/emulator-large-type-scrolled.png)。图片中的人工测试计划当时已删除，原模拟器 09:21 闹钟未改动。当时 [真机专注卡片](docs/screenshots/ui-focus-card/phone-focus-dark.png) 和 [真机主页](docs/screenshots/ui-focus-card/phone-alarm-dark.png) 也已查看，安装与检查未修改规则、权限或系统设置。此前 [真机三页](docs/screenshots/ui-useful-overview/phone-overview.png)、[倒计时示例](docs/screenshots/ui-useful-overview/overview.png) 及更早的 [楷体真机截图](docs/screenshots/ui-kai/phone-after.png)、[紧凑底栏对比](docs/screenshots/ui-compact-navigation/navigation-comparison.png)、[衬线日出截图](docs/screenshots/ui-serif-sunrise/overview.png)、[清晨薄荷截图](docs/screenshots/ui-refresh/overview.png)、[首版 S25+](docs/screenshots/s25-main-dark.png)、[首版模拟器深色](docs/screenshots/emulator-main.png)、[首版模拟器浅色](docs/screenshots/emulator-main-light.png) 均保留为历史证据。截图不能证明真机锁屏响铃或后台运行。

## 构建与安装

工具链固定在工程配置中，首次构建需要下载依赖。以下是当前开发机配置，并非最低运行系统均已验证的声明。

| 项目 | 版本或路径 |
| --- | --- |
| AGP / Gradle Wrapper | 9.3.1 / 9.5.0 |
| Kotlin / Compose 编译插件 | AGP 内置 Kotlin；Compose 插件 2.2.10 |
| Compose BOM / KSP | 2026.02.01 / 2.3.6 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29（Android 10） |
| Room / DataStore / Graphics Path | 2.8.4 / 1.2.1 / 1.1.0 |
| 开发机 JDK | `E:\AndroidStudio\jbr`，25.0.2；字节码目标为 Java 17 |
| Android SDK | `E:\AndroidSDK`，需安装 platform 37 |
| 包名 / 版本 | `dev.daybreak.clock` / 0.1.0 |

在项目根目录运行 PowerShell；其他电脑替换 JDK、SDK 路径，并配置本机 `local.properties` 的 `sdk.dir`。

```powershell
$env:JAVA_HOME = 'E:\AndroidStudio\jbr'
$env:ANDROID_HOME = 'E:\AndroidSDK'
.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

本轮 [计时器一屏布局 Release APK](artifacts/daybreak-0.1.0-timer-fit.apk) 为 15,215,981 字节，约 14.51 MiB；SHA-256：`0C312455C7CC12AFC3C33E3C132A901EA7C15AA636F4FFAFA448D88E57DF3323`。最终 [构建日志](timer-fit-delivered-build.log) 成功，已再次覆盖安装到 Galaxy S25+；首屏可见断言、横屏和实机验证见 [记录](docs/TIMER_LAYOUT_ACCEPTANCE.md)。

此前 [添加入口 Release APK](artifacts/daybreak-0.1.0-section-add.apk) 为 15,232,365 字节，约 14.53 MiB；SHA-256：`9DF3F090A18A5D9FBA902A9F9304D10A01137D320C0BB1B4857FAE07FD849514`。已覆盖安装到 Galaxy S25+，添加入口、窄屏大字体与构建验证见 [记录](docs/SECTION_ADD_ACCEPTANCE.md)。

此前 [计时器山景 Release APK](artifacts/daybreak-0.1.0-timer-landscape.apk) 为 15,232,365 字节，约 14.53 MiB；SHA-256：`112058AB657870B5CD10A8ED5CFFBEC976BBB9B52394E3CFF84E6611F9965777`。v2 [签名验证](timer-art-signature.log) 通过；[Galaxy S25+ 覆盖安装](timer-art-phone-install.log) 成功，手机 `base.apk` 哈希与交付包一致。构建、横竖屏与功能回归见 [山景记录](docs/TIMER_LANDSCAPE_ACCEPTANCE.md)。

此前 [计时器 Release APK](artifacts/daybreak-0.1.0-timer.apk) 为 14,497,312 字节，约 13.83 MiB；SHA-256：`CB442AA11E8FD57E8028E121D64CC69CB28ECDD319FF8C6E4DADF12D7A883717`。最终生产构建与 21 项单元测试见 [日志](timer-release-validation.log)，v2 签名与 `zipalign -c -P 16 4` 对齐检查通过，见 [打包验证日志](timer-apk-verification.log)。界面和平台验证见 [计时器记录](docs/TIMER_ACCEPTANCE.md)。此功能版本随后已安装到 Galaxy S25+；本轮山景修订的交付状态见最新记录。

此前 [专注时段延长修复 Release APK](artifacts/daybreak-0.1.0-focus-extension-fix.apk) 为 13,390,808 字节；v2 [签名验证](design/debug/focus-live/apk-signature.log) 和 16 KB 对齐通过。SHA-256：`E2ED81B0A98C9A69501B1B97E6A8B8A9B44AA84E8C342EF9772195DCAEF51B01`。已安装到 Galaxy S25+（SM-S9360，Android 16，R5CY22T6SDB），手机正式 `base.apk` [哈希一致](design/debug/focus-live/installed-apk-hash.log)，真实同一计划恢复 X 拦截，见 [修复记录](docs/FOCUS_EXTENSION_FIX.md) 与 [最终手机状态](design/debug/focus-live/final-phone-state.log)。

此前 [专注蒙版 Release APK](artifacts/daybreak-0.1.0-focus-overlay.apk) 为 13,390,808 字节；v2 签名与 16 KB 对齐成功，见 [APK 验证日志](design/focus-overlay-apk-validation.log)。SHA-256：`F1262AF9FD62E1A2D121E8D7B950BCBC8B5099274C1BE9E39F5F33E78B2EB039`。当时已 [覆盖安装](design/focus-overlay-phone-installation.log) 到三星 SM-S9360，手机 `base.apk` [哈希一致](design/focus-overlay-phone-installed-hash.log)；[最终运行摘要](design/focus-overlay-phone-runtime-final.log) 确认服务已绑定、未崩溃。该轮未操作锁屏手机的 X 蒙版，UI/交互验证范围见 [专注蒙版记录](docs/FOCUS_OVERLAY_DESIGN.md)。

此前 [专注恢复 Release APK](artifacts/daybreak-0.1.0-focus-recovery.apk) 为 11,807,776 字节；v2 签名与 16 KB 对齐检查通过，见 [签名日志](focus-fix-signature.log) 和 [对齐日志](focus-fix-alignment.log)。SHA-256：`0D696DCB877DE761D294ED240EC1296A92504344EC287221FC9F7E9022F6595B`。当时已 `install -r` 覆盖安装到 Galaxy S25+（SM_S9360，Android 16），手机 `base.apk` [哈希核验](focus-fix-phone-installed-hash.log) 与交付包一致；该轮真机测试见 [验证记录](docs/FOCUS_RECOVERY.md)。

上一轮 [设置抽屉 Release APK](artifacts/daybreak-0.1.0-settings-drawers.apk) 为 11,791,392 字节，约 11.25 MiB；当时 v2 签名与 `zipalign -P 16` 检查通过，见 [签名日志](design/ui-settings-drawers-signature.log) 和 [对齐日志](design/ui-settings-drawers-alignment.log)。SHA-256：`131F795AA60DF558840C6597EBC34FC48BE78A160D528AE19E0BF32390324DCF`。当时通过 `install -r` 覆盖安装到 Galaxy S25+（R5CY22T6SDB），手机 base APK 哈希与本地一致，见 [安装日志](design/ui-settings-drawers-phone-installation.log)；未新增真机界面截图。

上一轮 [平滑切页 Release APK](artifacts/daybreak-0.1.0-smooth-motion.apk) 为 11,791,392 字节，约 11.25 MiB；当时 v2 签名与 `zipalign -P 16` 检查通过，见 [签名日志](design/ui-motion-interruption-signature.log) 和 [对齐日志](design/ui-motion-interruption-alignment.log)。SHA-256：`86C41D1DA6EFD68C048B4B9E58B4818961D523DDA10E1D410D553BD2684A1E88`。当时覆盖安装到 S25+，手机 base APK 哈希与本地一致，见 [安装日志](design/ui-motion-interruption-phone-installation.log)；未新增真机界面截图。

上一轮 [低日山景 Release APK](artifacts/daybreak-0.1.0-low-sun.apk) 为 11,791,392 字节，约 11.25 MiB；当时 v2 签名与 `zipalign -P 16` 检查通过，见 [签名日志](design/ui-low-sun-signature.log) 和 [对齐日志](design/ui-low-sun-alignment.log)。SHA-256：`AFD3DCD1F33D2310202159C222643329BA32DACBA5F2DD0189AED8B484F0FD0A`。当时覆盖安装到 S25+，远端 base APK 哈希与本地一致，见 [安装日志](design/ui-low-sun-phone-installation.log)；未解锁手机或新增真机界面验收。

上一轮 [山景流转 Release APK](artifacts/daybreak-0.1.0-landscape-motion.apk) 为 11,791,392 字节，约 11.25 MiB；当时 v2 签名与 `zipalign -P 16` 检查通过，见 [对齐日志](design/ui-landscape-motion-alignment.log)。SHA-256：`BA74EBD83799E0C70A824BC386F52393313B14957A714DEE52FA83D092AE103D`。当时覆盖安装到 S25+，远端 base APK 哈希已核对一致；安装见 [日志](design/ui-landscape-motion-phone-installation.log)。

上一轮 [专注卡片 Release APK](artifacts/daybreak-0.1.0-focus-card.apk) 为 11,470,460 字节，约 10.94 MiB；当时 apksigner v2 签名验证和 `zipalign -P 16` 通过，见 [打包验证日志](design/ui-focus-card-package-validation.log)。SHA-256：`8811FA254938AC8D486F3910DA85F62B463A0AFA83D9DD4F263CFFC3D1747D3B`。当时覆盖安装到 S25+，远端 base APK 哈希与交付包一致，见 [真机安装日志](design/ui-focus-card-phone-installation.log)。

上一轮 [居中倒计时 Release APK](artifacts/daybreak-0.1.0-countdown.apk) 为 11,470,460 字节，约 10.94 MiB；当时 v2 签名、对齐和 S25+ 覆盖安装证据见 [打包日志](design/ui-countdown-package-validation.log) 与 [安装日志](design/ui-countdown-phone-installation.log)。SHA-256：`951BBB6A8F995F6C8716B73FE5BDFF5B55852801C6C1F0044BE97FB8780034E7`。

上一轮 [楷体 Release APK](artifacts/daybreak-0.1.0-kai.apk) 为 11,470,460 字节，约 10.94 MiB；当时签名、对齐及 S25+ 安装证据见 [打包日志](design/ui-kai-package-validation.log) 和 [安装日志](design/ui-kai-phone-installation.log)。SHA-256：`1CD98043FB5370CDD339BAA9D7FBE93384DF9049F2115AA1165C927937061756`。

上一轮 [紧凑底栏 Release APK](artifacts/daybreak-0.1.0-compact-navigation.apk) 为 3,136,325 字节，约 2.99 MiB；当时 v2 签名与 `zipalign -P 16` 通过，见 [历史打包日志](design/ui-nav-package-validation.log)。SHA-256：`2ED2A1677ED40636CB1084080552B67ACA60EBE03423F00136044EB3AE31146E`。

上一轮 [衬线日出 Release APK](artifacts/daybreak-0.1.0-serif-sunrise.apk) 为 3,136,325 字节，约 2.99 MiB；当时 v2 签名和 `zipalign -P 16` 通过，见 [历史打包日志](design/ui-serif-package-validation.log)。SHA-256：`F340CB83B3858332C05DDA19BBFF8CDF466FF5416A68904C3C196A60651404BB`。

历史 [清晨薄荷 Release APK](artifacts/daybreak-0.1.0-ui-refresh.apk) 为 3,042,635 字节，约 2.90 MiB；当时 apksigner 签名验证和 `zipalign -P 16` 通过，并安装到无声模拟器。SHA-256：`3A41C2D9DB5D5D5E67ED9A253483FD9F9290E1F67A71A190744A45B18EE37902`。

历史首版 [Release APK](artifacts/daybreak-0.1.0.apk) 为 2,984,978 字节，约 2.85 MiB；当时已通过 apksigner v2 签名验证和 `zipalign -c -P 16 4`，并在 S25+ 成功覆盖安装。SHA-256：`FFCAA04E574782F3569195EA9BF8B23B72B49F2A242B4BB08D35B7C35B4A305B`。

Release 启用 R8 和资源收缩，使用开发机 debug 密钥签名，便于覆盖同签名的已安装 APK；这是自用签名配置，不是商店发布签名。开发用 Debug APK 位于 `app\build\outputs\apk\debug\app-debug.apk`；重建自用版本可运行 `.\gradlew.bat --no-daemon :app:assembleRelease`，输出位于 `app\build\outputs\apk\release\app-release.apk`。

手机打开 USB 调试，在手机确认电脑的调试授权。下列安装与设备测试命令仅在构建成功后运行：

```powershell
$clockAdb = 'E:\AndroidSDK\platform-tools\adb.exe'
& $clockAdb devices -l
$clockDevice = '填写上一步显示的设备序列号'
& $clockAdb -s $clockDevice install -r '.\artifacts\daybreak-0.1.0-timer-fit.apk'
& $clockAdb -s $clockDevice shell am start -n dev.daybreak.clock/.MainActivity
```

集成与平台测试使用另一个终端中的无声模拟器，启动参数包含 `-no-window -no-audio -no-snapshot`；不要将图书馆中的手机作为测试目标。平台测试的逻辑和回退状态通过不代表扬声器发声、音量或振动已经验收。

```powershell
$clockEmulator = '填写无声模拟器的设备序列号'
$env:ANDROID_SERIAL = $clockEmulator
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

清晨薄荷首轮该 Gradle 任务在下载 UTP 组件时被证书信任错误阻断，改由 `adb shell am instrument` 完成 4 项平台回归和 1 项编辑流程测试；衬线日出修订也经 adb instrumentation 执行了编辑流程测试。紧凑底栏与楷体修订未重新执行这些测试。居中倒计时修订新增 `CountdownOverviewTest`，专注卡片与山景流转当轮经 adb instrumentation 重跑该测试，不将 `connectedDebugAndroidTest` 记为通过。低日与平滑切页修订未重跑这些业务测试；平滑切页当轮完成 3 项生产动画 helper 的 Compose 可控时钟回归，设置抽屉当轮未重跑业务或动画测试。此前专注恢复轮次通过的 6 项模拟器回归及 18 项单元测试见 [专注恢复记录](docs/FOCUS_RECOVERY.md)，蒙版轮次的 5 项不同仪器用例及交互重跑见 [专注蒙版记录](docs/FOCUS_OVERLAY_DESIGN.md)；专注延长修复轮次 10 项专注仪器测试见 [延长时段修复记录](docs/FOCUS_EXTENSION_FIX.md)。

## 首次使用

1. 打开“设置”，允许精确闹钟和通知；检查锁屏全屏提醒状态。全屏授权允许请求全屏入口，系统仍可能展示横幅；可从闹钟通知进入响铃页。
2. 使用应用锁时，手动开启“朝醒 · 定时应用锁”无障碍服务。侧载 APK 若提示“受限设置”，可在系统“设置 → 应用 → 朝醒 → 更多”寻找“允许受限设置”，按本机提示操作后再开启服务。入口名称可能随系统变化，参见 [Android 受限设置说明](https://support.google.com/android/answer/12623953)。
3. 检查三星“电池 → 后台使用限制”中的休眠、深度休眠及从不休眠列表，并检查朝醒的应用电池设置。将应用移出休眠/深度休眠并在提供的选项中允许后台运行，然后实测熄屏和长待机。三星的休眠管理会限制后台活动，设置本身不代表响铃已验收，参见 [三星应用管理说明](https://developer.samsung.com/mobile/app-management.html)。
4. 在允许声音的环境创建近期闹钟验证声音和关闭流程，再保存正式计划。自选铃声建议先选择本机可读取文件并试听；文件被删除、授权撤销或云提供器无法及时读取会触发备用路径。

未授权精确闹钟时会显示未排程；授权恢复后可使用“重建待执行计划”。勿扰、闹钟音量、通知渠道和后台策略需要在实际手机上检查。

## 恢复与边界

闹钟规则和会话使用 Room 保存；最小执行快照另写入设备保护存储中的 `alarm-journal.json`。启动恢复时先合并日志中的会话结果，再重建快照和待执行调度。首次解锁前的 receiver、响铃 service 和 activity 使用设备保护快照，跳过自选文件，尝试系统或内置备用铃声。16 KB 无声模拟器实际重启后，在 `RUNNING_LOCKED` 状态经 `setAlarmClock` 触发前台服务与全屏固定题目；错误答案拒绝，正确答案在首次解锁前结束服务并返回锁屏。解锁后验证同一道题的答案、ENDED 日志合并和一次性闹钟禁用；测试规则与模拟器 PIN 已清理。S25+ 尚未测试该链路，声音和振动效果仍待真机验收。Android 的两种存储可用时机见 [Direct Boot 文档](https://developer.android.com/privacy-and-security/direct-boot)。

重启广播只恢复系统闹钟调度，不直接开始播放。恢复时发现错过不超过 10 分钟的待执行闹钟，会安排近期补响；更早的记为错过。时间/时区改变、升级和授权恢复也有恢复入口，去重与数据一致性需按验收清单验证。应用锁规则使用解锁后的 Room 数据，首次解锁前的应用锁不属于已支持范围。

应用锁通过无障碍窗口事件与当前窗口根节点包名识别目标，以无障碍覆盖层拦截；不遍历节点或读取文字。服务连接及最近检查共同决定页面上的运行状态，重连与检查异常恢复见 [专注恢复记录](docs/FOCUS_RECOVERY.md)。它不会提供系统级禁开；强停、卸载、撤销权限、修改系统时间和系统终止均可影响运行。电话、紧急呼叫、桌面、设置及本应用被排除于可选目标。分屏、弹窗和画中画尚未验证，不能承诺覆盖所有入口。算术关闭约束本应用正常关闭流程，不能阻止关机、系统终止或调低音量。

16 KB 检查中 APK 的 `zipalign -P 16` 通过，DataStore 的 64 位库 LOAD/RELRO 对齐通过；Graphics Path 1.1.0 的 RELRO 仍不符合 16 KB 对齐。Manifest 使用 `android:pageSizeCompat="enabled"` 开启 Android 官方兼容模式，16 KB 模拟器的 4 项回归与 Direct Boot 验证通过，不能将结果称为全部原生 16 KB 通过。当前 S25+ 页大小为 4096。参见 [Android 页大小与兼容模式](https://developer.android.com/guide/practices/page-sizes) 和 [Graphics Path 版本记录](https://developer.android.com/jetpack/androidx/releases/graphics)。

## 代码与验证

| 目录 | 职责 |
| --- | --- |
| `ui` / `feature` | 闹钟、专注、计时器、设置与编辑界面；ViewModel 提交操作 |
| `domain` | 本地时间计算、跨午夜窗口、算术题与应急等待 |
| `data` | Room、设备保护执行日志、DataStore 偏好 |
| `platform/alarm` | 精确调度、响铃服务、通知入口、铃声与恢复 |
| `platform/focus` | 有效会话、应用筛选、无障碍覆盖与边界刷新 |

此前山景修订最终 Debug / Release / AndroidTest、23 项单元测试与 Lint 成功，见 [构建日志](timer-art-final-build.log)；Lint 为 0 错误、23 项既有警告。[8 项不同仪器测试](timer-art-instrumentation.log) 通过，另完成真实横屏 2424 × 1080 和浅色竖屏 1080 × 2424 的四页布局检查；图像、太阳顺序、测试范围与安装证据见 [山景记录](docs/TIMER_LANDSCAPE_ACCEPTANCE.md)。

此前计时器功能最终 Debug / Release、21 项单元测试与 Lint 成功，见 [生产构建日志](timer-release-validation.log)；Lint 为 0 错误、23 警告，保留既有依赖版本、配置与 API 建议，未添加依赖。[10 项不同仪器测试](timer-final-tests.log) 通过；另以同一布局捕捉用例分别检查深色、浅色及 320 dp / 2 倍字体，不计为新增不同测试。截图、恢复机制与真机待测范围见 [计时器记录](docs/TIMER_ACCEPTANCE.md)。

此前专注延长修复轮次 Debug / Release / AndroidTest、18 项单元测试与 Lint 成功，见 [构建日志](design/debug/focus-live/extension-fix-build.log)；Lint 为 0 错误、21 警告。新增延长窗口用例 [旧代码 4 项失败](design/debug/focus-live/extension-red.log)、[修复后 4 项通过](design/debug/focus-live/extension-green.log)，另有 [6 项专注回归](design/debug/focus-live/focus-regression.log) 通过，合计 10 项不同仪器测试。真机同一计划、移除最近任务后的 X 拦截和测试清理见 [延长时段修复记录](docs/FOCUS_EXTENSION_FIX.md)；该测试不代表系统强停或进程被杀后的恢复。

此前蒙版轮次 Debug / Release / 测试 APK、18 项单元测试与 Lint 成功，见 [交付构建日志](design/focus-overlay-delivery-build.log)；Lint 为 0 错误、21 警告。[5 项仪器用例](design/focus-overlay-instrumentation-awake.log) 通过，随后在最终 Debug 构建上扩展并 [重跑交互用例](design/focus-overlay-live-config.log)，运行中切换 1.6 倍字体与短宽屏仍可滚动操作。手机安装、哈希及服务运行状态当时已核验，该轮 UI/交互来自模拟器，见 [专注蒙版记录](docs/FOCUS_OVERLAY_DESIGN.md)。

此前专注恢复轮次 Debug / Release、18 项单元测试及 Lint 成功，见 [构建日志](focus-fix-build-validation.log)；Lint 为 0 错误、20 警告。[模拟器回归](focus-fix-platform-validation.log) 通过 6 项。Galaxy S25+ 当时确认三星 MARs 强停记录，改为“不受限制”后 X 拦截、划掉任务后再打开及 60 秒应急解除通过；修复前失败、真机证据与未测范围见 [专注恢复记录](docs/FOCUS_RECOVERY.md)。

设置抽屉当轮 Debug / Release 构建及 Lint 成功（1 分 7 秒），见 [验证日志](design/ui-settings-drawers-validation.log)，Lint 为 0 errors / 20 warnings。该版原生抽屉的权限入口/返回、关闭方式、深浅色与 320 dp / 2 倍字体滚动检查见 [交互记录](design/ui-settings-drawers-ui-check.log)。HTML 当时已检查抽屉开关、快速关开、跨抽屉焦点、主题与资产加载，无控制台错误或警告；权限项为设计示例。设置抽屉当轮未重跑业务、动画或 Direct Boot 测试。详情见 [界面更新记录](docs/UI_REFRESH.md)，保持历史范围。

平滑切页当轮的 [构建与 Lint 日志](design/ui-motion-interruption-validation.log)、[3 项动画回归通过记录](design/ui-motion-interruption-green.log)（2.313 秒）及原实现 [失败记录](design/ui-motion-interruption-red.log) 保留；正常及 5 倍慢动画检查与关闭动画回归均属于该轮历史证据。

低日当轮的 [构建与 Lint 日志](design/ui-low-sun-validation.log)、模拟器太阳位置及浏览器检查保持历史范围；当时未重跑逻辑测试或新增录屏。

山景流转当轮的 [构建与 Lint 日志](design/ui-landscape-motion-validation.log) 保留，`CountdownOverviewTest` 当时经 adb instrumentation 通过 1 项（8.956 秒），执行于该版最终小幅太阳位置调整之前。真机三页、模拟器浅深色、320 dp / 2 倍字体、系统动画倍率为 0 时切换与设置末项可达，以及 HTML 三页/主题/资源检查均保持历史范围；当时视口覆盖未生效，未计为 HTML 360 px 验证。

专注卡片当轮的 [构建与 Lint 日志](design/ui-focus-card-validation.log)、[1 项概览回归日志](design/ui-focus-card-instrumentation.log)（6.398 秒）及专注卡片深浅色、跨午夜、2 倍字体与真机检查均保持历史范围。

居中倒计时当轮的 [构建与 15 项单元测试日志](design/ui-countdown-validation.log)、[1 项概览测试日志](design/ui-countdown-instrumentation.log) 与 [真机三页](docs/screenshots/ui-useful-overview/phone-overview.png) 保持历史范围。

楷体当轮的 [构建与 Lint 日志](design/ui-kai-validation.log) 及 [真机截图](docs/screenshots/ui-kai/phone-after.png) 保留为历史证据；当时检查了中文字体、基线、模拟器浅色与 2 倍字体布局。

紧凑底栏当轮的 [构建与 Lint 日志](design/ui-nav-validation.log) 和 [导航对比](docs/screenshots/ui-compact-navigation/navigation-comparison.png) 保留为历史证据；当时检查了三页切换、2 倍字体纵排与系统三键导航。

上一轮衬线日出的 [构建与 Lint 日志](design/ui-serif-final-validation.log)、9 项单元测试结果（`app/build/test-results/testDebugUnitTest`）及 [1 项编辑流程日志](design/ui-serif-editor-test.log)（5.395 秒）保持历史范围；当时检查了日出遮挡滚动、末项可达与 360 dp / 1.5 倍字体布局。

清晨薄荷首轮的 [构建与 Lint 日志](design/ui-final-validation.log)、[4 项平台回归日志](design/ui-platform-regression.log)（26.897 秒）和 [1 项编辑流程日志](design/ui-workflow-test-final.log)（6.146 秒）保留为历史证据；原 4 项平台套件本轮未整套重跑，当前范围以计时器记录为准。

以下为首版历史验收：时间/DST、算术和应急计时的 9 个单元测试通过；16 KB 无声模拟器完整 4 项回归通过（15.419 秒，0 跳过），覆盖旧回调失效、答案校验、重叠窗口及应急范围、前台服务与失效 URI 回退，以及已在前台的 Chrome 被覆盖、删规则仍保护和自然到期移除。解锁后 Direct Boot 合并验证另有 1 项通过。此前测试观察器失败轮次保留在验收记录中。模拟器结果不能替代 S25+ 的声音、锁屏、Doze 和三星多窗口验收。

| 验证项 | 首版历史状态 |
| --- | --- |
| Debug / Release APK、测试 APK | 最终构建成功；9 项单元测试通过，无失败、错误或跳过 |
| Android Lint | 0 errors、20 warnings；版本、UseKtx 及其他提示保留 |
| S25+ 安装与深色主界面 | Debug 启动、Release 覆盖安装及静默界面验证通过；未测试声音/振动或新增授权 |
| 16 KB 无声模拟器平台测试 | Debug 完整 4 项回归通过，0 跳过 |
| Direct Boot | Debug 模拟器首次解锁前触发/答题关闭、解锁后合并验证通过；S25+ 未测 |
| 最终自用 APK | Release 已交付；2.85 MiB，v2 签名验证、ZIP 对齐、S25+ 更新安装及 16 KB 模拟器主界面启动通过 |
| P1 风险原型退出标准 | 未通过，缺少真机行为结论 |
| P5 恢复与回归退出标准 | 未通过，缺少完整验收 |
