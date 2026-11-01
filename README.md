# 息间 · StillBrief

专注时收集手机通知，休息时用AI筛选，再以本机通知或飞书提醒你查看值得回应的消息。

[English](README.en.md)

## 使用

1. 在 [Actions](https://github.com/HITzengyz/StillBrief/actions) 打开最近成功的 **Android checks**，下载 **StillBrief-debug** 并解压，安装其中APK。在「后台运行与保活」开启通知读取，返回确认监听已连接。
2. 在设置最上方「语言 / Language」选择中文或English，即时生效。新摘要跟随所选语言，通知原文和历史摘要保持原样。
3. 下拉选择 **OpenAI / Anthropic / DeepSeek**，填写对应API Key。各家独立保存密钥、模型和接口地址，点「保存设置」才切换当前提供商。
4. 选择提醒方式：**本机通知**无需Webhook，先到「本机通知与免打扰」授权并发送测试通知；**飞书机器人**需填写群机器人的Webhook。开启「AI分析与提醒」并保存。
5. 点击首页大时间选择番茄时长，开始专注；也可随时休息或立即分析。需要查看的通知会合并成一条摘要提醒。
6. 在通知卡片点「不再收集…」，屏蔽相同正文、某类通知或整个应用。在「通知过滤」中取消规则。
7. 到「后台运行与保活」按顺序开启自启动、关闭本应用电池优化，需要时再给多任务卡片加锁。荣耀电池优化中选“不允许”，表示不允许系统优化本应用。

「高级设置」可修改所选AI的模型和完整HTTPS地址，以及共用的筛选偏好和飞书签名密钥。只有机器人开启签名验证时才填签名密钥；高级保存不切换当前提供商。

如需免打扰时仍收到本机提醒：在系统「免打扰 → 允许打扰 → 应用」中加入息间，或在消息摘要通知类别中允许打扰。飞书关键词验证沿用 `息间提醒`。

支持Android8.0及以上。新安装默认本机通知；升级保留原提醒方式，相同签名覆盖安装可保留记录与配置。

页面切换采用轻柔淡入淡出；关闭系统动画时直接切换。

## 构建

需要JDK17和Android SDK35：

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
# 已连接设备或模拟器时
./gradlew connectedDebugAndroidTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。

[MIT License](LICENSE) · [隐私说明](PRIVACY.md) · [第三方许可](THIRD_PARTY_NOTICES)
