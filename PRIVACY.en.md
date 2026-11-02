# Privacy ([中文](PRIVACY.md))

**StillBrief has no backend servers. We do not collect, track, or receive any of your personal data.** The entire codebase is open-source and verifiable.

Here is where your data goes when using the app:

1. **On your device:**
   Collected notification logs and your API keys are stored locally on your phone.
2. **When AI analysis runs:**
   With AI analysis enabled, a round ending, ending focus early, a manual analysis, or retrying a failed analysis sends pending notification titles, text, source apps, timestamps and related metadata, along with your custom filtering rules, directly from your phone to the AI provider you configured (e.g., OpenAI, Anthropic, DeepSeek, or your custom endpoint).
3. **Where summaries go:**
   - **On-device notifications:** The summary is displayed locally as a standard Android system notification.
   - **Feishu bot:** The summary is sent via Webhook to your configured Feishu group. **Keep in mind that anyone in that Feishu group will be able to read the summary**, so make sure you point the bot to a private, solo group.

4. **Instant verification codes:**
   When enabled with Feishu bot selected, verification codes and their notification source and text are sent directly to your configured Feishu group, without calling an AI provider. This also works outside focus sessions.

**A quick heads-up:** Always use a trusted AI provider, and double-check the URL if you are routing requests through a custom or third-party proxy endpoint.
