# 息间 · ai4summary

专注时收集手机通知，休息时用AI筛选，再通过飞书提醒你查看值得回应的消息。

## 使用

1. 从 [Releases](https://github.com/HITzengyz/ai4summary/releases) 下载APK安装。在「后台运行与保活」开启通知读取，返回确认监听已连接。
2. 进入「设置」，下拉选择 **OpenAI / Anthropic / DeepSeek**，填写对应API Key。每家独立保存密钥、模型和接口地址，点「保存设置」才切换当前提供商。
3. 在飞书群聊 → 设置 → 群机器人中添加自定义机器人，复制Webhook到「飞书机器人地址」。开启云端分析并保存。
4. 点击首页大时间选择番茄时长，开始专注；也可随时休息或立即分析。需要查看的通知会合并成一条飞书摘要。
5. 在通知卡片点「不再收集…」，屏蔽相同正文、某类通知或整个应用。在「通知过滤」中取消规则。
6. 到「后台运行与保活」查看权限和省电状态，按页面说明设置荣耀应用启动管理与最近任务锁定。

「高级设置」可修改当前所选AI的模型和完整HTTPS接口地址，以及共用的筛选偏好和飞书签名密钥。只有机器人开启签名验证时才填写签名密钥。高级配置独立保存，不会切换当前提供商。

支持Android8.0及以上。升级时用相同签名覆盖安装，可保留本地通知、过滤规则和配置。

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

## 开源

[MIT License](LICENSE) · [隐私说明](PRIVACY.md) · [第三方许可](THIRD_PARTY_NOTICES)
