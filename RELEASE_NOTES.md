# LidSleepX 0.3.0 (internal test)

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
- A battery rule prevents lid sleep on battery power. This rule drains the battery.
- DarkWake awareness reads the `pmset` wake log.
- DarkWake updates the status only. DarkWake does not restore the sleep timers.
- A full wake restores the sleep timers or recalculates the power policy.
- A unified lid hold policy controls manual, timed, and power rules.
- Lid close sleep, or a lid-close action that only turns the display off.
- Timed cancel for the prevention rules.
- Sleep now, and display sleep now.
- LaunchAgent start at login.
- LaunchAgent restart after a crash.
- Configuration reset from the Tray menu and the General settings page.
- A 60-second cache for the system idle timeout.
- A 60-second cache for the wake log. A new event bypasses the cache.
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
The app and the helper must use the same version.
An old helper shows a warning in the Tray menu and in the settings page.

## Install

1. Open `LidSleepX-0.3.0.dmg`.
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
SHA-256: c5032016ec68846dde82a52de2e1467c0e9be4cb062c81dd709f93040d39eb73
LidSleepX-0.3.0.dmg
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
