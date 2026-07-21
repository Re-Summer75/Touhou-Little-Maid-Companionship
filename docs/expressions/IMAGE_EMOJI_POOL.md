# 图片表情池

## 1. 原版资源

车万女仆 1.5.3 默认提供 20 张图片表情：

```text
emoji_01.png
emoji_02.png
emoji_03.png
emoji_04.png
emoji_05.png
emoji_06.png
emoji_07.png
emoji_08.png
emoji_09.png
emoji_10.png
emoji_11.png
emoji_12.png
emoji_13.png
emoji_14.png
emoji_15.png
emoji_16.png
emoji_17.png
emoji_18.png
emoji_19.png
emoji_20-24x24.png
```

资源目录：

```text
_reference/TouhouLittleMaid/src/main/resources/assets/touhou_little_maid/
textures/chat_bubble/maid_emoji/
```

这些文件只有编号，没有“开心”“生气”等语义标签。不要把某个编号的当前画面当作长期兼容协议。

## 2. 资源扫描规则

客户端资源重载时，`EmojiReloadListener` 会扫描所有资源命名空间中的：

```text
textures/chat_bubble/maid_emoji/
```

规则：

- 支持 `.png` 和 `.gif`。
- 文件名没有尺寸后缀时，默认按 `24×24` 显示。
- 文件名以 `-宽x高` 结尾时使用指定尺寸，例如 `wave-32x24.gif`。
- 宽高会被限制在 8 到 256 之间。

附属可直接添加资源：

```text
src/main/resources/assets/maid_intelligence/
textures/chat_bubble/maid_emoji/happy.png
```

该图片会自动加入全局随机池，不需要 Java 注册。

## 3. 随机显示

```java
maid.getChatBubbleManager().addChatBubble(
        EmojiChatBubbleData.create()
);
```

服务端只同步“显示随机图片表情”。具体图片由客户端渲染器随机选择，因此不同玩家可能看到不同图片。

适合：

- 普通闲聊
- 无明确语义的气氛反馈
- 不要求所有客户端显示同一图片的场景

## 4. 指定图片

```java
ResourceLocation image = new ResourceLocation(
        "maid_intelligence",
        "textures/chat_bubble/status/tool_warning.png"
);
maid.getChatBubbleManager().addChatBubble(
        ImageChatBubbleData.singleImage(image, 24, 24)
);
```

`singleImage` 适合纹理实际尺寸与传入宽高一致的单图。需要使用图集时，可以改用 `ImageChatBubbleData.create(...)` 的完整参数版本指定 UV、纹理尺寸和持续时间。

适合：

- 工具耐久告急
- 生命值告急
- 工作完成
- 明确的开心、生气、疑惑等附属状态

## 5. 使用建议

| 需求 | 推荐方式 |
| --- | --- |
| 随机气氛表情 | `EmojiChatBubbleData.create()` |
| 明确状态图片 | `ImageChatBubbleData` 指定附属纹理 |
| 要求所有玩家看到同一内容 | 指定图片，不使用客户端随机池 |
| 动图 | 将 GIF 放入随机池目录 |
| 语义稳定性 | 使用附属命名空间和语义化文件名 |

## 6. 相关源码

```text
_reference/TouhouLittleMaid/src/main/java/com/github/tartaricacid/touhoulittlemaid/
client/resource/listener/EmojiReloadListener.java
client/renderer/entity/chatbubble/implement/EmojiChatBubbleRenderer.java
entity/chatbubble/implement/EmojiChatBubbleData.java
entity/chatbubble/implement/ImageChatBubbleData.java
```
