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
- 所有提交都用逐条 `-c user.name=... -c user.email=...`（`312572725+Tsimin-ORGANIC@users.noreply.github.com`），**不要写全局 git config**。
- GitHub 认证走 `git credential fill`（用户名 Tsimin-ORGANIC），本机没有 `gh` CLI。
- **故意没推上去**：`.workbuddy/`（本地代理笔记）、`kemon-os.html`（旧草稿）。用户知情，想要的话可以加。
- 最新 Release：v0.1.3（versionCode 3），`CintyezOS.apk` 约 1.0 MB，下载地址 `https://github.com/Tsimin-ORGANIC/CintyezOS/releases/latest`。README 已有"在 Android 上试"一节。

## 3. HTML 应用（kemon-os-v2.html）

- 状态机：`S = {screen:'lock'|'boot'|'home'|'matrix', mode, settingsOpen, agOpen, theme...}`；localStorage 键 `cintyez.theme`、`cintyez.kemon`；`KEMON.connected()`；对外接缝 `window.kemon = {config, chat, openSettings, version:'0.1'}` 和 `kemonChat()`。API key 存 localStorage 是**有意的原型接缝**（用户不担心隐私，玩具项目）。
- 架构：右侧快捷抽屉 + "更多设置"跳 Android 系统设置锚点 `#droid`；左侧 Kemon 代理抽屉；设置矩阵里只有唯一一个真图标。
- 本段新增的 Android 钩子：
  - **`window.kemonAndroidBack()`** — 返回键分层：`#droid` 面板 → Kemon 抽屉（聊天态先回列表）→ 设置 → 矩阵（先 hideMenu/取消选中再关矩阵）→ 锁屏 detail/expanded。全部返回 `false` 时交给系统（双击返回退出）。
  - **`systemDark()` / `resolveDark()`** — 主题初始化先读 URL 的 `systemDark=1/0`（安卓壳传入），再退回 `matchMedia`。URL 带 systemDark 时 media query 监听器不生效。

## 4. Android 壳

`android/` 目录，minSdk 26 / targetSdk 34 / compileSdk 34，AGP 8.5.2 + Gradle 8.7 + JDK 17，依赖 `androidx.webkit:webkit:1.11.0`。

- **MainActivity.java**：
  - 加载 `https://appassets.androidplatform.net/assets/index.html?systemDark=1/0`（WebViewAssetLoader `/assets/` 路径映射到 assets），`systemDark` 由 `UI_MODE_NIGHT_MASK` 决定。
  - 全屏沉浸：透明状态/导航栏，`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`，insets 监听器给 WebView 打刘海 top padding，并跟踪 `imeVisible`。
  - **手势排除**：`updateGestureExclusions()` 给左右边缘各设 24dp 排除区（API 29+，上限宽度/4），保护抽屉滑动手势不被系统返回手势吃掉；由 `web.post(...)` + `addOnLayoutChangeListener` 双保险触发。
  - 返回键：先 `evaluateJavascript` 调 `window.kemonAndroidBack()`，返回 false 才双击 2200ms 内退出 + Toast"再按一次返回桌面"。
  - 外链跳系统浏览器（`Intent.ACTION_VIEW`）；console 日志进 Logcat，tag `CintyezOS`。
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

## 6. 遗留问题：Android 15 启动闪退（已暂停）

现象：安卓 15 真机安装成功，点开即闪退。已排查过的：

- ✅ APK dex 里确认存在 `Landroidx/webkit/WebViewAssetLoader` 和 `Los/cintyez/launcher/MainActivity`（不是缺类）。
- ✅ HTML 里只用了 localStorage，没有 alert/fetch 等风险 API。
- ✅ 逐行读过 MainActivity.java 1–130 行，候选嫌疑：Android 15 edge-to-edge/predictive-back 行为变化（targetSdk 34）、`hideSystemBars()` 在 attach 前调用的时机问题、刘海 padding 的 insets 监听、Toast 时序、OEM 魔改。没有定论。
- **下一步计划（未执行）**：在 GitHub Actions 里起 Android 15 模拟器（`reactivecircus/android-emulator-runner@v2`，api-level 35，google_apis x86_64），`am start` 后抓 `adb logcat` 的 FATAL EXCEPTION 和截图作为 artifact——日志一到手基本就能定位。

本地没有 Android SDK/adb，也没有模拟器；所有验证都靠 CI。

## 7. 本地测试手段

- HTML 交互测试：Playwright-core + Edge channel 的临时 harness 在 `C:\Users\User\AppData\Local\Temp\kemon-shot\`（`test-android-hooks.js` 测过 kemonAndroidBack 分层和 systemDark 参数，3/3 通过）。
- 已交付 v0.1.3 的验证记录在 `C:\Users\User\AppData\Local\Temp\kemon-apk\`（APK、apksig 校验脚本、CI 日志）。
- 临时目录可能已被清理，但脚本内容简单，可随时重写。

## 8. 给下一个 AI 的话

- 用户是高中生、非程序员，靠 AI 写代码的 vibe coder。讲清"是什么/为什么"，别堆术语；给他可点的链接和成品。
- 用户偏好单线顺序执行，不搞多线虚拟团队（见记忆 `feedback-single-thread.md`）。
- 中文交流。README 的语气和调性（"现在的 AI 手机，是在手机上装了一个 AI"）是这个项目的门面，改动前先读。

## 9. 记忆与原始资料

- 项目记忆：`C:\Users\User\.qoder-cn\projects\F--Project-CintyezOS\memory\`（项目愿景、ORGANIC 原始设定、愿景原始对话指针）。
- 用户记忆：`C:\Users\User\.qoder-cn\memory\`（用户画像、单线执行偏好）。
- 愿景原始出处：`F:\DeepChat\ldahgrk5rm6fx6vf9o.md`（DeepSeek 导出）。
