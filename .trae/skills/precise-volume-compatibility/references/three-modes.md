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
implementations with Shizuku, using explicit authorized constructors. Public
Shizuku constructors accept the official server identities, root UID 0 and
shell UID 2000, and reject other UIDs.

Root is an optional, explicitly selected production backend. Assistant and
Shizuku do not invoke `su`; an already authorized root-started Shizuku server
is supported through the Shizuku API. Root requires neither a running Shizuku
server nor the companion package. Leaving Root mode disconnects its Binder, invalidates
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

## Authorization display and accessibility entry, 2026-10-31

The selected Shizuku page previously took all status from the volume controller.
When correction was disabled or the keeper was absent, it displayed only
“Shizuku 兼容模式已暂停”, hiding the application's actual grant. The page now
reads the running Shizuku Binder and permission independently, displays
“Shizuku 已授权” together with the actual correction/connection state, and
refreshes on Binder receipt, death, permission result and activity resume.
An absent server still reports “Shizuku 未运行”; a saved manager grant cannot
establish a running server after reboot. No grant is fabricated or requested
by the status refresh. The service-owned write/raw bridges retain their checks.

`ACCESSIBILITY_DETAILS_SETTINGS` expects the flattened service in
`Intent.EXTRA_COMPONENT_NAME`, not the former invented provider extra.
On AOSP and the examined Vivo firmware, that activity requires
`OPEN_ACCESSIBILITY_DETAILS_SETTINGS` (`signature|installer`); adding a
uses-permission does not grant it to this app.

The examined Vivo Settings APK exposes `com.vivo.settings.VivoSubSettings`.
Launching its validated `AccessibilityDetailsSettingsFragment` with
`:settings:show_fragment` and `Intent.EXTRA_COMPONENT_NAME` opens the exact
service detail. That system fragment still checks device policy and restricted
settings. Try this only on Vivo/iQOO after the generic detail activity fails;
retain the public accessibility-list fallback with
`:settings:fragment_args_key` both at top level and inside
`:settings:show_fragment_args`. Other vendor detail pages remain unverified.

`ShizukuStatusInstrumentation` runs only on a disposable emulator and verifies
the real server/permission plus Activity status while correction is disabled.
Use the real Shizuku authorization dialog: granting the legacy
`moe.shizuku.manager.permission.API_V23` with `pm grant` alone did not change
the server's own permission readback. Preserve that failed setup separately.

## Lit-keyguard latency investigation after 1.5.1

The locked raw-input path inherited the screen-off 300 ms release wait.
Non-wireless/idle raw keys could therefore leave the native coarse value in
place during that wait. Bluetooth playback already wrote at release, but
confirmation and the panel still waited for the guard. A newer pending press
also suppresses the preceding final panel.

`applyRawAtRelease` now permits a fresh matched raw release on a genuinely lit,
locked display to correct an observed matching transition immediately. A
consumed raw key can use the media-only fallback with unchanged readback;
all-stream mode still needs an observed transition to select the stream.
The existing power-chord, source readiness, freshness, mode, expected-current,
expiry and single late-write retry checks remain in force. Screen-off idle
keys retain their previous settling delay.

When AudioManager actually reads back the target on lit keyguard,
`showSettlingReadback` can render that sample before the guard finishes.
It uses the internal SETTLING state and freezes the third row at that actual sample;
later coarse overwrites do not become panel frames. Final confirmation still
waits for the original deadline. Neither a returned backend value nor a
requested target alone authorizes this early frame.

The user requested the same presentation as unlocked panels: SETTLING is quiet,
while genuine PENDING and UNCONFIRMED states retain their existing feedback.
Both PENDING and SETTLING freeze readback updates; clearing the text must not
resume passive reads during the late-write guard. The layout keeps its reserved
status-row height. Diagnostics include the actual status text, and the existing
locked raw fixture checks a blank status while a forced late overwrite is pending.

`LockedRawTimingInstrumentation` supplies synthetic matched callbacks to the
real assistant Binder while the phone is actually locked. It measures release
to readback and panel attachment, then checks a deliberately late coarse
overwrite, consumed raw keys, pending reversal, passive presets and the unchanged
screen-off path. These are controller/IPC timings, excluding physical-key
delivery and helper polling. Window attachment alone does not establish
visibility above keyguard; keep the external-HID visibility fixture and
built-in physical-button recording separate.

On Vivo V2419A/API 36, helper 1.4, native range 0–150 and 50 configured steps,
the original 1.5.1 synthetic locked-idle run measured 348 ms from release to
target readback and 381 ms to panel attachment. Final test-patch APK
`2b94ea5d32b08fc3dcd52f4de66eba16cfc85b572448294bad47f09a9ec927fa`
measured 14 ms and 44 ms respectively; its 16 assertions passed, including a
forced late overwrite, consumed key, pending reversal and unchanged hidden
screen-off idle behavior. Main unit tests passed 54/54; helper tests 3/3;
final emulator frames/layout/screen-off/ordinary passed 13/56/16/17 assertions.
Assistant synthetic backend suites passed 107 assertions at both 50 and 64 steps.
Final assistant UHID locked idle/playback suites passed 52 assertions at both
50 and 64 steps, including visible accessibility windows, exact reversal, later
readback and restoration. These external simulated keys bypass the strict
built-in helper parser and do not establish built-in physical latency.
The attempted Shizuku backend suite could not connect because its server was
not running; this is an unverified prerequisite, not a backend pass. Root was
not rerun. The reported multi-second physical panel delay and built-in button
latency still need a separate physical recording.

The quiet-display follow-up APK
`76aa85561470a8a1a94fb837ed25a08b59dab58861d468873a569a6a48be4781`
passed all 17 locked raw assertions, including blank status during a deliberately
late coarse overwrite. Main unit tests passed 54/54; emulator final-frame and
layout checks passed 13/13 and 56/56. This follow-up changes panel state and text
only; the earlier physical input and multi-second latency limitations remain.

## Root-started Shizuku authorization fix, 2026-10-31

On V2419A/API 36, the official Shizuku 13.6.0 server ran under UID 0 and the
main application was already authorized. The helper grant action nevertheless
showed “请使用 ADB 或无线调试启动的 Shizuku” with “打开 Shizuku”.
The activity, volume bridge, input bridge and public user-service constructors
accepted only UID 2000. The failure was an unsupported-server check, not a
missing user grant.

`ShizukuServerIdentity` now accepts UID 0 and 2000; app authorization and v11+
checks remain. Public volume/input services derive their permitted identity
from the actual process UID. The explicit internal Root constructors still
require their requested UID. Permission setup and helper grant services accept
both official identities. Existing volume calls already chose `android` for
UID 0 and `com.android.shell` for UID 2000. This change does not invoke `su`
from Shizuku or assistant mode.

Test APK SHA-256:
`97f6335af826e64a01e1840d66b536829e210ec74340e330b3e3e792afe7c482`.
The phone installed these exact bytes. Selecting Shizuku showed “已授权” and
both services connected as UID 0; raw discovery found two devices. Returning
to the original assistant mode and tapping the helper grant action confirmed
user 0 DUMP granted and assistant input available/registered. Current playback
remained active and native media volume remained 120/150, with 50 steps.
Accessibility was already off and remained off; no physical keys were injected.

The API 33 emulator used actual official servers under both UIDs.
Authorization-with-correction-off passed 6/6 per identity; synthetic background
volume suites passed 102/102 at both 5 and 7 steps per identity. Root permission
setup executed its fixed commands and connected accessibility on the successful
6/6 rerun. Its original 5/6 run found accessibility enabled but marked crashed
after instrumentation; clearing only that disposable test state allowed the
service to reconnect. The original backend setup failure had correction off.
Keep both original failures beside their reruns outside Git.
ADB permission setup also passed 6/6. A final-frame run lost its observer
after a long rejected write dismissed and reopened the panel; real diagnostics
already showed the correct unconfirmed readback. Observing every attachment
in the development fixture fixed the recording and passed 13/13.
Final emulator layout/screen-off/ordinary suites passed 56/56, 16/16 and
17/17 on the same application APK.
Final first-launch guidance and the missing-server helper chooser passed
19/19. Temporary emulator Shizuku was stopped and its starter removed.

Server UID acceptance and raw-reader readiness do not prove built-in physical
key delivery, audible Bluetooth behavior or phone reboot survival. Those
physical-device matrix cases remain outstanding.
