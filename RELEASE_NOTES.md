# LidSleepX 0.1.0 (internal test)

This is the first internal test build.
It is not a public release.

## Requirements

- macOS 12 or later.
- An Apple Silicon Mac (arm64).
- JDK 25 is not required to run the app.

## What is in this build

- Menu-bar app with no Dock icon.
- Battery sleep rules: capacity threshold and time-remaining threshold.
- AC rules: prevent idle sleep and lid sleep on AC power.
- Lid close sleep, or a lid-close action that only turns the display off.
- Timed cancel for the prevention rules.
- Sleep now, and display sleep now.
- Start at login.
- Notifications and a rotating log.
- GitHub Releases update check for stable versions.
- English and Simplified Chinese text.

## Privileged helper

The app needs a privileged helper to change `pmset disablesleep`.
The app installs the helper only after you approve the install dialog.
The helper accepts only `version` and `setDisableSleep`.

## Install

1. Open `LidSleepX-0.1.0.dmg`.
2. Drag `LidSleepX.app` to `Applications`.
3. Open `LidSleepX.app`.

**Warning:** This build uses an ad-hoc signature.
macOS Gatekeeper rejects this build.
If macOS blocks the app, open the app from the `Privacy & Security` settings, or clear the quarantine flag:

```sh
xattr -dr com.apple.quarantine /Applications/LidSleepX.app
```

## Checksum

```text
SHA-256: acc216eb2b09a1348c76ec0c236818c60047619579f7725c76205496e3ad1145
LidSleepX-0.1.0.dmg
```

## Not in this build

- No sleep mode.
- No automatic update install.
- No Developer ID signature.
- No notarization.
- No Intel or Universal 2 build.
- No App Store build.
