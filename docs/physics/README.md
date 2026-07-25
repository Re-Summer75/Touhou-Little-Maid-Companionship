# 女仆骨骼物理（VRM 弹簧骨）

为 Gecko 女仆的尾巴、头发、耳朵等自由摆动骨骼添加一层二级动作物理：跟随身体运动和预设骨骼动画产生真实惯性摆动、带轻微重力下垂，同时**收敛回动画姿态，并以非对称摆角和保守碰撞代理减少过摆与常见穿模**。实现参考 VRM `VRMC_springBone` 弹簧骨模型。

## 分析基准

- Minecraft：1.20.1 · Forge：47.4.0 · 车万女仆：1.5.3 Forge
- 范围：仅 Gecko 模型（`AnimatedGeoModel`），覆盖头发、尾巴、耳朵、裙摆、丝带、披风、翅膀等软体链；纯客户端。
- 参考技术：VRM `VRMC_springBone`、Dynamic Bone、t3ssel8r 二阶动力学。

发饰防遮挡、动画姿态相对角限制、Backstop 和碰撞代理的实现细节与阶段状态见
[`SECONDARY_MOTION_CONSTRAINTS.md`](SECONDARY_MOTION_CONSTRAINTS.md)。

## 核心原则：刚度拉回动画，重力只是小扰动

早期方案把"重力+惯性合力"当成骨骼的绝对倒向目标，导致抬头时头发朝世界下方倒、尾巴持续下垂——**重力覆盖了造型**。VRM 模型从根上避免这点:

- 每根骨骼维护其尾端方向的模拟值 `currentDir`；
- 每帧用 **Verlet 积分**更新:
  1. **惯性**：`(currentDir − prevDir) × (1 − drag)`，保留上一帧摆动动量；
  2. **刚度**：朝**动画休息方向** `restDir` 拉回 × `stiffness × dt`（`restDir` = 骨骼动画姿态下的尾端朝向，**逐帧跟随动画**）；
  3. **外力**：`(小重力 + 实体移动惯性 + 预设动画惯性 + 转身伪力) × dt`；
  4. 归一化到单位长度。

因为刚度目标是**动画姿态**而非世界下方、重力只是小外力，所以:

- **静止只微垂**：平衡在"刚度 = 重力"处，垂量 ≈ `GRAVITY_POWER / STIFFNESS`（约 8°），有界不发散；
- **抬头头发跟随**：`restDir` 随头骨动画一起抬，参考空间搬运先同步历史方向，非对称摆角负责限制向内倒；默认不再生成头部碰撞；
- **运动才甩**：转身/起步/急停/跳跃以及预设动画中的快速位移、缩放和旋转加减速会驱动瞬态摆动，然后弹回动画姿态。

## 最小活动骨架上的逐骨骼解算

[`PhysicsSolverLayout`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/PhysicsSolverLayout.java) 在模型准备阶段构建“全部 driven 骨、模拟/碰撞参考骨及其到根路径的并集”。这个最小活动子树只保留受驱动骨和传播动画或参考朝向所必需的祖先；无关手臂、武器和装饰分支不会进入每帧热路径。

[`SpringBoneSolver`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SpringBoneSolver.java) 按预序扁平数组迭代解算，不再递归完整骨骼树，也不在热路径查询 `IdentityHashMap`。祖先节点只传播动画姿态；driven 节点先以动画姿态积分并施加自身偏转，再把施加后的朝向传给子骨，因此与旧完整树递归的父子语义一致。

生产约束路径把模拟方向 `currentDir` 转回局部，求从休息轴到合法方向的轴角四元数，与动画局部四元数相乘后再转换为 Gecko 的 ZYX 欧拉值；因此渲染方向与投影状态一致。无约束旧行为只保留在 reference 等价基准通道。

### 解算器模块边界

`SpringBoneSolver` 只保留稳定公共 API，实际实现位于独立
[`solver/spring`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring)
子包；门面与内部实现按状态、姿态、运动采样、积分、约束和写回拆成职责单一的小型模块：

- `SpringBoneEngine` / `SpringBoneFrameRunner` 负责编排生命周期和活动骨架预序遍历；
- `SpringBoneState`、`SpringBoneScratch`、`SpringBoneMetrics` 分别拥有持久状态、零分配 scratch 和诊断计数；
- `AnimationPoseSnapshot` 保存物理写回前的局部 rotation/position，并在下一次动画入口恢复，使物理层成为可逆覆盖；
- `AnimationPoseFrame` 在物理写回前捕获纯动画层级，`AnimationMotionSampler` / `AnimationMotionSample` 计算每段动画枢轴线加速度、安装参考角速度/角加速度和缩放导数；
- `SpringOrientationComposer`、`SpringReferenceTransport`、`SpringDirectionIntegrator`、`SpringConstraintProjector` 依次处理姿态、参考空间、积分和投影；
- `SpringDeflectionApplier` / `SpringPivotCompensator` 写回 Gecko 旋转与虚拟枢轴补偿，`RuntimeBoneEndpoints` 传播最终层级端点；
- `SpringBoneMath` 统一保存解算常量和无分配数学工具。

## 力臂归一化（解决大块几何过摆）

有些骨骼支点在几何边缘、几何体很大（如 `BaseHair` 头盖式整块头发）。同样的旋转角,几何越大末端扫得越远。[`BoneKinematics.measure`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/BoneKinematics.java) 算出每根骨骼**支点到几何最远角的力臂**,把偏转角上限按反比压低:

```text
该骨骼角度上限 = min(MAX_ANGLE, MAX_TIP_DISPLACEMENT / 力臂)
```

大块（力臂大）自动被限到小角度、末端位移有界;小发丝（力臂小）保持灵活。**全自动从几何推导,零逐骨骼硬编码。**

## 驱动骨骼判定

名称不能可靠表达模型作者的意图：第三方模型常用 `bone17` 等匿名节点，同一个 `BaseHair` 也可能既有可见头盖几何又承担分叉父节点。现在由 [`PhysicsBoneSelectionPlan`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneSelectionPlan.java) 保存每个**骨骼实例**的最终决策，优先级为:

1. **模型元数据**：作者显式指定的链与排除项，置信度最高;
2. **几何 / 拓扑自动发现**：[`PhysicsBoneDiscoverer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneDiscoverer.java) 根据绑定姿态的 pivot、cube AABB、薄度、力臂、链长、分叉和相对 Head / Body 位置评分;
3. **名称提示**：[`PhysicsBoneClassifier`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneClassifier.java) 给常见英文、拼音、中文及日文罗马字名称加分并选择默认参数，支持 `TwinTail` 等多词组合；名称仍**不是入选前提**。

具体实现集中在 `client/discovery` 子包：元数据绑定、候选评分、头部/躯干规则、刚性过滤和计划写入彼此独立；`discovery/structure` 另外负责镜像分支、同 pivot 重叠、紧凑底座、下装面板、细长挂饰、侧挂/包头穿戴物、长单骨网格和真实链段拓扑。入口 [`PhysicsBoneDiscoverer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneDiscoverer.java) 只保留稳定门面。

自动发现采用高置信门槛，低置信节点保持静止，避免把肢体、武器和固定头饰当作软体。空骨骼仍作为层级枢轴参与分析但不直接驱动，其语义会沿连续空锚点传给可见子骨。可见的 `MFrontHair` 不会再被当作空 `M` 枢轴；不在 `Head` 层级内、但名称或空间挂点明确属于头发的骨骼也会参与头部候选评分，同时保留已经高置信识别出的翅膀、裙摆等身体软体类型。`BaseHair` / `TopHair` 这类包围头部、有实体几何且连接发丝的分叉骨会识别为 `HEAD_SHELL`。没有子骨但 AABB 在左右、前后覆盖 Head 核心且与头部保持足够纵向重叠的语义头发也按单骨包头壳层处理；这类骨骼同时承载枢轴前后的网格，普通发丝旋转无法让正面保持贴合又让背面自由拖尾，因此使用零静态重力、高刚度、低惯性和低摆幅参数。纯空分叉容器仍跳过。

刘海会额外识别 `Bangs`、`Fringe`、`FrontHair`、`刘海`、`前髪` 等别名；`MBangs`、`LongHair` 这类空锚点即使跨越多级容器或分出多个匿名片段，也会把高置信语义传给所有可见分支。完全匿名的前额薄片、成组小片和连接头发外壳的头顶发束可通过几何与层级关系识别。眉毛、眼、嘴、脸红和表情使用强排除语义，覆盖 `meimao`、`zui`、`xiao`、`saihong`、`lianhong` 等内置模型别名；匿名但呈现为面部下半区对称薄贴片的腮红也会被刚性过滤。即使这些面部组件位于 `Hair` 层级内或名称同时含有 `Hair`，也不会继承头发物理。`Face_Bangs` 这类明确刘海名称仍可正常入选。

仓库内的 [`geckolib_model_reference`](../../geckolib_model_reference/README.md) 保存本体内置的全部 27 个 Gecko 几何模型并参与离线回归。圣女酒狐的匿名 `BaseHair/bone5` 由“前额位置 + 头发外壳直属叶节点”识别；这类旋转多方块刘海的整体 AABB 可能横跨整个额头且看起来不够薄，因此宽度不再被误当成长发长度，直属头发外壳的结构证据也会补偿整体 AABB 的厚度偏差。年糕狐的 `HairFemaleK_Matching` 会作为单骨包头 `HEAD_SHELL`，而独立 `HairFront` 与左右马尾仍保持普通头发动力学。基础纸板狐现在固定验证匿名腮红 `bone53` 刚性、同 pivot 重叠的 `bone29/30/27/31` 发饰刚性、镜像长网格 `bone3/bone9` 作为左右单骨马尾入选，以及 `bone32`、`bone37`、`bone11/38` 保持刚性。`bone37` 把头部多处互不连通、各自带 cube pivot/rotation 的饰件绑在同一 bone 上，`guashi` 也没有占优的连通主体；骨骼级自动物理无法为它们提供唯一安全转轴，因此不会再驱动。`qunzi` 仍使用单骨裙摆档位。

昂贵的完整发现结果按不可变 `GeoModel` 身份弱缓存，并按 `modelId` 分区。模板以共享 `GeoBone` 身份保存 `Decision`、路径和静态运动学；同一 `GeoModel` 创建新的 `AnimatedGeoModel` 时只需一次 O(N) live bone 绑定，仍会生成实例隔离的 [`PhysicsBoneSelectionPlan`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneSelectionPlan.java)，不会跨实体保存 `AnimatedGeoBone`。

女仆切换模型会重建实体自己的 layout 和 solver runtime；实体离开客户端世界会立即遗忘状态；F3+T 与 TLM 自定义模型包热加载会同时清空 sidecar、实例计划、`GeoModel` 模板和实体 runtime。

## 发饰独立与真实链段

- `RIGID_ATTACHMENT_BASE`：命名发夹/发球，以及具有紧凑、同 pivot 重叠或连接远端柔性后代等强结构证据的匿名底座，只跟随动画，不进入 solver；它不会把细长或悬垂后代一起排除。明确的 `Fringe/Hair/Pony` 名称和 metadata 选择优先保留柔性，防止把刘海误判为附件。
- `FLEXIBLE_CHAIN_SEGMENT`：真实 parent-child driven 链会烘焙链根、段序号和段总数。仅自动发现链使用根到梢曲线：根段刚度/阻尼更高、参考跟随更强、摆角更小；梢端刚度/阻尼更低、移动/转身与旋转惯性更高，从而在父偏转正确传播的同时产生相对相位差，不再像整条刚体。
- `COMPOUND_SINGLE_BONE`：基础纸板狐 `bone3/bone9`、年糕狐 `LeftPony/RightPony` 这类所有 cube 只绑定一个 bone 的长网格无法产生真实弯折；系统保留整体小幅摆动，但使用更高刚度/阻尼和更严格的角度、末端位移档位，避免连同发扎大幅扫动。真正分段必须由模型提供多个不同 pivot 的父子骨。
- 运动学会在存在明确主连通 cube 簇时，用 OBB/SAT 连通判定后的主簇推断 attachment pivot/axis 和实际 segment length，同时用全部 cube 计算独立的安全力臂；远离主体的小装饰不会拉偏旋转轴或把碰撞端点拉成虚假长段，也不会从摆幅安全包络中消失。若一个自动候选由多个互不连通且没有任何簇占优的 cube 组构成，则不存在能安全代表全部几何的单一 pivot，整个自动 chain 保持刚性；schema sidecar 显式驱动仍可覆盖。以上全部在模型/layout 构建期完成，不增加逐帧分配。
- schema sidecar 的显式链和排除继续拥有最高优先级；自动刚性结论不会覆盖作者指定的 driven bone。

## 裙摆、身体挂饰与侧挂面具

- 裙摆语义覆盖 `skirt/dress/qunzi`、内外/前后裙片、常见拼音及中日韩名称；`guashi/pendant/tassel`、吊坠和流苏作为 `RIBBON` 候选。宽薄下装面板也可在无可靠名称时由下半身位置、薄度、上缘连接和分支结构识别；没有主导连通簇的分布式候选最终仍保持刚性。
- `Dress/qunzi` 这类主体只绑定一个可动 bone 的网格使用 `COMPOUND_SINGLE_BONE` 裙摆档位；真正的父子 `SKIRT` 链使用根硬梢软曲线。左右、前后或环形独立裙片按真实分支各自生成 chain ID，并只消费 Body Capsule，不再生成腿部代理。
- `Mask` 不再被名称一刀切排除：位于头侧、从上部悬挂且不包覆头部、也不拥有眼嘴表情子树的面具使用 `HEAD_LOCAL RIBBON`。`SHmask/faceplate`、头盔、贴脸面具、带表情子树的面具及手持分支仍保持刚性。
- `HEAD_SHELL` 与悬垂饰品使用零静态重力，静止及匀速动画姿态都保持作者原位；实体移动、转身及预设动画加减速与 Verlet 惯性仍会产生摆动。悬垂饰品另烘焙高参考跟随、高阻尼、较低旋转惯性和约 `7°～14°` 的四向摆角；头部饰品默认只使用摆角限制，身体挂饰仍可使用 Body 自动代理。
- 基础 `zhiban` 的 `bone3/bone9` 所有 cube 仍只绑定单一 bone，因此只能整体小幅摆动；`zhiban_hanfu/new_year` 的 `MWX → MWX2 → MWX3` 则把每段端点精确烘焙到下一 authored joint，运行时 parent tip 与 child pivot 保持重合。

## 模型物理元数据

模型作者可在 TLM 模型包（目录或 ZIP）内添加:

```text
assets/<namespace>/tlm_companionship/physics/<model-path>.json
```

例如模型 ID `geckolib:winefox` 对应 `assets/geckolib/tlm_companionship/physics/winefox.json`:

```json
{
  "schema_version": 3,
  "mode": "auto",
  "exclude": ["Head/Mask", "fixed_decoration*"],
  "chains": [
    {
      "id": "anonymous_back_hair",
      "type": "HAIR",
      "root": "Head/Hair/bone17",
      "include_descendants": true,
      "exclude": ["Head/Hair/bone17/flower"],
      "profile": {
        "stiffness_scale": 0.9,
        "gravity_scale": 0.8,
        "drag_scale": 1.1,
        "inertia_scale": 1.0,
        "turn_scale": 1.0,
        "angle_scale": 0.8,
        "tip_displacement_scale": 0.9
      },
      "constraints": {
        "simulation_space": "HEAD_LOCAL",
        "rotation_inertia_scale": 0.2,
        "swing_limits": {
          "left_degrees": 35,
          "right_degrees": 35,
          "outward_degrees": 40,
          "inward_degrees": 12
        },
        "hit_radius_scale": 1.0,
        "collision": {
          "auto": false,
          "proxies": [
            {
              "kind": "capsule",
              "reference": "Body",
              "start": [0, 8, 0],
              "end": [0, 16, 0],
              "radius": 2,
              "hit_radius": 0.5
            }
          ]
        }
      }
    }
  ]
}
```

- `mode: "auto"`：显式链/排除项优先，其余骨骼继续自动发现;
- `mode: "explicit"`：只驱动元数据列出的链;
- `root` / `bones` 接受完整路径或能唯一定位的路径后缀；名称唯一时也可直接写名称；`*` / `?` 可用于排除匹配;
- `type` 支持 `HEAD_SHELL`、`HAIR`、`TAIL`、`EAR`、`SKIRT`、`RIBBON`、`CAPE`、`WING`、`GENERIC`;
- `profile` 全部是默认参数的 `0–4` 倍率，省略字段等于 `1`。
- `constraints.simulation_space` 支持 `AUTO`、`HEAD_LOCAL`、`BODY_LOCAL`、`MODEL`;
- `rotation_inertia_scale` 为 `0–1`：`0` 完全跟随参考骨旋转并忽略其动画运动外力，`1` 保留模型空间旋转惯性；中间值同时控制动画枢轴线加速度及补充角加速度的注入强度;
- `swing_limits` 使用角度制，分别控制左、右、向外和向内最大摆角;
- `constraints.collision` 是 schema 3 字段。`auto` 默认为 `true`，自动代理与 `proxies` 中的显式代理合并，并应用到相关链的每个受驱动段；设为 `false` 时不生成自动代理，只保留有效的显式代理。显式形状支持 Plane（`point` / `normal`）、Sphere（`center` / `radius`）和 Capsule（`start` / `end` / `radius`）。
- 显式代理的位置和形状半径都使用 **Gecko / Bedrock 模型空间像素**，`16 px = 1` 个模型空间方块；`normal` 只是无单位方向。可选 `hit_radius` 也是像素，表示受驱动骨末端的碰撞半径；省略时从该骨几何横截面推断，最终仍会乘 `hit_radius_scale`。
- `reference` 可写 `MODEL`、`ROOT` 或能唯一解析的节点引用。`ROOT` 只在模型恰有一个顶层根时有效；普通引用接受完整路径、唯一的路径后缀或唯一节点名。缺失或歧义引用只跳过对应代理；代理的静止几何按模型空间填写，运行时随所选 reference 的完整仿射变换运动。
- `backstop` / `head_collision` 仍可解析以兼容旧 sidecar，但不再生成自动头部代理；需要头部或腿部碰撞时必须使用 schema 3 显式 Plane/Sphere/Capsule。
- `schema_version: 1` 的既有 sidecar 在未声明 `constraints` 时保持旧的无约束语义；版本 2 省略 `constraints` 时仍采用部件类型的角度约束默认值。版本 1/2 不读取 schema 3 的 `collision` 字段；新文件应使用版本 3。
- F3+T 资源重载及 TLM 直接加载新下载的目录/ZIP 模型包都会刷新 sidecar、选择计划和实体模拟状态。

## 运动信号与门控

- **重力**：模型空间 `(0, −GRAVITY_POWER, 0)`；悬垂部件使用分类型倍率，`HEAD_SHELL` 仅保留近乎为零的重力，避免抬头时整块头盖向后翻;
- **移动惯性**：实体 `getDeltaMovement` 按 20 TPS 游戏 tick 差分并在两个 tick 之间保持，不再向渲染帧插入“尖峰后归零”的假信号；按渲染器 `180° − bodyYaw` 的逆变换转进模型空间，再取反作为滞后力；**着地时丢弃垂直分量**(避免重力/地面钳位每 tick 抖动);
- **预设动画惯性**：在任何物理写回前按 Gecko 的 position → pivot → ZYX rotation → scale → inverse pivot 顺序构建纯动画层级。每个 driven bone 对动画枢轴位置做二阶差分，并对安装参考的四元数增量取对数得到角速度/角加速度；`α × r`、`ω × (ω × r)`、缩放径向加速度和缩放/旋转耦合项共同形成模型空间末端运动信号。相同姿态的连续渲染样本被视为动画 sample-and-hold：累计真实时间、保持上一加速度目标，只在姿态实际变化时重新求导，避免 20 TPS/不规则控制器更新被误算成“零速度→瞬时高速→零速度”脉冲。直接写在 driven bone 上的关键帧旋转仍由逐帧 `restDirection` 自然产生拖尾，显式角导数只采样其安装参考，避免同一局部旋转重复计入;
- **分类型注入**：动画信号继续乘 `HAIR/TAIL/EAR/SKIRT/RIBBON/CAPE/WING` 类型增益和现有 `SpringProfile.inertiaScale`。枢轴平移使用 `rotationInertiaScale`，补充角项使用 `rotationInertiaScale × (1 − rotationInertiaScale)`，因此完全跟随和完全保留模型空间惯性的两个端点都不会重复注入参考旋转;
- **转身伪力**：`wrapDegrees(yBodyRot − yBodyRotO)` × 增益,补足纯模型空间捕捉不到的转身惯性;
- **非线性降噪**：实体移动和转身信号使用软死区、`tanh` 软限幅与 One Euro 式自适应低通；逐骨动画加速度使用同类径向软门控、`0.5` 软上限和 `40 ms` 指数低通，既保留快速甩动又抑制关键帧插值噪声;
- **帧率与突变**：`dt` 来自 TLM 硬编码动画使用的 `tickCount + partialTick` 时间轴并 clamp 到 `0.1s`；刚度/重力/外力按 dt 缩放，阻尼和动画低通均按时间指数换算。同一动画时刻的重复或乱序渲染返回 `dt=0`，只重新覆盖当前骨骼而不再次推进 Verlet，避免多段链把不稳定的墙钟微步逐级放大。姿态保持 `125 ms` 后加速度目标开始回到零，但导数基线保留到 `250 ms`，兼容低更新率动画。首次采样、暂停、时间轴大间隔、单帧参考旋转超过 `75°`、枢轴跃迁超过 `max(0.5 block, 3 × segmentLength)` 或缩放长度比超过 `2.5` 时重建导数历史并输出零力，避免动画切换/恢复瞬间爆甩;
- **距离门控**：相机 24 格外跳过并清状态。

## 零分配热路径与性能基准

- 每个活动节点持久复用 animation/rendered quaternion、纯动画与渲染层级仿射变换和运行时端点；每个 driven slot 还预分配动画局部 rotation/position 快照，并持久保存上一动画枢轴、线速度、安装参考四元数、角速度、缩放速度及滤波输出；每个实体持有零分配 `AnimationTimelineClock`，并继续复用积分方向、碰撞最近点/法线和其它向量 scratch；
- `AdaptiveMotionFilter`、`MotionNoiseGate`、`MotionSignalSampler` 与 pivot 补偿提供 out 参数路径，热路径不创建 `MotionSignals`、force 向量或滤波输出对象；
- [`BonePhysicsVerification`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/BonePhysicsVerification.java) 用测试专用旧递归解算器作为 oracle，对 `winefox` 与匿名模型逐帧比较 rotation、position 和弹簧方向；序列覆盖可变 dt、移动、转身、暂停和 pivot 补偿，容差为 `1e-5`；
- [`BonePhysicsBenchmark`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/BonePhysicsBenchmark.java) 是独立非门禁入口，充分预热后只围绕 solver 调用报告每帧解算耗时、访问节点数和当前线程分配量，排除测试姿态重置本身的临时对象。绝对耗时受 JVM、CPU 和后台负载影响，不参与 `check` 成败。

最新一次 Windows/JDK 17 开发环境样例（`winefox`，20,000 个测量帧）：

```text
recursive-full: 58647.4 ns/frame, nodes=181/181, allocation=14912.00 B/frame
iterative-active-legacy: 51753.9 ns/frame, nodes=110/181, allocation=0.00 B/frame
iterative-active-constrained: 106806.0 ns/frame, nodes=110/181, allocation=0.00 B/frame
legacy-equivalent solver speedup: 1.13x
constraint-layer cost: 2.06x legacy-active

head/schema2-auto-disabled: 3196.7 ns/frame, proxies=0, allocation=0.00 B/frame
head/schema3-auto-disabled: 3265.0 ns/frame, proxies=0, allocation=0.00 B/frame
skirt/schema2-body-disabled: 3080.1 ns/frame, proxies=0, allocation=0.00 B/frame
skirt/schema3-body-only: 3741.4 ns/frame, proxies=2, allocation=0.00 B/frame
```

## 运行时端点层级

- [`RuntimeBoneEndpoints`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/RuntimeBoneEndpoints.java) 按最小活动骨架的预序顺序，使用与 Gecko 相同的 position → pivot → ZYX rotation → scale → inverse pivot 顺序组合父子仿射变换；
- 所有活动节点缓存最终模型空间 pivot，驱动节点同时缓存由有效 pivot、骨轴和实际 segment length 定义的 tip；真实多骨链的非末段直接以“当前 effective pivot → 下一段 authored pivot”烘焙轴和长度，因此静止及偏转后的 parent tip 与 child runtime pivot 都保持同一关节。父骨物理偏转、虚拟枢轴位移补偿和非均匀缩放都会逐层传给后代；
- [`SpringBoneSolver.copyRuntimePivot`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SpringBoneSolver.java) 与 `copyRuntimeTip` 通过 out 参数暴露结果，单位为模型空间方块；`copyAnimationAcceleration` 暴露当前已门控和滤波的逐段动画惯性信号。首次解算前和 `reset()` 后返回无效；
- 每个受驱动段都独立执行碰撞；其运行时 pivot 包含父骨物理偏转、Gecko position、虚拟枢轴位移补偿和非均匀 scale，最终 pivot/tip 同时供碰撞诊断与调试绘制使用。

## 统一碰撞代理

- [`solver/collision`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision) 提供通用 Plane、Sphere、Capsule 及固定骨长投影；三种形状继续供 Body/Back 自动策略和 schema 3 显式代理使用；
- 每根驱动骨持有有序的不可变 `CollisionProxySet`，可同时关联多个代理；每个代理有独立 `referenceNodeIndex`，因此不同形状可以跟随不同参考骨；
- 自动代理仅保留躯干策略：`SKIRT` 与 `BODY_LOCAL RIBBON` 使用 Body Capsule，`CAPE` 使用 Body Capsule + Back Plane。`HAIR`、`EAR`、`HEAD_LOCAL RIBBON` 不生成头部代理，`SKIRT` 也不生成左右腿代理；schema 3 显式代理仍可按模型需要引用 Head 或 Leg；
- 自动拟合不可把作者原始姿态当成待修复穿透：solver 首次准备或 reset 后会零分配记录每个自动代理已有的静止交叠，只阻止物理方向进一步深入。这样 `dt=0` 的首次写回保持当前动画 rotation/position 完全不变；schema 3 显式代理不使用这项宽限，仍严格服从作者边界；
- schema 3 显式代理追加在自动代理之后；`collision.auto:false` 则仅保留有效显式代理。每个代理 reference 及其祖先都会加入最小活动骨架，包含位于受驱动骨之后的 sibling reference；
- 每帧在任何物理偏转写回前，先捕获带代理段、实际碰撞 reference 及这些节点祖先的动画仿射增量。逐段 runtime pivot 与 segment length 包含父物理、position 补偿及非均匀 scale，碰撞投影使用实际缩放后的段长；全几何 safety lever 另按层级保守 scale 收紧末端位移角限。Plane 法线使用 inverse-transpose，Sphere / Capsule 半径使用仿射变换的保守谱尺度上界，在正交轴缩放时精确、出现剪切时不低估；
- [`RuntimeCollisionCache`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/runtime/RuntimeCollisionCache.java) 为每个代理预分配准备态：每帧只更新上述碰撞依赖节点，并把静止几何变换到运行时一次；四轮摆角/碰撞交替投影复用同一结果，不在每轮重复矩阵变换。加入纯动画运动预采样后，本次环境中完整约束路径相对 `legacy-active` 为 `2.06x`，生产热路径仍为 `0 B/frame`；
- Capsule 使用线段最近点与有限次半空间投影，线段退化为点时走解析 Sphere 路径，端点位于轴线时使用确定性回退法线；两个活动 Plane 形成狭窄可行域时直接求边界交线，避免顺序投影在近切或近反向法线下慢收敛；
- 自动代理会拒绝受驱动 reference、受驱动祖先及由受驱动分支贡献的拟合边界，避免碰撞体被同一物理链反向拖动；代理对象、准备态和 scratch 均由 solver 持久复用。验证覆盖三种形状、单骨三个代理、胶囊退化、近切/近反向 Plane、准备态与直接投影等价、reset 以及通用多代理路径 `0 B/frame`。

## 异常枢轴修正

第三方 Gecko 模型使用的 Bedrock JSON，其 pivot 可能远离网格、落在几何中心，甚至位于柔性部件的末端。直接采用作者 pivot 会导致旋转原点错误；把枢轴强制吸附到受驱动 cube 表面同样不正确，因为真实关节可以位于子件与支撑体的间隙中或支撑体内部。当前使用加载期 **Contact-Aware Virtual Pivot Optimization**：

- 对受驱动主 cube 簇的每个 OBB 面固定采样，并在父级/最近实体祖先的 OBB union 上求最近点对；接触带宽按子件尺度在 `0.5～2 px` 内自适应，允许模型作者留下小间隙；
- 接触点对使用面积与距离加权，经过离群过滤、空间聚类和沿主轴的接触集中度检查；像横穿整个 Head 的薄片会被判为歧义接触，而不会被误认成单侧关节；
- 候选包含接触带中心、最大接触簇中心、authored pivot 向接触平面的受限投影、全局最近点对中点、旧几何附着端和 authored pivot。每个候选在两个正交轴上离线虚拟摆动 `±5°/±10°`，评分新增接触分离、相对静止姿态新增的支撑体穿透、接触中心偏移，以及仅在 authored pivot 本就靠近接触带时启用的作者先验；
- 优化后的枢轴允许落在两表面之间，不再存在“corrected pivot 必须位于 child cube 表面”的约束。接触或候选置信度不足时保留 authored pivot 并收紧摆角；明显远程的 authored pivot 在中等置信度下仍可安全纠正；
- authored pivot 若已位于自身网格并在 `2 px` 内接近可靠接触带，则优先保留，避免把本来正确的关节做无意义位移。`DANGLING_ACCESSORY` 等单骨结构角色仍会纠正真正异常的 pivot；可靠真实多骨链继续保留各段 authored joint，严重脱离的链关节才会替换；
- 对紧凑复合单骨部件增加外围杠杆检查：即使 authored pivot 距某个小 cube 只有约 `1 px`，只要它相对全部可见几何产生了明显过长的质心力臂，而置信度至少 `0.20` 的接触候选能按比例显著缩短该力臂，仍允许纠正。该路径要求至少三个可见 cube、次长轴不小于最长轴的 `0.55`，并排除真实多骨链、耳朵、翅膀、尾巴和裙摆，避免把细长发束或真实悬臂吸到质心；
- 由外围杠杆证据修正的自动部件统一进入 `COMPOUND_SINGLE_BONE` 固定饰品档：零静态重力、`0.12 rad` 几何安全角和较低转动惯性。纸板狐华服 `bone109` 的 authored pivot 到接触候选移动约 `3.59 px`，可见力臂由约 `6.17 px` 降至 `2.96 px`；身份旋转下 position 补偿严格为零，调试字段为 `attachmentLeverCorrected`；
- 所有单骨候选还执行尺度无关的支撑稳定性检查。重力力矩的稳定方向取决于支点与质量中心的相对位置，绝对质量和模型大小不会改变“正置/倒置”结论，因此不再设置 cube 数量、最小跨度或惯性载荷门槛。上下死区按几何对角线缩放为 `clamp(diagonal × 0.025, 0.125 px, 0.5 px)`，最小支点改善量为 `clamp(diagonal × 0.05, 0.25 px, 1 px)`；
- authored pivot 位于质量中心下方、而置信度至少 `0.15` 的接触候选位于质量中心上方并满足改善量时，无论附件是单 cube 还是大型复合模型，都采用上方接触枢轴；该判定由接触拓扑和归一化位置决定，不依赖名称或绝对尺寸。真实多骨链仍保留作者关节；
- 对 Ribbon/Cape/悬垂饰品还执行反向保护：若 authored pivot 仍贴近自身网格且不低于质量中心，而接触优化反而要把它下移到质量中心以下，则拒绝这次优化并保留 authored pivot。这样纸板狐华服小蝴蝶结 `bone101/bone103` 不再从约 `Y=41.06` 被错误拉到约 `Y=38.1`；debug dump 使用 `supportStabilityPreserved` 标记该路径；
- 只有已经被结构分析明确识别为 `DANGLING_ACCESSORY`、却又找不到可信上方接触的单骨附件，才自动回退为 `RIGID_ATTACHMENT_BASE`。普通上生发束、耳朵、裙摆及其它悬臂结构在证据不足时保留 authored pivot，不会因质量中心位于其上方就被误冻结；
- 自动修正的 Ribbon/Cape/悬垂饰品改为 `COMPOUND_SINGLE_BONE` 稳定档：静态重力为零、转动惯性和四向摆角收紧；Hair/Skirt 等其它类型只修正支点并保留原有动力学。debug dump 的 `supportStabilityCorrected` 可直接确认该路径；纸板狐华服后脑 `HUDIEJIE` 会从 authored `Y=34.18` 改用接触推断约 `Y=36.66` 的上方支点；
- `HEAD_SHELL` 优先使用父支撑接触带；宽面接触采用独立的保守阈值，无法形成可靠接触时才回退网格质心。零静态重力和小摆角策略不变；
- 主轴/竖直端点继续提供骨轴与无支撑回退。对单骨和真实链末端，最终再用全部可见 cube 的质量中心验证轴极性：质量中心距 pivot 超过 `0.5 px`、且沿当前轴落在反方向超过 `0.25 px` 时翻转轴，并按翻转后的全几何重新计算 segment length。这样保留轻微歧义下的刘海/裙摆语义方向，同时防止轴指向可见主体背面后把 `-acceleration` 的正确惯性表现成同向前倾；
- 真实多骨链的非末段不执行该翻转，仍精确指向下一 authored joint，保持 parent tip 与 child pivot 连续。debug dump 的 `axisPolarityCorrected` 可直接定位被安全校正的骨骼；
- 实际 Gecko pivot 不可修改，因此在施加旋转时仍同步计算 position 补偿；补偿包含当前动画旋转和非均匀缩放，并在碰撞布局中使用与 Gecko 渲染顺序一致的绑定姿态仿射矩阵；
- 采样、距离场、聚类和虚拟摆动只在模型发现/layout 构建期执行并缓存；逐帧 solver 不执行这些计算，生产热路径仍为 `0 B/frame`。完整算法与回退边界见 [`CONTACT_AWARE_PIVOT_INFERENCE.md`](CONTACT_AWARE_PIVOT_INFERENCE.md)。

## 注入点（可逆物理覆盖）

[`GeckoMaidEntityBonePhysicsMixin`](../../src/main/java/com/laixia/maidintelligence/mixin/GeckoMaidEntityBonePhysicsMixin.java) 在 `GeckoMaidEntity.setCustomAnimations` 的 `HEAD` 恢复上一帧保存的纯动画局部 rotation/position，并在每个 `RETURN` 无条件重新解算。

不能再按返回值跳过：Gecko 控制器的帧率限制器会令父类提前 `return false`，但 `GeckoMaidEntity` 随后仍会运行硬编码动画；内置 `tail/default` 正是在这些帧继续用 `tickCount + partialTick` 重写尾根 X/Z。若只在 `true` 帧施加物理，画面会在纯尾巴动画与动画叠加物理之间交替；若不先恢复就每帧施加，则旧偏转又会被当作动画输入而累积。

`AnimationPoseSnapshot` 在每次 solver 写回前保存所有 driven bone 的六个局部 rotation/position 通道。下一次入口先恢复快照，再让控制器和硬编码动画更新，最后重新覆盖物理，因此限流帧和正常帧都使用同一条动画→物理流水线，且恢复/捕获均为 `0 B/frame`。

Mixin 同时把与 `tail/default` 完全相同的 `tickCount + partialTick` 传给 `AnimationTimelineClock`。同一姿态若因轮廓、额外渲染阶段或其它重复调用进入多次，首次调用按动画时间差推进，后续调用使用 `dt=0`；不再把 0.1～1 ms 的墙钟间隔误当成额外物理子步。该误差在单骨上很小，但会沿 `Tail → Tail7` 的父子变换累积并主要暴露在末端。

慢速动画还会暴露与速度无关的姿态量化：若用 `acos(rest · current)` 提取物理偏角，两个近乎平行的 `float` 单位向量会把点积舍入为 `1`，导致真实偏转连续增长时画面先保持零、跨过精度台阶后再突然跳变。写回现在直接使用同时包含一阶小角信息的 `atan2(|rest × current|, rest · current)` 计算姿态误差；约 `10^-5～10^-4 rad` 的微小偏转也能连续生成局部旋转，不再依赖速度阈值或等待误差累积。该修正逐段生效，因此也阻止了年糕狐 `FoxTailA → Body_Tail6` 六段尾巴把局部量化放大到末端。

## 调试工具

- **`/maidphysicsdebug`** → 获得**骨骼调试棒**(带 NBT 的原版木棍,无需注册物品)。
- **手持** → [`MaidSkeletonDebugLayer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidSkeletonDebugLayer.java) 在所有女仆身上画骨架：灰色小十字 = authored pivot；黄/紫大十字 = 自动/元数据驱动骨骼的有效 pivot；红色大十字 = 支撑端仍有歧义、已自动收紧摆角；橙线 = 推断后的物理骨轴；RGB 线 = Gecko 模型坐标轴；青线 = cube 线框。碰撞层另画亮青色 runtime segment、蓝色 Plane、绿色 Sphere、琥珀色 Capsule，以及与代理同色的 reference origin 十字；当前末端对该代理仍为负 clearance 时，整组代理改画红色。
- **右键女仆** → [`PhysicsDebugSkeletonDump`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsDebugSkeletonDump.java) 把实际 `modelId`、完整骨架、模型路径、`META/AUTO` 来源、裙摆/饰品结构角色与判定原因、链 ID、段序号/总数、重力倍率、最终四向摆角/参考空间/碰撞策略、主簇选择、segment length、真实链 `jointSpacing`、全几何 safety lever 和置信度写入 `run/logs/latest.log`；同一次 dump 还记录每段 runtime pivot/tip、每个代理的 `AUTOMATIC/EXPLICIT` 来源、reference、运行时几何、形状/末端半径、缩放后力臂、clearance 和穿透标记，并在聊天栏汇总骨骼、代理和穿透数量。右键只 dump、不触发其它交互。

## 调参

基础解算参数集中在 [`SpringBoneMath`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/SpringBoneMath.java) 顶部，动画导数门控/滤波位于 `AnimationMotionSample` 和 `AnimationInertiaPolicy`，时钟、24 格门控和诊断窗口位于 [`MaidBonePhysics`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java):

| 参数 | 作用 |
| --- | --- |
| `STIFFNESS` | 拉回动画姿态的刚度;越大越硬、垂量/摆幅越小 |
| `GRAVITY_POWER` | 重力强度;静止垂量 ≈ `GRAVITY_POWER / STIFFNESS` |
| `DRAG` | 惯性阻尼(0–1),越大越快停摆 |
| `INERTIA_GAIN` / `TURN_GAIN` | 移动 / 转身摆幅 |
| `MAX_ANGLE` / `MAX_DEFLECT_*` | 总偏转与逐轴上限(代替碰撞、防钻入身体) |
| `MAX_TIP_DISPLACEMENT` | 力臂归一化的允许末端位移;越小大块越收敛 |
| `MIN_CUTOFF` / `*_BETA` | 自适应降噪的静态平滑强度 / 大动作跟随速度，位于 `AdaptiveMotionFilter` |
| `MAX_ACCELERATION` / `FILTER_TIME_CONSTANT` | 预设动画逐骨惯性信号的软上限 / 低通时间常数 |

上述值是全局基准，实际骨骼还会乘以 `HEAD_SHELL` / `HAIR` / `TAIL` / `EAR` / `SKIRT` / `RIBBON` / `CAPE` / `WING` 的默认档位和可选元数据 `profile`。

## 已知限制

- **代理不是精确网格碰撞**：自动 Plane / Sphere / Capsule 来自静止 AABB 与拓扑启发式，只约束每段的末端球，不能表达任意第三方网格、凹面或整块渲染几何；它能减少常见穿模，但不保证完全无穿模。复杂模型应使用 schema 3 显式代理校正自动结果。
- **头部与腿部默认无碰撞**：为避免自动拟合造成原位偏移，系统不再生成 Head Plane/Sphere/Capsule 或左右腿 Capsule；复杂模型可用 schema 3 显式代理恢复指定区域。
- **硬约束而非 XPBD**：当前摆角和三种碰撞代理使用固定骨长下的硬 PBD 投影；只有出现明确的链间柔性距离需求时才按需加入 XPBD compliance。
- **单骨网格不能真实分段**：同一 Gecko bone 的所有 cube 共享一次 rotation/position 写回。若一个骨骼同时覆盖头部前后，任何刚体旋转都不可能让前发保持贴合、同时保留后发的完整拖尾；自动发现只能把它降为保守 `HEAD_SHELL`，保留很轻的整体响应。需要两侧真正独立运动时，模型必须把前部壳层和后发拆到不同 bone。系统仍可修正具有主导连通簇的枢轴并保守限幅；若多个分离簇势均力敌，则自动保持刚性，而不会任选原点或用渲染劫持伪造与碰撞、端点和 Sodium 路径不一致的局部弯曲。
- **静态几何不能反演真实铰链**：支撑稳定性可以判断“接触候选是否比倒置底支点更合理”，但模型没有材质、胶接强度或运动观测。非 `DANGLING_ACCESSORY` 的歧义悬臂在没有可靠接触时保留作者关节；需要强制刚性或特殊动力学时仍应提供 sidecar。
- **自动发现是保守启发式**：几何无法无歧义地区分造型相似的发丝、丝带和固定装饰；低置信节点默认不动，复杂模型建议提供 sidecar。
- **范围**:仅 Gecko;Bedrock(`BedrockPart` + JS 脚本)与 YSM(仅捕获顶点)暂不支持。

## 验证

```bash
./gradlew.bat --offline verifyBonePhysics
./gradlew.bat --offline benchmarkBonePhysics
./gradlew.bat --offline cleanTest check
```

离线校验 schema 1/2 不生成自动 Head 代理、schema 3 裙摆只生成 Body 代理、显式 Head/Leg 引用仍可用、三种代理几何和胶囊退化、自动与显式布局、父骨偏转、后序显式 Leg reference、完整 affine、非均匀 scale/剪切尺度与缩放后碰撞力臂、近切/近反向 Plane、自动 reference 安全、准备态/直接投影等价、reset、20/30/60/120 FPS 不变量及约束路径 `0 B/frame`。动画惯性另覆盖静止姿态严格零力、关键帧 position/rotation/scale 均能产生有界信号、30/60/120 FPS 峰值一致性、动画切换/暂停/恢复零假冲量，以及 TLM `tail/default` 在控制器限流期间继续更新时的六通道姿态恢复和连续物理覆盖；实际 `winefox` 七段 `Tail → Tail7` 还会用每帧 0～2 次重复渲染验证相同动画时间只推进一次、末端位移与局部旋转无累计跳变，年糕狐 `FoxTailA → Body_Tail6` 则以慢速正弦目标验证微小姿态误差无零值台阶、局部旋转步长及步长变化有界。同时覆盖接触枢轴的 `1～2 px` 间隙、宽面与单骨包头 Head Shell、年糕狐 `HairFemaleK_Matching` 的零重力低惯性档及独立刘海/马尾保留、横穿支撑体的歧义接触、远程 authored pivot、真实多骨链、OBB 主簇、微型单 cube 支撑修正、匿名复合单骨附件、歧义上生悬臂、纸板狐华服 `HUDIEJIE` 上移、`bone101/bone103` 的稳定 authored pivot 保护，以及 `bone109` 的紧凑外围支点和身份补偿。全部 27 个内置模型还会扫描每个单骨/链末端的 `axis · (visibleMassCenter - effectivePivot)`，禁止物理轴明确背离可见主体，并验证 bind pose、首次动画姿态及 reset 后的 `dt=0` rotation/position 完全不变。无主导连通簇的自动候选保持刚性、显式 metadata 可覆盖；裙摆、面具和汉服/新年左右 `MWX` 三段继续验证 authored/effective/runtime 关节分离、端点重合和单次冲量相对响应。
