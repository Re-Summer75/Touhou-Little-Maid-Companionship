# 二级运动防遮挡与约束技术方案

> 状态：阶段 1～8 已落地；仅在出现明确的链间柔性关系需求时按需增加 XPBD。
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

## 初始缺口与当前状态

实施约束层前，[`SpringBoneSolver`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SpringBoneSolver.java)
已经具备：

- 随动画更新的 `restDirection`；
- 帧率修正阻尼；
- 全局角度、逐轴偏转和末端位移上限；
- 大块几何的力臂归一化；
- 最小活动骨架和逐帧零分配热路径。

但它缺少三项硬保证：

1. `currentDirections` / `previousDirections` 不会随头部或基础骨骼的
   帧间旋转增量共同搬运。抬头时，旧方向会相对新动画姿态产生滞后；
2. 当前摆角上限前后对称，不能单独收紧“朝头发内部”的方向；
3. 角度只在写入骨骼旋转时截断，积分状态本身没有投影回合法区域，
   且没有任何头部或身体碰撞代理。

上述三项现已由 [`SecondaryMotionConstraint`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SecondaryMotionConstraint.java)
和 `solver/collision` 通用代理框架实现：参考骨旋转增量先搬运历史状态，
积分后将方向投影到四向摆角椭圆，再依次执行每骨关联的
Plane / Sphere / Capsule 代理，并把合法方向写回当前和历史状态。
单纯调整 `STIFFNESS`、`GRAVITY_POWER` 或 `MAX_ANGLE` 仍不能替代
这些硬约束。

## 总体处理流水线

每帧按以下顺序处理活动骨架：

1. 在任何物理写回前捕获带代理段、碰撞 reference 及其祖先的动画姿态、层级仿射增量和法线变换；
2. 将历史方向搬运到本链的参考空间；
3. 执行现有惯性、刚度、重力积分；
4. 恢复骨长；
5. 投影动画姿态相对的非对称摆角约束；
6. 按顺序投影当前骨骼关联的 Plane / Sphere / Capsule 代理；
7. 最多交替复查摆角和碰撞四轮；代理内部也按固定上限复查多代理；
8. 将最终合法方向写回当前和历史状态；
9. 根据合法方向生成骨骼旋转和虚拟枢轴位移补偿。

碰撞和角约束可能互相破坏。当前实现最多交替投影四轮，并在一轮中
两类约束都未修正时提前退出；无碰撞、未触边骨骼只执行一轮快速检查，
不会使用无限循环。每个代理的运行时几何每帧只准备一次，四轮投影和
代理内部复查都复用准备态，不重复计算 reference 仿射变换。

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
当前经夹具回归的自动默认值为：`HEAD_SHELL=0.05`、`HAIR=0.35`、
`EAR=0.15`、`RIBBON=0.45`、`SKIRT/CAPE=0.65`，其余类型保持
`MODEL` 惯性 `1.0`。模型作者可用 sidecar 覆盖。

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

写回 Gecko 骨骼时，先组合动画与物理轴角四元数，再用显式 ZYX 公式
分解为三个欧拉分量。回归会从最终欧拉值重建四元数并检查其骨轴方向，
避免“状态已投影但画面旋转仍有偏差”。

## 通用碰撞代理

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

### Plane / Sphere / Capsule

单个平面不能覆盖头部弧面，当前自动布局按部件和模拟空间组合轻量代理：

- `HAIR`、`EAR`、`HEAD_LOCAL RIBBON`：每个受驱动段使用 Head Plane +
  Sphere；Head 静止 AABB 纵横比较高时，曲面形状改为短 Capsule；
- `SKIRT`：Body Capsule + 经唯一性和几何可靠性检查的左右腿 Capsule；
- `CAPE`：Body Capsule + 随 Body 运动的 Back Plane；
- `BODY_LOCAL RIBBON`：Body Capsule。

碰撞检测使用每段骨骼末端球，而不是无半径点。`hitRadius` 从该骨骼
几何横截面估计，并可由 schema 3 元数据覆盖；投影始终维持固定骨长。
左右腿不能唯一、可靠配对时只跳过腿代理，不猜测骨骼，Body Capsule
仍可继续使用。

当前碰撞数据统一为不可变的 Plane / Sphere / Capsule 代理数组。
每根驱动骨可关联零到多个代理，每个代理独立记录形状参数、
`referenceNodeIndex`、有效 pivot、`hitRadius` 和力臂；因此同一骨骼
可以同时受随 Head 运动的平面、随 Body 运动的胶囊和模型空间代理约束。
现有 `backstop` 会烘焙为 Plane，`head_collision` 会烘焙为 Sphere
（高纵横比时为短 Capsule），其原有投影公式与顺序保持不变。

Sphere 与退化 Capsule 使用固定骨长的解析最小点积边界；普通 Capsule
先求端点到线段的最近点，再做有限次固定骨长半空间投影。端点落在胶囊
轴线或胶囊退化为点时使用确定性法线/球体回退，避免零向量归一化。
所有代理对象、代理数组、准备态和 `CollisionScratch` 均在布局或 solver
构造期分配，逐帧只遍历数组并复用向量。多代理在一次完整遍历没有修正时
立即退出；较高的固定上限仅用于两个近切曲面约束的慢收敛穿透反例。

静止 `Head` AABB 自动拟合头部 Sphere 或短 Capsule，并从驱动骨几何
最小横截面估计 `hitRadius`。碰撞连接点使用 `BoneKinematics` 校正后的
有效 pivot，而非可能远离网格、位于中心或处于柔性远端的作者 pivot。
附着端以父级/最近实体祖先网格作为支撑体，对竖直端点和几何主轴端点
计算表面距离；距离接近的悬挂件再按重力选择质心上方端点，支撑体明确
位于部件下方时则选择下端。贴近网格的直接空锚点只作为次级证据，
不会覆盖明确支撑关系。距离使用每个旋转 cube 的实际表面而非总 AABB；
两个端点评分仍接近时不移动 authored pivot，并降低该骨骼摆角。静态
碰撞连接点通过匹配 Gecko 渲染顺序的绑定姿态仿射矩阵变换到碰撞空间。
投影直接解固定骨长下的最小点积边界，不会先移动端点再以归一化破坏
碰撞结果。

最小活动骨架包含每个模拟/碰撞 reference 及其祖先。每帧先捕获实际
碰撞 reference 及其祖先的动画 affine delta，因此即使 sibling reference 在受驱动骨之后，
也能使用尚未被物理写回污染的完整姿态。随后运行时端点层按 Gecko 的
position → pivot → ZYX rotation → scale → inverse pivot 顺序传播父物理
偏转、虚拟 pivot 的 position 补偿和非均匀 scale；每个受驱动段以自己的
runtime pivot 参与投影。

`RuntimeCollisionCache` 对每个代理每帧只准备一次 reference origin、
运行时形状、pivot、半径和 `hitRadius`，四轮交替投影复用同一准备态。
Plane 法线使用 affine 的 inverse-transpose 后重新归一化；Sphere /
Capsule 及末端半径使用保守谱尺度上界：旋转加轴缩放时等于最大轴，
出现剪切时使用诱导范数/Frobenius 上界，避免低估碰撞体。

### 自动生成与显式覆盖

自动代理只能提供保守默认值：

- 表面点来自连接点或可见几何中心；
- 外法线来自头部中心指向连接点，并在退化时回退到模型朝向；
- 代理尺寸来自静止几何边界；
- 只把代理分配给相关链，不能让无限平面影响整个模型。

schema 3 sidecar 可在 `constraints.collision.proxies` 中显式添加 Plane、
Sphere 和 Capsule；静止坐标与半径使用 Gecko 像素（`16 px = 1` 模型
空间方块），`hit_radius` 可省略。`reference` 支持 `MODEL`、唯一 `ROOT`
和能唯一解析的路径/节点。`auto` 默认为 `true`，显式代理追加到自动代理
之后；`auto:false` 只保留有效显式代理。缺失或歧义 reference 只跳过对应
代理，不影响同链其它代理。

旧 `backstop` / `head_collision` 开关继续分别控制自动 Head Plane 和
自动 Head Sphere/短 Capsule；schema 1/2 不读取 schema 3 的 `collision`
字段，兼容旧 sidecar 行为。复杂模型可用显式代理修正自动 AABB 拟合，
但它仍不是任意网格的精确碰撞。

### 调试可视化与 dump

手持骨骼调试棒时，亮青线显示逐段 runtime pivot/tip；Plane 为蓝色、
Sphere 为绿色、Capsule 为琥珀色，代理的 reference origin 以同色十字
标出。当前末端对某代理的 `clearance < 0` 时，该代理整组改为红色，表示
准备态下仍检测到穿透，而不是声称整个渲染网格都已精确检测。

右键女仆会把逐段 runtime 几何、reference、代理形状、形状/末端半径、
力臂、`AUTOMATIC/EXPLICIT` 来源、clearance 和穿透标记写入
`run/logs/latest.log`，聊天栏给出代理与穿透数量摘要。

## 刚性连接点与柔性后代

不能把“位于头发层级中”等同于“整根骨骼都应物理化”。

应区分：

- **运动学连接点**：发夹、发饰底座、丝带结、固定扣件；
- **柔性后代**：吊坠、流苏、丝带尾、长发分段。

运动学连接点完全使用动画变换，等价于 PBD 中逆质量为零的附着点。
只有其柔性后代进入 solver。若模型把底座和摆动物合并在一个骨骼里，
则使用更严格的局部角限制和 Backstop；不能凭空拆分网格。

自动发现综合名称、分叉结构、几何紧凑度、同 pivot 几何重叠、镜像
sibling 和后代延伸。明确发夹名称或具有强结构证据的紧凑底座标记为
`RIGID_ATTACHMENT_BASE`；只拒绝底座自身，悬垂或细长后代继续独立评分。
明确的 `Hair/Fringe/Pony` 名称不会被匿名结构规则覆盖，元数据仍可强制
指定任何骨骼。

当前发现器会将头部层级中的 `flower`、`clip`、`hairpin`、
`ornament`、`accessory`、`ball(s)` 及对应中日韩发饰标记视为
运动学连接点；基础纸板狐中同 pivot 重叠的 `bone29/30/27/31`
即使匿名也会保持刚性。镜像、从头部附着并向下延伸的 `bone3/bone9`
改为两个独立的 `COMPOUND_SINGLE_BONE` 马尾；年糕狐 `Balls` 继续刚性，
`LeftPony/RightPony` 继续驱动但使用单骨安全档位。

## 真实链段的根梢动力学

每个 driven bone 在计划构建期记录真实链根、段序号和段总数。这里只沿
实际 parent-child 关系连接段，不从数字后缀猜测，也不把左右分叉合并成
同一自动 chain。显式 metadata 链只记录拓扑，不修改作者 profile。

自动发现的多段 `HAIR/RIBBON/CAPE/TAIL` 使用有界插值：

- 根段提高刚度和阻尼、降低移动/转身惯性与摆角，并更强跟随参考骨；
- 梢端逐步降低刚度和阻尼，提高移动/转身及旋转惯性，允许相对父段滞后；
- 每段仍独立执行力臂角限、四向摆角和碰撞，父段物理偏转继续正确传给
  子段 pivot/rest frame。

因此多骨双马尾会产生段间相位差，而不是靠破坏父子变换来制造弯曲。
对所有 cube 只绑定一个 bone 的长网格，渲染只能接受一次骨变换：
系统使用更高刚度/阻尼及更小 angle/tip 档位控制整体摆动，不伪造与
碰撞、调试或 Sodium 渲染不一致的逐 cube 虚拟关节。

运动学同时区分“主连通 cube 簇”和“全部可见几何”。cube 连通使用带
margin 的 OBB/SAT，而不是会把相隔细长方块误连的包围球。当主簇足够
占优时，attachment pivot/axis 与实际 segment length 使用主簇，避免
远处小饰件拉偏旋转轴或碰撞端点；独立 safety lever 始终覆盖全部 cube，
保证脱离主簇的装饰仍会收紧安全摆幅。

## 按需 XPBD 投影层

当前硬 PBD 已按以下独立约束投影层运行，无需为现有碰撞重写积分器：

```text
integrate
  -> length
  -> asymmetric swing
  -> collision/backstop
  -> length
  -> commit state
```

若以后出现需要柔性而非绝对刚性的链间约束，可使用 XPBD：

```text
alphaTilde = compliance / (dt * dt)
deltaLambda =
    (-C - alphaTilde * lambda)
    / (sumInverseMassGradientSquared + alphaTilde)
```

这样柔性约束的软硬不会随帧率和迭代数明显漂移。Backstop 等不可穿透
约束继续使用不等式投影；链间距离等柔性关系再使用 compliance。

当前 Plane、Sphere、Capsule 和摆角均为无 compliance 的硬 PBD 投影。
现有方向状态天然保持骨长，因此尚无必须引入 XPBD 乘子的链间约束；
只有后续出现明确的柔性距离或链间关系需求时才实施该部分。

## 数据与性能设计

静态数据进入 `PhysicsBoneSelectionPlan` / `PhysicsSolverLayout`：

- 链的参考骨索引和模拟空间；
- 静止局部基 `axis/right/outward`；
- 四向摆角限制；
- 每骨有序代理数组及每个代理独立的参考骨索引；
- Plane 点/法线、Sphere 中心/半径、Capsule 线段/半径；
- 每个代理的有效 pivot、`hitRadius`、力臂和约束类型。
- 每骨结构角色、真实链根、段序号/总数，以及已经烘焙的根梢
  `SpringProfile` / `rotationInertiaScale`；
- 主 cube 簇推断的 attachment frame/segment length 和覆盖全部 cube
  的独立安全力臂。

运行时数据由 solver 预分配：

- 每个活动参考骨的上一帧四元数和本帧共享旋转增量；
- 碰撞依赖节点在物理写回前捕获的 animation affine delta、
  inverse-transpose 法线矩阵和保守谱尺度上界；
- 每个活动节点的层级仿射变换与模型空间 pivot；
- 每个驱动节点的模型空间几何 tip、实际缩放后 segment length，以及
  层级最大 scale 上界下的 safety lever 角限；
- 每代理逐帧准备一次的运行时几何，以及 Plane / Sphere / Capsule
  共用的端点、最近点、法线和四元数 scratch；
- 每个驱动槽的当前/历史方向。

公共入口 `SpringBoneSolver` 仅作为兼容门面；状态、scratch、姿态组合、
参考空间搬运、积分、约束、偏转写回和端点传播分别位于
`solver/spring` 子包的独立模块；碰撞形状、固定骨长投影、代理集合和
自动/显式代理烘焙和准备态位于 `solver/collision` 子包，新增模块均不超过
200 行。

XPBD 约束乘子按设计不分配，除非以后确有链间柔性关系需求。

不得在逐帧热路径创建集合、流、临时 `Vector3f` 或 `Quaternionf`。
代理只随其活动参考骨更新，远处实体仍沿用现有物理禁用和生命周期策略。

## 验证覆盖

### 行为不变量

- 抬头、低头、左右看时，刚性发饰底座保持动画相对位置；
- 柔性后代不越过自身 Backstop；
- 最终方向始终位于非对称摆角区域；
- 碰撞后骨长保持不变；
- 父骨物理偏转、位置补偿和非均匀缩放会传递到后代 pivot/tip；
- 状态中不残留朝约束内部的速度；
- 不出现 NaN、零向量归一化或瞬间翻转。

### 固定测试序列

现有离线回归覆盖：

- schema 1/2 兼容、schema 3 解析、Gecko 像素换算、可选
  `hit_radius` 和无效代理独立跳过；
- Plane、Sphere、Capsule 几何、胶囊退化、自动/显式合并及
  `auto:false`；
- Head 每段代理、Body/双腿/Back Plane 布局，以及双骨架或歧义腿排除；
- 受驱动父子链、父物理偏转、虚拟 pivot position 补偿、非均匀 scale
  与后序 sibling Leg reference；
- animation affine 捕获、非均匀 scale/剪切尺度上界、准备态与直接投影
  等价、reset 和穿透清除；
- 自动 reference 的 self/受驱动边界贡献安全检查，以及近切、近反向
  Plane 对的狭窄可行域投影；
- 纸板狐匿名紧凑发饰、镜像单骨马尾、年糕狐发球，以及 metadata 对
  自动刚性判断的优先覆盖；
- 纸板狐汉服/新年、酒狐等真实多骨链的独立 chain、连续段序号、根梢
  参数单调性和同一冲量下的非零段间相对偏转；
- OBB/SAT 主 cube 簇、旋转零厚度平面的面积权重、rank-one 退化 cube
  排除，以及主簇 segment length 与全部 cube 安全力臂同时成立；
- 非均匀缩放下 runtime segment、碰撞力臂和全几何位移角限一致；
- 头部平视/仰视、大角度转头、移动序列，以及 20、30、60、120 FPS
  不变量；
- 单骨三个代理、不同 reference 和 solver/多代理热路径 `0 B/frame`。

### 性能门槛

- 优化路径继续保持 `0 B/frame`；
- 记录每帧约束投影次数、碰撞次数和访问节点数；
- 与当前 solver 分别基准，避免碰撞代理让所有模型无条件承担成本；
- 新行为建立独立 golden，不伪称与无约束旧解算逐帧等价。

最新 Windows/JDK 17、`winefox`、20,000 测量帧样例：

```text
recursive-full: 53500.0 ns/frame, nodes=181/181, allocation=14592.00 B/frame
iterative-active-legacy: 46899.3 ns/frame, nodes=107/181, allocation=0.00 B/frame
iterative-active-constrained: 84982.0 ns/frame, nodes=107/181, allocation=0.00 B/frame
legacy-equivalent solver speedup: 1.14x
constraint-layer cost: 1.81x legacy-active
```

绝对耗时会随机器波动；稳定门槛是生产约束路径继续保持
`0 B/frame`，并单独报告旧积分等价路径与约束路径。`constrained` 的
`1.81x` 代价包含碰撞依赖 affine 捕获、每段代理准备和投影；准备态缓存
避免四轮交替投影重复变换同一代理。

## 已知限制

- 自动代理由静止 AABB、名称和拓扑启发式拟合为 Plane / Sphere /
  Capsule，不是任意网格、凹面或整块渲染几何的精确碰撞；它减少常见
  穿模，但不保证完全无穿模。
- 左右腿只有在唯一且可靠配对时才生成 Capsule；双骨架、重名或几何
  歧义会保守跳过腿代理。
- 当前摆角与碰撞是固定骨长的硬 PBD，不提供 XPBD compliance；XPBD
  只在以后出现明确链间柔性关系时按需加入。
- 同一个 Gecko bone 内所有 cube 共享一次 TRS；主簇只改善枢轴/轴和
  安全包络，不能让单骨马尾真正弯成多段。真实分段必须由模型提供多个
  不同 pivot 的父子骨。

## 分阶段落地

1. **已完成—参考空间搬运**：参考骨帧间旋转、共享增量缓存和突变重置；
2. **已完成—非对称摆角**：相对当前动画姿态投影并回写当前/历史状态；
3. **已完成—头部约束**：自动 Backstop、头部球体和骨骼 `hitRadius`；
4. **已完成—刚柔分离**：保守识别固定发饰底座，柔性后代继续发现；
5. **已完成—运行时端点层级**：传播父物理、position 补偿和非均匀
   scale 下的真实 pivot/tip；
6. **已完成—统一碰撞代理**：通用 Plane / Sphere / Capsule、每骨多代理、
   每代理独立参考骨及零分配投影；
7. **已完成—自动身体代理与逐段碰撞**：每个相关受驱动段消费运行时
   pivot，覆盖 Head、Body、可靠双腿和 Back Plane，并支持 schema 3
   显式代理；
8. **已完成—发饰与马尾独立化**：结构化刚性底座、匿名镜像单骨马尾、
   主 cube 簇运动学及真实多骨链根硬梢软参数；
9. **按需实施—XPBD 柔性约束**：只在出现链间柔性关系需求时加入。

## 参考资料

- [VRMC_springBone 1.0 specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_springBone-1.0/README.md)
- [VRMC_springBone_extended_collider 1.0](https://github.com/vrm-c/vrm-specification/tree/master/specification/VRMC_springBone_extended_collider-1.0)
- [Unreal Engine AnimDynamics](https://dev.epicgames.com/documentation/unreal-engine/animation-blueprint-animdynamics-in-unreal-engine)
- [KawaiiPhysics Parameter Reference](https://github.com/pafuhana1213/KawaiiPhysics/wiki/Parameters-en)
- [NVIDIA APEX Clothing Backstop](https://docs.nvidia.com/gameworks/content/gameworkslibrary/physx/apexsdk/APEX_Clothing/Clothing_Module_Doc.html)
- [Position Based Dynamics](https://matthias-research.github.io/pages/publications/posBasedDyn.pdf)
- [XPBD: Position-Based Simulation of Compliant Constrained Dynamics](https://mmacklin.com/xpbd.pdf)
- [Unity Cloth](https://docs.unity3d.com/Manual/class-Cloth.html)
