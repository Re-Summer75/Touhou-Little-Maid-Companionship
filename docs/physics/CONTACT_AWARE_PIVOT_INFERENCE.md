# 接触感知枢轴推断实施方案

## 1. 目标

在不信任第三方 Gecko/Bedrock 模型 authored pivot 的前提下，为自动发现的
软体骨骼推断稳定、可解释的旋转枢轴，同时满足：

1. 静止姿态和 `dt=0` 不改变模型原始 rotation/position；
2. 枢轴由“受驱动部件与支撑体的连接关系”决定，而不是强制落在子网格表面；
3. 真实多骨链优先保留作者关节；
4. 无法可靠推断时保留 authored pivot 或保持刚性，不猜测；
5. 所有计算只发生在模型发现/布局构建期，逐帧热路径继续 `0 B/frame`；
6. 不引入原生库、神经网络或在线服务。

## 2. 问题定义

实施前的逻辑主要使用子骨网格的全局 PCA/竖直端点，并在低置信度时执行：

```java
mesh.closestPoint(authoredPivot)
```

对应测试又要求所有修正后的 pivot 到子骨 cube 的距离为零。这不是正确的
关节不变量：真实枢轴可以位于子骨与支撑体之间、支撑体内部或多个接触面的
中心。表面强制吸附会把带间隙、插入式连接、旋转 cube 和复合发饰推到错误端。

静态几何不能唯一确定语义关节，因此实现必须输出置信度并保留安全回退。

## 3. 采用的方法

采用适配 Gecko OBB cube 的 **Contact-Aware Virtual Pivot Optimization**
（接触感知虚拟枢轴优化），思想来自：

- Kinematify / DW-CAVL：距离加权接触带、虚拟运动下的接触保持和穿透惩罚；
- MotionAnyMesh：接触界面候选、PCA/RANSAC 初始化和物理一致性验证；
- 功能性关节中心估计：只有存在足够运动信息时，才能通过最小二乘进一步确定
  固定点；本阶段不引入运行时校准。

本项目已经知道骨骼分段和父子层级，因此不需要重新做网格分割、骨架提取或
机器学习预测。

## 4. 输入与坐标空间

每个候选骨骼使用同一模型空间（单位：block，`16 px = 1 block`）：

- `childMesh`：`BoneMeshPartition.frame()` 选中的受驱动主簇；
- `fullChildMesh`：仅用于安全力臂；
- `supportMesh`：最近有可见 cube 的祖先；
- `authoredPivot`：模型原始 pivot；
- `chainSegment`：多骨链段信息；
- `partType` / `structureRole`：发现阶段语义。

旋转 cube 继续使用 baked `position/dx/dy/dz` 表示的 OBB，不退回总 AABB。

## 5. 算法

### 5.1 接触带采样

对 child 每个 OBB 的六个面生成固定、确定性的采样点：

- 面中心；
- 四个面角的内缩点；
- OBB 中心只用于质量/方向，不参与接触带。

对每个 child 表面样本 `x`：

1. 在 support OBB union 上求最近点 `y`；
2. 记录距离 `d = |x-y|`；
3. 先求全体最小距离 `dMin`；
4. 接触带阈值使用自适应带宽：

```text
band = dMin + clamp(childDiagonal * 0.08, 0.5 px, 2.0 px)
```

只保留 `d <= band` 的样本，允许作者有小间隙，但不会把远端大面纳入连接区。

### 5.2 鲁棒接触中心

每个保留样本生成子/支撑最近点对：

```text
midpoint = (x + y) / 2
weight = areaWeight * exp(-(d-dMin)^2 / (2*sigma^2))
```

先按 midpoint 距离做一次中位数截尾过滤，再计算加权中心 `contactCenter`。
枢轴允许位于两表面之间，不强制投影到 child mesh。

同时计算：

- `contactMass`：有效权重占全部表面权重的比例；
- `axialConcentration`：接触带沿子件主轴所占比例，识别横穿整个支撑体的
  歧义接触；
- `gapScore`：`dMin` 相对 child 尺度的可信程度；
- `clusterDominance`：最大接触簇权重占比。

### 5.3 候选枢轴

固定生成、去重以下候选：

1. 接触带加权中心；
2. 最大接触簇中心；
3. authored pivot 在接触主平面上的投影；
4. 子/支撑全局最近点对中点；
5. authored pivot（只作为保留候选）。

真实多骨链且 authored joint 未明显脱离时，仍直接使用 authored pivot。

### 5.4 虚拟摆动评分

对每个几何候选，使用当前静止方向构造两个正交摆动轴，离线模拟：

```text
angles = {-10°, -5°, +5°, +10°}
```

目标函数：

```text
J(p) =
  λcontact * 接触根部相对初始距离的增加
+ λpenetration * 相对静止重叠新增的支撑体穿透
+ λanchor * |p-contactCenter|²
+ λprior * robustDistance(p, authoredPivot)²
```

说明：

- 接触项只使用接触带样本，不要求整块发饰始终贴住头部；
- 穿透项扣除静止姿态已有的作者重叠，避免“修复”原始造型；
- authored prior 只在 authored pivot 靠近接触带时增强；
- 候选数量和角度固定，结果确定且不进入逐帧路径。

选择最低 `J` 的候选，并用最佳/次佳分数差计算优化置信度。

### 5.5 最终置信度与回退

接触置信度由间隙分数门控接触质量、主簇占比、样本数和主轴集中度；
优化置信度再乘以最佳/次佳候选分数差与绝对评分质量：

```text
contactConfidence =
  gapScore
* weighted(contactMass, clusterDominance, sampleCount)
* axialConcentration

optimizerConfidence =
  contactConfidence
* scoreSeparation
* scoreQuality
```

决策：

- 真实多骨链可靠 authored joint：保留 authored；
- authored pivot 在自身网格上、且距推断接触带不超过 `2 px`：保留；
- `confidence >= 0.20`：仅在 authored pivot 脱离、反向或明显偏离支撑端时
  使用优化枢轴；
- 单骨候选满足尺度无关的支撑稳定性反转时，使用独立的
  `confidence >= 0.15` 门槛；
- `0.12 <= confidence < 0.20`：只纠正明显脱离整个子网格的 authored
  pivot，并收紧 `safeAngle`；
- `confidence < 0.12`：保留 authored pivot；若模型无单一安全枢轴，则
  由现有分布式几何规则保持刚性；
- 无主导连通簇：沿用现有整链刚性策略。

### 5.6 尺度无关的支撑稳定性

接触正确不代表支点在重力下稳定。若 pivot 位于质量中心下方，整个可见
主体会成为倒置摆；该点即使位于网格内部，也不应仅因“未脱离网格”而保留。
重力力矩为 `m(c-p)×g`，质量 `m` 只改变力矩大小，不改变稳定方向，因此
支点选择不应使用绝对质量、cube 数量或模型跨度门槛。

使用全可见网格质量中心和按对角线缩放的死区：

```text
scale = childDiagonalPixels
margin = clamp(scale * 0.025, 0.125 px, 0.5 px)
minImprovement = clamp(scale * 0.05, 0.25 px, 1 px)
```

仅当以下条件全部满足时启用稳定支点：

```text
single bone
contactConfidence >= 0.15
authoredPivot.y <= massCenter.y - margin
inferredPivot.y >= massCenter.y + margin
inferredPivot.y - authoredPivot.y >= minImprovement
```

该条件没有部件类型和绝对尺寸限制，因此一个 cube、对角线仅数像素的小
挂件与大型复合蝴蝶结使用同一判据。满足时使用接触优化候选。自动
Ribbon/Cape/`DANGLING_ACCESSORY` 的几何安全角进一步收紧到
`0.12 rad`，并切换到 `COMPOUND_SINGLE_BONE` 稳定档：零静态重力、
低转动惯性和小摆角；Hair/Skirt 等其它类型保留原有动力学及普通修正
安全角，只采用可靠支点。

固定饰品另有对称的“禁止降级”保护：

```text
preserveDistance = clamp(scale * 0.15, 0.5 px, 2 px)

if part is Ribbon/Cape/DANGLING_ACCESSORY
   and contactConfidence >= 0.15
   and authoredPivot.y >= massCenter.y - margin
   and inferredPivot.y <= massCenter.y - margin
   and authoredPivot.y - inferredPivot.y >= minImprovement
   and distance(authoredPivot, childMesh) <= preserveDistance:
    preserve authoredPivot
```

该路径阻止接触优化把原本贴近网格、位于质量中心附近或上方的固定点
变成倒置底支点，并写入 `supportStabilityPreserved`。它只用于固定饰品，
不用于普通 Hair；上生发束允许以位于质量中心下方的真实根部作为悬臂。
纸板狐华服小蝴蝶结 `bone101/bone103` 会保留约 `Y=41.06 px` 的 authored
pivot，不再采用约 `Y=38.1 px` 的下方接触候选。

真实多骨链不使用该规则，避免破坏作者关节。只有结构分析已经明确标记为
`DANGLING_ACCESSORY`、却没有可靠上方接触的单骨附件才自动回退为
`RIGID_ATTACHMENT_BASE`；普通上生发束、耳朵、裙摆和其它歧义悬臂在
证据不足时保留 authored pivot，显式 metadata 不受自动回退限制。

纸板狐华服 `HUDIEJIE` 的 authored pivot 为 `Y=34.18 px`，等效质量中心
约为 `Y=34.85 px`，接触优化候选约为 `Y=36.66 px`，置信度约 `0.195`。
普通 `0.20` 门槛会错误保留底支点；支撑稳定性例外会采用上方候选。

### 5.7 紧凑部件的外围杠杆

“到自身网格的距离较近”并不等于旋转杠杆合理。复合饰品的 authored
pivot 可能距最外围小 cube 仅约 `1 px`，因而未达到 detached 条件，却离
全部可见几何的质量中心数像素；围绕该点旋转仍会造成整件饰品公转和原位
偏移。

对 Hair/Ribbon/Cape/Generic 的紧凑复合单骨部件增加独立证据：

```text
single bone
visibleCubeCount >= 3
secondaryExtent / longestExtent >= 0.55
contactConfidence >= 0.20

minShift = clamp(diagonal * 0.20, 0.75 px, 2 px)
minRadiusReduction = clamp(diagonal * 0.18, 0.75 px, 2 px)
surfaceTolerance = clamp(diagonal * 0.04, 0.125 px, 0.5 px)

distance(authored, inferred) >= minShift
radius(authored, visibleMassCenter)
    - radius(inferred, visibleMassCenter) >= minRadiusReduction
radius(inferred, visibleMassCenter)
    <= 0.55 * radius(authored, visibleMassCenter)
distance(inferred, attachmentMesh) <= surfaceTolerance
```

这里的 cube 数量和轴比用于确认“复合紧凑部件”的拓扑，而不是设置绝对
模型尺寸门槛。真实多骨链以及 Ear/Wing/Tail/Skirt 不使用该规则；细长
发束的次长轴比例也会拒绝候选。因此不会把正常位于根部的悬臂 pivot
吸到质心。

命中后写入 `attachmentLeverCorrected`，并将自动部件切换为
`COMPOUND_SINGLE_BONE` 固定饰品档：零静态重力、`0.12 rad` 几何安全
角和较低转动惯性。纸板狐华服 `bone109` 的 pivot 移动约 `3.59 px`，
可见力臂从约 `6.17 px` 降到 `2.96 px`；身份旋转的 position 补偿仍严格
为零，所以加载或静止姿态不会被虚拟枢轴平移。

## 6. HEAD_SHELL

不再无条件使用 `mesh.centroid()`。

- 接触置信度达到宽面专用阈值 `0.15`：使用接触感知枢轴；
- 无可靠支撑：保留当前质心回退，并维持低角度、零静态重力；
- 不能把头壳整体强制吸附到自身表面。

## 7. 轴极性与惯性方向

`SpringDirectionIntegrator` 的平移惯性始终是 `-modelAcceleration`。部分模型
仍出现同向前倾，并不是全局符号相反，而是 axis 指向可见主体背面：模拟 tip
向后移动时，主要 cube 位于 pivot 的另一侧，旋转后的视觉位移因而向前。

对单骨和真实链末端增加加载期最终检查：

```text
visibleOffset = fullMesh.centroid - effectivePivot

if |visibleOffset| > 0.5 px
   and dot(axis, visibleOffset) < -0.25 px:
    axis = -axis
    segmentLength = fullMesh.maximumProjectionFrom(effectivePivot, axis)
```

- `0.25 px` 死区保留 HairFront 等紧凑、质量中心近 pivot 的语义端点；
- 使用全部可见 cube，而不是 attachment 主簇，保证实际一起旋转的几何不在
  tip 反侧；
- 真实多骨链非末段不翻转，继续指向下一 authored joint，维持关节连续；
- debug dump 输出 `axisPolarityCorrected`。

## 8. 模块划分

新增模块均控制在 200 行以内：

- `MeshSurfaceSamples`：固定 OBB 面采样；
- `MeshDistanceField`：OBB union signed-distance / 最近表面查询；
- `AttachmentContactPatch`：接触带结果与置信度；
- `AttachmentContactAnalyzer` / `AttachmentContactStatistics`：最近点对、
  聚类、主轴集中度与鲁棒中心；
- `VirtualPivotCandidates` / `VirtualPivotScorer` /
  `VirtualPivotOptimizer`：候选生成和虚拟摆动评分；
- `PivotInferenceResult`：pivot、confidence、score 和接触来源。
- `BoneAxisPolarity`：单骨/链末端可见质量极性安全检查。
- `SupportStabilityPivotEvidence`：尺度归一化的质量中心、倒置支点和
  上方接触检查。
- `AttachmentLeverEvidence`：紧凑复合部件的外围支点与质心力臂检查。
- `AttachmentPivotResolution`：合并 authored、接触、稳定性和杠杆证据。
- `AttachmentFrameGeometry`：attachment axis 与分类型几何安全角。

修改：

- `MeshSupportBox`：暴露面采样、包含测试和 signed distance；
- `BoneAttachmentFrame`：消费接触优化结果；
- `AttachmentPivotPolicy`：移除“低置信度必须吸附 child surface”；
- `AttachmentPivotEvidence`：异常 authored pivot 与 `2 px` 保留带；
- `HeadShellAttachmentFrame`：Head Shell 接触优先、质心回退；
- `PhysicsKinematicsDebugText`：输出 contact/optimizer 和轴极性诊断；
- `BoneKinematics.Metrics`：保存必要的加载期诊断值和
  `supportStabilityPivotCorrected` / `supportStabilityPivotPreserved` /
  `attachmentLeverPivotCorrected` / `supportStabilityUnsupported`，
  不增加逐帧临时对象；
- `AttachmentMountStabilizer`：为自动固定饰品烘焙零静态重力和小摆幅
  档位。

## 9. 正确性不变量

必须继续成立：

1. `effectivePivot == authoredPivot` 时不产生 position 补偿；
2. 修正 pivot 后，任意动画 rotation/非均匀 scale 下虚拟枢轴补偿数学成立；
3. `dt=0` 时所有内置模型姿态完全不变；
4. 多骨链 parent runtime tip 与 child runtime pivot 连续；
5. full mesh 始终决定安全力臂，contact 主簇只决定 attachment frame；
6. 显式碰撞代理和 Body/Cape 自动代理行为不变；
7. 逐帧解算保持零分配。
8. 单骨和链末端的轴不能明确背离全部可见 cube 的质量中心。
9. 被标记为支撑稳定性修正的单骨附件，其 effective pivot 必须从质量中心下方
   移到可靠接触带的上方，且真实多骨链不得命中该规则。
10. 被标记为支撑稳定性保护的固定饰品必须保留 authored pivot，不能采用
    位于质量中心下方的接触候选。
11. 被标记为外围杠杆修正的部件必须显著缩短可见质量中心力臂，且身份
    rotation 下的 position 补偿必须为零。

不再成立、必须删除的旧不变量：

```text
corrected effectivePivot 必须位于 child cube 表面
```

## 10. 验证计划

新增/保留的合成与内置模型回归：

- 1–2 px 间隙；
- 远程 authored pivot；
- 多接触簇与主簇；
- 横穿支撑体的低置信度接触；
- 多骨链 authored joint；
- HEAD_SHELL；
- 匿名复合单骨附件；
- 微型单 cube 附件；
- 无可靠上方接触时仅对明确 `DANGLING_ACCESSORY` 的自动刚性回退；
- 普通上生发束和歧义悬臂不被误冻结；
- 纸板狐华服后脑 `HUDIEJIE`；
- 纸板狐华服小蝴蝶结 `bone101/bone103` 的 authored pivot 反向保护；
- 纸板狐华服 `bone109` 的外围支点、缩短后力臂和身份补偿；
- `RightSideHair` 等细长发束不得命中紧凑部件规则；
- 全部 27 个模型的单骨/链末端可见质量轴极性；
- 修正 pivot 后 `dt=0`。

替换 `BundledPivotSurfaceVerification`：

- 不再扫描“到 child surface 距离为零”；
- 改为检查 contact distance、虚拟摆动新增穿透、接触保持和置信度回退。

保留并通过：

- `InitialPoseStabilityVerification`；
- `BundledChainJointVerification`；
- `DistributedBoneRigidityVerification`；
- `BoneKinematicsVerification` 的补偿数学；
- 完整 `verifyBonePhysics`；
- `benchmarkBonePhysics` 的 `0 B/frame`。

## 11. 性能预算

所有新计算均发生在模型发现阶段：

```text
O(childSurfaceSamples * supportBoxes * candidates * virtualAngles)
```

Gecko 骨通常只有少量 cube，固定采样和候选上限可保证单骨仅数千次标量运算。
结果存入现有 plan/cache；每帧 solver 不执行 SDF、采样、聚类或候选评分。

## 12. 实施状态

- [x] 实现 OBB 面采样、contains/signed distance；
- [x] 实现接触带、鲁棒中心和主轴歧义检查；
- [x] 实现固定候选与虚拟摆动评分；
- [x] 接入 `BoneAttachmentFrame` / `AttachmentPivotPolicy`；
- [x] HEAD_SHELL 切换为接触优先、质心回退；
- [x] 删除 `BundledPivotSurfaceVerification`，补齐接触与回退回归；
- [x] debug dump 输出 `contactConfidence` / `pivotScore` /
  `supportStabilityCorrected` / `supportStabilityPreserved` /
  `attachmentLeverCorrected` / `axisPolarityCorrected`；
- [x] 实现尺度无关的单骨支撑稳定性判定和自动稳定档；
- [x] 实现紧凑复合部件的外围杠杆修正和固定饰品档；
- [x] 增加微型单 cube、匿名附件、歧义悬臂及纸板狐华服
  `HUDIEJIE`、`bone101/bone103`、`bone109` 回归；
- [x] 全部 27 个模型完成单骨/链末端轴极性扫描；
- [x] 完整 `verifyBonePhysics` 通过；
- [x] `benchmarkBonePhysics` 保持 constrained solver `0 B/frame`；
- [x] 同步 README 与约束文档。

本次 Windows/JDK 17、`winefox` 20,000 帧结果：

```text
iterative-active-constrained: 89169.2 ns/frame
allocation=0.00 B/frame
```

## 13. 参考

- Kinematify, DW-CAVL: <https://arxiv.org/html/2511.01294v4>
- MotionAnyMesh: <https://doi.org/10.48550/arxiv.2603.12936>
- RigNet 局限（服装/饰品小部件）:
  <https://ar5iv.labs.arxiv.org/html/2005.00559>
- CGAL Mean Curvature Flow Skeletonization 输入限制:
  <https://doc.cgal.org/latest/Surface_mesh_skeletonization/index.html>
