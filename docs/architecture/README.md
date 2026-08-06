# 多版本架构与版本矩阵

本文描述稳定业务模块、版本适配器和可发布 distribution 的边界。版本组合的唯一结构化来源是
[`gradle/version-matrix.json`](../../gradle/version-matrix.json)；不得再把 Minecraft、Forge、
Java、Parchment、TLM、Gecko 及项目路径组合复制到 `gradle.properties`、版本目录或其他清单。
陪伴智能的所有权、生命周期、不变量、生产接线和验证注册规则见
[陪伴智能维护契约](companion-intelligence.md)。

当前默认 target 为 `1.20.1`：

- Minecraft `1.20.1`
- Forge `47.4.0`
- Java `17`
- Parchment `parchment:2023.08.20-1.20.1`
- Touhou Little Maid `>= 1.5.3`
- GeckoLib 代际 `3`
- Forge adapter `:adapters:forge-1.20.1`
- TLM adapter `:adapters:tlm-1.20.1-gecko3`
- distribution `:distribution:forge-1.20.1`

## 依赖方向

依赖只能从外层指向内层：

1. `kernel` 提供身份、事件、结果和服务注册等最小稳定原语。
2. `shared:geometry` 提供不含游戏类型的几何值对象与算法。
3. 八个顶层 `features/*` 及 `:features:ai:behavior`、`:features:ai:orchestration`
   子模块只依赖 `kernel`、必要的 `shared:*` 和通用 JVM 库；feature 之间通过 Port 或领域
   事件协作，不直接绑定彼此实现。
4. `adapters/forge-*` 与 `adapters/tlm-*-gecko*` 实现 Port，并把 MC、Forge、TLM、Gecko
   类型转换为稳定模型。
5. `distribution/*` 是组合根、资源容器和唯一 ForgeGradle 编译边界，同时装配稳定模块与两个
   adapter。

禁止反向依赖：稳定模块不得 import `net.minecraft`、`net.minecraftforge`、TLM 或 Gecko；
adapter 不得依赖 distribution 的 `@Mod` 组合根；TLM adapter 不得持有 Forge 网络、菜单或
installer 基础设施。新增版本不得复制 `kernel`、`shared` 或 `features` 源码。

## 源码规模与目录组织约束

- 单个手写实现模块（Java/Groovy 源文件或构建脚本）原则上不得超过 **500 个物理行**；
  该限制针对单个文件，不是整个 Gradle 子项目的代码总量。文件接近 450 行时，新增职责前应先
  按职责拆分。
- 已超过 500 行的存量文件视为待治理项，不得继续堆叠新职责；下次进行实质修改时应同步拆分。
  自动生成代码、大型声明式映射或必须保持局部性的核心算法可以例外，但须在相邻文档中说明原因，
  且不得借例外继续混入无关职责。
- 拆分必须以领域职责、生命周期或依赖边界为依据，禁止仅按行号把一个内聚流程机械切成多个文件。
- 源码目录优先按 feature 划分，再按职责建立子目录，避免所有实现平铺在同一包层级。职责词汇表
  按层区分，同名包不得跨层复用：
  - 稳定模块专用：`api`（平台中立的用例入口）、`application`、`domain`、`port`、`event`
    （领域事件本身）。
  - adapter 专用：`bridge`（以游戏类型表达、供 Mixin 与 handler 消费的适配契约，对应稳定层的
    `api`）、`handler`（Forge/TLM 事件订阅者，对应稳定层的 `event`）、`client`、`server`、
    `network`、`codec`、`mixin`、`forge`、`tlm`。
  - `verifySourceLayout` 会拒绝出现在 adapter 中的 `feature/*/api` 与 `feature/*/event` 包。
- 同一源码目录原则上不直接放置超过 **12 个生产源码文件**；超过时应按稳定职责继续分组。
  同一父级原则上不放置超过 **8 个同级业务 Gradle 模块**；超过时应先引入领域分组层，
  版本 target 则继续由版本矩阵组织，不得复制业务模块。
- 目录分层应服务于导航和边界表达。除 API 边界、版本隔离或框架约定外，不为单个普通实现创建
  无意义的单文件目录；新子目录应能容纳一组职责一致、共同演进的文件。

## 顶层 feature 与 AI 行为子模块的公开边界

### level

- `MaidLevelApi<S>`：查询、授予经验和覆盖等级进度的用例入口。
- `ExperienceSource`、`LevelChange`、`LevelProgress`：稳定输入/结果模型。
- `MaidLevelStore<S>`：等级持久化 Port。
- `LevelNotificationPort<S>`：等级提升通知 Port，传输方式由 adapter 决定。
- `MaidLevelChangedEvent`：跨功能协作所用领域事件。

### status

- `MaidStatusApi<S>`：查询饥饿/饱和度并应用食物恢复。
- `MaidStatusFeedbackApi<S>`：接收 TLM 运行时确认后的瞬时状态反馈；当前用于带冷却的背包满气泡。
- `MaidStatusState`：稳定状态值。
- `MaidStatusStore<S>`：状态持久化 Port。
- 饥饿与工具耐久策略属于纯领域策略，不接受实体、物品或能力对象。

### ai

- `MaidAiOptimizationApi`：服务端 AI 优化 tuning 与性能、动态范围、战斗反应指标入口；
  `MaidAiTuning` 将 Forge 配置收敛为不可变稳定值，TLM adapter 不反向读取平台配置。
- `PathReachabilityCache`：按女仆实例持有的固定容量可达结果缓存，不保存游戏对象或可变路径。
- `OwnerMotionTracker` 与 `DynamicActivityRadiusPolicy`：以 20 tick 静止、3 tick 移动滞回计算
  瞬时空闲/工作/战斗半径，统一上限 24；只持有内存状态，不修改 TLM NBT 或持久化半径。
- `CombatReactionPolicy` 与 `CombatThreatKind`：固定“女仆攻击者、主人攻击者、主人目标、
  最近敌对实体”的排序，规定可见性、范围、扫描节流和候选上限，不涉及伤害或武器行为。
- `MaidMovementCoordinationApi` 与 `MovementIntentLease`：为 TLM 内置移动写入提供短租约、
  跨系统权威矩阵（紧急 > 硬承诺 > 主人命令 > 软/被动）和 Brain priority 决胜；
  `BehaviorOccupancySnapshot` 将原版占用分为 IDLE/SOFT/HARD，注视等 `OWNER_COMMAND`
  可干净抢占 SOFT。状态不持久化，也不包含 Minecraft 对象。
- `PassiveFollowPolicy`：只在主人静止、已识别原版任务仍有效且未达到紧急传送距离时暂缓
  普通跟随；它仍属于原版 AI 调度优化。
- 性能 API 仍只降低原有判断成本；移动协调不接管 Brain 周期、Activity 或任务列表。
  未知 Task、ExtraBrain 与自定义目标采用 fail-open，保留第三方写入并临时退出仲裁。

### ai:behavior

- `:features:ai:behavior` 是独立 Gradle 子模块，包边界为 `feature.behavior`，只保留原创
  陪伴行为的稳定事实/刺激/动作词表、传感器时序与刺激门面；它依赖编排模块，但不依赖
  `:features:ai` 的原版 AI 优化实现。
- `MaidGazeRecallApi<O, M>` 是注视手势提交入口；`GazeGestureTracker` 实现两段式手势
  （注视令女仆回望，再指向附近地面才构成召唤），任何纯时长阈值都无法区分欣赏与召唤，
  这一性质由"注视 600 tick 不得触发"验证钉住。好感度、模式、距离和动作语义不再硬编码
  于该模块。
- `OwnerFactIds`/`OwnerFacts` 提供 25 项主人事实（手持、体征、姿态、效果、背包），分数
  一律归一到 `[0,1]`；无主人时返回 NaN 而非 0，使"血量偏低"不会对不存在的主人成立。
  TLM 侧按主人而非按女仆缓存，背包扫描走独立慢时钟。
- Affordance 商品值受 `[0,1]` 构造约束：排序按搜索半径比例扣减距离，越界值等于关闭该查
  询的距离因素。零食柜按实际库存计值（空柜为 0），掉落物广告带 TTL 并靠再次观察续期；
  容器同时广告 `open_container`，使"需走到方块"的查询不被地面食物挤占。
- `FreedomMaidTask`（`tlm_companionship:freedom`）是本模组唯一的 TLM 工作项：
  `createBrainTasks` 返回空列表，把行为决策完整交给陪伴编排；保留惊慌、进食与随机走动。
  行为占用分类只在家园模式且身处限制区之外时才计 `HOME_RETURN`，在家不算被占用。
- `AffordanceAdvertisement`/`AffordanceQuery` 与 `DefaultAffordanceIndex` 提供有 revision、
  TTL、top-K 和每 tick 检查预算的对象中心感知；零食柜、主人和座位由 TLM Provider
  在已加载世界事件上发布，查询绝不加载区块。
- `AbilityDefinition`、`AbilityGrant` 与 `AbilityActivationRequest` 将授权、短期请求、
  冷却和执行态分离。`AbilityTemplateCompiler` 只把受支持模板展开为既有 Intent/Plan，
  不建立第二套执行器；Grant 通过 TLM TaskData 持久化。
- `CompanionLearningProfile` 将 Outcome 分别投影为可靠性、主人偏好和时段习惯，不合并成
  总 reward。默认 `SHADOW` 只记录；`ACTIVE` 也只能通过 `UtilityModifierPort` 提供有上限
  的软分数，不能绕过 Guard、授权、命令优先级或资源容量。
- `OwnerCoordinationService` 按主人 UUID 与维度分组，对共享 Request 使用硬资格、Utility、
  距离、负载、fairness aging 和 UUID 稳定平局竞标，并限制 `fanOut`。当前
  `deploy_boat` 自主请求使用唯一响应者；协调故障退化时仍共享 request ID，由资源 Claim
  保持提交安全。

### ai:orchestration

- `:features:ai:orchestration` 是平台中立的确定性意图引擎，包边界为
  `feature.orchestration`。它只依赖 `kernel`，不包含 Minecraft、Forge 或 TLM 类型。
- `IntentDefinition` 与 `PlanDefinition` 描述 Guard、Utility、激活概率、承诺/滞回、
  中断等级、冷却、动作步骤、超时和成功/失败/取消转移；`FactKey`/`FactValue` 与
  `ActionSchema` 对注册事实和动作参数做类型约束。
- `IntentCatalog.compile` 将事实索引、条件、状态名与转移预编译为不可变目录，并拒绝未知
  ID、重复定义、非法参数、不可达状态和无终点循环。`MutableIntentCatalog` 只通过一次
  volatile 发布切换完整代际。
- `DefaultMaidIntentOrchestrator<M>` 为每个主体保留弱引用运行态；普通候选按实体 ID
  错峰评估，显式刺激立即重评，当前动作每 tick 推进。目录代际变化、时间回退、实体卸载、
  Guard 失效和硬中断都会取消旧动作。
- 有界 `EpisodicEvent`、`Belief` 与 `OperationOutcome` 使用 operation/event/correlation
  身份去重；每个执行终态只写一个 Outcome。短期邮箱和 DecisionTrace 留在弱引用运行态，
  高价值记忆才通过 `CompanionMemoryPort` 持久化。
- Plan format 2 可声明 `NEVER_RESUME`、`RESTART_STEP`、`RESUME_CHECKPOINT`、
  `REPLAN_SUFFIX` 或 `ATOMIC`。暂停先 quiesce 并释放移动目标与 Claim，恢复前重验目录、
  Guard、超时和动作前置条件。
- `CoordinationClaimService` 是 ServerLevel 主线程上的原子资源目录，使用 epoch 与 fencing
  token 管理 claimed → occupied → released 生命周期；容器槽、座位、放置点和共享 request
  与单女仆 `MovementIntentLease` 严格分离。
- Forge adapter 读取 `data/<namespace>/maid_ai/intents/*.json` 与
  `data/<namespace>/maid_ai/plans/*.json`。资源栈按优先级覆盖；无效上层定义回退下层，整批
  编译失败保留上一代。格式与扩展注册见
  [`intent-ai-data.md`](intent-ai-data.md)。
- TLM adapter 用一个 `MaidIntentBehavior` 统一更新工作释放/随机游走边沿、采集事实并推进
  调度器。注视处理器与缺食反馈只提交 TTL 刺激；`TlmMaidIntentActions` 只做动作分派，
  主人交互与放船副作用由各自 action family 实现，并以 `COMPANION` 移动来源复用拾取保
  护、硬状态和 fail-open。
- "走过去、占住、做掉"形状的差事统一由 `errand.ApproachAndCommitAction` 骨架执行，它是
  陪伴差事唯一写入 `WALK_TARGET`、唯一认领资源的位置；`Errand` 只提供差异部分（找什么、
  到了做什么、可选 prepare 与复验），`ApproachTarget` 抽象方块槽位/方块/实体三类目标。
  `requiresClaim=false` 表示"到某处"而非"取某物"——跟随、回家与陪伴不认领，否则第一个
  出发者会锁住主人；椅子、掉落物与容器槽按各自粒度认领。放船由能力生命周期驱动、无移动
  写入，形状不同，不纳入骨架。
- 感知查询统一经 `queryAffordances(affordances, commodity, …)` 单一入口，具体查询只保留
  结果解析与世界复验；合格集合作为参数，使数据包可指向本模组未内置的广告主。座位判定
  采用正向清单（TLM 家具、船、矿车），因为原版几乎任何实体都接受乘客——排除法曾先后漏
  掉"椅子也是 LivingEntity"与"女仆坐在掉落物上"两类错误。
- 内置目录定义 `gaze_recall`、`hungry_feedback`、`hungry_standard`、
  `hungry_high_trust`、`post_task_return`、`snack_cabinet_meal`、`wander_return`、
  `anticipate_departure`、`loose_food_meal`、`follow_owner`、`return_home`、
  `rest_on_seat`、`keep_company` 等意图与对应计划；优先级编码语义（回家 50 > 跟随 40 >
  陪伴 30 > 落座 10，地面食物 65 > 零食柜 60）。意图数据验证按目录自动发现计划，
  不维护手工清单。
  饥饿阈值、概率、归队等待和冷却全部由 Data Pack 决定；TOML 只控制引擎预算、诊断和
  注视传感器安全参数。

### advancement

- `MaidStatisticsApi<S>`：累计投喂和经验统计的用例入口。
- `MaidStatisticsStore<S>`：统计持久化 Port。
- `MaidLevelQueryPort<S>`：只读等级视图，避免 advancement 依赖 level 实现。
- `MaidExperienceRewardPort<S>`：完成进度后的经验奖励出口。
- `MaidCourtshipMemory<S>`：动物投喂触发缺口的窄 Port。
- `MaidAdvancementExperienceRewardedEvent` 以及 `MaidStatistics`、`ItemId`、`ResourceId`
  是稳定事件/值模型。

### interaction

- `FaceSelectionApi`：从 adapter 捕获的候选几何中选择稳定面部表面。
- `MaidMouthFeedPort<P>`：服务端已校验口部喂食入口。
- `MaidFeedingStatusPort<S, I>`：interaction 所需的最小饱和度与营养能力。
- `MaidFedEvent`、`FaceGeometry`、`MaidFacePlane` 是稳定事件/几何模型。

### physics

- `PhysicsBoneSelectionPlan`：模型局部、按骨骼身份索引的二级动作选择结果。
- `BoneModelPort`：读取稳定骨架快照和刷新动画基准姿态。
- `PoseDriverPort`：把风等外部驱动力写入复用向量。
- `PoseWriterPort`：把最终姿态写回 adapter 持有的动画骨架。
- `SpringBoneSolver`、碰撞布局和 metadata 模型仅消费稳定快照，不持有渲染器或实体。

### shading

- `FaceNormalTemplate`、`ModelShadingTemplates<M>`：逐面法线/绕序模板及弱键缓存入口。
- `CubeGeometrySource<M>`：读取 adapter 网格立方体的窄 Port。
- 缓存键由 adapter 的网格身份决定；资源重载时由 adapter 触发失效。

### atmosphere

- `EnvironmentalWindField` 与 `WindVector`：确定性、无分配的风场采样入口和值模型。
- `MutableWindVectorPort<V>`：写入 adapter 自有可变向量的零分配 Port。
- 世界、维度、天气和遮挡查询留在 Forge adapter；feature 只接收标量和稳定哈希。

## Forge adapter、TLM adapter 与 distribution

`adapters/forge-<mc>` 负责：

- Forge 生命周期、事件、命令、网络 channel、菜单和客户端安装；
- MC 资源、NBT、几何、封包和世界类型转换；
- 只以 MC/Forge 为目标的 common/client Mixin；
- 为各 feature 提供 packet registrar，不导入 TLM/Gecko 实现。

`adapters/tlm-<mc>-gecko<代际>` 负责：

- `EntityMaid`、Brain/TaskData、TLM 事件和扩展点；
- `ActivityRadiusBridge` 只改写 `getRestrictRadius()` 的瞬时返回值；`CombatReactionBridge`
  在 Brain tick 前预热内置攻击任务的 `ATTACK_TARGET`，tick 后仅为实际战斗目标登记移动租约；
- Gecko、Bedrock、YSM 模型/渲染桥；
- TLM/Gecko Mixin，且所有目标显式 `remap = false`；
- advancement 的世界、战斗、成长 criterion 分别实现窄触发端口，不建立全能触发枢纽；
- 实现 feature Port，但不注册 Forge channel、菜单或 installer。

`distribution/forge-<mc>` 负责：

- 唯一 `net.minecraftforge.gradle` 插件边界与 `@Mod` 组合根；
- 将两个 adapter 的 Java 源目录和稳定模块输出装入同一个发布 jar；
- `mods.toml`、语言、advancement、Mixin descriptor 和生成资源；
- run 配置、reobf、功能验证、IDE 元数据与发布产物。

生产 Java 源除组合根外不得放入 distribution。发布 jar 仍位于
`distribution/forge-<mc>/build/libs`，当前 release 路径保持
`distribution/forge-1.20.1/build/libs`。

## 矩阵装配与 target 选择

`settings.gradle` 永远只 include 一份 `kernel`、`shared:geometry`、八个顶层 feature 和
`:features:ai:behavior`、`:features:ai:orchestration` 子模块，然后从矩阵的每条 target
动态 include 两个 adapter 与一个 distribution。

根构建把矩阵值注入对应 distribution。稳定模块使用所有 target 中最低的 Java 版本编译，
保证单份稳定字节码可被每个 distribution 消费。每个 distribution 使用本 target 的 Java、
Minecraft、Forge、Parchment、TLM 范围、Gecko 代际和 adapter 路径。

以下根任务聚合矩阵中的所有 distribution：

- `check`
- `assemble`（聚合每个 `reobfJar`）
- `verifyAll`

run、IDE 准备、单项验证、审计和 benchmark 使用 target 路由。默认读取矩阵的
`defaultTarget`，所以原命令不带参数仍运行 `1.20.1`：

```powershell
.\gradlew.bat --offline runClient
.\gradlew.bat --offline benchmarkBonePhysics
```

显式选择使用：

```powershell
.\gradlew.bat --offline runClient -PversionTarget=1.20.1
.\gradlew.bat --offline verifyBonePhysics -PversionTarget=1.20.1
```

未知 target 会在配置阶段列出可用 key 并失败，不会静默回退。

## 新增 Forge/MC/TLM/Gecko target

1. 先确认变化能否由现有 feature API/Port 表达。若只是游戏 API 差异，只改 adapter。
2. 选择 target key，并确定 Minecraft、Forge、Java、Parchment、TLM 最低版本和 Gecko
   代际。项目命名必须为 `forge-<mc>`、`tlm-<mc>-gecko<代际>` 和 `forge-<mc>`。
3. 参考 [`templates/version-target`](templates/version-target/README.md) 建立两个 adapter
   壳。只实现当前版本的 Port/安装桥，不复制业务源码。
4. 建立 distribution，保留一个 ForgeGradle 边界和一个 `@Mod` 组合根；接入对应依赖坐标、
   run 配置、资源差异和验证入口。
5. 为该版本准备 common、client、TLM/Gecko 三份 Mixin descriptor，并在 `mods.toml`
   注册。
6. 最后向 `gradle/version-matrix.json` 追加一条对象。模板目录本身永远不会被
   `settings.gradle` include。
7. 运行矩阵护栏和全量离线验证；再生成该 target 的 IDE run 元数据并实机检查。

若 Minecraft 版本相同但需要并存多个 loader/Gecko 组合，应先扩展项目命名规则和护栏，
不能让两个 target 共享同一个 distribution。ForgeGradle 插件自身版本属于构建工具兼容性，
不是游戏版本矩阵字段，仅在新 ForgeGradle API 确实要求时更新版本目录。

未来 NeoForge adapter 可以实现同一组 Port 并拥有独立 distribution；本轮矩阵和模板只描述
已实现的 Forge 边界，不创建 NeoForge 或 Fabric 占位模块。

## 网络 ID 与协议兼容

- channel 名保持 `tlm_companionship:main`。
- 当前协议字符串为 `13`，客户端与服务端使用严格相等检查。
- 消息 ID `0..6` 固定且连续：既有 ID 不得重排、复用或改变方向。
- 兼容新增封包只能追加新 ID，并同步更新连续性验证。
- 改变现有字段顺序、编码、语义或收发方向属于不兼容变更，必须提升协议字符串。
- 不同版本 target 只有在协议字符串、ID、方向和 codec 语义全部一致时才可声明网络兼容；
  Java 类名相同不代表 wire format 兼容。

## Mixin descriptor 与 refmap

每个 Forge distribution 维护三份 descriptor：

- `tlm_companionship.common.mixins.json`：纯 MC 通用目标。
- `tlm_companionship.client.mixins.json`：纯 MC 客户端目标。
- `tlm_companionship.tlm-gecko<代际>.mixins.json`：TLM/Gecko 目标。

MixinGradle 对单个 source set 只可靠生成一份 refmap，因此 common 与 client 共用
`tlm_companionship.refmap.json`。TLM/Gecko descriptor 不声明 refmap，对应 Mixin 必须
`remap = false`。文件名中的 Gecko 代际必须与矩阵及 TLM adapter 名一致；三份 descriptor
均须在 `mods.toml` 注册。

## Java、映射与资源差异

- Java toolchain 和 `--release` 来自矩阵；稳定模块按最低 Java 编译，distribution 按自身
  target 编译。
- Parchment channel/version 只从矩阵注入 ForgeGradle mappings。
- MC/Forge/TLM/Gecko 类型、方法签名和资源格式差异封装在 adapter/distribution，禁止用
  版本判断污染 feature。
- 数据生成、`mods.toml` 展开、Mixin descriptor、语言和 advancement 属于 distribution；
  可跨版本复用的算法与值模型属于 feature/shared。
- 若资源格式跨 MC 版本变化，为对应 distribution 提供版本资源或转换器，不在稳定模块中
  分叉。

## 验证与性能约束

矩阵护栏纯读本地文件，不解析远程元数据，检查：

- target key 唯一且默认 key 存在；
- 三个声明项目目录、`build.gradle` 和 Gecko descriptor 存在并已 include；
- 每条 target 只有 distribution 应用一次 ForgeGradle；
- adapter、distribution 命名与 Minecraft/Gecko 代际一致；
- 矩阵不包含 `kernel`、`shared` 或 `features` 复制项；
- `gradle.properties` 与版本目录不重复矩阵字段。

全量命令：

```powershell
.\gradlew.bat --offline verifyVersionMatrix check verifyAll assemble
```

意图 AI 另有 `verifyIntentOrchestration` 与 `verifyIntentData` 两个纯 JVM 入口，覆盖
Utility、确定性选择、承诺/滞回、硬中断、冷却、恢复状态机、Outcome 去重、Claim 代际、
能力模板、学习边界、确定性竞标、目录代际、格式版本、内置资源编译和无效重载回退。
带 `public static main` 的独立 `*Verification.java` 由构建自动发现并纳入 `verifyAll`；
内部验证片段必须从自动入口静态可达。`verifyVerificationReachability` 会拒绝孤立
Verification；`verifyTestInventory` 只保留源码数量下限，不再被视为注册覆盖证明。
GameTest Server 继续验证注视、三秒指挥跟随、载具共乘、座位镜像/补位与持久锁定、
零食柜单份取餐、统一求食计划、任务后/随机游走归队、战斗抢占、拾取保护、一次性刺激、
放船不重复消费以及同一主人下两名女仆只响应一个共享放船 Request。
`runGameTestServer` 使用独立 `run-gametest` 工作目录，每次启动前删除旧测试世界；空模板
覆盖全部夹具坐标，实体坐标必须通过 `GameTestPositions` 转换，避免并行测试和跨轮残留串扰。

GameTest 类由 Forge 按 `@GameTestHolder` 自动发现，不存在显式注册目录——先前的
`GameTestCatalog` 正因让人误以为可以借注册控制测试集而被删除。多元素场景统一通过
`CompanionScene` 构建（多女仆、无主女仆、椅子、船、掉落物、交战目标），并遵守网格纪律：

- 场景整体抬高 12 格，使家具落在邻居座位查询的 ±4 格垂直带之外；测试放出的椅子和船
  正是隔壁测试要找的东西，曾令三格开外的指挥落座测试间歇失败。
- 敌对目标只创建、不加入世界：占用分类只问"是否持有攻击目标"，而真实僵尸的索敌达
  16 格，钉住或抬高都无法隔离。
- 断言不等待寻路：够得着的目标一次调用出结果，够不着的只断言"她被派往何处"。

target 负向检查示例（失败为预期）：

```powershell
.\gradlew.bat --offline runClient -PversionTarget=not-declared
```

physics 热路径保持 `0 B/frame`：帧循环不得创建集合、流、装箱值、临时向量或按帧缓存；
必须复用 scratch、扁平布局和 `MutableWindVectorPort`/`PoseDriverPort` 输出对象。涉及 solver、
碰撞、风驱动或 adapter 姿态桥的改动至少运行 `verifyBonePhysics` 与
`benchmarkBonePhysics`；benchmark 的线程分配结果不是可放宽的建议值，而是架构约束。
