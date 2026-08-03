# 意图 AI Data Pack 格式

## 资源位置与重载

- 意图：`data/<namespace>/maid_ai/intents/<id>.json`
- 计划：`data/<namespace>/maid_ai/plans/<id>.json`
- 能力：`data/<namespace>/maid_ai/abilities/<id>.json`
- 文件路径决定定义 ID。例如
  `data/example/maid_ai/intents/rest/near_owner.json` 对应
  `example:rest/near_owner`。
- 意图与能力当前使用 `format_version: 1`；计划支持版本 1 和带恢复元数据的版本 2。
  `format_version` 始终必填。
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
  "format_version": 2,
  "initial_state": "approach",
  "resume_policy": "restart_step",
  "checkpoints": ["approach"],
  "maximum_suspend_ticks": 600,
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

版本 2 的 `resume_policy` 可选值为 `never_resume`、`restart_step`、
`resume_checkpoint`、`replan_suffix`、`atomic`。暂停会先 quiesce 当前动作并释放临时移动
目标、监听器和 Claim；恢复前重新检查目录代际、持续 Guard、动作前置条件及
`maximum_suspend_ticks`。`checkpoints` 只能引用现有状态。版本 1 等价于
`never_resume`，保持旧数据的取消后不恢复语义。

编译器会拒绝：

- 未注册动作或未知参数；
- 参数类型不匹配；
- 不存在的转移目标；
- 不可达状态；
- 没有任何终点路径的状态环；
- 超出意图、计划、状态、条件和 Utility 数量上限的目录。

## 数据驱动能力

能力 Grant 只代表女仆获得授权，不会直接运行。主人命令或自主需求必须生成带 TTL 的
`ActivationRequest`，模板编译器再为两种来源各生成一个 Intent，并让它们共享同一 Plan：

```json
{
  "format_version": 1,
  "template": "world_item_deploy",
  "action": "tlm_companionship:action/deploy_boat",
  "parameters": {},
  "timeout_ticks": 40,
  "request_ttl_ticks": 100,
  "cooldown_ticks": 200,
  "command_score": 900.0,
  "autonomous_score": 120.0,
  "interrupt_priority": 500
}
```

当前只开放 `world_item_deploy` 模板。未知模板、未知动作、参数 Schema 不匹配或模板产物
无法与主目录共同编译时，本次整批 reload 被拒绝，Intent 与 Ability 两个目录都保留上一代。
Grant 通过 TLM TaskData 保存；请求、冷却与执行态只存在于有界运行时。

内置 `deploy_boat` 优先复用主人或女仆附近的空船，否则寻找已加载水面并取得共享 Request
与放置点 Claim。生成实体是提交点，成功加入世界后才消耗一件带指纹复验的船物品。
自主渡水需求按主人 UUID 与维度竞标，只有唯一 assignment 获胜者能提交请求；主人命令仍
只针对明确选择的女仆。

## 内置事实与动作

事实注册位于 `CompanionIntentIds`，并声明 `BOOLEAN`、`NUMBER` 或 `SIGNAL` 类型。首阶段
提供：

- 主人有效性/距离；
- 好感度、饥饿、附近 TLM 零食柜是否存在合法柜内餐食；
- 跟随/Home、命令坐下/坐姿、睡眠、拴绳、载具/被动座椅；
- 可移动、攻击目标、Panic、合并战斗状态、工作目标、物品使用、内置/第三方任务分类；
- 移动租约是否活跃、租约优先级、fail-open、硬阻塞、工作目标释放年龄；
- `behavior_occupancy_level`（`0=IDLE`/`1=SOFT`/`2=HARD`）与 `behavior_occupancy_reason`；
  旧 `movement_hard_blocked` 由 `HARD` 派生，仍可兼容旧 Guard；
- 注视召回、缺食反馈求食、任务后归队、随机游走归队信号。

占用 Guard 约定：

- 被动归队/饥饿/零食柜等默认 `behavior_occupancy_level eq 0`。
- 注视召回使用 `lte 1`，允许干净抢占 `SOFT`。
- Ability 模板编译出的命令/自主意图默认也要求 `eq 0`；未实现软抢占生命周期时不得放宽。

动作同样由 `CompanionIntentIds` 注册 `ActionSchema`。首阶段只有：

- `tlm_companionship:action/approach_owner`
  - `speed`: NUMBER，可选
  - `close_distance`: INTEGER，可选
  - `authority`: STRING，可选；`owner_command` 启用软抢占与 override 续租，
    缺省为被动 `COMPANION`
- `tlm_companionship:action/fetch_snack_cabinet_meal`
  - `speed`: NUMBER，可选
  - `close_distance`: INTEGER，可选
  - 前往事实采集阶段缓存的 TLM 零食柜，到达后在服务端原子取出一份通过 Work Meal
    校验的食物并启动本体进食；取消时只清理该动作拥有的步行目标
- `tlm_companionship:action/companion_command_window`
  - `duration_ticks`: INTEGER，必填；内置注视计划使用 60 tick
  - `speed`: NUMBER，必填
  - `close_distance`: INTEGER，必填
  - 窗口内持续跟随主人，优先共乘仍有容量的主人载具；满座时搜索 3 格内同类型且
    能接纳玩家的空位（TLM 凳子与娱乐座视为同一家族），或在主人离座后接替其释放
    实体。仅接受玩家类型的空位会在容量校验后兼容女仆。由该动作取得的座位/载具
    在计划结束后保持锁定，
    直到主人主动下座或战斗危险释放；锁定的 TLM 被动座位另允许在非 Home 模式下
    达到本体紧急跟随距离时解除并传送
- `tlm_companionship:action/request_hunger_attention`
  - 无参数
  - `hungry_feedback`、`hungry_standard` 与 `hungry_high_trust` 只定义不同的触发条件、
    评分和概率，统一引用 `request_food` 计划；计划抵达主人后必须经过该动作
- `tlm_companionship:action/deploy_boat`
  - `ability_id`: STRING，必填
  - 只接受仍有效且已授权的能力请求；自主请求还必须保有 owner coordination assignment

## 扩展步骤

1. 在 behavior 模块的 `CompanionIntentIds` 增加事实或动作 ID，并为事实声明类型、为动作
   声明参数 Schema。
2. 在 `TlmMaidFactReader` 提供事实编码；短期边沿事件通过 `MaidIntentApi.signal` 提交，
   不把副作用写进事实采集器。信号条件只负责激活门控，成功选中后即消费；运行中的计划
   不再受已消费信号阻断，但其他持续事实条件仍会每 tick 执行硬中断检查。
3. 在 `TlmMaidIntentActions` 注册动作分派，并在对应 action family 实现副作用；动作取消时
   只清理自己拥有的目标、监听器与 Claim。
4. 添加 Data Pack 定义和纯 JVM 编译验证，再补充真实 Brain 下的 GameTest。
5. 使用 `/tlmcompanionship ai stats` 检查目录代际和调度计数，使用
   `/tlmcompanionship ai explain <女仆>` 查看当前状态、候选分数与阻塞条件。

稳定编排模块不得 import Minecraft/TLM 类型。新增游戏事实和副作用必须留在 adapter，
不能通过 Data Pack 引入脚本执行能力。
