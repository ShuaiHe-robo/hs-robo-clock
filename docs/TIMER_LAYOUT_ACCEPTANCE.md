# 朝醒 · 计时器一屏布局

2026-10-09，调整计时器空闲页，将时间设置与到时提醒完整放入常规窗口首屏，并在宽矮窗口使用左右两栏。[最终 Release APK](../artifacts/daybreak-0.1.0-timer-fit.apk) 与 [claude-design 交互稿](../design/朝醒%20·%20计时器一屏布局.html) 已保存；最终构建与动效测试通过，APK 已再次覆盖安装到 Galaxy S25+。

## 布局行为

空闲页移除重复大号时钟；外边距为 16 dp、卡间距为 10 dp，时间卡内边距为 12 / 14 dp、圆角为 22 dp。四个快捷时长固定为四列，每项高 48 dp；紧凑提醒区将复健、振动开关并排，并保留答题关闭说明。运行和暂停状态继续展示大号时钟，暂停/继续与取消按钮横排。

正常竖屏采用紧凑纵排；可用宽度至少 560 dp、可用高度小于 400 dp 时左右两栏。可用高度小于 400 dp 的空闲页省略重复页标题，保留“设定时间”卡标题与完整山景。常规窗口使用固定布局；极端大字号、键盘缩小可用高度或缺权限提示需要更多高度时保留滚动以保证操作可访问，不承诺所有尺寸都无需滚动。

计时器山景单独平滑缩减景深：竖屏比例 0.25、宽矮屏比例 0.30，高度限制为 180 dp，沿用几何计算后乘 0.80，为图样与按钮保留间隔。太阳横向位置及相对尺寸的方向参数保持。

## 已验证结果

浏览器实际检查 390 × 760、360 × 640、720 × 360 三种尺寸，快捷时长→开始→暂停→取消流程通过，深浅色正常，素材全部加载，控制台无错误。

最终 [交付构建日志](../timer-fit-delivered-build.log) 记录 Debug、Release、Lint 成功（1 分 8 秒），Lint 为 0 错误、23 警告。本轮 23 项单元测试通过，见 [单元测试构建记录](../timer-fit-final-build.log)。

[最终小屏首屏检查](../timer-fit-small-delivered-test.log) 1 项通过（4.913 秒）；与此前 [横屏检查](../timer-fit-landscape-test.log)、[工作流程检查](../timer-fit-workflow-test.log) 合计为 `TimerWorkflowTest` 的 3 次不同配置回归，不记为 3 个不同测试用例。[动效测试](../timer-fit-motion-test.log) 另通过 3 项（1.4 秒）。

首屏断言覆盖全部三个时长输入框，以及“设定时间”“到时提醒”“复健”“振动”“开始计时”可见，且无竖向滚动容器。矮屏初次失败已修复；[小屏完整检查](../timer-fit-small-complete-test.log) 与最终交付检查均验证省略空闲页重复标题的方案通过。

Galaxy S25+ 已检查竖屏和横屏全卡可见，实际操作 10 分钟快捷时长的开始、暂停、继续及取消，结束后恢复 5 分钟。最终 APK 再次覆盖安装成功，竖屏实屏已重拍。截图：[竖屏](screenshots/ui-timer-fit/phone-portrait.png)、[横屏](screenshots/ui-timer-fit/phone-landscape.png)、[暂停](screenshots/ui-timer-fit/phone-paused.png)。

原有 1 个闹钟、1 条专注计划保持；手机旋转偏好恢复为 `accelerometer_rotation=1`、`user_rotation=0`、`fixed-to-user-rotation=default`，CLI controller 已停止。此前声音、振动与复健真机结果保留在 [实机记录](TIMER_PHONE_ACCEPTANCE.md)，不作为本轮重新测试结论。

## 交付包

[最终 APK](../artifacts/daybreak-0.1.0-timer-fit.apk) 为 15,215,981 字节，约 14.51 MiB；SHA-256：`0C312455C7CC12AFC3C33E3C132A901EA7C15AA636F4FFAFA448D88E57DF3323`。Galaxy S25+ 再次执行覆盖安装，结果为 Success。
