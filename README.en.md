# StillBrief · 息间

Collect phone notifications while you focus, then get a useful AI summary on your device or in Feishu during your break.

[中文](README.md)

## Get started

1. Open the latest successful **Android checks** run in [Actions](https://github.com/HITzengyz/StillBrief/actions), download **StillBrief-debug**, unzip it, and install the APK. Open Settings → Background operation and grant notification access.
2. Choose **English** under **语言 / Language** at the top of Settings. New summaries use the selected language; original notifications and existing summaries stay unchanged.
3. Select **OpenAI, Anthropic, or DeepSeek**, enter its API key, and save. Each provider has its own key, model, and endpoint.
4. Choose a reminder destination: **On-device notification** needs no Webhook; open **Local notifications and Do Not Disturb**, grant permission, and send a test. **Feishu bot** needs the group bot’s Webhook. Enable **AI analysis and reminders** and save.
5. Tap the timer to choose a duration and start focusing, or analyze now. Notifications worth checking are combined into one reminder.
6. Tap **Stop collecting…** on a notification to exclude the same body, a channel, or an app. Manage rules in **Notification filters**.
7. In **Background operation**, allow auto-start and remove battery optimization for this app. Lock the recent-task card if needed. On HONOR, **Don’t allow** under Battery optimization means not allowing the system to optimize this app.

Advanced settings contain the selected provider’s model and full HTTPS endpoint, plus shared preferences and an optional Feishu signing secret. Saving advanced settings does not switch the active provider.

For local reminders during Do Not Disturb, add StillBrief under **Do Not Disturb → Allowed interruptions → Apps**, or allow interruptions for the summary channel. Feishu keyword validation continues to use `息间提醒`.

Requires Android8.0+. New installations default to local notifications; upgrades keep the existing destination. Update with the same signing key to retain records and settings.

Pages switch with a gentle fade; disabling system animations makes transitions immediate.

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
