# 动作接入指南

## 1. 可由附属触发的动作

| 目标动作 | 推荐触发方式 | Gecko 表现 | Bedrock 表现 |
| --- | --- | --- | --- |
| 坐下 | `maid.setInSittingPose(true)` | `sit` | 坐下骨骼脚本 |
| 站起 | `maid.setInSittingPose(false)` | 回到移动或 `idle` | 恢复站立 |
| 祈求 | `maid.setBegging(true)` | `beg` | 头部和耳朵脚本 |
| 通用挥手 | `maid.swing(hand)` | `swing_hand` 或 `swing_offhand` | 手臂挥动 |
| 远程举手 | `maid.setSwingingArms(true)` | 没有独立主动作 | `arm/swing.js` |
| 奔跑 | 让导航和实体进入冲刺 | `run` | 移动骨骼脚本 |
| 行走 | 让导航真实移动 | `walk` | 移动骨骼脚本 |
| 睡眠 | 使用实体睡眠流程 | `sleep` | 睡眠脚本 |
| 游泳 | 使用实体游泳状态 | `swim` | 硬编码游泳修正 |
| 使用物品 | 使用原版物品流程 | `use_*` | 手臂使用姿势 |
| 吃喝 | 物品 `UseAnim` 为吃或喝 | `use_*:eat/drink` | 使用姿势 |
| 受伤 | 由真实伤害产生 `hurtTime` | `attacked` | 受伤骨骼 |
| 捡雪球 | 发送原版动画消息 | `pick_up_snowball` | 取决于模型支持 |

不要为了动画伪造死亡、伤害、睡眠或任务状态，这些状态会改变真实游戏逻辑。

## 2. 坐下和站起

```java
maid.setInSittingPose(true);
maid.getNavigation().stop();
```

恢复：

```java
maid.setInSittingPose(false);
```

如果坐下时仍有攻击目标或移动记忆，还应根据行为需求清理导航和相关 Brain Memory。

## 3. 短暂祈求

开始：

```java
maid.setBegging(true);
```

结束：

```java
maid.setBegging(false);
```

附属必须自己计时并确保结束状态得到恢复。不要在异常退出或切换任务后永久留下 `begging = true`。

## 4. 通用挥手

```java
maid.swing(InteractionHand.MAIN_HAND);
```

这是通用手臂挥动，也用于攻击。适合作为没有专用动画时的打招呼回退表现，但不具备明确语义。

## 5. 捡雪球动画

该动画需要服务端和跟踪客户端同步：

```java
NetworkHandler.sendToTrackingEntity(
        MaidAnimationMessage.pickUpSnowball(maid),
        maid
);
```

`MaidAnimationMessage` 目前只完整封装了 `pick_up_snowball`。

不建议直接修改：

```text
maid.animationId
maid.animationRecordTime
maid.shouldReset
```

它们属于本体内部实现，升级时可能变化。

## 6. 推荐的状态映射

| 附属行为 | 推荐气泡 | 推荐动作 |
| --- | --- | --- |
| 普通空闲 | 随机图片或 `idle` 颜文字 | 由状态机使用 `idle` |
| 正在工作 | `work` 颜文字或进度气泡 | 由真实任务和移动驱动 |
| 睡眠或休息 | `sleep` 颜文字 | 真实睡眠时使用 `sleep` |
| 受到伤害 | `hurt` 颜文字 | 真实伤害触发 `attacked` |
| 生命值告急 | 自定义警告图片 | 保持真实动作 |
| 工具即将损坏 | 自定义警告图片 | 可短暂 `beg` 提醒 |
| 工作完成 | 自定义完成表情 | 通用挥手或专用庆祝 |
| 请求主人注意 | 文本或指定图片 | 短暂 `beg` |
| 摸头互动 | 开心表情 | 专用动作或回退动作 |
| 大模型思考 | 等待气泡 | 保持当前真实动作 |

气泡文档见 [表情系统](../expressions/README.md)。

## 7. 建议的封装层

```text
MaidActionService
├─ sit(maid)
├─ stand(maid)
├─ beg(maid, duration)
├─ wave(maid)
├─ startInteraction(maid, actionType)
└─ stopInteraction(maid, actionType)
```

封装层负责：

- 选择 Gecko、Bedrock 或通用回退表现。
- 同步服务端和客户端状态。
- 管理持续时间。
- 行为中断时恢复状态。
- 避免多个动作同时抢占同一状态。

## 8. 摸头等新动作

本体没有统一的摸头、拥抱、害羞或庆祝动作。

跨模型实现摸头建议分为三层：

### 游戏逻辑层

- 判断玩家和女仆距离。
- 检查所有权、冷却和女仆状态。
- 增加好感、播放声音或触发其它效果。

### 视觉表现层

- Gecko 模型播放附属约定的专用动画。
- 支持附属脚本的 Bedrock 模型读取同步状态。
- 普通模型回退为 `beg`、通用挥手或仅显示气泡。

### 状态恢复层

- 到时清除动作状态。
- 女仆受伤、死亡、传送或切换任务时立即中止。
- 玩家离开范围时停止持续互动。

## 9. 主要兼容风险

| 风险 | 处理方式 |
| --- | --- |
| 两套模型系统表现不同 | 所有动作都设计通用回退 |
| 模型缺少动画 | 回退到原版状态或仅气泡 |
| 动作状态未恢复 | 统一由服务层管理生命周期 |
| 高优先级状态覆盖动作 | 接受死亡、睡眠、游泳等状态优先 |
| 客户端状态不同步 | 由服务端发送明确同步消息 |
| 内部 API 版本变化 | 不在业务模块直接使用内部字段 |
| 动画和声音不同步 | 行为逻辑分别触发并统一计时 |

## 10. 当前推荐顺序

优先直接复用：

1. `setInSittingPose`。
2. `setBegging`。
3. `swing`。
4. 真实移动、持物和物品使用状态。
5. `MaidAnimationMessage.pickUpSnowball`。

谨慎使用：

1. `AnimationManager.register`。
2. `maid.animationId` 等内部字段。
3. 模型私有骨骼和私有动画名。
4. 为渲染效果伪造真实游戏状态。
