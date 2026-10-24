# 故障复盘：启动即闪退（Android 11+）

- 日期：2026-10-24
- 影响版本：v0.1.3 / v0.1.4（APK 安装成功，点开即闪退）
- 影响范围：**Android 11+ 全部**，不只是用户报告的 Android 15
- 修复版本：v0.1.5（提交 `8096f6d`）
- 修复提交：https://github.com/Tsimin-ORGANIC/CintyezOS/commit/8096f6d

## 症状

真机（Android 15）安装顺利，启动瞬间闪退，无任何界面。APK 本身完整：dex 里能找到 `MainActivity` 和 `WebViewAssetLoader`，签名、入口、assets 都校验通过。

## 根因

```text
FATAL EXCEPTION: main
Process: os.cintyez.launcher PID: 1945
java.lang.RuntimeException: Unable to start activity ComponentInfo{os.cintyez.launcher/os.cintyez.launcher.MainActivity}:
  java.lang.NullPointerException: Attempt to invoke virtual method
  'android.view.WindowInsetsController com.android.internal.policy.DecorView.getWindowInsetsController()'
  on a null object reference
  at com.android.internal.policy.PhoneWindow.getInsetsController(PhoneWindow.java:4092)
  at os.cintyez.launcher.MainActivity.hideSystemBars(MainActivity.java:158)
  at os.cintyez.launcher.MainActivity.onCreate(MainActivity.java:58)
```

`onCreate()` 在 `setContentView()` **之前**调用了 `hideSystemBars()`。此时 `PhoneWindow` 的 DecorView 还没创建（`mDecor == null`），`Window.getInsetsController()` 直接解引用 null DecorView → NPE。Android 11+ 的实现都会这样；之前只在 Android 15 真机上验证过，所以误以为是 15 独有问题。

## 修复

```java
// 1) 调用时机：setContentView(root) 之后
setContentView(root);
hideSystemBars();

// 2) 取 controller 的方式：先确保 DecorView 存在，再判空
WindowInsetsController controller =
    getWindow().getDecorView().getWindowInsetsController();
if (controller != null) { ... }
```

`onBackPressed()` 里隐藏输入法的同类调用也一并改成 decor 路径。

## 验证

CI 冒烟工作流（`.github/workflows/android-smoke.yml`）在 API 33 / 35 模拟器上各跑一遍：

| 检查项 | 修复前（API 33） | 修复前（API 35） | 修复后（API 33） | 修复后（API 35） |
| --- | --- | --- | --- | --- |
| 安装 | 成功 | 成功 | 成功 | 成功 |
| `am start` | 启动后立即死 | 启动后立即死 | 存活 | 存活 |
| `pidof` | 空 | 空 | 2725 | 2795 |
| crash buffer | FATAL EXCEPTION | FATAL EXCEPTION | 空 | 空 |
| 截图 | 系统桌面 | 系统桌面 | CintyezOS 锁屏 | CintyezOS 锁屏 |

- 修复前 CI run：https://github.com/Tsimin-ORGANIC/CintyezOS/actions/runs/40057195876
- 修复后 CI run：https://github.com/Tsimin-ORGANIC/CintyezOS/actions/runs/40057550285

## 文件说明

```text
before-fix/api-33/        修复前，Android 13
  crash.txt               crash buffer 原文（含完整堆栈）
  am-start.txt            am start -W 输出
  logcat.txt.gz           完整 logcat（约 3.3 MB 压缩后）
  exit-info.txt           ApplicationExitInfo（进程死亡原因）
  install.txt             安装输出
after-fix/api-33/         修复后，Android 13（同结构 + pid.txt）
after-fix/api-35/         修复后，Android 15（同结构 + pid.txt）
after-fix/screenshot-api-35.png   修复后截图：CintyezOS 锁屏已渲染
```

> 截图上方是系统的全屏提示和模拟器里 Pixel Launcher 的压力 ANR 弹窗，属于模拟器环境噪音，不是本 App 的问题；背景里能正常看到 CintyezOS 的锁屏界面。后续可在冒烟脚本里加 `settings put secure immersive_mode_confirmations confirmed` 去掉提示。

## 复现 / 重新抓日志

1. GitHub → Actions → **Android smoke test** → Run workflow（`main`）。
2. 约 5 分钟，产物在 run 页面的 Artifacts：`smoke-api-33` / `smoke-api-35`。
3. 本地有真机时：`adb logcat -b crash`，或 `adb shell dumpsys activity exit-info os.cintyez.launcher`。

## 教训

- **别在 `onCreate` 里、`setContentView` 之前碰 WindowInsetsController / DecorView。**
- 只在单一系统版本上验证，会把"全版本 bug"误判成"新系统 bug"；CI 多 API 冒烟值得保留。
- 冒烟脚本必须断言"进程存活"，否则崩溃也会显示任务成功。
