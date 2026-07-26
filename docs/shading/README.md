# 女仆模型逐面外法线修正

为 Gecko 女仆模型的每个 cube 独立重新计算六个面的外法线与外向绕序。算法只读取实际
顶点，不依赖模型、骨骼、光影包名称或 cube 之间的穿插关系。

## 成因

TLM 的 Sodium 路径会用叉积生成平面法线，并对 mirror cube 取反法线，但部分几何的顶点
绕序没有同步反转。使用 `gl_FrontFacing` 的光影会把这些外表面判为背面并再次翻转法线，
最终只有对应 cube 出现错误明暗。

此外，TLM 1.5.3 的 Sodium 路径把 DOWN 法线 X 分量误写成 `-ny.z`，本模块恢复为
`-ny.x`。

## 算法

每个网格首次出现时执行：

1. 根据 cube 的 `position / dx / dy / dz` 建立八个实际角点；
2. 对每个面取前三个顶点，以叉积得到严格垂直于平面的单位法线；
3. 用“面中心减 cube 中心”确定外侧，叉积指向内侧时翻转法线；
4. 同时缓存该面的绕序修正位，渲染时按 `0,3,2,1` 提交反向面；
5. 退化面无法叉积时才使用有限的轴向回退。

法线数组和每 cube 一个绕序位掩码通过 `WeakHashMap` 按 `GeoMesh` 身份缓存。热路径只做
数组读取和法线矩阵变换，逐帧零堆分配。

## Sodium/Embeddium

普通骨骼继续使用批量快速路径。只有缓存中确实存在内向绕序的骨骼才回退到逐面写入器，
独立 mirror cube 同样可以触发，不要求复合组件或几何穿插。

## 注入点

- `GeoRendererOutsideNormalMixin`：为 Gecko 默认路径接入逐面外法线写入器；
- `SodiumOutsideNormalFallbackMixin`：只让需要绕序修正的骨骼退出批量路径；
- `SodiumDownNormalPackingMixin`：修复原批量路径的 DOWN 法线 X 分量。

## 生命周期与范围

- F3+T 和 TLM 自定义目录/ZIP 模型热加载会清理弱缓存；
- Bedrock 与 YSM 不经过 Gecko `GeoMesh`，不在本模块范围内；
- `ShadowPassDetector` 由交互层用于跳过光影阴影阶段，避免非相机矩阵参与面追踪；它不修改
  本模块的法线。

## 验证

```bash
./gradlew.bat --offline verifyModelShading
```

验证覆盖 TLM 面拓扑、倾斜平面的叉积法线、mirror 外向绕序、弱缓存生命周期，以及全部
内置 Gecko 模型的有限、单位、垂直且朝外的面法线。
