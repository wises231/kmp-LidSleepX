# LidSleepX

LidSleepX is a menu-bar app that controls macOS sleep behavior.
It watches the battery, the lid, and the idle timer.
It needs a privileged helper to change the `disablesleep` setting.

The project is an independent implementation.
It does not share code, text, or assets with other projects.

## Features

- Sleep when the battery capacity reaches a threshold.
- Sleep when the battery time remaining reaches a threshold.
- Prevent idle sleep while the Mac uses AC power.
- Prevent lid sleep while the Mac uses AC power.
- Sleep immediately when the lid closes.
- Cancel a prevention rule after a selected delay.
- Sleep now, or turn the display off now.
- Start at login.
- Show notifications and write a rotating log.
- Check GitHub Releases for a stable update.
- Show English and Simplified Chinese text.

## Requirements

- macOS 12 or later.
- An Apple Silicon Mac.
- JDK 25. The `scripts/package-macos.sh` script finds JDK 25 in the Kotlin cache.

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
- `dist/LidSleepX-0.1.0.dmg`

The script builds the app image with `jpackage`, sets the bundle keys, and signs the app with an ad-hoc signature.

## Privileged helper

The app installs a LaunchDaemon only after you approve the installation dialog.
The helper accepts two commands: `version` and `setDisableSleep`.
The helper checks the peer user ID before the helper runs a command.

The app writes its configuration to:

```text
~/Library/Application Support/com.wyz.lidsleepx/config.json
```

The app writes its log to:

```text
~/Library/Application Support/com.wyz.lidsleepx/logs/LidSleepX.log
```

## License

MIT. See `LICENSE`.
