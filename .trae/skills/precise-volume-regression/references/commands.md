# Standalone build and automated evidence

Run from the repository root. Python utilities require Python 3.9+; Android
builds require JDK 21, Android platform 37 and build-tools 36.0.0.
Set ANDROID_HOME/JAVA_HOME for the current machine or provide a local SDK path.
The project does not require the former enclosing repository.

```sh
./gradlew -PvolumeStepsE2E=true \
  :assembleRelease :lintRelease :testReleaseUnitTest \
  :compatibility-helper:assembleRelease :compatibility-helper:lintRelease \
  :precise-volume-steps-test-host:assembleDebug
```

Use `--offline` after dependencies are available if network metadata stalls.
Exact local configuration used in the earlier session:

```sh
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
bash gradlew --offline -PvolumeStepsE2E=true \
  :assembleRelease :lintRelease :testReleaseUnitTest \
  :compatibility-helper:assembleRelease :compatibility-helper:lintRelease \
  :precise-volume-steps-test-host:assembleDebug
```

Outputs:

```text
build/outputs/apk/release/precise-volume-steps-release.apk
compatibility-helper/build/outputs/apk/release/compatibility-helper-release.apk
tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk
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

Baseline count is 20 (may grow):

- `VolumeDisplayTest`: independent percent/configured-scale mapping.
- `VolumeStepQuantizerTest`: older grid utility behavior.
- `VolumeStepHistoryTest`: actual planner spacing/reversal. Native minima 0/1/5,
  ranges 7/15/50/100/120/150/255, every configured count 5–150; every interior
  origin on selected uneven/even ranges; immediate opposite keys; failed and
  external writes; no historical 14→15→16 stops; retracing/revisiting.

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
| `ScreenOffInstrumentation` | Actual off state, synthetic full and up-only keys, no panel/wake, wake-lock expiry; 8 |

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
python3 tests/write_report.py --version 1.4.12 \
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
