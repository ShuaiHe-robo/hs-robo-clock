# 朝醒 · 计时器实机验收

2026-10-09 约 17:12—17:22（北京时间），按用户“安装到我的手机上进行实机测试”的要求，在 Galaxy S25+（SM-S9360，`R5CY22T6SDB`，Android 16 / API 36）测试最终 [山景版 Release APK](../artifacts/daybreak-0.1.0-timer-landscape.apk)。用户明确回复“铃声和振动都正常”；输入、暂停/继续、后台到时、复健答题与取消流程均完成验证，本轮未修改应用代码。

## 安装与方式

重新执行 `adb install -r` 返回 Success，手机 `base.apk` SHA-256 仍为 `112058AB657870B5CD10A8ED5CFFBEC976BBB9B52394E3CFF84E6611F9965777`，与最终交付包一致，APK `targetSdk=37`。未安装 Debug 测试 APK。使用已有 `android-cli` 与 ADB 在真实手机上触控操作、读取界面和捕捉截图，声音与振动由用户实际感知确认；这次是工具辅助的实机交互验收。

可复用的原生触控录制工具保存在 [phone-timer-test.ps1](../artifacts/phone-timer-test.ps1)。

## 实机结果

| 项目 | 观察与证据 |
| --- | --- |
| 时间输入 | 从默认 5 分钟改为分钟 `00`、秒 `30`，三星数字键盘可输入且不遮挡字段；全零时显示范围提示。[键盘](screenshots/ui-timer-phone/keyboard.png)、[30 秒设置](screenshots/ui-timer-phone/setup-30-seconds.png) |
| 暂停与状态保留 | 复健和振动开启后启动 30 秒；暂停时剩余 13 秒，等待 3 秒、切到专注页再返回，以及横竖旋转后仍为 13 秒。[运行](screenshots/ui-timer-phone/running.png)、[暂停](screenshots/ui-timer-phone/paused.png)、[横屏暂停](screenshots/ui-timer-phone/landscape-paused.png) |
| 横屏山景与太阳 | 四页山景铺满窗口，太阳完整且沿闹钟→专注→计时器→设置向左移动并缩小。四页实屏：[闹钟](screenshots/ui-timer-phone/landscape-page-0.png)、[专注](screenshots/ui-timer-phone/landscape-page-1.png)、[计时器](screenshots/ui-timer-phone/landscape-page-2.png)、[设置](screenshots/ui-timer-phone/landscape-page-3.png) |
| 后台到时 | 继续剩余 13 秒后返回桌面；到时触发前台 `AlarmRingingService` 与 `category=alarm` 通知，系统记录 `USAGE_ALARM` 音频处于 started，用户确认铃声和振动正常。[服务日志](../timer-phone-ringing-service.log)、[音频日志](../timer-phone-audio-active.log) |
| 复健关闭 | 手动返回应用进入关闭响铃页，题目 `39 + 83`；输入 `0` 显示“答案不对，再试一次”并保持响铃，输入 `122` 后关闭并回到空闲。[题目](screenshots/ui-timer-phone/math-challenge.png)、[错误答案](screenshots/ui-timer-phone/wrong-answer.png)、[关闭后](screenshots/ui-timer-phone/dismissed.png) |
| 取消确认 | 使用 1 分钟快捷时长、复健关闭，首次取消弹窗选择保留后继续倒计时，第二次取消并确认回空闲。[确认弹窗](screenshots/ui-timer-phone/cancel-confirm.png)、[取消后](screenshots/ui-timer-phone/cancelled.png) |

后台到时时，解锁手机仍停留桌面；随后手动返回应用进入 [关闭响铃入口](screenshots/ui-timer-phone/ringing-main.png)，本次未观察到自动全屏弹出。正确答案关闭后，[状态日志](../timer-phone-after-dismiss.log) 确认 `Active AlarmRingingService: False`，UID 10531 的活动音频播放器也为 False。响铃时的系统音频记录为 `piid=46263`、`uid=10531`、`state=started`、`usage=USAGE_ALARM`。

## 测试后状态

最终恢复默认 5 分钟、复健关闭、振动开启，没有临时计时器，见 [最终空闲页](screenshots/ui-timer-phone/final-idle.png)。原有 08:15 闹钟和 1 条专注计划未改；应用原浅色主题保持。

测试后应用 logcat 最近 500 条未匹配到 `FATAL EXCEPTION` 或 `ANR`。操作工具 controller 已退出，生产 `FocusAccessibilityService` 仍绑定，`AlarmRingingService` 已结束。

系统旋转设置恢复为 `accelerometer_rotation=1`、`user_rotation=0`、`wm fixed-to-user-rotation=default`。锁屏、Doze、长待机、真实重启及首次解锁前计时器行为尚未测试。
