# Development

The Android app and privileged entry point are Kotlin. Build with JDK 21, Android SDK 37.0 / Build Tools 37.0.0, and the checked-in Gradle wrapper.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:writeRuntimeDependencies
python3 tools/check_license_catalog.py
python3 tools/package_release.py --check-release-apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## Fixed notification

`RootMain` only accepts enable, disable and status. It verifies root authorization and the tested Samsung model, then temporarily drops to UID/GID 1000. It invokes Samsung `ISemTelephony.invokeOemRilRequestRawForPhone(0, request, response)`. The fixed OEM bytes are `11 b0 00 05 01` (enable) / `11 b0 00 05 00` (disable), matching stock SysDump's OTP-status notification. The app sends the permission notification directly; it does not perform OTP authentication or provision an engineering token. Do not describe this as passing Samsung authentication.

The RIL result alone is insufficient. Qualcomm QRTR service4097 must expose remote MODEM:CMD(instance1,node0) and MODEM:DCI_CMD(instance3,node0); local node1 entries do not count. Publication is asynchronous. Each request has bounded wait and the root process exits. There are no arbitrary OEM/RIL/NV/EFS payloads, firmware patches, policy changes, or credential operations.

## Boot behavior

The non-exported receiver accepts only BOOT_COMPLETED and MY_PACKAGE_REPLACED. It schedules one JobScheduler job when the setting is enabled. The nonperiodic job has at most two explicit retries and no network/battery exemption request. Root permission must be granted interactively in the root manager before unattended execution. Android background restrictions or an app force-stop can delay/prevent delivery. The app-open path remains available. Job cancellation prevents rescheduling canceled work.

The visible activity automatically applies on a fresh launch. NSG launch is only initiated by the visible activity after verified endpoint availability. Boot jobs never launch UI or NSG. The app does not subscribe to RF logs, intercept NSG, keep a foreground service, or force the radio to a band/SIM.

## Release

Keep `.signing/release.keystore` (alias galaxy-diag) and `.signing/password` private, mode600 in a mode700 directory. `signing-certificate.sha256` contains only the public certificate pin. A private backup is maintained separately in the user's signing-key backup folder; never place it in Git/CI/artifacts. Commit all sources, then run:

```sh
python3 tools/package_release.py --source-ref HEAD
```

The script runs checks, signs only the release variant, verifies the pinned certificate and manifest, and creates the APK, exact-commit source ZIP, signature report, metadata and SHA256SUMS. CI checks unsigned release structure but never signs or uploads APK artifacts. Release notes describe behavior, supported environments and limitations for users. Publish a new tag rather than moving an old tag.

## Validation

Protocol tests cover fixed bytes, remote/local QRTR discrimination, both endpoints, malformed input, shell path quoting and bounded retries. Physical test notes belong in `VALIDATION.md`; a scheduled job or code inspection is not proof of a real reboot test. Never reboot the OS/SystemUI/modem to satisfy a test without separate authorization.
