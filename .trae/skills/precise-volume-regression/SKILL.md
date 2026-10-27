---
name: precise-volume-regression
description: Verify volume behavior and releases against past user feedback. Use before changing or releasing this app, testing a real phone, or fixing rounding, flicker, startup and screen-off issues.
---

# Verify changes

Read [build and automated suites](references/commands.md) and
[the physical-phone feedback matrix](references/phone-matrix.md).
For vendor/startup/identity changes also read the
[device compatibility skill](../precise-volume-compatibility/SKILL.md).

## Required procedure

1. Select an explicit device. Snapshot current volume/range, steps/scope,
   backend, accessibility list/connectivity, permissions, screen/timeout,
   playback/route and installed APK hashes. Preserve the user's normal state.
2. Build main and helper from the same source and signing key. Run release unit
   tests and lint. Compile the test host only for development. Use the standalone
   commands in the reference; no enclosing workspace is needed.
3. Run the frame/readback/layout/screen-off suites on a disposable emulator.
   Use real Android volume/power/IPC, not a mock alone. Do not run destructive
   emulator setup, AppOp rejection or screen-off fixtures on a personal phone.
4. Run backend instrumentation on the target phone using even and uneven step
   counts. Check every `pass` and final instrumentation/error status, not just
   `adb`'s exit code. Use `tests/run_instrumentation.py` to retain results and APK
   hashes. A failed test must fail the report.
5. Restore runtime accessibility after instrumentation, then test **physical
   phone buttons** against the full phone matrix. Inspect live settled readback
   and record panel video/frame geometry. External HID remains a separate test.
6. Exercise normal↔Shizuku/helper transitions, permission revocation, process
   death, task removal, real reboot and screen-off/locked states where relevant.
   Record real screen state and route; do not infer them from requested state.
7. Restore volume, settings, AppOps, wake/timeout and other accessibility services.
   Remove temporary test hosts/probes/native devices and verify normal service
   connectivity. Restore a pre-existing test host rather than blindly removing it.
8. Report firmware, versions/hashes, backend, input type, counts, failures,
   reruns, restoration and untested requirements. Keep all original failed runs.
   Use `tests/write_report.py` for new evidence; never copy old pass counts into
   a new release as though rerun.

## Do not regress

- Normalize percentage from actual maximum independently of configured steps.
- One graphical bar; three stable text rows; `%` denominator; no clipping.
- Direct system readback; hold the previous real sample during pending work;
  display actual result on failure; never fabricate the target.
- Origin-anchored cumulative rounding and exact reversal, including pending
  opposite keys and off-grid starts; no inserted history stops.
- Accessibility keys require a matching native transition. A fresh matched raw
  volume release can authorize an off-screen media change without that transition.
  Passive playback/presets never authorize writes; cancel power chords and
  stale/unmatched raw input.
- Bounded single writes, expected-current/expiry checks, stale-callback rejection.
- Partial CPU wake lock only; hidden panel while off; key-up recovery requires
  recent matching evidence; no display wake.
- Preserve existing working Shizuku behavior and user settings during switching.

A real-device pass is necessary before claiming a changed vendor behavior works.
When a phone is absent, finish builds/documentation/emulator tests and explicitly
leave the physical matrix unverified. This is a reporting rule, not a reason to
stop independently executable work or request redundant permission.
