# Implementation and historical findings

## Scope and current identity

The project began as the independent `volume-steps` module inside
`yuehong-privilege-assistant`. It was renamed to `precise-volume-steps` on
2026-10-26. The app is now `精准音量` / Precise Volume Steps, main version 1.4.15,
code 27; helper version 1.1, code 2. It is a standalone Gradle project with
root app sources in `src/`, a `compatibility-helper` application and optional
`tests/host` and `tests/ui` applications. Do not introduce the enclosing app's
Root, firmware or exploit modules into this project.

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
| UI observer target package | `roro.stellar.volumesteps.uitools` (test only) |
| Helper routed-write test package | `com.vivo.finevolumesteps.helper.test` (androidTest only) |
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

For the 2026-10-28 post-reboot report, unavailable Shizuku raw input and the
helper status that hid it, read [reboot screen-off findings](reboot-screen-off.md).
The user's setting remained 50. Main/helper startup and screen-off key-reader
startup must be reported separately.

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

### 1.4.13: native-jump inference, removed in 1.4.14 and restored narrowly later

Automated ADB diagnosis on the V2419A separated two layers:

- A shell-created UHID consumer device is **not a wake source**. Raw `getevent`
  on its node captured `KEY_VOLUMEDOWN DOWN/UP` while the display was asleep,
  yet InputReader/`VivoInputPolicy` dropped the event before both audio and the
  accessibility filter: native volume stayed unchanged and the app logged no
  key. Shell cannot write `/dev/input/event*` (SELinux), enable sysfs wakeup,
  or use `UHID_SET_WAKEUP`, so no adb-only input reproduces the physical
  wake-key input path. Treat this as a harness limitation.
- When the physical key is routed straight to audio while asleep, the native
  coarse jump occurs without a pass-to-user event, so accessibility never sees a
  key. `VolumeAdjustmentController.inferLockedOffHardwareKey` then treated the
  observed media transition as key evidence only when:
  - the display is non-interactive or the keyguard is locked,
  - audio mode is normal and A2DP is inactive (headset/AVRCP and calls stay
    with the system),
  - the jump is one or two hardware coarse steps,
    `ceil((max-min)/15)` ≤ |delta| ≤ `2*ceil(...)`.
  It plans from the pre-jump value through the same anchored history, holds the
  existing 2 s partial wake lock, keeps the panel hidden, and requires readback.
  The unlocked display still ignores passive changes; this is regression tested.
- `dispatchCorrection` waits up to about 600 ms for a supported companion
  binder after helper-process recreation instead of using the main UID, whose
  background write the firmware ignores. Do not remove the bound when no
  companion is available: foreground direct writes and non-Vivo devices need it.

Real-phone verification on 2026-10-26 used a helper-UID test instrumentation to
make the firmware-equivalent routed jump while `input keyevent 223` had put the
display to sleep: 60→50 inferred and corrected to 57; 60→70 inferred and
corrected to 63 (50 steps, range 150); screen stayed Asleep, no panel attached.
The physical wake-key input path remains distinct and was not directly pressed.
Bluetooth route behavior and a real reboot remain outstanding.

### 1.4.14: explicit raw volume-key input through ADB Shizuku

The user's idle repro (81%, five down presses asleep, wake and one down:
expected 69%, observed 79%) showed that a native-jump heuristic cannot handle
keys that firmware drops before audio. The heuristic was also unsafe: an
external slider or route change of the same size could authorize a write.
It was removed in 1.4.14. The final 1.4.15 ordinary-mode work later restored a
narrower playback-only form after reproducing 1.4.13's simulated playback
correction. That simulation does not reproduce the user's reported physical
success before reboot; the task-start package record identifies 1.4.14. It
requires a ready Vivo helper, a truly noninteractive display, active playback,
normal audio mode, a non-wireless route and a one- or two-step coarse media
transition. Idle uses delivered accessibility callbacks instead. A programmatic
coarse preset with the same shape remains observationally ambiguous; see
[ordinary screen-off findings](ordinary-screen-off.md).

`VolumeKeyShellService`, `IVolumeKeyShell`, `IVolumeKeyListener` and
`ShizukuKeyBridge` form a separate optional read-only backend. Existing
`ShizukuVolumeBridge`, `VolumeShellService` and `IVolumeShell` are unchanged.
UID 2000, explicit Shizuku selection and authorization are required.
Bounded `getevent -lp` discovers dedicated volume/power nodes; keyboard, touch,
relative-motion and switch devices are excluded. Per-node blocking readers
pass only parsed volume/power events, never raw input text. A standalone power
node alone cannot indicate volume readiness. Hotplug requests are rescanned,
errors disable readiness, and Binder death/disable closes readers.

A matched volume down/up pair must be fresh (250 ms); standalone release,
stale input, opposing held keys, power chords and holds beyond 10 s cancel.
Raw input owns noninteractive presses when available to avoid duplicate
accessibility correction. Normal audio mode is required. Bluetooth A2DP was
incorrectly excluded in an intermediate build; a real-phone reader probe
showed fresh events on the connected Bluetooth route, and that guard was
removed. Key presses then passed through Shizuku with independent system
readback. Releases authorize correction; an absent native change is valid
for this explicit raw path. Snapshot copies preserve the original rounding
path across pending acknowledgements.

An intermediate version-code-23 playback run missed the third asleep press:
the system changed 115→105 before the raw callback's snapshot, while the poll
still held 115. The controller incorrectly planned from 105 and wrote 102
instead of 112. `RawVolumeBaseline` now handles both an unread native change
and an already-observed change after the key, using the preceding real sample.
Passive presets observed before the key become the new origin. Four unit
tests record these orderings; the fresh-input timeout remains 250 ms.

The next version-code-25 rapid run selected 112 correctly, but a late native
adjustment changed it to 122 after the write. This was not stale raw input:
every raw event arrived in 1–4 ms. Version code 26 waits at least 300 ms after
the last release before correction, then also waits for native transitions
to settle. This accommodates the firmware's delayed key dispatch and keeps
pending burst directions together. Native writes still have the existing
expected-current and expiry checks; this adds no retry of a failed write.

The initial version-code-26 64-step playback burst failed after its test
player had stopped: UI inspection during secure unlock targeted the host,
which restarts its process. Retaining the main PID alone missed this.
Audio diagnostics also reported background-playback hardening; adding focus
alone did not fix the stopped player. UI inspection now targets a separate
test-only observer application; component names used by external unlock
helpers stay unchanged. The fixture requires the player PID and active
playback to survive wake/unlock and checks playback again around bursts.
These are fixture corrections, not changes to the production playback behavior.
The next idle-to-playback run stopped at the audio-state assertion before
playback key testing. `am start` had only brought the existing host task forward,
so the new play extra never reached `onNewIntent`. The HID harness now uses
`FLAG_ACTIVITY_SINGLE_TOP` for player starts and cleanup. Keep the audio-state
assertions; task visibility alone does not establish active playback.

The V2419A's dedicated nodes are `gpio-keys` (volume up) and `mtk-pmic-keys`
(volume down + power). External UHID events were observed and corrected while
truly asleep, with no hidden media session, display wake or invisible activity
inside the app. The development host provides actual playback only for its
playback test. The first version-code-23 rapid run woke the secure camera:
ADB settings writes had silently failed. The fixture now disables the
long-press and double-press shortcuts through the native UI, snapshots the
camera option and verifies all original values at restoration. The original
failed runs remain outside Git, separate from later repeats.
Built-in physical-button delivery and real reboot remain
untested; external HID success is recorded separately.

Final code-26 evidence: 31 unit checks and release lint passed; 97 emulator
checks passed. Complete Vivo external-HID runs passed 83 checks at 50 steps
and 83 at 64 steps, with actual playback asserted across unlock and bursts.
Shizuku backend runs passed 85 checks each at 50/64; companion runs passed
87 each. See `dist/verification-1.4.14.md` for hashes and failed-run history.
At 50 steps, both idle/playback runs gave 121→106 after five asleep down
presses, then 103 after the awake sixth press: 81%→69%. All phone test
packages/probes were removed and main/helper/Shizuku connectivity restored.
The helper source stayed at 1.1; the newly built installed helper's byte hash
differs from the historical distributed 1.1 APK and is recorded separately.

On resuming the interrupted backend matrix, both completed Shizuku runs had
restored native volume 0. The later phone readback was 129/150 and only GKD
remained enabled in accessibility Settings. The cause of those changes was
not established. The keeper initially reported that its package was frozen;
after reopening, queued raw events had ages far beyond the freshness limit.
Preserve the newer volume, inspect connection state, and reject stale volume
events instead of replaying them as new presses. Re-enable the main service
through the native UI when restoration is authorized, retaining other services.
The final cleanup preserved 129/150 and verified main/GKD connectivity.

The recovery also exposed two validation mistakes. A JSON snapshot stores
Settings pairs as lists while the Python helper returns tuples; compare their
values after normalization. Vivo's `dumpsys accessibility` lists bound services
by labels over multiple lines, while enabled services contain package/component
IDs. Parse these separate fields and verify connectivity; searching for package
names on one bound-service line produces a false failure.

## Bluetooth volume indices and audible gain

The app adjusts the current media-volume index, `STREAM_MUSIC = 3`, through
`AudioManager.setStreamVolume`; Shizuku calls the corresponding
`IAudioService.setStreamVolume`. Android persists output-specific values in
`Settings.System`. Verified names on the Vivo are `volume_music_bt_a2dp`
(Bluetooth A2DP), `volume_music_speaker` (speaker), and `volume_music`
(base/default). Read current volume through AudioManager or the audio service:
a saved value for another output is not the active route's current volume.
The configured count is app-private SharedPreferences `volume_step_settings`,
key `steps`. At native 0–150 and 50 configured intervals, a press selects an
offset of 3 native units; the app does not alter the system's native maximum.

The user reported equal audible intensity at 58%, 61% and 63% with Absolute
Volume enabled. Android normally sends unattenuated audio plus a volume command
in that mode, and the headset applies gain. Classic AVRCP expresses 0–127,
but a headset can have fewer supported gain levels. Bluetooth SIG's low-
resolution interoperability test includes a 50% command with a 40% reported
headset setting. Ordinary 150→127 scaling distinguishes the reported percentage
values, so the protocol's 127 maximum alone does not explain that plateau.
Coarse headset/OEM gain mapping is plausible; ignored commands or small audible
differences require separate investigation.

Read-only diagnostics during this discussion showed the connected OPPO Enco X3
with `AbsVol=false`. The enabled-state mapping and sound pressure were not
captured. Do not record an OPPO plateau cause or an on/off audible pass from
that snapshot. With Absolute Volume disabled, phone-side attenuation generally
allows finer gain adjustments; distinct audible levels still depend on the
phone's volume curve and headset processing.

The AOSP developer-option property is `persist.bluetooth.disableabsvol`;
`true` disables Absolute Volume. The app does not set it. Vivo's per-device
preference location was not established, and an unset property does not prove
that the active device uses Absolute Volume. Check AudioService support and the
current device's AvrcpVolumeManager map after a connection change. If the usual
Bluetooth dump is empty, the observed adapter supports:

```sh
adb -s SERIAL shell dumpsys activity service \
  com.android.bluetooth/.btservice.AdapterService --print
```

Keep that full dump outside Git; it can list other paired devices and addresses.
Capture outgoing commands, reported headset volume, constant audio and measured
output when diagnosing an audible plateau. Keep phone readback and acoustic
measurements separate.

Sources: [Android Absolute Volume behavior](https://source.android.com/docs/core/connect/bluetooth/services#absolute-volume-control),
[Bluetooth SIG test suite, Appendix B §7.1](https://files.bluetooth.com/wp-content/uploads/dlm_uploads/2025/02/AVRCP.TS_.p23.pdf?download=1#page=202),
and [Bluetooth accessory volume guidance](https://learn.microsoft.com/en-us/windows-hardware/design/accessory-guidelines/bluetooth-accessory-guidelines/bluetooth-accessory-guidelines-classic-audio#volume-hands-free-profile-hfp--audiovideo-remote-control-profile-avrcp).

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
new writes. Accessibility correction requires a matching native transition;
the optional raw backend can act on a fresh matched off-screen key release.
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
| 1.4.13 screen-off emulator | 14/14 (includes routed-jump inference and unlocked guard) |
| 1.4.13 real Vivo asleep routed writes | down 60→50→57, up 60→70→63; screen stayed off, no panel |

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
