# 特性优先架构迁移交接

## 当前状态
- `baseline-guards` 已完成：迁移前六组验证全部通过，碰撞验证已由 Physics 聚合入口覆盖；全模型穿模审计、性能基准和 Jar 清单已采集。
- `gradle-modules` 进行中：根工程已改为聚合构建，已建立 `kernel`、`shared/geometry`、七个 feature、两个 adapter 和 `distribution/forge-1.20.1` 骨架。
- 其余计划项尚未开始收口，不能将当前提交视为可发布版本。

## 迁移前基线
- 六组验证：Level、Status、Advancement、Face、Physics、Shading 全部通过。
- 可变帧物理：最差 `bowR5`，`0.001 px/f`。
- 全模型审计：27 个模型；最差 axis `-1.10 px`、geom `4.01 px`、buzz `3.4 px/f`。
- `winefox` 约束解算：`254633.5 ns/frame`，热路径 `0 B/frame`。
- 碰撞代理：80 个受驱动段共 3226 个代理。

## 已落地的工程结构
- [`build.gradle`](../../build.gradle) 是根生命周期聚合器。
- [`settings.gradle`](../../settings.gradle) 注册全部新子工程。
- [`build-logic`](../../build-logic) 提供普通 Java 模块约定和版本 API import 禁令。
- [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) 集中版本矩阵。
- [`distribution/forge-1.20.1`](../../distribution/forge-1.20.1) 是当前版本唯一 ForgeGradle、Mixin、runClient 和 reobf 边界。
- 旧生产源码目前整体暂存于 [`adapters/tlm-1.20.1-gecko3`](../../adapters/tlm-1.20.1-gecko3)，资源和测试已迁入 distribution；这是中间态，尚未完成 Forge/TLM 与纯核心分类。
- `kernel` 已开始建立 `FeatureId`、`MaidId`、领域事件总线和服务注册契约。

## ForgeGradle 约束
同一版本只允许一个 ForgeGradle 工程。adapter 保持独立源码边界，由对应 distribution 统一编译和 reobf，避免多个 FG6 工程的 mappings、run 与 reobf 状态互相污染。纯 feature 仍必须使用普通 `java-library` 并独立执行架构检查。

## 下一步
1. 完成 `MutableServiceRegistry` 和通用结果类型，随后先运行 Gradle 配置检查。
2. 将 `@Mod` 组合根迁入 distribution，删除 `FeatureCatalog` 静态清单，以构造器显式装配七个 feature。
3. 按 import 与职责把事件、命令、网络、GUI 移入 Forge adapter，把 EntityMaid、TaskData、Gecko、Bedrock、YSM 与 Mixin 移入 TLM adapter。
4. 把六个常规特性的纯 domain/api/port/application 迁入各自 feature，并抽取 shared geometry。
5. 最后迁移 Physics；先引入 `BoneModelPort`/`PoseWriterPort`，再重组 discovery/layout/spring/collision/session，期间持续跑完整 Physics 聚合验证。
6. 全部完成后重新运行 check、全模型审计、benchmark、reobfJar 与客户端冒烟，对照本页基线。

## 注意事项
- 当前模块化中间态尚未执行迁移后的构建验证，接手后应先修复 Gradle 配置与源码归属，不要回滚已经通过迁移前验证的物理修复。
- 仓库根目录的 `budget*.txt`、`*-audit.txt`、`*-vb.txt`、`*-trace.txt` 等是本地诊断输出，本次提交刻意不纳入版本控制。
