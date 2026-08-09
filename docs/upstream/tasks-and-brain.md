# 工作模式与 Brain

源：`entity/task/`、`api/task/IMaidTask.java`

## 内置工作模式

`entity/task/` 下二十一个：

**战斗类** `TaskAttack`（近战）、`TaskBowAttack`、`TaskCrossBowAttack`、`TaskTridentAttack`、`TaskDanmakuAttack`（弹幕）

**农牧类** `TaskNormalFarm`、`TaskCocoa`、`TaskMelon`、`TaskSugarCane`、`TaskGrass`、`TaskShears`、`TaskMilk`、`TaskHoney`、`TaskFeedAnimal`

**杂务类** `TaskFishing`、`TaskTorch`、`TaskSnow`、`TaskExtinguishing`、`TaskFeedOwner`

**闲置类** `TaskIdle`、`TaskBoardGames`

注册与查找走 `TaskManager`。我们的自由模式（`FreedomMaidTask`）就是往这里注册一个新的 uid。

## `IMaidTask` 的钩子

这是**本体给工作模式开放的全部控制点**。要改女仆的哪一类行为，先看这里有没有现成开关——我们的隔离方案正是建立在其中几个之上。

| 钩子 | 默认 | 作用 |
| --- | --- | --- |
| `createBrainTasks(maid)` | 必须实现 | 这个模式往 Brain 里注册哪些行为 |
| `createRideBrainTasks(maid)` | 空 | 骑乘状态下的行为 |
| `isEnable(maid)` | true | 这个模式当前能不能选 |
| `isHidden(maid)` | false | 是否在 GUI 里隐藏 |
| `enableLookAndRandomWalk(maid)` | true | **是否注册本体的注视与随机游走** |
| `enablePanic(maid)` | true | **是否注册惊慌逃跑** |
| `enableEating(maid)` | true | **是否注册自动进食** |
| `workPointTask(maid)` | — | 是否属于"工作点"型任务 |
| `canSitInJoy(maid, joyType)` | — | 能否坐娱乐方块 |
| `getAmbientSound(maid)` | — | 环境音 |
| `getIcon()` / `getName()` / `getDescription()` | — | GUI 展示 |
| `getEnableConditionDesc` / `getConditionDescription` | 空 | GUI 里显示的启用条件说明 |
| `getTaskConfigGuiProvider` / `getTaskInfoGuiProvider` | 默认容器 | 该模式自己的配置界面 |

### 我们的用法

`FreedomMaidTask` 设 `enablePanic=false`、`enableLookAndRandomWalk=false`、`enableEating=true`、`workPointTask=false`。

- 关惊慌是必须的：`MaidPanicTask` 激活时每 tick 抹掉 `WALK_TARGET`，会把我们写的移动目标清掉。
- **进食刻意保留**，因为饥饿值是本体机制，其它模式都会自己吃，自由模式不吃会显得反常。

真正的隔离不在这些开关上，而在 `MaidBrain.registerBrainGoals` 的 HEAD 注入：自由模式整段跳过本体的行为注册，只保留一份白名单（游泳、攀爬、呼吸、开门、注视落点、移动落点、清睡眠）。见[架构文档](../architecture/README.md)。

## Brain

本体用 1.20 的 Brain 系统（`BehaviorControl` + 记忆模块 + 传感器），不是 Goal。几处对我们要紧的：

- `MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES` 由传感器按**原版默认 20 tick** 的节奏填充。我们的威胁扫描**不再读它**，改为每 5 tick 自己扫世界（但保留视线判定），因为那一秒滞后正是"敌人贴脸她还愣着"的主要来源。
- `MemoryModuleType.WALK_TARGET` 是移动的唯一出口。自由模式里只有我们和本体的呼吸反射会写它。
- `MemoryModuleType.ATTACK_COOLING_DOWN` 我们用来记挥击冷却。
