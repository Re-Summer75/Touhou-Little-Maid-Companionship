# 颜文字池

原版数据位于：

```text
_reference/TouhouLittleMaid/src/main/resources/data/touhou_little_maid/
chat_bubble/kaomoji.json
```

## 1. 池和合并规则

| 池名 | 原始数量 | 自动选择时的数量 | 用途 |
| --- | ---: | ---: | --- |
| `core` | 60 | 不单独存在 | 加入 `work` 和 `idle` |
| `work` | 4 | 64 | 工作日程 |
| `idle` | 4 | 64 | 空闲日程或空闲任务 |
| `sleep` | 16 | 16 | 休息日程 |
| `hurt` | 8 | 8 | 女仆受伤 |

加载时，`core` 会分别追加到 `work` 和 `idle`，随后从内部映射中移除。因此不存在可以单独调用的运行时 `core` 池。

## 2. `core` 通用池

共 60 条：

```text
(๑>◡<๑)  (｡•̀ᴗ-)✧  (⁄ ⁄•⁄ω⁄•⁄ ⁄)⁄  (๑˃̵ᴗ˂̵)ﻭ✧  (≧∇≦)/
(๑´0`๑)  ( • ω • )✧  (ง •_•)ง  (づ￣ ³￣)づ  (｡･∀･)ﾉﾞ
(⁎⁍̴̛ᴗ⁍̴̛⁎)  (๑˘︶˘๑)  (●ↀωↀ●)✧  (￣▽￣)ノ  (｡•ㅅ•｡)♡
(≧ω≦)  (๑•ㅂ•)و✧  (ฅ´ω`ฅ)  (°▽°)/  (๑´ڡ`๑)☆
(๑•̀ㅁ•́๑)✧  (๑•́ ₃ •̀๑)  (๑ơ ₃ ơ)♥  (*/ω＼*)  ( •̀ ω •́ )✧
(๑╹ᆺ╹)  (￣ε￣＠)  (੭ ᐕ)੭*⁾⁾  (๑´ㅂ`๑)  (๑¯∀¯๑)
(｡◕‿◕｡)  (≧◡≦)  (๑´ڡ`๑)  (⁀ᗢ⁀)  (๑•̀ㅂ•́)و✧
(｡♥‿♥｡)  (づ｡◕‿‿◕｡)づ  ( ˘ ³˘)♥  (๑´⍢`๑)  ( •́ _ •̀)
(๑˃ᴗ˂)ﻭ  (｡･ω･｡)ﾉ♡  (＠＾－＾)  (〃￣ω￣〃ゞ  (･ω<)☆
(๑´• .̫ •ू`๑)  (✿◠‿◠)  ( •ω•ฅ)  (´｡• ᵕ •｡`)  (⁂•ི̛ᴗ•ི̛⁂)
(*≧ω≦)  (oﾟ▽ﾟ)o  (•̀ᴗ•́)و ̑̑  (ﾉ◕ヮ◕)ﾉ*:･ﾟ✧  (๑•̀ᄇ•́)ﻭ✧
(｡･Д･)ゞ  (☆ω☆)  (๑>ᴗ<๑)  ٩(｡•́‿•̀｡)۶  (๑♡⌓♡๑)
```

## 3. `work` 工作池

专用内容 4 条，运行时还会包含全部 `core`：

```text
( ≧Д≦)
(눈_눈)
〒.〒
(☉_☉)
```

自动触发条件：

```text
maid.getScheduleDetail() == Activity.WORK
```

## 4. `idle` 空闲池

专用内容 4 条，运行时还会包含全部 `core`：

```text
^_^
(╬ Ò ‸ Ó)
(≧▽≦)
(⁎˃ᆺ˂)
```

自动触发条件：

```text
maid.getScheduleDetail() == Activity.IDLE
或
maid.getTask() == TaskManager.getIdleTask()
```

## 5. `sleep` 睡眠池

共 16 条：

```text
(¦3[▓▓]
(￣o￣) . z Z
(-_-)zzz
(＝＿＝)～Zzz
(。-ω-)zzz
(￣ρ￣)..zzZZ
(*´0｀)～ゞ
(´～` )
（－_－）zzz
(´-ω-｀)Zzz…
(∪｡∪)｡｡｡zzz
(˘ω˘)ｽﾔｧ
(o_ _)o zZZ
(︶ω︶)
(＿ ＿*) Z z z
（´～｀）
```

自动触发条件：

```text
maid.getScheduleDetail() == Activity.REST
```

这里判断的是日程活动 `REST`，不要求女仆当前真的躺在床上。

## 6. `hurt` 受伤池

共 8 条：

```text
˃ʍ˂
✖‿✖
(╥﹏╥)
(>_<)
(つ﹏<)･ﾟ｡
(T_T)
(ノдヽ)
(｡>﹏<｡)
```

自动触发条件：

```text
触发 MaidDamageEvent
且
当前气泡集合为空
```

## 7. 原版调用方式

按当前日程选择 `work`、`idle` 或 `sleep`：

```java
KaomojiData.showRoutineKaomoji(
        maid,
        maid.getChatBubbleManager()
);
```

从 `hurt` 池选择：

```java
KaomojiData.showHurtKaomoji(
        maid,
        maid.getChatBubbleManager()
);
```

## 8. 自定义颜文字

直接显示附属选择的内容：

```java
maid.getChatBubbleManager().addChatBubble(
        TextChatBubbleData.type2(
                Component.literal("(•̀ᴗ•́)و")
        )
);
```

资源包或数据包可以通过原路径向现有池追加条目：

```text
data/touhou_little_maid/chat_bubble/kaomoji.json
```

加载器也会保存 `happy`、`danger` 等新键，但原版自动逻辑不会读取它们，也没有公开的“按任意池名随机取值”方法。

附属若需要以下语义，建议维护自己的池：

- `happy`
- `hungry`
- `low_health`
- `tool_warning`
- `work_complete`
- `need_owner`

## 9. 相关源码

```text
_reference/TouhouLittleMaid/src/main/java/com/github/tartaricacid/touhoulittlemaid/
datapack/KaomojiData.java
datapack/KaomojiDataReloadListener.java
entity/chatbubble/RandomEmoji.java
entity/chatbubble/implement/TextChatBubbleData.java
```
