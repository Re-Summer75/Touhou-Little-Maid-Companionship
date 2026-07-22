<div align="center">

# 🌸 车万女仆：朝夕相伴 <br> Touhou Little Maid: Companionship

**为《车万女仆》带来更沉浸、更智能的互动体验**

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=flat-square&logo=minecraft)
![Mod Loader](https://img.shields.io/badge/Mod_Loader-Forge-orange?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![PRs Welcome](https://img.shields.io/badge/PRs-Welcome-brightgreen?style=flat-square)

</div>

---

> **“朝夕相伴，不止于工作。”**  
> 这是一个为《车万女仆》(Touhou Little Maid) 深度定制的扩展模组。我们致力于为女仆添加成长系统、饱食状态、智能工作模式以及更多丰富细腻的互动体验，让女仆真正成为你在 Minecraft 世界中不可或缺的伙伴。

## ✨ 核心特性

### 🌟 成长与等级系统
- 女仆在完成日常工作时将积累经验并提升等级。
- 全新的等级 UI 提示，直观展示女仆的成长进度。

### 🍔 饱食度与状态反馈
- **独立的饱食度机制**：女仆拥有自己的饱食度与饱和度（不再与玩家绑定或缺失），支持自动进食。
- **动态回血**：引入基于饱和度的快速回血机制，与基础饱食回血并行独立计算。
- **拒绝进食**：满饱和度时禁止喂食，女仆会通过摇头、拒绝音效及聊天气泡向你反馈“吃不下了”。
- **精细互动**：支持直接喂食整个蛋糕，并按比例恢复营养与饱和度。

### 🤝 沉浸式互动体验
- **面部追踪粒子**：喂食时，进食碎屑粒子会精准追踪女仆的面部模型坐标并动态生成。
- **状态气泡**：当女仆饥饿、工具耐久度过低或自动替换工具时，会弹出专属的聊天气泡提示。

### 🧠 智能 AI 行为
- **工具保护**：工作时如果工具耐久度过低，女仆会自动寻找背包中的备用工具进行替换，防止珍贵工具损坏。

---

## 📦 依赖与安装

### 环境要求
- **Minecraft**: `1.20.1`
- **Mod Loader**: `Forge` (推荐版本 47.4.0 及以上)

### 前置模组
- **[Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid)**: `>= 1.5.3` (必须)

### 安装方法
1. 确保已安装上述对应版本的 Forge 和车万女仆本体。
2. 将下载的 `tlm-companionship-x.x.x.jar` 放入游戏根目录的 `mods` 文件夹中。
3. 启动游戏即可。

---

## 📖 开发者与机制文档

如果您是模组开发者、整合包作者，或者想深入了解本模组的内部机制，请查阅我们的详细文档：

- 📚 **[开发与机制文档主页](./docs/README.md)**
- 🎨 **[GUI 界面分析与扩展指南](./docs/MAID_GUI_ANALYSIS.md)**
- 🍖 **[状态反馈与饱食度系统详解](./docs/status-feedback/README.md)**
- 💬 **[气泡与表情系统 API](./docs/expressions/CHAT_BUBBLE_API.md)**
- 🤝 **[物品交互机制](./docs/interactions/README.md)**

---

## 🛠️ 参与贡献

我们非常欢迎社区的参与！无论是提交 Bug 报告、功能建议，还是直接提交 Pull Request，您的每一份贡献都将让这个模组变得更好。

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交您的更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

---

## 📄 开源协议

本项目采用 **MIT License** 开源协议。详细信息请参阅 [LICENSE](LICENSE) 文件。

<div align="center">
  <i>Made with ❤️ by 铼夏 (LAY) & 社区贡献者</i>
</div>