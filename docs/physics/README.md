# 女仆骨骼物理（VRM 弹簧骨）

为 Gecko 女仆的尾巴、头发、耳朵等自由摆动骨骼添加一层二级动作物理：跟随身体运动、预设骨骼动画和环境风场产生真实摆动、带重力质感，同时**收敛回动画姿态，并以非对称摆角和保守碰撞代理减少过摆与常见穿模**。实现参考 VRM `VRMC_springBone` 弹簧骨模型。

作者姿态是刚度的拉回目标，风和碰撞宽限都以它为参照量、不会把静止造型搬走。**重力是刻意的例外**：它按世界下方恒定作用，因此静止时也会把弹簧停在"重力与恢复力平衡"处而不是画出来的位置，下垂量就是质感的来源。曾改成沿静止方向分解以消除这点位移，实测造型确实不动了，但部件同时失去了垂坠感、看起来发硬，因此退回世界重力。

## 分析基准

- Minecraft：1.20.1 · Forge：47.4.0 · 车万女仆：1.5.3 Forge
- 范围：仅 Gecko 模型（`AnimatedGeoModel`），覆盖头发、尾巴、耳朵、裙摆、丝带、披风、翅膀等软体链；纯客户端。
- 参考技术：VRM `VRMC_springBone`、Dynamic Bone、t3ssel8r 二阶动力学。

发饰防遮挡、动画姿态相对角限制、Backstop 和碰撞代理的实现细节与阶段状态见
[`SECONDARY_MOTION_CONSTRAINTS.md`](SECONDARY_MOTION_CONSTRAINTS.md)。

## 核心原则：刚度拉回动画，重力只叠加不覆盖

早期方案把"重力+惯性合力"当成骨骼的绝对倒向目标，导致抬头时头发朝世界下方倒、尾巴持续下垂——**重力覆盖了造型**。VRM 模型从根上避免这点:

- 每根骨骼维护其尾端方向的模拟值 `currentDir`；
- 每帧用 **Verlet 积分**更新:
  1. **惯性**：`(currentDir − prevDir) × (1 − drag)`，保留上一帧摆动动量；
  2. **刚度**：朝**动画休息方向** `restDir` 拉回 × `stiffness / max(1, mass) × dt`（`restDir` = 作者动画姿态叠加程序化风动画后的尾端朝向，**逐帧跟随目标**）；
  3. **外力**：`(世界重力 + 实体移动惯性 + 预设动画惯性 + 转身伪力) × dt`；
  4. 归一化到单位长度。

因为刚度目标是**动画姿态**而非世界下方，所以:

- **重力只叠加不覆盖，但确实会有静态垂量**：作者画裙摆下垂、腰带侧躺时，画的已经是重力作用后的形态，再叠加一次世界下方的恒力等于把重力算了两遍，弹簧因此停在"力与恢复力平衡"处——上翘的发丝会略垮、横向的丝带会略歪，调小强度只能减轻不能消除。这点位移是被接受的：它同时也是垂坠感的来源。曾把重力沿静止方向分解来消除它（平行分量只改变拉回力度、垂直分量按偏离程度淡入），静止造型确实精确保持在作者位置，但部件不再下沉、观感发硬，因此退回世界重力。真正需要"零静态垂量"的类型（`HEAD_SHELL`、悬垂饰品）改用零重力倍率单独处理；
- 质量不改变重力加速度，只降低重物的 `k/m` 拉回速度；轻物不会比作者基准拉得更硬，否则单帧步长会越过碰撞投影、把末端顶进代理；
- **抬头头发跟随**：`restDir` 随头骨动画一起抬，参考空间搬运先同步历史方向，非对称摆角负责限制向内倒，头部网格 Box 负责挡住真正的穿透；
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
- `WorldPoseDriverSource` / `WorldPoseDriverSources` 是可选氛围动画的注册边界；物理核心只汇总世界空间姿态信号、转换到模型空间并生成移动的 `restDirection`，不依赖具体风场实现。

## 力臂归一化（解决大块几何过摆）

有些骨骼支点在几何边缘、几何体很大（如 `BaseHair` 头盖式整块头发）。同样的旋转角,几何越大末端扫得越远。[`BoneKinematics.measure`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/BoneKinematics.java) 算出每根骨骼**支点到几何最远角的力臂**,把偏转角上限按反比压低:

```text
该骨骼角度上限 = min(MAX_ANGLE, safeAngle, MAX_TIP_DISPLACEMENT / 力臂) × 档位缩放
```

大块（力臂大）自动被限到小角度、末端位移有界;小发丝（力臂小）保持灵活。**全自动从几何推导,零逐骨骼硬编码。**

这个上限有四个消费者：layout 烘焙逐骨上限、投影按运行时缩放重算、偏转写回把渲染角钳到同一值、风驱动只取其中一份额。四者必须一致，否则物理按一个上限收敛、渲染按另一个上限截断。因此公式只存在于 [`SwingRange`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SwingRange.java) 一处：`MAX_ANGLE = 1.05 rad`（约 `60°`）是任何骨都不会越过的平限，`MAX_TIP_DISPLACEMENT = 4.5 px` 约束的是末端行程而不是角度——同样的角度会把长发末端甩得远得多，按行程约束才能让长短段看起来服从同一套规则。

`safeAngle` 是第三道限制，来自 attachment frame 对支点的**实测置信度**而不是范围偏好：支点被证实时它等于 `MAX_ANGLE`（不额外收紧），支点靠推断时收到 `0.40 rad`，端点或支撑置信度不足时进一步收到 `0.25` / `0.18` / `0.12`。绕一个猜出来的旋转中心大角度旋转会把网格搬到作者从未放置的地方，误差随角度放大，所以这几档是几何不确定性的保护，不随全局范围一起放宽。

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
- `FLEXIBLE_CHAIN_SEGMENT`：真实 parent-child driven 链会烘焙链根、段序号和段总数。仅自动发现链使用根到梢曲线：根段质量、刚度和阻尼更高，参考跟随更强、摆角和受风倍率更小；梢端更轻、更软，移动/转身与旋转惯性及受风倍率更高，从而在父偏转正确传播的同时产生相对相位差，不再像整条刚体。
- 链的延续只看**有没有反证**，不再要求每一段自己也能独立评到软体分。空间评分器（`rear lower-body flexible chain`、`narrow planar rear chain`、`broad rear upper-body cloth`）全都奖励位于身体后方的几何，因此正面裙片往下一两段就没有证据可拿，链会在半途断掉，而背面几何完全相同的镜像段却能活下来——`winefox` 的 `RB3` 与 `RF3` 尺寸、薄度、高度全部一致，一个通过一个被拒。既然链的身份已由根段确立，柔性段的唯一子骨只要没有被刚性过滤、可穿戴分类或头附件分类**明确排除**，就继承父段的类型与链 ID。全部内置模型由此多出 `89` 根受驱动骨、无一根被移除，新增的全部是裙链末段、翅膀末节、捧花花瓣和弓弦这类本就该跟着摆的几何。
- `COMPOUND_SINGLE_BONE`：基础纸板狐 `bone3/bone9`、年糕狐 `LeftPony/RightPony` 这类所有 cube 只绑定一个 bone 的长网格无法产生真实弯折；系统保留整体小幅摆动，但使用更高刚度/阻尼和更严格的角度、末端位移档位，避免连同发扎大幅扫动。真正分段必须由模型提供多个不同 pivot 的父子骨。
- 运动学会在存在明确主连通 cube 簇时，用 OBB/SAT 连通判定后的主簇推断 attachment pivot/axis 和实际 segment length，同时用全部 cube 计算独立的安全力臂；远离主体的小装饰不会拉偏旋转轴或把碰撞端点拉成虚假长段，也不会从摆幅安全包络中消失。若一个自动候选由多个互不连通且没有任何簇占优的 cube 组构成，则不存在能安全代表全部几何的单一 pivot，整个自动 chain 保持刚性；schema sidecar 显式驱动仍可覆盖。以上全部在模型/layout 构建期完成，不增加逐帧分配。
- schema sidecar 的显式链和排除继续拥有最高优先级；自动刚性结论不会覆盖作者指定的 driven bone。

## 裙摆、身体挂饰与侧挂面具

- 裙摆语义覆盖 `skirt/dress/qunzi`、内外/前后裙片、常见拼音及中日韩名称；`guashi/pendant/tassel`、吊坠和流苏作为 `RIBBON` 候选。宽薄下装面板也可在无可靠名称时由下半身位置、薄度、上缘连接和分支结构识别；没有主导连通簇的分布式候选最终仍保持刚性。
- “整体足够宽”不能单独证明匿名几何是裙摆：短裤、下摆夹克和骨盆壳同样满足位置与跨度条件。结构分析会检查每个 cube；若某个占节点 AABB 至少 `10%` 的主导 cube 在三轴上都不薄（最短/最长边至少 `0.45`），该节点具有刚性体积核心，不再走宽面板捷径。真正的环形裙壳虽然整体 AABB 有厚度，组成它的仍是薄板，因此不受影响；明确的 `skirt/dress/cloth` 名称仍高于这项匿名反证。店员酒狐的 `jk2` 由 `5 × 4.1 × 4.25 px` 躯干块和两侧厚块组成，现在保持刚性并重新成为裙摆的精确网格碰撞体。
- 一个短小腰环若位于至少两块独立长裙片之上，则它是所有裙片共用的刚性安装座，不是额外弹簧段；即使安装座本身名为 `cloth` 也保持静止，各子裙片仍独立进入 solver。否则整圈围绕身体中心做一次旋转，一侧外摆时另一侧必然内切，单一末端碰撞无法保护完整环形网格。店员、制服和生存者酒狐的 `cloth → Right/Left/Front/BackClothe` 均走此路径。
- `Dress/qunzi` 这类主体只绑定一个可动 bone 的网格使用 `COMPOUND_SINGLE_BONE` 裙摆档位；真正的父子 `SKIRT` 链使用根硬梢软曲线。左右、前后或环形独立裙片按真实分支各自生成 chain ID，并消费可达的刚性 cube 网格；只有完全没有可用网格时才回退到 Body Capsule。
- `Mask` 不再被名称一刀切排除：位于头侧、从上部悬挂且不包覆头部、也不拥有眼嘴表情子树的面具使用 `HEAD_LOCAL RIBBON`。`SHmask/faceplate`、头盔、贴脸面具、带表情子树的面具及手持分支仍保持刚性。
- `HEAD_SHELL` 与悬垂饰品另外使用零静态重力，连"更沉的回拉"也不要；实体移动、转身及预设动画加减速与 Verlet 惯性仍会产生摆动。悬垂饰品另烘焙高参考跟随、高阻尼、较低旋转惯性和约 `7°～14°` 的四向摆角；头部饰品默认只使用摆角限制，身体挂饰仍可使用 Body 自动代理。
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
        "wind_scale": 1.25,
        "mass_scale": 1.2,
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
- `profile` 省略字段等于 `1`。普通倍率限制为 `0–4`；`mass_scale` 限制为 `0.25–4`。`wind_scale` 表示迎风面积/气动耦合，可单独关闭或增强受风目标；`mass_scale` 进入弹簧的 `k/m`：质量越大，跟随阵风越慢、低频惯性和滞后越明显，2 Hz 阵风的跟随幅度明显低于轻部件，但既不会错误改变重力加速度，也不会因为拉回变慢而把静止造型留在偏移位置。
- `constraints.simulation_space` 支持 `AUTO`、`HEAD_LOCAL`、`BODY_LOCAL`、`MODEL`;
- `rotation_inertia_scale` 为 `0–1`：`0` 完全跟随参考骨旋转并忽略其动画运动外力，`1` 保留模型空间旋转惯性；中间值同时控制动画枢轴线加速度及补充角加速度的注入强度;
- `swing_limits` 使用角度制，分别控制左、右、向外和向内最大摆角;
- `constraints.collision` 是 schema 3 字段。`auto` 默认为 `true`，自动网格 Box 与 `proxies` 中的显式代理合并，并应用到相关链的每个受驱动段；设为 `false` 时不生成任何自动代理，只保留有效的显式代理。显式形状支持 Plane（`point` / `normal`）、Sphere（`center` / `radius`）和 Capsule（`start` / `end` / `radius`）；网格 Box 只由自动流程生成，不需要也不接受手工声明。
- 显式代理的位置和形状半径都使用 **Gecko / Bedrock 模型空间像素**，`16 px = 1` 个模型空间方块；`normal` 只是无单位方向。可选 `hit_radius` 也是像素，表示受驱动骨末端的碰撞半径；省略时对刚性网格取 `0.3 px` 的数值容差（以碰撞体最薄半轴的 `15%` 为上限），布料层取外层布片自身半厚，最终仍会乘 `hit_radius_scale`。
- `reference` 可写 `MODEL`、`ROOT` 或能唯一解析的节点引用。`ROOT` 只在模型恰有一个顶层根时有效；普通引用接受完整路径、唯一的路径后缀或唯一节点名。缺失或歧义引用只跳过对应代理；代理的静止几何按模型空间填写，运行时随所选 reference 的完整仿射变换运动。
- `backstop` / `head_collision` 仍可解析以兼容旧 sidecar，但不再生成拟合形状；头部和腿部现在由网格 Box 自动覆盖，特殊区域仍可用 schema 3 显式 Plane/Sphere/Capsule 补充。
- `schema_version: 1` 的既有 sidecar 在未声明 `constraints` 时保持旧的无约束语义；版本 2 省略 `constraints` 时仍采用部件类型的角度约束默认值。版本 1/2 不读取 schema 3 的 `collision` 字段；新文件应使用版本 3。
- F3+T 资源重载及 TLM 直接加载新下载的目录/ZIP 模型包都会刷新 sidecar、选择计划和实体模拟状态。

## 环境风场

- [`EnvironmentalWindFeature`](../../src/main/java/com/laixia/maidintelligence/feature/atmosphere/EnvironmentalWindFeature.java) 是独立氛围增强模块，通过通用 `WorldPoseDriverSource` 扩展点注册 [`EnvironmentalWindSampler`](../../src/main/java/com/laixia/maidintelligence/feature/atmosphere/client/wind/EnvironmentalWindSampler.java)。移除该 feature 只会让扩展点回退为空姿态驱动，不需要修改 `MaidBonePhysics` 或弹簧解算器。
- 宏观风向、阵风包络和波浪前沿只由维度标识、世界坐标与 `gameTime` 生成，因此相邻女仆仍处于同一片连续风场。小尺度在现实中本来就空间去相关，所以快速层、暴风层和飑风层按实体稳定 UUID 哈希取各自的涡旋偏移（最大约 `±640 block`，暴风层取一半），再叠加约 `0.70～1.32` 的扰动增益、`±0.12 rad` 的平均风向偏置和 `0.78～1.22` 的气动响应时间差。结果是同一阵风扫过全场，但每个模型的抖动相位、幅度和迟滞都不同。该过程不创建 `Random`、不依赖墙钟，重复渲染和资源重载仍可复现。
- 单个模型内部同样不同步：每根驱动骨骼按骨名哈希得到自己的涡旋身份，在弹簧内以 `0.32 + 5.5 × 风强` 的 Strouhal 式频率积分独立抖振相位，对受风目标施加 `±0.46 rad` 的摆动方位偏移。相位按 `dt` 积分而非乘以经过时间，因此阵风改变频率时不会跳相；`dt = 0` 的重复渲染沿用同一相位。摆角上限、位移上限与碰撞投影仍作用在结果之上，抖振只改变方向不放宽安全边界。
- **同一条链上的阵风强度是一列沿链传播的波**，而不是各段各抖：`±42%` 的幅度调制改按**链根路径**哈希，全链共享同一个涡旋，再按段序号 `index / count` 给出最多 `0.85` 个格点的相位延迟。同一次阵风于是先到根、后到梢，形成掠过表面的波浪，而不是一堆互不相干的抖动——这正是湍流经过物体时的样子。两个细节是必需的：一是波的频率按**整体风强**和链身份积分（而不是按单段自己的迎风分量），否则各段速率不同、几秒内延迟就会退化成噪声；二是**方位偏移仍然逐骨独立**，幅度和方位都同步的链会像一根刚体一样整体扭转，把布料压进它挂着的身体里（实测多驱动 `0.011 block`，只共享幅度则回到基线）。相位与延迟因此分成两条：强度是全链共享的波，方向是每根骨自己的涡旋。
- 离线校验用四段尾巴测这条波：末段沿风向的高频运动要在**延迟约 16 帧**处与根段高度相关（`0.95`），而逐帧对齐时的相关必须很低（`0.19`）。两个条件都要：弹簧自己就带一点传播延迟（关掉波仍读到 `5` 帧），单看延迟证明不了什么，只有"错开时像、对齐时不像"才是真的有波在走。
- 环境强度综合维度基础值、插值雨量/雷暴、相对海平面高度和浸水状态。每个实体每 `15 tick` 或移动超过一格时，复用一个 `MutableBlockPos` 检查中心与四个水平偏移点的 `canSeeSky`；室内保留约 `6%` 的弱气流，浸水后仅保留约 `8%`。
- 风场采用 [Taylor 冻结湍流近似](https://courses.ems.psu.edu/meteo300/node/737)：无周期二维梯度噪声构成约 `220 / 58 / 28 block` 的平均风向、宏观阵风和局部湍流尺度，再沿主风向平流经过实体。各层使用 `2～3` 个旋转 fBm octave，幅度按近似 Kolmogorov 速度谱的 `2^(-1/3)` 衰减。基础层始终共享，快速层仅按稳定实体哈希错开微观相位；强风再按强度平方混入共享的约 `24 block`、`14 block/s` 暴风层。接近最大风力时，额外非线性混入约 `10 block`、`42 block/s` 且与主风偏转 `25°` 的逐实体飑风层，显著提高横向抖动频率；微风不会被同步增频。另有沿主风传播、横向拉宽到约 `160 block` 的非周期阵风前沿，以 `10 block/s` 调制顺风压力和横向湍流，形成成片经过模型的波浪脉冲，而非单点随机抖动。所有层使用固定平流速度，天气渐变不会在长时间世界中跳相。最终矢量使用 `tanh` 软限幅到 `0.45`，滤波响应从平静时约 `0.38 s`、强风时约 `0.14 s` 连续缩短到最大风时约 `0.055 s`。暂停、`dt=0` 和同一动画时刻的额外 render pass 只复用当前风，不推进滤波。
- 方案遵循 [GPU Gems 的实时随机风动画](https://developer.nvidia.com/gpugems/gpugems3/part-i-geometry/chapter-6-gpu-generated-procedural-wind-animations-trees)与低成本 [fBm 风场合成](https://doi.org/10.20870/ijvr.2011.10.1.2802)思路。Von Kármán / Dryden 更适合飞行动力学中的速度扰动谱，但需要有状态随机整形滤波；当前目标是给多实体骨骼提供共享、可复现的空间风场，因此不采用该路径。
- 世界风使用与移动信号相同的 body-yaw 逆变换进入模型空间，然后按逐骨 `windScale`、逐骨抖振和几何安全角算出一个倾角；弹簧再按逐骨 `massScale` 形成不同的跟随延迟和波浪滤波。风不进入重力/惯性外力积分。摆角、总位移安全限和碰撞静止宽限始终以未受风修改的作者姿态为基准，风不能移动安全边界或把初始风致穿透登记为合法重叠。模型切换、传送、维度变化和时间轴断层会同时清空采样器与姿态信号。
- 风是**加在弹簧上的侧向偏置**，不是被搬走的静止目标：倾角以 `tan(angle)` 的形式沿单位切向进入积分，与恢复项共用同一个刚度系数，因此稳态倾角精确等于 `angle` 且与帧时、质量无关，而作者姿态始终是弹簧被拉回的平衡点。这样阵风停下后骨骼靠自己回位，中途进入解算器的骨骼（换装、变形态）也从作者姿态起步，用几帧倾进风里，而不是凭空出现在偏转后的位置。零时长的重复渲染帧同样不产生位移。
- [`PoseDriveGustFilter`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/pose/PoseDriveGustFilter.java) 在信号进入解算器前扣掉持续分量，只留阵风：常驻的风会让弹簧稳定停在偏置与恢复力平衡的位置不再回来，露天站着的女仆于是常年斜着一片裙摆，读起来已经不是作者做的那个剪影。滤波器减去时间常数 `3 s` 的滑动均值——阵风前沿、湍流、抖振的间隔都远短于此因而完整保留，被持续按住几秒的分量则衰减掉。均值在首次采样时直接取当前风而不是从零起步，否则模型一进入视野就会先甩出去再慢慢飘回来。滤波在模型空间进行，因此转身迎风也算一次阵风。该操作按实体持有一个 `Vector3f`，模型切换与时间轴断层会一并清空。
- 扣掉持续分量约损失三分之一幅度（实测保留微风 `40%`、强风 `63%` 的 RMS），因此受风增益相应上调到 `1.45`，让恶劣天气的观感与允许常驻偏斜时持平；软饱和与部件安全上限不变，强风只是更频繁地触顶。
- 默认受风强度按部件区分：`HEAD_SHELL` 为零，头发、尾巴、丝带和披风较高，耳朵与裙摆适中，翅膀及通用附件保守；自动链的梢端比根部更易受风，固定式单骨附件会进一步衰减。风致目标角使用 `tanh` 软饱和，而非贴住上限的硬截断；部件安全上限约为裙摆/翅膀 `12.6°`、耳朵 `14.9°`、披风/通用部件 `18.3°`、头发/丝带 `22.9°`、尾巴 `26.4°`，另外不超过该骨自身摆角上限的 `78%`，余量留给惯性与行走摆动叠加。裙摆同时保留更高重力倍率和约 `8°` 的向内总摆角限制，优先保持垂坠感并减少穿过身体。
- 这两道上限只决定**最大风**能吹多远。风信号在恶劣天气早已远超上限（雷暴阵风前沿约 `0.45`，裙摆折算的请求角约 `0.59 rad`），因此上限是唯一的瓶颈；而 `tanh` 在小信号段近似线性，`limit × tanh(request / limit)` 在微风下几乎只等于 `request`，放宽上限不会连带改变晴天的观感。长部件（尾巴、披风）此前实际受限于按末端行程折算的几何上限而非分类型上限，因此 `78%` 这一份额对它们的影响比分类型数值更大。

## 运动信号与门控

- **重力**：以 `GRAVITY_POWER` 为大小，沿世界下方恒定注入，因此静止时也存在静态垂量（见上文核心原则）。悬垂部件使用分类型倍率，`HEAD_SHELL` 仅保留近乎为零的重力，避免抬头时整块头盖向后翻;
- **环境风**：缓存环境只低频探测，连续风向和阵风按世界时间每帧采样；世界→模型转换后乘逐骨 `windScale`，将与骨轴正交的分量转换为有安全角上限的程序化动画目标，不作为物理外力;
- **移动惯性**：实体 `getDeltaMovement` 按 20 TPS 游戏 tick 差分并在两个 tick 之间保持，不再向渲染帧插入“尖峰后归零”的假信号；按渲染器 `180° − bodyYaw` 的逆变换转进模型空间，再取反作为滞后力；**着地时丢弃垂直分量**(避免重力/地面钳位每 tick 抖动);
- **预设动画惯性**：在任何物理写回前按 Gecko 的 position → pivot → ZYX rotation → scale → inverse pivot 顺序构建纯动画层级。每个 driven bone 对动画枢轴位置做二阶差分，并对安装参考的四元数增量取对数得到角速度/角加速度；`α × r`、`ω × (ω × r)`、缩放径向加速度和缩放/旋转耦合项共同形成模型空间末端运动信号。相同姿态的连续渲染样本被视为动画 sample-and-hold：累计真实时间、保持上一加速度目标，只在姿态实际变化时重新求导，避免 20 TPS/不规则控制器更新被误算成“零速度→瞬时高速→零速度”脉冲。直接写在 driven bone 上的关键帧旋转仍由逐帧 `restDirection` 自然产生拖尾，显式角导数只采样其安装参考，避免同一局部旋转重复计入;
- **分类型注入**：动画信号继续乘 `HAIR/TAIL/EAR/SKIRT/RIBBON/CAPE/WING` 类型增益和现有 `SpringProfile.inertiaScale`。枢轴平移使用 `rotationInertiaScale`，补充角项使用 `rotationInertiaScale × (1 − rotationInertiaScale)`，因此完全跟随和完全保留模型空间惯性的两个端点都不会重复注入参考旋转;
- **转身伪力**：`wrapDegrees(yBodyRot − yBodyRotO)` × 增益,补足纯模型空间捕捉不到的转身惯性;
- **非线性降噪**：实体移动和转身信号使用软死区、`tanh` 软限幅与 One Euro 式自适应低通；逐骨动画加速度使用同类径向软门控、`0.5` 软上限和 `40 ms` 指数低通，既保留快速甩动又抑制关键帧插值噪声;
- **帧率与突变**：`dt` 来自 TLM 硬编码动画使用的 `tickCount + partialTick` 时间轴并 clamp 到 `0.1s`；刚度/重力/外力按 dt 缩放，阻尼、动画低通和风场过渡均按时间指数换算。同一动画时刻的重复或乱序渲染返回 `dt=0`，只重新覆盖当前骨骼而不再次推进 Verlet 或风场，避免多段链把不稳定的墙钟微步逐级放大。姿态保持 `125 ms` 后加速度目标开始回到零，但导数基线保留到 `250 ms`，兼容低更新率动画。首次采样、暂停、时间轴大间隔、单帧参考旋转超过 `75°`、枢轴跃迁超过 `max(0.5 block, 3 × segmentLength)` 或缩放长度比超过 `2.5` 时重建导数历史并输出零力，避免动画切换/恢复瞬间爆甩;
- **距离门控**：相机 24 格外跳过并清状态。

## 零分配热路径与性能基准

- 每个活动节点持久复用 animation/rendered quaternion、纯动画与渲染层级仿射变换和运行时端点；每个 driven slot 还预分配动画局部 rotation/position 快照，并持久保存上一动画枢轴、线速度、安装参考四元数、角速度、缩放速度及滤波输出；每个实体持有零分配 `AnimationTimelineClock`、通用姿态驱动源及世界/模型姿态向量，风模块内部再独立复用风矢量和天空探针。程序化噪声每次采样通常执行 `64` 个格点哈希，仅进入飑风区间时增至 `76` 个；不创建随机对象、数组或纹理，并继续复用积分方向、程序化目标切线、碰撞最近点/法线和其它向量 scratch；
- `AdaptiveMotionFilter`、`MotionNoiseGate`、`MotionSignalSampler` 与 pivot 补偿提供 out 参数路径，热路径不创建 `MotionSignals`、force 向量或滤波输出对象；
- [`BonePhysicsVerification`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/BonePhysicsVerification.java) 用测试专用旧递归解算器作为 oracle，对 `winefox` 与匿名模型逐帧比较 rotation、position 和弹簧方向；序列覆盖可变 dt、移动、转身、暂停和 pivot 补偿，容差为 `1e-5`；
- [`BonePhysicsBenchmark`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/BonePhysicsBenchmark.java) 是独立非门禁入口，充分预热后只围绕 solver 调用报告每帧解算耗时、访问节点数和当前线程分配量，排除测试姿态重置本身的临时对象。绝对耗时受 JVM、CPU 和后台负载影响，不参与 `check` 成败。测量帧被切成 `5` 轮并**只报告最快的一轮**：单一长平均会把测量期间机器干了什么一并算进去，在这个负载上足有 `20%`，足以凭空造出或抹掉一次改动的效果；最快的那轮是最接近无干扰运行的一轮。

最新一次 Windows/JDK 17 开发环境样例（`winefox`，20,000 个测量帧，取最快轮）：

```text
recursive-full: 35834.0 ns/frame, nodes=181/181, allocation=14560.00 B/frame
iterative-active-legacy: 30963.6 ns/frame, nodes=153/181, allocation=0.00 B/frame
iterative-active-constrained: 232594.9 ns/frame, nodes=153/181, allocation=0.00 B/frame
legacy-equivalent solver speedup: 0.96x
constraint-layer cost: 7.87x legacy-active
constrained collision proxies: 3234 over 80 driven segments

head/schema2-auto-disabled: 3734.3 ns/frame, proxies=0, allocation=0.00 B/frame
head/schema3-auto-disabled: 3157.2 ns/frame, proxies=0, allocation=0.00 B/frame
skirt/schema2-body-disabled: 2671.9 ns/frame, proxies=0, allocation=0.00 B/frame
skirt/schema3-body-only: 5762.0 ns/frame, proxies=11 [Box=11], allocation=0.00 B/frame
```

`constraint-layer cost` 拿整条约束路径与无约束路径相比，因此包含参考空间搬运、摆角投影、姿态驱动和碰撞，不能当成碰撞自身的开销读。要单看碰撞，用 `skirt` 的两行：`11` 个 Box 摊到 `+3.1 µs/frame`。

约束层从 `231 µs` 涨到 `289～298 µs` 是段身采样和“宽限不再永久”一起带来的：多出的一个采样点本身约 `+19 µs`，其余是宽限到期后真正需要求解的接触变多了——此前那些接触是被宽限**定义掉**的，帧时间便宜但腿会直接穿过裙子。段身采样在两处都加了包围球预筛，但收益有限，因为裙段本来就被躯干和两条腿包围着，采样点确实靠在碰撞体上。

重力曾改为相对姿态求解（每段多一个点积，成本可忽略），后因观感发硬退回世界重力，两者的帧时间差落在噪声内：`298 µs` 对 `324 µs`，方向与直觉相反——同一份代码在不同会话测出过 `298` 和 `350 µs`，因此这类个位数百分比的差异只能同会话对照，不能跨会话比较。

“间隙留存”的收益是靠开关做 A/B 量出来的，各取五个样本：最小值 `233` 对 `248 µs`，中位数 `252` 对 `270 µs`，两个统计量方向与量级一致，约 `6%`。之所以只有个位数，是因为省下的几何求解本来就不是这条路径的大头：分段计时显示每帧绑定与剔除约 `494 µs`、整个松弛投影只有 `83 µs`（该次计时含 `nanoTime` 自身开销，故只看比例）。**下一处该动的是剔除的层次**——按参考骨分组再沿最长轴二分之后，每帧仍有 `1099` 次组测试却只否掉 `40.8%`，`2300` 次单体测试否掉 `70.2%`；组被切到平均只剩 `2.9` 个碰撞体，粗筛因此几乎退化成逐个测。碰撞体在参考骨局部空间是刚性的，可以在加载期离线建 BVH、运行时只搬根节点，比通用引擎的动态 AABB 树更省。

## 运行时端点层级

- [`RuntimeBoneEndpoints`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/RuntimeBoneEndpoints.java) 按最小活动骨架的预序顺序，使用与 Gecko 相同的 position → pivot → ZYX rotation → scale → inverse pivot 顺序组合父子仿射变换；
- 所有活动节点缓存最终模型空间 pivot，驱动节点同时缓存由有效 pivot、骨轴和实际 segment length 定义的 tip；真实多骨链的非末段直接以“当前 effective pivot → 下一段 authored pivot”烘焙轴和长度，因此静止及偏转后的 parent tip 与 child runtime pivot 都保持同一关节。父骨物理偏转、虚拟枢轴位移补偿和非均匀缩放都会逐层传给后代；
- [`SpringBoneSolver.copyRuntimePivot`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SpringBoneSolver.java) 与 `copyRuntimeTip` 通过 out 参数暴露结果，单位为模型空间方块；`copyAnimationAcceleration` 暴露当前已门控和滤波的逐段动画惯性信号。首次解算前和 `reset()` 后返回无效；
- 每个受驱动段都独立执行碰撞；其运行时 pivot 包含父骨物理偏转、Gecko position、虚拟枢轴位移补偿和非均匀 scale，最终 pivot/tip 同时供碰撞诊断与调试绘制使用。

## 统一碰撞代理

- [`solver/collision`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision) 提供通用 Plane、Sphere、Capsule、Box(OBB) 及固定骨长投影；Box 供网格自动碰撞使用，其余三种供 Back Plane 策略和 schema 3 显式代理使用；
- 每根驱动骨持有有序的不可变 `CollisionProxySet`，可同时关联多个代理；每个代理有独立 `referenceNodeIndex`，因此不同形状可以跟随不同参考骨；
- 自动碰撞不再拟合胶囊，而是直接使用网格本身：[`MeshColliderPlanner`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/build/MeshColliderPlanner.java) 在 layout 构建期把**每个刚性 cube**取为一个 Box，候选粒度是 cube 而不是骨骼。cube 自带 `dx/dy/dz` 边框，因此 Box 与网格严格等同（含 cube 自身旋转）；若按骨骼取 AABB，59 个散布 cube 的发套会被膨胀成一个大半是空气的包围盒，既不准也会挡住空处。头部、躯干、手臂、腿都无需作者手工摆放形状；`CAPE` 仍额外附加 Back Plane；
- 自动拟合不可把作者原始姿态当成待修复穿透：solver 首次准备或 reset 后会零分配记录每个自动代理已有的静止交叠，只阻止物理方向进一步深入；校准输入固定为未叠加风动画的作者方向，风致穿透不会被吸收到静止宽限。这样 `dt=0` 的首次写回保持当前动画 rotation/position 完全不变；schema 3 显式代理不使用这项宽限，仍严格服从作者边界；
- 自动代理的约束是**相对当前动画姿态**的，与具体是哪个动作无关：每帧用未叠加风和惯性的作者方向重新测量刚性 reference 已经造成的重叠，放宽到该深度，只拒绝二级运动在此之上加深的部分。作者本来就允许模型自穿——坐姿把腿折进裙摆、拥抱把两个身体压在一起、蹲下把裙面顶进大腿——绝对边界会与动画逐帧对抗：动画每帧拉回去、投影每帧推出来，结果不是按帧率嗡嗡抖，就是把部件从作者画的位置整片挤走。宽限按 `0.20 s` 时间常数收紧，因此姿态离开时边界是渐进交还而不是在部件还陷在里面时瞬间合上。显式代理和受驱动布料之间的层碰撞不走这条路径，仍严格执行作者边界；
- 宽限**只能以 `2 px/s` 变宽**，且首帧校准值同样会衰减、不是永久下限。"动画自己造成了重叠"底下藏着两件要求完全相反的事：姿态**落进**重叠是作者剪影，必须放；碰撞体**扫过**去是布料本该反应的东西，放了就是腿直接穿过裙子。区分二者的是**持续时长的量级**——踢腿的接触只有零点几秒，坐下之后腿是长期压在那里的——所以宽限干脆跟不上快速接近：几百毫秒内合拢的姿态照旧被吸收，一次踢腿则跑在宽限前面，仍是真实碰撞。这不需要任何分类器，两端退化行为也都合理：无限速率会把一切吸收掉，零速率会与每个作者姿态对抗。额度按“接触中经过的时间”发放并按步长封顶，因为同一个 `elapsed` 还要服务于释放（离开视野的配对必须按真实时间交还表面），若拿它去换增长额度，一个刚回来的碰撞体一帧就能领到整秒的放宽，等于没有限速。首帧校准只做初值：把它永久保留等于让某一个任意帧的读数说了算，而对会动的碰撞体——腿静止时本来就在它将来要踢的裙子里面——那一帧对其它任何一帧都没有说明力，腿于是获得了永久豁免，可以随时踢到那个深度而裙子一动不动；
- 陷入 Box 内部的末端**沿上一帧那张面推出**：出口面本来每帧按“最浅穿透轴”重选，而这是整套求解里唯一一个离散决策——立方体对角线上两张面深度完全相等，待机姿态只要在这条平局线附近微微颤动，推出方向就会在两帧之间转过 `90°`，看上去就是高频振动而不是稳定接触。同一张面要被抢走，对手必须浅出 `1/64` 方块；否则沿用旧面，末端漂过盒心时也不例外——不然它会被从对侧推出去，那是一次 `180°` 的跳变。末端离开碰撞体即清除记忆。滞回是平局裁决而不是锁定：明显更浅的面照常接管；
- 若某段的投影修正**连续三帧反向**、**且本帧的松弛迭代跑完仍未收敛**，[`SpringProjectionDamper`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/SpringProjectionDamper.java) 会逐帧调低响应至 `12%`，让互相矛盾的要求收敛到中间那个姿态——同时浅浅陷进两侧，而挤压在物理上本来就是这个样子——而不是在两张面之间来回跳。单侧接触的修正方向恒定，永远不触发，全程满力响应；无反向后按 `0.25 s` 恢复。要求**连续**而不是单次是必需的：无解的挤压是周期 2 的极限环，每一帧都反向；而横扫过去的肢体每个步幅只反向一次，按单次反向就降响应，正好会让裙子对被踢这件最需要反应的事失去反应（`14 rad/s` 步频下响应从 `0.28` 掉到 `0.16 rad`）。但"反向"本身不足以认定无解，因为段被拉向的那个目标自己会动：风给它加倾角，而阵风的方向是游走的，于是只是**靠在**身体上的布料，其修正反向得和真正无处可去的一样勤——照此降响应，投影就只剩 `12%` 的力气，风正好把布压进它本来只是靠着的身体里（实测 `0.19 px`，禁用阻尼即消失）。松弛迭代自己能回答这个问题：单个碰撞体一个 pass 就满足并保持满足，而要求相反的两个碰撞体，给多少 pass 都不会收敛。两个条件同时成立才降响应，既保住挤压的阻尼，也不把移动的目标读成挤压；
- 碰撞体不只是"拦住"部件，还会**把它托住**：[`SpringContactSupport`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/SpringContactSupport.java) 按上一帧碰撞修正的方向记下接触法向，并从下一帧的**环境输入**里扣掉压向该法向的分量。环境输入包含风，尽管风是以"倾角"而不是力的形式到达的：风偏移的是弹簧被拉向的那个目标，向内吹时目标就落在身体里面，而投影解不了一个永远到不了的目标——只要天气持续，它每帧回答同一个违规，布料就停在拉力与推力平衡的那个深度上（实测 `0.19 px`）。拉回作者姿态的恢复力刻意不在其中：那个姿态连同它自带的重叠就是平衡点（作者本来就可以把裙裾画进髋部），抵消它会让部件回不到模型画的位置。只靠位置投影解不掉恒力接触，而且失效得相当明显：重力每帧往碰撞体里加一帧的行程，投影每帧原样推回，部件就按这个量永远颤——`winefox` 站着不动三秒，最抖的一段走了 `1.53 rad` 并且隔帧反向，肉眼完全看得见。它能自我维持的原因是帧与帧之间什么都没变：投影回答的是部件在哪，而问题出在还有什么在推它。真实接触是**回答那个推力**的，支撑面给出刚好抵消法向分量的力，部件就在自己完全承载的载荷下静止。法向不用另求：投影只可能把段沿着它撞到的那个面推开，所以它推的方向就是面推回来的方向。仅扣除向内的分量，因此斜面上照旧下滑、被抬离即刻释放；支撑要连续接触若干帧才充满（`20%/帧`），否则正在落向布面的部件会被冻结在初次接触处、垂不到身上；最后一次修正之后按 `0.30 s` 衰减，既覆盖"完全被托住所以已经没有修正可做"这个正常状态，也让碰撞体撤走时部件及时下落。全套 43 段的累计行程由此从 `1.63` 降到 `0.072`；
- 投影同时限制**帧间可见位移**（`40 rad/s`）：硬约束会一步解出合法姿态，违反量一大就是一次瞬移——动画把碰撞体横扫过发丝、换形态、模型带着重叠进入视野都属此类。预算刻意衡量帧与帧之间的结果而不是一次求解内部的工作量：摆角与碰撞在四轮交替里经常大幅互相抵消，最终却只挪动了一点点，若按内部工作量计费，两侧硬边界都会被削弱，段会永远差一点贴不到面。阈值刻意远高于弹簧自身能产生的速度：接触中的段每帧都在被修正，预算一紧就会连带扼住它跟随快速动画的能力，先落后再追上同样是一种卡顿，抖动交给上面两条各自在源头处理。`dt=0` 的重复渲染帧预算为零，因此不产生任何位移；
- 剔除分桶的容量由实测决定，而且平衡点不在直觉的位置。桶越小球包越贴合、过滤越准，看上去应当一路细分下去；但每个桶无论是否命中都要付一次球心仿射变换加一次扫掠测试，这笔固定开销整棵树都要付，而只有存活的桶才为内部逐个代理付钱。`winefox` 的 `3234` 个代理在**桶=8** 时会切出几百个桶，约束层是无约束解算的 `8.4` 倍；**桶=32** 降到 `7.7` 倍（`288 µs` 对 `350 µs`）；再往上（`128`）曲线已平，因为此时逐桶固定开销已经摊薄，只剩过滤变松的副作用。剔除精度不受影响：存活的桶依旧逐个代理测试；
- 逐帧的**代理姿态变换一次性对全模型做完**，不做"按需变换"。后者听起来更省——被拒绝的桶背后那些 shape 根本不用变换——但实测更慢（`305 µs` 对 `288 µs`）：一个碰撞体被所有能碰到它的段共享，于是"要不要跳过"这个世代判断要按**配对**执行几千次，换来的只是几百个 shape 不变换；它还会摧毁保守推进依赖的运动上界，因为上一帧没人需要的 shape 报不出"一帧内的位移"，只能退化成无穷。这两项加起来超过它省下的变换；
- 烘焙期**不设任何数量上限**：一个段能碰到的 cube 全部附着，碰撞面因此与模型完全一致，而不是人为挑出的一小撮。只做两类必要剔除：厚度不足 `0.5 px` 的 cube 无法与末端球形成稳定接触（只会左右抖），以及末端摆动锥完全够不到的 cube。可达性用 `SwingCone` 判定——末端恒定位于半径为力臂的球壳上、且被摆角限制在静止方向附近的一顶球冠内，因此“半径合适但在身后”的几何直接出局，比单纯的扫掠球紧得多。参考骨自身也会转动，故按 `0.35 rad` 弦长为远离参考枢轴的 cube 补偿余量。普通候选的枢轴已深埋在内部超过 `1 px` 时仍会丢弃；与测得身体地标重叠的刚性 cube 例外，因为裙根 pivot 本来就常埋在腰部网格内，实际是否可用继续由末端摆动锥决定。`winefox` 由此得到 `3234` 个代理覆盖 `80` 个受驱动段，远处灯笼、武器仍不进入热路径；
- 网格 Box 只认刚性骨，因此两片同为受驱动的布料互相穿过时它无能为力——围裙压在裙面上、外褂压在裙撑上都属此类，双方都在动，谁也不是对方的碰撞体。[`ClothLayerPlanner`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/build/ClothLayerPlanner.java) 专门补这一层，并且**只单向成对**：外层把内层当碰撞体，内层完全看不见外层，既保住作者排定的层序，也杜绝两片软布每帧互推形成震荡。识别条件全部来自静止几何——两块 cube 都是薄板（最薄轴不超过次薄轴的 `0.5`）、法线夹角在 `20°` 内、相对躯干轴同侧、沿法线错开量超过较薄一方厚度的 `40%`（低于此值是同一张布拆成多骨，没有层序可言）且两片之间的净空气不超过 `3 px`，面内两轴的交叠还要都达到较窄一方的 `35%`。若两根骨互为对方内衬（绕过胯部的裙片就会这样），则判定无层序，双向丢弃。同一块外层布若同时覆盖多条动态内衬链，只保留面内中心最近的一条；该链上的连续骨段仍全部保留，从而让长围裙跟随分节裙面，同时避免左右裙片和围裙自身分支从不同方向夹持同一物理段；
- 层碰撞体**就是内衬那块 cube 的原始尺寸**，只是拆掉了朝内的那一面：`1 px` 的实心薄片拦不住一帧就能越过它的末端，穿到另一侧后反而报告“无接触”，看起来就是直接跳了过去；半开棱柱没有背面可落，末端无论陷多深都只能从正面推出。此前的做法是把内衬向内加厚 `4 px`，代价是调试叠加里的碰撞盒明显大于模型本身，现已不需要。判定量与推出方向必须是同一套度量，否则“擦着边”的微小交叠会被按正面深度推开而炸出一次大摆；
- 层碰撞的末端半径取**外层布片自身的半厚**，而不是从受驱动骨整体比例推导：后者对一片 `1 px` 的布会给出接近 `2 px` 的球，把两层顶开一道肉眼可见的缝。这个半厚是必要的外扩，它让外层的**表面**而不是骨轴落在下层布面上；
- 刚性网格的末端半径则是纯数值容差（`0.3 px`，上限为碰撞体最薄半轴的 `15%`），因为末端是骨轴上的点、网格相对该轴的位置未知，测量骨骼无法回答"轴停在离表面多远才算贴合"。此前按受驱动骨最薄边的一半推导，实测每一对都由那个 `15%` 上限胜出，末端半径于是只与碰撞体最薄半轴成正比而与布片厚度无关：同一片裙子贴躯干被顶开 `0.675 px`、贴 `0.3 px` 薄饰片只有 `0.045 px`，越厚的碰撞体把布料悬浮得越远；
- 调试叠加把线框画在**代理自身几何**上、不叠加末端半径，因此紫色 Box 与品红层碰撞体应当与青色 cube 描边重合。叠加末端半径会让每个代理都显示成"大了一圈"，包括布料层那些完全正确的外扩；
- schema 3 显式代理追加在自动代理之后；`collision.auto:false` 同时关闭网格 Box、层碰撞与其余自动代理，只保留有效显式代理。每个代理 reference 及其祖先都会加入最小活动骨架，包含位于受驱动骨之后的 sibling reference；
- 每帧在任何物理偏转写回前，先捕获带代理段、实际碰撞 reference 及这些节点祖先的动画仿射增量。受驱动的 reference 是唯一例外：它的动画姿态并不是网格实际所在的位置，因此改用 solver 上一趟写出的变换。滞后一帧在布料上看不出来，换来的是预捕获仍是单趟 prepass，而不必在节点循环中途重入刷新；逐段 runtime pivot 与 segment length 包含父物理、position 补偿及非均匀 scale，碰撞投影使用实际缩放后的段长；全几何 safety lever 另按层级保守 scale 收紧末端位移角限。Plane 法线使用 inverse-transpose，Sphere / Capsule 半径使用仿射变换的保守谱尺度上界，在正交轴缩放时精确、出现剪切时不低估；Box 的三条轴各自随仿射变换旋转，轴长直接乘进对应半轴，因此非均匀缩放的肢体仍被精确跟随；
- 数量放开后成本由六层结构而不是上限来控制：
  - **共享形状**：同一个 cube 常被几十个段引用。[`PreparedCollisionShape`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/runtime/PreparedCollisionShape.java) 按“形状 + 参考骨 + 几何”去重，每帧只做一次仿射/法线变换，代理本身退化为“枢轴 + 力臂 + 静止宽限”的轻量绑定。`3234` 个代理背后只有几十个唯一形状，矩阵变换量下降一个数量级；
  - **逐帧锥剪枝**：代理按参考骨分组，同组几何刚性相连，因此其静止包围球随该骨变换后依然有效，一次测试即可否掉整根骨。存活组内再逐个用 `SwingCone` 判定；绝大多数 cube 只花一次平方距离——末端恒在力臂球壳上，偏离球壳超过自身半径的直接返回不可达，无需开方与锥角运算；
  - **组内空间细分**：只按参考骨分组不够，因为一根骨的 cube 可以铺满整个身体：`BaseHair` 的 `59` 个 cube 罩住整个头，包围球大到否不掉任何东西，于是每帧为这 `59` 个逐一付费。分组因此沿最长轴继续二分到每组不超过 `8` 个，让帧循环有足够紧的球去否。细分只决定“测谁”，不决定“测出什么”，[`CullBucketSubdivisionVerification`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/CullBucketSubdivisionVerification.java) 同时钉住两件事：末端交叠的碰撞体一个都不能被剔掉，远处的一个都不能进来；
  - **Top-K 求解**：末端球一次只可能被少数几个面挡住，而松弛循环每轮要重扫全集，因此每帧只把最近的 `6` 个送进求解。裙摆被躯干和两条腿同时夹住时，光是这一处就已经不止三个面，再叠一层围裙就更多；上限过低看起来就是末端一边解某个面、一边无视另一个面。排序每帧按实时姿态重做，没有任何代理被永久丢弃，且松弛循环一旦一轮无修正就退出，空槽只花一次拒绝；
  - **绑定推迟到排序之后**：绑定要重算碰撞体缩放、两份宽限以及由宽限派生的半径，而排序只用得到可达性判定。因此绑定不再对“扫掠体恰好罩住”的每个代理都做一遍（`winefox` 上每帧 `685` 次），只对最终留下的那几个做；
  - **间隙留存**（保守推进）：投影为了判断该不该推，本来就得先算出间隙，读回来不花钱；而在两侧相向移动量把它耗尽之前，这个间隙都还成立。于是一次测量能顶掉后续若干轮乃至若干帧的求解——实测末端采样有 `70%` 直接跳过整段几何。留存量按“自测量以来所有可能缩短它的运动”扣减：段自身的转动（力臂乘转角）、段身采样点沿段轴的滑动、碰撞体在动画下的位移（[`PreparedCollisionShape`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/runtime/PreparedCollisionShape.java) 给出的表面点位移上界：中心位移加三条半轴端点位移，覆盖任意角点）、枢轴移动、缩放变化，以及宽限释放导致的有效半径回涨。任何一项漏扣都是漏检，而漏检的表现正是布料坐视肢体穿过去，因此由 [`SweptContactVerification`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/SweptContactVerification.java) 兜底：把跨帧那笔账整体抹掉，响应会从 `0.17` 塌到 `0.0018 rad`。被剔除一帧的配对没人给它记账，所以按帧序号判定不连续即作废，这比在剔除路径上逐个通知便宜；
- 逐帧重测作者姿态深度（相对宽限）曾是这条路径上最贵的一项：它对每个存活配对每帧算一次完整 clearance，在 `winefox` 上摊到 `+158 µs/frame`。现在先用一次包围球判定挡住——排序留下的是**离偏转后末端**最近的碰撞体，而作者方向通常并不指向那里，绝大多数配对根本够不到，无需进入 Box 局部空间。同一个判定也服务于投影本身；
- [`RuntimeCollisionCache`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/runtime/RuntimeCollisionCache.java) 每帧只更新碰撞依赖节点，并把静止几何变换到运行时一次；四轮摆角/碰撞交替投影复用同一结果，不在每轮重复矩阵变换。生产热路径仍为 `0 B/frame`；
- Box 在参考骨局部空间做一次点-盒最近点求解：外部按 clamp 结果取面/棱/角，内部按最浅轴逃逸，随后复用与 Plane 相同的半空间投影，因此不需要迭代搜索，单个 Box 的成本与一个 Plane 同量级；
- Capsule 使用线段最近点与有限次半空间投影，线段退化为点时走解析 Sphere 路径，端点位于轴线时使用确定性回退法线；两个活动 Plane 形成狭窄可行域时直接求边界交线，避免顺序投影在近切或近反向法线下慢收敛；
- 自动代理会拒绝受驱动 reference、受驱动祖先及由受驱动分支贡献的拟合边界，避免碰撞体被同一物理链反向拖动；代理对象、准备态和 scratch 均由 solver 持久复用。验证覆盖四种形状、单骨多代理、胶囊退化、Box 面/角投影、近切/近反向 Plane、准备态与直接投影等价、reset 以及通用多代理路径 `0 B/frame`。

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

Mixin 同时把 `tail/default` 使用的 `tickCount + partialTick` 时间源传给 `AnimationTimelineClock`，但在相加前保留整数 tick 的 `double` 精度，避免长时间存活实体因 `float` 尾数不足丢失渲染帧。同一姿态若因轮廓、额外渲染阶段或其它重复调用进入多次，首次调用按动画时间差推进，后续调用使用 `dt=0`；不再把 0.1～1 ms 的墙钟间隔误当成额外物理子步。该误差在单骨上很小，但会沿 `Tail → Tail7` 的父子变换累积并主要暴露在末端。

慢速动画还会暴露与速度无关的姿态量化：若用 `acos(rest · current)` 提取物理偏角，两个近乎平行的 `float` 单位向量会把点积舍入为 `1`，导致真实偏转连续增长时画面先保持零、跨过精度台阶后再突然跳变。写回现在直接使用同时包含一阶小角信息的 `atan2(|rest × current|, rest · current)` 计算姿态误差；偏角轴和参考骨旋转只过滤接近浮点退化范围的零量，不再用仿真级 `epsilon` 截断真实微角度。输入侧的移动与动画加速度仍由噪声门和低通滤波器调理，因此不会把数值抖动重新注入姿态。该修正逐段生效，也避免年糕狐 `FoxTailA → Body_Tail6` 六段尾巴把局部量化放大到末端。

## 调试工具

- **`/maidphysicsdebug`** → 获得**骨骼调试棒**(带 NBT 的原版木棍,无需注册物品)。
- **手持** → [`MaidSkeletonDebugLayer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidSkeletonDebugLayer.java) 在所有女仆身上画骨架：灰色小十字 = authored pivot；黄/紫大十字 = 自动/元数据驱动骨骼的有效 pivot；红色大十字 = 支撑端仍有歧义、已自动收紧摆角；橙线 = 推断后的物理骨轴；RGB 线 = Gecko 模型坐标轴；青线 = cube 线框。碰撞层另画亮青色 runtime segment、蓝色 Plane、绿色 Sphere、琥珀色 Capsule、紫色网格 Box，以及与代理同色的 reference origin 十字；调试层只画本帧实际进入求解的代理（逐段最近的若干个），而不是全部候选，因此看到的就是 solver 真正在用的碰撞面；几十个段共用同一刚性碰撞体时也只画一次，避免同一个盒被叠画十几遍。当前末端对该代理仍为负 clearance 时，整组代理改画红色并单独重绘。
- **Shift + 右键女仆** → [`PhysicsDisplacementLog`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsDisplacementLog.java) 绑定该女仆，之后每 0.1 秒把所有受驱动段相对弹簧平衡姿态的位移（模型像素）写入后台日志，带骨骼名、积分步长、投影步长、推动它的约束（`swing`/`coll`/`both`）、接触支撑与阻尼；静止段只计数不列行。再次 Shift + 右键解除绑定。度量基准是**弹簧平衡姿态**而非 bind pose——坐姿动画本身会把裙摆骨旋转 65°，从 bind pose 量会把动画本身算成物理位移。这是台架审计无法替代的一环：`MeshPenetrationAudit` 只能按测试写死的关键帧摆姿势，游戏里的实际姿态与之不同时，台架量到 0.1 px 的段在游戏中可能抖得很明显。
- **右键女仆** → [`PhysicsDebugSkeletonDump`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsDebugSkeletonDump.java) 把实际 `modelId`、完整骨架、模型路径、`META/AUTO` 来源、裙摆/饰品结构角色与判定原因、链 ID、段序号/总数、重力/受风/质量倍率、最终四向摆角/参考空间/碰撞策略、主簇选择、segment length、真实链 `jointSpacing`、全几何 safety lever 和置信度写入 `run/logs/latest.log`；同一次 dump 还记录每段 runtime pivot/tip、每个代理的 `AUTOMATIC/EXPLICIT` 来源、reference、运行时几何、形状/末端半径、缩放后力臂、clearance 和穿透标记，并在聊天栏汇总骨骼、代理和穿透数量。右键只 dump、不触发其它交互。

## 调参

基础解算参数集中在 [`SpringBoneMath`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/SpringBoneMath.java) 顶部，动画导数门控/滤波位于 `AnimationMotionSample` 和 `AnimationInertiaPolicy`，时钟、24 格门控和诊断窗口位于 [`MaidBonePhysics`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java):

| 参数 | 作用 |
| --- | --- |
| `STIFFNESS` | 拉回动画姿态的刚度；实际恢复加速度按 `stiffness / max(1, mass)` 计算 |
| `mass_scale` | 逐骨有效质量；`> 1` 越慢越下垂并低通掉高频阵风，`< 1` 不会超过作者刚度基准 |
| `GRAVITY_POWER` | 重力加速度；不乘质量，静态垂量由重力与 `k/m` 共同决定 |
| `DRAG` | 惯性阻尼(0–1),越大越快停摆 |
| `INERTIA_GAIN` / `TURN_GAIN` | 移动 / 转身摆幅 |
| `MAX_ANGLE` / `MAX_TIP_DISPLACEMENT` | 总偏转与末端行程上限，位于 `SwingRange`，四个消费者共用同一来源 |
| `MAX_DEFLECT_*` | 逐轴上限(代替碰撞、防钻入身体) |
| `MIN_CUTOFF` / `*_BETA` | 自适应降噪的静态平滑强度 / 大动作跟随速度，位于 `AdaptiveMotionFilter` |
| `MAX_ACCELERATION` / `FILTER_TIME_CONSTANT` | 预设动画逐骨惯性信号的软上限 / 低通时间常数 |

上述值是全局基准，实际骨骼还会乘以 `HEAD_SHELL` / `HAIR` / `TAIL` / `EAR` / `SKIRT` / `RIBBON` / `CAPE` / `WING` 的默认档位和可选元数据 `profile`。

## 已知限制

- **段身只多取一个采样点，且不覆盖靠根部的 `35%`**：每段除末端外，再对**碰撞体正对着的那个段身位置**投影一次，因此横扫进裙面中段的腿不再毫无阻挡地穿过去（`winefox` 上典型步频的响应从 `0.010` 提升到 `0.17 rad`）。这十几倍的差距说明段身采样不是补充而是**唯一**的检测手段：裙片就挂在作者画的位置，末端落在腿的行程之上，从头到尾碰不到腿，接触全部发生在段的中段。采样点取“段轴上碰撞体最正对的一点”而不是固定的一串位置：那里最先也最深地咬进来，而固定阶梯要为找到同一个位置付出每级一次投影。更靠根部的接触被拉到 `0.35` 处求解而不是原地求解——段绕根部旋转，太靠根的点无论转多少都几乎不动，硬要解出能让它脱离的转角会把整段甩到模型另一侧。所以裙根一带的重叠仍不受约束，这是“绕支点旋转”这个模型的固有结果，不是漏检。

  末端与段身各自持有独立的出口面记忆和静止宽限。共用一份宽限会让段身的作者交叠（裙片本来就压在它挂着的胯上）把末端的阈值一起顶高，段于是对任何浅于它已陷入深度的东西都不再抵抗，实测响应会掉回原来的量级。无限平面不参与段身采样：它没有“中心”可正对，而且作为半空间，直段的末端脱离即全段脱离。厚度不足 `0.5 px` 的薄片仍不参与碰撞。

  剔除层的口径也仍以末端为准：可达性建立在“末端恒位于半径为力臂的球壳上”，`SwingCone` 会把偏离球壳超过自身半径的碰撞体判为不可达，**包括离支点太近的**。段身投影用的是同一批存活代理，因此这条剔除的口径决定了段身能看到什么。
- **布料层碰撞管不了绕过边缘**：半开棱柱在法线方向没有背面，因此层间漂移无论多深都能被推回，但外层若整片摆出内衬的面内轮廓（从裙侧甩到裙外），末端就不再压在那块布上，此后只按侧面距离约束。层碰撞保证“压着不陷进去”，不保证“绕过去还能拉回来”。
- **作者已交叠的部位不会被推开**：自动代理逐帧跟踪当前作者姿态造成的不可避免重叠，并只允许到该深度。发根、坐下的裙摆、被抱住压扁的衣角都不会突然弹出，风、重力和惯性仍不能继续压入。代价是动画自己把部件塞进碰撞体时系统不会纠正——那是作者的剪影，不是待修复的穿模。区分“落进去”和“扫过去”的判据是速率（`2 px/s`）：慢到这个程度的姿态变化一律按作者意图吸收，因此**极慢的**穿插动作依旧不会被纠正。
- **某些步频下腿与裙片同步而没有接触**：裙片是弹簧，腿是驱动源，两者频率接近时裙片跟着腿走、相对位移趋近于零，此时没有任何穿透需要解决（`winefox` 在 `0.5 s` 步幅附近就是这样）。这不是碰撞失效，但意味着“被踢开的幅度”不能拿单一步频去衡量——[`SweptContactVerification`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/SweptContactVerification.java) 因此横扫多个步频取最大值。
- **硬约束而非 XPBD**：当前摆角和四种碰撞代理使用固定骨长下的硬 PBD 投影；只有出现明确的链间柔性距离需求时才按需加入 XPBD compliance。
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

离线校验 schema 1/2 不生成自动代理、schema 3 自动派生网格 Box（裙摆拿到躯干与左右腿、发丝拿到头部且长头骨的半轴与 cube 精确一致）、`collision.auto:false` 关闭全部自动碰撞、不可达的远处刚性骨不进入计划、扁平发光层不会成为碰撞体、受驱动骨永不成为网格碰撞参考、多 cube 参考骨的可达 cube 一个不漏地全部附着、叠层布料只由外层单向约束（共面并排与互相环抱的两片都不成层，半开内衬能挡住向内压的外层，且内衬动画瞬间归零时碰撞体仍停在解算姿态）、`winefox` 与 `zhiban_hanfu` 的每一个 Box 半轴都能对上参考骨的某个真实 cube、把发丝驱入头骨时 Box 精确停在表面而关闭碰撞则直接穿入、腿横扫过 `winefox` 裙面时布料确实被驱离静止姿态（横扫多个步频取最大位移，避免落在腿与裙片同步而本就无接触的频率上；关掉段身采样、让宽限不再衰减、或按单次反向就降响应，三者各自都会使其失败）、显式 Head/Leg 引用仍可用、四种代理几何和胶囊退化、Box 面/角投影、自动与显式布局、父骨偏转、后序显式 Leg reference、完整 affine、非均匀 scale/剪切尺度与缩放后碰撞力臂、近切/近反向 Plane、自动 reference 安全、准备态/直接投影等价、reset、20/30/60/120 FPS 不变量及约束路径 `0 B/frame`。环境风另覆盖天气/海拔强度排序、维度基值、邻近空间一致性、室内和浸水衰减、软限幅、30/60/120 FPS 近似一致、`wind_scale` 边界、旧无风重载等价及风场热路径 `0 B/frame`；去同步部分验证不同实体的高频脉动相关性低于 `0.6` 而平均风向仍同向、逐实体响应时间确有差异、同名骨在相同风下不会同步摆动、无风时不产生任何逐骨差异、四段链的阵风确实以约 16 帧延迟从根传到梢而逐帧对齐时并不相似（关掉传播延迟即失败），以及 `dt=0` 重复渲染不推进抖振相位；质量部分验证 2 Hz 阵风下轻部件跟随幅度高于重部件 20% 以上。动画惯性另覆盖静止姿态严格零力、关键帧 position/rotation/scale 均能产生有界信号、30/60/120 FPS 峰值一致性、动画切换/暂停/恢复零假冲量，以及 TLM `tail/default` 在控制器限流期间继续更新时的六通道姿态恢复和连续物理覆盖；实际 `winefox` 七段 `Tail → Tail7` 还会在持续风中用每帧 0～2 次重复渲染验证相同动画时间只推进一次、末端位移与局部旋转无累计跳变，年糕狐 `FoxTailA → Body_Tail6` 则以慢速正弦目标叠加持续风，验证微小姿态误差无零值台阶、局部旋转步长及步长变化有界。同时覆盖接触枢轴的 `1～2 px` 间隙、宽面与单骨包头 Head Shell、年糕狐 `HairFemaleK_Matching` 的零重力低惯性档及独立刘海/马尾保留、横穿支撑体的歧义接触、远程 authored pivot、真实多骨链、OBB 主簇、微型单 cube 支撑修正、匿名复合单骨附件、歧义上生悬臂、纸板狐华服 `HUDIEJIE` 上移、`bone101/bone103` 的稳定 authored pivot 保护，以及 `bone109` 的紧凑外围支点和身份补偿。全部 27 个内置模型还会扫描每个单骨/链末端的 `axis · (visibleMassCenter - effectivePivot)`，禁止物理轴明确背离可见主体，并验证 bind pose、首次动画姿态及 reset 后的 `dt=0` rotation/position 完全不变。`dt=0` 只能证明“还没积分时不写回”，而重力沉降、投影把部件走出作者交叠这类恒定影响全都要靠时间才显形，因此同一批模型另外**静止十秒**（`600` 帧真实步长、无风无运动），要求每一根受驱动段的方向偏离作者方向不超过 `0.01 rad`。该检查比较方向向量而不是写回的欧拉角：解算器写的是由方向分解出的三元组，并不唯一，作者姿态接近半圈的骨会以另一种分解回来（`(0.013, -3.1506, 0)` 与 `(-3.1286, 0.009, 3.1416)` 是同一朝向却逐通道全不相同）。[`GravityRestPoseVerification`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/GravityRestPoseVerification.java) 另用四种作者朝向（下垂、横向、上举、斜向）单独钉住重力：下垂骨与世界重力共线，即使按世界下方施加恒力也不会动，因此只有横向和上举的合成夹具才能暴露回归；同一处还验证重力并没有被取消——被抬起后释放的横向段必须落到作者轴线**以下**，纯沿轴的恢复力做不到这一点。无主导连通簇的自动候选保持刚性、显式 metadata 可覆盖；裙摆、面具和汉服/新年左右 `MWX` 三段继续验证 authored/effective/runtime 关节分离、端点重合和单次冲量相对响应。
