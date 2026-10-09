# 朝醒 · 专注蒙版设计

2026-10-09，按用户要求使用 `claude-design` 优化被限制应用的拦截蒙版，采用 A“晨间留白”。[本轮 Release APK](../artifacts/daybreak-0.1.0-focus-overlay.apk) 已覆盖安装到三星 Galaxy S25+，构建、18 项单元测试及模拟器验证通过。[三方向 HTML 设计](../design/朝醒%20·%20专注蒙版-v1.html) 中，B“山间静夜”、C“时间书签”保留为视觉探索，A 已实施并支持系统深浅色。此前服务恢复修复及真机 X 验收继续见 [专注恢复记录](FOCUS_RECOVERY.md)，本轮不改专注引擎或拦截、恢复规则。手机本轮处于锁屏状态，蒙版 UI 与交互验证来自模拟器。

## 原生布局

`FocusOverlayView.kt` 使用居中布局，沿用文楷中文、Gelasio 衬线数字与品牌薄荷绿。透明纸感山间小路插画置于主标题上方，配合目标应用名、时分秒倒计时和自动恢复时间，让等待状态清晰可读。衬线时间开启 `tnum`，数字宽度保持一致。主操作“返回桌面”高 56 dp，应急操作高 48 dp。

蒙版随系统切换深浅色；正文可滚动，容纳较大的系统字体，以及应急等待、取消、等待完成后的确认状态。`onConfigurationChanged` 重建蒙版，在运行中切换主题、字体或方向时维持引擎会话与应急计时。返回桌面及取消等待继续保持限制，等待完成后仍需再次确认解除。界面继续调用既有拦截与服务恢复逻辑，不改引擎。

原生实屏：[浅色倒计时](screenshots/focus-overlay/light-hours.png)、[深色倒计时](screenshots/focus-overlay/dark-hours.png)、[应急等待](screenshots/focus-overlay/emergency-wait.png)、[大字体顶部](screenshots/focus-overlay/large-font-top.png)、[大字体操作区](screenshots/focus-overlay/large-font-actions.png)、[1920 × 1080 短宽屏顶部](screenshots/focus-overlay/wide-top.png)、[短宽屏操作区](screenshots/focus-overlay/wide-actions.png)。短屏需要向下滚动才能到达操作按钮。

## 设计与验证状态

HTML 已在真实内置浏览器加载，本地字体和三张图均加载成功，控制台无错误或警告（`[]`）；三方案画布统一为 899 px 可用内容高度，窄宽屏没有水平溢出，已操作应急等待、取消和主题切换。上述原生截图用于检查实际布局，HTML 的 B、C 方向不代表已实施的原生界面。

最终 Debug、Release、测试 APK、18 项单元测试及 Lint 成功，见 [交付构建日志](../design/focus-overlay-delivery-build.log)；Lint 为 0 错误、21 警告。

[仪器回归日志](../design/focus-overlay-instrumentation-awake.log) 为 `OK (5 tests)`，64.341 秒，包含新增 `FocusOverlayInteractionTest` 的 1 项交互用例、`FocusRecoveryTest` 的 3 项恢复用例与 `FocusPlatformTest` 的 1 项自然到期用例。交互用例覆盖返回桌面仍锁定、应急等待仍锁定且等待按钮禁用、取消后继续锁定、1.6 倍字体操作可达，以及等待完成后确认解除。

随后在最终 Debug 构建上扩展并重跑同一交互用例，主动唤醒 fixture，在蒙版显示期间将字体切换为 1.6 倍、不重新启动目标应用，并检查 1920 × 1080 短宽屏可滚动到按钮。[实时配置日志](../design/focus-overlay-live-config.log) 为 `OK (1 test)`，13.825 秒。该次是重复验证，不算第 6 个不同测试。

首次仪器测试因模拟器从旧快照自动睡眠而超时，唤醒并保亮后重跑，见 [重跑日志](../design/focus-overlay-instrumentation-awake.log)；新 fixture 也明确唤醒设备。此处不将首次超时记为拦截逻辑失败。

## APK 与真机安装

[专注蒙版 Release APK](../artifacts/daybreak-0.1.0-focus-overlay.apk) 为 13,390,808 字节，SHA-256：`F1262AF9FD62E1A2D121E8D7B950BCBC8B5099274C1BE9E39F5F33E78B2EB039`。apksigner v2 签名与 16 KB `zipalign` 检查成功，见 [APK 验证日志](../design/focus-overlay-apk-validation.log)；日志开头的 JDK native access 提示为工具警告，不影响签名验证结果。

已通过 `adb install -r` 覆盖安装到三星 SM-S9360，见 [安装日志](../design/focus-overlay-phone-installation.log) 的 `Success`。安装后的手机 `base.apk` 哈希与交付包完全一致，见 [手机哈希日志](../design/focus-overlay-phone-installed-hash.log)。13:58:34（UTC+8）启动后核验朝醒无障碍服务 `Bound=true`、`Crashed=false`，见 [最终运行摘要](../design/focus-overlay-phone-runtime-final.log)。手机屏幕锁定，本轮未操作真机 X 蒙版；此前真机 X 拦截结果属于服务恢复轮次，不作为本轮蒙版 UI 验证。模拟器已关闭，测试尺寸、字体倍率及深浅色配置已还原，临时 fixture 已由 `finally` 清理。

## 插画资产与生成提示词

插画使用内置 `image_gen` 生成，最终采用原图。原资产为 [quiet-path.png](../design/assets/focus-overlay-v1/quiet-path.png)，原生资源为 [focus_quiet_path.png](../app/src/main/res/drawable-nodpi/focus_quiet_path.png)，两者字节相同。PNG 为 RGBA，1536 × 1024，2,144,004 字节；透明区域保留，纸感山丘与太阳为不透明主体。

完整提示词：

```text
Use case: illustration-story. Asset type: transparent landscape illustration for the native Android app 朝醒, a calm scheduled focus blocker. Generate a new standalone raster illustration, not a mockup. A small warm pale apricot rising sun peeks over softly layered sage and mint green hills; a narrow ivory path curves quietly from the foreground through the hills toward the sun. Hand cut paper collage with fine natural watercolor paper grain, soft imperfect organic edges, restrained Chinese picture-book elegance. Wide 3:2 composition, scene fills most of the canvas with a little transparent breathing room around it. Three simple hills, tiny understated grass shapes, no buildings, no people, no clouds, no additional objects. Palette limited to deep forest green #28664A, sage #83AE93, pale mint #C2DCC8, sun #F2CD8C and warm ivory. Legible as a 280dp-wide mobile illustration on either light #F4F7F4 or dark #111716 backgrounds. Fully transparent background outside the organic scene; opaque paper-textured hills and sun. No text, letters, watermark, border, frame, shadow or interface controls. Keep the feeling warm, quiet and inviting, never punitive.
```
