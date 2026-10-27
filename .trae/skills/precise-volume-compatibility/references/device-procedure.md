# Installation and device diagnosis

## Select and preserve a phone

Use `adb devices -l` and an explicit `adb -s SERIAL` for every call. Never infer
the target from the first device. Record model, manufacturer, SDK and full build
fingerprint, installed main/helper versions and certificate, backend selection,
configured steps/scope, actual stream min/max/current, route/playback state,
notification/overlay/background permissions, screen/keyguard state, timeout,
USB stay-awake and the complete enabled-accessibility list.

Capture service diagnostics without changing settings:

```sh
adb -s SERIAL shell dumpsys activity service \
  roro.stellar.volumesteps/.VolumeKeeperService
adb -s SERIAL shell cmd media_session volume --stream 3 --get
adb -s SERIAL shell settings get secure enabled_accessibility_services
adb -s SERIAL shell settings get global stay_on_while_plugged_in
adb -s SERIAL shell settings get system screen_off_timeout
adb -s SERIAL shell getprop ro.build.fingerprint
```

Service dump's `version=` currently prints package name, not versionName.
Use `dumpsys package` for version code/name. An enabled accessibility setting
does not prove a connected service; verify `accessibility connected=true`.
Keep snapshots outside Git; device logs can include personal data.

## Install the correct APKs

The production main and helper must share one certificate. The main package
checks the helper's certificate; the provider additionally requires a signature
permission. Distributed versions 1.4.12/1.1 are signed with the same existing
local development key. A fresh machine's default Android debug key is different.
Do not commit private signing material or silently uninstall to bypass a mismatch.
For testing fresh builds, build/install both with the same test certificate;
an upgrade of the existing phone needs its original certificate.

| Backend | Install / setup |
|---|---|
| Direct AudioManager | Main only, accessibility and background permissions |
| Vivo helper | Main + helper, main self-start/background, helper 关联启动 |
| Shizuku | Main + external Shizuku, ADB-started shell UID 2000, explicit grant |

Helper label: `精准音量兼容助手（独立应用）`. It has no launcher icon.
Main entry **设置独立兼容助手 → 打开助手权限设置** opens its system settings.
Ready diagnostics should show `companion=supported=true, ready=true`;
test with `Shizuku=enabled=false` to establish helper behavior independently.
No WRITE_SECURE_SETTINGS grant to Shizuku is needed for this backend.

The test-host APK is development-only and must never be presented as a third
required user app. Do not leave research probes, HID devices or test apps behind.

## Vivo installer acknowledgement through ADB

The user authorized installation confirmation through ADB taps rather than
requiring a manual phone confirmation. For an APK the user authorized:

1. Start `adb -s SERIAL install -r APK` and retain its output/exit status.
2. Inspect the current installer UI. Restrict candidates to the observed
   package installer; do not blindly tap fixed coordinates or unrelated dialogs.
3. The observed risk checkbox was
   `com.android.packageinstaller:id/deleted_file_state_cb`. If unchecked,
   tap the midpoint of its current bounds, re-read UI, then tap enabled
   **继续安装** (or the actual installation confirmation).
4. A USB connection-mode dialog previously obscured the installer; inspect
   the dialog and dismiss it deliberately before retrying.
5. Bound the wait (historically 120 seconds), preserve failure output and verify
   installed version/label/APK hash. `adb install` must actually report Success.

Ordinary `uiautomator dump` can temporarily suppress this accessibility service.
Use the test host's `DumpUiInstrumentation` with
`FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` for runtime behavior inspection.
If using ordinary dumps for installer recovery before a host is available,
check accessibility connectivity afterward.

ElementTree nodes with no children are false in boolean tests:
use `node is not None`, not `if node`, when searching leaf UI nodes.

## Restore accessibility without disturbing other services

Package replacement, instrumentation and Shizuku revocation have left the
service enabled but disconnected. On the observed Vivo, rewriting the secure
setting alone did not repair a faulted connection.

Open accessibility Settings, locate **精准音量服务** (historical label:
**细粒度音量服务**), inspect its actual switch bounds, toggle only this service
off/on if needed, and handle its specific allow/close dialog.
Do not replace `enabled_accessibility_services` with a single app; the earlier
phone also used GKD. Compare before/after lists and then verify actual keeper
diagnostics. If a helper update was made, also verify the provider reconnected.

Check whether the component is enabled before toggling it. If it is absent from
the list, adding it through the native UI legitimately changes that list.
Compare against the previous other services plus the authorized main component;
an unchanged-list assertion applies when reconnecting an already enabled service.
Do not report a disconnected service as the cause of an earlier failure without
matching evidence from that failure.

On the observed Vivo, `dumpsys accessibility` uses multiline
`Bound services:{Service[label=GKD, ...], Service[label=精准音量服务, ...]}`.
The following `Enabled services` field contains package/component IDs.
Parse the full bound-services section up to the next field; verify both labels,
the expected component set and the keeper's `accessibility connected=true`.
Do not search a single bound-service line for package names.

## UI observation and blocked Settings

Use the host component
`roro.stellar.volumesteps.testhost/.DumpUiInstrumentation` with the separate
`roro.stellar.volumesteps.uitools` target installed. Read the returned
`INSTRUMENTATION_RESULT: path=` rather than hardcoding a host-specific XML path.
Changing the target changes the external-files directory. Inspect private
unlock helpers for this dependency without exposing their credentials.
Keep the observer until the final UI/accessibility/shortcut checks are finished,
then remove introduced host/observer/probe packages.

For a reused playback activity, use `am start -f 0x20000000` so new extras reach
`onNewIntent`. A plain start can merely bring the task forward and leave its
previous play/stop state intact. Confirm real AudioTrack activity after launch
and after backgrounding, not the requested `play=true` extra.

If Settings appears to lack the expected service or shortcut, inspect the active
window before more scrolling. A Vivo USB connection chooser from
`com.vivo.frameworkui` obscured both Settings and installation in this session.
Dismiss its inspected **取消** button when appropriate, then reopen the intended
page. Do not interpret the obscured page as a missing app permission.

For Vivo volume shortcuts, snapshot both Settings values and the selected
camera option. Tap the inspected inner native switch, not the larger preference
row; the row can advertise `clickable=true` without changing the setting.
Allow the page to settle, then re-read Settings. Compare deserialized snapshots
by normalized values: tuples returned by Python become lists in JSON.

## Resume after interruption

Check completed result files and surviving host/HID/instrumentation processes.
Snapshot the current phone before modifying it. Record native volume, full
preferences, accessibility enablement/connectivity, raw/write readiness, screen,
playback, route, shortcuts and actual APK hashes. A stopped terminal does not
prove Android-side fixtures or their cleanup finished.

Distinguish known fixture leftovers from newer phone settings. If the last
completed test restored volume 0 but the current phone reads 129, preserve 129
unless evidence identifies it as an unfinished test write. Those numbers are
an example from this session, not future preset/restoration values.
Record an absent or frozen keeper before reopening; do not call it connected
because its accessibility component appears in Settings.

For a backend matrix, skip a completed cell only after checking its exact main
APK, fixture identity, backend, step count, complete assertions and error-free
completion. Restore all preferences in cleanup, not only steps or backend.
Remove only fixtures introduced for the session and confirm no active playback,
pending correction, overlay or key wake lock remains.

## Screen and installation record

The user previously asked to keep the phone awake while away. In that session,
USB stay-awake was set to 2 and screen timeout retained at 600000 ms. These are
historical values; snapshot the actual phone and restore its values after tests.
Stay-awake must be temporarily disabled for genuine screen-off tests; record
`screen interactive=false` and keyguard state rather than assuming a sleep
key succeeded. Do not lock an unattended personal phone unless the relevant
test is authorized and its recovery is available.

If Vivo's self-start UI entry is missing, inspect standard permissions and the
system startup record before changing code. A historical full reinstall rebuilt
the record; overlay installs did not. Full uninstall erases settings and is a
last-resort, separately justified operation with preservation/restoration.

## Another vendor

1. Establish whether failure is missing key delivery, rejected write, stale
   expected value, disabled service, connection/startup failure or display-only.
2. Compare native range/route and foreground/background behavior using the same
   current state. Capture actual readbacks, never infer success from a return.
3. Investigate startup permissions and audio caller policy independently.
   Use system/component evidence and matched probes; do not guess an OEM prefix.
4. Record normal/helper/Shizuku behavior separately with physical buttons while
   foreground, home, other app, locked, screen-off, active/paused media, speaker
   and Bluetooth. Follow the regression skill for repro cases.
5. Prefer a small optional adapter selected from evidence. Preserve universal
   math, readback, reversibility, no-extra-window behavior and existing Shizuku.
6. Add a profile with fingerprint/date/versions/backend/permissions/result/input
   type and limitations. A manufacturer allow check in code is not certification.
