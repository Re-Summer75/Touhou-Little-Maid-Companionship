# Gecko 条件动作与资源池

本体 `maid.animation.json` 当前提供 97 个动画条目。模型包缺少同名动作时，加载器会尝试合并本体默认动画。

资源位置：

```text
_reference/TouhouLittleMaid/src/main/resources/assets/touhou_little_maid/
animation/maid.animation.json
```

## 1. 基础、状态和杂项：26 个

```text
death
idle
beg
jump
run
walk
attacked
sit
swim
chair
gomoku
bookshelf
computer
keyboard
picnic
sleep
swim_stand
boat
ladder_up
ladder_stillness
ladder_down
game_win
game_lost
pick_up_snowball
vehicle$minecraft:player
empty
```

主状态的触发顺序见 [Gecko 主状态动作](GECKO_MAIN_STATES.md)。

## 2. 基础使用和挥动：4 个

```text
use_mainhand
use_offhand
swing_hand
swing_offhand
```

## 3. 平行动画层：16 个

```text
parallel0
parallel1
parallel2
parallel3
parallel4
parallel5
parallel6
parallel7
pre_parallel0
pre_parallel1
pre_parallel2
pre_parallel3
pre_parallel4
pre_parallel5
pre_parallel6
pre_parallel7
```

这些控制器持续循环。具体骨骼内容由模型包定义，不应当作语义动作调用。

## 4. 持物动作：31 个

### 空手和钓鱼

```text
hold_mainhand:empty
hold_offhand:empty
hold_mainhand:fishing
```

### 物品分类

```text
hold_mainhand:sword
hold_offhand:sword
hold_mainhand:bow
hold_offhand:bow
hold_mainhand:shield
hold_offhand:shield
hold_mainhand:spear
hold_offhand:spear
hold_mainhand:charged_crossbow
hold_offhand:charged_crossbow
hold_mainhand:crossbow
hold_offhand:crossbow
hold_mainhand:pickaxe
hold_offhand:pickaxe
hold_mainhand:shovel
hold_offhand:shovel
hold_mainhand:hoe
hold_offhand:hoe
hold_mainhand:axe
hold_offhand:axe
hold_mainhand:throwable_potion
hold_offhand:throwable_potion
```

### 精确物品

```text
hold_mainhand$touhou_little_maid:picnic_basket
hold_offhand$touhou_little_maid:picnic_basket
hold_mainhand$minecraft:mace
hold_offhand$minecraft:mace
hold_mainhand$minecraft:snowball
hold_offhand$minecraft:snowball
```

## 5. 使用物品动作：15 个

```text
use_mainhand:gohei
use_mainhand:eat
use_offhand:eat
use_mainhand:drink
use_offhand:drink
use_mainhand:spyglass
use_offhand:spyglass
use_mainhand:bow
use_offhand:bow
use_mainhand:shield
use_offhand:shield
use_mainhand:spear
use_offhand:spear
use_mainhand:crossbow
use_offhand:crossbow
```

## 6. 挥动和攻击动作：5 个

```text
swing:sword
swing:throwable_potion
swing_offhand:throwable_potion
swing$minecraft:snowball
swing_offhand$minecraft:snowball
```

这里没有独立的摸头、挥手问候或拥抱动作。`swing_hand` 只是通用挥臂，模型无法判断它代表攻击还是打招呼。

## 7. 命名规则

| 名称模式 | 匹配对象 | 示例 |
| --- | --- | --- |
| `hold_mainhand$物品ID` | 主手精确物品 | `hold_mainhand$minecraft:apple` |
| `hold_offhand$物品ID` | 副手精确物品 | `hold_offhand$minecraft:shield` |
| `hold_mainhand#标签ID` | 主手物品标签 | `hold_mainhand#minecraft:swords` |
| `swing$物品ID` | 主手挥动指定物品 | `swing$minecraft:snowball` |
| `use_mainhand:使用类型` | 主手使用类型 | `use_mainhand:eat` |
| `vehicle$实体ID` | 乘坐指定实体 | `vehicle$minecraft:player` |
| `vehicle#实体标签` | 乘坐带标签实体 | 模型包定义 |
| `chair$模型ID` | 指定座椅模型 | 模型包定义 |
| `passenger$实体ID` | 女仆背负指定实体 | 模型包定义 |
| `head$物品ID` | 头部装备 | 模型包定义 |
| `chest$物品ID` | 胸部装备 | 模型包定义 |
| `legs$物品ID` | 腿部装备 | 模型包定义 |
| `feet$物品ID` | 脚部装备 | 模型包定义 |

符号含义：

- `$`：按注册 ID 精确匹配。
- `#`：按标签匹配。
- `:`：按本体物品分类或 `UseAnim` 匹配。

这些名称是状态机匹配协议，不是公共的强制播放命令。

## 8. 副控制器

| 控制器 | 用途 |
| --- | --- |
| `hold_mainhand` | 主手持物 |
| `hold_offhand` | 副手持物 |
| `swing` | 挥手和攻击 |
| `use` | 使用物品 |
| `misc` | 祈求、棋局结果、捡雪球 |
| `passenger` | 女仆背上的乘客 |
| `magic_casting` | 附属魔法咏唱 |
| `head`、`chest`、`legs`、`feet`、`body` | 装备动画 |
| `parallel0` 到 `parallel7` | 模型包并行动画 |
| `pre_parallel0` 到 `pre_parallel7` | 模型包前置并行动画 |

副控制器可以和主状态并行。例如女仆播放 `walk` 时，可以同时叠加持剑与护甲动画。

## 9. 相关源码

```text
_reference/TouhouLittleMaid/src/main/java/com/github/tartaricacid/touhoulittlemaid/
client/animation/gecko/AnimationManager.java
client/animation/gecko/GeckoMaidEntity.java
client/animation/gecko/GeckoModelLoader.java
```
