# 女仆的同步状态

源：`entity/passive/EntityMaid.java`，`EntityDataAccessor` 声明段

这些是**客户端也能看到的事实**，也就是"她身上有哪些东西可以直接读，不需要我们自己推断"。写新的事实（fact）之前先看这里有没有现成的。

## 身份与外观

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `DATA_MODEL_ID` / `DATA_SOUND_PACK_ID` | String | 模型与音效包 |
| `DATA_IS_YSM_MODEL` 等四个 `YSM_*` | — | YSM 模型联动 |
| `BACKPACK_TYPE` | String | 背包种类（见下） |
| `BACKPACK_SHOW` / `BACK_ITEM_SHOW` / `BACKPACK_ITEM_SHOW` / `BACKPACK_FLUID` | — | 背包外观与流体 |

## 状态与数值

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `DATA_TASK` | String | **当前工作模式 uid**——我们的自由模式判定就读它 |
| `DATA_HUNGER` | int | 饥饿值，上限见 `maid_hunger` 属性 |
| `DATA_FAVORABILITY` | int | 好感点数 0–384，见 [favorability.md](favorability.md) |
| `DATA_EXPERIENCE` | int | 经验 |
| `DATA_STRUCK_BY_LIGHTNING` | bool | 被雷劈过，最大生命额外 +20 |
| `DATA_INVULNERABLE` | bool | 无敌 |
| `DATA_BEGGING` | bool | 正在祈求（要吃的） |

## 行为开关

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `DATA_HOME_MODE` | bool | 家园模式 |
| `DATA_PICKUP` | bool | 是否拾取 |
| `DATA_RIDEABLE` | bool | 可否骑乘 |
| `SCHEDULE_MODE` | MaidSchedule | 作息表 |
| `RESTRICT_CENTER` / `RESTRICT_RADIUS` | BlockPos / float | 活动限制中心与半径 |

> `RESTRICT_RADIUS` 是本体原来推导传送距离用的（`半径 + 2`）。我们**刻意不再用它**推导，传送距离固定 24 格，理由见 [陪伴行为文档](../maid-behaviors/companion/README.md)。

## 战斗与动作

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `DATA_IS_CHARGING_CROSSBOW` | bool | 正在上弩 |
| `DATA_IS_AIMING` | bool | 正在瞄准 |
| `DATA_ARM_RISE` | bool | 抬手（举盾/举物） |

## 交互

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `CHAT_BUBBLE` | ChatBubbleDataCollection | 聊天气泡集合，本体自带一套气泡系统 |
| `GAME_SKILL` / `GAME_STATUE` | — | 棋类游戏的技能与状态 |

## 背包种类

`entity/backpack/`：`EmptyBackpack`、`SmallBackpack`、`MiddleBackpack`、`BigBackpack`，以及功能型的 `CraftingTableBackpack`、`FurnaceBackpack`、`EnderChestBackpack`、`TankBackpack`。

槽位数由背包种类决定，取用统一走 `maid.getAvailableBackpackInv()`——**我们的武器扫描已经用的是这个**，所以换背包自动生效。
