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
| 低 | `run` | 循环 | 在地面冲刺 |
| 低 | `walk` | 循环 | 在地面移动 |
| 最低 | `idle` | 循环 | 无其它状态时兜底 |

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
                "maid_intelligence:custom_action",
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
