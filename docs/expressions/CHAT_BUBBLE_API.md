# 聊天气泡 API

## 1. 原版气泡类型

| 类型 ID | 数据类 | 原版是否主动使用 | 用途 |
| --- | --- | --- | --- |
| `touhou_little_maid:text` | `TextChatBubbleData` | 是 | 文本和颜文字 |
| `touhou_little_maid:emoji` | `EmojiChatBubbleData` | 是 | 随机图片表情 |
| `touhou_little_maid:waiting` | `WaitingChatBubbleData` | 是 | AI 思考等待 |
| `touhou_little_maid:image` | `ImageChatBubbleData` | 没有常规调用 | 指定任意图片 |
| `touhou_little_maid:progress` | `ProgressChatBubbleData` | 没有常规调用 | 文字和进度条 |

`image` 和 `progress` 已由本体注册，可以直接使用。“没有常规调用”不代表不可用。

## 2. 取得管理器

```java
ChatBubbleManager manager = maid.getChatBubbleManager();
```

添加气泡会返回一个 ID。添加失败时返回 `-1`：

```java
long bubbleId = manager.addChatBubble(data);
```

可使用 ID 删除气泡：

```java
manager.removeChatBubble(bubbleId);
```

## 3. 文本气泡

显示翻译键：

```java
manager.addTextChatBubble("tlm_companionship.bubble.hello");
```

显示任意组件：

```java
manager.addChatBubble(
        TextChatBubbleData.type2(
                Component.literal("工作完成")
        )
);
```

仅在上一个气泡过期后添加：

```java
long bubbleId = manager.addTextChatBubbleIfTimeout(
        "tlm_companionship.bubble.warning",
        previousBubbleId
);
```

## 4. 随机图片气泡

```java
manager.addChatBubble(
        EmojiChatBubbleData.create()
);
```

图片由客户端从资源池随机选择。详细规则见 [图片表情池](IMAGE_EMOJI_POOL.md)。

## 5. 指定图片气泡

```java
ResourceLocation image = new ResourceLocation(
        "tlm_companionship",
        "textures/chat_bubble/status/low_health.png"
);
manager.addChatBubble(
        ImageChatBubbleData.singleImage(image, 24, 24)
);
```

## 6. 思考等待气泡

使用管理器提供的快捷方法：

```java
long waitingId = manager.addThinkingText(
        "tlm_companionship.bubble.thinking"
);
```

更新等待文字：

```java
waitingId = manager.refreshThinkingText(
        "tlm_companionship.bubble.thinking",
        waitingId,
        Component.literal("正在规划路径")
);
```

AI 回复完成后可替换为聊天内容：

```java
manager.addLLMChatText("我已经想好啦。", waitingId);
```

## 7. 进度气泡

```java
manager.addChatBubble(
        ProgressChatBubbleData.create(
                Component.literal("工作进度"),
                0xFF333333,
                0xFF55AA55,
                0.65,
                true
        )
);
```

`progress` 推荐限制在 `0.0` 到 `1.0`。颜色使用 ARGB 整数。

## 8. 自定义气泡类型

附属入口 `ILittleMaid` 提供注册方法：

```java
@Override
public void registerChatBubble(ChatBubbleRegister register) {
    register.register(
            new ResourceLocation("tlm_companionship", "custom"),
            new CustomBubbleSerializer()
    );
}
```

只有现有五种类型无法表达需求时才应注册新类型。普通图文、等待和进度反馈优先复用原版类型。

## 9. 模型面部表情限制

本体不存在统一的：

```text
setExpression("happy")
ExpressionPool
FaceState
```

传统 Bedrock 模型可能使用以下骨骼或脚本：

| 名称 | 触发 |
| --- | --- |
| `blink`、`blink2` | 周期性眨眼 |
| `_blink`、`_bink` | 反向眨眼兼容 |
| `hurtBlink` | `hurtTime > 0` |
| `head/beg.js` | `maid.isBegging()` |

Gecko 和第三方模型包也可以定义私有面部骨骼，但不存在跨模型统一的开心、伤心或生气动作。

需要稳定表达情绪时，应优先使用气泡。

## 10. 建议的附属封装

```text
MaidExpressionService
├─ showRandomImage(maid)
├─ showRoutineKaomoji(maid)
├─ showHurt(maid)
├─ showWarning(maid, warningType)
├─ showProgress(maid, text, progress)
└─ clearOwnedBubble(maid)
```

封装后，业务功能无需直接依赖本体的全部气泡实现类。

## 11. 相关源码

```text
_reference/TouhouLittleMaid/src/main/java/com/github/tartaricacid/touhoulittlemaid/
entity/chatbubble/ChatBubbleManager.java
entity/chatbubble/IChatBubbleData.java
entity/chatbubble/ChatBubbleRegister.java
entity/chatbubble/implement/
api/ILittleMaid.java
```
