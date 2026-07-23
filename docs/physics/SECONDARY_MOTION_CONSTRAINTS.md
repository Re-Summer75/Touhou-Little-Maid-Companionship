# 二级运动防遮挡与约束技术方案

> 状态：技术调研与实施设计，尚未全部落地。
>
> 范围：Gecko 女仆的头发、发饰、耳朵、丝带、裙摆、披风等骨骼二级运动。

## 目标

解决仅靠弹簧、重力和统一摆角上限无法可靠处理的问题：

- 女仆抬头时，发饰因惯性滞后向头发内部倒并被遮挡；
- 发夹、饰品底座等刚性连接点被当成整块柔性骨骼驱动；
- 发丝、丝带、裙摆或披风穿入头部和身体；
- 低帧率、帧率波动或约束迭代数变化后，限制强度发生明显变化；
- 为了避免穿模而全局提高刚度，导致所有软体失去自然摆动。

本方案不以继续调整全局常数为主，而是在现有 Verlet 风格积分之后增加
**参考空间搬运、动画姿态相对角约束和局部碰撞投影**。

## 调研结论

成熟实时二级运动系统通常同时使用以下机制，而不是只依赖弹簧力：

1. **动画姿态目标**：弹簧持续返回当前动画姿态，而不是固定世界方向；
2. **可选模拟空间**：惯性可以相对角色、基础骨骼或指定中心计算；
3. **角度或锥形约束**：限制骨骼偏离动画姿态的范围；
4. **球、胶囊和平面碰撞**：将模拟端点投影到合法区域；
5. **刚性连接点**：固定根部完全跟随动画，只模拟其柔性后代；
6. **PBD/XPBD 约束投影**：直接修正不合法位置，避免显式力产生过冲。

相关依据：

- VRM `VRMC_springBone 1.0` 使用 Verlet 积分、动画初始方向、
  `center` 参考空间、`hitRadius`、球形和胶囊碰撞器；
- VRM `VRMC_springBone_extended_collider` 增加内部球、内部胶囊和单侧平面；
- Unreal AnimDynamics 提供动画目标、逐轴最小/最大角度、锥形和
  平面约束；
- KawaiiPhysics 提供 Component / World / Base Bone 模拟空间、
  World Damping Rotation、Limit Angle、球/胶囊/平面/盒碰撞，
  并用 XPBD Bone Constraint 维持骨骼间关系；
- NVIDIA APEX Clothing 的 Backstop 使用跟随蒙皮姿态的碰撞球，
  阻止模拟点越过角色表面；
- PBD 将固定连接点视为零逆质量运动学点，XPBD 用 compliance
  消除约束刚度对时间步和迭代数的依赖。

## 现有实现的缺口

当前 [`SpringBoneSolver`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SpringBoneSolver.java)
已经具备：

- 随动画更新的 `restDirection`；
- 帧率修正阻尼；
- 全局角度、逐轴偏转和末端位移上限；
- 大块几何的力臂归一化；
- 最小活动骨架和逐帧零分配热路径。

但它仍缺少三项硬保证：

1. `currentDirections` / `previousDirections` 不会随头部或基础骨骼的
   帧间旋转增量共同搬运。抬头时，旧方向会相对新动画姿态产生滞后；
2. 当前摆角上限前后对称，不能单独收紧“朝头发内部”的方向；
3. 角度只在写入骨骼旋转时截断，积分状态本身没有投影回合法区域，
   且没有任何头部或身体碰撞代理。

因此，单纯提高 `STIFFNESS`、降低 `GRAVITY_POWER` 或缩小
`MAX_ANGLE` 只能减轻现象，不能保证不向后倒或不穿模。

## 总体处理流水线

每帧按以下顺序处理活动骨架：

1. 读取当前动画姿态和父骨骼姿态；
2. 将历史方向搬运到本链的参考空间；
3. 执行现有惯性、刚度、重力积分；
4. 恢复骨长；
5. 投影动画姿态相对的非对称摆角约束；
6. 投影 Backstop、球形或胶囊碰撞；
7. 再次恢复骨长并复查角约束；
8. 将最终合法方向写回当前和历史状态；
9. 根据合法方向生成骨骼旋转和虚拟枢轴位移补偿。

碰撞和角约束可能互相破坏，因此第 5～7 步应使用少量固定迭代，
并由基准测试决定实际迭代数，不能用无限循环。

## 参考空间与旋转惯性

### 模拟空间

每条链选择一个稳定参考空间：

- `HEAD_LOCAL`：头发、发饰、兽耳；
- `BODY_LOCAL`：裙摆、披风、胸前丝带；
- `MODEL`：尾巴或需要保留角色整体转动惯性的部件。

模拟状态仍可保存在模型空间，但需要记录参考骨上一帧方向
`q_ref_previous`。当前参考方向为 `q_ref_current`，则：

```text
q_delta = q_ref_current * inverse(q_ref_previous)
q_transport = slerp(identity, q_delta, 1 - rotationInertiaScale)
currentDirection  = q_transport * currentDirection
previousDirection = q_transport * previousDirection
```

`rotationInertiaScale` 的语义：

- `0`：完全跟随参考骨旋转，不因抬头产生相对滞后；
- `1`：保持原模型空间惯性；
- 中间值：只保留部分旋转惯性。

刚性发饰底座应使用接近完全跟随的配置；长发、丝带可保留部分惯性。
默认值必须通过模型夹具和游戏内测试确定，不在本文中猜测。

### 突变处理

参考骨旋转增量超过安全阈值、模型切换、实体传送或状态恢复时，
直接将当前和历史方向重置到动画 `restDirection`，避免把一次不连续
姿态当成真实角速度。

## 动画姿态相对的非对称摆角

统一圆锥只能限制总摆角，无法区分向前、向后和向侧面。发饰应在
动画姿态局部空间建立正交基：

- `axis`：骨骼静止方向；
- `right`：局部左右方向；
- `outward`：从头部或身体表面指向外侧的方向。

将候选方向 `d` 投影到该基，得到左右和内外两个切向分量。分别使用：

- `leftLimit` / `rightLimit`；
- `outwardLimit` / `inwardLimit`。

当归一化后的切向点落到允许椭圆外时，将其投影到椭圆边界，再重建
单位方向。这样可令向头发内部的 `inwardLimit` 比外侧更严格，同时
保留自然的左右摆动。

约束中心必须是**当前动画姿态**，不能只使用模型绑定姿态。投影后的
方向必须写回 `currentDirections`；同时修正 `previousDirections`，
移除朝非法区域的隐式速度，防止每帧反复撞击边界。

## Backstop 与头部碰撞

### 局部 Backstop 平面

对发饰和贴近头部的短发，在静止连接位置生成随头骨运动的局部平面：

```text
C_plane(p) = dot(p - planePoint, outwardNormal) - hitRadius
```

约束要求 `C_plane >= 0`。若小于零，则：

```text
p = p - C_plane * outwardNormal
```

这与 VRM 扩展的单侧平面碰撞和 APEX Backstop 的用途一致：
模拟点可以在外侧摆动，但不能越过蒙皮或头发外壳进入内部。

### 球形与胶囊代理

单个平面不能覆盖头部弧面，应同时提供轻量代理：

- 头部：由 `Head` / `HEAD_SHELL` 静止 AABB 拟合球体、椭球近似或短胶囊；
- 躯干：由 Body AABB 拟合胶囊；
- 腿部：裙摆按需使用左右腿胶囊；
- 背部：披风增加随躯干运动的背面平面。

碰撞检测使用骨骼末端球，而不是无半径点。`hitRadius` 应从该骨骼
几何横截面估计，并允许模型元数据覆盖。碰撞投影后必须再次恢复骨长。

### 自动生成与显式覆盖

自动代理只能提供保守默认值：

- 表面点来自连接点或可见几何中心；
- 外法线来自头部中心指向连接点，并在退化时回退到模型朝向；
- 代理尺寸来自静止几何边界；
- 只把代理分配给相关链，不能让无限平面影响整个模型。

复杂发型必须允许 sidecar 元数据显式指定模拟空间、角度限制、
碰撞代理和排除项。自动推断不应覆盖作者配置。

## 刚性连接点与柔性后代

不能把“位于头发层级中”等同于“整根骨骼都应物理化”。

应区分：

- **运动学连接点**：发夹、发饰底座、丝带结、固定扣件；
- **柔性后代**：吊坠、流苏、丝带尾、长发分段。

运动学连接点完全使用动画变换，等价于 PBD 中逆质量为零的附着点。
只有其柔性后代进入 solver。若模型把底座和摆动物合并在一个骨骼里，
则使用更严格的局部角限制和 Backstop；不能凭空拆分网格。

自动发现可综合名称、分叉结构、几何紧凑度和后代长度，但不确定时
保持刚性，并允许元数据强制指定。

## XPBD 投影层

现有积分器无需立即重写。可先增加独立约束投影层：

```text
integrate
  -> length
  -> asymmetric swing
  -> collision/backstop
  -> length
  -> commit state
```

对于需要柔性而非绝对刚性的约束，使用 XPBD：

```text
alphaTilde = compliance / (dt * dt)
deltaLambda =
    (-C - alphaTilde * lambda)
    / (sumInverseMassGradientSquared + alphaTilde)
```

这样约束软硬不会随帧率和迭代数明显漂移。Backstop 等不可穿透约束
可直接使用不等式投影；链间距离等柔性约束再使用 compliance。

## 数据与性能设计

静态数据进入 `PhysicsBoneSelectionPlan` / `PhysicsSolverLayout`：

- 链的参考骨索引和模拟空间；
- 静止局部基 `axis/right/outward`；
- 四向摆角限制；
- Backstop 局部点、法线和作用半径；
- 球/胶囊代理及关联链；
- `hitRadius`、compliance 和约束类型。

运行时数据由 solver 预分配：

- 上一帧参考骨四元数；
- 每个驱动槽的约束乘子；
- 端点、法线和四元数 scratch；
- 固定大小的活动碰撞代理数组。

不得在逐帧热路径创建集合、流、临时 `Vector3f` 或 `Quaternionf`。
代理只随其活动参考骨更新，远处实体仍沿用现有物理禁用和生命周期策略。

## 验证要求

### 行为不变量

- 抬头、低头、左右看时，刚性发饰底座保持动画相对位置；
- 柔性后代不越过自身 Backstop；
- 最终方向始终位于非对称摆角区域；
- 碰撞后骨长保持不变；
- 状态中不残留朝约束内部的速度；
- 不出现 NaN、零向量归一化或瞬间翻转。

### 固定测试序列

至少覆盖：

- 头部从平视连续转到仰视，再返回平视；
- 一帧内大角度转头和状态重置；
- 行走加速度、急停、跳跃与同时抬头；
- 20、30、60、120 FPS 下的相同输入序列；
- 纸板狐头顶发饰/呆毛、酒狐长发及匿名模型夹具；
- Backstop、球体和胶囊边界上的极端初始状态。

### 性能门槛

- 优化路径继续保持 `0 B/frame`；
- 记录每帧约束投影次数、碰撞次数和访问节点数；
- 与当前 solver 分别基准，避免碰撞代理让所有模型无条件承担成本；
- 新行为建立独立 golden，不伪称与无约束旧解算逐帧等价。

## 分阶段落地

1. **参考空间搬运**：加入参考骨帧间旋转和状态重置测试；
2. **非对称摆角**：投影并回写当前/历史状态；
3. **头部 Backstop**：先覆盖发饰和短发，再加入头部球/胶囊；
4. **刚柔分离**：发现固定底座，只驱动柔性后代；
5. **身体代理**：扩展到裙摆、披风和胸前饰件；
6. **XPBD 柔性约束**：在确有链间关系需求时加入，不提前扩大范围。

## 参考资料

- [VRMC_springBone 1.0 specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_springBone-1.0/README.md)
- [VRMC_springBone_extended_collider 1.0](https://github.com/vrm-c/vrm-specification/tree/master/specification/VRMC_springBone_extended_collider-1.0)
- [Unreal Engine AnimDynamics](https://dev.epicgames.com/documentation/unreal-engine/animation-blueprint-animdynamics-in-unreal-engine)
- [KawaiiPhysics Parameter Reference](https://github.com/pafuhana1213/KawaiiPhysics/wiki/Parameters-en)
- [NVIDIA APEX Clothing Backstop](https://docs.nvidia.com/gameworks/content/gameworkslibrary/physx/apexsdk/APEX_Clothing/Clothing_Module_Doc.html)
- [Position Based Dynamics](https://matthias-research.github.io/pages/publications/posBasedDyn.pdf)
- [XPBD: Position-Based Simulation of Compliant Constrained Dynamics](https://mmacklin.com/xpbd.pdf)
- [Unity Cloth](https://docs.unity3d.com/Manual/class-Cloth.html)
