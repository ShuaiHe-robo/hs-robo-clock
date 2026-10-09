# 朝醒 · 专注恢复修复

2026-10-09，针对“无障碍服务已经勾选，但定时专注时段内仍能打开 X”修复服务重连与状态判断，并完成 Galaxy S25+（SM_S9360，Android 16）安装和测试。[本轮 Release APK](../artifacts/daybreak-0.1.0-focus-recovery.apk) 的 18 项单元测试与 6 项模拟器回归通过。首轮在“受限制”电池配置、服务正常时，时段内打开 X 与回桌面重开均被拦截，随后确认三星 MARs 强停朝醒；改为“不受限制”并重新开启服务后，第二个临时窗口内打开 X 及划掉朝醒后重开 X 均受到拦截。此前设置抽屉版的安装与截图继续作为历史版本证据。

## 原因与修复

旧服务只缓存 `TYPE_WINDOW_STATE_CHANGED` 事件的包名，重新连接时没有当前前台应用；如果目标应用已经打开而没有新的切窗事件，就可能漏拦。无障碍配置虽然设置了 `flagRetrieveInteractiveWindows`，但 `canRetrieveWindowContent=false`，无法据此读取当前窗口。旧权限摘要仅查看系统里是否勾选服务，服务已经断开也会误报可用。[修复前日志](../focus-recovery-before.log) 复现了“重连后漏拦已打开应用”和“勾选但断开仍报告可用”两项失败。

本轮启用读取当前窗口的能力，使用窗口根节点包名识别当前应用，不遍历节点、不读取文字；同时处理窗口变化并每秒重新校验，排除朝醒自身覆盖层及输入法窗口。服务连接、解绑、销毁和最近 5 秒内的检查结果共同决定真实运行状态。刷新或覆盖层显示异常后可以继续重试；receiver 与界面刷新捕获非取消异常，避免一次失败中断后续恢复。`FocusEngine` 仅在调度成功后缓存 `boundaryAt`，调度失败时保留重试机会。

既有界面结构保持。专注页异常提示可直接打开无障碍设置；权限抽屉显示实际连接与检查状态，摘要改为“已就绪”。新增朝醒系统设置入口，说明三星电池应设为“不受限制”并移出深度休眠。读取窗口所需能力见 Android 官方 [getWindows()](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#getWindows()) 与 [FLAG_RETRIEVE_INTERACTIVE_WINDOWS](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS)。

## 验证与交付

[最终模拟器回归日志](../focus-fix-platform-validation.log) 为 `OK (6 tests)`（33.309 秒）：

| 用例 | 本轮覆盖 |
| --- | --- |
| `FocusRecoveryTest`，3 项 | 对已打开 Chrome 的服务重连拦截；勾选但断开时报告未运行；真实刷新异常与 receiver 不崩溃并恢复 |
| `FocusPlatformTest`，1 项 | 活动应用被覆盖；删除规则不提前解除，时段结束自动移除覆盖层 |
| `ExecutionTest`，1 项 | 重叠窗口、删除规则与应急解除仍遵循原会话范围 |
| `CountdownOverviewTest`，1 项 | 专注倒计时及应急按钮可用 |

18 项单元测试通过：`TimeRulesTest` 12 项、`CountdownTextTest` 3 项、`FocusServiceHealthTest` 3 项。最终 Debug、Release、`testDebugUnitTest` 与 `lintDebug` 构建成功，见 [构建日志](../focus-fix-build-validation.log)。Lint 为 0 错误、20 警告。

最终 Release 已在模拟器覆盖安装并视觉检查：[权限抽屉实屏](screenshots/focus-recovery/emulator-permissions-running.png) 显示“4 / 4 已就绪”与“无障碍服务已连接 · 拦截检查正常”，后台设置按钮能打开朝醒的系统应用详情。`dumpsys accessibility` 确认服务已绑定、`capabilities=1`。开发检查时，普通 `uiautomator dump` 会短暂抑制无障碍服务；检查工具应使用 `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`，避免把工具造成的临时断连当成正常运行状态。

APK 为 11,807,776 字节，v2 签名与 16 KB `zipalign` 检查通过，见 [签名日志](../focus-fix-signature.log) 和 [对齐日志](../focus-fix-alignment.log)。SHA-256：`0D696DCB877DE761D294ED240EC1296A92504344EC287221FC9F7E9022F6595B`；手机安装的 `base.apk` 哈希与该交付包一致，见 [真机哈希核验](../focus-fix-phone-installed-hash.log)。

本次真机验收沿用上述代码与 APK，没有为手机测试额外修改代码或重新构建。模拟器中的重连、检查恢复与自然到期结论仍保留其验证范围。

## 真机后台问题与验收

本轮 `install -r` 覆盖安装成功。用户手动重新开启“朝醒 · 定时应用锁”后，`dumpsys accessibility` 确认服务已绑定、`retrieveInteractiveWindows=true`、`capabilities=1`。13:16 时段内打开 X 已拦截，回桌面再打开仍拦截，见 [X 拦截实屏](screenshots/focus-recovery/phone-x-blocked.png)。

手机原电池配置为 [“受限制”](screenshots/focus-recovery/phone-battery-before.png)。在该配置下，从最近任务划掉朝醒后，13:17:01.789 的 pid 23553 退出原因为 `FORCE STOP`，描述为 `stop dev.daybreak.clock due to MARs #2`，见 [退出记录](../focus-fix-phone-exit-info-after-dismiss.log)。系统勾选仍在，服务却处于 Crashed / 不工作，见 [异常状态实屏](screenshots/focus-recovery/phone-service-not-working.png)；[此前退出记录](../focus-fix-phone-exit-info.log) 还包含多次相同 MARs 强停。

通过系统界面仅将朝醒电池配置改为 [“不受限制”](screenshots/focus-recovery/phone-battery-unrestricted.png)，系统 deviceidle 白名单新增 `user,dev.daybreak.clock,10531`，MARs 的 FAS 从 Y 变为 N；随后仅将朝醒无障碍服务关闭后再开启，其他服务未改动。三星的后台限制与应用管理背景见 [官方说明](https://developer.samsung.com/mobile/app-management.html)。

第二轮临时计划为 12:00—14:17。13:25 从最近任务仅划掉朝醒后，再次打开 X 仍被覆盖，见 [划掉后台后的拦截实屏](screenshots/focus-recovery/phone-x-blocked-after-dismiss.png) 与 [验证日志](../focus-fix-phone-validation.log)。检查时最近任务中已无朝醒 `MainActivity`，窗口焦点属于 `dev.daybreak.clock`，底层 focusedApp 为 X。13:30 左右再次核验 [服务状态](../focus-fix-phone-accessibility-final.log) 与 [退出记录](../focus-fix-phone-exit-info-final.log)，最新退出仍为 13:17:01.789；改为“不受限制”后，从划掉任务到最终检查未出现新增进程退出。

测试完成后删除临时计划 `X-test-1311`，活动快照继续拦截；正常等待 60 秒并再次确认应急解除，13:27 的 X 窗口恢复可用。原唯一计划“专注时间”仍为每天 01:00—06:30、5 个应用、开启，见 [清理后实屏](screenshots/focus-recovery/phone-cleaned.png)。13:29 的 [最终权限抽屉实屏](screenshots/focus-recovery/phone-permissions-running.png) 显示“4 / 4 已就绪”与“无障碍服务已连接 · 拦截检查正常”。

## 尚待验证的范围

第一轮 13:17 的计划到期与 MARs 强停发生在同一时刻，不能据此独立证明真机自然到期成功；自然到期移除覆盖层目前有模拟器证据。真机尚未做锁屏后解锁、长待机或夜间、重启、内存杀进程后的自恢复，以及系统强停后的自启动验证。

从最近任务划掉朝醒和系统“强行停止”需分别理解；本轮通过的是改为“不受限制”后的划掉任务测试。Android 的停止状态会保持到用户交互，见 [官方停止状态说明](https://developer.android.com/about/versions/15/behavior-changes-all)。本轮不承诺绕过系统强停，后续继续验收锁屏、长待机和自然到期。
