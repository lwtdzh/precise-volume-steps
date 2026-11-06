# Precise Volume Steps 1.5.3 volume-key investigation 2026-11-06 — recorded verification

APK SHA-256: `749a33bf8ebf6b5c6d45cd99b2eec02d8afb54e9711212e9fc6c17a93688bc30`

Unit tests: 67 tests, 0 failures, 0 errors.
Release lint: 0 errors; 30 warnings.

| Run | Device / API | Input | Passed | Outcome |
|---|---|---|---:|---|
| emulator-5594-backend-1793969298928791000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 102/102 | PASS |
| emulator-5594-frames-1793968815367450000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 13/13 | PASS |
| emulator-5594-helper-startup-1793968712847607000.json | emulator-5594 / 33 | synthetic strict EventHub dumps; actual helper monitor and Android power | 7/7 | PASS |
| emulator-5594-layout-1793968820718329000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 56/56 | PASS |
| emulator-5594-ordinary-1793968787854961000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 17/17 | PASS |
| emulator-5594-privileged-hold-1793969144134797000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 36/36 | PASS |
| emulator-5594-privileged-hold-1793969265848321000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 10/10 | PASS |
| emulator-5594-privileged-hold-1793969278286712000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 26/26 | PASS |
| emulator-5594-released-key-wake-1793968717911794000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 27/27 | PASS |
| emulator-5594-released-key-wake-1793968753068694000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 27/27 | PASS |
| emulator-5594-screen-off-1793968828729616000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 16/16 | PASS |
| emulator-5594-backend-1793971812045685000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 102/102 | PASS |
| emulator-5594-modes-1793971720156022000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 31/31 | PASS |
| emulator-5594-privileged-hold-1793971728279289000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 35/36 | FAIL |
| emulator-5594-privileged-hold-1793971777785454000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 10/10 | PASS |
| emulator-5594-privileged-hold-1793971791067288000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 26/26 | PASS |
| emulator-5594-modes-1793972233702981000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 31/31 | PASS |
| emulator-5594-privileged-hold-1793972241477073000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 35/36 | FAIL |
| emulator-5594-modes-1793973472158275000-setup-failure.json | emulator-5594 / unknown | input source not recorded | 0/0 | FAIL |
| emulator-5594-privileged-hold-1793973472833817000-setup-failure.json | emulator-5594 / unknown | input source not recorded | 0/0 | FAIL |
| emulator-5594-modes-1793973567442038000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 31/31 | PASS |
| emulator-5594-privileged-hold-1793973576037956000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 36/36 | PASS |
| emulator-5594-privileged-hold-1793973905486724000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 36/36 | PASS |

Original failed runs remain separate from repeats. Synthetic callbacks and external input do not establish physical-button coverage.
Unit/lint files are the current local evidence; run the build immediately before this report. A matching device APK hash does not establish a matching source commit.

Physical phone matrix: record separately; no pass is inferred here.

Failures:

- emulator-5594-privileged-hold-1793971728279289000.json: staggered native writes settle to the complete raw count —
- emulator-5594-privileged-hold-1793972241477073000.json: staggered native writes settle to the complete raw count —
- emulator-5594-modes-1793973472158275000-setup-failure.json: Installed host APK does not match tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk
- emulator-5594-privileged-hold-1793973472833817000-setup-failure.json: Installed host APK does not match tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk
