# NetHack 5 — ChatGPT Guide (Android)

Android adaptation of [JodiJodington/NetHack-Android](https://github.com/JodiJodington/NetHack-Android) with contextual AI help.

**This repository tracks a small overlay rather than copying the enormous NetHack source tree.** GitHub Actions checks out a pinned upstream source, overlays the Java guide Activity, and builds the native game and APK. The original game rules are unchanged.

## Planned controls

* **Quick Help** – capture the *visible* dungeon, status, and recent messages, then request a beginner-friendly prioritized survival/progression guide.
* **Ask ChatGPT** – type a question; the current visible state is supplied automatically, and the assistant explains specific moves and commands.
* **AI configuration** – paste an OpenAI API key the first time. It is encrypted on device with Android Keystore. No API key is bundled with the APK or added to this repository.

Calls use the [OpenAI Responses API](https://platform.openai.com/docs/api-reference/responses). An OpenAI API key is separate from a ChatGPT subscription, may incur charges, and requires internet connectivity. The game itself remains offline.

## Limitations

In the first iteration the assistant receives only what a human could see in the current game screen (image, displayed status, current message), not private dungeon secrets or full inventory. It can be wrong; always verify before executing a dangerous command. Input prompts and state are sent only when help is requested. The guide never controls the character.

## Build

Workflow: `.github/workflows/build.yml`. Builds NetHack from a pinned upstream commit on Ubuntu with Android SDK/NDK; outputs an APK artifact. Uses a unique application ID so it can coexist with the original port. For **stable updateable release signing**, configure repository Actions secrets `NETHACK_KEYSTORE_BASE64`, `NETHACK_KEYSTORE_PASSWORD`, and `NETHACK_KEY_ALIAS` / `NETHACK_KEY_PASSWORD`. Without these, the workflow outputs a CI debug build (which may be signed differently between runners).

Upstream: original NetHack game © NetHack DevTeam and contributors; port/frontend licenses remain applicable.
