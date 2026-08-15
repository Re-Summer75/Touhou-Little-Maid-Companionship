# 女仆的同步状态

源：`entity/passive/EntityMaid.java`，`EntityDataAccessor` 声明段

这些是**客户端也能看到的事实**，也就是"她身上有哪些东西可以直接读，不需要我们自己推断"。写新的事实（fact）之前先看这里有没有现成的。

> ⚠️ **副手不是稳定存放处。** `EntityMaid` 覆写了 `completeUsingItem`，其中无条件调用
> `backCurrentHandItemStack()`——那个方法把副手里的东西整个塞进背包（塞不下就丢地上），
> 再把 `hideInv[0]` 放回副手。它服务的是本体"临时换手吃饭"协议
> （`memoryHandItemStack` 存、用完还），但**不检查这一次使用是否走了那套协议**。
> 于是任何完成的物品使用都会顺手卸掉她的副手：实测战斗中吃一颗金苹果（32 tick），
> 随后副手空了两百多 tick。需要副手的功能必须每 tick 复查并重新装备，
> 见 `ShieldGuard.equipFromPack`。
>
> ⚠️ **那套协议的两个方法都会掉东西，且断口是常态。** `memoryHandItemStack` 往
> `hideInv[0]` 存之前，若那一格已有物品**直接生成 `ItemEntity` 扔掉**；
> `backCurrentHandItemStack` 把副手塞进背包时，塞不下的部分**同样扔在地上**。
> 两者假定"存"和"还"总是成对发生、由 `completeUsingItem` 收尾——而战斗每次换武器
> 都会 `stopUsingItem` 打断进食，于是这一对就断了：盾滞留在 `hideInv[0]`、食物滞留
> 在副手，`canUseShield()` 从此为假，而下一顿饭的"存"把玩家的盾扔在地上。
>
> **本模组的规则：副手归自己管，腾空只走背包，整份放得下才放，放不下就一动不动
> ——不产生掉落物。**实现在 `MaidOffhand`（`vacate` / `recoverStranded`）。
>
> 配套的顺序规则：**先从背包取出要用的那件东西，再腾手；腾不出来就原样放回。**
> 腾手要往背包里放东西，而要取的那件（那口饭、那面盾）占着的往往就是唯一的空位。
> 反过来写的后果是"背包满了她就守着食物饿死"、"背包满了她永远拿不到盾"——
> 而背包容量 6/12/18/24/36 里，格子越多的越容易长期处在塞满状态，
> 所以这一条随背包变大而变重要。
>
> 三个入口都经 `MaidOffhand`：`ShieldGuard.equipFromPack`（拿盾前腾手）、`MaidMealAccess`（不再借
> `memoryHandItemStack`）、`WeaponSwap.unpark`（副手只放盾，别的武器回背包，回去之后
> 军械表才看得见它——本体的 `tryEquipFromBackpack` 只在主手和背包之间换，
> **副手从来不是它的一个选项**）。
>
> 盾牌相关的本体机制：`canUseShield()`（副手能 `SHIELD_BLOCK` 且不在冷却）、
> `isBlocking()` **覆写掉了原版的五 tick 预热**（举起即生效）、`blockUsingShield()`
> 实现斧破盾（100 tick 冷却）、`passiveUseShieldTick` 是中弹射物后的自动举盾。
> 主动举盾的 `MaidUseShieldTask` **不在自由模式的 brain 保留清单里**，
> 所以自由模式下的举盾由本模组自己负责。

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
