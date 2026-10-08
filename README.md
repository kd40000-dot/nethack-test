# NetHack 5 + ChatGPT Web Guide

An Android overlay for [JodiJodington/NetHack-Android](https://github.com/JodiJodington/NetHack-Android), based on NetHack 5.0. It adds beginner-friendly game-state help using the **ChatGPT website in Fennec**, rather than the paid OpenAI API.

> This is a hobby/test build, not affiliated with OpenAI or NetHack's maintainers. Physical-device testing is required.

## Touch-first HUD (preview)

The v5002 prototype introduces a Shattered Pixel Dungeon-inspired dark-stone HUD (visual inspiration only, not copied game art). The top area displays HP, dungeon depth, status ailments, and always-visible shortcuts to the **current weapon, armor, rings, and amulet** reports from NetHack. The bottom bar has six touch actions:

- **Bag:** open the existing NetHack inventory with larger icon rows and status subtitles.
- **Gear:** inspect wielded/worn equipment, wield, wear, remove or swap gear.
- **Act:** pick up, look, apply, search, open/close, eat, drink, read, drop and wait.
- **Explore:** stairs, travel, examine, search and wait.
- **Magic:** fire, throw, zap, cast, read, wield and kick.
- **More:** ChatGPT quick help and questions in Fennec, virtual keyboard for uncommon commands, settings, help, extra commands and Cancel.

A small contextual action above the bar uses current HUD status and already-discovered map tiles to suggest an action (e.g. hunger, low HP, doors or items). Suggestions are deliberately conservative and do not perform actions automatically. NetHack controls item selection and directions after you tap a command. Tapping the dungeon still moves the character. The previous many-button command grid is hidden.

**Current limitations:** Equipment is not yet rendered as a persistent full character paper-doll; tapping a slot queries the actual NetHack equip report. The inventory still uses NetHack's native item menus with a new theme; it is not yet a custom draggable equipment grid. Contextual suggestions can be wrong if an ambiguous tile glyph obscures the real terrain. More advanced commands remain in the keyboard fallback.

**APK signing reminder:** The previously published debug-signed APKs have ephemeral signatures. Until you configure the permanent signing key, newly built debug APKs cannot replace the existing installation through a normal Android update. Use the documented backup/migration process only after verifying the full backup.

## In-game controls

- **✦ Quick help:** read current visible NetHack state and create a proactive survival/progression question.
- **Ask ChatGPT:** ask a specific question; the app includes your current discovered 80x21 dungeon view, location, displayed status and recent messages.
- Both buttons **copy the full prompt to the clipboard**, then **launch Fennec** at chatgpt.com with a prefilled question when the encoded URL is short enough. When the URL is too long, or the prefill feature fails, **paste your clipboard into ChatGPT and tap Send**.
- ChatGPT Plus works when **you are already logged in to chatgpt.com in Fennec**. No API key, API credits, ChatGPT Android app, special cookies, embedded WebView login, or automated sending are involved. The prompt is sent only when *you* tap Send in the browser.

**Limitations:** This browser route passes **structured text** (the rendered/discovered map, messages and HUD), **not screenshots or hidden game data**. ChatGPT's question URL composer prefill is not a guaranteed/stable official integration. Sometimes you must paste manually. NetHack can be terminated by Android while backgrounded; save your game regularly and set background battery usage to unrestricted.

The game engine does not run an AI model locally and does not automate any actions.

## How the build works

This repository contains only the Android guide overlay and scripts, not a copy of the upstream NetHack source tree. .github/workflows/build.yml fetches pinned upstream NetHack sources, overlays the Android Java Activity, compiles for arm64, and uploads an APK artifact. .github/workflows/publish.yml publishes a directly downloadable APK when the build passes.

Package ID stays **com.tbd.nethack5.guide** (same as the first preview), currently versionCode **5001**.

### IMPORTANT: first preview APK's private signing key is unavailable

The **first** app-arm64-v8a-debug.apk (versionCode 5000) was built on a disposable GitHub Actions runner, which automatically generated a per-run debug.keystore. Neither the keystore nor its private key was archived.

This means **there is no way to create a normal in-place update using that exact signing certificate**. Android requires the signing identity to match, regardless of the APK's package name or whether the phone is rooted. Do **not** uninstall the old APK before backing up your saves.

### Permanent signing for all future updates

Use your own signing keystore stored securely on your device and in GitHub Actions repository secrets. Termux steps:

1. Install dependencies: pkg install gh openjdk-21 coreutils
2. Run: gh auth login (log in to the GitHub account that owns this repository).
3. Download and review [scripts/setup-permanent-signing.sh](scripts/setup-permanent-signing.sh).
4. Run bash setup-permanent-signing.sh in Termux. It generates a private .p12 keystore **locally**, configures all four Actions secrets (NETHACK_KEYSTORE_BASE64, NETHACK_KEYSTORE_PASSWORD, NETHACK_KEY_ALIAS, NETHACK_KEY_PASSWORD) without checking a key into Git, and triggers a signed build.
5. Back up the **keystore file and password offline**. Don't disclose either or commit them to public GitHub.

Once the stable signing certificate is configured, future releases built from that keystore can install **in place** over one another, with app data/settings preserved.

### Root-assisted migration from the previous debug-signed APK

**Save and quit the current game first.**

1. Download and inspect [scripts/nethack-root-migrate.sh](scripts/nethack-root-migrate.sh). Place it at /sdcard/Download/nethack-root-migrate.sh.
2. Run this in Termux: su -c 'sh /sdcard/Download/nethack-root-migrate.sh backup'. This only reads and backs up; it **does not uninstall** anything. Check that it reports a verified backup under /sdcard/Download/NetHackGuide-migration/.
3. Download the new **permanently signed** arm64 release APK into Download. Run: su -c 'sh /sdcard/Download/nethack-root-migrate.sh restore /sdcard/Download/NEW-APK-FILENAME.apk' (replacing the exact filename). This last step **must uninstall and reinstall** the app internally to change the signature, but restores its private files/settings afterward.
4. Verify NetHack runs with your saves and preferences. Keep the archived old APK and data tarball as a rollback point.

The script attempts to reinstall the preserved original signed APK if the new APK fails to install. Root-assisted data restore is experimental; export/save your game independently before running it. Android Keystore-encrypted API keys from the old version are intentionally obsolete and may no longer be recoverable after uninstall.

**DO NOT** install subsequent debug-signed previews as long-term updates: GitHub Actions' generated debug signing key changes between runners. Only the configured release key is stable.

## Privacy

The app copies game-state text to Android clipboard and passes it through a URL to ChatGPT when that URL is short enough. On Android, clipboard contents may be visible to your keyboard or other apps with clipboard privileges. The URL may be recorded in your browser history, so don't include secrets or personal information in Ask ChatGPT questions. Browser login credentials stay in Fennec; this project does not read them.

Original NetHack source copyright and licenses belong to the NetHack DevTeam and contributors. Upstream frontend licenses continue to apply.
