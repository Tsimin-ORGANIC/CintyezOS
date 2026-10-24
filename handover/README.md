# 交接包（handover/）

这是给"下一个 AI / 下一个 harness"的完整上下文包。根目录的 `HANDOVER.md` 是项目主交接文档；这个文件夹放的是**可搬运的佐料**：日志、技能、记忆、自进化建议。目的只有一个：换任何平台（Claude Code / Codex / Cursor / 其他），把仓库丢过去就能接上。

## 先读什么（按顺序）

1. `../README.md` — 项目是什么、四个支柱、路线图、边界。
2. `../HANDOVER.md` — 当前状态、Android 壳与 CI 细节、踩过的坑、最近改动。
3. `logs/2026-10-24-android-crash/` — 最近一次真实故障的完整证据链（崩溃日志、修复、验证）。
4. `skills/` — 做 UI/动效时应该加载的技能原文。
5. `memory/` — 用户画像、协作偏好、项目愿景的原始记忆。
6. `self-evolution.md` — 给未来 AI 的自我迭代建议与 harness 迁移清单。

## 换 harness 的开场白（直接复制）

```text
读 F:\Project\CintyezOS\handover\README.md、HANDOVER.md 和 README.md，然后按 handover/logs/2026-10-24-android-crash/ 里的记录确认当前 APK 状态。
用户是高中生、非程序员，中文交流，单线顺序执行。
下一步按 README 路线图：在 kemon-os-v2.html 里跑通 mock 信息层。
```

## 目录说明

```text
handover/
├── README.md                  ← 你正在读的索引
├── self-evolution.md          ← 自进化建议 + 迁移清单
├── logs/
│   └── 2026-10-24-android-crash/
│       ├── README.md          ← 故障复盘（症状 → 根因 → 修复 → 验证）
│       ├── before-fix/        ← 修复前的原始日志（API 33 / 35，App 崩）
│       └── after-fix/         ← 修复后的原始日志（进程存活、crash 为空）+ 截图
├── skills/
│   ├── README.md              ← 15 个技能清单与迁移方法
│   └── <skill>/SKILL.md       ← 技能原文（Markdown，无依赖）
└── memory/
    ├── user/                  ← 用户级记忆（画像、协作偏好）
    └── project/               ← 项目级记忆（愿景、ORIGIN 设定、原始对话指针）
```

## 更新规则

- 每修完一个真实问题，在 `logs/` 下按 `YYYY-MM-DD-简短主题/` 新建目录，放原始日志 + 一份复盘 README。
- 每次会话结束，更新根目录 `HANDOVER.md` 的"最近改动记录"；新判断/新坑同步到 `self-evolution.md`。
- 技能和记忆是**快照**：如果源头（Qoder 的 skills / memory 目录）变了，重新拷贝一次，不要手改快照。
- 日志里不要放真实设备标识、账号、密钥；目前全部来自模拟器，安全。
