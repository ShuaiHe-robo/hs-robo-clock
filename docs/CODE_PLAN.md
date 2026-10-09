# 安卓闹钟与定时应用锁代码计划

日期：2026-10-08　版本：v0.2 讨论稿

## 1. 已确认需求与首版范围

开始规划时目录为空，尚未创建 Android 工程。用户已确认：主测试手机为三星 Galaxy S25+；第一版自用，安装 APK；应用锁允许经过等待和二次确认后应急解除；铃声文件可选，支持选择手机里的 MP3，未选文件使用默认铃声。手机的实际 Android / One UI 版本尚未记录。

首版做本地离线应用，包含闹钟增删查改与开关、每个闹钟独立的“复健开关”（答对一道整数题才能关闭）、定时限制指定应用使用。以下细节都是建议默认值，可继续讨论：

- 闹钟支持一次性、按星期重复、标签、铃声和振动；算术题默认两位数加减，整数且结果非负；首版默认没有贪睡。
- 应用锁支持开始/结束本地时刻、星期、多个目标应用和跨午夜窗口；重叠规则取受限应用并集。
- 应急等待默认 60 秒，再确认一次；只记录本地应急解除记录。

首版不加入账户、云同步、广告、社交和使用时长分析。更难题型等增强功能留作后续迭代。

## 2. 技术与代码组织

建议 Kotlin、Jetpack Compose / Material 3、ViewModel、StateFlow、Coroutines。Room 保存规则和执行会话，DataStore 保存偏好。暂定最低 Android 10 / API 29；compileSdk、targetSdk、Gradle 插件和依赖版本在建工程时，结合实机系统和当时稳定工具链确定。

采用单一 `:app` 模块，按职责分包；必要处手动注入依赖，不为每个类添加接口。UI 展示状态、提交操作，调度、答案验证和拦截逻辑不放进 Activity / ViewModel。参考 [Android 架构建议](https://developer.android.com/topic/architecture/recommendations)。

| 包 | 主要职责与候选类 |
| --- | --- |
| `feature/alarm` | 列表、编辑页、`AlarmViewModel`，展示下一次触发和权限状态 |
| `feature/challenge` | `MathChallengeGenerator`、`AnswerValidator`，生成和验证题目 |
| `feature/focus` | 规则编辑、应用选择、应急解除界面 |
| `platform/alarm` | `AndroidAlarmScheduler`、`AlarmReceiver`、`AlarmRingingService`、`AlarmActivity`、`ScheduleRecoveryReceiver` |
| `platform/focus` | `FocusAccessibilityService`、`BlockingOverlayController`，处理系统事件和覆盖页 |
| `data` | Room 实体、DAO、Repository、偏好与恢复快照存储 |
| `domain` | 下一次闹钟、跨午夜窗口、有效会话并集等纯逻辑 |

## 3. 数据模型与状态规则

- `Alarm`：`id`、本地时分、重复星期、`enabled`、`label`、可空的 `ringtoneUri`（空表示默认铃声，不表示静音）、`ringtoneSource`（默认/系统/文件）、`ringtoneDisplayName`、`vibrate`、`mathUnlockEnabled`、`difficulty`、`revision`。
- `AlarmOccurrence / RingingSession`：区分规则和本次执行，保存独立 `occurrenceId`、`scheduledAt`、规则版本/配置快照（含铃声 URI、来源和名称）、题目和状态。题目生成后持久化，旋转或重新进入页面不能换题，已有会话也不受之后改铃声影响。状态可为待触发、响铃中、已结束、已错过。
- `FocusRule`：`id`、本地开始/结束时刻、星期、目标包名、`enabled`。跨午夜窗口的星期按开始日解释；开始等于结束时首版拒绝保存，避免隐含全天锁定。
- `FocusSession`：一次规则窗口的唯一标识、开始/结束时间、规则与目标应用快照、`ACTIVE / RELEASE_PENDING / EMERGENCY_RELEASED / EXPIRED`、应急计时状态。

规则编辑影响未来执行。`revision` 用来排除旧调度回调，已经触发的会话以持久化快照为准。关闭或删除规则不能结束正在响铃或受保护的锁定会话；删除源闹钟也不能级联删除正在响铃的 occurrence。

先保存配置和待调度状态，再执行系统调度，成功后记录结果。Room 事务不能包含 AlarmManager 调用；失败必须显示“未排程”及原因，并允许重建。恢复过程按唯一会话标识去重，不盲目重复创建。

## 4. 闹钟触发、铃声与算术关闭

触发链为：计算下一次本地时间 → `AlarmManager.setAlarmClock` 安排单次触发 → Receiver 校验 occurrence、版本和状态并去重 → 启动 `mediaPlayback` 前台响铃服务并及时发布前台通知 → 通知的 full-screen intent 打开锁屏响铃页。

系统可能只展示横幅，因此通知必须保留点击进入入口。音频使用 `USAGE_ALARM`，处理振动、音频焦点及来电中断恢复。服务持有响铃会话，退出页面、按返回键或划走最近任务均不代表关闭。重复闹钟逐次计算下一次，不用固定 24 小时或不精确重复任务。[闹钟调度](https://developer.android.com/develop/background-work/services/alarms)、[音频焦点](https://developer.android.com/media/optimize/audio-focus)。

编辑闹钟提供“默认铃声 / 系统铃声 / 选择音频文件”，显示选中文件名称，支持试听/停止及恢复默认。文件选择是可选操作；取消系统选择器保持原选择，不能被当成静音。系统铃声使用 `RingtoneManager.TYPE_ALARM` 选择。[RingtoneManager](https://developer.android.com/reference/android/media/RingtoneManager)。

文件选择使用系统 `ACTION_OPEN_DOCUMENT`（Compose 可用 `ActivityResultContracts.OpenDocument`），按 `audio/*` 筛选并验证实际可解码，支持 MP3。读取 `content URI`，调用 `takePersistableUriPermission` 保存读取授权，持久化 URI 和展示名，不依赖绝对文件路径。只申请所选文件的读取授权，无需扫描全盘或为此申请 `READ_MEDIA_AUDIO` / 广泛存储权限。读取授权无法持久化或文件验证失败时，不替换原选择并显示重新选择/使用默认选项，不能宣称新文件已经有效保存。[系统文件选择与授权](https://developer.android.com/training/data-storage/shared/documents-files)。

真正响铃时仍要处理文件被删除/移动、授权撤销、解码失败，以及外部/云文件提供器不能及时读取的情况。读取与准备应有超时，失败回退系统默认闹钟铃声，若默认不可用再用应用内置备用音，不能因自选文件失败而静默漏响。首次解锁前不依赖尚不能读取的用户文件，使用可用的系统铃声或内置备用音，并遵循第 6 节的 Direct Boot 恢复设计。

关闭路径统一交给服务/业务层：复健开关关闭则直接结束；开启则根据会话 ID 验证答案，正确才结束。通知按钮、返回路径和规则编辑不能跳过验证，不能仅靠 UI 布尔值放行。答错保留原题并继续响铃。多个闹钟同时到来，建议持久化独立会话、串行处理；完成一个才能处理下一个，避免相互覆盖，同一 occurrence 只处理一次。

## 5. 定时应用锁与应急解除

应用锁采用以下初步方案，需先通过 S25+ 真机原型验收：用户主动开启 `AccessibilityService`，监听必要窗口/应用切换事件，以包名和有效会话判断限制，使用 `TYPE_ACCESSIBILITY_OVERLAY` 展示拦截页及剩余时间。尽量不读取窗口文本或节点内容，不轮询 UsageStats。[服务 API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)、[覆盖层类型](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo#TYPE_ACCESSIBILITY_OVERLAY)。

窗口起止安排单次边界状态刷新，并结合服务内计时和系统事件重新计算；开始时目标应用已在前台也应出现覆盖，到期或切入允许应用及时移除。服务重连、重启恢复时重新计算；过滤自身覆盖产生的事件，避免循环。边界刷新不播放闹钟、不发送全屏通知。

应用选择只列举可启动应用，使用限定 `queries`，不默认申请 `QUERY_ALL_PACKAGES`。本应用、系统权限入口、桌面、电话和紧急呼叫不进入锁定目标，电话功能始终可用。三星分屏、弹窗、画中画需专门验证，未经验证的路径不能宣称已经覆盖。[限定包可见性](https://developer.android.com/training/package-visibility/declaring)。

应急流程建议：发起时绑定当时所有生效 FocusSession 的 ID → 进入 `RELEASE_PENDING` 并等待 60 秒 → 二次确认 → 将这些仍生效的会话标为 `EMERGENCY_RELEASED`。等待时继续拦截，取消则回到 `ACTIVE`；自然到期的会话转为 `EXPIRED`。新开始的窗口不在本次解除范围，后续重复计划继续生效。

等待保存真实时间戳和同一次开机内的单调时钟基准，页面重建或应用进程重建不能跳过等待；建议设备重启后重新完整等待，界面说明该行为。倒计时由业务状态计算，不由页面递减数字决定。

## 6. 权限、恢复与平台边界

首版权限建议如下，依系统版本检测和请求：

- `SCHEDULE_EXACT_ALARM`：受影响版本的特殊授权，排程前检查；`POST_NOTIFICATIONS`：API 33+；`USE_FULL_SCREEN_INTENT`：包含 Android 14+ 的能力检测和设置入口。
- 前台服务通用权限、`mediaPlayback` 类型及适用版本对应权限；振动、必要的短时 wake lock、`RECEIVE_BOOT_COMPLETED`。
- 无障碍服务声明及用户人工启用；APK 在部分系统上可能需要人工允许“受限设置”，指引按实际 OS 设计。

权限拒绝或撤销后，页面显示实际能力及无法排程状态，不能虚假显示“正常启用”。BOOT、时间/时区变更、升级、授权恢复后重建待执行计划，已开始会话仍遵守其快照规则。[全屏权限](https://developer.android.com/about/versions/14/behavior-changes-14)、[前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[后台启动条件](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[受限设置](https://support.google.com/android/answer/12623953)。

首次解锁前响铃必须完整支持 Direct Boot：相关 receiver、service、activity 声明 `directBootAware`，调度及题目读取路径使用 device-protected 最小快照；不能依赖尚不可读取的用户音频文件，使用解锁前可用的系统铃声或内置备用音。Room 是解锁后的主存储；快照写入、删除、版本及解锁后合并要有一致性恢复设计，不能只标记恢复 receiver 就承诺可响铃。[Direct Boot](https://developer.android.com/privacy-and-security/direct-boot)。

BOOT 广播只恢复排程，不直接启动 mediaPlayback 服务，遵守 Android 15+ 的 BOOT 限制；响铃仍由真实闹钟触发。补响建议只覆盖重启后检查发现、已错过不超过 10 分钟的闹钟，更久则记为 missed；阈值待讨论。三星休眠、深度休眠和后台限制纳入实测。

产品边界：普通非 root 应用是在检测目标启动后拦截使用，无法保证系统级永久禁开。强停、卸载、撤销权限、修改系统时间等可以影响功能，不尝试锁设置或阻止卸载。算术要求约束本应用正常关闭路径，不承诺抵抗系统终止、调低音量或关机。全屏通知仅用于真正的闹钟。

## 7. 开发阶段与退出标准

| 阶段 | 交付 | 退出标准 |
| --- | --- | --- |
| P0 最小工程 | Compose 空壳、构建说明，记录实机 Android / One UI 与工具链 | APK 在 S25+ 安装运行；版本选择可复现 |
| P1 风险原型 | 最小响铃链和无障碍覆盖原型 | 熄屏/锁屏/待机下精确响铃、声音与全屏降级可用；拦截起止及分屏/画中画行为有实测结论，确认方案后进入完整 UI |
| P2 基础闹钟 | CRUD、开关、Room、下一次调度；默认/系统/自选 MP3 铃声及试听 | 重进应用数据保留，铃声选择保存恢复、试听/停止正常；关闭/修改/删除取消旧触发；排程失败可见 |
| P3 算术解锁 | 持久化题目、服务验证、会话队列 | 答错继续响；正常关闭入口均受验证；页面重建不换题，重复投递不重复响 |
| P4 定时应用锁 | 完整规则、应用选择、跨午夜/并集、应急流程 | 起止边界生效；等待无法由重建跳过；本次应急不破坏未来计划 |
| P5 恢复与回归 | 权限状态、Direct Boot/重启恢复、使用说明和可安装 APK | 必要测试通过；三星实际限制和已验证范围写入说明 |

P1 的临时原型在 P2 后替换为完整持久化实现，不保留两套运行机制。先验证高风险平台行为，再完善 UI。

## 8. 必要验证

- 单元测试：下一次闹钟覆盖当天已过、星期和时区/DST；跨午夜窗口、重叠并集；题目整数与正确性、业务层拒绝错误答案；应急计时和状态转换。
- 集成验证：旧 PendingIntent 回调不响、删除取消、重复投递去重；数据恢复和快照一致性；规则删除不抹掉当前会话。
- 铃声验证：未选文件时使用默认铃声；取消选择保持原配置；试听/停止；重进应用及重启后仍能凭持久化授权读取；授权持久化失败明确提示；文件删除、不可读、解码失败或读取超时时回退；Direct Boot 下备用铃声能够响铃。
- S25+ 真机：熄屏、Doze、划走最近任务；重启后未首次解锁；通知/全屏权限拒绝；音量、静音、勿扰及来电后恢复；无障碍撤销/重连；锁开始时已经在目标应用、到期移除覆盖；从桌面、最近任务、通知进入目标；分屏、弹窗、画中画；应急结束后未来窗口仍生效。

系统强停属于平台边界，不把“被强停后仍保证运行”设为退出标准。当前文档是计划，没有实现代码或真机测试结果。

## 9. 下一轮待讨论

1. S25+ 的实际 Android / One UI 版本。
2. 默认一道两位数加减题是否合适。
3. 应急等待默认 60 秒是否合适。
4. 最低 API 29 和补响 10 分钟阈值是否采用。

## 10. 实施状态（2026-10-08）

本节补充当前进度；上文“尚未创建工程”“系统版本尚未记录”“没有实现代码”描述讨论稿初始状态，原需求和阶段退出标准保留。

已创建单模块 Kotlin / Compose Material 3 工程，代码包含闹钟 CRUD、算术关闭、定时应用锁、铃声选择与备用播放、设备保护执行日志及解锁后 Room 合并。首版采用最低 API 29、两位数加减题、无贪睡、60 秒应急等待和 10 分钟补响阈值，仍可根据实测调整。

主设备已确认 Galaxy S25+（SM-S9360），Android 16 / API 36，One UI 8.5（80500），固件 S9360ZCSDCZH1，USB 调试连接已确认。工具链和构建命令见 [README](../README.md)；具体结果见 [设备验收](DEVICE_ACCEPTANCE.md)。

界面初稿借鉴 `design-taste-frontend`（设计 5、动效 3、密度 3），现按用户指定的 `gpt-taste` 复核按钮可读性、标题层级与留白；原生 Compose Material 3 保留闹钟与专注任务流程，该 skill 的网页 AIDA/GSAP 结构未作为 Android 实现要求。

最终构建包含 Debug、测试 APK、9 项单元测试、Lint 和 Release，1 分 18 秒完成；单元测试 0 失败/错误/跳过，Lint 为 0 errors / 20 warnings。S25+ 已安装启动并成功覆盖更新最终 Release，保存深色主界面截图；用户在图书馆，真机仅做静默界面验证，未新增授权、未试听/响铃/振动。模拟器浅色主界面已目视检查，横屏和大字号未测。

Debug APK 在 16 KB Pixel_10 无声模拟器修正后完整 4 项回归通过（15.419 秒、0 跳过），此前失败轮次保留。模拟器实际重启后，在 `RUNNING_LOCKED` 首次解锁前系统触发闹钟，显示固定题目，拒绝错答并允许正确答题结束服务；解锁后独立验证 1 项通过（0.264 秒），确认同题答案、ENDED 日志合并及一次性闹钟禁用。测试规则和模拟器 PIN 已清理，实际声音/振动未测。最终 Release 另外完成 16 KB 模拟器安装与 MainActivity 启动检查（Status ok，1.071 秒，显示朝醒，当次 crash buffer 空），验证范围为主界面启动。

最终 [自用 APK](../artifacts/daybreak-0.1.0.apk) 为 2,984,978 字节（约 2.85 MiB），SHA-256 为 `FFCAA04E574782F3569195EA9BF8B23B72B49F2A242B4BB08D35B7C35B4A305B`，apksigner v2 及 `zipalign -c -P 16 4` 通过。Release 启用 R8/资源收缩和本机 debug 密钥签名，方便自用同签名覆盖更新；不是商店签名。依赖为 DataStore 1.2.1、Graphics Path 1.1.0；后者 RELRO 仍不合格，16 KB 模拟器通过结果依赖 Android 官方页大小兼容模式，不能称全部原生对齐通过。

P0 工程交付通过。P1 的平台风险与 P5 的恢复回归退出标准未通过：S25+ 锁屏/Doze、声音/振动、Direct Boot、三星多窗口与后台限制仍需真机证据，模拟器或界面截图不替代实机验收。原阶段退出标准保留，未宣称整个计划验收完成。
