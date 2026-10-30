# Assistant, Shizuku and Root modes

The user requested three exclusive modes in 1.5.0. Preserve existing runtime
package IDs, accessibility component, helper signature permission and provider
authority. `OperatingMode` owns selection; legacy `shizuku_enabled` preferences
migrate without switching an existing Shizuku installation to assistant mode.

## Independent Root backend

`RootVolumeBridge` uses libsu 6.0.0 core/service. `RootVolumeService` runs under
UID 0 and accepts Binder calls only from the main application's exact UID.
Clear the caller identity only after that check and restore it in `finally`.
The privileged service shares the bounded volume and dedicated input reader
implementations with Shizuku, using explicit authorized constructors. Existing
public Shizuku constructors remain restricted to shell UID 2000.

Root is an optional, explicitly selected production backend. Assistant and
Shizuku do not request Root. Root requires neither a running Shizuku server nor
the companion package. Leaving Root mode disconnects its Binder, invalidates
queued writes and rejects stale callbacks. An existing authorization may
reconnect on startup; the Root manager controls whether it remains valid.

References: [libsu](https://github.com/topjohnwu/libsu),
[RootService documentation](https://topjohnwu.github.io/libsu/),
[Shizuku user services](https://shizuku.rikka.app/guide/).

`PrivilegedSetup` runs a fixed list of commands for the owning Android user:
notification grant, overlay AppOp, background AppOps, device-idle whitelist and
own accessibility component. Merge the current accessibility list; do not
replace other services. Android command success means execution succeeded,
not that an OEM allowed or bound the service. The page and permission guide
must use actual permission and service readback. OEM startup/background limits
and restricted accessibility settings may still require manual actions.

Root evidence from a disposable API 33 AOSP emulator includes actual `su` UID 0,
libsu RootService Binder, AudioService writes, permission readback, rejected
stale/expired/internal-stream writes and mode disconnection. Its AOSP `su`
client was temporarily replaced by an exact-app-UID test broker, and SELinux
was temporarily permissive. Restore the original binary, modes and enforcing
state afterward. This does not certify a Magisk authorization prompt, SELinux
policies or real Root-phone screen-off delivery.

## One download, independent helper identity

The main APK bundles the same-signed release helper APK as an asset.
`HelperApkProvider` verifies the helper package and main/helper signing
certificates before exposing a single read-only, non-exported installer URI
with an explicit access grant. Installation remains Android's native package
installer flow. `REQUEST_INSTALL_PACKAGES` requires the user to allow the main
app as an installation source.

On the tested Vivo firmware, helper writes depend on the actual companion
package/UID. Moving its service into the main package, a second process in the
same package, or changing only a caller string does not retain that independent
identity. Bundle installation reduces downloads while preserving the observed
behavior. Do not describe it as a true single installed package.

## Bluetooth coarse-step masking

Previously the early mask applied only to companion input. All selected,
ready raw-input backends now use it when noninteractive, in normal audio mode,
with active media playback on a wireless media route. Read a real per-press
origin rather than adopting an unconfirmed earlier target. When native volume
has moved in the key direction, quickly restore that origin, then apply the
origin-anchored fine target at matched release. Keep expiry/expected-current
checks and the bounded late-write settling check.

Shizuku 64-step synthetic background tests also exposed a native write racing
the expected-current check during exact reversal: the stale rejection left the
coarse endpoint visible. All three privileged writers now share one bounded
retry for a definite stale rejection, using current readback, the original
500 ms expiry and matching key directions. An uncertain backend failure does
not retry. Preserve the original failed run and successful final APK rerun.

A previous release timer must not complete a newer held press. Check both
gesture ownership and `released` before finishing; coalesce detection timers.
Power chords, stale input, mode changes, awake callbacks, calls and passive
volume changes remain independently guarded.

Passing numerical readback or external HID tests cannot establish perceived
Bluetooth gain or built-in physical button delivery. Verify Shizuku with the
user's actual player, real stable screen-off state and built-in keys. A2DP
PCM signal power is distinct from headphone acoustic gain. Earlier user
confirmation applied to the assistant version, not this Shizuku change.

Current 1.5.0 APK `a3a32b611b3050f2bd9612c830d438311671124ae05042512eaa1eef13f113d5`
was measured on Vivo V2419A with OPPO Enco X3, Absolute Volume off and a
controlled 440 Hz A2DP signal. One five-press external-HID run matched every
direct target's 50 ms PCM floor. A strict rerun retained one first-key sample
1.1 dB below the target calibration for 50 ms and therefore failed. Keep both
runs; the passing run does not erase the timing variation.

A later built-in-button recorder held actual Bilibili playback, A2DP routing,
stable screen-off state and ready Shizuku write/raw services for 120 seconds,
but received zero physical volume releases. Native volume stayed 111/150.
This is an incomplete acquisition, not a physical pass or behavioral failure.
Evidence remains under the private temporary directory
`pvs-physical-shizuku-1793349831`; a user-performed five-key sequence and
subjective listening result were still required at that point.

A later completed run used the same APK and exact Bilibili UID playback through
OPPO Enco X3. The built-in power pair was followed by 17.3 seconds of stable
screen-off time and five complete PMIC volume-down pairs. Settled readback was
111→108→105→102→99→96. Firmware coarse writes were restored in 4–28 ms before
each fine target; the user reported no perceptible extra drop. Keep this as the
built-in physical and subjective evidence under
`pvs-shizuku-physical-live-current`.

That run exposed a separate assistant-mode reconnection race. An input check
queued before the helper Binder existed could finish after connection and make
the real check remain skipped. Final APK
`031fbf6c454afa78bc68dab6aeb7ff729c820eee71f8c639bd012a9baf5792c2`
returns after starting an absent connection and immediately rechecks a replaced
Binder. On the Vivo, Shizuku→assistant recovered
`checked=true, available=true, registered=true`; companion 50/64-step runs each
passed 105 checks. Shizuku volume, raw-input and Bluetooth-mask code did not
change from the completed physical build.

## Lit keyguard in 1.5.1

Vivo V2419A/API 36 hid the main application's TYPE_APPLICATION_OVERLAY
window while keyguard was locked: WindowManager reported
`mPolicyVisibility=false` and `isVisible=false`. Root external-HID input
still changed actual native volume from 24 to 21; an invisible panel did not
establish a failed write.

`VolumeOverlayController.flushRender` now selects the already-connected
accessibility service while keyguard is locked. Its TYPE_ACCESSIBILITY_OVERLAY
window can appear on the lit keyguard. Existing context migration removes the
old window before adding the new one; unlocked rendering keeps the normal
permission-based choice. Do not wake/unlock the device or substitute a fake
third-row target. OEM volume UI may remain visible independently.

Helper 1.4's `InputDumpMonitor` reads when prewarmed, noninteractive or
keyguard-locked. A screen-on broadcast must not stop a locked reader.
ACTION_USER_PRESENT and the reader's next state check stop idle monitoring
after unlock unless wireless-playback prewarm is still needed.
`VolumeAdjustmentController.companionInputOwnsKeys` uses the same locked/off
condition for raw gesture ownership and accessibility deduplication.

The helper's strict EventHub parser observes verified built-in PMIC/GPIO
devices only. On API 29+, accessibility callbacks from an identified external
InputDevice remain eligible in assistant mode. Suppressing those callbacks
merely because the built-in helper listener is ready drops external keys.
The tested Vivo's InputReader classifies both gpio-keys and mtk-pmic-keys
as `IsExternal: false`; record device classification when testing other firmware.
Shizuku and Root retain their existing dedicated raw-device ownership.
Unknown/synthetic device IDs do not bypass built-in deduplication.

API 33 AOSP EventHub dumps lack the verified SysfsDevicePath and pressed
KeyState required by this helper parser, even when an emulator device is
named gpio-keys. An attempted emulator helper lifecycle instrumentation failed
with IllegalArgumentException; keep that evidence, and do not weaken the
physical-device parser to make it pass. The supported phone's actual helper
lifecycle is checked separately in `tests/lockscreen_keys.py`.

Main-service helper-health diagnostics are cached for up to two seconds.
Tests must wait for refreshed lifecycle diagnostics before assessing whether
the reader remains active. This settles a status check; it does not establish
immediate physical-button receipt or eliminate Bluetooth coarse transients.
The new locked-screen external-HID fixture records real screen/keyguard state,
idle/playback, exact readback and reversal, visible window type, later
overwrites, passive presets and restoration for each selected mode.
Built-in buttons and reboot remain separate requirements.
