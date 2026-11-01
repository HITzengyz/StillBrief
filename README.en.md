# StillBrief · 息间

Collect phone notifications while you focus, then get a useful AI summary on your device or in Feishu during your break.

[中文](README.md)

## Get started

1. Download and install **StillBrief-2.0.apk** from [Releases](https://github.com/HITzengyz/StillBrief/releases/latest). Open Settings → Background operation and grant notification access.
2. Choose **English** under **语言 / Language** at the top of Settings. New summaries use the selected language; original notifications and existing summaries stay unchanged.
3. Select **OpenAI, Anthropic, or DeepSeek**, enter its API key, and save. Each provider has its own key, model, and endpoint. Use the eye, copy, and clear buttons beside the key; save after clearing.
4. Choose a reminder destination: **On-device notification** needs no Webhook; open **Local notifications and Do Not Disturb**, grant permission, and send a test. **Feishu bot** needs the group bot’s Webhook. Enable **AI analysis and reminders** and save.
5. Tap the timer and drag the duration dial (tap its number for exact input), choose **Single** or **Loop**, and start. Single analyzes the round and stops; Loop analyzes each round and immediately starts the next countdown. Only useful summaries are sent.
6. Tap **Stop collecting…** on a notification to exclude the same body, a channel, or an app. Manage rules in **Notification filters**.
7. In **Background operation**, allow auto-start and remove battery optimization for this app. Lock the recent-task card if needed. On HONOR, **Don’t allow** under Battery optimization means not allowing the system to optimize this app.

Advanced settings contain the selected provider’s model and full HTTPS endpoint, plus shared preferences and an optional Feishu signing secret. Saving advanced settings does not switch the active provider.

For local reminders during Do Not Disturb, add StillBrief under **Do Not Disturb → Allowed interruptions → Apps**, or allow interruptions for the summary channel. If your Feishu bot used `息间提醒` as a keyword, switch to signature verification and remove that keyword restriction.

Requires Android8.0+. New installations default to local notifications; upgrades keep the existing destination. Update with the same signing key to retain records and settings.

Only notifications received from the start to the end of the current round are collected. Idle notifications are excluded. Pause stops the countdown but continues collecting for the same round. Ending manually stops looping and analyzes the current round; Analyze now processes only its remaining notifications. History stays available.

The loop interval is fixed when started. Changing the default duration applies to the next manual start. Summaries preserve visible contacts, full numbers, and verification codes.

Upgrading from 1.x preserves settings, filters, and historical summaries. The old unassigned cache and active timer are reset; start a new focus round.

## Build

Use JDK17 and Android SDK35:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # with a device or emulator
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Build the release variant with `./gradlew assembleRelease`; see [release signing](docs/releasing.md).

[MIT License](LICENSE) · [Privacy](PRIVACY.en.md) · [Third-party notices](THIRD_PARTY_NOTICES)
