# 技能快照（skills/）

这里是从 Qoder 用户技能目录 `C:\Users\User\.qoder-cn\skills\` 复制出来的 **15 个 SKILL.md 原文**，快照于 2026-10-24。每个技能一个文件夹，只有一个 `SKILL.md`，纯 Markdown，无依赖。

## 怎么在别的 harness 里用

- **支持 Agent Skills（SKILL.md + frontmatter）的 harness**（例如 Claude Code）：把整个文件夹拷到对应 skill 目录（用户级 `~/.claude/skills/<name>/`，或项目级 `.claude/skills/<name>/`），保持 `<name>/SKILL.md` 结构即可。
- **没有技能机制的 harness**：不用安装。SKILL.md 本身就是写给 AI 读的方法论文档；开工时说一句"读 handover/skills/emil-design-eng/SKILL.md，按它的规范做"即可。也可以把你常用的几条规则提炼进系统提示。
- 快照可能落后于源头；需要更新时重新从 Qoder 目录整份拷贝，不要手改。

## 这个项目实际用到 / 最相关的

| 技能 | 用途 | 在本项目中的使用场景 |
| --- | --- | --- |
| `emil-design-eng` | 界面做工与设计判断的总纲（动效决策、组件细节、性能） | 所有 UI 打磨、动画取舍 |
| `mobile-native` | 让网页在手机上像原生应用（触控、视口、滚动、输入框） | `kemon-os-v2.html` 的移动端手感 |
| `apple-design` | 手势驱动界面、弹簧、拖拽/回弹、半透明材质、排版 | 解锁、抽屉、矩阵拖拽 |
| `animation-performance` | 保持在帧预算内，排查掉帧 | 锁屏重力波、矩阵视点滑行 |
| `animation-vocabulary` | 用词反查（"那个弹一下的效果叫什么"） | 和用户对齐动效描述 |
| `break-ui` | 用最坏数据压界面（长文本、空列表、emoji） | 发布前压力测试锁屏通知 |
| `find-animation-opportunities` | 找"该动但没动"的地方（只读建议） | 迭代前的动效审计 |
| `improve-animations` | 输出可执行的动效改进计划（只读） | 大改前的规划 |
| `review-animations` | 审阅现有动画并给优先级 | 动效回归检查 |
| `prototype` | 快速做可交互原型 | 新视图先用原型验证 |
| `pick-ui-library` | 选 UI 库 | 只在真要引库时用（本项目原则是不引） |
| `animate` / `animate-expo` | Web / React Native 动效实现 | `animate` 可参考；`animate-expo` 本项目不用 |
| `ask-sonner` | Sonner toast 库 | 本项目不用（无框架） |
| `write-swift` | 现代 Swift 写法 | 本项目不用（未来 iOS 壳可能相关） |

## 技能清单

```text
animate/                         animate-expo/
animation-performance/           animation-vocabulary/
apple-design/                    ask-sonner/
break-ui/                        emil-design-eng/
find-animation-opportunities/    improve-animations/
mobile-native/                   pick-ui-library/
prototype/                       review-animations/
write-swift/
```
