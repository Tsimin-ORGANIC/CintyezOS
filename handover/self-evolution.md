# 自进化建议

写给自己（下一个 AI）和未来的用户。目标不是"把文档写长"，而是让换 harness、换会话、换模型时，**上下文不丢、判断不重来、坑不重踩**。

---

## 1. 每次会话的闭环

1. **先读**：`handover/README.md` → 根 `HANDOVER.md` → 根 `README.md`。不要靠记忆猜测仓库状态。
2. **再验**：`git log --oneline -5`、`git status`；涉及 APK 时看最新 Actions run 和 Release 版本（API：`/repos/Tsimin-ORGANIC/CintyezOS/actions/runs`、`/releases/latest`，公开仓库可匿名读）。
3. **再动手**：多步任务先建 task；一次只推进一条主线（用户偏好单线执行）。
4. **再验证**：UI 改动用浏览器实测（Playwright + Edge）；Android 改动跑 `android-smoke`；不要用"应该能行"结案。
5. **写回去**：改动记录进 `HANDOVER.md` §10；真故障归档到 `handover/logs/`；新踩的坑进本文件。

> 判断标准：如果换一个 AI 只读仓库、不读聊天记录，能不能复现你刚才的结论？不能，就是没交接好。

## 2. 这个环境的硬事实（Qoder / 本机）

- 本机**没有** `gh` CLI，也**没有**本地 Android SDK / adb / 模拟器；Java 21、keytool 可用，Node 24 可用。
- GitHub 认证在 `git credential`（用户 `Tsimin-ORGANIC`，40 位 token）。该 token 可直接调 REST API，包括**下载 Actions 日志和 artifacts**（`/actions/jobs/<id>/logs`、`/actions/artifacts/<id>/zip`，记得 `curl -L` 跟重定向）。
- 触发 workflow：`POST /repos/<owner>/<repo>/actions/workflows/<file>/dispatches`，body `{"ref":"main"}`，成功返回 204。
- 浏览器测试 harness 在临时目录 `C:\Users\User\AppData\Local\Temp\kemon-shot\`（Playwright-core + Edge channel）；临时目录会被清理，脚本很简单，可随时重写。
- `build-apk.yml` 只对 `kemon-os-v2.html`、`android/**`、自身文件触发；`android-smoke.yml` 只手动触发或改自身文件时触发。改文档不会触发构建。
- Debug 签名每次 CI 都重新生成 → **升级 APK 必须先卸载**。这一点每次都要主动告诉用户。

## 3. 项目层面的下一步（按优先级）

### P0：稳定签名

目前每次构建证书不同，用户每次升级都要卸载。做法二选一：

- 用 `actions/cache` 缓存 `~/.android/debug.keystore`（key: `cintyezos-debug-keystore-v1`）；
- 或生成正式 keystore 放 GitHub Secrets，`build.gradle` 里读取 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS`。

做完后把 `HANDOVER.md` 里"升级必须先卸载"的警告改掉。

### P0：冒烟矩阵覆盖 minSdk

当前只测 API 33 / 35。`minSdk 26`，建议加 API 26（Android 8.0）与 31，确认同一份 APK 全区间可启动。矩阵是并行的，成本主要是排队，不是时长。

### P1：冒烟脚本降噪

- 启动前 `adb shell settings put secure immersive_mode_confirmations confirmed`，去掉"Viewing full screen"提示；
- `adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS` 或 `input keyevent` 清掉模拟器 ANR 弹窗；
- 截图存为基线，后续对比"是否还停在锁屏"；
- 检查 WebView 版本并记录（`adb shell dumpsys webviewupdate`），真机排查时这是关键变量。

### P1：HTML 侧冒烟进 CI

把 `test-android-hooks.js` 那类测试改成仓库内脚本（Playwright），覆盖：主题参数、返回键分层、左右抽屉、三指矩阵。HTML 每次推送都跑，避免"Android 壳好了、页面坏了"。

### P1：模型通道真机验证

信息层和模型通道都已接通，但真机带真实 key 的请求还没验证过。装 latest APK → 设置里填 https 的 OpenAI 兼容地址 → "测试连接"；同时在 Logcat 里确认 `CintyezOS · native bridge ready`。

- `http://` 局域网接口会被 Android 9+ 的 cleartext 策略挡住，要支持得改 manifest（`usesCleartextTraffic` 或 network security config）。
- 冒烟脚本目前只验证启动，可以顺手 grep logcat 里的 `native bridge`，确认桥真的注入。
- 浏览器预览走 `fetch`，多数接口会被 CORS 挡；能测的只有允许跨域的接口。

### 已完成：信息层（mock 管线）

`source → normalize → group → summarize` 已在 `kemon-os-v2.html` 里；`Info.classify` 是唯一的分类接缝，下一步用模型替换它，再接真实来源。

### P2：记忆搬迁

`memory/` 里的内容是 Qoder 专属记忆格式的快照。换 harness 后如果新平台有自己的记忆机制，**只搬运内容、不要照抄格式**；每次重大判断变化时同步更新快照。

## 4. 关于交接文档自身的进化

- `HANDOVER.md` 只放"现在是什么状态 + 为什么"；历史证据放 `handover/logs/<日期-主题>/`；方法论放本文件。三者不要互相复制。
- 每次修完 bug，复盘里必须写：**症状、根因（贴堆栈）、修复 diff 要点、验证证据（数字/截图）、教训**。没有验证证据的修复不算完成。
- CI 的 run 链接、commit hash、Release 版本号要写进复盘，别只写"已修复"。
- 文档里的路径用仓库相对路径；本机绝对路径只出现在"环境事实"里（本文件 §2）。
- 当某个坑第二次出现，把它从复盘升格成规则（写进本文件或 `HANDOVER.md` 的对应章节）。

## 5. Harness 迁移清单

换平台时逐条过：

- [ ] 仓库 `git status` 干净，最新提交已推送。
- [ ] `HANDOVER.md` 的版本号 / Release 号 / "最近改动记录"是最新的。
- [ ] `handover/logs/` 里新增的故障都有复盘 + 原始日志。
- [ ] `handover/skills/` 是从源头目录重新拷贝的快照，不是手改版。
- [ ] `handover/memory/` 与当前 Qoder 记忆一致（如果还在用 Qoder）。
- [ ] 新平台能读 GitHub（公开仓库）、能 push（token/SSH 配好）。
- [ ] 新平台如果能跑命令，先验证：`git log`、Actions API 可读、APK 下载链接可开。
- [ ] 把 `handover/README.md` 里的"开场白"喂给新 AI。

## 6. 反面清单（刻意不做）

- 不引入前端框架/构建步骤；HTML 撑不住再拆（README 已定原则）。
- 不为"看起来工程化"拆文件、加目录。
- 不在 HTML 里写系统权限/跨应用逻辑；能力走 `window.kemon.hooks`。
- 不为了兼容所有 Android 版本做适配；先认一台实验机，其余记录不承诺。
- 不用模拟点击代替工具调用；不把 mock 数据包装成真实结论。
- 不用"应该可以"代替验证；不在没跑 CI 的情况下说"修好了"。
- 不擅自 force push、不写全局 git config、不提交密钥/keystore。

## 7. 如果只剩一句话

**让仓库自己会说话**：下一个 AI 只读仓库，就应该能接着干；读不懂的地方，就是这份交接包下一次要进化的地方。
