# Version target 模板

本目录位于 `docs/`，不会被 `settings.gradle` 自动 include。复制示例前替换以下占位符：

- `@TARGET_KEY@`
- `@MC_VERSION@`
- `@MC_VERSION_RANGE@`
- `@FORGE_VERSION@`
- `@FORGE_VERSION_RANGE@`
- `@FORGE_LOADER_RANGE@`
- `@JAVA_VERSION@`
- `@PARCHMENT_VERSION@`
- `@TLM_MINIMUM@`
- `@GECKO_GENERATION@`

目标目录结构：

```text
adapters/
├─ forge-@MC_VERSION@/
│  ├─ build.gradle
│  └─ src/main/java/                 # MC/Forge Port 实现
└─ tlm-@MC_VERSION@-gecko@GECKO_GENERATION@/
   ├─ build.gradle
   └─ src/main/java/                 # TLM/Gecko Port 实现

distribution/
└─ forge-@MC_VERSION@/
   ├─ build.gradle
   └─ src/main/
      ├─ java/.../MaidIntelligence.java
      └─ resources/
         ├─ META-INF/mods.toml
         ├─ tlm_companionship.common.mixins.json
         ├─ tlm_companionship.client.mixins.json
         └─ tlm_companionship.tlm-gecko@GECKO_GENERATION@.mixins.json
```

使用顺序：

1. 将两个 `*.build.gradle.example` 分别复制为新 adapter 的 `build.gradle`。
2. 实现现有 feature Port；不要复制 `kernel`、`shared` 或 `features` Java 源码。
3. 以当前 distribution 构建为基线创建新组合根，只替换版本依赖、资源和 Mixin 差异。
4. 复制 `matrix-entry.json.example` 的对象到矩阵 `targets` 数组并替换占位符。
5. 运行 `verifyVersionMatrix`；通过前不要提交或发布该 target。

模板不包含假的游戏 API 实现，也不代表 NeoForge/Fabric 已受支持。
