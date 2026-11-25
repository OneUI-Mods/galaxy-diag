# Galaxy DIAG

[日本語](README.md) · English

A root app that prepares Galaxy modem-measurement connectivity. Opening it sends the DIAG permission request and verifies that the modem endpoint is usable. **Open NSG** launches Network Signal Guru.

## Automatic startup after boot

Automatic startup after device boot can be switched on or off in settings. The default is on when no choice has been saved; an existing off choice is preserved. Startup only restores configured behavior and does not override feature switches, permissions, or lock-state restrictions. Successfully preparing DIAG for the first time no longer overwrites an explicitly disabled startup setting.

## Requirements

- A Samsung Galaxy device (validated on the Galaxy S26 Ultra **SM-S948Q / SM8850 / Android 17**).
- A working root environment, such as KernelSU.
- Network Signal Guru, installed separately, for measurements.

There is no model restriction: the permission request is also sent on Galaxy devices other than the validated one. Some models, OS versions, or modems may not connect, and OS or modem updates can break compatibility even on the same model. The initial release did not validate actual 5G NR reception or post-boot automatic application through a physical device reboot. The job shared with startup processing was physically validated through recovery of a closed connection.

## Usage

1. Install the signed release APK from [Releases](https://github.com/OneUI-Mods/galaxy-diag/releases).
2. **Allow root access for Galaxy DIAG** in KernelSU or your root manager. If `su` is hidden, approve the app in the root manager first.
3. Open Galaxy DIAG. Preparation can take more than ten seconds.
4. When the app reports that the MPSS connection is confirmed, choose **Open NSG**.

After the first successful application, **Apply automatically after startup** becomes enabled. It then applies once after device startup or an app update, with at most two retries for early-startup failures. If Samsung closes access later, reopen the app to reapply it.

**Revoke permission** closes DIAG and disables automatic startup application. Opening the app again reapplies permission. To keep automatic application disabled, turn the switch off and do not reopen the app.

## Interface

The connection card shows preparation status and provides **Open NSG**. Reapply/status controls are at the bottom of the card; automatic application and revocation are grouped in settings rows. Use the top-right information button → **Appearance** to choose system, light, or dark themes.

## Battery and privacy

There is no resident service, periodic monitoring, or app-specific network communication. Root helpers run only during operations and exit afterward. DIAG permission and NSG's own measurement/logging are separate; stop NSG when finished. Long-term battery use with DIAG enabled has not been measured.

Licenses are searchable in the app. The project uses the [MIT License](LICENSE). Development, signing, and validation are in the [developer guide (Japanese)](docs/DEVELOPMENT.md).

[Build environment / ビルド環境](docs/build-environment.md)
