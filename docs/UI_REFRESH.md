# 朝醒 · 界面更新

2026-10-08，按用户要求调用 `claude-design` 优化原生 Android 界面。当前 [设置抽屉修订](../design/朝醒%20·%20设置抽屉.html) 将系统权限与使用说明收进两个底部抽屉，保留平滑切页、低日山脊位置、居中倒计时、清晨薄荷、文楷、数字字体、专注卡片与紧凑底栏。原 [平滑切页](../design/朝醒%20·%20平滑切页.html)、[低日山景](../design/朝醒%20·%20低日山景.html)、[山景流转](../design/朝醒%20·%20山景流转.html)、[三种界面方向](../design/朝醒%20·%20界面方向.html)、[衬线日出](../design/朝醒%20·%20衬线日出版.html)、[紧凑底栏](../design/朝醒%20·%20紧凑底栏.html)、[楷体](../design/朝醒%20·%20楷体版.html)、[居中倒计时](../design/朝醒%20·%20居中倒计时.html) 与 [专注卡片预览](../design/朝醒%20·%20专注卡片.html) 均保留；预览时间与规则为示例，实际应用使用保存的数据。

## 本轮设置抽屉

当前入口：[最终版 APK](../artifacts/daybreak-0.1.0-settings-drawers.apk)、[设置抽屉交互稿](../design/朝醒%20·%20设置抽屉.html)、[模拟器实屏总览](screenshots/ui-settings-drawers/settings-drawers-preview.png)。权限抽屉中间版不作为本轮交付入口。

设置主页面仅保留真实运行权限摘要（已允许数 / 4 与待开启数）、外观与计划（深色开关、重建）及使用说明入口。权限抽屉保留精确闹钟、通知、锁屏全屏提醒和定时应用锁四个系统入口；说明抽屉保留三星后台提示、“本地与可控”及有记录时显示的最近 5 条应急解除。共用 `SettingsEntry` / `SettingsDrawer` / `UsageParagraph`，抽屉跳过半展开，高度最多为当前窗口的 85%，标题和关闭按钮固定，正文以 LazyColumn 滚动。业务、授权、字体、资产与平滑切页实现未修改。

最终同版 Release 的模拟器 [主页](screenshots/ui-settings-drawers/emulator-settings.png)、[权限](screenshots/ui-settings-drawers/emulator-permissions.png)、[说明](screenshots/ui-settings-drawers/emulator-guide.png) 和 [浅色说明](screenshots/ui-settings-drawers/emulator-guide-light.png) 已查看：主页没有原长段落或记录，权限摘要真实显示 3/4 及待开启数，原说明与最近 5 条记录仍在抽屉。四个系统入口逐一打开后，Back 返回同一权限抽屉；关闭按钮、说明 Back 关闭与权限标题区下滑关闭通过，未改变授权，见 [交互记录](../design/ui-settings-drawers-ui-check.log)。320 dp / 2 倍字体下，[主页](screenshots/ui-settings-drawers/emulator-main-320dp-2x.png) 可滚到说明入口，[权限四项](screenshots/ui-settings-drawers/emulator-permissions-320dp-2x.png) 完整可见，[说明正文](screenshots/ui-settings-drawers/emulator-guide-320dp-2x.png) 可滚到 [第 5 条记录](screenshots/ui-settings-drawers/emulator-guide-320dp-2x-bottom.png)。

Debug / Release 构建与 Lint 成功（1 分 7 秒），见 [验证日志](../design/ui-settings-drawers-validation.log)，Lint 为 0 errors / 20 warnings。v2 签名与 `zipalign -P 16` 通过，见 [签名日志](../design/ui-settings-drawers-signature.log) 和 [对齐日志](../design/ui-settings-drawers-alignment.log)。APK 为 11,791,392 字节，约 11.25 MiB；SHA-256：`131F795AA60DF558840C6597EBC34FC48BE78A160D528AE19E0BF32390324DCF`。已 `install -r` 覆盖安装到 Galaxy S25+（R5CY22T6SDB），手机 base APK 哈希一致，见 [安装日志](../design/ui-settings-drawers-phone-installation.log)。本轮没有新的真机截图，未重跑业务、太阳动画或 Direct Boot 测试。

HTML 已真实浏览器验证权限/说明抽屉开关、快速重复关开、跨抽屉焦点、浅深色及图资产加载，控制台无错误或警告；段落只在说明 overlay，权限项是设计示例。总览由 [build-settings-drawers-preview.py](../design/build-settings-drawers-preview.py) 在现有 `datasci` / Pillow 中仅缩放拼接三张实屏，无新依赖或新 AI 素材。模拟器已恢复 1080 × 2424 / density 420、字体倍率 1.0、animator 原未设置值；重启后读取实际可选中开关确认原深色偏好，随后已关闭模拟器。

## 平滑切页历史记录

历史入口：[平滑切页 APK](../artifacts/daybreak-0.1.0-smooth-motion.apk)、[平滑切页交互稿](../design/朝醒%20·%20平滑切页.html)、[正常速度预览 GIF](screenshots/ui-motion-interruption/emulator-normal-rapid.gif) / [视频](screenshots/ui-motion-interruption/emulator-normal-rapid.mp4)、[正常速度过渡帧](screenshots/ui-motion-interruption/normal-motion-frames.png)。辅助证据：[5 倍慢动画视频](screenshots/ui-motion-interruption/emulator-slow-rapid.mp4) / [GIF](screenshots/ui-motion-interruption/emulator-slow-rapid.gif)、[慢动画过渡帧](screenshots/ui-motion-interruption/motion-frames.png) 与 [最终专注页](screenshots/ui-motion-interruption/emulator-focus.png)。

诊断核对本工程 Compose animation-core 1.10.4 的 [AAR 字节码](../design/baseline-motion-interruption/transition-bytecode.txt) 与 [AndroidX 官方源码](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/Transition.kt)：原 `Transition` 中断 tween 时使用默认 spring。原实现 0 → 2 动画运行至 448 ms 时反向切到 0，峰值从完整行程的 1.759 升至 5.906 屏宽/秒，约 3.36 倍，见 [失败记录](../design/ui-motion-interruption-red.log)。修复将景高、太阳横坐标、尺寸及山脊锚点组成 `LandscapeGeometry` 四维向量，以 `animateValueAsState` 显式 760 ms tween 同步重定向；山景透明度组成三维向量使用 520 ms tween，视差使用 `animateFloatAsState` 的 760 ms tween。每次切页从当前值转向最新目标，导航持续可用，低日位置、资产和业务规则保持。

平滑切页当轮最终 [动画回归](../design/ui-motion-interruption-green.log) 通过 3 项（2.313 秒），覆盖正常、5 倍系统慢动画、关闭动画，以及多次 0 → 2 → 1 → 0 → 2 → 1 切换的最新目标与速度断言。正常完整/中断峰值为 1.7592832 / 1.5261675 屏宽/秒，5 倍为 0.35259125 / 0.3058668；关闭动画直接到达最新目标。测试直接运行生产动画 helper，使用 Composition / Recomposer / BroadcastFrameClock 可控时钟。初次 Espresso 测试因隐藏 `InputManager` API 不兼容失败，属于框架限制，最终测试已绕开；没有新增依赖，也不将该初次失败记为产品 UI 故障。当时未重跑业务或系统功能用例。

平滑切页当轮 Debug / Release / 测试 APK 构建与 Lint 成功（1 分 13 秒），见 [验证日志](../design/ui-motion-interruption-validation.log)，Lint 为 0 errors / 20 warnings。当时 v2 签名与 `zipalign -P 16` 通过，见 [签名日志](../design/ui-motion-interruption-signature.log) 和 [对齐日志](../design/ui-motion-interruption-alignment.log)。APK 为 11,791,392 字节，约 11.25 MiB；SHA-256：`86C41D1DA6EFD68C048B4B9E58B4818961D523DDA10E1D410D553BD2684A1E88`。当时 `install -r` 覆盖安装到 Galaxy S25+（R5CY22T6SDB），手机 base APK 哈希与本地一致，见 [安装日志](../design/ui-motion-interruption-phone-installation.log)；没有新的真机界面截图。

该轮正常速度模拟器视频实际时长约 9.61 秒，预览 GIF 取原录屏 3.8 秒起的 5.5 秒整机画面，以 15 fps 导出，未加速；5 倍慢动画视频约 13.45 秒。两次实际视觉检查均包含动画尚未结束时多次反向/跨页，最后专注页的低太阳、卡片与导航一致。媒体均来自原生模拟器，临时 animator 倍率当时已恢复原未设置值，模拟器已关闭。该版 HTML 使用 rAF 从最后渲染状态按所选时长重定向，当时浏览器已验证连续四次切页最终为专注、全部图加载及关闭动效后设置立即落点；全页截图中专注布局、三种山景与低日锚点正常。随后恢复 760 ms，控制台无错误或警告；该轮未生成新素材。

## 低日山景历史记录

历史入口：[低日山景 APK](../artifacts/daybreak-0.1.0-low-sun.apk)、[低日交互稿](../design/朝醒%20·%20低日山景.html)、[设置前后对比](screenshots/ui-low-sun/settings-sun-comparison.png)、[模拟器设置](screenshots/ui-low-sun/emulator-settings.png) 与 [专注](screenshots/ui-low-sun/emulator-focus.png)。

`LandscapeScene.kt` 将太阳中心按山脊定位，闹钟 / 专注 / 设置的 `sunHorizon` 分别为 0.42 / 0.74 / 0.59，顶部位置为“景高 × 山脊比例 + 下移量 − 太阳直径 / 2”。在现有 `datasci` / Pillow 环境中读取透明山景对应太阳横坐标处的脊线，测得比例 0.4184 / 0.7428 / 0.5859，并四舍五入用于布局。闹钟保持原有半轮露出，常规手机尺寸下专注约降低 30 dp、设置约降低 24 dp。资产没有重新生成或修改，三页景高、760 ms 太阳动画、山景交叠与列表遮挡保持；既有规则和存储未修改。

低日当轮 Release 构建与 Lint 通过（1 分 17 秒），见 [验证日志](../design/ui-low-sun-validation.log)，Lint 为 0 errors / 20 warnings。当时 v2 签名和 `zipalign -P 16` 检查通过，见 [签名日志](../design/ui-low-sun-signature.log) 与 [对齐日志](../design/ui-low-sun-alignment.log)。APK 为 11,791,392 字节，约 11.25 MiB；SHA-256：`AFD3DCD1F33D2310202159C222643329BA32DACBA5F2DD0189AED8B484F0FD0A`。当时通过 `install -r` 覆盖安装到 Galaxy S25+（R5CY22T6SDB），远端 base APK 哈希与本地一致，见 [安装日志](../design/ui-low-sun-phone-installation.log)。手机该轮锁屏，没有请求解锁或新增真机界面截图。

低日版 Release 的模拟器设置/专注截图当时已实际查看，太阳中心落到山脊，下半隐藏。设置对比图来自山景流转版 `emulator-settings-dark.png` 与低日版 `emulator-settings.png`，均为 1080 × 2424 实屏截图，由 [build-low-sun-preview.py](../design/build-low-sun-preview.py) 在 `datasci` / Pillow 中仅裁切、等比缩放生成。该版 HTML 当时已浏览器实载并点通专注 → 闹钟 → 设置，半轮太阳正确，无控制台错误或警告。模拟器当时已关闭，未改系统显示或字体偏好；未重跑逻辑单元或仪器测试，也未新增录屏。这些记录继续保留历史范围。

## 山景与动效历史记录

历史入口：[山景流转 APK](../artifacts/daybreak-0.1.0-landscape-motion.apk)、[交互设计稿](../design/朝醒%20·%20山景流转.html)、[真机三页](screenshots/ui-landscape-motion/phone-three-scenes.png)、[模拟器动效视频](screenshots/ui-landscape-motion/emulator-motion.mp4) / [GIF](screenshots/ui-landscape-motion/emulator-motion.gif)、[过渡帧](screenshots/ui-landscape-motion/motion-frames.png)、[资产生成记录](../design/landscape-motion-prompts.md)。

[山景流转交互稿](../design/朝醒%20·%20山景流转.html) 展示三页构图、浅深色及 760 / 480 / 0 ms 切换选项，并支持 `prefers-reduced-motion`；这些选项属于 HTML 预览。原生应用的 `LandscapeFrame` 持续跨越三个 tab：闹钟为清晨起伏山坡，专注为低谷与深绿山脊，设置为简化远山。同一黄色纸感太阳以 760 ms `FastOutSlowIn` 连续移动、缩放；山景以 520 ms 交叠并伴随 12 dp 轻微视差，正文先 120 ms 淡出，再 200 ms 淡入。切换停止后没有循环动画。

三页山景高度比例为 1 / 0.88 / 0.72，常规窗口保持降低 40 dp，较短窗口按景高的 20% 降低。该版专注页太阳顶部位于景高的 0.20 处再加下移量。内容继续滚入自然山景轮廓下方，三页列表统一按最高山景预留底部空间，确保设置末项可以滚出。居中倒计时、文楷与 Gelasio 时间数字、专注卡片、紧凑导航、既有规则与存储均保留。

四个透明资产由内置 GPT Image 生成，参考原日出插画；PNG / WebP 保存在 `design/assets/landscape-motion`，分别打包为 `scenic_hills_alarm`、`scenic_hills_focus`、`scenic_hills_settings` 与 `scenic_sun`。实际 prompt、保存路径及离线编码处理见 [生成记录](../design/landscape-motion-prompts.md)。

山景流转当轮 Debug / Release / 测试 APK 构建及 Lint 成功（1 分 18 秒），见 [验证日志](../design/ui-landscape-motion-validation.log)；Lint 为 0 errors / 20 warnings，保留既有提示。`CountdownOverviewTest` 当时经 adb instrumentation 通过 1 项（8.956 秒），实际工具输出为 `Time: 8.956` / `OK (1 test)`；该次输出未另存日志，执行于该版最终小幅太阳位置调整之前。当时未重跑 15 项单元测试、Direct Boot、原编辑流程或原 4 项平台回归，不将 `connectedDebugAndroidTest` 记为通过。

山景流转 APK 为 11,791,392 字节，约 11.25 MiB；当时 v2 签名和 `zipalign -P 16` 检查通过，见 [对齐日志](../design/ui-landscape-motion-alignment.log)。SHA-256：`BA74EBD83799E0C70A824BC386F52393313B14957A714DEE52FA83D092AE103D`。当时通过 `install -r` 覆盖安装到 Galaxy S25+（R5CY22T6SDB），远端 base APK 哈希已核对一致；安装见 [日志](../design/ui-landscape-motion-phone-installation.log)。用户解锁后当时已实际查看 [闹钟页](screenshots/ui-landscape-motion/phone-alarm.png)、[专注页](screenshots/ui-landscape-motion/phone-focus.png) 与 [设置页](screenshots/ui-landscape-motion/phone-settings.png)：20:05 / 每天 /“元神”/ 启用的闹钟、17:30—17:34 / 工作日 / 2 个应用 / 关闭的专注计划及 4/4 权限保持，未操作改变数据。

山景流转当轮模拟器手动检查浅深色、[320 dp / 2 倍字体](screenshots/ui-landscape-motion/emulator-focus-320dp-2x.png)、系统 animator 倍率为 0 时切换，以及 [深色设置末项](screenshots/ui-landscape-motion/emulator-settings-scrolled.png) 和 [浅色设置末项](screenshots/ui-landscape-motion/emulator-settings-light-bottom.png) 完整滚到山景上方。随后恢复 1080 × 2424 / density 420、字体倍率 1.0 与 animator 原未设置值。浏览器当时已检查 HTML 三页主流程、主题、关闭动效（0 秒）及字体/资产加载，无控制台错误或警告；视口覆盖未实际生效，不计为 HTML 360 px 验证。

该轮真机录屏接口返回 `UNASSIGNED_LAYER_STACK`，产生 0 字节文件，无法作为动效证据。上方视频、GIF 和过渡帧均来自该版 Release 的模拟器，实际过渡帧已查看；MP4 有效时长约 9.95 秒、1,756,004 字节，GIF 为 1,489,938 字节。录制见 [日志](../design/ui-landscape-motion-recording.log)（193 帧，错误日志为空），在 `datasci` 环境中通过 ffmpeg 导出。这些动效媒体与真机静态截图分别标注来源，均保留为上一版证据。

## 专注计划卡片历史记录

历史入口：[专注卡片 APK](../artifacts/daybreak-0.1.0-focus-card.apk)、[三方案 HTML（采用 A）](../design/朝醒%20·%20专注卡片.html)、[真机卡片预览](screenshots/ui-focus-card/phone-focus-card-preview.png)、[完整真机截图](screenshots/ui-focus-card/phone-focus-dark.png)、[模拟器 Release 预览](screenshots/ui-focus-card/focus-card-preview.png)。`FocusPlanCard` 将 18 sp 文楷名称与开关放在同一行，开始/结束使用 32 sp Gelasio 两列时间；细分隔线下以 FlowRow 集中显示重复日期、应用数量及关闭状态。跨午夜在结束标题标“次日”；字体倍率大于 1.4 或卡内可用宽度小于 260 dp 时，时间自动上下排列。整张卡仍可点击编辑，开关保持既有规则保存与会话语义。

三方案 HTML 已浏览器实载检查，浅深色、360 px 大字号、开关和字体正常，无溢出或控制台错误。模拟器 [未来专注](screenshots/ui-focus-card/emulator-focus-upcoming.png)、[活动专注](screenshots/ui-focus-card/emulator-focus-active.png) 为 instrumentation 测试样例；[Release 深色卡片](screenshots/ui-focus-card/emulator-release-dark.png) 为人工创建的 09:00—11:00 / 工作日 / 1 个应用样例。[关闭跨午夜](screenshots/ui-focus-card/emulator-disabled-overnight.png) 显示 09:00—次日 07:00；[浅色卡片](screenshots/ui-focus-card/emulator-light.png) 已目视检查。720 × 1600 / density 320 / 字体倍率 2.0 的 [纵排卡片](screenshots/ui-focus-card/emulator-large-type.png) 与 [滚动后的卡片](screenshots/ui-focus-card/emulator-large-type-scrolled.png) 已查看，信息自然换行，最后卡片能完整滚到添加按钮上方。

专注卡片当轮 Debug / Release / 测试 APK 构建与 Lint 成功（71 秒），见 [验证日志](../design/ui-focus-card-validation.log)；Lint 为 0 errors / 20 warnings。既有 `CountdownOverviewTest` 经 adb instrumentation 通过 1 项（6.398 秒），见 [回归日志](../design/ui-focus-card-instrumentation.log)，专注倒计时、应急等待与取消仍有效。当时未重跑前一轮 15 项单元测试、原 `EditorWorkflowTest` 或原 4 项平台回归，不将 `connectedDebugAndroidTest` 记为通过。

专注卡片 APK 为 11,470,460 字节，约 10.94 MiB；当时 v2 签名和 `zipalign -P 16` 通过，见 [打包日志](../design/ui-focus-card-package-validation.log)。SHA-256：`8811FA254938AC8D486F3910DA85F62B463A0AFA83D9DD4F263CFFC3D1747D3B`。当时通过 `install -r` 覆盖安装到 Galaxy S25+（R5CY22T6SDB），远端 base APK 哈希与交付包一致，见 [真机安装日志](../design/ui-focus-card-phone-installation.log)。

专注卡片当轮 [真机专注截图](screenshots/ui-focus-card/phone-focus-dark.png) 已实际查看：名称与开关、开始/结束两列及底部同行信息正常，原 17:30—17:34 / 工作日 / 2 个应用 / 关闭的计划保持。[真机主页](screenshots/ui-focus-card/phone-alarm-dark.png) 显示用户自行更新的 20:05 每日启用闹钟；当时安装与检查未修改任何规则、权限或系统设置，手机留在专注页。该轮 [真机预览](screenshots/ui-focus-card/phone-focus-card-preview.png) 由 [build-focus-card-preview.py](../design/build-focus-card-preview.py) 根据实屏卡片及标题位置，在现有 `datasci` / Pillow 环境中裁切并等比缩至 500 px，未修改界面内容。这些真机截图继续保持历史范围。

人工样例已通过编辑删除，[恢复后截图](screenshots/ui-focus-card/emulator-restored.png) 显示 0 个专注计划，原模拟器 09:21 闹钟未改动。字体倍率 1.0、1080 × 2424 / density 420 与深色偏好已恢复，模拟器已成功关闭。[模拟器预览](screenshots/ui-focus-card/focus-card-preview.png) 来自人工样例的 Release 截图，包含“定时计划”标题和卡片；由 [build-focus-card-preview.py](../design/build-focus-card-preview.py) 在现有 `datasci` / Pillow 环境中仅裁切、等比缩放生成，未修改界面内容。

## 居中倒计时历史记录

历史入口：[居中倒计时 APK](../artifacts/daybreak-0.1.0-countdown.apk)、[HTML 预览](../design/朝醒%20·%20居中倒计时.html)、[倒计时示例总览](screenshots/ui-useful-overview/overview.png)、[真机三页](screenshots/ui-useful-overview/phone-overview.png)。主页在背景上居中显示“距离响铃还有”及小时/分钟，下方显示真实下一次日期 HH:mm；没有额外实时时钟、右上重复设置图标或“安心休息”宣传卡。没有可显示的下一次会话时，按实际状态显示全部关闭、尚未排程或未创建；响铃中的状态优先显示。原闹钟卡、文楷与 Gelasio 数字、薄荷配色、日出遮挡和紧凑底栏保留。

专注空闲时不显示宣传大卡，存在启用计划时显示下一次开始倒计时；窗口运行时显示距全部结束的倒计时，并保留应急解除、等待确认与取消流程。设置标题后直接显示运行权限小计和权限入口。新增 `TimeRules.nextFocusStart` 仅计算未来的开始时间，不把当前窗口结束误当成下一次开始，并沿用 `focusWindow` 的单次 `atZone` 夏令时语义。`CountdownText` 对剩余分钟向上取整，未来不足一分钟仍显示 1 分钟，已经到达或过去显示 0 分钟。

居中倒计时当轮 Debug / Release 构建、15 项单元测试（12 项 TimeRules、3 项 CountdownText）及 Lint 通过，见 [验证日志](../design/ui-countdown-validation.log)；Lint 为 0 errors / 20 warnings。当时新增 [CountdownOverviewTest](../design/ui-countdown-instrumentation.log) 经 adb instrumentation 通过 1 项（4.069 秒），验证闹钟标题与设置图标移除、未来/活动专注标题、应急等待确认禁用及取消恢复。未重跑原 `EditorWorkflowTest` 或原 4 项平台回归，也不将 `connectedDebugAndroidTest` 记为通过。测试仅在无声模拟器运行，测试专注规则已删除，测试窗口置为 EXPIRED 并刷新。

居中倒计时当轮目视检查模拟器 [闹钟页](screenshots/ui-useful-overview/emulator-alarm-dark.png)、[下一次专注](screenshots/ui-useful-overview/emulator-focus-upcoming.png)、[活动专注](screenshots/ui-useful-overview/emulator-focus-active.png) 及 [设置页](screenshots/ui-useful-overview/emulator-settings-dark.png)。闹钟截图为模拟器原 09:21 开启计划，专注截图为已清理的模拟器测试样例。HTML 当时检查了 1360 px / 360 px 的浅深色、字体及图像，无横向溢出或控制台错误；页面中的时间与规则为示例，手机系统闹钟参考画面中的 07:45 不作为朝醒规则。

居中倒计时 APK 为 11,470,460 字节，约 10.94 MiB；当时 v2 签名与 `zipalign -P 16` 通过，见 [打包日志](../design/ui-countdown-package-validation.log)。SHA-256：`951BBB6A8F995F6C8716B73FE5BDFF5B55852801C6C1F0044BE97FB8780034E7`。Galaxy S25+ 当轮覆盖升级，远端 base APK 哈希一致，见 [安装证据](../design/ui-countdown-phone-installation.log)。

居中倒计时当轮真机 [闹钟页](screenshots/ui-useful-overview/phone-alarm-dark.png)、[专注页](screenshots/ui-useful-overview/phone-focus-dark.png) 与 [设置页](screenshots/ui-useful-overview/phone-settings-dark.png) 已实际截图并查看。首页无右上重复设置图标和宣传卡，原 17:24 闹钟仍禁用，工作日、答题关闭及振动配置保留，显示“闹钟已全部关闭”；专注保留原关闭的 17:30—17:34 工作日计划及 2 个应用，不显示空泛状态卡；设置直接显示 4 / 4 权限及入口。手机规则未改动，系统闹钟 07:45 仅作布局参考，检查后已返回主页。

居中倒计时当轮查看 720 × 1600 / density 320 / 字体倍率 2.0 的 [闹钟页](screenshots/ui-useful-overview/emulator-alarm-large-type.png)、[滚动后闹钟页](screenshots/ui-useful-overview/emulator-alarm-large-type-scrolled.png) 和 [设置页](screenshots/ui-useful-overview/emulator-settings-large-type.png)：居中倒计时两行清晰，日期适配、标签自然换行，滚动可见完整闹钟卡片，导航自动纵排。检查后恢复字体倍率 1.0、原尺寸与 density、深色主题，并成功关闭模拟器；检查范围为上述界面。

[示例总览](screenshots/ui-useful-overview/overview.png) 明确标记闹钟及专注倒计时来自模拟器，设置来自真机；[真机三页拼图](screenshots/ui-useful-overview/phone-overview.png) 全部来自实际手机截图。拼图由 [build-countdown-overview.py](../design/build-countdown-overview.py) 在现有 `datasci` / Pillow 环境生成，仅裁切系统状态区、等比缩放并排版，未修改界面内容。此前截图与测试继续保持历史范围。

## 楷体与基线修复历史记录

历史入口：[楷体 APK](../artifacts/daybreak-0.1.0-kai.apk)、[楷体 HTML 预览](../design/朝醒%20·%20楷体版.html)、[真机预览图](screenshots/ui-kai/phone-kai-preview.png)、[实际完整截图](screenshots/ui-kai/phone-after.png)。15 种 Compose Typography 样式均使用内置文楷；`ClockDigits` 明确保留 Gelasio 时间数字。原生专注拦截层的文字、按钮和含中文的倒计时同步使用文楷。`AlarmMetadata` 将闹钟名称、重复日期及下一次唤醒的名称/日期统一为 `bodyLarge`（16 sp / 25 sp、常规字重），通过 `alignByBaseline` 对齐，并允许换行。

楷体当轮检查模拟器深色 [闹钟页](screenshots/ui-kai/emulator-alarm-dark.png)、[专注页](screenshots/ui-kai/emulator-focus-dark.png)、[设置页](screenshots/ui-kai/emulator-settings-dark.png)、[编辑页](screenshots/ui-kai/emulator-editor-dark.png) 和 [浅色主屏](screenshots/ui-kai/emulator-alarm-light.png)，中文文楷与时间数字分别渲染。720 × 1600 / 2 倍字体下手动查看 [列表](screenshots/ui-kai/emulator-alarm-large-type.png)、[编辑页](screenshots/ui-kai/emulator-editor-large-type.png) 及 [滚动后的编辑页](screenshots/ui-kai/emulator-editor-large-type-scrolled.png)，日期在窄屏换行，星期按钮和快捷选项自然换行，保存按钮可达；检查范围为这些具体界面。

楷体当轮 Galaxy S25+ 通过 `adb install -r` 覆盖安装，远端 base APK SHA-256 与交付包一致，见 [安装证据](../design/ui-kai-phone-installation.log)。用户解锁后取得的 [更新后截图](screenshots/ui-kai/phone-after.png) 显示中文文楷，名称与重复日期的纵向 bounds 均为 y = 1130..1187；[更新前截图](screenshots/ui-kai/phone-before.png) 保留此前系统无衬线呈现。当时原 17:24 闹钟仍禁用，工作日、答题关闭及振动配置保留。[真机预览图](screenshots/ui-kai/phone-kai-preview.png) 从完整更新后截图去掉顶部 135 px 系统状态区，再等比缩至 504 px 宽；由 [build-kai-preview.py](../design/build-kai-preview.py) 使用现有 `datasci` 环境中的 Pillow 生成，无新增依赖。

楷体当轮 `assembleDebug`、`assembleRelease` 与 `lintDebug` 通过，见 [构建日志](../design/ui-kai-validation.log)；Lint 为 0 errors / 20 warnings。楷体 APK 为 11,470,460 字节，约 10.94 MiB，v2 签名和 `zipalign -P 16` 通过，见 [打包日志](../design/ui-kai-package-validation.log)。SHA-256：`1CD98043FB5370CDD339BAA9D7FBE93384DF9049F2115AA1165C927937061756`。楷体当轮未重跑单元或 instrumentation 测试；其 HTML 浅色、深色、休息状态与小宽度检查无横向溢出或控制台错误。

楷体当轮视觉检查结束后，模拟器恢复字体倍率 1.0、默认分辨率与深色偏好，并成功关闭。

## 紧凑底栏历史记录

历史入口：[紧凑底栏 APK](../artifacts/daybreak-0.1.0-compact-navigation.apk)、[HTML 预览](../design/朝醒%20·%20紧凑底栏.html)、[导航对比图](screenshots/ui-compact-navigation/navigation-comparison.png)。`ClockNavigationBar` 以 22 dp 图标和 12 sp 文字横排，选中项使用 18 dp 圆角薄荷胶囊；每项点击高度至少 48 dp，常规导航主体为 56 dp（上下各 4 dp 留白，不含分隔线与系统栏）。保留系统底部及左右实际 inset；宽度小于 320 dp 或字体倍率大于 1.6 时改为纵排，标签仍可见，并保留 Tab 选择语义。

1080 × 2424 / density 420 的实际截图中，导航上方分隔线从 y = 2148 下移到 y = 2210，释放约 24 dp 的内容空间，与导航主体从 80 dp 缩至 56 dp 一致。

当轮实际检查 [深色主界面](screenshots/ui-compact-navigation/main-dark.png)、[浅色主界面](screenshots/ui-compact-navigation/main-light.png)、[专注页](screenshots/ui-compact-navigation/focus-dark.png) 和 [设置页](screenshots/ui-compact-navigation/settings-dark.png)，三个导航入口切换有效。720 × 1600 / density 320 / 2 倍字体下 [纵排导航无遮挡](screenshots/ui-compact-navigation/main-small-font2.png)；[系统三键导航](screenshots/ui-compact-navigation/main-three-button.png) 不覆盖按钮。[更新前深色截图](screenshots/ui-compact-navigation/before-dark.png) 为实际应用截图。紧凑底栏 HTML 实载字体及插图正常，无控制台错误或警告，360 px 无横向溢出，预览导航项高 48 px。

紧凑底栏当轮 Debug / Release 构建和 Lint 成功（54 秒），见 [验证日志](../design/ui-nav-validation.log)；Lint 为 0 errors / 20 warnings。该 APK 为 3,136,325 字节，约 2.99 MiB，v2 签名与 `zipalign -P 16` 通过，见 [打包日志](../design/ui-nav-package-validation.log)。SHA-256：`2ED2A1677ED40636CB1084080552B67ACA60EBE03423F00136044EB3AE31146E`。当轮 Release 安装并启动到 Android 37 / 16 KB 无声模拟器，未重复单元或 instrumentation 测试。

紧凑底栏当轮仅替换导航组件，业务、存储、字体及日出曲线遮挡保持既有实现。检查后恢复并确认模拟器手势导航、字体倍率 1.0、1080 × 2424 / density 420 和深色偏好，原计划未修改；当轮启动的无声模拟器已关闭，未操作真机。

## 视觉系统

- 浅色背景 `#F4F7F4`，主色 `#28664A`；深色背景 `#111716`，主色 `#9BD5BA`。使用柔和卡片色与细分隔线建立层级。
- 闹钟卡、编辑/响铃页的 HH:mm 及专注卡时间区间使用本地 Gelasio 衬线数字；中文界面、居中概览与含中文倒计时使用霞鹜文楷。时间按可用宽度与字体倍率缩放，避免与开关挤压。
- 统一留白、圆角卡片、状态标签和图标；主要操作使用明确文字，编辑页底部保留保存按钮。星期按钮至少 48 dp，在窄屏和大字号下换行。
- 主界面底部日出为前景图层，透明区域透出列表，太阳和山丘轮廓覆盖滚入的卡片；响铃页与专注拦截页继续使用小幅插画。插画作为装饰资源，不承载运行状态或操作提示。

## 改动范围

衬线日出修订保留清晨薄荷配色，调整字体、主界面信息密度和底部插画。列表占据完整内容高度，日出绘制在其上方；日出高度为 `min(宽度 × 0.65, 高度 × 0.25, 280 dp)`，下移量为 `min(40 dp, 日出高度 × 0.2)`，常规手机约下移 40 dp，短窗口按比例减小。列表底部留白为日出高度减去下移量再加 20 dp，使末项能完整滚出。

清晨薄荷首轮已完成闹钟、专注、设置与编辑页的层级整理，并将响铃页及无障碍覆盖层统一到同一视觉系统。应用选择弹层跳过半屏，按实际窗口高度限制尺寸，列表伸缩并适配输入法，修正确认按钮落到屏幕外的问题；这些改动保留在当前版本中。

界面主要实现位于 `ClockTheme.kt`、`ClockApp.kt`、`Editors.kt`、`DesignComponents.kt`、`AlarmActivity.kt` 和 `FocusAccessibilityService.kt`；居中倒计时修订增加 `CountdownText.kt` 和供概览使用的 `TimeRules.nextFocusStart`，专注卡片修订增加 `FocusPlanCard` / `FocusTimePoint` 布局，山景流转修订增加 `LandscapeScene.kt` 的持久山景与切换动画，低日修订调整太阳山脊锚点，平滑切页修订修复动画中断重定向，本轮在 `ClockApp.kt` 增加设置入口与两个抽屉。既有闹钟排程、题目校验、跨午夜执行窗口、60 秒应急等待及会话快照规则保持；Room、设备保护执行日志和 DataStore 的结构与读写规则未修改。

## 离线字体与插画

中文字体为 [霞鹜文楷 GB Lite Regular v1.522 官方版本](https://github.com/lxgw/LxgwWenkaiGB-Lite/releases/tag/v1.522)，应用内置 [`wenkai_regular.ttf`](../app/src/main/res/font/wenkai_regular.ttf)，14,116,284 字节。SHA-256：`1675C708CCE181871D9A8ADC987F35A0CABC6FF980685CD99F05D2655EA08C4C`。完整 SIL Open Font License 1.1 保存在 [`LXGWWenKaiGBLite-OFL.txt`](../app/src/main/assets/fonts/LXGWWenKaiGBLite-OFL.txt) 并随 APK 打包；设计预览也保留 [字体](../design/assets/fonts/LXGWWenKaiGBLite-Regular.ttf) 与 [许可证](../design/assets/fonts/LXGWWenKaiGBLite-OFL.txt)。

时间字体来自 [Gelasio 作者仓库](https://github.com/SorkinType/Gelasio)，Regular 的字宽度量兼容 Georgia。应用内置 [`gelasio_regular.ttf`](../app/src/main/res/font/gelasio_regular.ttf)，147,572 字节；预览使用同一字体的 [Gelasio-Regular.ttf](../design/assets/fonts/Gelasio-Regular.ttf)。字体按 SIL Open Font License 1.1 分发，[许可证](../app/src/main/assets/fonts/Gelasio-OFL.txt) 随 APK 打包。

用户允许使用 GPT Image，清晨薄荷首轮生成了薄荷绿山丘与暖色日出的插画。原图保存在 [morning-landscape-original.png](../design/assets/morning-landscape-original.png)，预览 WebP 为 [morning-landscape.webp](../design/assets/morning-landscape.webp)，应用资源为 [`drawable-nodpi/morning_landscape.webp`](../app/src/main/res/drawable-nodpi/morning_landscape.webp)，40,942 字节。衬线日出修订复用既有图像，在绘制时放大 1.4 倍、向下平移图层高度的 11% 再加上述下移量，裁掉透明留边。字体与插画均随 APK 打包，显示时无需联网。

## 衬线日出历史截图与验证

历史入口：[衬线日出 APK](../artifacts/daybreak-0.1.0-serif-sunrise.apk)、[HTML 预览](../design/朝醒%20·%20衬线日出版.html)、[截图总览](screenshots/ui-serif-sunrise/overview.png)。独立截图包括 [浅色主界面](screenshots/ui-serif-sunrise/main-light.png)、[深色主界面](screenshots/ui-serif-sunrise/main-dark.png)、[闹钟编辑页](screenshots/ui-serif-sunrise/alarm-editor.png)、[列表滚入日出](screenshots/ui-serif-sunrise/scroll-under-sunrise.png) 和 [末项完整滚出](screenshots/ui-serif-sunrise/last-alarm-reachable.png)。滚动截图中的 `Sunrise-QA` 为临时测试闹钟，测试后已删除；太阳及山丘自然轮廓覆盖下层卡片，末项能完整滚到插画上方。

当轮实际检查了 360 dp 宽度（720 × 1600、density 320）与 1.5 倍字体下的 [主界面](screenshots/ui-serif-sunrise/main-small-large-font.png) 和 [编辑页](screenshots/ui-serif-sunrise/editor-small-large-font.png)：时间完整、星期按钮按 4 + 3 排列，保存可见。衬线日出 HTML 经浏览器实载确认字体与三张插画正常，控制台无错误或警告，360 px 无横向溢出。

衬线日出当轮 Debug / Release 构建和 Lint 成功（1 分 9 秒），见 [最终验证日志](../design/ui-serif-final-validation.log)；Lint 为 0 errors / 20 warnings。9 项单元测试通过，结果位于 `app/build/test-results/testDebugUnitTest`；`EditorWorkflowTest` 经 adb instrumentation 通过 1 项（5.395 秒），见 [编辑流程日志](../design/ui-serif-editor-test.log)。原有 4 项平台回归属于清晨薄荷首轮证据，衬线日出当轮未重复执行。

[衬线日出 Release APK](../artifacts/daybreak-0.1.0-serif-sunrise.apk) 为 3,136,325 字节，约 2.99 MiB；当时 v2 签名和 `zipalign -P 16` 检查通过，见 [打包日志](../design/ui-serif-package-validation.log)。SHA-256：`F340CB83B3858332C05DDA19BBFF8CDF466FF5416A68904C3C196A60651404BB`。当轮安装并启动到 Android 37 / 16 KB 无声模拟器，测试后恢复原单个闹钟、深色偏好、字体倍率 1.0、1080 × 2424 / density 420；未操作真实手机，也未重新执行 Direct Boot 验证。

## 清晨薄荷首轮历史记录

历史 [截图总览](screenshots/ui-refresh/overview.png) 包含常规界面；独立截图包括 [深色主界面](screenshots/ui-refresh/main-dark.png)、[浅色主界面](screenshots/ui-refresh/main-light.png)、[休息空状态](screenshots/ui-refresh/resting-light.png)、[闹钟编辑页](screenshots/ui-refresh/alarm-editor.png)、[专注页](screenshots/ui-refresh/focus-light.png)、[专注编辑页](screenshots/ui-refresh/focus-editor-light.png)、[设置页](screenshots/ui-refresh/settings-dark.png)、[时间选择](screenshots/ui-refresh/time-picker-light.png)、[应用选择](screenshots/ui-refresh/app-picker-light.png) 和 [输入法展开时的应用选择](screenshots/ui-refresh/app-picker-keyboard-light.png)。

上一轮目视检查常规深浅色，以及 360 dp 宽度（720 × 1600、density 320）与 1.5 倍字体下的 [主界面](screenshots/ui-refresh/main-small-large-font.png)、[编辑页](screenshots/ui-refresh/editor-small-large-font.png)：星期按钮按 4 + 3 排列，时间不挤压，保存按钮可见。[横屏大字号编辑页](screenshots/ui-refresh/editor-landscape-large-font.png) 的内容可滚动，保存按钮保持可见。当时手动验证了搜索 Chrome、选择并确认，以及取消未保存的专注草稿。

上一轮 Debug / Release 构建和 Lint 成功，见 [构建日志](../design/ui-final-validation.log)；Lint 为 0 errors / 20 warnings，当时 9 项单元测试通过。Android 37 / 16 KB 无声模拟器 `emulator-5554` 的 [4 项原有平台回归](../design/ui-platform-regression.log) 通过（26.897 秒），[新增 EditorWorkflowTest](../design/ui-workflow-test-final.log) 1 项通过（6.146 秒），校验每天快捷选择、复健和振动选项持久化、重建保留草稿，以及取消不覆写数据库。

上一轮 `connectedDebugAndroidTest` 下载 UTP 组件时被证书信任错误阻断，测试 APK 改由 adb instrumentation 运行，该 Gradle 任务未记为通过。编辑流程测试使用平台 UiAutomation，避开 Android 37 上不兼容的 Espresso InputManager 调用，并处理缓存刷新及窗口定位。当轮检查结束后恢复了模拟器显示设置和原深色偏好。

上一轮 [Release APK](../artifacts/daybreak-0.1.0-ui-refresh.apk) 为 3,042,635 字节，约 2.90 MiB；当时 apksigner 签名验证和 `zipalign -P 16` 通过，并安装到无声模拟器。SHA-256：`3A41C2D9DB5D5D5E67ED9A253483FD9F9290E1F67A71A190744A45B18EE37902`。

清晨薄荷首轮未安装或操作真实 S25+。更早的首版 APK、截图与设备验收保留在 README 和 [设备验收记录](DEVICE_ACCEPTANCE.md) 中；此前 Direct Boot 与真机结论保持历史范围。上述模拟器结果不能证明真机声音、振动、锁屏响铃、Doze 或三星多窗口行为。
