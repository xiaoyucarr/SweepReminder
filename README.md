# 扫地提醒 — Minecraft 1.21.11 Fabric

## 功能
- **右上角常驻** 20 分钟循环扫地倒计时，显示 `MM:SS`。
- 精确识别你的服务器提示 `60秒后就要大扫除啦！东西别落了~`，也兼容 `还有60秒清理掉落物`、`60秒后清理掉落物` 后，**立即把倒计时校准成 01:00**。
- 识别 `扫地完成`、`清理完成`、`已清理掉落物` 等后，自动重置为 `20:00`。
- 兼容游戏系统消息、聊天公告和部分 Action Bar 提示。
- 剩余 60 秒显示红色并给出提醒；其他时间显示绿/黄色。
- 按 **R**（可在游戏“选项 → 控制”中改键）可在扫地刚完成时手动同步 `20:00`。
- 纯客户端模组，服务器无需安装。

## 重要说明
- **检测的是服务器发出的文字消息，不是直接读取服务器插件的扫地日程**。
- 首次进服先显示 20 分钟**估算**倒计时；收到真实 60 秒警告或扫地完成公告才会校准。
- 已加入你的服务器公告原文匹配；如果服务器以后修改了公告，再更新匹配规则。
- 真实清理可能因服务器卡顿或插件设置延迟，所以显示的是**预计清理时间**。

## 编译
准备 Java 21、Gradle 8.12+，在项目目录运行：

```bash
gradle build
```

生成 JAR 通常在 `build/libs/sweep-reminder-1.21.11-1.1.0.jar`。
将 JAR 放入 Minecraft 1.21.11 的 `mods` 文件夹，还需要 Fabric Loader 与 Fabric API（1.21.11）。

此 ZIP 为**源代码项目**；未在本环境编译验证。

## Build JAR using GitHub Actions (no local Gradle installation)

1. Create a GitHub repository and upload **the files and hidden `.github` directory** from this project, keeping their relative paths. To preserve the hidden directory, use Git on your computer (`git add .`, commit, push).
2. In your repository open **Actions > Build installable Fabric JAR > Run workflow** (or push to the `main` branch).
3. After the workflow succeeds, open the run's **Artifacts** and download `SweepReminder-Fabric-1.21.11-JAR`.
4. Extract the downloaded artifact ZIP; use `sweep-reminder-1.21.11-1.1.0.jar` (not `-sources.jar`) in `.minecraft/mods` alongside the appropriate Fabric API.

This GitHub workflow has not been executed or verified from this environment.
