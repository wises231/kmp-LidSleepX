# MacIsland 0.4.0 (internal test)

This is the first MacIsland internal test build.
It is not a public release.

## Requirements

- macOS 12 or later.
- An Apple Silicon Mac (arm64).
- JDK 25 is not required to run the app.

## What is in this build

- A menu-bar app with no Dock icon.
- A full-width screenshot toolbar at the top of the screen.
- Screenshot takeover with the system thumbnail hidden.
- Screenshot copy, preview, Markup, context menu, and drag behavior.
- A `Control+Option+T` toolbar shortcut.
- Screenshot takeover restore on disable or normal exit.
- Sleep modes 0, 3, and 25 through `hibernatemode`.
- Event-driven lid detection with `IOServiceAddInterestNotification`.
- Battery sleep rules for capacity and time remaining.
- AC idle-sleep and lid-sleep prevention.
- Battery lid-sleep prevention. This option drains the battery.
- DarkWake awareness from the `pmset` wake log.
- A full wake restores the sleep timers or recalculates the power policy.
- A unified lid-hold policy for manual, timed, and power rules.
- Timed cancel for prevention rules.
- Sleep Now and Turn Display Off.
- LaunchAgent start at login.
- LaunchAgent restart after a crash.
- Configuration reset from the Tray menu and the General settings page.
- A 60-second cache for the system idle timeout.
- A 60-second cache for the wake log.
- Notifications and a rotating log.
- Stable update checks from the GitHub Releases page.
- English and Simplified Chinese text.
- A single-instance lock.

## Clean install

MacIsland 0.4.0 uses the new `com.wyz.macisland` identifier.
MacIsland does not migrate LidSleepX settings.
MacIsland does not modify the old LidSleepX app, helper, or configuration.
Uninstall LidSleepX 0.3.0 before you use this build.

## Privileged helper

The helper accepts only these commands:

- `version`
- `setDisableSleep`
- `setHibernateMode`

The app and the helper must use version `0.4.0`.
An old helper shows "Helper: Update Required".

## Install

1. Open `MacIsland-0.4.0.dmg`.
2. Drag `MacIsland.app` to `Applications`.
3. Open `MacIsland.app`.

**Warning:** This build uses an ad-hoc signature.
macOS Gatekeeper rejects this build.
If macOS blocks the app, open the app from the `Privacy & Security` settings, or clear the quarantine flag:

```sh
xattr -dr com.apple.quarantine /Applications/MacIsland.app
```

## Checksum

```text
SHA-256: 0e9e2dc02be19f4735a5006276d43bc7921586503c7c6887b6fc9b00fe8fe50e
MacIsland-0.4.0.dmg
```

## Internal test notes

- Use a test Mac.
- The battery lid-hold option drains the battery.
- Complete the real-Mac checklist in `README.md`.
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
