# 共享执行核心

[ExecutionCore](ExecutionCore.kt) 为闹钟、计时器和专注提供共同的命令顺序、时间与系统唤醒接口。
它不保存业务状态，也不直接响铃或拦截应用；这些职责留在各自的引擎和输出控制器。

## 接口与接线

| 类型 | 职责 |
| --- | --- |
| `ExecutionTime / ExecutionClock` | 一次采样墙上时间、单调时间、启动代号和时区 |
| `WakeKey / WakePurpose` | 用用途与 ID 标识一个系统触发，隔离闹钟与专注边界 |
| `ScheduledWake` | 表达触发键与墙上截止时间 |
| `ExecutionScheduler` | 查询精确能力、登记与取消系统触发 |
| `ScheduleResult` | 明确报告失败及实际是否精确；降级成功仍是已登记 |
| `RecoveryReason` | 统一描述进程、界面、开机、解锁、升级、授权和时间变化 |

[ClockApplication](../../ClockApplication.kt) 只创建一个 core 及一个 Android 调度适配器，注入 AlarmEngine 与 FocusEngine。
[AndroidExecutionClock](../../platform/execution/AndroidExecutionClock.kt) 提供系统时间；[AndroidExecutionScheduler](../../platform/execution/AndroidExecutionScheduler.kt) 决定 Android 触发方式。
生产与测试通过相同接口驱动同一个核心，测试可替换时钟、调度和执行 dispatcher。

## 命令与登记不变量

- `execute` 在 IO dispatcher 的同一 Mutex 内串行运行，跨引擎共享命令顺序。
- 每条执行命令进入锁后只采样一次时间，事务与边界计算使用同一个 ExecutionTime。
- 展示查询可以单独读取当前时间；单次采样约束针对执行命令，不缓存整个进程的时间。
- 调度、取消与 delivery invalidation 在执行命令内使用；业务先持久化，再登记外部触发。
- 成功登记按 WakeKey 和时间缓存，普通刷新相同截止时间不重复调用系统。
- 失败结果不缓存，下一次检查可重试；`exact=false` 表示已降级登记，不等于失败。
- `delivered` 删除本地登记缓存，使已消费的入口可以重新登记。
- `cancel` 同时取消系统入口与缓存；不同用途不会互相取消。
- 恢复使用 `force=true` 重新登记；本地缓存不能证明系统仍持有 PendingIntent。

## Android 适配与边界

| 用途 | 触发方式 |
| --- | --- |
| ALARM | setAlarmClock → 显式、不可变的前台服务 PendingIntent，发生时验证 occurrence |
| FOCUS_BOUNDARY | 真正的下一边界 → setExactAndAllowWhileIdle 广播；精确能力不可用时降级 |

迁移只在新入口成功登记后移除旧 PendingIntent；取消覆盖新旧形式。
专注不使用伪 alarm clock 保活，服务活跃时的检查也不反复登记相同边界。
核心缓存仅是进程内优化；业务事实分别保存在闹钟执行日志与专注事务存储。
force-stop 可能取消全部入口并停止包；本核心不能在 stopped 期间自行恢复。
