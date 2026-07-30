# Distributions

每个矩阵 target 对应一个 `forge-<mc>` distribution，并且只能在这里应用一次
ForgeGradle。distribution 是发布与运行组合根，负责：

- 装配一份稳定 `kernel/shared/features` 与矩阵指定的两个 adapter；
- 持有唯一 `@Mod` 入口、`mods.toml`、三份 Mixin descriptor 和版本资源；
- 提供 run、reobf、功能验证、数据生成及 IDE 元数据任务。

生产 Java 源不得包含组合根以外的业务实现。版本差异放入 adapter 或本 distribution 的
资源/构建配置，不复制 feature 源码。

根 `check`、`assemble`、`verifyAll` 聚合全部 distribution；运行和专项任务使用
`-PversionTarget=<key>` 路由。当前发布产物路径保持
`distribution/forge-1.20.1/build/libs`。完整边界见
[`docs/architecture`](../docs/architecture/README.md)。
