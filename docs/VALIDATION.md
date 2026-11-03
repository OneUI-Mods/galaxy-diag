# Validation — 2026-11-03 JST

## Scope

Galaxy S26 Ultra SM-S948Q / SM8850 / Android17 BZI9, KernelSU samsung-v2.2. Root permission is app-specific. The first run failed because KernelSU hid su from the newly installed app. After the app received root authorization, subsequent runs succeeded. No global root policy, SELinux mode, radio/SIM/band setting, lock credential, firmware image, OS/SystemUI/modem restart was used.

## Checks

- Five JVM tests passed: fixed permission bytes; local router false positives; required remote endpoints; shell quoting; bounded automatic retries.
- Release build and lint passed. Expected hidden-API warnings remain: the app intentionally targets the tested private Samsung Binder interface. Future firmware compatibility is not guaranteed.
- All55 resolved runtime dependencies match the bundled searchable license catalog. The app has no Internet or foreground-service permission.
- Dedicated release certificate is pinned in signing-certificate.sha256. Release APK signature, package/version/SDK, nondebuggable flag, manifest, 16KB zip alignment and absence of test components/private credentials are checked by the packaging script.
- Main activity automatically sent the notification and verified the MPSS endpoints from its own granted root process.
- The visible disable confirmation closed both remote MPSS endpoints and turned off the boot setting. Closing/reopening the app reapplied the permission and restored both endpoints.
- The boot setting was re-enabled. A controlled RootMain disable then closed MPSS without changing that setting. An explicit MY_PACKAGE_REPLACED event to the real production receiver scheduled job8101; forcing that nonperiodic job restored both endpoints. Its last result was successful, the job completed/disappeared and no root helper remained.
- The app's button launched official NSG4.8.12 after preparation. Physical UI showed LTE B3/20MHz, EARFCN1300, PCI252, RSRP-97.3dBm, SINR5.2dB, PLMN/TAC/cell ID, CQI, rank, RB, downlink QPSK/uplink16QAM and throughput counters. NSG was exited after testing.

No actual device reboot was performed. The receiver/job test proves that production code path on the running device, not boot delivery or timing after a physical reboot. NR reception, long-duration battery cost, firmware updates, other models and Samsung deep-sleep restrictions remain unverified. A cached app process is not a resident service; only request-triggered work is implemented.

Detailed screenshots, package/signature checks and device invariants are retained outside the public source tree in the investigation workspace. User-facing release notes contain behavior and limitations rather than this test history.


## 0.2.0 visual redesign

The main screen now has one primary NSG action, a connection summary, secondary actions and grouped settings rows. Day/night colors use resource variants; appearance selection changes only this app. The current font and display scale fit the complete main screen without clipping. Day and night main screens and the license list were inspected on the physical device. The primary action color with white text has a calculated contrast ratio of5.02:1. App information and appearance choices remain accessible through the top-right information icon. Existing boot automation settings were preserved on upgrade.

The license search uses a grouped list and reports matching results. Opening license text hides only this app's keyboard and limits the detail scroll area. Theme settings are restored to following the device after validation. Screenshots are kept in the private investigation workspace rather than publishing phone status-bar content. The fixed root notification and bounded boot job are unchanged; only the recorded connection state now includes an explicit open/closed/unknown field for presentation.
