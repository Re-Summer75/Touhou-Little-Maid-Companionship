# 好感度

源：`entity/favorability/FavorabilityManager.java`、`entity/favorability/Type.java`

好感度是一个 0–384 的整数（`DATA_FAVORABILITY`），按点数分四级。**它同时改四样东西**，而我们此前只知道其中一样。

## 分级与加成

| 级 | 点数门槛 | 最大生命 | 攻击力 | **攻击距离加成** | **横扫范围** |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 0 | 20 | 2 | +0 | 1 |
| 1 | 64 | 30 | 3 | **+1** | 2 |
| 2 | 192 | 40 | 4 | **+3** | 3 |
| 3 | 384 | 80 | 6 | **+5** | 4 |

### 两类加成的关键区别

**生命与攻击力走属性**。升级时 `add()` 直接 `setBaseValue` 改 `MAX_HEALTH` 与 `ATTACK_DAMAGE` 的基础值，所以任何读属性的代码都会自动拿到正确的值——我们的 `CombatReadiness` 读属性，这两项一直是对的。

**攻击距离与横扫范围不走属性**，每次现算：

```java
// EntityMaid.getMeleeAttackRangeSqr —— 覆写，且忽略 entity 参数
attackDistance = getAttributeValue(ForgeMod.ENTITY_REACH)      // 基础 2.0
               + getAttackDistancePlusByPoint(favorability);    // 0/1/3/5
```

所以女仆的实际触及是 **2.0 / 3.0 / 5.0 / 7.0 格**，而僵尸只有 `sqrt(0.6*2*0.6*2+0.6) ≈ 1.43`。**她一直是够得到而对方够不到的**，只是我们没用上。

> ⚠️ 我们曾以"双方触及相同"为前提论证零伤害不可能。那个前提是错的。实测拉宽这个窗口并不能降低群战伤害（伤害来自被包围，见 [战斗文档](../maid-behaviors/companion/COMBAT.md)），但**触及数字本身必须按本体的算**。

`ENTITY_REACH` 是 Forge 属性，所以**任何给它加修饰符的武器/饰品都会直接扩大触及**——长柄武器的距离优势天然由它承载，不需要我们另造机制。

### 雷击

`isStruckByLightning()` 为真时，升级重算血量会额外 **+20**（`getHealthByLevel(level) + 20`）。满好感被雷劈过的女仆有 100 血，并触发 `MAID_100_HEALTHY` 成就。

## 加减途径

`Type` 定义三元组：名字、点数、冷却（tick）。同名类型在冷却内不再计分（`canAdd`）。

| 行为 | 点数 | 冷却 |
| --- | ---: | ---: |
| 看书架 / 用电脑 / 敲键盘 / 下五子棋 | +2 | 24000（一天） |
| 睡觉 | +2 | 24000 |
| 五子棋赢 | +8 | 12000 |
| 中国象棋赢 / 国际象棋赢 | +4 | 18000 |
| 吃工作餐 | +1 | 3600（3 分钟） |
| 在家吃饭（`OnHomeMeal`） | +1 | 24000 |
| 家里的饭（`HomeMeal`） | +1 | 1200（1 分钟） |
| 偷吃可食用方块 | +1 | 3600 |
| **死亡** | **−2** | 12000 |

减分有两种：`reduce` 只在**当前等级的点数下限内**扣（不会掉级），`reduceWithoutLevel` 会掉级并把属性改回去。

## 事件

升级/降级都会：

1. 遍历饰品调用 `IMaidBauble.onFavorabilityLevelChange(maid, stack, oldLevel, newLevel)`
2. 发 `MaidFavorabilityLevelChangeEvent`
3. 给主人触发成就 `FAVORABILITY_INCREASED`，满级再触发 `FAVORABILITY_INCREASED_MAX`

加分时无论是否升级都会向附近发红心粒子。

## 我们目前的利用情况

| 加成 | 我们用了吗 |
| --- | --- |
| 生命 | ✅ 读属性，自动正确 |
| 攻击力 | ✅ 读属性，自动正确 |
| 攻击距离 | ✅ 读 `getMeleeAttackRangeSqr`（`MeleeSwing.reach`） |
| 横扫范围 | 自由模式用自己的一套，不随本体的好感分档 |

攻击距离读实时值是必须的：它是 `ENTITY_REACH` 属性加好感加成的合成结果，装备、饰品、其它模组都可能改它，自己算等于把玩家的养成与配装全部丢掉。
