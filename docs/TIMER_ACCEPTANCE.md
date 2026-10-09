# 朝醒 · 计时器交付与验收

2026-10-09，新增独立“计时器”页面。[本轮 Release APK](../artifacts/daybreak-0.1.0-timer.apk)、[claude-design 三方向交互稿](../design/朝醒%20·%20计时器.html) 与原生截图已保存。用户所说的可选“复健”沿用现有闹钟定义：答对一道两位数加减题才能关闭到时提醒。最终数字居中版本已完成生产构建及深浅色、窄屏大字体检查；此功能版本随后已安装到 Galaxy S25+（`R5CY22T6SDB`）；以下测试仍以功能轮次的模拟器结果为准，不代表真机声音验收。后续山景修订另见 [计时器山景记录](TIMER_LANDSCAPE_ACCEPTANCE.md)。

## 页面与操作

底部导航顺序为“闹钟 / 专注 / 计时器 / 设置”。计时器沿用当前清晨薄荷配色、霞鹜文楷中文、Gelasio 时间数字、圆角卡片与山景。交互稿提供 A 居中时间卡片、B 紧凑布局、C 细线圆框三个方向；原生页面采用 A。

- 以时、分、秒设定时长，范围为 1 秒至 24 小时；提供 1、5、10、25 分钟快捷时长。
- 同一时间运行一个计时器；开始前选择振动与复健，铃声使用默认闹钟铃声。
- 运行时显示剩余时间，支持暂停、继续及确认取消。切换页面、重建 Activity 后保留计时状态。
- 到时进入现有闹钟响铃页。启用复健时，错误答案保持响铃，正确答案结束会话；普通计时器无需答题即可关闭。取消操作不能结束已进入响铃的复健会话。

原生模拟器截图：[设置](screenshots/ui-timer/timer-idle.png)、[控制区](screenshots/ui-timer/timer-controls.png)、[运行](screenshots/ui-timer/timer-running.png)、[暂停](screenshots/ui-timer/timer-paused.png)、[到时复健](screenshots/ui-timer/timer-ringing.png)。设置截图已更新为最终数字居中版本；补充截图见 [浅色](screenshots/ui-timer/timer-light.png)、[320 dp / 2 倍字体](screenshots/ui-timer/timer-large.png) 与 [大字体控制区](screenshots/ui-timer/timer-large-controls.png)。

## 调度与恢复

到时提醒复用 `AlarmManager → Receiver → AlarmRingingService / AlarmActivity` 链路。计时器及响铃快照通过 `BootStore` 原子写入设备保护存储；现有 Room schema 保持不变。

同一次设备启动中，以 `elapsedRealtime` 和 boot count 计算剩余时间，避免调整系统时钟改变运行倒计时。设备重启后，按保存的 wall deadline 恢复剩余时间，并限制为原始时长以内。暂停保存剩余时间；继续生成新的 `occurrenceId`，旧回调不会触发响铃。取消状态在恢复后仍有效，已响铃会话的关闭规则继续使用保存的快照。

持久化读取、恢复入口及过期回调的行为已通过模拟器测试；本轮没有实际重启设备完成计时器验收，不能将实现恢复逻辑或调用恢复入口的结果视为真实重启/首次解锁前验证。

## 已完成验证

[前一轮构建日志](../timer-final-build.log) 记录 Debug、Release、AndroidTest 构建及 Lint 成功。最终数字居中版本的 [生产构建日志](../timer-release-validation.log) 记录 Debug、Release、21 项单元测试与 Lint 成功，其中新增 3 项 `TimerRulesTest` 覆盖时间计算及显示边界。Lint 为 0 错误、23 警告，保留既有依赖版本、配置与 API 建议；本轮没有添加依赖。

[模拟器仪器测试日志](../timer-final-tests.log) 记录 10 项测试通过（31.675 秒）：

| 测试 | 数量 | 验证范围 |
| --- | ---: | --- |
| `TimerWorkflowTest` | 1 | 设置、开始、暂停/继续、确认取消、页面切换和 Activity 重建 |
| `TimerPlatformTest` | 1 | 真实精确闹钟回调进入前台响铃服务及通知，错误/正确答案关闭规则 |
| `TimerExecutionTest` | 2 | 持久化读取、暂停/取消恢复、旧回调失效及复健关闭约束 |
| `LandscapeMotionTest` | 3 | 既有山景动画回归 |
| `EditorWorkflowTest` | 1 | 既有编辑流程回归 |
| `ExecutionTest` | 2 | 既有执行逻辑回归 |

平台测试在 `-no-audio` 无声模拟器运行；通过结果确认服务、通知和答题关闭逻辑，不代表扬声器发声或振动效果已验收。持久化测试中的到期情形包含修改保存的 deadline 后调用恢复/触发入口；真实系统回调由 `TimerPlatformTest` 单独覆盖。

HTML 交互稿已在浏览器实际加载字体与山景资产，并检查主流程、错误/正确答案、三种布局、深浅色及约 327 px 宽视口，未发现横向溢出或控制台错误/警告。

最终原生页面的 [深色检查](../timer-visual-dark.log)、[浅色检查](../timer-visual-light.log) 与 [窄屏大字体检查](../timer-visual-large.log) 各通过一次。三次均以 `TimerWorkflowTest` 的 capture 模式捕捉布局，属于同一用例重复运行，不能将本轮 10 项不同仪器测试写成 13 项不同测试。窄屏环境为 320 dp（840 px、420 dpi），字体为 2.0 倍；底栏自动排列为 2 × 2，控制按钮可通过滚动触达，末尾的完整“开始计时”按钮可滚出山景遮挡。测试后模拟器已恢复 `font_scale=1.0` 与原始窗口尺寸。

## 待验收范围

计时器功能版本随后已安装到 Galaxy S25+，但未完成真机声音、振动、锁屏、Doze、长待机、实际重启及首次解锁前计时器验证。此前闹钟 Direct Boot 验证仍属于历史闹钟证据，不移作本轮计时器通过结论。

## 交付包

[计时器 Release APK](../artifacts/daybreak-0.1.0-timer.apk) 为 14,497,312 字节，约 13.83 MiB；SHA-256：`CB442AA11E8FD57E8028E121D64CC69CB28ECDD319FF8C6E4DADF12D7A883717`。v2 签名验证通过，`zipalign -c -P 16 4` 返回 0，见 [打包验证日志](../timer-apk-verification.log)。此功能版本随后已安装到 Galaxy S25+；安装成功不代表真机声音、振动或后台行为已验收。后续山景修订的交付另见 [新记录](TIMER_LANDSCAPE_ACCEPTANCE.md)。
