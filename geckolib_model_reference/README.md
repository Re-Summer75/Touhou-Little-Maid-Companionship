# 内置 Gecko 模型参考集

这里保存车万女仆 1.5.3 内置 Gecko 模型的可追踪几何夹具，供骨骼物理自动发现和回归验证使用。

- `models/entity/`：本体内置的全部 27 个 Gecko 模型，包括 `winefox_saint.json`。
- `maid_model.json`：模型 ID、缩放和动画配置清单。
- `animation/`、`textures/entity/`：原 `winefox_blockbench` 夹具附带的酒狐动画和贴图。

模型几何来自 `_reference/TouhouLittleMaid` 的内置
`touhou_little_maid-1.0.0/assets/geckolib` 模型包。测试直接解析这里的副本，
避免依赖被 Git 忽略的参考仓库。
