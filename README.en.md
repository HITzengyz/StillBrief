# StillBrief · 息间

Collect phone notifications while you focus, then get a useful AI summary in Feishu during your break.

[中文](README.md)

## Get started

1. Install the APK from [Releases](https://github.com/HITzengyz/StillBrief/releases). Open Settings → Background operation and grant notification access.
2. Choose **English** under **语言 / Language** at the top of Settings. New summaries use the selected language; original notifications and existing summaries stay unchanged.
3. Select **OpenAI, Anthropic, or DeepSeek**, enter its API key, and save. Each provider has its own key, model, and endpoint.
4. Add a custom bot in your Feishu group’s settings and copy its Webhook URL into Settings. Enable cloud analysis and save.
5. Tap the timer to choose a duration and start focusing, or analyze now. Notifications worth checking are combined into one Feishu reminder.
6. Tap **Stop collecting…** on a notification to exclude the same body, a channel, or an app. Manage rules in **Notification filters**.
7. Use **Background operation** to review permissions and follow your phone’s background-launch instructions.

Advanced settings contain the selected provider’s model and full HTTPS endpoint, plus shared preferences and an optional Feishu signing secret. Saving advanced settings does not switch the active provider.

Feishu keyword validation continues to use `息间提醒`; English summaries keep this reminder prefix.

Requires Android8.0+. Update with the same signing key to retain local records and settings.

## Build

Use JDK17 and Android SDK35:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # with a device or emulator
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

[MIT License](LICENSE) · [Privacy](PRIVACY.en.md) · [Third-party notices](THIRD_PARTY_NOTICES)
