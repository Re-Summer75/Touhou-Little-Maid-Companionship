# 版本适配器边界

版本组合以 [`gradle/version-matrix.json`](../../gradle/version-matrix.json) 为唯一结构化来源；
完整依赖方向、target 路由和新增版本模板见
[`docs/architecture`](../architecture/README.md)。

`adapters/forge-1.20.1` 负责 Forge 47 与 Minecraft 1.20.1 的事件安装、
命令、网络、菜单/客户端生命周期、资源重载、MC 类型转换和纯 MC/Forge
Mixin。共享 `SimpleChannel` 仅协调各 feature 的 packet registrar；协议
版本保持 `13`，消息 ID `0..5` 显式固定。
菜单注册、资源重载和客户端事件安装壳也由 Forge adapter 持有；依赖
TLM 基类的具体容器、屏幕和渲染状态通过窄工厂/回调从 TLM adapter
注入，避免 Forge adapter 反向依赖 TLM。

`adapters/tlm-1.20.1-gecko3` 只负责 EntityMaid、TaskData/Brain、TLM
事件、Gecko/Bedrock/YSM 模型与渲染桥、TLM Extension 和 TLM/Gecko
Mixin。TLM 模块不得实现 Forge installer。

Mixin 分为三份配置：

- `tlm_companionship.common.mixins.json`：纯 MC 通用目标。
- `tlm_companionship.client.mixins.json`：纯 MC 客户端目标。
- `tlm_companionship.tlm-gecko3.mixins.json`：TLM/Gecko 目标，全部
  `remap = false`。

MixinGradle 对单个 source set 只生成一份可靠 refmap，因此 common 与
client 配置共享 `tlm_companionship.refmap.json`；TLM/Gecko 配置不声明
refmap，避免生成无效映射。新增 MC/TLM 版本时应新增对应的两个 adapter
与 distribution，不复制 `features/*`。
