# Version adapters

本目录只存放版本/平台绑定实现。实际 include 集合来自
[`gradle/version-matrix.json`](../gradle/version-matrix.json)。

- `forge-<mc>`：Forge/MC 生命周期、网络、命令、菜单、资源和类型转换。
- `tlm-<mc>-gecko<代际>`：TLM 实体/任务、模型渲染桥及 TLM/Gecko Mixin。

adapter 实现稳定 feature 的 API/Port，但不复制业务算法。Forge adapter 不得 import
TLM/Gecko 实现；TLM adapter 不得注册 Forge 网络、菜单或 installer；两者都不得依赖
distribution 组合根。

新增版本流程和可复制模板见
[`docs/architecture`](../docs/architecture/README.md)。模板位于 `docs/`，不会被 Gradle
自动 include。
