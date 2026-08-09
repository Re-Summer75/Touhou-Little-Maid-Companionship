# 可挂钩的事件与饰品回调

源：`api/event/`、`event/maid/`、`api/bauble/IMaidBauble.java`

**要在某个时刻插手时，先看这里有没有现成的事件**，不要动辄写 mixin。

## API 事件（`api/event/`）

面向第三方，稳定性最好。

### 战斗

| 事件 | 时机 |
| --- | --- |
| `MaidAttackEvent` | 她发起攻击 |
| `MaidHurtTarget` | 她命中目标（有 `Pre`） |
| `MaidHurtEvent` | 她受伤 |
| `MaidDamageEvent` | 伤害结算 |
| `MaidDeathEvent` | 死亡 |

### 生活

| 事件 | 时机 |
| --- | --- |
| `MaidTickEvent` | 每 tick |
| `MaidAfterEatEvent` | 进食后 |
| `MaidPickupEvent` | 拾取 |
| `MaidRequestItemEvent` | 请求物品 |
| `MaidFishedEvent` | 钓到东西 |
| `MaidEquipEvent` | 装备变化 |
| `MaidBackpackChangeEvent` / `MaidBaubleChangeEvent` | 背包 / 饰品变化 |
| `MaidTaskEnableEvent` | 工作模式启用 |
| `MaidFavorabilityLevelChangeEvent` | **好感度等级变化** |
| `MaidTamedEvent` | 被驯服 |
| `InteractMaidEvent` | 被交互 |
| `MaidTombstoneEvent` | 墓碑 |
| `ConvertMaidEvent` / `MaidAndItemTransformEvent` | 转化 |
| `MaidWirelessIOEvent` | 无线传输 |
| `MaidTypeNameEvent` / `MaidPlaySoundEvent` | 名称 / 音效 |
| `AddJadeInfoEvent` / `AddTopInfoEvent` | Jade / TOP 信息条 |
| `RegisterKubeJSEvent` | KubeJS 集成 |

## 内部事件（`event/maid/`）

面向本体自己，改起来更容易受版本影响。

`ApplyGoldenAppleEvent`、`ApplyPotionEffectEvent`、`PotionItemUse`、`GetExpBottleEvent`、`UseNameTagEvent`、`UseFavorabilityToolEvent`、`SaddleMaidEvent`、`MaidMountEvent`、`DismountMaidEvent`、`SwitchSittingEvent`、`HandleBackpackEvent`、`MaidDropBaubleEvent`、`MaidDeathFavorability`、`MaidAreaClickEvent`、`SlabClickEvent`、`MaidFarmlandTrample`

## 饰品回调（`IMaidBauble`）

饰品能在这些时刻插手，**有些能改结果**：

| 回调 | 返回值 | 作用 |
| --- | --- | --- |
| `onTick(maid, stack)` | — | 每 tick |
| `onInjured(maid, stack, source, MutableFloat damage)` | boolean | **能改伤害数值，也能取消** |
| `onDeath(maid, stack, source)` | boolean | **能取消死亡** |
| `onMeleeAttack(maid, stack, target)` | — | 近战命中 |
| `onRangedAttack(maid, stack, task)` | — | 远程攻击 |
| `onMaidEat(maid, stack, food, mealType)` | — | 进食 |
| `onFavorabilityLevelChange(maid, stack, old, new)` | — | 好感升降 |
| `onPutOn` / `onTakeOff` | — | 戴上 / 取下 |
| `syncClient(maid, stack)` | boolean | 是否同步到客户端 |
| `getChatBubbleId()` | String | 关联的聊天气泡 |

> `onInjured` 能改伤害、`onDeath` 能取消死亡——意味着**我们算出的"她还能挨几下"可能被饰品推翻**。风险评估把 `effectiveHealth` 当作硬上限时，这是一个已知的乐观偏差。

## 我们目前的挂钩情况

我们几乎不用这些事件，主要靠 mixin 和自己的编排循环。

自由模式下这是对的：行为时机由我们自己的循环给出，挂在本体事件上等于把时机交还给本体。其它工作模式下则相反——那里保持本体原样，我们只是旁观者，用事件比用 mixin 稳。

> 饰品的 `onInjured` 能改伤害、`onDeath` 能取消死亡。我们把 `effectiveHealth` 当硬上限，因此风险评估对装了这类饰品的女仆偏悲观。
