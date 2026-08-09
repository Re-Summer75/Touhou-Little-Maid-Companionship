# 属性表

源：`entity/passive/EntityMaid.java#createAttributes`、`init/InitAttribute.java`

**任何"她能做到多远/多快/多疼"的数字，先在这里查是不是属性。** 是属性就该读属性，不要另写常量——属性会被装备、饰品、状态效果、其它模组改，写死的常量不会。

## 原版属性

| 属性 | 基础值 | 说明 |
| --- | ---: | --- |
| `MAX_HEALTH` | 20 | **由好感度改基础值**：20/30/40/80，雷击再 +20 |
| `ATTACK_DAMAGE` | — | **由好感度改基础值**：2/3/4/6 |
| `ATTACK_SPEED` | — | 每秒攻击次数，**受手持武器的攻速修饰符影响**（铁剑 1.6、斧约 0.9）。注意空手或持弓时读到的是**裸基础值 4.0** |
| `ATTACK_KNOCKBACK` | — | |
| `FOLLOW_RANGE` | **64** | 注释明说"目前仅用于寻路，女仆最大可寻路 64 格"，**不是索敌范围** |
| `LUCK` | — | 保留未用 |
| `ForgeMod.ENTITY_REACH` | **2** | 女仆近战范围判定的基础值，好感度在此之上加 0/1/3/5 |

> ⚠️ `ATTACK_SPEED` 的陷阱：我们曾用 `maid.getAttributeValue(ATTACK_SPEED)` 给**背包里**的近战武器定价，而她当时握着弓——读到 4.0，等于把铁剑的输出高估两倍半。逐武器攻速必须从**物品自己的修饰符**算：`基础值 + 物品 MAINHAND 的 ATTACK_SPEED 修饰符`。

> ⚠️ `FOLLOW_RANGE` 是 64，别拿它当感知或索敌半径用。我们对**敌人**用 `FOLLOW_RANGE` 估算远程触及是另一回事（那是敌人自己的属性，通常 16）。

## 本体新增属性

`init/InitAttribute.java`，全部 `setSyncable(true)`。

| 属性 | 默认 | 含义 |
| --- | ---: | --- |
| `maid_use_item_speed` | 1 | 使用物品速度倍率 |
| `maid_crossbow_attack_speed` | 1 | 弩攻速倍率 |
| `maid_gun_attack_speed` | 1 | 枪攻速倍率 |
| `maid_shoot_cooldown` | 2 | 射击冷却 |
| `maid_trident_cooldown` | 20 | 三叉戟冷却 |
| `maid_pickup_range` | 0.5 | 拾取范围 |
| `maid_passive_use_shield_tick` | 100 | 被动举盾时长 |
| `maid_hunger` | 20 | 饥饿上限 |

**这些我们目前一个都没读。** 尤其是三个攻速/冷却属性：我们的 `CombatReadiness.SHOTS_PER_SECOND` 是写死的 1.0，而本体给弩和枪各留了倍率属性，给射击和三叉戟各留了冷却属性。整合包或饰品调了它们，我们的定价不会跟着变。

## 不走属性的量（必须问方法，不能猜）

| 量 | 来源 | 注意 |
| --- | --- | --- |
| 女仆近战触及 | `maid.getMeleeAttackRangeSqr(target)` | 覆写版，**忽略 target 参数**，实为 `ENTITY_REACH + 好感加成` |
| 横扫范围 | `FavorabilityManager.getSweepRange(target, favorability)` | 随好感四档放大 |
| 敌人近战触及 | `mob.getMeleeAttackRangeSqr(against)` | 原版体宽公式，**依赖目标体宽** |
| 饥饿值 | `maid.getHunger()` | 上限由 `maid_hunger` 属性给 |

> `getMeleeAttackRangeSqr` 忽略 `target` 参数是**上游当前实现的细节**。我们在 `CombatReadiness` 里传 `maid` 自己进去因此碰巧安全，但上游哪天改成考虑目标体宽，我们就会静默算错。
