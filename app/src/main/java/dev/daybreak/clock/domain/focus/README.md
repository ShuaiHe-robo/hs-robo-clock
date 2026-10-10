# 专注执行与输出模块

[FocusEngine](FocusEngine.kt) 决定哪个冻结窗口仍有效、哪些应用受限，以及应急解除的范围。
它和 [FocusController](FocusController.kt) 不调用 Android API；生产与测试使用同一组接口。

## 接口与存储

| 类型 | 职责 |
| --- | --- |
| `FocusExecutionStore / FocusRecords` | 在同一持久事务内读取和修改规则、窗口及解除状态 |
| `FocusTargetPolicy` | 校验受保护应用，避免把必要系统入口加入拦截 |
| `FocusBoundary` | 发布下一真实边界，以及精确 / 降级 / 失败结果 |
| `FocusPresentation` | 发布当前目标应用、截止时间与应急等待状态 |
| `FocusForeground / FocusOutput` | 提供当前应用并渲染或移除覆盖；平台实现这些接口 |

接口见 [FocusExecution.kt](FocusExecution.kt)；[AndroidFocusExecutionStore](../../platform/focus/AndroidFocusExecutionStore.kt) 使用 Room 事务。
AlarmEngine 与 FocusEngine 共用 [ExecutionCore](../execution/README.md) 及同一个 AndroidExecutionScheduler，不合并业务状态。
每条命令在共享 IO 串行区采样一次时间；事务提交成功后才发布会话与登记边界。
专注存储需要解锁，开机首次解锁前不访问 Room；它不继承闹钟的 Direct Boot 能力。

## 窗口与解除不变量

- 规则当前窗口以规则 ID 和开始时间标识，重复刷新不会生成第二份相同窗口。
- 活动窗口冻结开始、结束、目标应用与标签；编辑、禁用或删除源规则不会提前解除它。
- 延长规则保留原冻结段，原段到期后生成新的延长快照，使用届时规则的目标集合。
- 查询延长历史包括终态，已解除的延长段不能被过期父窗口重新复活。
- 状态允许 ACTIVE → RELEASE_PENDING / EXPIRED；等待中可取消、到期或确认解除，终态不能恢复为 ACTIVE。
- 发起解除时 token 只绑定当时已有窗口；后来开始或延长的新窗口不被该 token 解除。
- 60 秒等待使用单调时间与启动代号；修改墙上时间不能缩短等待，跨重启重新等待 60 秒。
- 事务失败不发布半完成计划或解除状态；系统排程失败保留持久业务事实，后续检查可重试。
- 边界投递清除共享缓存后重算；进程 / 界面恢复强制重新登记相同截止时间。

## 控制器与 Android 适配

FocusController 统一处理前台选择、窗口事件、会话变化与每秒检查，不把这些流程写进平台服务。
刷新或输出异常分别报告检查失败 / 覆盖不可用；下一轮继续重试，关闭控制器时撤销输出。
[FocusAccessibilityService](../../platform/focus/FocusAccessibilityService.kt) 仅适配窗口包名、无障碍覆盖层和服务健康状态。
平台读取窗口根包名，排除输入法与自己的覆盖层；不遍历节点或读取界面文字。
专注真实边界使用广播精确唤醒，可降级；覆盖仍依赖无障碍服务实际连接和允许的后台状态。
每秒检查发生在已连接的服务中，不是伪闹钟保活；系统 force-stop 或权限撤销后不能保证继续拦截。
