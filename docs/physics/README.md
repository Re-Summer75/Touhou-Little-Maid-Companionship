# 女仆骨骼物理（VRM 弹簧骨）

为 Gecko 女仆的尾巴、头发、耳朵等自由摆动骨骼添加一层二级动作物理：跟随身体运动产生真实惯性摆动、带轻微重力下垂，同时**收敛回动画姿态，并以非对称摆角和保守碰撞代理减少过摆与常见穿模**。实现参考 VRM `VRMC_springBone` 弹簧骨模型。

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
  3. **外力**：`(小重力 + 移动惯性 + 转身伪力) × dt`；
  4. 归一化到单位长度。

因为刚度目标是**动画姿态**而非世界下方、重力只是小外力，所以:

- **静止只微垂**：平衡在"刚度 = 重力"处，垂量 ≈ `GRAVITY_POWER / STIFFNESS`（约 8°），有界不发散；
- **抬头头发跟随**：`restDir` 随头骨动画一起抬，参考空间搬运先同步历史方向，非对称摆角与头部代理再阻止向内倒和穿入；
- **运动才甩**：转身/起步/急停/跳跃的瞬态惯性驱动摆动，然后弹回动画姿态。

## 最小活动骨架上的逐骨骼解算

[`PhysicsSolverLayout`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/PhysicsSolverLayout.java) 在模型准备阶段构建“全部 driven 骨、模拟/碰撞参考骨及其到根路径的并集”。这个最小活动子树只保留受驱动骨和传播动画或参考朝向所必需的祖先；无关手臂、武器和装饰分支不会进入每帧热路径。

[`SpringBoneSolver`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SpringBoneSolver.java) 按预序扁平数组迭代解算，不再递归完整骨骼树，也不在热路径查询 `IdentityHashMap`。祖先节点只传播动画姿态；driven 节点先以动画姿态积分并施加自身偏转，再把施加后的朝向传给子骨，因此与旧完整树递归的父子语义一致。

生产约束路径把模拟方向 `currentDir` 转回局部，求从休息轴到合法方向的轴角四元数，与动画局部四元数相乘后再转换为 Gecko 的 ZYX 欧拉值；因此渲染方向与投影状态一致。无约束旧行为只保留在 reference 等价基准通道。

### 解算器模块边界

`SpringBoneSolver` 只保留稳定公共 API，实际实现位于独立
[`solver/spring`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring)
子包；门面和每个内部模块均不超过 200 行：

- `SpringBoneEngine` / `SpringBoneFrameRunner` 负责编排生命周期和活动骨架预序遍历；
- `SpringBoneState`、`SpringBoneScratch`、`SpringBoneMetrics` 分别拥有持久状态、零分配 scratch 和诊断计数；
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

具体实现集中在 `client/discovery` 子包：元数据绑定、候选评分、头部/躯干规则、刚性过滤和计划写入彼此独立；`discovery/structure` 另外负责镜像分支、同 pivot 重叠、紧凑底座、长单骨网格和真实链段拓扑。入口 [`PhysicsBoneDiscoverer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneDiscoverer.java) 只保留稳定门面。

自动发现采用高置信门槛，低置信节点保持静止，避免把肢体、武器和固定头饰当作软体。空骨骼仍作为层级枢轴参与分析但不直接驱动，其语义会沿连续空锚点传给可见子骨。可见的 `MFrontHair` 不会再被当作空 `M` 枢轴；不在 `Head` 层级内、但名称或空间挂点明确属于头发的骨骼也会参与头部候选评分，同时保留已经高置信识别出的翅膀、裙摆等身体软体类型。`BaseHair` / `TopHair` 这类包围头部、有实体几何且连接发丝的分叉骨会识别为 `HEAD_SHELL`，使用高刚度、低摆幅参数；纯空分叉容器则跳过。

刘海会额外识别 `Bangs`、`Fringe`、`FrontHair`、`刘海`、`前髪` 等别名；`MBangs`、`LongHair` 这类空锚点即使跨越多级容器或分出多个匿名片段，也会把高置信语义传给所有可见分支。完全匿名的前额薄片、成组小片和连接头发外壳的头顶发束可通过几何与层级关系识别。眉毛、眼、嘴、脸红和表情使用强排除语义，覆盖 `meimao`、`zui`、`xiao`、`saihong`、`lianhong` 等内置模型别名；匿名但呈现为面部下半区对称薄贴片的腮红也会被刚性过滤。即使这些面部组件位于 `Hair` 层级内或名称同时含有 `Hair`，也不会继承头发物理。`Face_Bangs` 这类明确刘海名称仍可正常入选。

仓库内的 [`geckolib_model_reference`](../../geckolib_model_reference/README.md) 保存本体内置的全部 27 个 Gecko 几何模型并参与离线回归。圣女酒狐的匿名 `BaseHair/bone5` 由“前额位置 + 头发外壳直属叶节点”识别；这类旋转多方块刘海的整体 AABB 可能横跨整个额头且看起来不够薄，因此宽度不再被误当成长发长度，直属头发外壳的结构证据也会补偿整体 AABB 的厚度偏差。基础纸板狐现在固定验证匿名腮红 `bone53` 刚性、同 pivot 重叠的 `bone29/30/27/31` 发饰刚性、镜像长网格 `bone3/bone9` 作为左右单骨马尾入选，以及头发外壳顶部的匿名呆毛 `bone34` 保持柔性。

昂贵的完整发现结果按不可变 `GeoModel` 身份弱缓存，并按 `modelId` 分区。模板以共享 `GeoBone` 身份保存 `Decision`、路径和静态运动学；同一 `GeoModel` 创建新的 `AnimatedGeoModel` 时只需一次 O(N) live bone 绑定，仍会生成实例隔离的 [`PhysicsBoneSelectionPlan`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneSelectionPlan.java)，不会跨实体保存 `AnimatedGeoBone`。

女仆切换模型会重建实体自己的 layout 和 solver runtime；实体离开客户端世界会立即遗忘状态；F3+T 与 TLM 自定义模型包热加载会同时清空 sidecar、实例计划、`GeoModel` 模板和实体 runtime。

## 发饰独立与真实链段

- `RIGID_ATTACHMENT_BASE`：命名发夹/发球，以及具有紧凑、同 pivot 重叠或连接远端柔性后代等强结构证据的匿名底座，只跟随动画，不进入 solver；它不会把细长或悬垂后代一起排除。明确的 `Fringe/Hair/Pony` 名称和 metadata 选择优先保留柔性，防止把刘海误判为附件。
- `FLEXIBLE_CHAIN_SEGMENT`：真实 parent-child driven 链会烘焙链根、段序号和段总数。仅自动发现链使用根到梢曲线：根段刚度/阻尼更高、参考跟随更强、摆角更小；梢端刚度/阻尼更低、移动/转身与旋转惯性更高，从而在父偏转正确传播的同时产生相对相位差，不再像整条刚体。
- `COMPOUND_SINGLE_BONE`：基础纸板狐 `bone3/bone9`、年糕狐 `LeftPony/RightPony` 这类所有 cube 只绑定一个 bone 的长网格无法产生真实弯折；系统保留整体小幅摆动，但使用更高刚度/阻尼和更严格的角度、末端位移档位，避免连同发扎大幅扫动。真正分段必须由模型提供多个不同 pivot 的父子骨。
- 运动学会在存在明确主连通 cube 簇时，用 OBB/SAT 连通判定后的主簇推断 attachment pivot/axis 和实际 segment length，同时用全部 cube 计算独立的安全力臂；远离主体的小装饰不会拉偏旋转轴或把碰撞端点拉成虚假长段，也不会从摆幅安全包络中消失。以上全部在模型/layout 构建期完成，不增加逐帧分配。
- schema sidecar 的显式链和排除继续拥有最高优先级；自动刚性结论不会覆盖作者指定的 driven bone。

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
        "backstop": true,
        "head_collision": true,
        "hit_radius_scale": 1.0,
        "collision": {
          "auto": true,
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
- `rotation_inertia_scale` 为 `0–1`：`0` 完全跟随参考骨旋转，`1` 保留模型空间旋转惯性;
- `swing_limits` 使用角度制，分别控制左、右、向外和向内最大摆角;
- `constraints.collision` 是 schema 3 字段。`auto` 默认为 `true`，自动代理与 `proxies` 中的显式代理合并，并应用到相关链的每个受驱动段；设为 `false` 时不生成自动代理，只保留有效的显式代理。显式形状支持 Plane（`point` / `normal`）、Sphere（`center` / `radius`）和 Capsule（`start` / `end` / `radius`）。
- 显式代理的位置和形状半径都使用 **Gecko / Bedrock 模型空间像素**，`16 px = 1` 个模型空间方块；`normal` 只是无单位方向。可选 `hit_radius` 也是像素，表示受驱动骨末端的碰撞半径；省略时从该骨几何横截面推断，最终仍会乘 `hit_radius_scale`。
- `reference` 可写 `MODEL`、`ROOT` 或能唯一解析的节点引用。`ROOT` 只在模型恰有一个顶层根时有效；普通引用接受完整路径、唯一的路径后缀或唯一节点名。缺失或歧义引用只跳过对应代理；代理的静止几何按模型空间填写，运行时随所选 reference 的完整仿射变换运动。
- `backstop` / `head_collision` 是保留兼容的旧开关，分别控制自动 Head Plane 和自动 Head Sphere（高纵横比头部改用短 Capsule）；它们不删除 schema 3 的显式代理。
- `schema_version: 1` 的既有 sidecar 在未声明 `constraints` 时保持旧的无约束语义；版本 2 省略 `constraints` 时仍采用部件类型的自动约束默认值。版本 1/2 不读取 schema 3 的 `collision` 字段，旧文件和旧开关行为不变；新文件应使用版本 3。
- F3+T 资源重载及 TLM 直接加载新下载的目录/ZIP 模型包都会刷新 sidecar、选择计划和实体模拟状态。

## 运动信号与门控

- **重力**：模型空间 `(0, −GRAVITY_POWER, 0)`；悬垂部件使用分类型倍率，`HEAD_SHELL` 仅保留近乎为零的重力，避免抬头时整块头盖向后翻;
- **移动惯性**：实体 `getDeltaMovement` 按 20 TPS 游戏 tick 差分并在两个 tick 之间保持，不再向渲染帧插入“尖峰后归零”的假信号；按渲染器 `180° − bodyYaw` 的逆变换转进模型空间，再取反作为滞后力；**着地时丢弃垂直分量**(避免重力/地面钳位每 tick 抖动);
- **转身伪力**：`wrapDegrees(yBodyRot − yBodyRotO)` × 增益,补足纯模型空间捕捉不到的转身惯性;
- **非线性降噪**：移动和转身信号先经过径向软死区与 `tanh` 软限幅，再进入 One Euro 式自适应低通；小抖动使用低截止频率强滤波，快速动作按导数幅度自动提高截止频率;
- **帧率**：真实墙钟 `dt`(clamp 0.1s)；刚度/重力/外力按 dt 缩放，阻尼按 60 FPS 基准做指数换算；暂停或长时间断帧冻结该步，避免恢复瞬间跳动;
- **距离门控**：相机 24 格外跳过并清状态。

## 零分配热路径与性能基准

- 每个活动节点持久复用 rendered quaternion、层级仿射变换和运行时端点，每个实体持久复用积分方向、四元数、碰撞最近点/法线和其它向量 scratch；
- `AdaptiveMotionFilter`、`MotionNoiseGate`、`MotionSignalSampler` 与 pivot 补偿提供 out 参数路径，热路径不创建 `MotionSignals`、force 向量或滤波输出对象；
- [`BonePhysicsVerification`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/BonePhysicsVerification.java) 用测试专用旧递归解算器作为 oracle，对 `winefox` 与匿名模型逐帧比较 rotation、position 和弹簧方向；序列覆盖可变 dt、移动、转身、暂停和 pivot 补偿，容差为 `1e-5`；
- [`BonePhysicsBenchmark`](../../src/test/java/com/laixia/maidintelligence/feature/physics/client/BonePhysicsBenchmark.java) 是独立非门禁入口，充分预热后只围绕 solver 调用报告每帧解算耗时、访问节点数和当前线程分配量，排除测试姿态重置本身的临时对象。绝对耗时受 JVM、CPU 和后台负载影响，不参与 `check` 成败。

最新一次 Windows/JDK 17 开发环境样例（`winefox`，20,000 个测量帧）：

```text
recursive-full: 53500.0 ns/frame, nodes=181/181, allocation=14592.00 B/frame
iterative-active-legacy: 46899.3 ns/frame, nodes=107/181, allocation=0.00 B/frame
iterative-active-constrained: 84982.0 ns/frame, nodes=107/181, allocation=0.00 B/frame
legacy-equivalent solver speedup: 1.14x
constraint-layer cost: 1.81x legacy-active
```

## 运行时端点层级

- [`RuntimeBoneEndpoints`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/RuntimeBoneEndpoints.java) 按最小活动骨架的预序顺序，使用与 Gecko 相同的 position → pivot → ZYX rotation → scale → inverse pivot 顺序组合父子仿射变换；
- 所有活动节点缓存最终模型空间 pivot，驱动节点同时缓存由有效 pivot、骨轴和几何力臂定义的 tip；父骨物理偏转、虚拟枢轴位移补偿和非均匀缩放都会逐层传给后代；
- [`SpringBoneSolver.copyRuntimePivot`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SpringBoneSolver.java) 与 `copyRuntimeTip` 通过 out 参数暴露结果，单位为模型空间方块，首次解算前和 `reset()` 后返回无效；
- 每个受驱动段都独立执行碰撞；其运行时 pivot 包含父骨物理偏转、Gecko position、虚拟枢轴位移补偿和非均匀 scale，最终 pivot/tip 同时供碰撞诊断与调试绘制使用。

## 统一碰撞代理

- [`solver/collision`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision) 提供通用 Plane、Sphere、Capsule 及固定骨长投影；原 Backstop 烘焙为 Plane，原头部碰撞烘焙为 Sphere（高纵横比时为短 Capsule），现有效果和 sidecar 开关保持兼容；
- 每根驱动骨持有有序的不可变 `CollisionProxySet`，可同时关联多个代理；每个代理有独立 `referenceNodeIndex`，因此不同形状可以跟随不同参考骨；
- 自动代理应用于每个相关受驱动段：`HAIR`、`EAR` 和 `HEAD_LOCAL RIBBON` 使用 Head Plane +（Sphere 或高纵横比时的短 Capsule）；`SKIRT` 使用 Body Capsule 和经可靠性验证的左右腿 Capsule；`CAPE` 使用 Body Capsule + Back Plane；`BODY_LOCAL RIBBON` 使用 Body Capsule。左右腿不能唯一、可靠配对时会跳过腿代理，不猜测骨骼；
- schema 3 显式代理追加在自动代理之后；`collision.auto:false` 则仅保留有效显式代理。每个代理 reference 及其祖先都会加入最小活动骨架，包含位于受驱动骨之后的 sibling reference；
- 每帧在任何物理偏转写回前，先捕获带代理段、实际碰撞 reference 及这些节点祖先的动画仿射增量。逐段 runtime pivot 与 segment length 包含父物理、position 补偿及非均匀 scale，碰撞投影使用实际缩放后的段长；全几何 safety lever 另按层级保守 scale 收紧末端位移角限。Plane 法线使用 inverse-transpose，Sphere / Capsule 半径使用仿射变换的保守谱尺度上界，在正交轴缩放时精确、出现剪切时不低估；
- [`RuntimeCollisionCache`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/runtime/RuntimeCollisionCache.java) 为每个代理预分配准备态：每帧只更新上述碰撞依赖节点，并把静止几何变换到运行时一次；四轮摆角/碰撞交替投影复用同一结果，不在每轮重复矩阵变换。这个准备与逐段代理遍历是约束路径相对 `legacy-active` 达到 `1.80x` 的主要可见代价，但生产热路径仍为 `0 B/frame`；
- Capsule 使用线段最近点与有限次半空间投影，线段退化为点时走解析 Sphere 路径，端点位于轴线时使用确定性回退法线；两个活动 Plane 形成狭窄可行域时直接求边界交线，避免顺序投影在近切或近反向法线下慢收敛；
- 自动代理会拒绝受驱动 reference、受驱动祖先及由受驱动分支贡献的拟合边界，避免碰撞体被同一物理链反向拖动；代理对象、准备态和 scratch 均由 solver 持久复用。验证覆盖三种形状、单骨三个代理、胶囊退化、近切/近反向 Plane、准备态与直接投影等价、reset 以及通用多代理路径 `0 B/frame`。

## 异常枢轴修正

第三方 Gecko 模型使用的 Bedrock JSON，其 pivot 可能远离网格、落在几何中心，甚至位于柔性部件的末端。直接采用作者 pivot 会导致旋转原点错误；简单固定使用网格上沿又会把向上的呆毛和横向双马尾判反。当前解算器会在 Gecko 已转换的坐标系中测量实际 cube 顶点、几何主轴和父级实体几何：

- `HEAD_SHELL` 使用网格自身中心作为保守虚拟枢轴；
- 其它柔性部件把竖直端点和几何主轴端点作为候选“支撑点”，首先比较它们到父级/最近实体祖先表面的距离；
- 对支撑距离接近的头发、裙摆和披风，重力稳定性会选择质心上方的悬挂端；若父级表面明确位于部件下方（例如上翘呆毛），则改选下端。贴近网格的直接空锚点只提供次级加权，不能覆盖明确的支撑关系；
- `Bangs`、`FrontHair`、`HairFront` 等前发若与头部高度范围重叠，固定采用上方悬挂端，避免头部前表面的微小距离差把原点拉到刘海末端；完全位于支撑体上方时仍按下方接触端处理；
- 支撑距离按每个旋转 cube 的实际平行六面体表面计算，而不是只看可能包含大片空区的总 AABB；若两个支撑端评分仍接近，则保留原 authored pivot、标记低置信度并自动收紧摆角；
- authored pivot 若远离网格、位于远端，或明显比推断附着端更远离父级实体，就改用附着端；空锚点 pivot 不会覆盖可用的父级实体几何证据；
- 骨轴始终从附着端指向柔性几何远端，而且轴极性独立于是否需要替换 pivot；因此向上呆毛、下垂长发、横向发束以及共享/重叠 pivot 的续段都可得到稳定方向；
- 实际骨骼 pivot 不可修改，因此在施加旋转时同步计算位移补偿；补偿包含当前动画旋转和非均匀缩放，并在碰撞布局中使用与 Gecko 渲染顺序一致的绑定姿态仿射矩阵；
- 枢轴可信度低的骨骼额外采用更小角度上限，防止小角度经超长力臂放大。

## 注入点（关键的累积 bug 修复）

[`GeckoMaidEntityBonePhysicsMixin`](../../src/main/java/com/laixia/maidintelligence/mixin/GeckoMaidEntityBonePhysicsMixin.java) 以 `@Inject(at=@At("RETURN"))` 挂在 `GeckoMaidEntity.setCustomAnimations`,**且仅当返回值为 `true`(动画确实重摆了骨骼)时才解算**。

原因:`setCustomAnimations` 有帧率限制器,被限流的帧提前 `return false`、不重置骨骼;若在这些帧仍施加偏转,会叠加在上一帧已偏转的骨骼上无限累积(实测站定时尾巴曾绕到 7000°+)。只在 `true` 帧解算即可根除。

## 调试工具

- **`/maidphysicsdebug`** → 获得**骨骼调试棒**(带 NBT 的原版木棍,无需注册物品)。
- **手持** → [`MaidSkeletonDebugLayer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidSkeletonDebugLayer.java) 在所有女仆身上画骨架：灰色小十字 = authored pivot；黄/紫大十字 = 自动/元数据驱动骨骼的有效 pivot；红色大十字 = 支撑端仍有歧义、已自动收紧摆角；橙线 = 推断后的物理骨轴；RGB 线 = Gecko 模型坐标轴；青线 = cube 线框。碰撞层另画亮青色 runtime segment、蓝色 Plane、绿色 Sphere、琥珀色 Capsule，以及与代理同色的 reference origin 十字；当前末端对该代理仍为负 clearance 时，整组代理改画红色。
- **右键女仆** → [`PhysicsDebugSkeletonDump`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsDebugSkeletonDump.java) 把完整骨架、模型路径、`META/AUTO` 来源、结构角色、链 ID、段序号/总数、最终 profile、主簇选择、segment length、全几何 safety lever、置信度和入选/拒绝原因写入 `run/logs/latest.log`；同一次 dump 还记录每段 runtime pivot/tip、每个代理的 `AUTOMATIC/EXPLICIT` 来源、reference、运行时几何、形状/末端半径、缩放后力臂、clearance 和穿透标记，并在聊天栏汇总骨骼、代理和穿透数量。右键只 dump、不触发其它交互。

## 调参

解算参数集中在 [`SpringBoneMath`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/SpringBoneMath.java) 顶部，时钟、24 格门控和诊断窗口位于 [`MaidBonePhysics`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java):

| 参数 | 作用 |
| --- | --- |
| `STIFFNESS` | 拉回动画姿态的刚度;越大越硬、垂量/摆幅越小 |
| `GRAVITY_POWER` | 重力强度;静止垂量 ≈ `GRAVITY_POWER / STIFFNESS` |
| `DRAG` | 惯性阻尼(0–1),越大越快停摆 |
| `INERTIA_GAIN` / `TURN_GAIN` | 移动 / 转身摆幅 |
| `MAX_ANGLE` / `MAX_DEFLECT_*` | 总偏转与逐轴上限(代替碰撞、防钻入身体) |
| `MAX_TIP_DISPLACEMENT` | 力臂归一化的允许末端位移;越小大块越收敛 |
| `MIN_CUTOFF` / `*_BETA` | 自适应降噪的静态平滑强度 / 大动作跟随速度，位于 `AdaptiveMotionFilter` |

上述值是全局基准，实际骨骼还会乘以 `HEAD_SHELL` / `HAIR` / `TAIL` / `EAR` / `SKIRT` / `RIBBON` / `CAPE` / `WING` 的默认档位和可选元数据 `profile`。

## 已知限制

- **代理不是精确网格碰撞**：自动 Plane / Sphere / Capsule 来自静止 AABB 与拓扑启发式，只约束每段的末端球，不能表达任意第三方网格、凹面或整块渲染几何；它能减少常见穿模，但不保证完全无穿模。复杂模型应使用 schema 3 显式代理校正自动结果。
- **腿部歧义会保守跳过**：只有左右腿能唯一且可靠配对时，`SKIRT` 才获得双腿 Capsule；双骨架、重名或几何歧义时不猜测，仍保留可用的 Body Capsule。
- **硬约束而非 XPBD**：当前摆角和三种碰撞代理使用固定骨长下的硬 PBD 投影；只有出现明确的链间柔性距离需求时才按需加入 XPBD compliance。
- **单骨网格不能真实分段**：同一 Gecko bone 的所有 cube 共享一次 rotation/position 写回。系统可修正主簇枢轴并保守限幅，但不会用渲染劫持伪造与碰撞、端点和 Sodium 路径不一致的局部弯曲。
- **转动惯量**:力臂归一化压的是幅度;更真实的"大块又慢又沉"可再按力臂缩放驱动力(转动惯量 ∝ 力臂²)。
- **自动发现是保守启发式**：几何无法无歧义地区分造型相似的发丝、丝带和固定装饰；低置信节点默认不动，复杂模型建议提供 sidecar。
- **范围**:仅 Gecko;Bedrock(`BedrockPart` + JS 脚本)与 YSM(仅捕获顶点)暂不支持。

## 验证

```bash
./gradlew.bat --offline verifyBonePhysics
./gradlew.bat --offline benchmarkBonePhysics
./gradlew.bat --offline cleanTest check
```

离线校验 schema 1/2 的头部根段兼容与 schema 3 解析、三种代理几何和胶囊退化、双骨架/歧义腿排除、自动与显式布局、每个受驱动段的代理、父骨偏转、后序 Leg reference、完整 affine、非均匀 scale/剪切尺度与缩放后碰撞力臂、近切/近反向 Plane、自动 reference 安全、准备态/直接投影等价、reset、20/30/60/120 FPS 不变量及约束路径 `0 B/frame`；同时覆盖纸板狐匿名发饰/镜像单骨马尾、年糕狐发球、真实多骨链段序号与根梢参数单调性、单次冲量相对无输入对照的非零段间响应、OBB/SAT 主簇、旋转零厚度平面、退化线排除、全几何安全力臂、metadata 覆盖、内置 27 模型和旧递归 golden 等价。
