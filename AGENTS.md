# Development and distribution

- Android implementation stays in Kotlin. Use Material 3 and include searchable dependency licenses.
- Deliver only a signed, verified release APK, matching source ZIP, SHA256SUMS, and signature report.
- Keep signing keys/passwords private, outside Git, CI, logs, and release assets. Back up the app-specific key separately.
- Keep README and release notes user-facing. Put internals and validation in docs/DEVELOPMENT.md.
- Manage source in OneUI-Mods/galaxy-diag. CI verifies tests, lint, release build, dependency licenses, and APK structure. Do not upload unsigned APK artifacts or keys to CI.
- Do not reboot Android, SystemUI, or the modem for validation. Acquire the shared-device lease before device mutations and restore foreground afterward.
