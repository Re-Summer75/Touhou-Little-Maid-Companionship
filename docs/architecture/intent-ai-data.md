# 意图 AI Data Pack 格式

## 资源位置与重载

- 意图：`data/<namespace>/maid_ai/intents/<id>.json`
- 计划：`data/<namespace>/maid_ai/plans/<id>.json`
- 文件路径决定定义 ID。例如
  `data/example/maid_ai/intents/rest/near_owner.json` 对应
  `example:rest/near_owner`。
- 当前唯一格式版本是 `format_version: 1`，该字段必填。
- `/reload` 在后台解析资源并在服务端线程一次性发布不可变目录。资源包按低到高优先级覆盖；
  上层单文件解析或语义校验失败时保留下层有效版本，最终目录编译失败时继续使用上一代目录。

数据只能引用已注册的事实、动作和计划 ID，不支持脚本、反射或任意表达式。

## 意图定义

```json
{
  "format_version": 1,
  "plan": "tlm_companionship:approach_owner",
  "conditions": [
    {
      "fact": "tlm_companionship:fact/owner_valid",
      "operator": "eq",
      "value": 1.0
    }
  ],
  "utility": [
    {
      "fact": "tlm_companionship:fact/owner_distance",
      "minimum": 2.0,
      "maximum": 16.0,
      "weight": 0.1,
      "curve": "linear"
    }
  ],
  "selection": {
    "base_score": 0.5,
    "minimum_score": 0.0,
    "activation_chance": 1.0,
    "evaluation_interval_ticks": 1,
    "minimum_commit_ticks": 20,
    "switch_margin": 0.1,
    "interrupt_priority": 30,
    "cooldown_ticks": 0
  }
}
```

### Guard

`conditions` 中的全部条件必须同时成立。支持的 `operator`：

- `lt`
- `lte`
- `eq`
- `neq`
- `gte`
- `gt`

布尔与信号事实只能和 `0` 或 `1` 比较。信号具有 TTL，意图成功激活或概率判定失败后会被
消费，因此同一次边沿事件不会重复抽取概率。

### Utility 与选择

每项 Utility 先把事实从 `[minimum, maximum]` 归一化到 `[0, 1]`，再应用曲线和权重。
支持 `linear`、`inverse_linear`、`step`。最终分数是 `base_score` 与各项贡献之和。

`interrupt_priority` 先区分硬中断等级；同等级候选再比较 Utility，完全相同时按意图 ID
字典序确定结果。`minimum_commit_ticks` 和 `switch_margin` 防止短时间反复切换。
`activation_chance` 只对最终胜出的新候选执行一次确定性抽样。

## 计划定义

```json
{
  "format_version": 1,
  "initial_state": "approach",
  "states": {
    "approach": {
      "action": "tlm_companionship:action/approach_owner",
      "parameters": {
        "speed": "0.55",
        "close_distance": "2"
      },
      "timeout_ticks": 200,
      "on_success": "request",
      "on_failure": "$failure",
      "on_cancel": "$failure"
    },
    "request": {
      "action": "tlm_companionship:action/request_hunger_attention",
      "timeout_ticks": 20,
      "on_success": "$success",
      "on_failure": "$failure"
    }
  }
}
```

每个状态每 tick 执行一次动作，动作返回 `RUNNING`、`SUCCEEDED`、`FAILED` 或 `CANCELLED`。
超时按失败转移。`on_success`、`on_failure`、`on_cancel` 可指向另一状态，或使用
`$success`/`$failure` 结束计划；`on_cancel` 省略时默认 `$failure`。

编译器会拒绝：

- 未注册动作或未知参数；
- 参数类型不匹配；
- 不存在的转移目标；
- 不可达状态；
- 没有任何终点路径的状态环；
- 超出意图、计划、状态、条件和 Utility 数量上限的目录。

## 内置事实与动作

事实注册位于 `CompanionIntentIds`，并声明 `BOOLEAN`、`NUMBER` 或 `SIGNAL` 类型。首阶段
提供：

- 主人有效性/距离；
- 好感度、饥饿；
- 跟随/Home、命令坐下/坐姿、睡眠、拴绳、载具/被动座椅；
- 可移动、攻击目标、Panic、合并战斗状态、工作目标、物品使用、内置/第三方任务分类；
- 移动租约是否活跃、租约优先级、fail-open、硬阻塞、工作目标释放年龄；
- 注视召回、任务后归队、随机游走归队信号。

动作同样由 `CompanionIntentIds` 注册 `ActionSchema`。首阶段只有：

- `tlm_companionship:action/approach_owner`
  - `speed`: NUMBER，可选
  - `close_distance`: INTEGER，可选
- `tlm_companionship:action/companion_command_window`
  - `duration_ticks`: INTEGER，必填；内置注视计划使用 60 tick
  - `speed`: NUMBER，必填
  - `close_distance`: INTEGER，必填
  - 窗口内持续跟随主人，优先共乘仍有容量的主人载具；单座的 TLM 凳子/娱乐座位
    则执行就近入座或主人离座补位。由该动作取得的座位/载具在计划结束后保持锁定，
    直到主人主动下座或战斗危险释放
- `tlm_companionship:action/request_hunger_attention`
  - 无参数

## 扩展步骤

1. 在 behavior 模块的 `CompanionIntentIds` 增加事实或动作 ID，并为事实声明类型、为动作
   声明参数 Schema。
2. 在 `TlmMaidIntentContext` 提供事实编码；短期边沿事件通过 `MaidIntentApi.signal` 提交，
   不把副作用写进事实采集器。信号条件只负责激活门控，成功选中后即消费；运行中的计划
   不再受已消费信号阻断，但其他持续事实条件仍会每 tick 执行硬中断检查。
3. 在 `TlmMaidIntentActions` 实现动作；该类是陪伴意图写 Brain、移动租约或状态动作的唯一
   出口。动作取消时只清理自己拥有的目标。
4. 添加 Data Pack 定义和纯 JVM 编译验证，再补充真实 Brain 下的 GameTest。
5. 使用 `/tlmcompanionship ai stats` 检查目录代际和调度计数，使用
   `/tlmcompanionship ai explain <女仆>` 查看当前状态、候选分数与阻塞条件。

稳定编排模块不得 import Minecraft/TLM 类型。新增游戏事实和副作用必须留在 adapter，
不能通过 Data Pack 引入脚本执行能力。
