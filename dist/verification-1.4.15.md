# Precise Volume Steps 1.4.15 verification

Verified on 2026-10-28. Main version 1.4.15/code 27; helper 1.1/code 2 unchanged.
This APK was installed and tested locally; no GitHub release was published.
This is the historical status-change APK identified by the hash below. The
later [ordinary-mode investigation](../.trae/skills/precise-volume-compatibility/references/ordinary-screen-off.md)
uses different development APK bytes and retains the user's ordinary
screen-off requirement separately from the Shizuku results in this record.

## Finding and change

The rebooted Vivo retained 50 steps and had connected accessibility and helper
services, but its ADB Shizuku server and raw key reader were stopped. Starting
Shizuku restored both connections without restarting the keeper.

The application page previously hid that stopped connection behind the ready
helper status. This update shows the selected Shizuku status and uses the existing
low-priority notification to report an unavailable screen-off listener. It clears
the warning when the reader reconnects. Volume calculation and write backends
are unchanged.

**Automatic Shizuku startup after reboot remains unavailable.** The helper's
background volume-writing connection cannot replace raw key delivery. The current
non-root implementation requires Shizuku to be restarted after reboot.

## Tested artifacts

| Artifact | SHA-256 |
|---|---|
| Main, installed and distributed 1.4.15 | `e349e8641a0833d072a0f22087c84044993aa9e6359251817f2cbcf3f9190041` |
| Installed unchanged helper 1.1 | `23852b52e4669f476050affd7476f052323c07992aa6e1e764003a80316a6d80` |
| Development host | `8b4a15a5fa0bca4ed7716516af5d157c789fbf2941ec88e92545b3d65fee3c65` |
| Development UI observer | `0dcc914f87f0dd726baa10b7fee0e5f20ef2b7c0accff4ba4b328263c944fa89` |
| Screen-off HID fixture source | `ff36b0f01a5efa17d03ac618d62d63464f7b9a83f7e8da1921abecb44c5dc0a2` |
| Actual-player HID fixture source | `bc7455b42f291b84c61811b8e0464093a0cc891036e0625cc7d3089135ddd7d7` |

The main retains the existing development certificate:
`ba65663993e5a1001eec6828d3e6250c6b12caf00afb15f058c92462804c71b5`.
The installed helper bytes differ from the older distributed 1.1 APK; this
session did not replace it.

## Results

The actual phone is Vivo V2419A / PD2419, Android 16/API 36, fingerprint
`vivo/PD2419/PD2419:16/BP2A.250605.031.A3_V000L1/compiler260529100608:user/release-keys`.
Media range 0–150, speaker route. Shizuku was explicitly selected and
ADB-started. The disposable AOSP emulator used Android 13/API 33, range 0–15.

| Verification | Result | Evidence type |
|---|---:|---|
| Release build, main/helper lint | Passed; main lint 0 errors, 3 warnings | Gradle/Android tools |
| Unit tests | 31/31 | Universal math, display and raw-input units |
| Emulator panel/readback | 13/13 | Real Android audio, synthetic controller keys |
| Emulator layout | 56/56 | Real overlay geometry/readback |
| Emulator screen off | 16/16 | Real power/audio, synthetic callbacks |
| Emulator ordinary mode | 12/12 | Real Android audio/IPC |
| Vivo 50 steps | 135/135 | External UHID; idle and controlled AudioTrack; includes Shizuku loss/recovery UI and notification checks |
| Vivo 64 steps | 127/127 | External UHID; idle and controlled AudioTrack |
| Actual Bilibili background playback | 65/65 | External UHID; exact player UID owns started media; one-second readback audit after every press |
| Real reboot and subsequent recovery | 16/16 | Changed kernel boot ID, credential storage locked, actual service states; no physical volume presses |

At 50 steps, both controlled idle/playback runs started at 121: five asleep down
presses reached 106 and the awake sixth reached 103. Ten asleep down/up presses
and reversal also passed. The 64-step runs used actual origin 121 and cumulative
Java rounding. Bursts, holds, passive presets, hidden panel, no display wake,
playback continuity and wake-lock expiry were checked.

After the real reboot, the keeper, accessibility and helper were connected
before first credential unlock, with 50 steps and native 3/150 preserved.
Shizuku/raw input were unready and the notification reported the missing reader.
After unlock, ADB-starting Shizuku reconnected both services in the same keeper
process. This verifies keeper/helper startup and subsequent reconnection.

Actual Bilibili playback, after that reboot/recovery, remained active while
asleep. Ten up presses gave 3→6→9→…→33; ten down presses retraced to 3. Every
value stayed correct after a one-second wait; player/main PIDs survived and
the panel stayed hidden. An earlier exploratory Bilibili run also passed 65
checks; the table uses the repeat with the saved generic fixture.

## Original failures retained

Four first emulator attempts emitted no test assertions because the disposable
emulator's correction setting was disabled. Errors were “Controller unavailable”,
“Volume service not running” and “Monitor did not start”. After explicit fixture
setup enabled correction at five steps and allowed the overlay, all four suites
passed. The failed JSON/output files were retained alongside the repeats.

The first real-reboot fixture could not unlock using the development observer:
instrumentation could not start while credential storage was locked. A controlled
shell UI dump allowed the existing private unlock procedure to run. The failed
attempt and the initial empty notification capture remain separate from the
later locked-state warning and successful recovery. Boot broadcasts/notification
posting arrived after the first service sample; USB/ADB also re-enumerated slowly.
No credential was copied into the project.

The first cleanup checker treated `pm path`'s status 1 for an uninstalled test
package as an error. Exact package-list checks verified both removals; the
checker error and successful verification were retained separately.

Raw evidence, captures and generated runner output were retained outside Git
under `/tmp/pvs-reboot-20261028`. The permanent fixtures and skill procedures
do not depend on those temporary files.

## Restoration and limits

Native media volume 3/150, 50 steps, selected Shizuku mode, complete accessibility
list including GKD, actual bound services, original Vivo shortcuts, USB stay-awake
2 and screen timeout 600000 ms were verified. Test playback was stopped; the
introduced host/observer APKs, external HID and device-side temporary files were
removed. Main/helper/Shizuku/raw connections remained ready, with no pending
correction or key wake lock. The installed main hash matched the delivered APK.

**Built-in physical phone buttons in Bilibili and idle remain unverified.**
The user's working pre-restart behavior is user-reported; synthetic callbacks
and external HID cannot certify it. The added physical recorder distinguishes
actual GPIO/PMIC input from app receipt and writing. Bluetooth output gain and
other routes were not retested. No blanket vendor-support claim is made.
