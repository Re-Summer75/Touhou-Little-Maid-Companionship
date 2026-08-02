# 多版本架构与版本矩阵

本文描述稳定业务模块、版本适配器和可发布 distribution 的边界。版本组合的唯一结构化来源是
[`gradle/version-matrix.json`](../../gradle/version-matrix.json)；不得再把 Minecraft、Forge、
Java、Parchment、TLM、Gecko 及项目路径组合复制到 `gradle.properties`、版本目录或其他清单。

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
3. 八个顶层 `features/*` 及 `:features:ai:behavior` 子模块只依赖 `kernel`、必要的
   `shared:*` 和通用 JVM 库；feature 之间通过 Port 或领域事件协作，不直接绑定彼此实现。
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
  原生优先级镜像和冲突指标；仅对已开始的拾取增加有界承诺，普通跟随暂缓，
  战斗意图位于回区与普通跟随之间；物品失效、硬状态或超距传送仍可立即释放。
  状态不持久化，也不包含 Minecraft 对象。
- `PassiveFollowPolicy`：只在主人静止、已识别原版任务仍有效且未达到紧急传送距离时暂缓
  普通跟随；它仍属于原版 AI 调度优化。
- 性能 API 仍只降低原有判断成本；移动协调不接管 Brain 周期、Activity 或任务列表。
  未知 Task、ExtraBrain 与自定义目标采用 fail-open，保留第三方写入并临时退出仲裁。

### ai:behavior

- `:features:ai:behavior` 是独立 Gradle 子模块，包边界为 `feature.behavior`，承载本模组原创
  AI 行为，不依赖 `:features:ai` 的优化实现。
- `MaidGazeRecallApi<O, M>`：主人通过明确手势请求指定女仆靠近的窄用例入口。
- `ContinuousLookTracker` 与 `GazeRecallPolicy`：分别处理连续注视边沿触发、好感度门槛、
  跟随模式和硬移动状态；不持有 Minecraft 对象。
- `MaidHungryOwnerRequestApi<M>` 与 `HungryOwnerRequestPolicy`：在饥饿阈值、周期和概率
  均满足时请求靠近主人。普通分支与现有 40 点“需要食物”提示对齐，每 100 tick 以 10%
  概率触发；饥饿不高于 20 且好感度至少 3 级时切入 25% 的高好感度分支。两者均避让
  战斗与硬移动状态。TLM adapter 以弱引用暂存最长 200 tick 的抵达意图，进入主人身边
  后通过状态动作服务面向主人播放一次请求动作，避免远距离动作打断靠近过程。
- `MaidOwnerReturnApi<M>` 与 `OwnerReturnPolicy`：观察内置工作目标的释放边沿，在 20 tick
  内没有新目标时只写入一次主人目标；每个新随机游走目标另有 10% 概率改选主人，并以
  独立冷却抑制连续归队。运行态只保存在弱引用实例状态中，不进入女仆存档。
- 当前 TLM adapter 以允许穿透方块的服务端射线和 20 tick 连续注视实现召回，并通过
  ExtraBrain 执行求食和归队检查。三类移动写入从原版 AI 优化视角仍是外部行为；归队仅
  只读当前租约来避让工作、战斗、拾取、回区与 fail-open，不反向接管移动仲裁。

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
`:features:ai:behavior` 子模块，然后从矩阵的每条 target 动态 include 两个 adapter 与一个
distribution。

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
- 消息 ID `0..5` 固定且连续：既有 ID 不得重排、复用或改变方向。
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

target 负向检查示例（失败为预期）：

```powershell
.\gradlew.bat --offline runClient -PversionTarget=not-declared
```

physics 热路径保持 `0 B/frame`：帧循环不得创建集合、流、装箱值、临时向量或按帧缓存；
必须复用 scratch、扁平布局和 `MutableWindVectorPort`/`PoseDriverPort` 输出对象。涉及 solver、
碰撞、风驱动或 adapter 姿态桥的改动至少运行 `verifyBonePhysics` 与
`benchmarkBonePhysics`；benchmark 的线程分配结果不是可放宽的建议值，而是架构约束。
