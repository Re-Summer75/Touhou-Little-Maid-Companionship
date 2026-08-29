---
name: tlm-codebase-search
description: 用已建好的向量索引按语义搜索本模组与上游 TouhouLittleMaid 的代码——问"这个行为在哪实现的""谁决定她往哪走""弓什么时候松手"这类知道行为但不知道符号名的问题。凡是需要在本仓库或上游 TLM 里定位一段逻辑、理解某个机制怎么实现、或者 Grep 试了几次没找到对的文件，都该用这个 skill；也包括改完代码后需要更新索引的时候。Semantic/vector search over this mod and upstream TouhouLittleMaid source. Use whenever locating behaviour by description rather than by identifier name, when grep has failed to surface the right file, or when refreshing the index after code changes.
---

# 语义索引：怎么用

`E:\ALL\KaiFa\claude\RAG` 下有一套建好的混合检索索引（稠密向量 + BM25，RRF 融合），覆盖两个仓库。

| 索引 | 仓库 | 块数 |
|---|---|---:|
| 本模组 | `E:/ALL/KaiFa/MCMods/TLM-Companionship` | 4822 |
| 上游 TLM | `E:/ALL/KaiFa/MCMods/TouhouLittleMaid-1.20` | 5883 |

模型 `jinaai/jina-embeddings-v5-text-small`（1024 维）。路径全部写死，只在这台机器上有效。

## 先把常驻服务打开

一次搜索里检索本身只要 6 毫秒，**其余十八秒全在加载模型**。常驻进程把这笔钱只付一次：

```bash
E:/ALL/KaiFa/claude/RAG/cbi.sh serve &
```

之后每次搜索约 1 秒；没开就自动退回十八秒那条路，命令不用改、也不会报错。首次请求要等约 27 秒加载模型，之后都是热的。闲置 30 分钟自动退出，不会过夜占着显存。

查状态用 `cbi.sh serve --status`，停用 `cbi.sh serve --stop`。

**一个会话里搜三次以上就值得先开它。** 不开的话十次搜索要白等三分钟。

## 搜

工作目录在哪个仓库就搜哪个：

```bash
E:/ALL/KaiFa/claude/RAG/cbi.sh search "她怎么决定这一架该不该打" -k 8
```

从别处搜、或者要搜上游，用 `--path-root`：

```bash
E:/ALL/KaiFa/claude/RAG/cbi.sh search "Brain 活动是怎么注册的" \
  --path-root E:/ALL/KaiFa/MCMods/TouhouLittleMaid-1.20 -k 8
```

常用开关：

- `-k N` 返回条数，默认 8
- `--quiet` 只出路径和行号，不出代码片段——扫一眼定位用这个
- `--json` 结构化输出，要程序化处理时用
- `--path 'features/*'` 按 glob 限定目录
- `--code-only` 排除 markdown 和配置块，只留代码
- `--snippet-lines N` 每条显示多少行，默认 12

## 什么时候用它，什么时候别用

**知道名字就用 Grep。** 找 `MovementIntentAuthority`、`shouldTeleport` 这种确定的符号，Grep 是瞬时且精确的，语义检索反而更慢更模糊。

**知道行为不知道名字才用这里。** "谁在控制她的脚""条件是每 tick 检查还是只在进入时检查""走过去然后做一件事的通用骨架"——这类问题的答案分散在若干文件里，符号名也猜不出来，正是索引存在的理由。

**Grep 试两次没中就换过来。** 说明你猜的命名和作者的不一样，这时候继续猜关键词是在浪费时间。

**想知道上游怎么做的**，就搜上游那个索引。本模组深度依赖 TLM 的 Brain/Activity 体系，"原版是怎么处理的"经常是设计决策的前提。

## 读结果时要知道的一件事

**排第一的经常是测试而不是实现。** 这是量出来的，不是猜的：60 条标注查询里，期望文件进前十的有 54 条（90%），但好几条的第一名是测那个行为的 GameTest，而不是行为本身。

| 查询 | 第一名 | 实际想要的 |
|---|---|---|
| 弓什么时候松手放箭 | `RangedFireGameTests` | `RangedDrawCycle.readyToRelease` |
| 追击时停止距离怎么更新 | `CombatApproachGameTests` | `CombatMovement.chase` |

所以**看前五条，别只看第一条**。测试本身也常常有用——它用可执行的形式说明了这个行为该是什么样。

`--code-only` 值得知道它到底做什么：它把第一名从文档换成代码，但**不改变召回**。同一套 60 条评测，加不加都是 54/60、连未命中的都是同样六条。例如问"走过去然后做一件事的通用骨架"，默认前三名全是 markdown，加上之后第一名变成 `ApproachAndCommitAction.java`——后者显然更有用，但期望的 `Errand.java` 两种情况下都没进前十。所以：**想要第一眼看到代码就加，指望它帮你找到原本找不到的东西就不会**。

那六条固定未命中说明这套配置有它的边界。90% 是可靠的期望值，不是 100%——搜不到就换个说法再搜一次，或者退回 Grep。

## 代码改了之后

```bash
E:/ALL/KaiFa/claude/RAG/cbi.sh index E:/ALL/KaiFa/MCMods/TLM-Companionship \
  --exclude geckolib_model_reference
```

增量的，但两条路径的代价差很多，值得知道：

- **没有任何改动：约 1 秒。** 确认无事可做就直接返回，不加载模型。所以随手跑一次确认索引是新的，几乎不花钱。
- **有任何改动：约 27 秒。** 哪怕只重算一个块，也要先把模型加载进来。常驻服务帮不上这里——它只服务搜索。

文件按内容哈希跳过（`touch` 改 mtime 不会触发重建），块按内容哈希复用向量。改一个文件通常只有个位数的块真正重算，其余走缓存——所以那 27 秒基本全是加载模型，与改动量无关。

结论是**别每改一个文件就重建**，攒一批再跑。

`--exclude geckolib_model_reference` 每次都要带上，原因见下。漏带不会报错，只会把 30 个模型文件重新灌进去（约 7800 块）。

上游基本不动，需要时：

```bash
E:/ALL/KaiFa/claude/RAG/cbi.sh index E:/ALL/KaiFa/MCMods/TouhouLittleMaid-1.20 \
  --only src/main/java --only src/test/java --only src/main/resources/data
```

## 三件不要做的事

**不要换模型。** 不同模型的向量几何不通用，换了必须全量重建，而且两边混在一起会静默出错（索引里绑定了模型名，不匹配会拒绝）。现在这个是在 60 条中英评测上选出来的：v5-text-small 总分 93%，jina-code-embeddings-1.5b 87% 且吃六倍显存——名字里有 code 的那个输了，别凭直觉换回去。

**不要索引机器生成的数据。** 上游那 486 个 GeckoLib 模型/动画 JSON 曾经让一次跑了 45 分钟的索引直接 CUDA OOM 死掉：分块器按字符数估 token，在密集数字上低估 2.4 倍，于是块远超预期长度，而注意力是二次的。现在有 `MAX_SEQ_LENGTH=2048` 兜底不会再崩，但这类数据体量极大（本仓库 30 个文件曾占索引的 62%）、拖慢重建、且对检索毫无贡献——实测排除前后召回完全一样，54/60。

**不要拿它替代读代码。** 它告诉你去哪看，不告诉你那段代码对不对。定位到之后照常 Read。

## 出问题时

搜索报 `no index at ...`：索引没建或路径不对，确认 `--path-root` 指的是仓库根。

搜索突然变成十八秒：常驻服务掉了，`cbi.sh serve --status` 确认，重新 `serve &`。

索引时 CUDA OOM：有超长块混进来了。先确认 `--exclude` 带上了，再看是不是新增了机器生成的目录。

`cbi.sh reset <repo>` 删掉某个索引重来；`cbi.sh status <repo>` 看块数、向量数、模型绑定。`vectors` 明显多于 `distinct_chunks` 说明有孤儿向量，`cbi.sh compact <repo>` 回收。
