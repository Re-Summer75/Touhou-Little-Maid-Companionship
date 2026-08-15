# Gecko 主状态动作

Gecko 女仆的 `main` 控制器按优先级检查状态，第一个符合条件的动画生效。

## 1. 主状态池

| 优先级 | 动作名 | 循环 | 触发条件 |
| --- | --- | --- | --- |
| 最高 | `death` | 单次 | 女仆死亡 |
| 最高 | `sleep` | 循环 | 实体姿势为 `SLEEPING` |
| 最高 | `swim` | 循环 | 正在游泳 |
| 最高 | `ladder_up` | 循环 | 梯子上向上移动 |
| 最高 | `ladder_stillness` | 循环 | 梯子上静止 |
| 最高 | `ladder_down` | 循环 | 梯子上向下移动 |
| 高 | `gomoku` | 循环 | 五子棋娱乐 |
| 高 | `bookshelf` | 循环 | 书架娱乐 |
| 高 | `computer` | 循环 | 电脑娱乐 |
| 高 | `keyboard` | 循环 | 键盘娱乐 |
| 高 | `picnic` | 循环 | 回家吃饭或野餐 |
| 高 | `boat` | 循环 | 乘坐船 |
| 高 | `chair` | 循环 | 乘坐其它实体 |
| 高 | `sit` | 循环 | 女仆坐下状态 |
| 普通 | `swim_stand` | 循环 | 在水中但没有游泳 |
| 普通 | `attacked` | 单次 | `hurtTime > 0` |
| 普通 | `jump` | 循环 | 离地且不在水中 |
| 低 | `run` | 循环 | 在地面冲刺（本模组按实际速度驱动，见下） |
| 低 | `walk` | 循环 | 在地面移动 |
| 最低 | `idle` | 循环 | 无其它状态时兜底 |

## 1.5 `run`：本模组把它接上了

`run` 的判据是 {@code Mob.isSprinting()}（`AnimationRegister`）。自由模式下没有任何
东西翻过那个标志位，所以这段动画本来一次也不会播。

本模组按**她实际挪多快**驱动它，与她在做什么无关——赶路、跟人、追击、脱离一视同仁。
跑步动画描述的是腿的样子，而"她在做什么"跟那个样子没有必然关系；绑在某个状态上，就
会出现"明明在飞奔却走着"和"明明在挪半格却跑着"，而且每加一个行为都要回来补一笔。

两个门槛，都取原版玩家的速度：

| | 速度 | 含义 |
| --- | --- | --- |
| 起跑 | 5.612 格/秒 | 原版玩家冲刺的速度——原版认定"人型生物在跑"的速度 |
| 收腿 | 4.317 格/秒 | 原版玩家步行的速度 |

**两个门槛而不是一个**：速度在转弯、上坎、被撞、寻路节点之间本来就会掉一下，单门槛
会让她在走跑之间抽搐。中间那一段维持原状，抖动就跨不过去。回差的宽度是这两个原版
数值之差，不是设定值。

> **这只是动画，她没有变快。**`LivingEntity.setSprinting` 覆写了 `Entity` 的同名方法，
> 会额外挂上 `SPEED_MODIFIER_SPRINTING`（+30% 移动速度）。本模组走
> `EntitySharedFlagsAccessor` 直接翻 `Entity` 的第 3 号共享标志位：客户端照样选 `run`，
> 属性表一动不动。由 `movingFastSheRunsAndHerSpeedIsUntouched` 钉住——那条测试里有一
> 条断言专门比对前后的 `MOVEMENT_SPEED`，谁把它换回 `setSprinting` 就会当场变红。

## 2. 优先级示例

女仆在水中同时受伤时：

```text
swim：最高优先级
attacked：普通优先级
```

最终播放 `swim`，因为它先被匹配。

女仆坐下时受到伤害：

```text
sit：高优先级
attacked：普通优先级
```

最终仍然播放 `sit`。不要假设 `hurtTime > 0` 时一定能看到 `attacked`。

## 3. 娱乐动作

以下动作依赖女仆乘坐的 `EntitySit` 及其娱乐类型：

| 娱乐类型 | 动作 |
| --- | --- |
| `GOMOKU` | `gomoku` |
| `BOOKSHELF` | `bookshelf` |
| `COMPUTER` | `computer` |
| `KEYBOARD` | `keyboard` |
| `ON_HOME_MEAL` | `picnic` |

它们不是通过普通任务名称直接触发。

## 4. 载具动作

基础选择顺序：

1. 检查模型包定义的 `chair$模型ID`。
2. 检查 `vehicle$实体ID` 或 `vehicle#实体标签`。
3. 船使用 `boat`。
4. 其它乘坐状态使用 `chair`。
5. 没有乘坐实体但女仆处于坐下状态时使用 `sit`。

## 5. 扩展主状态

内部管理器允许客户端注册额外状态：

```java
AnimationManager.getInstance().register(
        new AnimationState(
                "tlm_companionship:custom_action",
                loopType,
                priority,
                predicate
        )
);
```

这不是稳定的附属 API。使用前必须保证：

- 对应模型拥有同名动画，或默认动画资源能够补齐。
- 状态谓词只在客户端读取安全数据。
- 不会抢占死亡、睡眠等更高优先级状态。
- Bedrock 模型有独立回退方案。

## 6. 相关源码

```text
_reference/TouhouLittleMaid/src/main/java/com/github/tartaricacid/touhoulittlemaid/
client/animation/gecko/AnimationRegister.java
client/animation/gecko/AnimationManager.java
client/animation/gecko/GeckoMaidEntity.java
```
