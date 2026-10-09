# 朝醒 · 专注时段延长修复

2026-10-09，修复将同一专注计划结束时间延后时，旧的过期会话阻止新增时段生效的问题。[本轮 Release APK](../artifacts/daybreak-0.1.0-focus-extension-fix.apk) 已安装到 Galaxy S25+（SM-S9360，Android 16，R5CY22T6SDB），在未重建或修改用户现有计划的情况下恢复 X 拦截，并验证移除朝醒最近任务后仍受限。10 项专注仪器测试、18 项单元测试及构建通过。此前 [服务恢复](FOCUS_RECOVERY.md) 与 [蒙版设计](FOCUS_OVERLAY_DESIGN.md) 保留为历史记录。

## 根因与行为

真机现有“专注时间”计划为每天 01:00—15:30（`startMinute=60`、`endMinute=930`），X 在 5 个目标应用中；但同一天原 01:00—06:30 的快照已经 `EXPIRED`。旧逻辑以 `ruleId:startAt` 识别原窗口，发现旧快照后便不再创建当前延长窗口。约 14:09 的 [拦截检查](../design/debug/focus-live/check-before.log) 为 `blocked=false`，[数据库诊断](../design/debug/focus-live/phone-state-before.log) 同时确认现行计划与旧过期会话。这次故障来自会话接续逻辑。

`FocusEngine.kt` 与 `ClockDatabase.kt` 现在查询原窗口以及所有延长窗口历史。原窗口继续按已保存的快照执行；只有先前窗口到达结束时间，且规则的新结束时间更晚时，才建立从当前时间到新结束时间的接续快照，保留旧会话。已应急解除的窗口在原结束时间前不会被刷新重新锁定，接续段不继承旧 `releaseToken`；已经解除的接续段也不会从过期父窗口重新生成。调度包括原窗口结束边界，避免规则已延长、原窗口已解除时错过接续。`Editors.kt` 同步说明“原窗口继续、延长时段接续”。

## 测试与交付

新增 `FocusExtensionTest` 的 4 项用例在 [旧代码上全部失败](../design/debug/focus-live/extension-red.log)，修复后 [全部通过](../design/debug/focus-live/extension-green.log)，覆盖过期原窗口后的延长、原窗口快照保持、原解除有效期，以及已解除接续段不重建。

[专注回归](../design/debug/focus-live/focus-regression.log) 另通过 6 项：`FocusRecoveryTest` 3 项、`FocusPlatformTest` 自然到期 1 项、`FocusOverlayInteractionTest` 交互 1 项，以及 `ExecutionTest` 专注用例 1 项。与新增 4 项合计，本轮通过 10 项不同的专注仪器测试。

18 项单元测试通过。Debug、Release、AndroidTest 构建及 Lint 成功，见 [构建日志](../design/debug/focus-live/extension-fix-build.log)；Lint 为 0 错误、21 警告。

APK 为 13,390,808 字节，SHA-256：`E2ED81B0A98C9A69501B1B97E6A8B8A9B44AA84E8C342EF9772195DCAEF51B01`。v2 [签名验证](../design/debug/focus-live/apk-signature.log) 与 16 KB 对齐均通过；手机正式 `base.apk` [哈希核验](../design/debug/focus-live/installed-apk-hash.log) 与交付包完全一致。

## 同一计划的真机验证

安装后 14:26:58 的 [X 检查](../design/debug/focus-live/check-after.log) 已为 `blocked=true`，见 [X 蒙版实屏](screenshots/focus-extension/phone-x-blocked.png)。[修复后数据库](../design/debug/focus-live/phone-state-after.log) 保留相同规则和旧 `EXPIRED` 会话，并新增 `ACTIVE` 接续窗口；[应用页面](screenshots/focus-extension/phone-after.png) 显示 1 个活动窗口、5 个应用、恢复时间 15:30。测试没有重建或修改该计划，修复前页面见 [原状态](screenshots/focus-extension/phone-before.png)。15:30 是本次计划的自动恢复时间；自然到期移除蒙版的机制在本轮模拟器回归中通过。

14:29:34 移除朝醒自身最近任务 10993 后，再打开 X 仍为 `blocked=true`，见 [拦截检查](../design/debug/focus-live/check-after-task-removal.log) 与 [移除任务后实屏](screenshots/focus-extension/phone-x-after-task-removal.png)。[后台状态](../design/debug/focus-live/background-state.log) 确认 `TaskGone=true`、服务 `Bound=true` / `Crashed=false`，移除前后 PID 均为 19384。这证明本次移除最近任务和前后台切换后仍能拦截，不代表已验证系统强停或进程被杀后的自恢复。

数据库诊断使用临时平台 Instrumentation；该工具结束会停止应用、断开服务，诊断后仅重连朝醒服务，其他服务设置保持原样。这属于测试副作用，与上述原始会话故障区分。临时 `FocusScheduleInspectionTest` 已删除，[平台 probe](../design/debug/focus-live/probe/FocusInspection.java) 保留为明确的 debug 工具；手机上的 `dev.daybreak.focusprobe`、`dev.daybreak.clock.test` 和临时截图已清理，本轮启动的模拟器已关闭。

[最终手机状态](../design/debug/focus-live/final-phone-state.log) 于 14:30:38 确认正式应用仍安装、服务已绑定且未崩溃；14:32:29 的 [最终 X 检查](../design/debug/focus-live/check-final.log) 仍为 `blocked=true`。手机仅保留正式朝醒包，屏幕超时按用户要求保持 `600000 ms`，即 10 分钟。
