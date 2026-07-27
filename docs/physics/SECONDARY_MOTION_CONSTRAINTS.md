# 二级运动防遮挡与约束技术方案

> 状态：阶段 1～11 已落地；仅在出现明确的链间柔性关系需求时按需增加 XPBD。
>
> 范围：Gecko 女仆的头发、发饰、耳朵、丝带、裙摆、披风等骨骼二级运动。

## 目标

解决仅靠弹簧、重力和统一摆角上限无法可靠处理的问题：

- 女仆抬头时，发饰因惯性滞后向头发内部倒并被遮挡；
- 发夹、饰品底座等刚性连接点被当成整块柔性骨骼驱动；
- 发丝、丝带、裙摆或披风穿入头部和身体；
- 预设动画的 position / scale 及安装参考角加速度不能产生独立惯性，快速动作只改变目标姿态而缺少附着点甩动；
- Gecko 控制器被帧率限制时，硬编码尾巴仍逐渲染帧更新，导致纯动画姿态与物理姿态交替；
- 低帧率、帧率波动或约束迭代数变化后，限制强度发生明显变化；
- 为了避免穿模而全局提高刚度，导致所有软体失去自然摆动。

本方案不以继续调整全局常数为主，而是在现有 Verlet 风格积分前后增加
**预设动画运动采样、参考空间搬运、动画姿态相对角约束和局部碰撞投影**。

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
Box / Plane / Sphere / Capsule 代理，并把合法方向写回当前和历史状态。
单纯调整 `STIFFNESS`、`GRAVITY_POWER` 或 `MAX_ANGLE` 仍不能替代
这些硬约束。摆角上限本身只存在于
[`SwingRange`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/SwingRange.java)
一处：layout 烘焙、投影重算、偏转写回和风驱动份额四个消费者必须读到同一
个值，否则物理按一个上限收敛、渲染按另一个上限截断。风只取其中 `78%`，
余量留给惯性与行走摆动叠加；上限本身由投影强制，所以这个份额只决定"强风
单独能读到多强"，而不是安全边界。

## 总体处理流水线

每帧按以下顺序处理活动骨架：

1. 在 `setCustomAnimations` 入口恢复上一帧物理写回前保存的局部 rotation/position；
2. 让 Gecko 控制器及硬编码动画在无旧物理偏转的姿态上运行；
3. 在任何新物理写回前保存 driven bone 的动画局部姿态，捕获活动骨架的纯动画 TRS，并另行捕获碰撞依赖节点的 animation affine delta 与法线变换；
4. 对每个 driven slot 采样动画枢轴线速度/线加速度、安装参考角速度/角加速度及缩放导数，执行突变判定、门控和低通；
5. 将历史方向搬运到本链的参考空间；
6. 执行 Verlet 惯性、刚度、**相对静止方向分解后的**重力、实体运动和动画运动积分；
7. 恢复骨长；
8. 投影动画姿态相对的非对称摆角约束；
9. 按顺序投影当前骨骼关联的 Box / Plane / Sphere / Capsule 代理；
10. 最多交替复查摆角和碰撞四轮；代理内部也按固定上限复查多代理；
11. 将最终合法方向写回当前和历史状态；
12. 根据合法方向生成骨骼旋转和虚拟枢轴位移补偿。

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
`MODEL` 惯性 `1.0`。自动悬垂饰品会在此基础上进一步把
`rotationInertiaScale` 压到 `0.10` 以下。`HEAD_SHELL` 与自动悬垂
饰品另外把静态重力设为零，连"更沉的回拉"也不要；移动与转身惯性仍然
生效，模型作者也可用 sidecar 覆盖。

### 相对静止方向的重力

恒力作用在弹簧上只有一个结果：停在"力与恢复力平衡"处，而不是停在被画
出来的位置。而作者画裙摆下垂、腰带侧躺时，画的**已经是**重力作用后的
形态，再叠加一次世界下方的恒力等于把重力算了两遍——上翘的发丝垮下来、
横向的丝带歪掉，且调小 `gravityScale` 只能减轻不能消除，因为位移的存在
本身就是恒力的性质。

因此重力按静止方向 `rest` 分解后注入：

```text
alongRest = dot((0, -g, 0), rest)          // 平行分量的标量
displaced = clamp(1 - dot(current, rest))  // 已偏离静止方向的程度
gravity   = rest * alongRest * (1 - displaced)
          + (0, -g, 0) * displaced
```

- **平行分量**无条件生效。方向随后被归一化，所以沿 `rest` 的分量只改变
  "被拉回去的力度"而不改变"回到哪里"：下垂部件因此更沉，逆重力上举的
  部件更软，这正是重力该给它们的重量感；
- **垂直分量**才是会搬动造型的那一半，按 `displaced` 淡入。静止时它严格
  为零，所以作者姿态被精确保持；被风吹开或被腿踢开之后它按偏离程度接入，
  部件仍朝真实的下方坠落。它在满偏离时饱和于完整重力，而恢复力随角度
  继续线性增长，因此不会自激。

竖直下垂的骨与世界重力共线，两种算法给出完全相同的结果；差异只出现在
横向、上举和斜向的部件上，而那正是原方案偏得最明显的地方。质量此后也
不再有"拉回慢所以静止时停在偏移位置"的副作用，只剩跟随延迟和滞后。

### 预设动画运动驱动

只更新 `restDirection` 可以表达 driven bone 自身关键帧旋转的自然拖尾，
却不能表达动画附着点平移；直接再注入同一个局部旋转又会重复计入。
当前实现因此在物理写回前构建一份纯动画层级，并把每段的**有效 pivot**
作为安装点。与实体速度信号保持同一 20 TPS 单位：

```text
v_pivot = (p_current - p_previous) / (20 * deltaPoseTime)
a_pivot = (v_pivot - v_pivot_previous) / deltaPoseTime

q_delta = q_mount_current * inverse(q_mount_previous)
omega   = quaternionLog(q_delta) / (20 * deltaPoseTime)
alpha   = (omega - omega_previous) / deltaPoseTime
```

`deltaPoseTime` 不是固定渲染帧间隔，而是从上一次**实际姿态变化**起累计的
`tickCount + partialTick` 动画时间。若动画控制器在多个渲染帧间保持同一值，采样器保留上一加速度
目标并继续低通，不把这些帧写成零速度；下一次姿态更新再以整个累计时间
求导。这样 20 TPS、帧率限制或不规则更新节奏下的连续动画不会形成
“保持→脉冲→反向脉冲”。姿态保持 `125 ms` 后加速度目标回零，但上一
有效姿态与速度基线保留到 `250 ms` 才确认静止，避免低更新率动画的下次
样本被误除以单个渲染帧间隔。

`q_mount` 使用 driven bone 的动画父级/安装参考，而不是该 driven bone 的
局部关键帧旋转；后者已通过 `restDirection` 进入系统。当前动画 segment
向量为 `r`，则额外计算：

```text
a_angular = alpha × r
          + 20 * omega × (omega × r)
          + 40 * omega × radialScaleVelocity
a_scale   = radialScaleAcceleration
```

最终信号按部件类型和约束烘焙：

```text
a_animation =
    typeGain * (
        rotationInertiaScale * a_pivot
      + rotationInertiaScale * (1 - rotationInertiaScale) * a_angular
      + 0.25 * rotationInertiaScale * a_scale
    )
```

积分器再乘已有 `SpringProfile.inertiaScale` 并取反作为滞后力。
若记 `rotationInertiaScale=s`，角项使用 `s(1-s)`：`s=0` 时参考完全跟随，不应产生旋转惯性；`s=1`
时旧的模型空间状态已经保留全部旋转惯性，也不应再注入一次。中间值只
补足参考搬运未表达的角加速度、向心和缩放/旋转耦合。

动画加速度经过径向软死区、`0.5` 软限幅和时间常数 `40 ms` 的指数低通。
HAIR、TAIL、EAR、SKIRT、RIBBON、CAPE、WING 和 GENERIC 使用独立有界
类型增益；固定头壳及自动悬垂饰品现有的低 `rotationInertiaScale` 会自然
压低该信号，不会因加入动画导数重新出现大幅扫动。

### 突变处理

首次采样、暂停、非有限动画时间、时间轴间隔超过 `0.25 s`、模型切换、
实体传送或状态恢复时重建运动历史。相同或乱序的 `tickCount + partialTick`
视为同一动画样本，整个 solver 使用 `dt=0` 重新写回但不推进状态。单次安装参考旋转超过 `75°`、动画 pivot 跃迁
超过 `max(0.5 block, 3 × segmentLength)`，或 segment 缩放长度比超过
`2.5` 时也判定为关键帧切换：动画加速度立即清零；参考骨旋转突变还会
把当前和历史方向重置到动画 `restDirection`。恢复后的第一帧只建立
基线，不把不连续姿态当成真实速度或加速度。

### 控制器限流与硬编码尾巴

`AnimatableEntity.setCustomAnimations` 在 Gecko 帧率限制器拒绝更新时返回
`false`，但 `GeckoMaidEntity` 仍在父类返回后调用硬编码动画。默认
`tail/default` 会继续按 `tickCount + partialTick` 写尾根 X/Z，因此返回值
不能表示“本帧骨骼完全没变”。只在 `true` 帧解算会令尾巴在纯动画和
动画叠加物理之间交替。

当前 `AnimationPoseSnapshot` 在 solver 写回前保存所有 driven bone 的六个
局部 rotation/position 通道；Mixin 在下一次 `setCustomAnimations` 的
`HEAD` 先恢复它，并在所有 `RETURN` 上重新解算。这样被限流帧仍能消费
硬编码尾巴的新姿态，同时旧物理偏转不会叠加为动画输入。快照数组在 solver
构造期分配，恢复和捕获均不产生逐帧对象。

推进时间不再读取 `System.nanoTime()`。Mixin 传入与 `tail/default` 相同的
`tickCount + partialTick`，每实体的 `AnimationTimelineClock` 只接受严格
向前的动画样本。同一姿态因额外渲染阶段进入多次时只首次推进 Verlet，
后续调用为 `dt=0`；因此微小墙钟子步不会沿 `Tail → Tail7` 的多段局部
旋转逐级放大。

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

### 局部 Plane（不再自动用于头部）

通用 Plane 仍支持在显式代理中生成随参考骨运动的局部平面：

```text
C_plane(p) = dot(p - planePoint, outwardNormal) - hitRadius
```

约束要求 `C_plane >= 0`。若小于零，则：

```text
p = p - C_plane * outwardNormal
```

这与 VRM 扩展的单侧平面碰撞和 APEX Backstop 的用途一致：
模拟点可以在外侧摆动，但不能越过蒙皮或头发外壳进入内部。

### 网格 Box / Plane / Sphere / Capsule

自动布局直接使用网格，粒度是 **cube 而不是骨骼**：每个属于非受驱动、
无受驱动祖先骨骼的静止 cube 成为一个 Box 代理。cube 自带 `dx/dy/dz`
边框，所以 Box 与网格严格等同并保留 cube 自身旋转；若按骨骼取 AABB，
散布着几十个 cube 的发套、法阵或披挂会被膨胀成大半是空气的包围盒，
既挡住空处又看不出对应几何。头、上身、手臂、腿都不再需要作者摆放胶囊。

- `HAIR`、`EAR`、`HEAD_LOCAL RIBBON`：头、上身及可达手臂的网格 Box；
- `SKIRT`、`BODY_LOCAL RIBBON`：躯干与左右腿的网格 Box；
- `CAPE`：网格 Box + 随 Body 运动的 Back Plane。

附着数量**不设上限**：一个段够得到的 cube 全部保留，碰撞面因此与模型
一一对应，而不是靠“挑几个代表”近似。只做两类必要剔除：

- **无法形成接触的薄片**：厚度不足 `0.5 px` 的 cube 与末端球只会产生
  左右翻面抖动，永远稳不下来，发光层和法阵面片属于此类；
- **摆动锥够不到的几何**：末端恒位于半径为力臂的球壳上，并被摆角限制
  在静止方向附近的一顶球冠内，因此判定用 `SwingCone` 而不是扫掠球——
  “半径合适但在身后”的 cube 直接出局。参考骨自身也会转动，故按
  `0.35 rad` 弦长为远离参考枢轴的 cube 补偿余量，摆动包络另留
  `1.4 rad`。枢轴已深埋超过 `1 px` 的普通候选、被已选 Box 覆盖 `75%`
  以上的嵌套件同样丢弃。与身体地标重叠的刚性 cube 不使用埋入启发式，
  因为裙根 pivot 通常就位于腰部网格内部，但仍必须通过同一摆动锥可达性
  检查。

`winefox` 由此得到 `3234` 个代理覆盖 `80` 个受驱动段，远处的灯笼、武器
仍不进入热路径。数量放开后的成本由共享形状、逐帧锥剪枝、组内空间细分、
Top-K 求解、延后绑定和间隙留存六层结构控制（见性能门槛）。若某模型完全
没有可用刚性网格，
才回退到旧的拟合 Body Capsule。

### 布料层碰撞

上面这套只认刚性骨，因此两片同为受驱动的布料互相穿过时它无能为力：
围裙压在裙面上、外褂压在裙撑上，双方都在动，谁也不是对方的碰撞体。
[`ClothLayerPlanner`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/collision/build/ClothLayerPlanner.java)
补这一层，成对关系**严格单向**：外层把内层当碰撞体，内层完全看不见
外层。这样既保住作者排定的层序，也杜绝两片软布每帧互推形成震荡。

层序判定全部来自静止几何，不依赖命名：

- 两块 cube 都是**薄板**——最薄轴不超过次薄轴的 `0.5`，蝴蝶结、绳扣一类
  的方块因此出局；
- 两块板的法线夹角在 `20°` 内，且相对躯干水平轴同侧（`dot ≥ 0.30`），
  裙子前后襟这种隔着身体的组合直接排除；
- 沿法线的错开量超过较薄一方厚度的 `40%`：低于此值是同一张布拆成多骨
  的共面片，围在胯上的一圈裙片实测彼此只差不到五分之一个板厚；
- 两片之间的净空气不超过 `3 px`，再远就是各穿各的两件衣服；
- 面内两轴的交叠都要达到较窄一方的 `35%`，只贴着一条边不算被覆盖。

若两根骨互为对方内衬——绕过胯部的裙片会一块在前、另一块在后——则认定
无层序，双向丢弃。判定在构造期对整个模型做一次，因此结论对每个段一致。

碰撞体就是内衬那块 cube 的**原始尺寸**，只是拆掉了朝内的那一面。`1 px`
的实心薄片拦不住一帧就能越过它的末端：穿到另一侧后反而报告“无接触”，
看起来就是直接跳了过去。半开棱柱没有背面可落，末端陷得再深也只能从
正面被推出。曾经改用向内加厚 `4 px` 的板来解决同一问题，代价是调试
叠加里的碰撞盒明显大于模型本身，作者无从判断它到底贴不贴合。

半开形状必须让判定量和推出方向出自同一套度量。间隙按“落在面内轮廓里
就是正面深度、否则是到棱柱的普通距离”计算，接触面就必须同样地在轮廓内
取正面、在轮廓外取最近的侧面。若一律按正面推出，一个擦着侧边的
`1e-9` 交叠会被当成“陷在整块板后面”，一帧炸出 `0.05 rad` 的摆角。

末端半径取**外层布片自身的半厚**，而不是从受驱动骨的整体比例推导：后者
对一片 `1 px` 的布会给出接近 `2 px` 的球，把两层顶开一道肉眼可见的缝。

运行时这些代理走与网格 Box 完全相同的共享形状、锥剪枝和 Top-K 路径，
唯一区别是参考骨是受驱动的，因此 [`RuntimeCollisionFrames`](../../src/main/java/com/laixia/maidintelligence/feature/physics/client/solver/spring/RuntimeCollisionFrames.java)
对它改用 solver 上一趟写出的变换而不是动画姿态——动画姿态并不是网格
实际所在的位置。滞后一帧在布料上看不出来，换来的是预捕获仍然是单趟
prepass，不必在节点循环中途重入刷新。`winefox` 由此新增 `9` 个代理，
`zhiban_hanfu` 的和服外褂与里衬共形成 `55` 对。

碰撞检测使用每段骨骼末端球，而不是无半径点。`hitRadius` 从该骨骼
几何横截面估计，并可由 schema 3 元数据覆盖；投影始终维持固定骨长。

当前碰撞数据统一为不可变的 Box / Plane / Sphere / Capsule 代理数组。
每根驱动骨可关联零到多个代理，每个代理独立记录形状参数、
`referenceNodeIndex`、有效 pivot、`hitRadius` 和力臂；因此同一骨骼
可以同时受随 Head 运动的网格盒、随 Body 运动的平面和模型空间代理约束。
旧 `backstop` / `head_collision` 字段继续解析，但不再烘焙拟合形状；
显式 Plane/Sphere/Capsule 的投影公式与顺序保持不变。

Box 在参考骨局部空间求点-盒最近点：外部按 clamp 得到面/棱/角接触，
内部沿最浅轴逃逸，随后复用平面半空间投影，无需迭代搜索。运行时三条
轴各自随仿射变换旋转，轴长直接乘进对应半轴，非均匀缩放的肢体依然
被精确跟随。

Sphere 与退化 Capsule 使用固定骨长的解析最小点积边界；普通 Capsule
先求端点到线段的最近点，再做有限次固定骨长半空间投影。端点落在胶囊
轴线或胶囊退化为点时使用确定性法线/球体回退，避免零向量归一化。
所有代理对象、代理数组、准备态和 `CollisionScratch` 均在布局或 solver
构造期分配，逐帧只遍历数组并复用向量。多代理在一次完整遍历没有修正时
立即退出；较高的固定上限仅用于两个近切曲面约束的慢收敛穿透反例。

Body/Back 自动代理及所有显式代理仍从驱动骨几何最小横截面估计
`hitRadius`。碰撞连接点使用 `BoneKinematics` 校正后的有效 pivot，而非
可能远离网格、位于中心或处于柔性远端的作者 pivot。
附着端使用加载期接触感知优化：对受驱动主 OBB 簇的六面固定采样，
在父级/最近实体祖先 OBB union 上求最近点对，并以 `0.5～2 px` 自适应
带宽形成接触带。样本经过面积/距离加权、离群过滤、空间聚类和沿子件
主轴的集中度检查；横穿整个支撑体的薄片会降低置信度。

候选枢轴包含接触带中心、主接触簇中心、authored pivot 向接触平面的
受限投影、全局最近点对中点、旧几何附着端和 authored pivot。候选在两个
正交轴上离线摆动 `±5°/±10°`，目标函数惩罚接触分离、相对静止姿态新增
的穿透、偏离接触中心，以及仅对本就靠近接触带的 authored pivot 启用的
作者先验。最终 pivot 可以位于子件与支撑面的间隙中，不再强制投影到
child cube 表面。

author pivot 位于自身网格且距可靠接触带不超过 `2 px` 时优先保留；
低置信结果保持 authored pivot 并收紧摆角，明显远程 pivot 才允许中等
置信度纠正。`DANGLING_ACCESSORY` 等单骨结构角色不能豁免真正异常 pivot；
未明显脱离网格的真实多段 authored chain 仍保留各自关节。`HEAD_SHELL`
优先使用宽面接触的保守阈值，无可靠支撑时回退质心。静态碰撞连接点继续
通过匹配 Gecko 渲染顺序的绑定姿态仿射矩阵变换到碰撞空间；详细算法见
[`CONTACT_AWARE_PIVOT_INFERENCE.md`](CONTACT_AWARE_PIVOT_INFERENCE.md)。

所有单骨候选还会检查重力下的支撑稳定性。稳定方向由支点与质量中心的
相对位置决定，绝对质量和模型尺寸不会改变倒置关系，因此不再使用 cube
数量、最小跨度或惯性载荷门槛。上下死区按模型尺度计算为
`clamp(diagonal × 0.025, 0.125 px, 0.5 px)`，候选相对 authored pivot
所需的最小向上改善量为
`clamp(diagonal × 0.05, 0.25 px, 1 px)`。

若 authored pivot 落在质量中心下方，而置信度至少 `0.15` 的接触优化
候选落在质量中心上方并满足改善量，则单 cube 小饰品和大型复合附件都
采用上方候选。真实多骨链不走该规则。Hair/Skirt 等类型只修正支点并
保留原动力学；自动 Ribbon/Cape/悬垂饰品改用
`COMPOUND_SINGLE_BONE` 稳定档，零静态重力并收紧惯性与摆角。

Ribbon/Cape/悬垂饰品同时有反向保护：如果 authored pivot 贴近自身网格
且尚未低于质量中心，而推断候选会把它明显移到质量中心下方，则保留
authored pivot 并使用 `supportStabilityPreserved` 标记。华服小蝴蝶结
`bone101/bone103` 因此不会再被接触优化从约 `Y=41.06` 下移到
`Y=38.1`。该保护不用于普通 Hair，以免把真正位于根部的上生发束支点
误判为不稳定。

紧凑复合部件还检查可见质量中心力臂。对于至少三个可见 cube、次长轴
不小于最长轴 `0.55` 的单骨 Hair/Ribbon/Cape/Generic，如果置信度至少
`0.20` 的接触候选能按模型对角线显著缩短 authored pivot 的质心半径，
即使 authored pivot 距某个外围小 cube 不到 `2 px` 也允许纠正。真实
多骨链以及 Ear/Wing/Tail/Skirt 不使用该规则，细长发束也会被轴比拒绝。
命中后写入 `attachmentLeverCorrected`，并使用零静态重力、`0.12 rad`
安全角和低转动惯性的固定饰品档。华服 `bone109` 的可见力臂由约
`6.17 px` 降到 `2.96 px`，身份 rotation 下 position 补偿保持为零。

只有结构分析已经明确标为 `DANGLING_ACCESSORY`、却没有可信上方接触的
单骨附件才回退为 `RIGID_ATTACHMENT_BASE`，并写出
“lacks stable upper support”原因。证据不足的普通上生发束、耳朵、裙摆
和其它悬臂保留 authored pivot，显式 metadata 仍可覆盖自动回退。

积分器的平移惯性固定为 `-modelAcceleration`，全局符号本身不按模型变化。
局部模型出现同向前倾时，原因是 axis 指向了可见主体背面：模拟 tip 正确
向后，但被该 bone 旋转的 cube 主要位于 tip 反侧。单骨和真实链末端因此
增加最终极性检查；当全部可见 cube 的质量中心距 effective pivot 超过
`0.5 px`，且沿 axis 明确落在反侧超过 `0.25 px` 时，翻转 axis 并用全几何
重算 segment length。轻微歧义继续服从刘海/裙摆语义端点；真实链非末段
仍指向下一 authored joint，不能为极性修正破坏关节连续性。
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
- Body/Back 表面方向来自静止几何，并在退化时回退到模型朝向；
- 代理尺寸来自静止几何边界；
- 只把代理分配给相关链，不能让无限平面影响整个模型。
- 自动拟合造成的初始静止交叠在 solver 首次准备或 reset 后标定为允许
  边界，只阻止二级运动继续深入；因此 `dt=0` 初始化不会改写当前动画
  rotation/position。显式代理不使用该宽限，仍严格执行作者边界。
- 自动代理的边界逐帧相对当前动画姿态，不区分具体动作：当前作者方向在
  刚性 reference 中的穿入量立即成为临时边界，风、重力和惯性只能到此
  为止，不能继续深入。作者随时可以让模型自穿（坐、抱、蹲），绝对边界
  会与动画逐帧对抗并表现为高频抖动或整片挤位。姿态离开后按 `0.20 s`
  时间常数收回，避免硬边界瞬间恢复造成弹跳；显式代理和受驱动布料之间
  的层碰撞不启用这条路径。
- 宽限只能以 `2 px/s` 变宽，且首帧校准值同样会衰减、不作为永久下限。
  "动画自己造成了重叠"底下藏着要求相反的两件事：姿态**落进**重叠是作
  者剪影，必须放；碰撞体**扫过**去是布料本该反应的，放了就是腿穿过裙
  子。区分二者的是持续时长的量级（踢腿接触零点几秒，坐下后腿长期压
  着），所以宽限干脆跟不上快速接近，无需任何分类器，两端退化也都合理。
  额度按“接触中经过的时间”发放并按步长封顶：同一个 `elapsed` 还要服
  务于释放（离开视野的配对须按真实时间交还表面），拿它换增长额度会让
  刚回来的碰撞体一帧领到整秒的放宽。首帧校准只作初值——永久保留等于让
  某个任意帧说了算，而腿静止时本来就在它将来要踢的裙子里面，那一帧对
  其它帧毫无说明力，等于给了腿永久豁免。
- 陷入 Box 的末端沿上一帧那张面推出。出口面原本每帧按最浅穿透轴重选，
  是整套求解里唯一的离散决策：立方体对角线上两面等深，待机姿态在平局
  线附近颤动就会让推出方向逐帧转 `90°`，表现为高频振动。对手需浅出
  `1/64` 方块才能夺面；末端漂过盒心时同样沿用旧面，否则会被从对侧推出
  造成 `180°` 跳变。离开碰撞体即清除记忆，明显更浅的面照常接管。
- 投影修正连续**三帧**反向时，`SpringProjectionDamper` 逐帧把响应降到
  `12%`，使矛盾要求收敛到两侧各浅浅陷入的折中姿态而不是来回跳。单侧
  接触方向恒定，不触发；无反向后按 `0.25 s` 恢复。要求连续而非单次是
  必需的：无解的挤压是周期 2 的极限环，每帧都反向；横扫过去的肢体每个
  步幅只反向一次，按单次降响应正好让裙子对被踢失去反应。这样无需测量碰
  撞体运动量即可分开。
- 每段除末端外，再对**碰撞体正对着的那个段身位置**投影一次。投影本就
  接受“放置采样点的力臂”并用它去除修正量，因此传入缩短的力臂即等于在
  段轴内侧求解，无需任何新几何。采样点取最正对的一点而非固定阶梯——那
  里最先也最深地咬进来，阶梯要为找到同一位置付出每级一次投影。末端与
  段身各自持有独立的出口面记忆与静止宽限：共用一份会让段身的作者交叠
  （裙片压在它挂着的胯上）顶高末端阈值，段于是对任何浅于既有深度的东
  西都不再抵抗。无限平面不参与段身采样：它没有“中心”可正对，且作为半
  空间，直段末端脱离即全段脱离。

  重力改为相对姿态求解后，段身采样从"补充"变成了**唯一**的检测手段：裙
  片不再被重力往外拽，就挂在作者画的位置，末端因此落在腿的行程之上、从
  头到尾碰不到腿。`winefox` 上关掉段身采样，横扫响应由 `0.17` 掉到
  `0.010 rad`。
- 投影结果另按 `40 rad/s` 限制帧间可见位移，使大幅违反在数帧内解开而
  不是一次瞬移。预算衡量帧间结果而非单帧内部工作量：摆角与碰撞在四轮
  交替中经常大幅互相抵消却几乎没有净位移，按内部工作量计费会让两侧硬
  边界都拉不满、段永远差一点贴不到面。阈值远高于弹簧自身速度：接触中
  的段每帧都被修正，预算一紧会连带扼住它跟随快速动画，先落后再追上同
  样是卡顿；抖动由上面两条在源头处理。`dt=0` 预算为零。

schema 3 sidecar 可在 `constraints.collision.proxies` 中显式添加 Plane、
Sphere 和 Capsule；静止坐标与半径使用 Gecko 像素（`16 px = 1` 模型
空间方块），`hit_radius` 可省略。`reference` 支持 `MODEL`、唯一 `ROOT`
和能唯一解析的路径/节点。`auto` 默认为 `true`，显式代理追加到自动代理
之后；`auto:false` 只保留有效显式代理。缺失或歧义 reference 只跳过对应
代理，不影响同链其它代理。

旧 `backstop` / `head_collision` 开关继续解析，但不再生成拟合形状；
schema 1/2 不读取 schema 3 的 `collision` 字段。头部和腿部现在由网格
Box 自动覆盖；显式代理用于补充网格无法表达的边界（如空中禁区）。

### 调试可视化与 dump

手持骨骼调试棒时，亮青线显示逐段 runtime pivot/tip；Plane 为蓝色、
Sphere 为绿色、Capsule 为琥珀色、网格 Box 为紫色、布料层内衬为品红，
reference origin 以同色十字标出。画的是本帧真正进入求解的代理，而不是全部候选，因此叠加层与
solver 的实际工作集一致。几十个受驱动段常常共用同一个刚性碰撞体，因此
形状相同且末端半径相同的代理只画一次，穿透中的代理始终单独重绘为红色。当前末端对某代理的调整后 `clearance < 0` 时，该代理整组改为
红色，表示穿透超过了自动代理首次标定的静止交叠（显式代理仍以绝对
边界计算），而不是声称整个渲染网格都已精确检测。

右键女仆会把实际 `modelId`、裙摆/饰品结构角色与判定原因、链段序号、
重力倍率、参考空间、四向摆角、碰撞策略、authored/effective pivot、
`supportStabilityCorrected` / `supportStabilityPreserved` /
`attachmentLeverCorrected` / `supportStabilityUnsupported`、`jointSpacing`、逐段 runtime 几何、reference、
代理形状、形状/末端半径、力臂、`AUTOMATIC/EXPLICIT` 来源、clearance 和穿透标记写入
`run/logs/latest.log`，聊天栏给出代理与穿透数量摘要。

## 刚性连接点与柔性后代

不能把“位于头发层级中”等同于“整根骨骼都应物理化”。

应区分：

- **运动学连接点**：发夹、发饰底座、丝带结、固定扣件；
- **柔性后代**：吊坠、流苏、丝带尾、长发分段。

运动学连接点完全使用动画变换，等价于 PBD 中逆质量为零的附着点。
只有其柔性后代进入 solver。若模型把底座和摆动物合并在一个骨骼里，
则使用更严格的局部角限制；不能凭空拆分网格。

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

## 裙摆与悬垂饰品

自动发现继续复用 `SKIRT` / `RIBBON` 求解类型，不增加逐 cube 写回：

- `qunzi`、内外/前后裙、`Dress` 及中日韩常见裙摆名称提供语义证据；无名称时使用下半身位置、宽薄面板、上缘连接和分支拓扑；
- 匿名宽面板还必须没有主导体积核心：占节点 AABB 至少 `10%`、最短/最长边至少 `0.45` 的厚 cube 是短裤、夹克或骨盆壳证据，不是布片。环形裙壳的组成 cube 仍是薄板，明确裙摆语义也继续优先；
- `guashi/pendant/tassel`、吊坠和流苏，以及细长、上部附着的身体挂件作为 `RIBBON` 候选；空裙根只向可见子件传递语义，本身不进入 solver；
- 带几何的短裙根若同时承载至少两块明显更长、向下延伸的独立命名裙片，也作为 `RIGID_ATTACHMENT_BASE`；子裙片各自驱动，安装座不再绕身体中心倾斜并把对侧压入躯干；
- 位于头侧、非包头、从上部悬垂且没有眼嘴/表情后代的 `Mask` 使用零静态重力、保留惯性摆动的 `HEAD_LOCAL RIBBON`；`SHmask/faceplate`、头盔、贴脸面具、带表情子树的面具和手持树保持刚性；
- 新结构角色 `DANGLING_ACCESSORY` 烘焙高参考跟随、高阻尼、零静态重力、低旋转惯性和小四向摆角。它响应实体移动、转身和预设动画的有界瞬态惯性，但不会让刚性主体在静止或匀速头部动画下掉到眼睛前；
- 基础纸板狐 `bone37` 的 cube 分布在头部多处、各自拥有不同 cube pivot/rotation，且不存在占优连通簇；它与 `bone32`、`bone11/38` 及同样无主导簇的 `guashi` 均保持刚性，`qunzi` 继续使用单骨裙摆档位。

单骨 `Dress/qunzi` 使用保守 `COMPOUND_SINGLE_BONE` 裙摆档位。真实
`SKIRT` 父子链使用更保守的根硬梢软曲线，并消费可达的刚性 cube 网格，
仅在没有可用网格时回退 Body Capsule；
头部悬垂饰品默认不消费碰撞代理。

## 真实链段的根梢动力学

每个 driven bone 在计划构建期记录真实链根、段序号和段总数。这里只沿
实际 parent-child 关系连接段，不从数字后缀猜测，也不把左右分叉合并成
同一自动 chain。显式 metadata 链只记录拓扑，不修改作者 profile。

自动发现的多段 `HAIR/RIBBON/CAPE/TAIL` 使用有界插值；多段 `SKIRT`
使用更高根部刚度、更低根部惯性和更小摆角的独立保守曲线：

- 根段提高刚度和阻尼、降低移动/转身惯性与摆角，并更强跟随参考骨；
- 梢端逐步降低刚度和阻尼，提高移动/转身及旋转惯性，允许相对父段滞后；
- 每段仍独立执行力臂角限、四向摆角和碰撞，父段物理偏转继续正确传给
  子段 pivot/rest frame；
- 真实链非末段直接用当前 effective pivot 到下一段 authored pivot
  烘焙 axis/segment length；运行时 parent tip 与 child pivot 始终落在
  同一关节，非根段不会被主 cube 簇校正到同一个虚拟支点。

因此多骨双马尾会产生段间相位差，而不是靠破坏父子变换来制造弯曲。
对所有 cube 只绑定一个 bone 的长网格，渲染只能接受一次骨变换：
系统使用更高刚度/阻尼及更小 angle/tip 档位控制整体摆动，不伪造与
碰撞、调试或 Sodium 渲染不一致的逐 cube 虚拟关节。

运动学同时区分“主连通 cube 簇”和“全部可见几何”。cube 连通使用带
margin 的 OBB/SAT，而不是会把相隔细长方块误连的包围球。当主簇足够
占优时，attachment pivot/axis 与实际 segment length 使用主簇，避免
远处小饰件拉偏旋转轴或碰撞端点；独立 safety lever 始终覆盖全部 cube，
保证脱离主簇的装饰仍会收紧安全摆幅。
若自动候选包含多个互不连通且没有任何簇达到主导权重的 cube 组，单一
bone rotation 无法同时保持各组作者位置，系统会把整个自动 chain 改为
刚性。schema sidecar 的显式 chain 保持最高优先级，可由模型作者覆盖。

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

- 每个实体的动画时间轴时钟，合并相同/乱序动画时刻的重复渲染；
- 每个活动参考骨的上一帧四元数和本帧共享旋转增量；
- 每个 driven slot 的动画局部 rotation/position 可逆覆盖快照；
- 每个活动节点的纯动画层级变换；每个 driven slot 的上一动画 pivot、
  线速度、安装参考四元数、角速度、segment 长度/缩放速度和滤波输出；
- 碰撞依赖节点在物理写回前捕获的 animation affine delta、
  inverse-transpose 法线矩阵和保守谱尺度上界；
- 每个活动节点的层级仿射变换与模型空间 pivot；
- 每个驱动节点的模型空间几何 tip、实际缩放后 segment length，以及
  层级最大 scale 上界下的 safety lever 角限；
- 每代理逐帧准备一次的运行时几何，以及 Plane / Sphere / Capsule
  共用的端点、最近点、法线和四元数 scratch；
- 每个驱动槽的当前/历史方向。

公共入口 `SpringBoneSolver` 仅作为兼容门面；状态、scratch、姿态组合、
动画姿态捕获/导数采样、参考空间搬运、积分、约束、偏转写回和端点传播分别位于
`solver/spring` 子包的独立模块；碰撞形状、固定骨长投影、代理集合和
自动/显式代理烘焙和准备态位于 `solver/collision` 子包；各模块保持
单一职责。

XPBD 约束乘子按设计不分配，除非以后确有链间柔性关系需求。

不得在逐帧热路径创建集合、流、临时 `Vector3f` 或 `Quaternionf`。
代理只随其活动参考骨更新，远处实体仍沿用现有物理禁用和生命周期策略。

## 验证覆盖

### 行为不变量

- 抬头、低头、左右看时，刚性发饰底座保持动画相对位置；
- 最终方向始终位于非对称摆角区域；
- 碰撞后骨长保持不变；
- 父骨物理偏转、位置补偿和非均匀缩放会传递到后代 pivot/tip；
- 静止预设动画的逐骨运动信号严格为零，连续关键帧 position、rotation
  和 scale 加减速均能产生有界信号；
- 控制器跨多个渲染帧保持姿态、以不规则间隔更新或发生同帧重复调用时，
  惯性保持连续；动画切换、暂停和从同一姿态恢复不会注入假冲量；
- 状态中不残留朝约束内部的速度；
- 不出现 NaN、零向量归一化或瞬间翻转。

### 固定测试序列

现有离线回归覆盖：

- schema 1/2 兼容、schema 3 解析、Gecko 像素换算、可选
  `hit_radius` 和无效代理独立跳过；
- Plane、Sphere、Capsule、Box 几何、胶囊退化、Box 面/角投影、
  自动/显式合并及 `auto:false`；
- 网格 Box 自动派生：长头骨的半轴与 cube 精确一致、发丝拿到头部、
  裙摆拿到躯干与左右腿、不可达远处骨被剪枝、扁平发光层被拒、
  受驱动骨不作网格参考、多 cube 参考骨的可达 cube 一个不漏、`winefox` 与 `zhiban_hanfu`
  的每个 Box 都能对上参考骨的某个真实 cube，以及驱入头骨时停在表面 /
  关闭碰撞则穿入的对照；
- 布料层碰撞：围裙认下面的裙片为内衬而裙片看不见围裙、共面并排的裙片
  不成层、互相环抱的两片双向丢弃、半开内衬确实挡住向内压的围裙，以及
  把内衬的动画瞬间弹回静止时碰撞体仍停在解算姿态而非跟着动画归零；
- CAPE 在网格 Box 之外仍附加 Back Plane；
- 受驱动父子链、父物理偏转、虚拟 pivot position 补偿、非均匀 scale
  与后序显式 sibling Leg reference；
- 接触感知 pivot 的 `1～2 px` OBB 间隙、宽面 `HEAD_SHELL`、横穿支撑体
  的低置信接触、远程 authored pivot、有效 authored pivot 保留，以及
  不再要求 corrected pivot 位于 child surface；
- 全部 27 个模型的单骨/链末端可见质量轴扫描，覆盖自动极性翻转且禁止
  `axis · (visibleMassCenter - effectivePivot) < -0.25 px`；
- animation affine 捕获、非均匀 scale/剪切尺度上界、准备态与直接投影
  等价、reset 和穿透清除；
- 预设动画枢轴平移、安装参考角加速度和 scale 导数驱动，静止零力，
  30/60/120 FPS 峰值一致性，以及大角度/大位移/大缩放切换和暂停恢复
  的导数历史重建；另以 120 FPS 渲染、4/5/7/8 帧不规则 sample-and-hold
  源验证加速度无高频反向脉冲，并验证 `dt=0` 重复样本不清空历史；
- TLM `tail/default` 在 Gecko 控制器限流帧仍更新时，上一物理覆盖的六个
  局部通道可完整恢复；实际 `winefox` 七段 `Tail → Tail7` 以每帧
  0～2 次重复调用验证相同动画时刻只推进一次，末端输出连续且不累积；
- 全部 27 个内置模型在 bind pose、首次动画姿态和 solver reset 后的
  `dt=0` 初始化均不改变任何 driven bone 的 rotation/position；
- 自动 reference 的 self/受驱动边界贡献安全检查，以及近切、近反向
  Plane 对的狭窄可行域投影；
- 纸板狐匿名紧凑发饰、镜像单骨马尾、年糕狐发球，以及 metadata 对
  自动刚性判断的优先覆盖；
- 27 个内置模型中的圣女酒狐 `SkirtBone*`、基础酒狐 `clothe/LF*`、
  纸板狐 `qunzi`、年糕狐 `Dress`、克鲁诺亚 `SkirtUp` 和婚纱
  长裙；覆盖类型、独立分支、连续段序、根梢曲线及 Body-only 代理；
- 基础/婚纱酒狐侧挂 `Mask` 零静态重力惯性驱动，以及带面部子树的
  `winefox_little Mask`、`winefox_astronaut SHmask`、头盔和手持树刚性；
- 基础纸板狐 `bone32/bone37/guashi` 及汉服对应分布式饰件保持刚性，以及汉服/新年
  左右 `MWX` 三段 authored/effective/runtime pivot 分离、端点重合；
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
recursive-full: 35834.0 ns/frame, nodes=181/181, allocation=14560.00 B/frame
iterative-active-legacy: 37247.0 ns/frame, nodes=153/181, allocation=0.00 B/frame
iterative-active-constrained: 288676.7 ns/frame, nodes=153/181, allocation=0.00 B/frame
legacy-equivalent solver speedup: 0.96x
constraint-layer cost: 7.87x legacy-active
constrained collision proxies: 3234 over 80 driven segments

head/schema2-auto-disabled: 3734.3 ns/frame, proxies=0, allocation=0.00 B/frame
head/schema3-auto-disabled: 3157.2 ns/frame, proxies=0, allocation=0.00 B/frame
skirt/schema2-body-disabled: 2671.9 ns/frame, proxies=0, allocation=0.00 B/frame
skirt/schema3-body-only: 6928.7 ns/frame, proxies=11 [Box=11], allocation=0.00 B/frame
```

绝对耗时会随机器波动；稳定门槛是生产约束路径继续保持
`0 B/frame`，并单独报告旧积分等价路径与约束路径。

`constraint-layer cost` 是整条约束路径与无约束路径之比，因此包含纯动画
层级、逐段导数预采样、参考空间搬运、摆角投影、姿态驱动以及碰撞。**它不
是碰撞自身的开销**，把它当碰撞成本读会一路指向错误的优化对象。要单看
碰撞，用 `skirt` 的两行对照：`11` 个 Box 摊到 `+3.1 µs/frame`。

代理数量不再设上限：能碰到的 cube 全部附着，`winefox` 由 `259` 升到
`3234`。控制成本的是六层结构而不是裁剪精度：

1. **共享形状**。同一 cube 常被几十个段引用，`PreparedCollisionShape`
   按几何去重，每帧只做一次仿射/法线变换；`3234` 个代理背后只有几十
   个唯一形状。代理本身只保留枢轴、力臂和静止宽限。
2. **逐帧锥剪枝**。代理按参考骨分组，组内几何刚性相连，其静止包围球
   随该骨变换后依旧有效，一次测试即可否掉整根骨。组内再逐个判定，且
   末端恒在力臂球壳上，偏离球壳超过自身半径的一次平方距离就返回不可
   达，绝大多数 cube 不进入开方与锥角运算。
3. **组内空间细分**。只按参考骨分组不够：一根骨的 cube 可以铺满整个
   身体，`BaseHair` 的 `59` 个 cube 罩住整个头，包围球大到否不掉任何
   东西，于是每帧为这 `59` 个逐一付费。分组因此沿最长轴继续二分到每组
   不超过 `8` 个（加载期一次，帧循环无成本）。细分只决定“测谁”，不
   决定“测出什么”——包围球必须始终覆盖它声称的内容，否则会静默丢掉
   正在接触的碰撞体，游戏里表现为布料穿过身体而 layout 里查不出原因。
4. **Top-K 求解**。末端球一次只可能被少数几个面挡住，而松弛循环每轮重扫
   全集，因此每帧只把最近的 `6` 个送进求解。裙摆被躯干和两条腿同时夹住
   已经不止三个面，再叠一层围裙就更多；上限过低看起来就是末端一边解某个
   面、一边无视另一个面。排序每帧按实时姿态重做，没有代理被永久丢弃，
   松弛循环一轮无修正即退出，空槽只花一次拒绝。
5. **绑定推迟到排序之后**。绑定要重算碰撞体缩放、末端与段身两份宽限，
   以及由宽限派生的四个半径；排序只用得到可达性判定，一样都不需要。
   因此绑定不再对“扫掠体恰好罩住”的每个代理都做一遍（`winefox` 上每帧
   `685` 次），只对最终留下的那几个做。
6. **间隙留存**（保守推进）。投影为了判断该不该推，本来就得先算出间隙，
   读回来不花钱；而在两侧相向移动量把它耗尽之前，这个间隙都还成立。一次
   测量因此能顶掉后续若干轮乃至若干帧的求解——末端采样实测有 `70%` 直接
   跳过整段几何。留存量按“自测量以来所有可能缩短它的运动”扣减，任何一项
   漏扣都是漏检：段自身转动（力臂乘转角）、段身采样点沿段轴的滑动（其力臂
   随碰撞体方位变化）、碰撞体在动画下的位移、枢轴移动、缩放变化，以及宽限
   释放导致的有效半径回涨。碰撞体那一项由 `PreparedCollisionShape` 给出
   表面点位移上界——中心位移加三条半轴端点位移，覆盖任意角点，无论肢体怎么
   转都不会低估。被剔除一帧的配对没人给它记账，故按帧序号判定不连续即作废；
   这比在剔除路径上逐个通知便宜，剔除路径每帧要走 `1615` 次。

逐帧重测作者姿态深度（相对宽限）曾是这条路径上单项最贵的一环：它对每个
存活配对每帧算一次完整 clearance，在 `winefox` 上摊到 `+158 µs/frame`。
现在同一个包围球判定先挡一道——Top-K 留下的是离**偏转后**末端最近的
碰撞体，而作者方向通常并不指向那里，绝大多数配对根本够不到，无需进入
Box 局部空间。

三项经测量确认不是瓶颈，因此没有为它们增加复杂度：每帧全量形状变换
（`11 µs`）、组内细分本身（`10 µs`）、松弛趟数从 `24` 降到 `6`
（`4 µs`，说明接触几乎总在一两趟内收敛，`24` 的余量不花钱）。

约束层随后从 `231 µs` 涨到 `289 µs`，两项来源：段身多出的那个采样点约
`+19 µs`，其余是宽限不再永久之后真正需要求解的接触变多了。后者不是回
退——此前那些接触是被宽限**定义掉**的，帧时间便宜，但腿会直接穿过裙
子。段身采样在投影与排序两处都加了包围球预筛，收益有限（`296 → 289
µs`）：裙段本来就被躯干和两条腿包住，采样点确实靠在碰撞体上，预筛否不掉。

重力改为相对姿态求解本身几乎不花钱（每段一个点积）。同一次会话内与旧的
世界重力做过对照：`298 µs` 对 `324 µs`，方向与直觉相反且落在噪声里。同
一份代码在不同会话测出过 `298` 与 `350 µs`，因此这个量级的差异只能同会
话对照，跨会话数字不可比。

跨会话不可比这一点后来直接妨碍了取舍，于是基准改成把测量帧切成 `5` 轮、
只报告最快的一轮：单一长平均把测量期间机器干的事一并算进去，在这个负载上
足有 `20%`，足以凭空造出或抹掉一次改动的效果。“间隙留存”据此用开关做了
A/B，各五个样本——最小值 `233` 对 `248 µs`，中位数 `252` 对 `270 µs`，
两个统计量方向与量级一致，约 `6%`。

只有个位数，是因为省下的几何求解本来就不是大头。分段计时给出的比例是：
每帧绑定与剔除约 `494 µs`，整个松弛投影只有 `83 µs`（该次计时含
`nanoTime` 自身开销，绝对值虚高，只能看比例）。**下一处该动的是剔除的
层次**：按参考骨分组再沿最长轴二分之后，每帧仍有 `1099` 次组测试却只否掉
`40.8%`，`2300` 次单体测试否掉 `70.2%`——组被切到平均只剩 `2.9` 个碰撞
体，粗筛几乎退化成逐个测，层次太浅。碰撞体在参考骨局部空间是刚性的，可以
在加载期离线建 BVH、运行时只搬根节点，比通用引擎为动态场景准备的动态 AABB
树省掉增量维护那部分。

## 已知限制

- 每段除末端外只再取**一个**段身采样点，且靠根部 `35%` 以内的接触被拉
  到 `0.35` 处求解而非原地求解：段绕根部旋转，太靠根的点无论转多少都
  几乎不动，硬要解出让它脱离的转角会把整段甩到模型另一侧。因此裙根一
  带的重叠仍不受约束——这是“绕支点旋转”的固有结果，不是漏检。末端摆
  动锥够不到的 cube 与厚度不足 `0.5 px` 的薄片仍不参与碰撞，故不保证
  完全无穿模。剔除层的口径仍以末端为准（可达性建立在“末端恒位于半径为
  力臂的球壳上”，`SwingCone` 会把偏离球壳超过自身半径的碰撞体判为不可
  达，**包括离支点太近的**），段身投影用的是同一批存活代理。
- 静止已交叠的部位只被阻止继续深入，不会被推开，避免作者姿态位移；区
  分“落进去”与“扫过去”的判据是 `2 px/s` 的速率，因此**极慢的**穿插
  动作仍会被当作作者意图而不纠正。
- 腿与裙片频率接近时两者同步、相对位移趋近于零，此时没有穿透需要解决
  （`winefox` 在 `0.5 s` 步幅附近如此）。这不是碰撞失效，但“被踢开的
  幅度”不能用单一步频衡量。
- 当前摆角与碰撞是固定骨长的硬 PBD，不提供 XPBD compliance；XPBD
  只在以后出现明确链间柔性关系时按需加入。
- 同一个 Gecko bone 内所有 cube 共享一次 TRS；占优主簇只改善枢轴/轴和
  安全包络，没有主导簇的分布式复合骨自动保持刚性，也不能让单骨马尾真正弯成多段。真实分段必须由模型提供多个
  不同 pivot 的父子骨；基础 `zhiban bone3/bone9` 因此只做单骨低重力
  限幅，`zhiban_hanfu/new_year MWX → MWX2 → MWX3` 才能形成可见分段。

## 分阶段落地

1. **已完成—参考空间搬运**：参考骨帧间旋转、共享增量缓存和突变重置；
2. **已完成—非对称摆角**：相对当前动画姿态投影并回写当前/历史状态；
3. **已调整—头部约束**：保留参考空间与非对称摆角，拟合 Head
   Plane/Sphere/Capsule 已由头骨网格 Box 取代；
4. **已完成—刚柔分离**：保守识别固定发饰底座，柔性后代继续发现；
5. **已完成—运行时端点层级**：传播父物理、position 补偿和非均匀
   scale 下的真实 pivot/tip；
6. **已完成—统一碰撞代理**：通用 Plane / Sphere / Capsule / Box、
   每骨多代理、每代理独立参考骨及零分配投影；
7. **已完成—网格自动碰撞与逐段碰撞**：每个相关受驱动段消费运行时
   pivot；碰撞体直接由刚性骨网格逐 cube 派生，只按可达性剪枝、不设
   数量上限，`CAPE` 追加 Back Plane，无需作者手工摆放形状；叠层布料
   之间按静止几何判定层序，外层单向以内层原尺寸的半开棱柱为碰撞体；
8. **已完成—发饰与马尾独立化**：结构化刚性底座、匿名镜像单骨马尾、
   主 cube 簇运动学及真实多骨链根硬梢软参数；
9. **已完成—裙摆与悬垂饰品**：裙摆/挂饰结构证据、侧挂面具刚柔拆分、
   单骨裙摆与真实裙链档位、零静态重力饰品和分离链关节；
10. **已完成—接触感知枢轴**：OBB 接触带、鲁棒聚类、固定候选虚拟摆动、
    置信度回退、Head Shell 接触优先、尺度无关的单骨支撑稳定性、紧凑
    部件外围杠杆修正、可见质量轴极性校正，并移除错误的 child-surface
    强制吸附；
11. **已完成—预设动画惯性**：纯动画 TRS 预采样、pivot 二阶差分、
    安装参考角速度/角加速度、scale 耦合、sample-and-hold 时间累计、
    分类型注入、软限幅、动画切换/暂停/重复渲染突变保护，以及 Gecko
    控制器限流期间的可逆物理覆盖与动画时间轴重复调用合并；
12. **按需实施—XPBD 柔性约束**：只在出现链间柔性关系需求时加入。

## 参考资料

- [VRMC_springBone 1.0 specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_springBone-1.0/README.md)
- [VRMC_springBone_extended_collider 1.0](https://github.com/vrm-c/vrm-specification/tree/master/specification/VRMC_springBone_extended_collider-1.0)
- [Unreal Engine AnimDynamics](https://dev.epicgames.com/documentation/unreal-engine/animation-blueprint-animdynamics-in-unreal-engine)
- [KawaiiPhysics Parameter Reference](https://github.com/pafuhana1213/KawaiiPhysics/wiki/Parameters-en)
- [NVIDIA APEX Clothing Backstop](https://docs.nvidia.com/gameworks/content/gameworkslibrary/physx/apexsdk/APEX_Clothing/Clothing_Module_Doc.html)
- [Position Based Dynamics](https://matthias-research.github.io/pages/publications/posBasedDyn.pdf)
- [XPBD: Position-Based Simulation of Compliant Constrained Dynamics](https://mmacklin.com/xpbd.pdf)
- [Unity Cloth](https://docs.unity3d.com/Manual/class-Cloth.html)
- [Kinematify / DW-CAVL](https://arxiv.org/html/2511.01294v4)
- [MotionAnyMesh](https://doi.org/10.48550/arxiv.2603.12936)
