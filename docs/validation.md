# 1.2 审查与验证

验证日期：2026-11-01。GPT-6.1 Sol 实现，主代理审查和验收。

- 构建成功；31项单元测试、32项Android15 ARM64设备测试全部通过；lint 0错误、22项提示。
- 真实覆盖安装1.1→1.2成功。旧DeepSeek加密密钥原样迁移，原机器人签名保留，两者在界面上可正常读取。
- 下拉选择DeepSeek、OpenAI、Anthropic；首次切到另两家密钥为空。OpenAI输入切走再返回仍保留草稿，保存并重新进入后仍存在；切回DeepSeek显示原密钥。未保存的下拉选择不改变当前使用的提供商。
- OpenAI默认模型和完整接口正确显示；共用的机器人签名不因提供商切换而丢失。
- 13项新单测覆盖三家请求参数/认证头、成功及空结果、拒绝/截断/坏格式、Anthropic文本块与思考块、完整JSON代码块；4项新设备测试覆盖旧配置迁移、配置隔离、固定整批配置及清除全部密钥。
- README改为简短使用说明，隐私内容独立到PRIVACY.md。

本轮验证使用虚构密钥和响应样本，没有向真实模型或飞书发送数据；各账号的实际模型权限、额度与网络可用性未实测。

适配依据：
- [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)：Bearer认证、JSON模式、max_completion_tokens。
- [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna)：默认模型，使用reasoning_effort=none；自定义其他模型不附带该参数。
- [Anthropic Messages](https://platform.claude.com/docs/en/api/http/messages/create)及[版本迁移说明](https://platform.claude.com/docs/en/api/migration-2026-10-21)：Bearer认证、anthropic-version=2026-10-21、顶层system、文本块与end_turn，默认模型claude-haiku-5-5。
- [DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)：保留deepseek-flash、JSON模式和关闭思考的请求参数。

---

# 1.1 审查与验证

验证日期：2026-11-01。GPT-6.1 Sol 实现，主代理审查、精简界面说明并执行验证。

| 检查 | 实际结果 |
|---|---|
| 单元测试 | 18/18 通过 |
| Android 15 ARM64 设备测试 | 28/28 通过，含16项数据库测试、11项解析测试、1项加密设置测试 |
| Debug 构建与 lint | 构建成功；lint 0错误、17项提示（同步保存、依赖升级建议、文本国际化） |
| 同签名覆盖升级 | 1.0→1.1安装成功，数据库v1→v2；4条虚构旧通知及批次保留，旧设置文件原样保留，旧加密API Key和签名密钥可解密 |
| 首页时长 | 点击大时间设35分钟；运行中调整剩余15分钟，暂停后调整25分钟，数据库保持PAUSED、remaining=1500000 |
| 设置精简 | 默认仅API Key、机器人地址和开关；基础保存后展开高级，旧签名密钥仍正常显示 |
| 通知过滤 | 从旧FAILED批次的单条原文添加相同正文规则，显示2条→1条；取消规则后恢复2条；独立管理入口可见 |
| 后台设置 | 页面显示真实授权、监听、闹钟和电池状态；电池优化按钮在Android15模拟器打开系统App battery usage页面 |
| APK签名 | apksigner验证通过，证书SHA-256与1.0一致 |
| 公开前检查 | 修改文件未发现真实API Key、Webhook或私钥；本地凭证与构建文件仍被忽略 |

新增设备回归覆盖：持续状态按内容去重且私聊/来电/邮件新事件保留；相同正文规则不受标题变化影响；应用/类别隔离；取消规则恢复采集；FAILED批次按新规则重新分析；分析已开始时保持原内容；旧v1数据库和旧排除应用迁移；修改剩余时间保持状态并拒绝旧计时事件；基础保存保留高级加密配置。

本轮未对真实DeepSeek/飞书发送数据，也未在荣耀真机验证熄屏、自启动、锁卡或最近任务隐藏。页面提供真实可读状态、标准系统入口及[荣耀官方操作指引](https://www.honor.com/cn/support/content/zh-cn00685514/)，不宣称厂商后台设置已经开启。最近任务隐藏仅改变卡片显示，不能代表保活成功，参见[Android AppTask文档](https://developer.android.com/reference/android/app/ActivityManager.AppTask#setExcludeFromRecents(boolean))。

交付包仍为个人试用debug包，覆盖升级请使用本轮与1.0同签名的APK，不要先卸载；其他电脑或GitHub CI构建的debug包可能使用不同签名。

---

# 首版审查与验证

验证日期：2026-11-01。实现由 GPT-6.1 Sol 完成，主代理审查、修复并执行以下验证。

## 已完成

| 检查 | 实际结果 |
|---|---|
| Debug APK 构建 | 成功，Android 8.0+，版本 1.0 |
| 单元测试 | 18/18 通过，0 失败、0 跳过 |
| Android 15 ARM64 模拟器测试 | 18/18 通过，含7项数据库测试、11项通知解析测试 |
| Android lint | 0 错误、15 项提示，涉及同步保存设置、可选依赖升级与界面文本国际化 |
| APK 签名 | v2 签名验证通过，已在模拟器安装启动 |
| 真实系统通知入口 | 模拟器发出两条中文虚构通知；标题和正文均进入本地 SQLite，并在通知页显示 |
| 专注交互 | 点击开始后持久化 RUNNING；点击现在休息后变为 IDLE，并建立待处理批次 |
| 配置缺失 | 云端未开启时明确记录 FAILED，不显示为空结果或送达成功 |
| 界面 | 实际检查中文主页、通知页、设置页；修正按钮层次与导航样式 |
| 公开前扫描 | 源码未发现实际 API Key、飞书 Webhook、私钥；构建产物、密钥与本地配置被忽略 |

## 审查中修复

- 私聊明确标记优先于会话标题；持续来电、分组汇总、媒体消息不会直接丢弃。
- 通知去重包含来源发布时间，避免上一批已处理后同内容真实重发被吞掉。
- 移除系统通知仅清除去重状态，不删本地历史。
- 批次快照与认领持久化；新到通知留待下一批，成功批次不重发。
- 旧计时事件携带会话标识，必须已到期才可结束新计时；前台倒计时到零可直接触发。
- 飞书业务码严格校验；发送超时、5xx及中断标记为送达不确定，手动重试明确提示可能重复。
- 超量通知分段分析后只发一条；超长单条输入显式截取并保守保留，本地原文完整保留。
- Android 12+ 云备份和设备迁移均排除本地通知与凭证；API Key等由Keystore加密。
- 数据库设备测试使用独立库，避免删除正式通知库。

## 尚未实测

未配置真实 DeepSeek API Key 和飞书 Webhook，因此未向真实模型发送聊天内容，也未向真实飞书群发消息。模型实际筛选效果、真实机器人送达、荣耀手机后台/锁屏/重启表现，需按 README 真机清单验收。本次设备测试不等同于上述链路已验证。

APK 为个人试用的 debug 签名包，不是应用商店发布包。不同电脑或 CI 的 debug 签名可能不同，安装更新时需保持相同签名；卸载会删除本地记录。
