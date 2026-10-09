# 朝醒 · 计时器同系山景与横屏修订

2026-10-09，按用户要求为计时器增加独立图样，修正横屏山景截断，并让四页太阳以统一方向移动。[本轮 Release APK](../artifacts/daybreak-0.1.0-timer-landscape.apk) 已完成构建并覆盖安装到 Galaxy S25+（`R5CY22T6SDB`），手机安装包哈希与交付包一致。23 项单元测试、8 项不同仪器测试及真实横竖屏布局检查通过。此前计时器功能版的行为与历史测试保留在 [计时器记录](TIMER_ACCEPTANCE.md)。

## 素材来源与取舍

用户指定参考 [原山景对话](codex://threads/01a11ae3-1bdb-78a3-affd-93e5e344d96c) 的生成 prompt。已读取原 `imageGeneration` 提示词，原文也保存在 [山景与太阳生成记录](../design/landscape-motion-prompts.md)。本轮使用内置 `image_gen` 重新生成素材，以原专注页 `focus-hills` 的细纸纹、柔和剪纸质感和薄荷/森林绿配色为参照，保留当前项目的视觉语言。

首版 [溪湾设计稿](../design/朝醒%20·%20计时器%20·%20溪湾山景.html) 被用户否决，已废弃，不是最终采用方向。最终使用 [同系山景设计稿](../design/朝醒%20·%20计时器%20·%20同系山景.html)：左侧低浅色坡，右侧森林绿圆坡，浅薄荷前景与下左两枚叶片，仅改变山坡构图。原计时器三方向稿保留为功能页面的历史设计。

最终 [PNG 原图](../design/assets/timer-landscape/timer-hills-v2.png) 与 [无损 WebP](../design/assets/timer-landscape/timer-hills-v2.webp) 均为 1774 × 887，具有真实 RGBA 透明上缘；生产资源为 `app/src/main/res/drawable-nodpi/scenic_hills_timer.webp`。运行时离线绘制，与其他三页的独立山景配合。

本轮调用内置 `image_gen`，设置 `transparent_background=true`，参考编辑目标为 `design/assets/landscape-motion/focus-hills.png`。生成输出文件名为 `exec-631dfead-2fab-45fb-83a6-9719e1d8d29c.png`；随后仅使用现有 conda `datasci` 环境中的 Pillow 转换为无损 WebP，没有绘制、调色或裁切。

实际发送的完整 prompt 原样保留如下：

```text
Use case: precise-object-edit. Asset type: transparent foreground hills layer for the timer page of the 朝醒 Android app. Edit target: attached focus hills image. Preserve EXACTLY its soft watercolor/cut-paper grain, restrained mint and forest-green palette, flat rounded shapes and minimal graphic leaves. Recompose the hills into a NEW visual sibling: a low pale sage ridge entering from LEFT, a softly rounded forest-green hill rising on the RIGHT, and a long light-mint foreground slope flowing gently downward from left to right. A low open saddle around one third of the width leaves space for a separately drawn sun. The silhouette must clearly differ from the attached left-high focus valley. Three simple overlapping rounded hill shapes, only one small group of two or three flat oval leaf marks near the lower left. Match the reference's fine paper texture and color closely, with no extra detail. ONLY hills, no sun, no sky, no glow. Wide 2:1 cutout, genuine transparency above the organic skyline; hills extend fully across both side edges and solidly fill the bottom edge. No creek, water, paths, realistic plants, flowers, sharp mountains, bright yellow-green, scenery detail, text, frame, app UI, white or black background. Keep the quiet handmade visual language unchanged while making the contour composition new.
```

## 横屏布局与太阳

`LandscapeScene` 使用屏幕全宽；正文以最大 720 dp 独立居中。山景画框高度改为 `min(width × 0.50, height × 0.32, 240 dp)`，同时受窗口高度约束，避免横屏仅按宽度计算过高背景。移除图片竖向平移和 1.10 倍放大，保持完整素材轮廓。

`Scaffold` 仅应用竖向 `safeDrawing` inset，横向安全边距单独作用于正文和编辑器，让装饰背景在横屏刘海一侧也铺满窗口。`consumeWindowInsets` 避免重复键盘间距，`MainActivity` 使用 `adjustResize`；本轮编辑器流程及真实横屏布局检查通过。

四个导航页各用独立山景，同一太阳依次向左移动并缩小，位置与尺寸使用统一的 760 ms 动画。太阳实际尺寸额外受画框高度的 0.86 倍与 136 dp 限制，并约束到画框边界内。

| 页面 | 太阳横向位置比例 | 太阳尺寸比例 |
| --- | ---: | ---: |
| 闹钟 | 0.72 | 0.34 |
| 专注 | 0.46 | 0.28 |
| 计时器 | 0.34 | 0.24 |
| 设置 | 0.23 | 0.20 |

这些比例描述实现参数；本轮原生四页截图确认横屏完整轮廓和太阳从右向左的顺序。

## 验证记录

新增 `LandscapeSizingTest` 两项用例在原尺寸算法上失败，见 [失败记录](../timer-art-red.log)。最终 [生产构建日志](../timer-art-final-build.log) 为 `BUILD SUCCESSFUL in 48s`，Debug、Release、AndroidTest、23 项单元测试与 Lint 全部通过；Lint 为 0 错误、23 项既有警告。

最终 [仪器回归日志](../timer-art-instrumentation.log) 记录 8 项不同用例通过（25.451 秒）：

| 测试 | 数量 | 范围 |
| --- | ---: | --- |
| `TimerWorkflowTest` | 1 | 时间设置、暂停/继续、取消及界面状态 |
| `LandscapeMotionTest` | 3 | 山景动画与切页回归 |
| `TimerExecutionTest` | 2 | 持久化恢复、旧回调失效与复健关闭规则 |
| `TimerPlatformTest` | 1 | 真实精确回调、响铃服务/通知与答题关闭 |
| `EditorWorkflowTest` | 1 | 编辑流程回归 |

中途 `TimerWorkflowTest` 曾因 `UiAutomation` 未可靠保持竖屏而取不到字段；测试 fixture 改用 `ROTATION_FREEZE_0 / 90` 并等待配置变化后，以上 8 项全部通过，生产逻辑未因此修改。

[深色横屏检查](../timer-art-landscape-dark.log) 通过 1 次（15 秒），实际截图为 2424 × 1080；[浅色竖屏检查](../timer-art-portrait-light.log) 通过 1 次（14.789 秒），实际截图为 1080 × 2424。两次均包含四页顺序截图，属于布局检查重复运行，不计为额外不同业务用例。

| 页面 | 深色横屏 | 浅色竖屏 |
| --- | --- | --- |
| 闹钟 | [实屏](screenshots/ui-timer-art/landscape-page-0.png) | [实屏](screenshots/ui-timer-art/portrait-light-page-0.png) |
| 专注 | [实屏](screenshots/ui-timer-art/landscape-page-1.png) | [实屏](screenshots/ui-timer-art/portrait-light-page-1.png) |
| 计时器 | [实屏](screenshots/ui-timer-art/landscape-page-2.png) | [实屏](screenshots/ui-timer-art/portrait-light-page-2.png) |
| 设置 | [实屏](screenshots/ui-timer-art/landscape-page-3.png) | [实屏](screenshots/ui-timer-art/portrait-light-page-3.png) |

横屏计时器另保留 [修复前](screenshots/ui-timer-art/landscape-before.png)、[修复后](screenshots/ui-timer-art/landscape-after.png) 与 [滚动控制区](screenshots/ui-timer-art/landscape-controls.png)。新版 HTML 已在浏览器实际预览横竖屏和深浅色，全部图像加载成功，无控制台错误或警告；计时、暂停、到时复健及输入正确答案 `73` 关闭的流程通过。

此前功能轮次的 21 项单元测试、10 项不同仪器测试和 320 dp / 2 倍字体验证仍属于历史功能版，不移作本轮新山景的证据。

## 交付与安装

[最终 Release APK](../artifacts/daybreak-0.1.0-timer-landscape.apk) 为 15,232,365 字节，约 14.53 MiB；SHA-256：`112058AB657870B5CD10A8ED5CFFBEC976BBB9B52394E3CFF84E6611F9965777`。[签名日志](../timer-art-signature.log) 显示 `Verifies`，v2 签名通过，v3 为 false；采用现有开发机 Android Debug 证书。

2026-10-09 17:02:57，Galaxy S25+（`R5CY22T6SDB`）执行 `adb install -r`，结果为 [Success](../timer-art-phone-install.log)。手机 `base.apk` 的 SHA-256 与上述交付包完全一致。安装包 `versionName=0.1.0`、`versionCode=1`、`minSdk=29`、`targetSdk=37`。

## 验收边界

本轮随后完成 Galaxy S25+ 的真实输入、暂停/恢复、横屏四页、后台到时、复健答题关闭及取消流程；用户实听确认“铃声和振动都正常”。详见 [实机验收记录](TIMER_PHONE_ACCEPTANCE.md)，其证据与上述模拟器/浏览器测试分别记录。锁屏、Doze、长待机、真实重启及首次解锁前行为尚未测试。
