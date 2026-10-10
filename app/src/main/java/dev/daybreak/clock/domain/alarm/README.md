# 闹钟与计时器执行模块

[AlarmEngine](AlarmEngine.kt) 决定哪次执行应发生、何时发生、如何结束；[RingingController](RingingController.kt) 选择响铃输出。
两个模块不调用 Android API，不要求待机进程常驻，也不包含专注业务状态。

## 接口与共享核心

| 接口 / 模块 | 职责 |
| --- | --- |
| `AlarmExecutionStore` | 提供计划、执行快照、计时器和可观察状态；保存执行事实 |
| `ExecutionCore` | 与 FocusEngine 共用命令顺序、时间、调度及用途隔离 |
| `ExecutionClock / ExecutionScheduler` | 由共享核心注入，生产与测试均可替换 |
| `AlarmEvents` | 记录排程、到点、恢复与状态变化 |
| `RingingOutput` | 将选中的快照转换为通知、音频和振动，或结束运行 |

业务接口见 [AlarmExecution.kt](AlarmExecution.kt)，时间与调度接口见 [共享执行核心](../execution/README.md)。
[ClockApplication](../../ClockApplication.kt) 为 AlarmEngine 与 FocusEngine 注入同一个 core 和 AndroidExecutionScheduler 实例。
[AndroidAlarmExecutionStore](../../platform/alarm/AndroidAlarmExecutionStore.kt) 封装 Direct Boot 日志和解锁后的 Room 投影。
[AndroidExecutionScheduler](../../platform/execution/AndroidExecutionScheduler.kt) 对 ALARM 使用 setAlarmClock → 显式前台服务 PendingIntent。

## 执行不变量

- 每次到点对应唯一 occurrence；重复恢复复用有效快照，重复投递不会重复创建响铃。
- revision 高于该计划当前版本及历史 occurrence 版本；删除后重建同 ID 也不能回退代际。
- 到点核对计划启用状态和代际；编辑、禁用、删除取消旧的待执行 occurrence。
- 状态仅允许 PENDING → RINGING / CANCELLED / MISSED、RINGING → ENDED 及同状态重入；终态不能复活。
- 执行快照先持久化，再取消触发或发布可见副作用；排程请求及失败同样留存快照。
- 已开始响铃使用冻结的铃声、振动和数学题快照；随后编辑或删除计划不会跳过关闭要求。
- 闹钟到点和恢复共用 10 分钟迟到规则：超期记为 MISSED，单次禁用，重复计划推进至下次。
- 计时器同次启动使用单调时间，重启后按墙上截止时间恢复；暂停 / 继续保留余量并创建新入口。

每条命令在共享 core 的 IO 锁内只采样一次时间；WakeKey.alarm(occurrence ID) 与专注边界互不取消。
[BootStore](../../data/BootStore.kt) 的设备保护日志是 occurrence / timer 执行事实权威；AtomicFile 写入成功后再发布状态。
Room 是解锁后的历史投影；历史写入失败不能撤回持久执行事实或阻止响铃。
所有恢复入口进入统一 recover；恢复强制登记，开机只重排，到点才启动响铃服务。

## 响铃运行与边界

RingingController 按存储顺序选择首个 RINGING，会话结束后交接下一项；重复投递不重播同一会话。
它跟踪处理中投递，避免状态暂为空时提前结束；无响铃且无处理中投递才调用 finish。
[AlarmRingingService](../../platform/alarm/AlarmRingingService.kt) 适配前台通知、音频、振动和有界 wake lock，销毁时清理。
服务可以恢复持久化的响铃；待机由 AlarmManager 持有计划，不运行全天保活服务。
平台 force-stop 可取消入口并禁止包启动；用户解除 stopped 后才能恢复，模块不能在强停期间自愈。
