# LidSleepX 0.2.1 (internal test)

This is the third internal test build.
It is not a public release.

## Requirements

- macOS 12 or later.
- An Apple Silicon Mac (arm64).
- JDK 25 is not required to run the app.

## What is in this build

- Menu-bar app with no Dock icon.
- Sleep modes 0, 3, and 25 through `hibernatemode`.
- Event-driven lid detection with `IOServiceAddInterestNotification`.
- Battery sleep rules: capacity threshold and time-remaining threshold.
- AC rules: prevent idle sleep and lid sleep on AC power.
- Lid close sleep, or a lid-close action that only turns the display off.
- Timed cancel for the prevention rules.
- Sleep now, and display sleep now.
- LaunchAgent start at login.
- LaunchAgent restart after a crash.
- Configuration reset from the Tray menu and the General settings page.
- A 60-second cache for the system idle timeout.
- Notifications and a rotating log.
- Stable update checks from the GitHub Releases page.
- English and Simplified Chinese text.
- A single-instance lock prevents more than one app process.
- The Login Item disable path releases the launchd job correctly.
- The app refreshes the helper status after installation.

## Privileged helper

The app needs a privileged helper to change these settings:

- `pmset disablesleep`
- `pmset hibernatemode`

The app installs the helper only after you approve the install dialog.
The helper accepts only `version`, `setDisableSleep`, and `setHibernateMode`.

## Install

1. Open `LidSleepX-0.2.1.dmg`.
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
SHA-256: 8c9a3d7f62436c93487fbb04ab629d8809cde2eafbeb35e81e819d42df6eb3d2
LidSleepX-0.2.1.dmg
```

## Internal test notes

- Use a test Mac.
- Remove the helper from the Tray menu after the test.
- Export the log when you report a problem.
- The update checker reads the Releases page. The update checker does not use the GitHub REST API.
- The update checker ignores pre-release tags.
- This build does not download or install updates.

## Not in this build

- No automatic update install.
- No Developer ID signature.
- No notarization.
- No Intel or Universal 2 build.
- No App Store build.
