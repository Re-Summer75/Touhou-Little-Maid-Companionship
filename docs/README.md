# 车万女仆：朝夕相伴开发文档

正式英文名：**Touhou Little Maid: Companionship**。

本目录集中存放“车万女仆：朝夕相伴”的设计、分析与兼容性文档。中文可简称为“TLM：朝夕相伴”，英文可简称为“TLM: Companionship”。

当前 MOD ID 为 `tlm_companionship`。旧 ID `maid_intelligence` 仅保留为存档数据迁移入口，不再用于模组注册、资源或网络命名空间。

## 文档索引

- [车万女仆界面分析手册](MAID_GUI_ANALYSIS.md)
- [车万女仆表情系统](expressions/README.md)
  - 图片表情池
  - 颜文字池
  - 聊天气泡 API
- [车万女仆动作系统](actions/README.md)
  - Gecko 主状态
  - Gecko 条件动作
  - Bedrock JS 动画
  - 附属接入指南
- [女仆状态反馈系统](status-feedback/README.md)
  - 独立饥饿与自动进食
  - 低耐久工具自动替换
  - 状态气泡与请求动作
- [女仆交互按键](interactions/README.md)
  - 打开界面与坐下按键
  - 食物、药品和物品交互
  - 准星提示与河童罗盘例外
- [女仆模型逐面外法线修正](shading/README.md)
  - 按实际面顶点重新计算外法线与外向绕序
  - 弱键缓存与 Sodium/Embeddium 精确回退
- [环境风与女仆骨骼物理](physics/README.md)
  - VRM 弹簧骨二级动作（尾巴、头发、耳朵）
  - 刚度拉回动画、逐骨骼解算、力臂归一化
  - 骨骼链判定、调试棒与调参
- [女仆原版进度系统](advancements/README.md)
  - 镜像玩家与每只女仆独立的 `PlayerAdvancements`
  - 女仆行为到原版触发器的桥接表与四个自定义触发器
  - Tab 页内的进度树界面与附属扩展方式
- [女仆独立成就系统（已并入原版进度）](achievements/README.md)
  - 旧成就到新进度的一对一映射与静默迁移
  - 附属模组的改造对照
- [MC百科收录资料](mcmod/README.md)
  - 简介正文与表单字段
  - 素材清单与提交注意事项

## 维护约定

- 新增开发文档统一放在本目录。
- 同一主题的文档放入独立子目录，并通过该目录的 `README.md` 建立索引。
- 表情、动作、GUI 等不同系统不得继续堆放在同一文件中。
- 文件名使用大写英文和下划线，便于与源码、资源目录区分。
- 文档应注明所依据的 Minecraft、Forge 和车万女仆版本。
- 升级车万女仆依赖后，应重新核对源码路径、动画名称和触发条件。
