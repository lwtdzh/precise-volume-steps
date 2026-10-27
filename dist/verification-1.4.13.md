# Precise Volume Steps 1.4.13 — screen-off hardware-key correction

Historical report. Version 1.4.14 removes the native-volume-jump heuristic
described below: a passive preset could be mistaken for a key, and a suppressed
key may make no native change. The routed-write results below are fixture
evidence and do not certify built-in physical-button delivery. See the current
README for the explicit Shizuku raw-input setup.

2026-10-26, Vivo V2419A (PD2419, Android 16), fully automated over ADB with no
manual interaction. Version code 18.

## Problem

Correction did not take effect when pressing the hardware volume key with the
display off, even though it worked on the lock screen while interactive.

## Root cause

Two distinct layers, both established on the real phone.

1. **Input layer (virtual keys).** An external UHID consumer-volume device is
   not a wake source. Raw `getevent` proved the kernel receives the key while
   the display is asleep (`KEY_VOLUMEDOWN DOWN/UP` on the device node), but
   InputReader/`VivoInputPolicy` drop the event before audio dispatch and before
   the accessibility filter, so neither native volume nor the app change.
   Writing the physical `/dev/input/event*` nodes is blocked by SELinux for the
   shell UID; sysfs wakeup controls are also inaccessible without root. The
   physical `mtk-pmic-keys` device is a wake source, so ADB-created input cannot
   reproduce that input-layer path. This is a test-harness limitation, not an
   app bug.
2. **Audio routing (real physical path).** Firmware routes an asleep hardware
   volume key straight to the audio service. The native coarse jump happens,
   but the event does not carry the pass-to-user flag, so the accessibility key
   filter never sees it. The old controller required a delivered key to
   authorize a correction and deliberately ignored all passive observations.
   The jump was therefore never corrected.

## Fix

`VolumeAdjustmentController` now treats a hardware-sized media volume transition
as hardware-key evidence when the display is off or the keyguard is locked:

- requires non-interactive state or locked keyguard; the unlocked display keeps
  the strict key-only rule (regression tested),
- normal audio mode and no active A2DP route; headset/AVRCP and in-call volume
  stay with the system,
- single or double native coarse step (`ceil(range/15)` to `2 * ceil(range/15)`),
- plans from the pre-jump native value through the same anchored reversible
  `VolumeStepHistory`, holds the same bounded partial wake lock, keeps the panel
  hidden while asleep, and writes through the ready backend,
- histories are preserved so later presses continue the correct positions;
  failed/unconfirmed writes still show the real readback.

When a Vivo/iQOO companion exists but its process has just been recreated, the
controller now waits up to about 600 ms for the provider binder instead of
falling straight to this app's own identity, which the firmware ignores in the
background.

## Automated screen-off verification on the real phone

A test-only `androidTest` instrumentation
(`AudioRouteInstrumentation`) runs in the helper package UID, which the tested
firmware accepts in the background. In daemon mode it receives routed writes
through an exported broadcast receiver, keeping one helper process alive. The
verification disables A2DP, establishes a baseline while unlocked, sleeps the
display with `input keyevent 223`, then makes the firmware-equivalent routed
jump (speaker route, range 150, 50 configured steps, fine step 3, coarse 10):

| Case | Native jump while asleep | Inferred | Backend | Final | Display |
|---|---|---|---|---|---|
| Routed down | 60 → 50 | yes | companion 50 → 57 | 57/150 | stayed Asleep, no panel |
| Routed up | 60 → 70 | yes | companion 70 → 63 | 63/150 | stayed Asleep, no panel |

Service events for the down case:

```text
poll stream=3 60 -> 50
locked/off native change inferred direction=-1 stream=3 60 -> 50
companion key write stream=3 target=57
companion result=57
key confirmed stream=3 value=57 delay=24
```

The lock screen was entered and dismissed automatically using a pattern-gesture
unlock. Bluetooth, original volume, accessibility services and wake settings
were restored. Test packages and pushed files were removed afterward.

This verifies the firmware-audio outcome end to end. It still does not prove the
physical wake-source key's input dispatch: ADB cannot create a wake-capable
device or write physical input nodes on this production firmware.

## Emulator and unit checks

- Unit tests: 20/20 passed.
- `ScreenOffInstrumentation`: 14/14, including full-key correction, key-up-only
  recovery, inferred routed coarse jump while the display is off, no panel,
  no display wake, and no inference while unlocked.
- Final panel/readback frames: 13/13.
- Three-row single-bar layout and readback: 56/56.
- Ordinary-mode background/stream checks: 12/12.
- Release lint: no errors for app and helper.

## Artifacts

Main APK SHA-256:

```text
a5e6f1f4c8351527b3e0eadffe98fbd365a8543f83c01b9b7617375d159bf523  precise-volume-steps-1.4.13.apk
```

Helper 1.1 is unchanged for users. The routed-write instrumentation exists only
under `compatibility-helper/src/androidTest/` and is not part of the release
helper APK.
