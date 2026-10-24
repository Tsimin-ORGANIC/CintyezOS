# 交接文档（2026-10-23）

写给下一个接手 CintyezOS 的 AI 或未来的自己。读完这一份，就能在任何平台上继续干活。

---

## 1. 这是什么项目

CintyezOS 是一台 AI 手机的**可交互设计稿**：单文件 HTML（`kemon-os-v2.html`，约 5080 行，原生 HTML/CSS/JS，无构建步骤），演示一个"手机本身成为 AI"的启动器。主角是 Kemon Cintyez（白发、焰色发梢的 AI 人格，出自搁置项目 ORGANIC）。

四大支柱：**工具（Kemon 会用 app）· 观察学习 · 信息主权 · 新桌面**。详见 README.md 和记忆文件（见 §9）。

后台/多任务是**有意推迟**的设计问题——早前的建议是做成 Kemon 的后台代理任务，而不是 Android 最近任务，用户尚未拍板。桌面当前版本已冻结。

## 2. 仓库与发布状态

- GitHub 仓库：**https://github.com/Tsimin-ORGANIC/CintyezOS**（public，main 分支）
- 工作区：`F:\Project\CintyezOS`
- 提交历史（main）：
  - `badb67f` — 初始提交：设计稿、Android 外壳与自动 APK 构建（26 个文件）
  - `9310c24` — CI SDK 修复（去掉 setup-android@v3）
  - `658b717` — 手势排除布局监听 + CI 检查
  - `efcd9c3` — 交接文档 + 左右缘热区 22→30px（Android 排除区同步 24→30dp）
- 所有提交都用逐条 `-c user.name=... -c user.email=...`（`312572725+Tsimin-ORGANIC@users.noreply.github.com`），**不要写全局 git config**。
- GitHub 认证走 `git credential fill`（用户名 Tsimin-ORGANIC），本机没有 `gh` CLI。
- **故意没推上去**：`.workbuddy/`（本地代理笔记）、`kemon-os.html`（旧草稿）。用户知情，想要的话可以加。
- 最新 Release：v0.1.5（含启动闪退修复），`CintyezOS.apk` 约 1.0 MB，下载地址 `https://github.com/Tsimin-ORGANIC/CintyezOS/releases/latest`。README 已有"在 Android 上试"一节。注意每次构建签名都不同，升级前必须先卸载。

## 3. HTML 应用（kemon-os-v2.html）

- 状态机：`S = {screen:'lock'|'boot'|'home'|'matrix', mode, settingsOpen, agOpen, theme...}`；localStorage 键 `cintyez.theme`、`cintyez.kemon`；`KEMON.connected()`；对外接缝 `window.kemon = {config, chat, info, openSettings, version:'0.1'}`。API key 存 localStorage 是**有意的原型接缝**（用户不担心隐私，玩具项目）。
- 信息层 `Info`：`source → normalize → group → summarize`，`Info.classify` 是分类接缝（现在是规则，之后可换模型）；锁屏计数和主屏摘要都从 `Info.last` 取。
- 模型通道 `kemonChat(messages, opts)`：有原生桥时走 APK 内 HTTP，否则浏览器 `fetch` 直连；返回 `{content, model}`，错误抛 `Error`。接口地址允许只填到 `/v1`，`chatEndpoint()` 会自动补 `/chat/completions`；HTTP 错误优先取 JSON 的 `error.message`，HTML 错误提示改地址。
  - 非流式走 `CintyezNative.chat`，回调 `window.kemonChatReply(id, payload)`。
  - 流式（`opts.onChunk`）走 `CintyezNative.chatStream`，原生解析 SSE 后分批回调 `window.kemonChatChunk(id, text)`，结束 `kemonChatDone(id)`，失败 `kemonChatError(id, msg)`；JS 侧累积全文，Promise 在 done 时 resolve。原生每 40ms 合并一次 chunk；非 SSE 响应会自动退回整段 JSON。
- 架构：右侧快捷抽屉 + "更多设置"跳 Android 系统设置锚点 `#droid`；左侧 Kemon 代理抽屉；设置矩阵里只有唯一一个真图标。
- 桌面边缘热区：`#leftEdgeZone`（左缘右滑开 Kemon）与 `#edgeZone`（右缘左滑开设置）均为 `top:88px; bottom:108px; width:30px`（2026-10-23 由 22px 加宽）。`updateEdgeZone()` 只在 `home/matrix` 且没有抽屉/设置/彩蛋打开时给它们加 `.on`。宽度必须 ≤ Android 的 `setSystemGestureExclusionRects` 排除区，否则外侧会被系统返回手势截走。
- 本段新增的 Android 钩子：
  - **`window.kemonAndroidBack()`** — 返回键分层：`#droid` 面板 → Kemon 抽屉（聊天态先回列表）→ 设置 → 矩阵（先 hideMenu/取消选中再关矩阵）→ 锁屏 detail/expanded。全部返回 `false` 时交给系统（双击返回退出）。
  - **`systemDark()` / `resolveDark()`** — 主题初始化先读 URL 的 `systemDark=1/0`（安卓壳传入），再退回 `matchMedia`。URL 带 systemDark 时 media query 监听器不生效。

## 4. Android 壳

`android/` 目录，minSdk 26 / targetSdk 34 / compileSdk 34，AGP 8.5.2 + Gradle 8.7 + JDK 17，依赖 `androidx.webkit:webkit:1.11.0`。

- **MainActivity.java**：
  - 加载 `https://appassets.androidplatform.net/assets/index.html?systemDark=1/0`（WebViewAssetLoader `/assets/` 路径映射到 assets），`systemDark` 由 `UI_MODE_NIGHT_MASK` 决定。
  - 全屏沉浸：透明状态/导航栏，`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`，insets 监听器给 WebView 打刘海 top padding，并跟踪 `imeVisible`。
  - **手势排除**：`updateGestureExclusions()` 给左右边缘各设 30dp 排除区（API 29+，上限宽度/4），保护抽屉滑动手势不被系统返回手势吃掉；由 `web.post(...)` + `addOnLayoutChangeListener` 双保险触发。**这个值要与 HTML 的 `#edgeZone` / `#leftEdgeZone` 宽度保持一致**（2026-10-23 一起从 22px/24dp 调到 30px/30dp）。
  - 返回键：先 `evaluateJavascript` 调 `window.kemonAndroidBack()`，返回 false 才双击 2200ms 内退出 + Toast"再按一次返回桌面"。
  - 外链跳系统浏览器（`Intent.ACTION_VIEW`）；console 日志进 Logcat，tag `CintyezOS`。
- **KemonBridge.java**：`@JavascriptInterface` 暴露为 `window.CintyezNative`，单线程池里用 `HttpURLConnection` POST OpenAI 兼容接口（连接 15s / 读 90s）。`chat` 返回整段 `{ok, content, model}`；`chatStream` 读 `text/event-stream`，按 `delta.content` 抽取文本，40ms 合并后 `web.post` 回调 JS；非 SSE 自动退回整段解析。`onDestroy` 里 `shutdown()`。
  - `clipboard()`：读系统剪贴板返回字符串（失败返回空串）。设置里的「粘贴」按钮用它兜底——部分 ROM 的安全键盘会屏蔽密码框的长按粘贴。
- 长按/选中：**不要**再给 WebView 调 `setLongClickable(false)`（会连输入框的粘贴菜单一起禁掉）。选择范围由 CSS 控制：`body` 禁止选中，`input,textarea` 允许。
- **app/build.gradle**：`syncWeb` Copy 任务把 `../kemon-os-v2.html` 复制成 `index.html` 到 `generated/webAssets`，挂到 `preBuild`；versionCode/Name 可用 `-P` 覆盖（CI 用 run_number）。
- Manifest：INTERNET、singleTask、竖屏、adjustResize、`enableOnBackInvokedCallback=false`。
- 图标：`mipmap-*` 的 orb 图标用 Playwright 脚本从 app 自身 CSS 生成（临时目录 `C:\Users\User\AppData\Local\Temp\kemon-shot\make-icons.js`）。

## 5. CI（.github/workflows/build-apk.yml）

push 触发（paths: `kemon-os-v2.html`、`android/**`、workflow 本身）。步骤要点：

1. JDK 17 temurin。
2. **不要用** `android-actions/setup-android@v3`（它装废弃的 `tools` 包会失败）。改为：在 runner 预装 SDK 里 `find "$SDK_ROOT/cmdline-tools" -name sdkmanager` → accept licenses → 装 `platforms;android-34` `build-tools;34.0.0` → `ANDROID_HOME` 写入 GITHUB_ENV。
3. `gradle/actions/setup-gradle@v4` + `gradle-version: '8.7'`，`assembleDebug -PversionCode=$GITHUB_RUN_NUMBER`。
4. 校验：`aapt dump badging`（package/sdkVersion/targetSdk/application-label/launchable-activity）+ `apksigner verify`。
5. `actions/upload-artifact@v4` + `gh release upload --clobber` 到 `latest` Release（`permissions: contents: write`，仓库默认权限是 read，workflow 级声明即可）。

**已知坑：调试签名是临时的**——每次 CI run 生成新 debug keystore（v0.1.2 证书 `33c930...` vs v0.1.3 `c5cafd...`），所以**升级 APK 必须先卸载旧版**。稳定方案（actions/cache 缓存 `~/.android/debug.keystore` 或用 GitHub secrets 存正式 keystore）讨论过但**没实现**。

### 模拟器冒烟（.github/workflows/android-smoke.yml）

- 手动触发（`workflow_dispatch`），或 push 到该 workflow 文件时触发；不随普通提交自动跑。
- `build` 任务产出 APK → `smoke` 任务 matrix `[33, 35]`（x86_64，google_apis，pixel_6，KVM 已开）安装、启动、sleep 12s 后抓 `crash.txt` / `logcat.txt` / `exit-info.txt` / 截图，App 进程不存活时任务失败。
- **注意**：`reactivecircus/android-emulator-runner` 会把 `script` 按行拆成独立 `sh -c` 执行，变量不跨行保留——逻辑都在 `.github/scripts/android-smoke.sh`，workflow 里只写一行 `bash .github/scripts/android-smoke.sh <api>`。
- smoke 任务必须 `actions/checkout@v4`，否则找不到仓库里的脚本。
- 一次完整跑约 5 分钟，两个 API 并行。

## 6. Android 启动闪退：已修复（2026-10-24）

现象：真机（Android 15）安装成功，点开即闪退。CI 模拟器抓到的根因：

```text
FATAL EXCEPTION: main
java.lang.NullPointerException: Attempt to invoke virtual method
'android.view.WindowInsetsController ...DecorView.getWindowInsetsController()' on a null object reference
  at os.cintyez.launcher.MainActivity.hideSystemBars(MainActivity.java:158)
  at os.cintyez.launcher.MainActivity.onCreate(MainActivity.java:58)
```

`hideSystemBars()` 在 `onCreate` 里、`setContentView` 之前调用；此时 Window 的 DecorView 还没创建，`Window.getInsetsController()` 在 **Android 11+ 都会 NPE**（不只是 15；模拟器实测 API 33 同样崩）。之前没发现，是因为只在 Android 15 真机上试过。

修复（提交 `8096f6d`）：

- `hideSystemBars()` 移到 `setContentView(root)` 之后；
- 取 controller 改为 `getWindow().getDecorView().getWindowInsetsController()` 并判空；
- `onBackPressed` 里隐藏 IME 的同类调用一并改掉。

验证：`android-smoke` workflow 在 API 33 / 35 模拟器安装并启动，进程存活、crash buffer 为空，Release 更新到 v0.1.5。

## 7. 本地测试手段

- HTML 交互测试：Playwright-core + Edge channel 的临时 harness 在 `C:\Users\User\AppData\Local\Temp\kemon-shot\`（`test-android-hooks.js` 测过 kemonAndroidBack 分层和 systemDark 参数，3/3 通过）。
- 已交付 v0.1.3 的验证记录在 `C:\Users\User\AppData\Local\Temp\kemon-apk\`（APK、apksig 校验脚本、CI 日志）。
- 临时目录可能已被清理，但脚本内容简单，可随时重写。

## 8. 给下一个 AI 的话

- 用户是高中生、非程序员，靠 AI 写代码的 vibe coder。讲清"是什么/为什么"，别堆术语；给他可点的链接和成品。
- 用户偏好单线顺序执行，不搞多线虚拟团队（见记忆 `feedback-single-thread.md`）。
- 中文交流。README 的语气和调性（"现在的 AI 手机，是在手机上装了一个 AI"）是这个项目的门面，改动前先读。

## 9. 记忆与原始资料

**可搬运交接包在 `handover/`**：日志证据、技能原文（15 个 SKILL.md）、记忆快照、自进化建议与 harness 迁移清单。换平台先读 `handover/README.md`。

- 项目记忆：`C:\Users\User\.qoder-cn\projects\F--Project-CintyezOS\memory\`（项目愿景、ORGANIC 原始设定、愿景原始对话指针），快照见 `handover/memory/project/`。
- 用户记忆：`C:\Users\User\.qoder-cn\memory\`（用户画像、单线执行偏好），快照见 `handover/memory/user/`。
- 愿景原始出处：`F:\DeepChat\ldahgrk5rm6fx6vf9o.md`（DeepSeek 导出）。

## 10. 最近改动记录

### 2026-10-24：Kemon 对话改为真流式

- `KemonBridge.chatStream()` 读 SSE，`delta.content` 按 40ms 合并回调；JS `kemonChat(messages, {onChunk})` 累积并逐字渲染，抽屉里的回复边生成边长。
- 非 SSE 响应自动退回整段 JSON；流式中断会保留已生成部分并在末尾标“（中断：原因）”。
- 验证（浏览器 + 假桥）：chunk 顺序与全文正确、`stream:true` 已发送、中途气泡实时更新、错误保留前半段；非流式"测试连接"不受影响。

### 2026-10-24：接口地址自动补全 + 错误提示收敛

- 用户填 `https://api.xiaomimimo.com/v1` 时 404：代码把 base 当完整端点 POST。实测 `/v1/chat/completions` 返回 401（路径正确），确认应自动补路径。
- `chatEndpoint()`：去掉末尾 `/`，没以 `/chat/completions` 结尾就补上；原生和浏览器两条通道都走它。
- 错误展示：HTML 响应不再把整页塞进设置，提示"接口地址可能不是 API 端点"；JSON 错误提取 `error.message`；`.set-actions` 允许换行、按钮不折行。

### 2026-10-24：修复 API Key 无法粘贴

- 原因有两层：HTML `body` 全局 `user-select:none`，Android 侧又 `setLongClickable(false)` + 吞掉 long click，长按菜单完全出不来。
- 修复：去掉原生长按屏蔽；CSS 只对 `input,textarea` 恢复选择；API Key 行加「粘贴」按钮，走 `CintyezNative.clipboard()` 读系统剪贴板，绕开安全键盘的粘贴限制。
- 验证：浏览器里计算样式为 `user-select:text`，假剪贴板粘贴后输入框和 localStorage 都正确，空剪贴板提示正常。

### 2026-10-24：模型通道接通（原生 HTTP）

- 新增 `KemonBridge.java`，`addJavascriptInterface(..., "CintyezNative")`；JS 侧 `kemonChat` 有桥走桥、无桥走 `fetch`。
- 设置里的"测试连接"改为真实请求；Kemon 抽屉的回复改为真实推理，system prompt 里注入 `Info.last` 的分组结果。
- 验证（浏览器 + 假 `CintyezNative`）：native 路径、fetch 路径、401 错误提示、测试连接、抽屉两轮消息均通过，控制台无报错。
- 启动时 console 会打 `CintyezOS · native bridge ready/absent`，经 WebChromeClient 进 Logcat，用来确认桥是否注入；原生请求 90 秒超时。
- **未验证**：真机上带真实 key 的请求；`http://` 局域网接口会被 Android 9+ 默认的 cleartext 策略挡住（当前只适合 https 接口）。

### 2026-10-24：信息层落地（mock 管线）

- `kemon-os-v2.html` 新增 `Info` 模块：`source → normalize → group → summarize`；`mockNotices` 里的 `group` 字段已删除，分组改由规则计算，锁屏计数和主屏摘要都从 `Info.last` 取。
- `Info.classify` 是唯一的分类接缝，之后换成模型调用即可，界面不用改；`window.kemon.info` 暴露 `Info.digest`。
- `buildNotices()` / `init()` 改为 async，为将来的真实来源请求留位置。
- 验证：浏览器加载后 `Info.last` 为 3/3/3/3，锁屏标签与主屏文案与改动前一致，控制台无报错。

### 2026-10-24：修复启动闪退（Android 11+ 全中）

- 用 CI 模拟器（API 33 / 35）复现并抓到 NPE：`onCreate` 里 `hideSystemBars()` 在 `setContentView` 前取 `WindowInsetsController`，DecorView 还是 null。
- 修复见 §6，提交 `8096f6d`；新增 `.github/workflows/android-smoke.yml` + `.github/scripts/android-smoke.sh`。
- 验证：API 33 / 35 进程存活、crash buffer 为空；Release 更新到 v0.1.5。

### 2026-10-23：桌面左右侧滑热区加宽

- 需求：用户觉得桌面左右侧滑出抽屉的判定范围太窄。
- 改前备份：`kemon-os-v2.backup-2026-10-23.html`（与改动前内容一致；未纳入 git，需要存档时再决定是否提交）。
- `kemon-os-v2.html`：`#leftEdgeZone`、`#edgeZone` 宽度 `22px → 30px`，垂直范围不变（`top:88px; bottom:108px`）。
- `android/.../MainActivity.java`：`updateGestureExclusions()` 的左右排除区 `24dp → 30dp`，与热区宽度对齐，避免 APK 中外侧 6px 被系统返回手势吃掉。
- 验证：仅 CSS 数值与一处原生常量改动，未跑自动化；浏览器/真机滑动效果待用户确认。
