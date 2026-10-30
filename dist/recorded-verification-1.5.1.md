# Precise Volume Steps 1.5.1 — recorded verification

APK SHA-256: `25fdd8269432e3f3a8c5de78a08e6bad2c83bad0bd74f6f19f3f094205843496`

Unit tests: 53 tests, 0 failures, 0 errors.
Release lint: 0 errors; 29 warnings.

Input clarification: the six HID runs used `/dev/uhid` to emulate external keys.
The table preserves their original `external USB HID` label; these rows record
UHID emulation, separately from physical USB devices and built-in phone buttons.

| Run | Device / API | Input | Passed | Outcome |
|---|---|---|---:|---|
| pvs-lockscreen-final3-assistant-50/result.json | 10AECU19N1002LM / 36 | external USB HID; actual Android audio and lit keyguard | 52/52 | PASS |
| pvs-lockscreen-final3-assistant-64/result.json | 10AECU19N1002LM / 36 | external USB HID; actual Android audio and lit keyguard | 52/52 | PASS |
| pvs-lockscreen-final3-root-50/result.json | 10AECU19N1002LM / 36 | external USB HID; actual Android audio and lit keyguard | 47/47 | PASS |
| pvs-lockscreen-final3-root-64/result.json | 10AECU19N1002LM / 36 | external USB HID; actual Android audio and lit keyguard | 47/47 | PASS |
| pvs-lockscreen-final3-shizuku-50/result.json | 10AECU19N1002LM / 36 | external USB HID; actual Android audio and lit keyguard | 47/47 | PASS |
| pvs-lockscreen-final3-shizuku-64/result.json | 10AECU19N1002LM / 36 | external USB HID; actual Android audio and lit keyguard | 47/47 | PASS |
| 10AECU19N1002LM-backend-1793364936389859000.json | 10AECU19N1002LM / 36 | synthetic controller callbacks; actual Android audio/IPC | 102/102 | PASS |
| 10AECU19N1002LM-backend-1793364960732715000.json | 10AECU19N1002LM / 36 | synthetic controller callbacks; actual Android audio/IPC | 102/102 | PASS |
| 10AECU19N1002LM-backend-1793364984571782000.json | 10AECU19N1002LM / 36 | synthetic controller callbacks; actual Android audio/IPC | 102/102 | PASS |
| 10AECU19N1002LM-backend-1793365008763058000.json | 10AECU19N1002LM / 36 | synthetic controller callbacks; actual Android audio/IPC | 102/102 | PASS |
| 10AECU19N1002LM-backend-1793365032924316000.json | 10AECU19N1002LM / 36 | synthetic controller callbacks; actual Android audio/IPC | 107/107 | PASS |
| 10AECU19N1002LM-backend-1793365057310774000.json | 10AECU19N1002LM / 36 | synthetic controller callbacks; actual Android audio/IPC | 107/107 | PASS |
| emulator-5594-frames-1793364486652099000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 1/1 | FAIL |
| emulator-5594-layout-1793364491180509000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 0/1 | FAIL |
| emulator-5594-ordinary-1793364506333923000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 17/17 | PASS |
| emulator-5594-screen-off-1793364492504547000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 16/16 | PASS |
| emulator-5594-frames-1793364545453850000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 13/13 | PASS |
| emulator-5594-layout-1793364548749300000.json | emulator-5594 / 33 | synthetic controller callbacks; actual Android audio/IPC | 56/56 | PASS |
| results.json | emulator-5594 / unknown | input source not recorded | 18/18 | PASS |

Original failed runs remain separate from repeats. Synthetic callbacks and external input do not establish physical-button coverage.
Unit/lint files are the current local evidence; run the build immediately before this report. A matching device APK hash does not establish a matching source commit.

Physical phone matrix: record separately; no pass is inferred here.

Failures:

- emulator-5594-frames-1793364486652099000.json: java.lang.IllegalStateException: Final display not observed: 6
- emulator-5594-layout-1793364491180509000.json: fast confirmation displays latest readback —
- emulator-5594-layout-1793364491180509000.json: java.lang.IllegalStateException: fast confirmation displays latest readback
