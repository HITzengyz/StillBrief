# ai4summary · 息间

一个本机直连 DeepSeek 与飞书机器人的 Android 应用：全时段收集系统通知，在番茄钟结束或手动休息时筛选，并只发送一条值得查看的消息摘要。Android 8.0（API 26）及以上；无后端、无 Firebase、无研究数据上传。

已完成 18 项单元测试与 18 项 Android 15 模拟器设备测试；真实 DeepSeek/飞书和荣耀熄屏链路待配置后验收，见[验证记录](docs/validation.md)。

## 开始使用

1. 安装 APK，进入「专注 → 授权通知读取」，在系统设置中允许「息间」。回到应用，确认「监听：已连接」。授权后才开始收集新通知，不会补回授权前或断连期间已经消失的通知。
2. 在「设置」填写 DeepSeek API Key、完整 HTTPS Chat Completions 地址（默认 `https://api.deepseek.com/chat/completions`）、模型（默认 `deepseek-flash`）、飞书/Lark 自定义机器人 Webhook，可选填写签名密钥。机器人需允许文本消息；如启用关键词验证，配置与摘要一致的关键词，例如 `息间提醒`。
3. 阅读说明后开启「云端分析与飞书提醒」并保存。开启会将本批通知正文和自定义筛选偏好直接发送到指定兼容接口；飞书只接收筛选后的摘要。本应用没有开发者服务器。
4. 选择番茄时长（1—180分钟），开始专注。可暂停/继续计时，也可「现在休息」结束计时并分析，或保持计时「立即分析当前缓存」。只有筛选结果非空才发送飞书。
5. 到「通知」查看近期原文、每批完整记录、结果及失败原因。失败必须手动重试；「送达不确定」须先检查飞书，再决定是否重发，重发可能重复。

飞书和 QQ/微信/邮件/短信/来电等通知均默认采集，只默认排除本应用。可排除已采集应用，也可直接填写任意应用包名。飞书/Lark 中带 `【息间提醒】` 的机器人行会被识别并去除；机器人摘要发送为带该前缀的单行，减少循环。第三方客户端如果删改此前缀或只展示无标记的汇总，仍可能无法识别，可手动排除对应客户端。不要给真人消息使用此前缀。

## 采集、批次与筛选

- `NotificationListenerService.onNotificationPosted` 到达时立即写 SQLite，清掉系统通知也不会删除已记录历史。相同 key、发布时间和正文的重复回调去重；新发布时间或新内容均追加保存；系统移除后清除该 key 的去重指纹，之后真实重发仍会记录。分组汇总和持续通知不会直接丢弃。
- 全时段采集；暂停只暂停番茄计时。每批以数据库事务固定当前所有未归批通知；分析、休息期间的新通知留待下一批。未处理的失败批次会阻止新批次推进，避免越过失败数据；新通知继续本地缓存。
- 同一 key 的后续正文若包含此前完整正文，在分析输入中合并为最新记录；新内容与替换更新均保留在完整本地记录。不同 key 不会仅因正文相同而合并。
- 使用 MessagingStyle 群聊元数据和系统 category 分类。明确私聊、未接来电必保留；隐藏的明确通信通知保守保留。QQ/微信等已知通信包的隐藏正文会作为通信处理，未知应用不会直接当私聊。安卓26/27元数据不足时不武断判私聊。
- 模型输出必须为完整 JSON、整数且存在的 `selected_ids`、一致的非空/空摘要；未知 ID、重复 ID、截断、空白、坏格式均失败。模型漏选硬保留项时，使用本地事实摘要补回。通知正文作为不可信数据，不执行其中指令。
- 大批输入按每段不超过80条、约50000个 JSON 字符分段分析，全部成功后合并选择，只发一条总提醒。单条超长正文在云端输入中限制为前32000字符，明确 `truncated=true` 并强制保留该条；本机原文完整保存。多段总提醒包含总条数、涉及应用和部分摘要，完整原文可在本批记录查看。任一段失败不会发送部分结果，重试会重新分析本批，可能再次产生 API 费用。

## 可靠性与计时

批次持久状态为 `QUEUED → ANALYZING → READY → SENDING → DONE/EMPTY`；分析失败为 `FAILED`，明确发送失败为 `SEND_FAILED`，超时、5xx、异常响应或发送进程中断为 `UNCERTAIN`。HTTP错误及 HTTP200 非零飞书业务码都不算送达；发送失败/不确定时保留原摘要，只在用户确认后重发。进程启动时恢复中断状态，已成功批次不会自动重发。

批次快照与状态变更在 SQLite 事务内，状态条件更新避免重复认领。WorkManager 使用唯一批次任务；同时点击和计时触发不会创建重复批次。网络调用和保存成功之间无法获得飞书的事务保证，所以无法承诺严格 exactly-once；已明确暴露不确定状态并禁止静默自动重发。

番茄使用 AlarmManager，另有 WorkManager 兜底，无长时间 `dataSync` 前台服务。未获准时闹钟权限时主页明确提示后台/熄屏可能延迟，可前往系统设置开启。进程重启、设备重启会恢复计时（按系统时间，因此手动改时间会影响倒计时）；旧闹钟携带 session token，到期时间与 token 均匹配才结束。用户强行停止应用后，安卓通常不会继续唤醒，重新打开应用后恢复。计时结束不播放声音、不弹本机提醒，飞书是休息时的输出通道。

## 数据与权限

申请通知读取、网络、闹钟和开机广播；不申请短信、通话记录、无障碍、通讯录或全应用查询权限。只收集系统实际交给监听服务的通知，不能读取应用内未发成系统通知的聊天记录，不能还原被隐藏的原文。电话/未接来电取决于手机拨号应用是否发布了对应通知及可读分类。荣耀的锁屏保护、省电、冻结策略可能影响采集或唤醒，需真实机验收。

通知原文存应用私有 SQLite，不记录正文/密钥到日志。API Key、Webhook、签名密钥使用 Android Keystore AES/GCM 加密，禁用云备份与设备迁移；通知数据库使用系统应用隔离，未另加数据库级加密。应用内可永久删除通知与批次；单独删除密钥会关闭云端分析。已完成/无需提醒记录保留30天，在应用进程启动时清理；待处理、失败和送达不确定的记录不自动删除。卸载/系统清除数据会删除全部本地记录和凭证。

## 本地构建与测试

依赖：JDK17、Android SDK Platform35、Build Tools34.0.0（AGP默认）、AGP8.7.3、Gradle8.9、Kotlin2.0.21；原生 Views，SQLiteOpenHelper，WorkManager2.10.0。没有真实凭证或预配置机器人。

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
# 设备或模拟器已连接时，运行真实SQLite事务测试
./gradlew connectedDebugAndroidTest
```

APK位于 `app/build/outputs/apk/debug/app-debug.apk`。CI执行单元测试、lint、debug APK 构建并保存构建产物，不发布到应用商店。单元测试覆盖硬保留、空结果、无效ID/空白/截断、合并、分段、飞书业务码；设备测试覆盖SQLite去重/重发、并发批次/认领、分析中新通知、发送中断恢复/手动重试、过期闹钟。

## 荣耀真机验收清单

- 系统通知读取授权显示连接；允许后台活动、自启动；按需移除电池优化与后台冻结限制。先前台验证，再锁屏验证，勿仅以模拟器通过代替。
- 设1分钟：QQ/微信私聊、群聊、短信、验证码、邮件待办、广告、未接来电、持续来电、仅有群汇总的应用分别发通知，核对本机逐条记录和筛选结果。锁屏隐藏正文时确认保守保留，不把未知广告应用当私聊。
- 连续更新同一通知、移除后同内容重发：前者去重且新内容不丢，后者重新记录。飞书真人消息会采集，息间机器人自身提醒不回流。
- 暂停时发消息、分析中发消息、休息时发消息：持续入库，进入下一批；并发点击分析、到时与手动同时触发只处理同一批。
- 锁屏/熄屏15分钟以上、进程被系统回收、设备重启，确认倒计时恢复与通知采集；关闭准时闹钟权限，确认降级提示和延迟实际表现。
- 错误API Key、断网、飞书签名错误/关键词不符：显示失败且不标成功；恢复后手动重试。发送中断网/杀进程：显示送达不确定，不自动重发，确认可能重复提示。
- 全无关通知应为无需提醒，飞书不发；无通知不建空批。超过500条通知应分段且最后只发一条；超长单条本机完整保留，云端截取标记且保守保留。
- 删除通知、删除凭证、卸载重装，验证数据/授权边界；勿把真实通知原文、API Key、Webhook写入issue或截图。

## 开源许可

本项目代码为 MIT License，Copyright (c) 2026 HITzengyz。参考了 NotiSummary 的通知监听入口设计，并使用其 Gradle wrapper JAR；未使用 Firebase、用户研究采集或云端上传实现。参考项目版权与 MIT 全文保留在 `THIRD_PARTY_NOTICES`。Gradle wrapper 由 Gradle 项目提供，适用 Apache License 2.0，见其上游许可。

## 参考资料

- [NotiSummary 参考项目](https://github.com/noti-summary/NotiSummary)，参考提交 `dccd78793e7277ca410dfc0cd89b3c8e4667e6fe`。
- [Android 通知监听接口](https://developer.android.com/reference/android/service/notification/NotificationListenerService)与[后台闹钟](https://developer.android.com/develop/background-work/services/alarms)。
- [DeepSeek JSON 输出](https://api-docs.deepseek.com/guides/json_mode/)与[模型名称](https://api-docs.deepseek.com/quick_start/pricing/)。
- [飞书自定义机器人](https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot)。
- [Android 15 敏感通知限制](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction)：系统可能遮盖验证码；本应用不会绕过该限制。
