# 车万女仆动作系统

本目录整理车万女仆 1.5.3 Forge 的模型动作、触发条件和附属接入方式。

## 文档索引

- [Gecko 主状态动作](GECKO_MAIN_STATES.md)
  - 20 个主状态动作
  - 优先级、娱乐和载具状态
- [Gecko 条件动作与资源池](GECKO_CONDITIONAL_ACTIONS.md)
  - 97 个默认动画资源
  - 持物、使用、挥动和条件命名规则
- [Bedrock JS 动画](BEDROCK_JS_ANIMATIONS.md)
  - 默认骨骼脚本
  - 任务显示脚本
  - 与 Gecko 动画的区别
- [动作接入指南](ACTION_INTEGRATION.md)
  - 可由附属触发的动作
  - 调用示例和兼容风险
  - 摸头等新动作的设计建议

## 分析基准

- Minecraft：1.20.1
- Forge：47.4.0
- 车万女仆：1.5.3 Forge
- 本体源码：`_reference/TouhouLittleMaid`

## 两套动画系统

| 系统 | 适用模型 | 动作来源 |
| --- | --- | --- |
| Gecko 动画 | `is_gecko: true` 的模型 | `.animation.json` 中的统一名称 |
| Bedrock JS 动画 | 传统 SimpleBedrock 模型 | 模型配置引用的 `.js` 骨骼脚本 |

同一动作在两套系统中的实现不同：

```text
坐下
├─ Gecko：主控制器选择 sit
└─ Bedrock：arm、leg、sit 等脚本共同修正骨骼
```

## 重要结论

1. 动作池不是一个可以随意调用 `play("动作名")` 的公共播放列表。
2. 最兼容的方式是改变女仆真实状态，让动画状态机自行选择动作。
3. 不要为了播放动画伪造死亡、伤害或睡眠，这会影响游戏逻辑。
4. Gecko 模型和 Bedrock 模型必须提供统一的降级表现。
5. 本体没有统一的摸头、拥抱、害羞或庆祝动作。

## 动作与声音

动作和声音基本独立：

- 女仆语音由声音包系统播放。
- AI 行为可以在改变状态时单独调用 `playSound`。
- 不应假设播放动作会自动播放声音。

新增互动应由行为逻辑同时管理动作、声音、气泡和结束后的状态恢复。
