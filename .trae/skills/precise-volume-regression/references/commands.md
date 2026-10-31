# Standalone build and automated evidence

Run from the repository root. Python utilities require Python 3.9+; Android
builds require JDK 21, Android platform 37.0 and build-tools 36.0.0.
Set ANDROID_HOME/JAVA_HOME for the current machine or provide a local SDK path.
The project does not require the former enclosing repository.

Install the exact SDK package name; `platforms;android-37` is not the package used
by this release DSL:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'
```

```sh
./gradlew -PvolumeStepsE2E=true \
  :assembleRelease :lintRelease :testReleaseUnitTest \
  :compatibility-helper:assembleRelease :compatibility-helper:lintRelease \
  :precise-volume-steps-test-host:assembleDebug \
  :precise-volume-steps-test-ui:assembleDebug
```

Use `--offline` after dependencies are available if network metadata stalls.
Exact local configuration used in the earlier session:

```sh
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
bash gradlew --offline -PvolumeStepsE2E=true \
  :assembleRelease :lintRelease :testReleaseUnitTest \
  :compatibility-helper:assembleRelease :compatibility-helper:lintRelease \
  :precise-volume-steps-test-host:assembleDebug \
  :precise-volume-steps-test-ui:assembleDebug
```

Outputs:

```text
build/outputs/apk/release/precise-volume-steps-release.apk
compatibility-helper/build/outputs/apk/release/compatibility-helper-release.apk
tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk
tests/ui/build/outputs/apk/debug/precise-volume-steps-test-ui-debug.apk
build/test-results/testReleaseUnitTest/TEST-*.xml
build/reports/lint-results-release.xml
```

Main/helper currently use the local Android development certificate for release
to preserve previously supplied APK upgrades. Build all apps on the same machine
or with the same explicitly managed signing key. Compare certificates before
updating a phone. Never export private keys into Git. Release bytes may differ
from historical APK bytes even at the same version when build configuration
changes; record actual tested hash, not only versionName.

## Existing unit tests

Current count is 35 (may grow):

- `VolumeDisplayTest`: independent percent/configured-scale mapping.
- `VolumeStepQuantizerTest`: older grid utility behavior.
- `VolumeStepHistoryTest`: actual planner spacing/reversal. Native minima 0/1/5,
  ranges 7/15/50/100/120/150/255, every configured count 5–150; every interior
  origin on selected uneven/even ranges; immediate opposite keys; failed and
  external writes; no historical 14→15→16 stops; retracing/revisiting.
- `RawVolumeKeyTest`: dedicated-node selection, separate power input, explicit
  event parsing, fresh matched release, stale/unmatched keys, chords and bounded holds.
- `RawVolumeBaselineTest`: audio policy writes before raw callbacks/polling,
  already-observed transitions, rebasing from passive presets, failed reads.
- `AccessibilityVolumeBaselineTest`: delayed callbacks with already-observed
  transitions, original event time, direction/freshness checks, passive changes
  and failed readbacks. Accessibility never adopts the raw fallback.

## Instrumentation

Install a main APK and same-signed test-host on the chosen device. For a real
Vivo/helper run install the same-signed helper and allow 关联启动. Main correction
must be enabled. Overlay tests need either connected accessibility or overlay
permission. Inspect both `enabled=true` and actual service connection.
Shizuku backend requires a running root or ADB server and prior app grant.

Fixtures mutate native volume and can interrupt the main process/accessibility.
They restore volume and selected test settings in `finally`, but still compare
before/after snapshots and restore the physical service connection.

```sh
adb -s SERIAL install -r build/outputs/apk/release/precise-volume-steps-release.apk
adb -s SERIAL install -r tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk
adb -s SERIAL install -r tests/ui/build/outputs/apk/debug/precise-volume-steps-test-ui-debug.apk
```

For lit-keyguard external input, use `tests/lockscreen_keys.py` on a selected
phone. Supply an existing authorized HID key tool and passive preset dex.
Start awake/unlocked with playback paused; the fixture preserves preferences,
volume and other accessibility services and creates controlled playback.

```sh
python3 tests/lockscreen_keys.py --serial PHONE_SERIAL \
  --keys /tmp/authorized-hid --input-kind uhid --preset-dex /tmp/preset/classes.dex \
  --backend ASSISTANT --steps 50 --output /tmp/pvs-locked-assistant-50
```

Use `--input-kind uhid` for emulated external keys, or `usb-hid` for a real USB
HID endpoint; do not report a `/dev/uhid` tool as a physical external keyboard.
Repeat ASSISTANT/SHIZUKU/ROOT with even and uneven counts. A secured phone needs
an explicitly authorized private `--unlock-command`; keep its implementation,
credentials and raw UI captures outside Git. The command is never retained in
reports. No unlock helper is needed on an unsecured disposable device.
Helper status refresh can take two seconds; the fixture waits for settled
health readback. External HID does not establish built-in physical delivery.

Vivo may require observed installer acknowledgement taps; use the compatibility
skill's device procedure. Do not interpret a hanging installer as completed.

For locked raw-input latency, run the assistant-only synthetic callback fixture
on the verified helper phone, with correction enabled, media-only scope and
playback paused. It creates a real lit keyguard and restores native volume.
It leaves a secured phone locked; use the authorized private unlock procedure
and reconnect accessibility afterward. Keep the original failing APK run.

```sh
python3 tests/run_instrumentation.py --serial PHONE_SERIAL \
  --suite locked-raw-timing \
  --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --host-apk tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk \
  --output /tmp/pvs-locked-raw-timing
```

This verifies release/readback/panel-attachment timing with real helper IPC,
late overwrites, consumed keys, pending reversal and unchanged off-screen
settling. It does not measure built-in physical delivery or actual window
visibility. Use `lockscreen_keys.py` and a separate physical recording for those.

The runner parses test JSON and final instrumentation status, records installed
main/host/helper APK hashes, Java fixture hash, before/after device snapshots,
raw output and errors outside Git:

```sh
# Physical Vivo backend test, synthetic controller callbacks with real helper IPC
python3 tests/run_instrumentation.py --serial PHONE_SERIAL \
  --suite backend --backend companion --steps 50 --output /tmp/pvs-phone
python3 tests/run_instrumentation.py --serial PHONE_SERIAL \
  --suite backend --backend companion --steps 64 --output /tmp/pvs-phone

# Disposable emulator only for rejected-write/frame and screen-off fixtures
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite frames --output /tmp/pvs-emulator
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite layout --output /tmp/pvs-emulator
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite screen-off --output /tmp/pvs-emulator
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite ordinary --output /tmp/pvs-emulator
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite backend --backend shizuku --steps 5 --output /tmp/pvs-shizuku
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite backend --backend shizuku --steps 7 --output /tmp/pvs-shizuku

# Optional Root: disposable rootable emulator or a separately authorized Root phone
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite modes --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --host-apk tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk \
  --output /tmp/pvs-root
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite backend --backend root --steps 7 \
  --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --host-apk tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk \
  --output /tmp/pvs-root
```

The main correction switch must be enabled before backend fixtures; a modes
fixture restores its original enabled state, so a prior clear-data guide run
can leave it off. Keep such setup failures. `modes` uses actual su/libsu,
checks permission readbacks, exclusive bindings, bundled APK bytes/read-only
installer URI and old release timers. AOSP's built-in su normally rejects
third-party apps; an adb-root emulator alone does not imply usable app su.
Use `three-modes.md` for the limitations of a temporary test broker.

Pass `--apk` and `--host-apk` on any fixture to require installed byte hashes.
After introducing `operating_mode`, external HID and other preference fixtures
must set that value as well as the legacy Shizuku flag, then restore both.

Use the actual `emulator-NNNN` serial in place of EMULATOR_SERIAL. The runner
rejects frame/screen-off fixtures for a non-emulator serial.

Clear-data permission guidance has a separate UI test. It installs the
same-signed main, host and non-suppressing UI observer, clears only the main
application on a disposable emulator, and checks that the guide appears while
correction is still off. It verifies all generic jump buttons, the permanent
reopen entry and the accessibility settings destination. It also installs the
helper, revokes DUMP, stops the test Shizuku server and verifies the missing-grant
chooser's full USB command, exact clipboard value, Shizuku action/recovery and
final revoked readback. The pure unit tests separately assert that missing
helper DUMP never makes on-screen readiness fail.

```sh
python3 tests/permission_guidance.py --serial EMULATOR_SERIAL \
  --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --output /tmp/pvs-permission-guide
```

For Shizuku authorization display, use the disposable-emulator-only
`shizuku-status` suite with `--expected-status authorized`, `denied` or
`stopped`. Prepare each actual server/grant state beforehand; the fixture
checks real Binder/permission readback, leaves correction off while reading
the page, and restores its preferences. Authorize through the actual Shizuku
dialog; a legacy Android permission grant alone is insufficient.
Repeat with `--expected-uid 0` for an actual root-started server; the default
`--expected-uid 2000` checks the ADB identity.
The fixture disables correction in `Instrumentation.onCreate`, before
`Application.onCreate` can enqueue the keeper. Disabling and stopping it only
in `onStart` raced startup and caused a foreground-service timeout in the
original fixture run; preserve that failure as test lifecycle evidence.

The frame fixture waits for actual pre-draw readback and correction state.
Register its observer on every panel attachment and remove it on detach:
a rejected write can outlast the prior dismissal timer and reopen the panel
with a new `ViewTreeObserver`. Observing only the first attachment loses the
failure-result frame even when diagnostics correctly show the actual volume.

```sh
python3 tests/run_instrumentation.py --serial EMULATOR_SERIAL \
  --suite shizuku-status --expected-status authorized \
  --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --host-apk tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk \
  --output /tmp/pvs-shizuku-status
```

| Fixture | Purpose and historical 1.4.11 count |
|---|---|
| `ShizukuRegressionInstrumentation` | Companion selector or Shizuku, stale/expiry/stream validation, background writes, pending bursts, reversal/revisiting; 87 Vivo / 85 emulator |
| `VolumeRegressionInstrumentation` | Ordinary AudioManager, passive/mute/canceled/consumed keys, delayed transitions, pending writes |
| `FinalPanelInstrumentation` | Actual readback, delayed/rejected writes, first/held/final frames, fixed third-row bounds; 13 |
| `OverlayStabilityInstrumentation` | Three rows and one bar, percent mapping, digit width, bounds, passive reopen; 56 |
| `ScreenOffInstrumentation` | Actual off state, synthetic full/up-only keys, ignored passive/consumed events, no panel/wake, wake-lock expiry; 16 |

The screen-off fixture wakes the emulator at cleanup and assumes a native
range containing its baseline 4/5. It is not a general real-phone screen-off
tool. The backend fixture can accept `screen_off=true` directly, but that is
still synthetic input and not physical routing certification.

The final-frame fixture waits for an actual pre-draw sample containing the
expected displayed value, native readback and correction state. A fixed 60 ms
delay after window attachment was insufficient on a busy emulator; keep the
original missing-first-frame and missing-failed-frame runs. This wait retains
the first-frame/intermediate-value assertions and fails if no frame arrives.

For a controlled Bluetooth output-gain comparison, the development test host's
`HostActivity` accepts `tone=true` with `play=true`. It starts a low-level 440 Hz
media `AudioTrack`; the default remains the near-silent playback fixture. Use a
safe native baseline, verify the A2DP route and read the active output thread's
`Signal power history` from `dumpsys media.audio_flinger`. Compare direct target
and physical-key runs using the same signal. Stop the service through
`play=false`, restore the original native volume and keep earbud analog gain
separate from measured A2DP PCM power.

## Full input / lifecycle emulator harness

Use only a disposable rootable AOSP emulator. `tests/e2e.py` clears app data,
changes accessibility/AppOps/animations, injects keys and reboots. Its
accessibility helper intentionally replaces the list for an isolated emulator;
never copy that behavior into a personal-phone procedure.

Compile its native input helper with the installed NDK:

```sh
NDK_TOOLCHAIN=/path/to/ndk/toolchains/llvm/prebuilt/darwin-x86_64
"$NDK_TOOLCHAIN/bin/aarch64-linux-android28-clang" \
  tests/native_keys.c -o /tmp/pvs-native-keys
python3 tests/e2e.py --serial emulator-5554 --keys /tmp/pvs-native-keys \
  --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --output /tmp/pvs-full --phase all
```
Run explicit `display`, `locked-boot`, `playback`, `rejected` phases separately;
`all` covers setup/audio/lifecycle/poll, not every optional phase.
The playback fixture uses the emulator's actual range. At 0–150 it retains the
Vivo-scale 30 baseline and 10-index player stride; on stock 0–15 AOSP it uses a
2-index player stride and computes every expected value from the real range.
It cannot certify Bilibili or an OEM by itself.
Historical assertions may expose current platform timing differences; investigate
and fix tests/code from evidence, never weaken them just to get a passing count.

## Installed-phone screen-off raw input (P15)

The 1.4.13 helper-UID routed-jump test is historical. Its native-jump inference
was removed because passive changes cannot authorize writes. Do not expect a
routed preset without a key to be corrected.

`tests/screen_off_hid.py` exercises the installed application's optional Shizuku
raw reader with a persistent external consumer HID device and independent
volume readback. It keeps the same main PID across sleep/wake, tests idle and
actual AudioTrack playback, five asleep presses and one awake press, reversal,
rapid mixed/pending bursts, held keys, passive presets, hidden panel and wake-lock
expiry. This is external HID evidence on a real phone, not built-in button
certification. No root or destructive main-process fixture is used for the
input sequence.

Use `screen-off-cases.md` to identify the assertions required for a particular
failure. In the final 1.4.14 runs, the native 150/50 case started at 121 and
reached 106 after five asleep down presses, then 103 after the awake sixth:
81%→69%. At 150/64, start 74 and expect 72,69,67,65,62, then 60. Compute
expectations with Java rounding for arbitrary native ranges and origins.
Each final HID run passed 83 assertions. Earlier 75-assertion runs omitted the
final playback-continuity checks and cannot replace the final fixture.

Compile the test-only HID executable and shell preset dex outside the repo:

```sh
NDK_TOOLCHAIN=/path/to/ndk/toolchains/llvm/prebuilt/darwin-x86_64
"$NDK_TOOLCHAIN/bin/aarch64-linux-android28-clang" tests/hid_volume.c -o /tmp/pvs-test-hid
mkdir -p /tmp/pvs-preset-classes /tmp/pvs-preset-dex
javac --release 8 -cp "$ANDROID_HOME/platforms/android-37.0/android.jar" \
  -d /tmp/pvs-preset-classes tests/ShellVolumePreset.java
"$ANDROID_HOME/build-tools/36.0.0/d8" --output /tmp/pvs-preset-dex \
  /tmp/pvs-preset-classes/ShellVolumePreset.class
python3 tests/screen_off_hid.py --serial SERIAL \
  --keys /tmp/pvs-test-hid --preset-dex /tmp/pvs-preset-dex/classes.dex \
  --steps 50 --output /tmp/pvs-raw
# Repeat with --steps 64. Add --baseline when testing a specific actual origin.
# Secure keyguard: --unlock-command 'python3 /path/to/private_unlock.py'
```

Install the same-signed development host and UI observer; start and authorize
Root or ADB Shizuku first.
The harness temporarily enables the raw backend, disables conflicting Vivo
volume-key shortcuts and restores settings, volume, accessibility and shortcuts
in cleanup. On the tested Vivo, shell `settings put` silently leaves these
values unchanged. The fixture snapshots the selected camera option, changes
the native UI's inner long-press switch and camera selection, and verifies
actual Settings readback before input and after restoration. Failure to apply
or restore a setting fails the run. `tests/android_device.py` uses a
non-suppressing UI dumper; ordinary
UiAutomation can disconnect accessibility and invalidate delivery tests.
The UI dumper's instrumentation component belongs to the host, but its target
is the separate `roro.stellar.volumesteps.uitools` package. Targeting the player
itself restarts that process when inspecting keyguard and stops playback.
Keep the separate observer installed while using UI/private-unlock helpers.
The HID fixture checks both main/player PID continuity and playback state
after unlock and around bursts. Player starts use `FLAG_ACTIVITY_SINGLE_TOP`
so a reused host task receives new playback extras through `onNewIntent`.
The player requests and abandons audio focus.
The 1.4.15 fixture additionally tests ten asleep presses and exact reversal in
both phases. Add `--shizuku-starter /path/to/official-starter` to test the
selected Shizuku connection stopping/recovering, the page's unmasked status,
and foreground-notification warning/clearance with the same main PID.
This option restarts the shell server, not the phone. Built-in reboot/Bilibili
recording uses `tests/phone_reboot_observer.py`; see cases R15–R18.
Actual-player external HID uses `tests/playing_app_hid.py` (R19); it checks
the installed player's exact UID has active media and audits one-second
settlement after every press, restoring volume without changing player settings.
Check actual connected service state after every target-main instrumentation.
Use an external private unlock command for secure keyguards; credentials never
belong in test sources, reports or logs. Bluetooth remains connected when it
was connected before testing. Compare route/range and capture built-in buttons
separately.

## Ordinary helper screen-off checks

Ordinary runs explicitly disable Shizuku. Restore the live keeper/accessibility
after target-main instrumentation before starting installed-phone input tests:

```sh
python3 tests/screen_off_hid.py --serial SERIAL \
  --keys /tmp/pvs-test-hid --preset-dex /tmp/pvs-preset-dex/classes.dex \
  --steps 50 --backend ordinary --scenario both --baseline 60 \
  --ten-presses-only --unlock-command 'python3 /path/to/private_unlock.py' \
  --output /tmp/pvs-ordinary-off
```

Choose a suitable baseline from the actual range. `--ten-presses-only` retains
all twenty settled volume assertions even when correction fails; setup, screen,
playback and process failures still stop the fixture. A retained false assertion
still fails the final report. `--scenario idle` / `playback` isolate each case.
Default Shizuku behavior remains unchanged. Actual-player HID also accepts
`--backend ordinary`; it checks the already-selected mode without changing it.

To reproduce read-only helper input-access evidence, build its test APK with
`:compatibility-helper:assembleDebugAndroidTest`, install that same-signed APK,
and run `com.vivo.finevolumesteps.helper.test/roro.stellar.volumesteps.compatibility.HelperInputAccessInstrumentation`
with `-e nodes /dev/input/eventN,/dev/input/eventM`. Select the observed built-in
nodes from capabilities; never copy a historical node number blindly. It checks
permissions and opens/closes nodes in the installed helper UID, without reading
or injecting keys, granting permissions or installing a privileged helper.
Remove the introduced test APK and verify provider/accessibility afterward.

Add `-e vivo_events true` for the optional own-UID Vivo `POWER_ID_INPUT`
registration check. It uses the matched event-service Binder descriptor and
transactions, counts only the selected event and always attempts unregister
after registration. No key is injected and no permission is granted.
On the examined API 36 Vivo, helper UID 10322 was denied with
`IllegalArgumentException: calling uid not allow`. A returned registration
alone or completed instrumentation is not proof of key delivery. Preserve
acquisition failures separately, compare the actual UID, remove the test APK
and recheck the live provider and other accessibility services.

For autonomous kernel simulation, compile the updated `tests/hid_volume.c` with
the same NDK command above. Add `--input-source kernel` to the installed-phone
fixture; it creates BUS_HOST uinput without requiring root on the tested Vivo,
uses an explicit simulation name/location, and waits five seconds after sleep:

```sh
python3 tests/screen_off_hid.py --serial SERIAL \
  --keys /tmp/pvs-test-hid --input-source kernel \
  --preset-dex /tmp/pvs-preset-dex/classes.dex --steps 50 \
  --backend ordinary --scenario both --baseline 60 --ten-presses-only \
  --unlock-command 'python3 /path/to/private_unlock.py' \
  --output /tmp/pvs-kernel-off
```

`--sleep-settle-seconds` can select 1–30 seconds; the default USB fixture
retains its earlier one-second wait. Both sources are simulation, not built-in
button qualification. The physical recorder rejects GPIO/PMIC-name candidates
outside a sysfs platform path, including virtual uinput clones. Its
`--progress --releases 20` options can report releases and finish after the
required count plus one second, while still retaining the maximum duration.

Actual-player tests also accept `--input-source kernel` and
`--retain-volume-failures`. The latter records every wrong native/settled
readback and continues the sequence; missing playback or wrong screen state
still stops the run, and any retained failure fails the final report.

## Phone-side physical recording with explicit readiness

If a terminal-owned background recorder is interrupted, use the bounded
phone-side recorder. This is diagnostic-only and may use the user's authorized
temporary root; neither binary nor shell script belongs in production APKs.
Build `tests/read_volume_events.c` with the installed NDK, including
`-Wall -Wextra -Werror`, to `/tmp/pvs-read-volume-events`. Copy it alongside
`tests/record_physical_volume.sh` in `/data/local/tmp`, using binary name
`pvs-read-volume-events`. Run the script with duration 1–600 seconds and a
new output directory beginning `/data/local/tmp/pvs-`.

Open the recorder while the screen is on. Confirm the output directory's
`ready` file and the native reader's `{"kind":"ready"}` record before asking
for physical input. Both GPIO/PMIC descriptors are opened read-only and their
names and platform sysfs paths are checked. All event records are unbuffered.
Power edges during screen changes remain in the trace; count volume keys
separately. After the input sequence and at least one second of quiet, create
the output directory's `stop` file. Wait for `complete`, pull the entire
directory outside Git, then remove only the introduced device-side files.
The original log-tag property is restored by cleanup.

Check complete down/up pairs, screen state around each pair, actual system
readback, main/helper readiness and player ownership before judging behavior.
A missing first DOWN is incomplete acquisition, even when the final volume
matches an expected endpoint. Do not change APKs or run target instrumentation
while the physical recorder is active.

## Passive installed-phone checks

`tests/phone_passive.py --serial SERIAL --output /tmp/pvs-passive` observes
an enabled connected app, makes controlled external media-volume presets and
checks no app popup/new correction, restoring native volume afterward.
It is not read-only despite needing no instrumentation/root/key injection.
It does not prove physical-button operation. The 50-step assumption was removed.

Permission collection is read-only:

```sh
sh tests/collect_android_permissions.sh SERIAL /tmp/pvs-permissions
```

It compares main/Bilibili permissions, AppOps and boot records; do not assume
Bilibili's proprietary startup permissions are transferable.

## Evidence report

Run build/unit/lint immediately before summarizing; include all original failures
and repeats rather than replacing a failed run. Use new JSON files from the
runner or full emulator harness with matching tested APK hashes:

```sh
python3 tests/write_report.py --version 1.4.14 \
  --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --run /tmp/pvs-emulator/ACTUAL_RUN.json \
  --run /tmp/pvs-phone/ACTUAL_RUN.json \
  --output /tmp/pvs-verification.md
```

A missing/mismatched APK hash, empty/failed test set or failed completion makes
the report fail. Do not merge evidence across APKs just because versions match.
Record a sanitized physical-phone matrix separately. Keep raw captures/logs and
temporary APK pulls outside the repository; commit only reviewable summary
evidence, code, skills and explicitly selected distribution artifacts.

Record the host/UI observer hashes as well as the main/helper hashes for input
tests. A host-only fixture change can invalidate an old run even when the main
APK is unchanged. A rebuilt helper can retain version 1.1 and its certificate
while changing bytes; distinguish the installed tested helper from an unchanged
historical download. A run with every emitted assertion passing but an unlock,
setup or restoration error is still failed. Keep old failures in a separate
appendix when their APK or fixture differs from the final successful run.

For an authorized push, verify CI against the full pushed commit SHA. Prefer an
authenticated GitHub CLI/API when available. In this session the CLI had no
login and the public API was rate-limited; the public Actions page still exposed
structured run state and commit links. Match that exact commit and require
completed/success. A direct run summary can resolve a lagging Actions list.
Do not infer success from a generic `Success` substring, an older green run,
or the absence of an error in the push command.

## Publish an authorized APK release

The tag-triggered `.github/workflows/release.yml` publishes the saved
`dist/precise-volume-steps-VERSION.apk` with `release-notes/VERSION.md`.
Commit both artifacts and their matching `dist/SHA256SUMS.txt` entry before
creating and pushing `vVERSION`. The tag must agree with `build.gradle`.
The workflow validates the APK hash, creates a stable release, downloads its
asset and verifies the same hash. It uses GitHub's ephemeral repository token
with `contents: write` limited to the publishing job; a local GitHub CLI login
is not needed to trigger it through an authorized SSH tag push.

Publish the exact distributed APK that was tested. A newly built Ubuntu CI APK
uses a different development certificate and cannot replace the existing
phone's APK as an upgrade. Verify the workflow result and public release asset
before reporting publication. Never move an existing release tag or replace an
existing mismatched asset silently.
