# 女仆独立成就系统（已并入原版进度）

原来的「女仆独立成就」是自成一套的第二套成就系统：自己的 JSON 目录、自己的触发类型、
自己的列表式界面。它已经整体并入原版进度系统，本文只保留迁移说明。

**新文档在这里：[女仆原版进度系统](../advancements/README.md)。**

## 为什么换掉

独立成就只能覆盖本模组自己上报的那几件事。用户要的是「玩家有什么女仆就有什么」——
拿到铁镐、下界旅行、模组自带的进度，女仆都该有。做到这一点只能让原版自己去分发判定，
于是有了镜像玩家方案；一旦女仆拥有真正的 `PlayerAdvancements`，再维护第二套成就就没有意义了。

## 存档会怎么样

不需要任何手工操作。每只女仆**首次**建立进度文件时自动迁移一次：

1. 读旧 TaskData `tlm_companionship:achievement_progress`；
2. 已解锁的成就静默补齐成对应进度（直接写进度、不走 `award`，所以不会补发聊天广播与奖励经验）；
3. 旧的计数（喂食次数、蛋糕次数、累计经验）抬进新的 `MaidStatistics` 统计量；
4. 清空旧 TaskData。

11 条成就一对一映射，ID 从 `tlm_companionship:<名称>` 变成 `tlm_companionship:maid/<名称>`，
标题与描述沿用原来的 `achievement.tlm_companionship.*` 语言键，所以界面上看到的文字没变。
新增了一条根进度 `tlm_companionship:maid/root` 作为女仆自己的 tab。

## 附属模组要改什么

| 旧做法 | 新做法 |
| --- | --- |
| `RegisterMaidAchievementTriggersEvent` 注册触发类型 | 不需要了，女仆读的就是玩家那份进度表 |
| `RegisterMaidAchievementsEvent` 或 `data/<ns>/maid_achievements/*.json` | 直接发 `data/<ns>/advancements/*.json` |
| `MaidAchievementApi#trigger` 上报 | 用原版触发器；女仆专属条件用四个自定义触发器 |
| 目录同步包、解锁通知包 | 已删除，网络协议版本提升到 `12` |

四个自定义触发器（喂食、等级、好感等级、累计经验）的字段说明见
[新文档第 5 节](../advancements/README.md#5-自定义触发器)。
