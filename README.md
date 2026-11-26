# NetWatch

个人双 SIM 卡流量管理与实时网络监控应用，面向小米 15 Ultra / HyperOS，也可在 Android 12（API 31）及以上的其他设备上运行。

数据只保存在本机，不连接服务器，不收集与流量监控无关的个人信息。

**最重要的原则：数据真实性优先于功能数量。** 系统给不了的数据会在界面里标明“不可用”或“估算”，不会把全设备统计拆成虚假的 SIM 1 / SIM 2 数字。

## 打开、编译与安装

### 在 Android Studio 中打开

1. 安装 Android Studio（建议使用支持 AGP 8.13 的稳定版）。
2. 安装 JDK 17 或 21，以及 Android SDK Platform 37、Build-Tools 36 或 37。
3. 用 **Open** 打开本目录 `NetWatch/`。
4. 复制 `local.properties.example` 为 `local.properties`，把 `sdk.dir` 改成你的 Android SDK 路径。Windows 示例：

   ```
   sdk.dir=C:\Users\你的用户名\AppData\Local\Android\Sdk
   ```

5. 等待 Gradle 同步完成后，选择应用模块，连接手机，点击 Run。也可生成 `app/build/outputs/apk/debug/app-debug.apk` 后手动安装。

调试包的应用 ID 是 `com.netwatch.debug`，正式包是 `com.netwatch`。

### 命令行编译

```bash
# Linux / macOS
export JAVA_HOME=...          # JDK 17 或 21
export ANDROID_SDK_ROOT=...   # SDK 根目录
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Windows 使用 `gradlew.bat`。

本仓库已包含 Gradle Wrapper（Gradle 9.6.0，配合 Android Gradle Plugin 9.5.2）。首次同步会下载依赖，需要网络。

### 安装到小米 15 Ultra

1. 在手机上开启开发者选项与 USB 调试。
2. 用 Android Studio 安装，或 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。
3. 打开应用后按下面的权限说明逐项授予。
4. 如需桌面小组件：长按桌面空白处 → 小组件 → NetWatch 流量概览。

HyperOS 对后台更严格。除了忽略电池优化，建议在系统设置里为 NetWatch 打开自启动、后台活动，并关闭“省电限制”，否则息屏后前台服务可能被杀，归属统计会漏记。

## 需要的权限（以及为什么需要）

| 权限 | 用途 | 不授予时的影响 |
| --- | --- | --- |
| 使用情况访问（`PACKAGE_USAGE_STATS`） | 调用 `NetworkStatsManager` 读取全设备及分应用流量 | 今日/历史系统统计、应用排行不可用 |
| 电话状态（`READ_PHONE_STATE`） | 读取卡槽编号、订阅 ID、运营商名称、默认数据卡 | SIM 识别为“需要权限”；**不会**用来读 IMSI 或手机号 |
| 通知（`POST_NOTIFICATIONS`） | 前台服务常驻通知与预警 | 无法在后台展示监控通知和预警 |
| 前台服务（`FOREGROUND_SERVICE` / `SPECIAL_USE`） | 按设定间隔采样速度并做预警 | 实时监控无法在后台持续运行 |
| 开机广播 | 若已开启后台监控，开机后恢复服务 | 重启后需手动打开应用 |
| 忽略电池优化（可选） | 降低系统杀掉监控服务的概率 | 后台可能中断，分卡归属会少计 |

应用不会申请读取手机号、短信、通讯录或 IMSI 的权限。

## 数据从哪里来：实测 / 估算 / 不可用

界面里会用标签标明每项数据的可信度。

### 实测（来自公开系统 API，未再加工）

- **实时速度**：`TrafficStats` 相邻两次计数的差值。默认数据卡切换时会重置采样基线，避免出现虚假尖峰。
- **全设备今日 / 历史移动或 Wi-Fi 流量**：`NetworkStatsManager.querySummaryForDevice`（需使用情况访问）。
- **应用流量排行**：`NetworkStatsManager.querySummary` 按 UID 汇总（需使用情况访问）。无法解析的应用会显示为“系统或未知应用 (uid:…)”。
- **当前默认数据卡、卡槽、订阅 ID、运营商名称**：`SubscriptionManager` / `SubscriptionInfo`（需电话权限）。卡槽编号和订阅 ID 始终分开显示，不会把订阅 ID 当成卡槽。
- **热点是否开启、共享接口名**：`TetheringManager`。

### 估算（本应用推导，必须看清标签）

- **某张 SIM 卡的“本周期已用”**：Android 公开 API **不能**让第三方应用按订阅查询官方分卡流量（现代系统下 `getSubscriberId()` 对普通应用不可用，`NetworkStatsManager` 查询时 subscriberId 只能传 `null`，得到的是全设备合计）。NetWatch 的做法是：在监控运行期间，把移动数据增量归属到**当时的默认数据卡**。这不是运营商账单，也不是系统按 SIM 出具的官方拆分。
  - 应用被杀掉或未运行的时段不会被计入，因此通常会 **小于** 实际用量。
  - 双卡同时走数据（部分机型的 DSDA）时，归属可能不准。
  - 更换 SIM 后，旧订阅的历史不会合并到新卡。
- **套餐剩余通用流量和占用比例**：用“已用（可能包含定向流量）”去减通用容量，因此剩余是偏保守的估算。
- **热点会话流量**：热点开启期间的移动数据增量，不是运营商“热点专用计数”。笔记本通过热点下载时，手机侧主要表现为移动网络接收（RX）。

### 不可用（公开 API 做不到）

- **运营商官方的分 SIM 账单拆分**。
- **定向流量 / 不计费流量用量**。系统不会把“30 GB 定向”从通用流量里分开。设置里可以填写定向容量，但已用无法拆分。
- **未授予相应权限时的系统统计、运营商名称和订阅列表**。
- **未设置套餐容量时的剩余流量和占用比例**。
- **未设置重置日时的真实账期**。此时按自然月显示，并注明“仅供参考”。

统计页如果只有全设备数据，就只显示全设备数据，**不会**把一个总数拆成 SIM 1 和 SIM 2。

## 首次安装的默认配置

均可在“设置”中修改，不会写死在业务逻辑里。

- 实时监控开启，采样间隔 1000 ms。
- 下载速度预警：6 MB/s 持续 3 秒。
- 滚动窗口 60 秒，窗口内流量达到 300 MB 预警。
- 通知冷却 60 秒。
- 套餐使用率预警默认 80%。
- SIM 1：备用卡，套餐监控开，容量和重置日均为未设置。
- SIM 2：主要卡，套餐监控开，默认通用 180 GB + 定向 30 GB，重置日未设置。

请尽快补上两张卡的重置日，并按实际套餐核对准 SIM 1 的容量。

## 信息结构

底部只有三个一级入口：**首页**、**统计**、**设置**。套餐、实时监控参数、预警阈值都在设置里（或从首页卡片点进去），没有单独的一级页面。

首页卡片：双卡套餐、当前数据卡、实时速度、今日流量（全设备）、热点、最近预警。点套餐卡片进入该卡设置，点速度区域进入实时详情。

桌面小组件展示默认数据卡、实时速度、今日全设备移动数据和该卡套餐估算。

## 技术栈

Kotlin、Jetpack Compose、Material 3、Coroutines / StateFlow、Room、DataStore Preferences、Jetpack Glance、WorkManager、Foreground Service、Navigation Compose、Kotlin Serialization。

- `minSdk` 31，`compileSdk` / `targetSdk` 37
- 仅使用 Android 官方公开 API，不需要 Root，不使用隐藏 API 或反射绕过限制，不修改 HyperOS 系统文件

## 项目结构

业务分层在 `app/src/main/java/com/netwatch/`：

- `core/` 系统封装、数据库、DataStore、权限
- `data/` 仓库与映射，UI 不直接访问数据库
- `domain/` 模型、计算器、用例（预警与套餐计算是纯 Kotlin，便于单测）
- `feature/` 各页面的 ViewModel 与界面
- `service/` 前台监控服务
- `widget/` Glance 桌面组件
- `di/AppContainer.kt` 手动依赖组装

## 已知限制（尤其是 HyperOS）

- 分卡统计只能估算，且依赖应用持续运行。
- 定向流量无法从系统读取。
- 热点流量为估算。
- HyperOS 可能额外限制后台与自启动，需要在系统设置中放行。
- 部分系统应用因包可见性限制，排行里可能只显示 UID。
- 首次安装后，历史系统统计可以按全设备回读（有使用情况访问时），但分卡归属只能从监控开始后累计。

## 许可

Apache License 2.0，见 `LICENSE`。
