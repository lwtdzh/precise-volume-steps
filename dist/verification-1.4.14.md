# Precise Volume Steps 1.4.14 — recorded verification

APK SHA-256: `db4602f21945311e6eaf50555e3e17ae2867a133ba662a91f1420bdc6813d72d`

Unit tests: 31 tests, 0 failures, 0 errors.
Release lint: 0 errors; 7 warnings.
Helper release lint: 0 errors; 1 warning.

| Run | Device / API | Input | Passed | Outcome |
|---|---|---|---:|---|
| AOSP-emulator-frames.json | AOSP-emulator / 33 | synthetic controller callbacks; actual Android audio/IPC | 13/13 | PASS |
| AOSP-emulator-layout.json | AOSP-emulator / 33 | synthetic controller callbacks; actual Android audio/IPC | 56/56 | PASS |
| AOSP-emulator-ordinary.json | AOSP-emulator / 33 | synthetic controller callbacks; actual Android audio/IPC | 12/12 | PASS |
| AOSP-emulator-screen-off.json | AOSP-emulator / 33 | synthetic controller callbacks; actual Android audio/IPC | 16/16 | PASS |
| Vivo-V2419A-screen-off-hid-shizuku-50.json | Vivo-V2419A / 36 | external UHID consumer keys; built-in physical buttons not pressed | 83/83 | PASS |
| Vivo-V2419A-screen-off-hid-shizuku-64.json | Vivo-V2419A / 36 | external UHID consumer keys; built-in physical buttons not pressed | 83/83 | PASS |
| Vivo-V2419A-backend-shizuku-50.json | Vivo-V2419A / 36 | synthetic controller callbacks; actual Android audio/IPC | 85/85 | PASS |
| Vivo-V2419A-backend-shizuku-64.json | Vivo-V2419A / 36 | synthetic controller callbacks; actual Android audio/IPC | 85/85 | PASS |
| Vivo-V2419A-backend-companion-50.json | Vivo-V2419A / 36 | synthetic controller callbacks; actual Android audio/IPC | 87/87 | PASS |
| Vivo-V2419A-backend-companion-64.json | Vivo-V2419A / 36 | synthetic controller callbacks; actual Android audio/IPC | 87/87 | PASS |

Original failed runs remain separate from repeats. Synthetic callbacks and external input do not establish physical-button coverage.
Unit/lint files are the current local evidence; run the build immediately before this report. A matching device APK hash does not establish a matching source commit.

Physical phone matrix: record separately; no pass is inferred here.

## Installed-phone results

Main 1.4.14, version code 26. HID tests ran on 2026-10-26; remaining backend checks and restoration completed on 2026-10-27.
Source/configuration SHA-256 (production, helper and test sources): `47c1cb5060756e58b28872a80988e1d34dcc767ce8dcc872a898259f01047236`.
Vivo fingerprint: `vivo/PD2419/PD2419:16/BP2A.250605.031.A3_V000L1/compiler260529100608:user/release-keys`; API 36; native media range 0–150.
The speaker/route state remained as configured on the phone; Bluetooth was enabled. These runs do not certify a Bluetooth endpoint or AVRCP behavior.

Both complete external-HID runs tested idle and real AudioTrack playback, including five presses asleep, wake/unlock and a sixth press, exact reversal, rapid mixed bursts, released holds, passive presets, hidden overlay, CPU wake-lock expiry, and main/player process continuity. Playback was asserted across unlock and around bursts. The production app creates no playback to receive keys.
At 50 steps, baseline 121/150 displays 81%; five down presses give 106/150, and the sixth gives 103/150 (69%). At 64 steps, baseline 74/150 retraces exactly despite uneven native increments.
The backend runs use synthetic controller callbacks with real Shizuku/helper IPC and Android readback. They test 50 and 64 steps for each backend.

Test host SHA-256: `21a38df7949554831cc6bf41dc9ddf8e4140c716f2b860d204ac5f9540d5aab5`.
UI observer SHA-256: `0dcc914f87f0dd726baa10b7fee0e5f20ef2b7c0accff4ba4b328263c944fa89`.
Installed helper SHA-256: `23852b52e4669f476050affd7476f052323c07992aa6e1e764003a80316a6d80`.
The installed helper was rebuilt from unchanged 1.1 sources with the existing development certificate. The historical distributed helper APK remains unchanged; its byte hash differs and these runs do not establish a new byte-for-byte test of that artifact.

## Earlier failed runs

Failed captures and successful reruns remain outside Git. These counts are not included in the successful-run totals above. Same-version experiments used several different APKs; their hashes are listed separately. The 75-check earlier 50-step run lacks the final playback-continuity assertions and is not used as final evidence.

| Original run | APK SHA-256 | Passed | Failure |
|---|---|---:|---|
| screen-off-hid-50-1793010870521649000.json | `cb8b7e3029d927048b113281241b01f2843e63fe8217fb697032af3ab0291387` | 0/0 | Fixture setup/unlock/restoration did not complete; restoration also failed; repaired before subsequent runs |
| screen-off-hid-50-1793010964905123000.json | `cb8b7e3029d927048b113281241b01f2843e63fe8217fb697032af3ab0291387` | 2/2 | Fixture setup/unlock/restoration did not complete; restoration also failed; repaired before subsequent runs |
| screen-off-hid-50-1793011252561884000.json | `c27d1f94c7b44149b1e63b4fd5928930113901e9478f028f369dfcd3a2f00240` | 8/9 | idle asleep press 1 has the exact native value |
| screen-off-hid-50-1793011464723702000.json | `6cfa0aad9f2393a8075c806d061eb8286c794afbfaf25aae61d00778c94ee319` | 8/9 | idle asleep press 1 has the exact native value |
| screen-off-hid-50-1793011879133547000.json | `617565d0ea06db4203de83c2a80b2cd151588b09d2dd79413a31738fe0927ae1` | 30/32 | idle wake lock expires and display stays off; volume and other accessibility services restored; restoration also failed; repaired before subsequent runs |
| screen-off-hid-50-1793012074946396000.json | `617565d0ea06db4203de83c2a80b2cd151588b09d2dd79413a31738fe0927ae1` | 26/28 | idle rapid mixed burst ends three steps below origin; volume and other accessibility services restored; restoration also failed; repaired before subsequent runs |
| screen-off-hid-50-1793012516792253000.json | `617565d0ea06db4203de83c2a80b2cd151588b09d2dd79413a31738fe0927ae1` | 46/47 | playback asleep press 3 has the exact native value; restoration also failed; repaired before subsequent runs |
| screen-off-hid-50-1793068046716160000.json | `76b9d8210df8ac6929023355ea1285dee781064d7f04609a9672d041a3fad99c` | 28/29 | idle rapid mixed burst ends three steps below origin; restoration also failed; repaired before subsequent runs |
| screen-off-hid-64-1793068379128741000.json | `db4602f21945311e6eaf50555e3e17ae2867a133ba662a91f1420bdc6813d72d` | 64/65 | playback rapid mixed burst ends three steps below origin |
| screen-off-hid-64-1793068631481832000.json | `db4602f21945311e6eaf50555e3e17ae2867a133ba662a91f1420bdc6813d72d` | 66/67 | playback audio state remains correct before rapid burst |
| screen-off-hid-64-1793068848451847000.json | `db4602f21945311e6eaf50555e3e17ae2867a133ba662a91f1420bdc6813d72d` | 17/17 | Fixture setup/unlock/restoration did not complete; restoration also failed; repaired before subsequent runs |
| screen-off-hid-64-1793069113533313000.json | `db4602f21945311e6eaf50555e3e17ae2867a133ba662a91f1420bdc6813d72d` | 46/47 | playback audio state matches fixture |

Early raw-input attempts incorrectly excluded the connected A2DP state; that guard was removed after direct event evidence. Early shortcuts/unlock failures were fixture failures: shell Settings writes had silently failed, then the secure camera obscured keyguard. The fixture now changes native UI switches and verifies restoration.
Version code 23 also exposed a production baseline race: native 115→105 could occur before the raw snapshot/poll, yielding 102 instead of 112. RawVolumeBaseline now retains the last real pre-key sample for this ordering.
Version code 25 exposed a late native write after a rapid burst (112→122). Code 26 waits at least 300 ms after the last raw release before correction.
The first code-26 playback burst and the focus-only repeat lost playback during UI inspection: instrumentation targeted the player and restarted it. A separate UI observer now preserves the player, and the fixture asserts playback and player PID.
The next observer run used an obsolete private UI XML path and failed unlock before playback testing; the local helper path was repaired. A subsequent run failed at the playback-start assertion because an existing host task did not receive its new play extra. SINGLE_TOP launches now deliver that extra. The final two complete HID runs use both fixture fixes and all strict assertions.

## Physical feedback matrix

The complete P01–P22 feedback matrix was not run in this automated session. Rows requiring built-in physical buttons remain unverified; no physical-button pass is inferred. Relevant automated evidence is listed below.

| Rows | Current automated evidence | Physical status |
|---|---|---|
| P01–P09 | Unit rounding plus real Shizuku/helper callback suites; HID reversals/bursts/holds | Not run |
| P10–P12 | Emulator layout and confirmed/held/rejected-write panel fixtures | Not run |
| P13–P14 | Ordinary/emulator passive/canceled-key checks; raw parser/chord unit tests | Not run |
| P15 | Complete external HID on Vivo, idle/playback, 50/64 steps | Built-in buttons not run |
| P16–P18 | Backend switching/fixture recovery and package upgrades; full lifecycle matrix not run | Not run |
| P19–P20 | No new real-phone reboot or route/endpoint certification | Not run |
| P21 | Authorized ADB installs and installed APK readback verified | Automated installation only |
| P22 | Readiness/IPC rejection fixtures; full OEM permission matrix not run | Not run |

## Restoration

Restored latest user state: native media volume 129/150, configured 50 steps, media-only correction enabled, Shizuku enabled; native long-press/camera shortcut values 1/1/2 restored. Bluetooth enabled, screen timeout 600000 ms, USB stay-awake 2.
Main and GKD accessibility services are enabled and connected; the compatibility helper and Shizuku key/volume services are ready. No pending correction, playback, overlay or key wake lock remains. Temporary host/UI/probe packages and pushed HID/preset files were removed. Main/helper/Shizuku remain installed.
The two complete HID runs restored their original 0/150 baseline. After the interrupted backend session, the current phone volume was 129/150 and only GKD remained in the enabled-accessibility list. That newer volume was preserved. The main service was enabled again through native Settings, retaining GKD. A USB connection dialog initially obscured Settings; it was dismissed through its inspected Cancel button before verifying both service connections.

This is verified external-HID and synthetic-backend evidence for the new Shizuku path. Built-in physical screen-off buttons, ordinary-mode screen-off delivery, real-phone reboot and Bluetooth endpoint behavior remain unverified.
