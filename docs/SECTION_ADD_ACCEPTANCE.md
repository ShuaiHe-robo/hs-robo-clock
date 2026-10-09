# 朝醒 · 卡片区域添加入口

2026-10-09，移除页底悬浮添加按钮，将“添加闹钟”放在“你的闹钟”标题右侧、“添加计划”放在“定时计划”标题右侧。两个入口共用 `DesignComponents.ListSectionHeading`，沿用当前配色和字体，最小高度为 48 dp；局部宽度小于 320 dp 或 `fontScale > 1.4` 时换行靠右。

[claude-design 交互稿](../design/朝醒%20·%20卡片区域添加入口.html) 已实际验证浅色、深色、大字号、宽模式与两个模拟添加入口，浏览器控制台无错误。

## 验证与安装

[构建日志](../section-add-build.log) 记录 Debug、Release、AndroidTest 和 Lint 成功（49 秒），Lint 为 0 错误、23 警告。[编辑流程测试](../section-add-editor-test.log) 为 `EditorWorkflowTest` 1 项通过（7.098 秒）。此前计时器功能及实机验收仍按原轮次记录。

[最终 Release APK](../artifacts/daybreak-0.1.0-section-add.apk) 为 15,232,365 字节，约 14.53 MiB；SHA-256：`9DF3F090A18A5D9FBA902A9F9304D10A01137D320C0BB1B4857FAE07FD849514`。ADB 覆盖安装到 Galaxy S25+（`R5CY22T6SDB`）成功，手机两个按钮均点击进入正确的添加编辑页，再返回未保存，仍保留原有 1 个闹钟和 1 条专注计划。

| 页面 | 真机 | 320 dp / 2 倍字体模拟器 |
| --- | --- | --- |
| 闹钟 | [实屏](screenshots/ui-section-add/phone-alarm.png) | [实屏](screenshots/ui-section-add/emulator-alarm-large.png) |
| 专注 | [实屏](screenshots/ui-section-add/phone-focus.png) | [滚动后实屏](screenshots/ui-section-add/emulator-focus-large.png) |

窄屏大字体下，两个添加按钮完整显示并靠右，添加计划可进入编辑页。专注截图中底部装饰仍遮挡部分空状态文案，该检查确认添加入口可见与可操作，未将整页无遮挡记为通过。

测试后恢复模拟器字体和尺寸，关闭本轮自启模拟器，手机 CLI controller 已停止。
