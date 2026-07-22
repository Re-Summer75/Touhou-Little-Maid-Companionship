# Bedrock JS 动画

传统 SimpleBedrock 模型不使用 Gecko 的统一动作名，而是加载 JavaScript 骨骼脚本。

## 1. 默认身体脚本

```text
animation/maid/default/head/default.js
animation/maid/default/head/blink.js
animation/maid/default/head/beg.js
animation/maid/default/head/music_shake.js
animation/maid/default/head/ear_shake.js
animation/maid/default/head/ear_beg_shake.js
animation/maid/default/head/hair_swing.js
animation/maid/default/head/hair_ponytail_swing.js

animation/maid/default/arm/default.js
animation/maid/default/arm/swing.js
animation/maid/default/leg/default.js

animation/maid/default/sit/default.js
animation/maid/default/sit/no_leg.js
animation/maid/default/sit/skirt_hidden.js
animation/maid/default/sit/skirt_rotation.js
animation/maid/default/sit/skirt_rotation_swing.js

animation/maid/default/status/backpack.js
animation/maid/default/status/backpack_level.js
animation/maid/default/status/sasimono.js
animation/maid/default/tail/default.js
animation/maid/default/wing/default.js
animation/maid/default/sleep/default.js
animation/maid.default.js
```

`head/music_shake.js` 和 `status/backpack_level.js` 在本版中只保留兼容入口，当前实现已废弃或为空。

## 2. 扩展身体脚本

```text
animation/maid/default/arm/extra.js
animation/maid/default/arm/vertical.js
animation/maid/default/head/extra.js
animation/maid/default/head/hurt.js
animation/maid/default/head/reverse_blink.js
animation/maid/default/leg/extra.js
animation/maid/default/leg/vertical.js
animation/maid/default/health/less_show.js
animation/maid/default/health/more_show.js
animation/maid/default/health/rotation.js
```

## 3. 任务显示脚本

Bedrock 模型会根据任务 UID 显示或隐藏任务专用网格：

```text
attack
danmaku_attack
farm
feed_animal
idle
milk
shears
sugar_cane
cocoa
extinguishing
feed
grass
melon
ranged_attack
snow
torch
```

这些更接近“模型部件显示状态”，不是完整身体动作。

限制：

- 部分本体任务没有对应脚本。
- Gecko 模型不使用这套任务显示脚本。
- 切换任务 UID 还会改变真实工作逻辑，不能只为动画随意切换。

## 4. 其它脚本组

本体还提供：

- 护甲显示、温度和天气变体。
- 元旦、圣诞、春节、端午和中秋节日效果。
- 特殊模型脚本。
- 坐垫脚本。
- 玩家女仆脚本。
- 时间、维度和随机选择等通用脚本。

这些脚本主要服务模型包，不是“TLM：朝夕相伴”的通用动作 API。

## 5. 常用状态对应关系

| 实体状态 | Bedrock 表现 |
| --- | --- |
| `isBegging()` | 头部倾斜、呆毛或耳朵等模型骨骼变化 |
| `isMaidInSittingPose()` | 手臂、腿、裙子和整体位置变化 |
| `isSleeping()` | 闭眼并切换睡眠网格 |
| `attackAnim` | 默认手臂攻击动作 |
| `isUsingItem()` | 手臂使用物品姿势 |
| `isSwingingArms()` | 远程武器举手姿势 |
| `hurtTime > 0` | 受伤面部骨骼 |
| `hasBackpack()` | 背包网格显示 |

## 6. 附属接入限制

传统模型没有：

```text
playAnimation("sit")
playAnimation("head_pat")
```

推荐通过真实实体状态触发现有脚本。新增摸头等专用动作时，需要：

1. 模型包加入附属脚本和所需骨骼。
2. 附属同步一个模型可读取的状态。
3. 对普通模型提供 `beg`、挥手或仅气泡的回退表现。

## 7. 相关源码

```text
_reference/TouhouLittleMaid/src/main/java/com/github/tartaricacid/touhoulittlemaid/
client/animation/inner/InnerAnimation.java
client/animation/inner/MaidBaseAnimation.java
client/animation/inner/MaidExtraAnimation.java
client/animation/inner/MaidTaskAnimation.java
client/animation/script/CustomJsAnimationManger.java
```
