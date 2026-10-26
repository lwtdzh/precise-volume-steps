# Precise Volume Steps 1.4.12 — standalone repository

Verified on 2026-10-26. Main version 1.4.12/code 17; helper 1.1/code 2.

The project now builds independently from its repository root. It contains the
Gradle wrapper, app, helper, optional test host, concise installation README,
agent entry instructions, two repository skills and their implementation history
and physical-phone test matrix. No enclosing privilege-assistant modules, private
keys or raw device captures are included.

Runtime package IDs, component names, provider authority and signature permission
remain unchanged. Both delivered APKs retain the earlier local development
certificate (SHA-256 `ba65663993e5a1001eec6828d3e6250c6b12caf00afb15f058c92462804c71b5`).
Other machines' default debug keys differ and cannot upgrade these installations.

## Current verification

| Check | Result |
|---|---:|
| Standalone main/helper release builds and test-host build | Passed |
| Clean-clone main/helper/test-host build, release lint and unit tests | Passed |
| Main release unit tests | 20/20 |
| Main/helper release lint | No errors; 7/1 warnings |
| Android 13 emulator final-readback / frame geometry | 13/13 |
| Emulator three-row / one-bar layout | 56/56 |
| Emulator screen-off / key-up recovery / wake-lock expiry | 8/8 |
| Emulator ordinary AudioManager correction | 12/12 |
| Passive installed-app checks after accessibility recovery | 6/6 |
| Skill format, links, script syntax and result-parser checks | Passed |
| Report rejects incomplete/failed runs and retains successful repeats separately | Passed |

The initial passive check recorded five failures and one passing restoration
check because the main service/accessibility was disconnected after
instrumentation. Restoring the accessibility connection and launching the
enabled main application made the complete repeat pass. Both original and repeat
outputs were retained outside Git. The skill explicitly requires restoring
runtime accessibility before normal-installed-app or physical-button checks.

Instrumentation uses synthetic controller callbacks with actual Android audio,
power state and IPC. It does not establish physical-button routing. Original
emulator APKs, preferences, overlay permission, accessibility list and native
volume were restored afterward.

No physical phone was connected during this standalone verification.
Vivo locked/screen-off physical-button handling, real reboot recovery and
Bluetooth routes remain outstanding. Historical Vivo 1.4.11 results, including
one unexplained failed background correction and its successful full repeat,
are preserved in the compatibility skill.

The distribution APKs below were rebuilt with standalone configuration, so their
hashes differ from the earlier 1.4.12 rename build while versions and signing
identity remain the same. These are the bytes used for this verification.

```text
d22ad36b82aea32afd0a3514fa7bb682edc3bc6bcee64ada16fb0c516bde3d73  precise-volume-steps-1.4.12.apk
702d73d121bc0e3e6d650c699c4904cfa8e33022a8e40af9e9b90d7d399161b5  precise-volume-steps-compatibility-helper-1.1.apk
```
