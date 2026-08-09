# 车万女仆：朝夕相伴文档

正式英文名：**Touhou Little Maid: Companionship**，MOD ID 为 `tlm_companionship`。

## 玩家行为

- [女仆行为总索引](maid-behaviors/README.md)
  - [陪伴 AI、移动与座位](maid-behaviors/companion/README.md)
    - [战斗](maid-behaviors/companion/COMBAT.md)
  - [照料、状态与反馈](maid-behaviors/care/README.md)
  - [主人交互与物品](maid-behaviors/interactions/README.md)
  - [成长、进度与界面](maid-behaviors/progression/README.md)
  - [视觉表现、环境与物理](maid-behaviors/visuals/README.md)

行为手册是触发条件、默认数值、可见效果和限制的唯一权威来源。

## 开发与机制

- [多版本架构与版本矩阵](architecture/README.md)
  - [数据驱动意图 AI 格式与扩展](architecture/intent-ai-data.md)
  - [新增版本模板](architecture/templates/version-target/README.md)
- [车万女仆界面分析手册](gui/README.md)
- [女仆原版进度系统](advancements/README.md)
- [环境风与女仆骨骼物理](physics/README.md)
  - [二级动作约束](physics/SECONDARY_MOTION_CONSTRAINTS.md)
  - [接触感知枢轴推断](physics/CONTACT_AWARE_PIVOT_INFERENCE.md)
- [女仆模型逐面外法线修正](shading/README.md)

## TLM 本体参考

- [表情与聊天气泡](expressions/README.md)
- [动作与动画状态](actions/README.md)

这些文档用于适配 TLM，不代表本模组新增了其中全部行为。

## 发布资料

- [MC 百科收录资料](mcmod/README.md)

## 维护约定

- 每个主题必须放入独立目录，并由该目录的 `README.md` 作为入口。
- 新行为文档放入 `maid-behaviors/<类别>/`；不得继续在 `maid-behaviors/` 根层堆放专题文件。
- 玩家规则只写在行为手册；机制文档通过链接引用，不复制按键表、数值表和行为说明。
- 已淘汰的设计与历史调研不保留为现行文档，历史记录由 Git 提供。
- 单文件尽量不超过 500 行；大型专题按职责继续拆分，而不是增加同层文件。
- 除目录入口 `README.md` 外，专题文件使用大写英文和下划线命名。
- 文档应注明 Minecraft、Forge 和 TLM 版本基线；升级依赖后重新核对路径、动画名和触发条件。
- 源码与构建边界见[规模与目录组织约束](architecture/README.md#源码规模与目录组织约束)。
