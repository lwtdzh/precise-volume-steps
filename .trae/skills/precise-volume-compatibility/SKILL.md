---
name: precise-volume-compatibility
description: Preserve device findings and diagnose Android volume backends. Use for Vivo, iQOO, other vendors, background writes, screen-off keys, startup or helper changes in this project.
---

# Device compatibility

Read [implementation and historical findings](references/context.md) before
changing a backend, startup path, key handling, or volume calculation.
Read [installation and diagnosis](references/device-procedure.md) before touching
a phone. Read the repository's
[regression skill](../precise-volume-regression/SKILL.md) before verification.
For failures after restart, also read
[reboot and screen-off findings](references/reboot-screen-off.md).
For ordinary-mode failures, read
[ordinary screen-off findings](references/ordinary-screen-off.md).
For mode selection, optional Root or bundled installation, read
[three-mode implementation](references/three-modes.md).

## Preserve these distinctions

1. Android key delivery authorizes a correction; the volume backend performs it.
   Diagnose delivery and writing separately.
2. The optional Vivo helper's **real own package and UID** affect the tested
   firmware's background audio policy. A caller string alone was not established
   as a safe substitute. The helper is independently signed, not privileged.
3. The helper's **关联启动** permission permits its process to start. The main
   application's foreground keeper holds a provider connection and reconnects.
   This process-start condition is separate from accepting an audio write.
4. Screen-off processing can use explicitly enabled ADB Shizuku to read dedicated
   volume/power devices. A fresh matched raw release authorizes a media write
   even when firmware made no native change. Accessibility-only recovery still
   requires a key-up and recent matching native change. A partial CPU wake lock
   keeps only the bounded correction alive.
5. Core rounding and panel displays use Android's native range. Universal volume
   math does not imply universal OEM background permissions.
6. Helper 1.2 can read exact built-in EventHub key state after a one-time DUMP
   grant on the verified Vivo firmware. Keep its input registration separate
   from helper audio-write readiness. Older helpers and missing grants still
   cannot replace a selected Shizuku raw reader.
7. When the user requires ordinary mode with the helper, keep Shizuku disabled
   throughout that verification. Shizuku recovery and status improvements do
   not satisfy ordinary screen-off playback or idle requirements.

Keep the raw callback's 250 ms freshness limit separate from the 300 ms wait
after release before correction. A fresh callback can still precede a delayed
native write. Check both orderings in the
[screen-off cases](../precise-volume-regression/references/screen-off-cases.md).
Use the preceding real sample when native audio policy changed volume before
the raw snapshot; retain the observed value when a passive preset preceded
the key. Raw and accessibility callback paths must not classify a passive jump
alone as a key. The Vivo ordinary-playback compatibility path is a documented
exception: while truly noninteractive, with active playback, a ready helper,
normal audio mode and a non-wireless route, it accepts only the firmware's
one- or two-step coarse media transition. Keep its external-preset ambiguity
visible and verify it separately from explicit key delivery.
For a delayed accessibility callback, recover only an already-observed matching
transition after the original KeyEvent time, within 250 ms, with unchanged
current readback. Exclude the app's preceding write confirmation. Keep the raw
reader's unobserved-change fallback out of accessibility handling.

## Work procedure

- Establish actual model, API level, fingerprint, stream, minimum and maximum,
  route, screen/keyguard state, playback state and backend readiness first.
- Capture the failing physical-button sequence with service diagnostics and
  independent AudioManager readback. Trace key receipt → native change → request
  → confirmation → panel. Find the missing stage before adding code.
- Preserve normal mode and explicitly selected Shizuku mode. Keep Shizuku
  `ShizukuVolumeBridge`, `VolumeShellService` and `IVolumeShell` independent.
- For another vendor, compare foreground and background public AudioManager
  writes under the same UID/route/settings. If investigating package identity,
  use otherwise-matched independently signed probes and actual own-UID writes.
  Do not extrapolate Vivo's prefix allowance to another manufacturer.
- Add a narrowly scoped optional backend only when observed policy requires it.
  Preserve signature permission, explicit UID/signature checks, supported-stream
  validation, native clamping, expected-current checks, expiry and cancellation.
- Avoid unbounded retries of uncertain writes and suppress obsolete callbacks.
  Assistant and Shizuku production paths must remain independent of Root.
  The user-requested optional Root mode is documented in `three-modes.md`.
  When root is provided only for diagnosis, limit it to evidence collection or bounded reversible input,
  restore every system mutation, and keep the resulting behavior separate from
  ordinary-app qualification. Never add an invisible activity or wake the
  display to make ordinary background volume writes seem successful.
- Record evidence, exact firmware, limitations and new tests in references.
  Update the human setup instructions if another APK or permission is needed.
- Test the actual phone using the regression matrix. Verify physical screen-off,
  locked and reboot cases separately; retain a pending status when untested.

Keep repository instructions loadable from a fresh clone. Temporary historical
`/tmp` paths and old phone serials are evidence locations, not permanent tools or
device selectors. Use the currently selected device and preserve its state.
