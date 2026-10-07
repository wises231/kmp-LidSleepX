# LidSleepX

LidSleepX is a menu-bar app that controls macOS sleep behavior.
It watches the battery, the lid, and the idle timer.
It needs a privileged helper to change macOS sleep and hibernation settings.

The project is an independent implementation.
It does not share code, text, or assets with other projects.

## Download (internal test)

This repository is under internal test.
The current build is not a public release.

Download the latest internal build:

- [LidSleepX-0.2.1.dmg](https://github.com/wises231/kmp-LidSleepX/releases/download/v0.2.1/LidSleepX-0.2.1.dmg)
- [Release page](https://github.com/wises231/kmp-LidSleepX/releases/tag/v0.2.1)

Install steps:

1. Open `LidSleepX-0.2.1.dmg`.
2. Drag `LidSleepX.app` to `Applications`.
3. Open `LidSleepX.app`.

**Warning:** This build uses an ad-hoc signature.
macOS Gatekeeper rejects this build.
If macOS blocks the app, allow the app in the "Privacy & Security" settings, or clear the quarantine flag:

```sh
xattr -dr com.apple.quarantine /Applications/LidSleepX.app
```

The download has this SHA-256 checksum:

```text
8c9a3d7f62436c93487fbb04ab629d8809cde2eafbeb35e81e819d42df6eb3d2  LidSleepX-0.2.1.dmg
```

## Internal test notes

- Use a test Mac. LidSleepX changes system sleep behavior.
- Keep important work open only if you accept a possible system sleep.
- The helper runs as root. Install it only from this repository.
- The app changes `pmset disablesleep` and `pmset hibernatemode`.
- Remove the helper from the Tray menu before you stop the test.
- Export the log when you report a problem.
- The export replaces your home directory with `~`.
- This build has no Developer ID signature and no notarization.
- This build has no Intel or Universal 2 package.
- This build has no App Store package.
- This build does not download or install updates.

Internal acceptance checklist:

1. Install the helper.
2. Close and open the lid.
3. Confirm the lid action starts at the lid event.
4. Change the sleep mode between 0, 3, and 25.
5. Clear the configuration and confirm the default values return.
6. Quit the app and confirm the LaunchAgent does not restart it.
7. Force-quit the app and confirm the LaunchAgent restarts it.
8. Run a manual update check.
9. Remove the helper and confirm `SleepDisabled` is `0`.

## 0.2.1 changes

- The Login Item disable path deletes the plist before it disables the launchd job.
- A single-instance lock prevents more than one app process.
- The app refreshes the helper status after installation.

## Features

- Sleep when the battery capacity reaches a threshold.
- Sleep when the battery time remaining reaches a threshold.
- Prevent idle sleep while the Mac uses AC power.
- Prevent lid sleep while the Mac uses AC power.
- Sleep immediately when the lid closes.
- Cancel a prevention rule after a selected delay.
- Sleep now, or turn the display off now.
- Set hibernation mode 0, 3, or 25.
- Detect lid changes with `IOServiceAddInterestNotification`.
- Start at login with a LaunchAgent.
- Restart after a crash with LaunchAgent `KeepAlive`.
- Clear the configuration from the Tray menu or the General settings page.
- Cache the system idle timeout for 60 seconds.
- Show notifications and write a rotating log.
- Check the GitHub Releases page for a stable update.
- Show English and Simplified Chinese text.

## Requirements

- macOS 12 or later.
- An Apple Silicon Mac.
- JDK 25 for packaging. The `scripts/package-macos.sh` script finds JDK 25 in the Kotlin cache.

## Build and test

```sh
/bin/sh ./kotlin build
/bin/sh ./kotlin test
```

## Package

```sh
bash scripts/package-macos.sh
```

The script writes these files:

- `dist/LidSleepX.app`
- `dist/LidSleepX-0.2.1.dmg`

The script builds the app image with `jpackage`, sets the bundle keys, and signs the app with an ad-hoc signature.

## Publish an internal test release

1. Run `scripts/package-macos.sh`.
2. Run `gh auth login`.
3. Run `bash scripts/create-release.sh`.

The release tag must be a full release.
The update checker ignores pre-release tags.

If you package a new artifact, update the SHA-256 checksum in this file.

## Privileged helper

The app installs a LaunchDaemon only after you approve the installation dialog.
The helper accepts three commands:

- `version`
- `setDisableSleep`
- `setHibernateMode`

The helper checks the peer user ID before the helper runs a command.

The app writes its configuration to:

```text
~/Library/Application Support/com.wyz.lidsleepx/config.json
```

The app writes its log to:

```text
~/Library/Application Support/com.wyz.lidsleepx/logs/LidSleepX.log
```

## Launch at login

The app writes the LaunchAgent to:

```text
~/Library/LaunchAgents/com.wyz.lidsleepx.plist
```

The LaunchAgent uses `RunAtLoad=true`.
The LaunchAgent uses `KeepAlive.SuccessfulExit=false`.
A normal exit stops the app.
A crash restarts the app.

## License

MIT. See `LICENSE`.
