---
name: project-vision
description: CintyezOS（原读作 Kemon OS）的长期目标——在一台 200 元档二手 Android 上做系统级 AI 桌面，四大支柱与当前原型的完成度边界
metadata:
  type: project
---

项目正式名是 **CintyezOS**；Cintyez 借自俄语 Синтез（合成），Kemon 是系统里 AI 主体的名字（全名 Kemon Cintyez，人格与命名继承自搁置项目 ORGANIC，详见 [[organic-reference]]）。曾用名 Kemon OS 已废弃。

项目目标是做一台"自己的 AGI 手机"：不是装一个 AI App，而是让 Kemon 本身就是系统界面。硬件路线是二手开放 Android（200 元档、尽量 8GB，讨论过小米 8 / LG V40），刷定制 AOSP；用户当前主力机是 Redmi K80。

四个支柱（来自用户 2026-10-21~23 与 DeepSeek 的对话）：
1. 工具箱：把手机能力注册成可调用的工具。
2. 观察学习：记录用户操作事件流 + 模型函数调用流，把目标函数"粘"成可复用新工具（用户称"万能胶水"），并做 MoE 式按情境路由，避免上下文爆炸。
3. 信息主权层：白名单信息源 + AI 摘要，锁屏即信息入口，不必逐个开 App。
4. 重做的桌面/启动叙事：AI 分组通知 → 上滑解锁、重力波坍塌成奇点 → "我们要构建什么？" → 应用矩阵（重要性环、聚焦后打开、长按注册为 Agent 对象）。

原始对话导出见 [[vision-source-conversation]]。

**Why:** 用户不满足于"手机上再装一个聊天框"，认为 AI 应该是操作系统层的主体；原型阶段不考虑隐私合规与成本，允许用云 API。
**How to apply:** 讨论功能时按这四层定位；判断某个改动是否值得做，先看它服务哪一层。不要把项目当成普通 launcher 皮肤或聊天 App。

当前 `kemon-os-v2.html` 是设计规格而非产品：交互与动效已完整，但应用"打开"是假启动页、Agent chips 只是展示不出现在对话里、通知与应用均为 mock、无真实模型与事件流采集。刷机/AOSP 属于后期阶段，短期应先在 Web 侧验证信息层与交互。
