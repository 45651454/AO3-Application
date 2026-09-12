# AO3 Application

非官方 AO3（Archive of Our Own）第三方 Android 阅读器。Kotlin + Jetpack Compose 编写，抓取 AO3 页面解析后用原生界面呈现，专注一件事：**把你爱看的文存到本地，随时离线读**。

> 本项目与 OTW（Organization for Transformative Works）及 AO3 官方无任何关联，是非商业的个人项目。

<!-- 截图占位：上传后取消注释
<p align="center">
  <img src="docs/screenshots/browse.png" width="24%" />
  <img src="docs/screenshots/detail.png" width="24%" />
  <img src="docs/screenshots/reader.png" width="24%" />
  <img src="docs/screenshots/library.png" width="24%" />
</p>
-->

## 功能

**浏览**
- 关键词搜索作品（标题、作者、摘要）
- 排序方式可选，「仅完结」筛选
- 点击标签进入标签浏览页，分页加载下一页

**作品详情**
- 完整标签、分级、警告、字数与章节数等统计
- 简介 + 章节列表，直接进入阅读

**阅读**
- 全文阅读，自动记录阅读进度
- 「继续阅读」回到上次位置

**书库**（全部本地存储，无需登录）
- **收藏** — 收藏作品，可打标签并按标签筛选
- **历史** — 阅读历史与进度
- **标签** — 收藏常用标签，一键回到该标签的浏览页
- **已下载** — 整篇下载后离线阅读，可随时删除

**设置**
- 主题：跟随系统 / 浅色 / 深色
- 数据导出与导入：收藏、历史、标签收藏导出为 JSON，换机时导入

## 设计取舍

- **不做登录。** 不接触你的 AO3 账号与密码，收藏、历史、阅读进度全部只存在设备本地。
- **1 请求/秒限速。** 所有网络请求强制间隔 1 秒，避免给对方服务器造成压力。
- **解析 HTML 而非调用 API。** AO3 没有公开 API，本项目用 Jsoup 解析页面，因此 AO3 改版可能导致解析失效。

## 构建

需要 JDK 11+ 与 Android SDK。

```bash
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。`local.properties` 需指向你的 Android SDK 路径，Android Studio 首次打开项目时通常会自动生成。

## 技术栈

| | |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 |
| 网络 | OkHttp 4.12 + Jsoup 1.18（HTML 解析） |
| 序列化 | kotlinx.serialization JSON |
| 存储 | SQLite（书库数据）+ SharedPreferences（设置） |
| 最低版本 | Android 7.0（API 24） |
| 目标版本 | API 36 |

## 免责声明

本项目为非官方粉丝作品，与 OTW / AO3 无关联，未获其授权或认可。作品内容版权归各自原作者所有。使用本应用时请遵守 [AO3 服务条款](https://archiveofourown.org/tos)。

---

## English

An unofficial AO3 (Archive of Our Own) client for Android, written in Kotlin with Jetpack Compose. Browse and search works, read with automatic progress tracking, keep a local library of favorites, history and tags, and download works for offline reading. No account required — everything stays on your device. Network requests are rate-limited to 1 per second out of respect for AO3's servers.

Not affiliated with, endorsed by, or connected to the OTW or AO3. Licensed under the [MIT License](LICENSE).
