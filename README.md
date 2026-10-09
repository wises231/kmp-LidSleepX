# Covio

Covio is a macOS menu-bar app.
It controls sleep behavior and provides a screenshot toolbar at the top of the screen.

Covio is an independent implementation.
It does not use the Tendedero name, icon, or documentation images.

## Download (internal test)

This repository is under internal test.
The current build is not a public release.

- [Covio-0.4.2.dmg](https://github.com/wises231/kmp-Covio/releases/download/v0.4.2/Covio-0.4.2.dmg)
- [Release page](https://github.com/wises231/kmp-Covio/releases/tag/v0.4.2)

Install steps:

1. Open `Covio-0.4.2.dmg`.
2. Drag `Covio.app` to `Applications`.
3. Open `Covio.app`.

Covio uses a clean install.
Covio does not copy settings from older internal-test builds.
The old apps, helpers, and settings remain unchanged.

**Warning:** This build uses an ad-hoc signature.
macOS Gatekeeper rejects this build.
If macOS blocks the app, allow the app in the "Privacy & Security" settings, or clear the quarantine flag:

```sh
xattr -dr com.apple.quarantine /Applications/Covio.app
```

The download has this SHA-256 checksum:

```text
1237b65bdb8c0c15f1300a0b6ca095f8280db3b1894f1018fb2803b386333344  Covio-0.4.2.dmg
```

## What is new in 0.4.2

- The project name is Covio.
- The Kotlin package is `com.wyz.covio`.
- The application identifier is `com.wyz.covio`.
- The app uses `Covio` for settings, logs, the helper, and the LaunchAgent.

## What is new in 0.4.1

- The app fixes a crash that happened when you opened Markup from a screenshot card.
- The app opens Markup on the main thread through a native helper.
- If Markup is unavailable, the app opens Preview.

## What is new in 0.4.0

- The app adds the screenshot toolbar.
- The app keeps the sleep, lid, DarkWake, helper, and LaunchAgent functions.
- The app adds a full-width screenshot toolbar at the top of the mouse screen.
- Screenshot takeover is on by default.
- New screenshots appear in the toolbar.
- A click copies the image and file URL.
- A double-click opens the preview.
- A long press opens macOS Markup. If Markup is unavailable, Preview opens.
- A right-click opens the screenshot menu.
- Dragging to an app copies the file and keeps the card.
- Dragging to a folder or the Desktop moves the file and removes the card.
- Dragging to the Trash deletes the file and removes the card.
- A canceled or failed drag returns the card.
- `Control+Option+T` shows or hides the toolbar.
- The Tray menu and the Top Shelf settings page control screenshot behavior.
- The settings and log use the `com.wyz.covio` application identifier.

## Screenshot toolbar

The toolbar width equals the full width of the screen under the mouse pointer.
The toolbar height is 210 points.
The toolbar shows 3 to 12 screenshot cards.
The mouse must stay on the menu bar for 0.25 seconds before the toolbar appears.
The toolbar hides 0.5 seconds after the mouse leaves it.
A new screenshot hides the toolbar after 2.5 seconds.
The toolbar does not appear in a full-screen Space.

Screenshot takeover writes these macOS settings:

- `location`
- `location-screenshot`
- `show-thumbnail`

Covio saves the old values before the first change.
Covio restores the old values when you disable takeover or quit the app normally.
If the app crashes, the next start reads the saved values and keeps them for the next restore.

The screenshot directory is:

```text
~/Library/Application Support/Covio/Screenshots
```

Screenshots stay on this Mac.
Covio does not send screenshot data to the network.

## Sleep and lid features

- Sleep at the low-battery capacity threshold.
- Sleep at the low-battery time threshold.
- Prevent idle sleep on AC power.
- Prevent lid sleep on AC power.
- Prevent lid sleep on battery power. This option drains the battery.
- Detect DarkWake and full wake events from the `pmset` wake log.
- Sleep immediately when the lid closes.
- Cancel a prevention rule after a selected delay.
- Run Sleep Now or Turn Display Off from the Tray menu.
- Set hibernation mode 0, 3, or 25.
- Detect lid changes with `IOServiceAddInterestNotification`.
- Start at login with a LaunchAgent.
- Restart after a crash with `KeepAlive.SuccessfulExit=false`.
- Clear the configuration from the Tray menu or the General settings page.
- Cache the system idle timeout for 60 seconds.
- Cache the wake log for 60 seconds.
- Show notifications and write a rotating log.
- Check the GitHub Releases page for a stable update.
- Show English and Simplified Chinese text.

## Requirements

- macOS 12 or later.
- An Apple Silicon Mac.
- JDK 25 for packaging.
- Xcode command line tools for the native drag library.

## Build and test

```sh
/bin/sh ./kotlin test
/bin/sh ./kotlin build
```

## Package

```sh
bash scripts/package-macos.sh
```

The script writes these files:

- `dist/Covio.app`
- `dist/Covio-0.4.2.dmg`

The script compiles `native/libCovioDrag.dylib` for arm64.
The script copies the library into the app image.
The script sets the bundle keys and applies an ad-hoc signature.

## Real-Mac acceptance checklist

Run this checklist before a public release.
The current internal test does not complete this checklist.

1. Install Covio 0.4.2.
2. Confirm the app version is `0.4.2`.
3. Install the privileged helper.
4. Confirm the helper version is `0.4.2`.
5. Confirm screenshot takeover changes the three `com.apple.screencapture` keys.
6. Take a screenshot. Confirm the system thumbnail is hidden.
7. Confirm the screenshot appears in the toolbar.
8. Click the screenshot. Confirm the image and file URL are on the clipboard.
9. Double-click the screenshot. Confirm Preview opens.
10. Long-press the screenshot. Confirm Markup opens.
11. Drag the screenshot to an app. Confirm the app receives a copy.
12. Drag the screenshot to a folder. Confirm the source moves.
13. Drag the screenshot to the Desktop. Confirm the source moves.
14. Drag the screenshot to the Trash. Confirm the source uses the Trash.
15. Cancel a drag. Confirm the card returns.
16. Close takeover. Confirm the three system keys return to the old values.
17. Quit Covio normally. Confirm the keys return to the old values.
18. Force-quit Covio. Start Covio. Confirm the saved snapshot is reused.
19. Test the toolbar on each display.
20. Enter a full-screen Space. Confirm the toolbar hides.
21. Press `Control+Option+T`. Confirm the toolbar toggles.
22. Delete a screenshot outside Covio. Confirm the card disappears.
23. Enable "Prevent lid sleep on AC power". Close the lid on AC power. Confirm SSH stays connected.
24. Enable "Prevent lid sleep on battery power". Close the lid on battery power. Confirm SSH stays connected.
25. Turn off the battery switch while the lid is closed. Confirm the Mac sleeps and SSH disconnects.
26. Reach the low battery threshold. Confirm the low battery rule wins.
27. Confirm the General page shows the DarkWake time, reason, and 24-hour count.
28. Fully wake the Mac. Confirm the timers restore or the policy recalculates.
29. Change the sleep mode between 0, 3, and 25.
30. Clear the configuration. Confirm the default values return.
31. Quit the app. Confirm the LaunchAgent does not restart it.
32. Force-quit the app. Confirm the LaunchAgent restarts it.
33. Remove the helper. Confirm `SleepDisabled` is `0`.

## Privileged helper

The app installs a LaunchDaemon only after you approve the installation dialog.
The helper accepts three commands:

- `version`
- `setDisableSleep`
- `setHibernateMode`

The helper checks the peer user ID before the helper runs a command.
The app and the helper must use the same version.
An old helper shows "Helper: Update Required" and disables the lid-hold switches.

## Paths

The configuration is:

```text
~/Library/Application Support/com.wyz.covio/config.json
```

The log is:

```text
~/Library/Application Support/com.wyz.covio/logs/Covio.log
```

The LaunchAgent is:

```text
~/Library/LaunchAgents/com.wyz.covio.plist
```

The helper socket is:

```text
/Library/Application Support/Covio/helper.sock
```

The helper LaunchDaemon is:

```text
/Library/LaunchDaemons/com.wyz.covio.helper.plist
```

## Publish an internal test release

1. Run `scripts/package-macos.sh`.
2. Run `gh auth login`.
3. Run `bash scripts/create-release.sh`.

The release tag must be a full release.
The update checker ignores pre-release tags.

## License

MIT. See `LICENSE`.
Third-party notices are in `THIRD_PARTY_NOTICES.md`.
