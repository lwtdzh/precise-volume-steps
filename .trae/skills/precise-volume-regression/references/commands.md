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

Current count is 31 (may grow):

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

## Instrumentation

Install a main APK and same-signed test-host on the chosen device. For a real
Vivo/helper run install the same-signed helper and allow 关联启动. Main correction
must be enabled. Overlay tests need either connected accessibility or overlay
permission. Inspect both `enabled=true` and actual service connection.
Shizuku backend requires its running ADB user service and prior grant.

Fixtures mutate native volume and can interrupt the main process/accessibility.
They restore volume and selected test settings in `finally`, but still compare
before/after snapshots and restore the physical service connection.

```sh
adb -s SERIAL install -r build/outputs/apk/release/precise-volume-steps-release.apk
adb -s SERIAL install -r tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk
adb -s SERIAL install -r tests/ui/build/outputs/apk/debug/precise-volume-steps-test-ui-debug.apk
```

Vivo may require observed installer acknowledgement taps; use the compatibility
skill's device procedure. Do not interpret a hanging installer as completed.

The runner parses test JSON and final instrumentation status, records installed
main APK hash, before/after device snapshots, raw output and errors outside Git:

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
```

Use the actual `emulator-NNNN` serial in place of EMULATOR_SERIAL. The runner
rejects frame/screen-off fixtures for a non-emulator serial.

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
The playback fixture models a player/media session with 0–150 range and coarse
native strides; it cannot certify Bilibili or an OEM by itself.
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
ADB Shizuku first.
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
Check actual connected service state after every target-main instrumentation.
Use an external private unlock command for secure keyguards; credentials never
belong in test sources, reports or logs. Bluetooth remains connected when it
was connected before testing. Compare route/range and capture built-in buttons
separately.

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
