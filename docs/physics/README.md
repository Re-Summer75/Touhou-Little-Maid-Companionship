# 女仆骨骼物理（VRM 弹簧骨）

为 Gecko 女仆的尾巴、头发、耳朵等自由摆动骨骼添加一层二级动作物理：跟随身体运动产生真实惯性摆动、带轻微重力下垂，同时**始终收敛回动画姿态、不破坏造型、不穿模**。采用业界标准的 VRM `VRMC_springBone` 弹簧骨模型。

## 分析基准

- Minecraft：1.20.1 · Forge：47.4.0 · 车万女仆：1.5.3 Forge
- 范围：仅 Gecko 模型（`AnimatedGeoModel`），尾巴 / 头发 / 耳朵链；纯客户端。
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

[`MaidBonePhysics.solve`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java) **递归骨骼树**，逐骨骼累积**动画姿态朝向四元数**（只用动画姿态，不把已施加的偏转喂回去，避免自激抖动）。每根骨骼把统一的世界外力**转进它自己的局部帧**再解算——朝向不同 → 局部力不同 → 偏转不同，于是一片头发**逐根错开弯曲**，而不是同步刚性摆动。

偏转的施加：把模拟方向 `currentDir` 转回局部,求"从休息轴 `boneAxis` 到 `currentDir`"的旋转,按小角度近似（旋转矢量 = 轴 × 角）叠加到骨骼的 ZYX 欧拉旋转上。

## 力臂归一化（解决大块几何过摆）

有些骨骼支点在几何边缘、几何体很大（如 `BaseHair` 头盖式整块头发）。同样的旋转角,几何越大末端扫得越远。[`leverArmOf`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java) 算出每根骨骼**支点到几何最远角的力臂**,把偏转角上限按反比压低:

```text
该骨骼角度上限 = min(MAX_ANGLE, MAX_TIP_DISPLACEMENT / 力臂)
```

大块（力臂大）自动被限到小角度、末端位移有界;小发丝（力臂小）保持灵活。**全自动从几何推导,零逐骨骼硬编码。**

## 驱动骨骼判定

[`PhysicsBoneClassifier`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsBoneClassifier.java) 按名分类 `TAIL` / `HAIR` / `EAR`;[`isDrivenBone`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidBonePhysics.java) 在此基础上再要求:

- **排除枢轴骨**：`M` + 大写开头（`MTail`、`MBangs`）是父级定位骨,只驱动其下真实几何骨;
- **排除分叉容器**：子骨骼数 ≥ 2 的骨骼(如 `Hair` 整片头发的父容器)——驱动它会带着所有发丝刚性同转、支点落在容器原点。只驱动链状骨与末端发丝,每根绕自己根部摆;
- **要求有几何**：无立方体的纯变换骨跳过。

## 运动信号与门控

- **重力**：模型空间 `(0, −GRAVITY_POWER, 0)`,小;
- **移动惯性**：实体 `getDeltaMovement` 逐帧差分得世界加速度(clamp 上限防瞬移甩飞),按身体朝向转进模型空间;**着地时丢弃垂直分量**(避免重力/地面钳位每 tick 抖动);
- **转身伪力**：`wrapDegrees(yBodyRot − yBodyRotO)` × 增益,补足纯模型空间捕捉不到的转身惯性;
- **帧率**：真实墙钟 `dt`(clamp 0.1s);刚度/重力/外力按 dt 缩放,休息垂量与 dt 无关;暂停冻结;
- **距离门控**：相机 24 格外跳过并清状态。

## 注入点（关键的累积 bug 修复）

[`GeckoMaidEntityBonePhysicsMixin`](../../src/main/java/com/laixia/maidintelligence/mixin/GeckoMaidEntityBonePhysicsMixin.java) 以 `@Inject(at=@At("RETURN"))` 挂在 `GeckoMaidEntity.setCustomAnimations`,**且仅当返回值为 `true`(动画确实重摆了骨骼)时才解算**。

原因:`setCustomAnimations` 有帧率限制器,被限流的帧提前 `return false`、不重置骨骼;若在这些帧仍施加偏转,会叠加在上一帧已偏转的骨骼上无限累积(实测站定时尾巴曾绕到 7000°+)。只在 `true` 帧解算即可根除。

## 调试工具

- **`/maidphysicsdebug`** → 获得**骨骼调试棒**(带 NBT 的原版木棍,无需注册物品)。
- **手持** → [`MaidSkeletonDebugLayer`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/MaidSkeletonDebugLayer.java) 在所有女仆身上画骨架:灰十字 = 普通骨骼原点;黄十字 + RGB 轴 + 青色立方体线框 = 物理驱动骨骼。
- **右键女仆** → [`PhysicsDebugSkeletonDump`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/PhysicsDebugSkeletonDump.java) 把完整骨架(名字/位置/pivot/旋转/缩放/立方体数/父子/驱动状态)写入 `run/logs/latest.log`,并把驱动骨骼摘要发到聊天栏;右键只 dump、不触发其它交互。

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

## 已知限制与后续

- **无真实碰撞体**:目前用角度/逐轴/力臂上限约束穿模,尚未实现 VRM 式球形 collider(未来可加,专门治抬头/贴身穿模)。
- **转动惯量**:力臂归一化压的是幅度;更真实的"大块又慢又沉"可再按力臂缩放驱动力(转动惯量 ∝ 力臂²)。
- **范围**:仅 Gecko;Bedrock(`BedrockPart` + JS 脚本)与 YSM(仅捕获顶点)暂不支持。

## 验证

```bash
./gradlew.bat --offline verifyBonePhysics
```

无依赖校验骨骼链/枢轴/容器的分类逻辑(`PhysicsBoneClassifier`)。
