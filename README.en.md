# StillBrief · 息间

**Let AI read your notifications first. Keep your attention on your work.**

Sometimes we check our phones because something needs a reply. Often, we check because something *might*. One notification turns into another app, and the work loses its place.

StillBrief gives AI the first pass: **your phone keeps receiving messages, AI reviews them at an interval you choose, and you get a summary when something is worth checking.** Nothing to act on? Keep working.

[Download for Android](https://github.com/HITzengyz/StillBrief/releases/latest) · [Quick start](#quick-start) · [中文](README.md)

## Your phone receives. AI filters. You choose when to respond.

Use your phone as the collection point for SMS, calls, email, WeChat, QQ, and notifications from other apps—all through Android notifications.

- **Check your phone less.** Get one useful summary at the end of a focus round. No useful items means no reminder.
- **Keep it on your phone.** Use Do Not Disturb to let only StillBrief's summaries interrupt. Or send summaries to Feishu on your computer.
- **Choose your rhythm.** Use Single for one focus session or Loop for continuous work with regular notification reviews.
- **Bring your own AI.** Choose OpenAI, Anthropic, or DeepSeek.

For example, a 35-minute session might include group chatter, routine system updates, and a direct message asking you to confirm a meeting. The useful reminder is “Zhang San asks you to confirm tomorrow afternoon’s meeting time,” rather than a replay of every notification.

## Quick start

### 1. Install and allow notification access

Download **StillBrief-2.0.apk** from [Releases](https://github.com/HITzengyz/StillBrief/releases/latest) and install it on Android 8.0 or later.

Open **Settings → Background operation**:

1. Grant **notification access**. Return to the home screen and check that the listener is connected.
2. Follow the guide to allow **auto-start** and remove **battery restrictions** for StillBrief.
3. Allow **exact alarms** so the countdown can trigger analysis in the background.

Choose **English** under **语言 / Language** at the top of Settings. The screenshots below show the Chinese interface.

### 2. Connect your AI and choose local reminders

In **Settings**:

1. Select your **AI provider** and enter its **API Key**.
2. Set the reminder destination to **On-device notification**.
3. Enable **AI analysis and reminders**, then **Save settings**.
4. Open **Local notifications and Do Not Disturb**, grant notification permission, and **Send test notification**.

The buttons beside the key let you reveal, copy, or clear it. Start with the default model and endpoint.

<p><img src="docs/images/ai-settings.png" width="300" alt="AI provider, API key and on-device reminder settings"></p>

### 3. Let only StillBrief through Do Not Disturb

From **Local notifications and Do Not Disturb**, open the system's Do Not Disturb settings.

- Turn **Do Not Disturb** on.
- Under **Allowed interruptions → Apps**, add **StillBrief** and keep it as the only app exception.
- Remove call and SMS exceptions too if you do not want them to interrupt directly.
- With Do Not Disturb still on, return to StillBrief and **Send test notification** to check that reminders reach you.

Names vary by phone. You may also find an “Override Do Not Disturb” option in StillBrief's **summary notification channel**.

**Keep notifications enabled in source apps such as your messenger and SMS app.** Messages must still reach the notification tray for StillBrief to collect them; use Do Not Disturb to silence interruptions.

<p><img src="docs/images/local-reminders.png" width="300" alt="Notification permission, test reminder and Do Not Disturb shortcuts"></p>

### 4. Pick a duration and start focusing

On **Focus**, tap the timer and drag the dial. Choose a **15, 25, 35, 45, or 60-minute** preset, or tap the center number for exact input.

Choose a mode, then **Start focus**:

| Mode | When the timer ends | Good for |
| --- | --- | --- |
| Single | Analyze the round, then stop | Reading a paper or finishing one task |
| Loop | Analyze the round and immediately start the next | Working continuously, with a review every 35 minutes |

Put your phone aside. StillBrief will remind you when there is something worth checking.

<p>
  <img src="docs/images/focus.png" width="280" alt="Choose Single or Loop on the focus screen">
  <img src="docs/images/duration.png" width="280" alt="Circular duration selector and minute presets">
</p>

## Make it yours

### Stop collecting a notification you do not need

On **Notifications**, tap **Stop collecting…** under a message. Exclude the same content, that notification channel, or the entire app. For example, exclude a repetitive Wi-Fi status update.

Remove the rule under **Settings → Notification filters** to collect it again.

### Receive summaries on your computer

Choose **Feishu bot**, enter your group bot's **Webhook**, and save. Keep your phone away and check it when a summary arrives on your computer.

Get the Webhook from **Feishu group settings → Group bots → Add custom bot**. If the bot uses signature verification, add its signing secret under StillBrief's **Advanced settings**.

### Take a break or review messages now

- **Pause timer:** pause the countdown while continuing to collect for this round.
- **End focus and analyze:** finish the round early and stop looping.
- **Analyze this round's pending notifications:** review messages already collected while the countdown continues.

Collection runs from the start to the end of each focus round. Previous summaries remain on **Notifications** and do not enter new rounds.

## Development and feedback

Share your experience, examples of missed or unnecessary reminders, and ideas in [Issues](https://github.com/HITzengyz/StillBrief/issues).

<details>
<summary>Build from source</summary>

Use JDK 17 and Android SDK 35:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
# With a device or emulator connected
./gradlew connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. See [release signing](docs/releasing.md) for release builds.

</details>

[Privacy](PRIVACY.en.md) · [MIT License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES)
