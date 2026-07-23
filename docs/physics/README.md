# 女仆骨骼物理（VRM 弹簧骨）

为 Gecko 女仆的尾巴、头发、耳朵等自由摆动骨骼添加一层二级动作物理：跟随身体运动产生真实惯性摆动、带轻微重力下垂，同时**始终收敛回动画姿态、限制过摆和穿模**。实现参考 VRM `VRMC_springBone` 弹簧骨模型。

## 分析基准

- Minecraft：1.20.1 · Forge：47.4.0 · 车万女仆：1.5.3 Forge
- 范围：仅 Gecko 模型（`AnimatedGeoModel`），覆盖头发、尾巴、耳朵、裙摆、丝带、披风、翅膀等软体链；纯客户端。
- 参考技术：VRM `VRMC_springBone`、Dynamic Bone、t3ssel8r 二阶动力学。

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
- **抬头头发跟随**：`restDir` 随头骨动画一起抬，刚度把头发拉向抬起的 `restDir`，只叠加轻微下垂，**不会向后倒**；
- **运动才甩**：转身/起步/急停/跳跃的瞬态惯性驱动摆动，然后弹回动画姿态。

## 逐骨骼独立解算（解决"一整块"）

[`MaidBonePhysics.solve`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java) **递归骨骼树**，逐骨骼累积**动画姿态朝向四元数**（只用动画姿态，不把已施加的偏转喂回去，避免自激抖动）。每根骨骼在模型空间独立维护方向，使用各自的动画休息轴和参数积分，最后再把偏转转回该骨骼局部帧；父骨偏转由渲染层级自然传给子骨，子骨只叠加自己的局部弹簧。

偏转的施加：把模拟方向 `currentDir` 转回局部,求"从休息轴 `boneAxis` 到 `currentDir`"的旋转,按小角度近似（旋转矢量 = 轴 × 角）叠加到骨骼的 ZYX 欧拉旋转上。

## 力臂归一化（解决大块几何过摆）

有些骨骼支点在几何边缘、几何体很大（如 `BaseHair` 头盖式整块头发）。同样的旋转角,几何越大末端扫得越远。[`leverArmOf`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java) 算出每根骨骼**支点到几何最远角的力臂**,把偏转角上限按反比压低:

```text
该骨骼角度上限 = min(MAX_ANGLE, MAX_TIP_DISPLACEMENT / 力臂)
```

大块（力臂大）自动被限到小角度、末端位移有界;小发丝（力臂小）保持灵活。**全自动从几何推导,零逐骨骼硬编码。**

## 驱动骨骼判定

名称不能可靠表达模型作者的意图：第三方模型常用 `bone17` 等匿名节点，同一个 `BaseHair` 也可能既有可见头盖几何又承担分叉父节点。现在由 [`PhysicsBoneSelectionPlan`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneSelectionPlan.java) 保存每个**骨骼实例**的最终决策，优先级为:

1. **模型元数据**：作者显式指定的链与排除项，置信度最高;
2. **几何 / 拓扑自动发现**：[`PhysicsBoneDiscoverer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneDiscoverer.java) 根据绑定姿态的 pivot、cube AABB、薄度、力臂、链长、分叉和相对 Head / Body 位置评分;
3. **名称提示**：[`PhysicsBoneClassifier`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneClassifier.java) 仅给常见英文/拼音名称加分和选择默认参数，**不再是入选前提**。

具体实现集中在 `client/discovery` 子包：元数据绑定、候选评分、头部/躯干规则、刚性过滤和计划写入彼此独立，入口 [`PhysicsBoneDiscoverer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneDiscoverer.java) 只保留稳定门面。

自动发现采用高置信门槛，低置信节点保持静止，避免把肢体、武器和固定头饰当作软体。空骨骼仍作为层级枢轴参与分析但不直接驱动。`BaseHair` / `TopHair` 这类包围头部、有实体几何且连接发丝的分叉骨会识别为 `HEAD_SHELL`，使用高刚度、低摆幅参数；纯空分叉容器则跳过。

模型计划按 `AnimatedGeoModel` 实例弱缓存，模拟状态按 `AnimatedGeoBone` 实例保存。女仆切换模型或资源重载时会整体重建，不会把上一个同名骨骼的 `null` 判定、方向或力臂带入新模型。

## 模型物理元数据

模型作者可在 TLM 模型包（目录或 ZIP）内添加:

```text
assets/<namespace>/tlm_companionship/physics/<model-path>.json
```

例如模型 ID `geckolib:winefox` 对应 `assets/geckolib/tlm_companionship/physics/winefox.json`:

```json
{
  "schema_version": 1,
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
- F3+T 资源重载及 TLM 直接加载新下载的目录/ZIP 模型包都会刷新 sidecar、选择计划和实体模拟状态。

## 运动信号与门控

- **重力**：模型空间 `(0, −GRAVITY_POWER, 0)`；悬垂部件使用分类型倍率，`HEAD_SHELL` 仅保留近乎为零的重力，避免抬头时整块头盖向后翻;
- **移动惯性**：实体 `getDeltaMovement` 按 20 TPS 游戏 tick 差分并在两个 tick 之间保持，不再向渲染帧插入“尖峰后归零”的假信号；按渲染器 `180° − bodyYaw` 的逆变换转进模型空间，再取反作为滞后力；**着地时丢弃垂直分量**(避免重力/地面钳位每 tick 抖动);
- **转身伪力**：`wrapDegrees(yBodyRot − yBodyRotO)` × 增益,补足纯模型空间捕捉不到的转身惯性;
- **非线性降噪**：移动和转身信号先经过径向软死区与 `tanh` 软限幅，再进入 One Euro 式自适应低通；小抖动使用低截止频率强滤波，快速动作按导数幅度自动提高截止频率;
- **帧率**：真实墙钟 `dt`(clamp 0.1s)；刚度/重力/外力按 dt 缩放，阻尼按 60 FPS 基准做指数换算；暂停或长时间断帧冻结该步，避免恢复瞬间跳动;
- **距离门控**：相机 24 格外跳过并清状态。

## 异常枢轴修正

部分 Bedrock 模型的骨骼 pivot 位于网格边缘，甚至离自身 cube 数十像素。解算器会测量烘焙网格 AABB 与体积中心：

- `HEAD_SHELL` 使用网格自身中心作为保守虚拟枢轴；
- 其它 pivot 明显脱离网格时，头发/裙摆/披风采用网格上沿中心，其余部件采用父骨骼 pivot 指向网格的最近点作为虚拟枢轴；
- 实际骨骼 pivot 不可修改，因此在施加旋转时同步计算位移补偿，使最终矩阵等价于绕虚拟枢轴旋转；
- 枢轴可信度低的骨骼额外采用更小角度上限，防止小角度经超长力臂放大。

## 注入点（关键的累积 bug 修复）

[`GeckoMaidEntityBonePhysicsMixin`](../../src/main/java/com/laixia/maidintelligence/mixin/GeckoMaidEntityBonePhysicsMixin.java) 以 `@Inject(at=@At("RETURN"))` 挂在 `GeckoMaidEntity.setCustomAnimations`,**且仅当返回值为 `true`(动画确实重摆了骨骼)时才解算**。

原因:`setCustomAnimations` 有帧率限制器,被限流的帧提前 `return false`、不重置骨骼;若在这些帧仍施加偏转,会叠加在上一帧已偏转的骨骼上无限累积(实测站定时尾巴曾绕到 7000°+)。只在 `true` 帧解算即可根除。

## 调试工具

- **`/maidphysicsdebug`** → 获得**骨骼调试棒**(带 NBT 的原版木棍,无需注册物品)。
- **手持** → [`MaidSkeletonDebugLayer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidSkeletonDebugLayer.java) 在所有女仆身上画骨架:灰十字 = 未驱动；黄十字 = 自动发现；紫色十字 = 元数据指定；受驱动骨骼同时显示 RGB 轴和青色立方体线框。
- **右键女仆** → [`PhysicsDebugSkeletonDump`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsDebugSkeletonDump.java) 把完整骨架、模型路径、`META/AUTO` 来源、类型、链 ID、置信度和入选/拒绝原因写入 `run/logs/latest.log`，并把驱动骨骼摘要发到聊天栏；右键只 dump、不触发其它交互。

## 调参

集中在 [`MaidBonePhysics`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java) 顶部:

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

## 已知限制与后续

- **无真实碰撞体**:目前用虚拟枢轴、分类型重力、角度/逐轴/力臂上限约束穿模；尚未实现 VRM 式球形 collider，因此任意第三方网格仍不能保证完全不穿模。
- **转动惯量**:力臂归一化压的是幅度;更真实的"大块又慢又沉"可再按力臂缩放驱动力(转动惯量 ∝ 力臂²)。
- **自动发现是保守启发式**：几何无法无歧义地区分造型相似的发丝、丝带和固定装饰；低置信节点默认不动，复杂模型建议提供 sidecar。
- **范围**:仅 Gecko;Bedrock(`BedrockPart` + JS 脚本)与 YSM(仅捕获顶点)暂不支持。

## 验证

```bash
./gradlew.bat --offline verifyBonePhysics
./gradlew.bat --offline check
```

离线校验名称提示、元数据优先级、模型切换缓存、运动坐标系、帧率阻尼、空锚点、locator 后代、异常 pivot 的虚拟枢轴矩阵补偿、真实 `winefox` 回归，以及匿名头盖、发丝、耳朵、尾巴、裙摆、丝带、披风、翅膀与刚性反例；游戏内仍需用调试棒检查第三方模型的最终视觉效果。
