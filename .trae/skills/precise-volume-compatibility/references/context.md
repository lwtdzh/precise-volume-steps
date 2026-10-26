# Implementation and historical findings

## Scope and current identity

The project began as the independent `volume-steps` module inside
`yuehong-privilege-assistant`. It was renamed to `precise-volume-steps` on
2026-10-26. The app is now `精准音量` / Precise Volume Steps, main version 1.4.12,
code 17; helper version 1.1, code 2. It is a standalone Gradle project with
root app sources in `src/`, a `compatibility-helper` application and optional
`tests/host` application. Do not introduce the enclosing app's Root, firmware or
exploit modules into this project.

| Installed identity | Preserve |
|---|---|
| Main package / namespace | `roro.stellar.volumesteps` |
| Accessibility component | `roro.stellar.volumesteps/.FineVolumeAccessibilityService` |
| Keeper component | `roro.stellar.volumesteps/.VolumeKeeperService` |
| Helper package | `com.vivo.finevolumesteps.helper` |
| Helper namespace | `roro.stellar.volumesteps.compatibility` |
| Provider authority | `com.vivo.finevolumesteps.helper.volume` |
| Signature permission | `roro.stellar.volumesteps.permission.ADJUST_COMPATIBILITY_VOLUME` |
| Shared helper AIDL | `compatibility-aidl/.../IVolumeCompatibility.aidl` |
| Test host package | `roro.stellar.volumesteps.testhost` |
| Settings | Device-protected `volume_step_settings` SharedPreferences |

The helper has no launcher activity, foreground notification, overlay or boot
receiver. The main app owns the ordinary three-row volume panel and low-priority
foreground-service notification. Production APKs contain no test host.

Default settings: disabled, 30 steps, media only. Supported configured count
5–150. Stream IDs supported by both write backends are 3, 2, 4, 5, 1, 0.
Main minSdk 28, target/compile SDK 37, AGP 8.13.2, build tools 36.0.0,
Gradle 8.14, Java 21. Preserve the `compileSdk { version = release(37) }`
syntax. Offline builds avoided an earlier dependency SSL/metadata stall.

## Vivo background finding: verified, firmware-specific

Connected test phone identified by ADB as Vivo V2419A / PD2419, Android 16;
its media range was 0–150. Earlier user conversation also mentions Android 13+
and other Vivo models; do not substitute those models for the observed device.
Obtain the exact current fingerprint before further experiments.

Direct main-app `AudioManager.setStreamVolume` worked in the foreground but was
ignored in the background on this phone despite `MODIFY_AUDIO_SETTINGS`.
The decompiled firmware's
`VivoAudioVolumePolicy.isVolumeChangedPkgInWhiteList` accepted names beginning
with `com.vivo` or `com.bbk`, without requiring the OEM certificate in this check.
Matched own-UID probes, otherwise equivalent and independently signed, found:

| Probe package | Background result |
|---|---|
| `com.vivo.fvsresearch` | Six writes accepted, alternating 73 and 72 |
| `research.volume.control` | Three decrement attempts ignored, actual stayed 73 |

Those probe packages were removed afterward. They were experimental controls,
not apps users must install.

The production helper therefore uses the actual installed package
`com.vivo.finevolumesteps.helper` and its own app UID / AudioManager context.
It does not impersonate an OEM certificate or claim Vivo affiliation. Retain the
visible independent-app label. Merely passing an OEM package string from the
main UID is not the established production mechanism.

The first signature-protected bound-Service transport repeatedly suffered
binding death. It was replaced by a provider returning a scoped Binder.
However, provider startup remained unavailable until **关联启动** was enabled
for the helper. Do not attribute success to the transport change alone.

`CompatibilityVolumeBridge`:

- Connects only for manufacturer `vivo` or `iqoo` (iQOO is attempted, not an
  established physical-device certification).
- Verifies the installed helper's certificate against the main application.
- Acquires and retains an unstable `ContentProviderClient`, calls `connect`,
  extracts the `volume` Binder and links a death recipient.
- Holds separate connection and adjustment generations; ignores stale work.
- Reconnects after death or package lifecycle changes, retries connection failures
  with increasing delay capped at 60 seconds.

`CompatibilityVolumeProvider`:

- Enforces the signature permission in `call`.
- For each AIDL adjustment, checks main UID and matching signatures again.
- Clears Binder calling identity before the helper's own audio call and restores
  it in `finally`.
- Accepts only supported streams, clamps to native minimum/maximum.
- Checks positive remaining expiry ≤1000 ms and expected current volume twice
  before writing. Main controller normally gives a 500 ms request expiry.
- Writes `setStreamVolume(stream, safe, 0)` and returns actual Android readback.
- Returns -1 for stale/expired and -2 for uncertain failure. Does not repeat an
  uncertain write.

Backend priority in `VolumeAdjustmentController.apply`:

1. Ready, explicitly enabled Shizuku.
2. Ready independent compatibility helper.
3. Main-UID public AudioManager fallback.

A failed write is not a new request to force a different backend to write again.
Preserve bounded confirmation and actual failed-result display.

## Shizuku remains separate

`ShizukuVolumeBridge` binds `VolumeShellService` only with a running v11+
Shizuku, permission granted and UID 2000 (ADB/wireless debugging). Root-started
Shizuku is deliberately rejected by the current implementation.
The shell user service reflects Android's audio Binder and uses
`com.android.shell`. Its real shell UID is checked at construction.
It shares expected-current, expiry, supported-stream and no-repeat safeguards.

Keep `ShizukuVolumeBridge.java`, `VolumeShellService.java` and
`src/main/aidl/.../IVolumeShell.aidl` unchanged when fixing unrelated normal-mode
issues. Shell-service APIs are reflected and remain version-sensitive.
Revoking authorization can force-stop the app and cause some systems to disable
accessibility; reopening the main application does not prove it reconnected.

An early conversation explored a Shizuku startup toggle and
`WRITE_SECURE_SETTINGS`. Those instructions were version-specific and do not
establish reliable non-root auto-start. The current app's normal/helper paths
require no such Shizuku grant. Never promise non-root Shizuku survives reboot.

## Startup and background process

`BootReceiver` handles BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, USER_UNLOCKED and
MY_PACKAGE_REPLACED. It records action/time in device-protected preferences and
starts the keeper only when enabled. The keeper is a special-use foreground
service with START_STICKY. Its controller owns helper/Shizuku connections.
The helper is reconnected through the keeper; it does not run an independent
root/shell boot script. Force-stopping the app requires explicit reopening.

Vivo separately controls startup/background execution. On the observed phone,
the system's startup database initially lacked an entry for the main app.
Standard RECEIVE_BOOT_COMPLETED permission and registered receiver did not make
the UI entry appear. Bilibili had a startup UI entry even without that standard
permission. After preserving settings, a full uninstall/reinstall rebuilt the
record and exposed self-start; overwrite install and keep-data uninstall did not.
This is a historical device-specific recovery, not a default installation step.
Full uninstall clears settings; never use it without a justified authorized need.

Helper force-stop → automatic reconnection and force-stop-both → explicit main
reopen → reconnection were tested in 1.4.8. A real Vivo reboot was not verified.

## Screen off and locked: still partially unresolved

Firmware inspection found accessibility input filtering requires Android's
pass-to-user flag (`1073741824`). Window policy can route off-screen volume
directly to audio/media. A volume-up release may arrive without its press.
`FineVolumeAccessibilityService` always returns false from onKeyEvent; keys
continue to Android. Accessibility is configured for filtering, not screen reads.

1.4.10 added:

- Non-reference-counted PARTIAL_WAKE_LOCK, acquired only for a received key,
  timeout `700 + 1000 + 300 = 2000 ms`. CPU only; never wake the display.
- `recoverKeyUp` only while noninteractive or keyguard locked, with key age
  ≤700 ms, no active gesture, and recent native change matching direction.
- Previous native readback / change timestamps reconstruct the pre-key origin.
- Passive changes alone never authorize recovery.
- Panel display suppressed while noninteractive.

Eight emulator checks passed with actual power state/audio but synthetic
controller callbacks. They verify full-key correction, missing-down recovery,
hidden panel, no display wake and wake-lock expiry.

**Physical Vivo screen-off/locked buttons remain unverified.** Suppressing both
down and up leaves no key evidence and the current implementation cannot correct.
The helper package does not fix this. Bluetooth route/endpoints and actual
Vivo reboot recovery also remain unverified. Record these as outstanding,
not as fixed based on a callback or emulator test.

## Reversible universal spacing

`VolumeStepHistory` is the production gesture planner;
`VolumeStepQuantizer` is an older zero-based utility and its tests remain.

```text
range = max − min
intervals = max(1, min(configuredSteps, range))
target(position) = clamp(origin + Java Math.round(position × range / intervals),
                         min, max)
```

Origin comes from real readback. Confirm the plan only after Android equals the
target. Reuse it only when starting volume equals the last confirmed target and
min/max/steps agree. External changes and settings/accessibility cancellation
clear the path. Pending opposite keys preserve the original full gesture.

For max 150 and 50 steps: origin 21 gives 21,24,27,30; origin 22 gives 22,25,28.
For max 150 and 64 steps: origin 74 gives 74,76,79,81; retracing returns exactly.
Use Java rounding for negative positions; do not replace it with another
language's tie-to-even rounding when constructing expectations.
Limits can clip the final increment. Repeated presses at a limit do not move
the logical cursor. More configured intervals than native integer range do not
produce finer actual values.

The 1.4.10 visited-value-list implementation inserted an off-grid origin as an
extra stop. Phone logs proved 21→22→24, displaying 14→15→16 despite 50 steps.
1.4.11 replaced it with origin plus cumulative rounded offsets. Never reintroduce
visited-value stops or repeated rounded single-increment accumulation.

## Display semantics and smooth updates

`VolumeDisplay.percent` is rounded `boundedCurrent × 100 / maximum`, independent
of configured steps. Percentage uses zero-to-maximum; the second row maps
current-minus-minimum over native range to configured steps. They are distinct
displays, not the planner's private cursor.

Panel: percentage header, configured-position row, direct-system row, then
exactly **one** graphical bar. A measured status row retains its height.
Fixed bounds, single-line text, tabular numerals, immediate progress updates
and no window animation avoid movement/clipping. `%` in formatted XML is `%%`.

`readSystemVolume` independently calls `getStreamVolume(displayedStream)` and
`getStreamMaxVolume(displayedStream)`. Never supply a target or configured count
as either number. Failed read displays explicit 读取失败.

1.4.10 refreshed this row every 100 ms while upper values were held; the user
reported flicker/trembling as the native coarse adjustment appeared.
1.4.11 removes refresh callbacks on hold, skips pending refreshes, and reads
again once success/failure settles. Preserve the last **real** sample during the
pending phase. Equal text is not rewritten. The first panel is hidden until
settlement. A rejected request for 68 with actual 71 must eventually show 71.

Passive broadcasts/polling never reopen or extend the panel and never authorize
new writes. Only key evidence plus a matching native transition does so.
DETECT_MS=35, KEY_TIMEOUT_MS=700, WRITE_TIMEOUT_MS=1000, POLL_MS=250;
panel dismissal delay=1200 ms. Keep expiry/readback cancellation bounded.

## Historical verification, not current blanket certification

| Version / run | Recorded result |
|---|---|
| 1.4.8 Vivo helper, idle and silent playback | 75/75 each |
| 1.4.11 unit tests | 20/20 |
| 1.4.11 Vivo 50 steps first run | 64 passes / 1 failure; suite stopped |
| 1.4.11 Vivo 50 steps full repeat | 87/87 |
| 1.4.11 Vivo 64 steps | 87/87 |
| 1.4.11 emulator Shizuku, native max 15, steps 5 and 7 | 85/85 each |
| 1.4.11 final/readback/frame geometry | 13/13 |
| 1.4.11 overlay layout/readback | 56/56 |
| 1.4.11 screen-off emulator | 8/8 |
| 1.4.11 live unlocked Vivo external HID | 21→24→27→30→27→24→21 |
| 1.4.12 rename | Builds/lint, 20 unit tests, APK labels and signatures |

First failed Vivo run: “background press 17 fine correction”; key and native
24→14 observed, no subsequent correction write. Cause undiagnosed.
The successful repeat does not explain it. Preserve this incident for future
intermittent-failure diagnosis.
Frame samples upper/system rows both `[8,8,11,15]`; last 15 was actual failed
readback, not requested target. Row bounds stayed fixed.

Instrumentation used synthetic controller callbacks and actual audio/IPC.
Final HID was external Android input routing, not physical phone-button coverage.
After testing, volume/steps restored, GKD accessibility retained, companion
connected, Shizuku disabled, and USB stay-awake enabled with ten-minute timeout
unchanged. This describes the historical session, not a required device state.

Earlier intermediate evidence lived in `/tmp/fvs-*`: decompiled framework files,
installer/accessibility scripts, logs, probes and HID binary. Those paths may
disappear; the findings and permanent tests are preserved here. Do not require
those files to build or understand the app.
