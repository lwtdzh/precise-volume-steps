# Ordinary Vivo mode after the user's correction

## Requirement and current device

On 2026-10-28 the user rejected the preceding Shizuku recovery/status change:
“还是不行，关屏后不符合预期，你不能只保证 shizuku 正常，普通模式使用助手也应该正常符合预期”.
The requirement remains correct ordinary helper control while asleep, both
actual Bilibili background playback and idle, including after real reboot.

In the next feedback the user explicitly confirmed: “重启前一切都是好的（没开
shizuku，用的助手）”. Treat ordinary helper success before reboot as the user's
reported baseline. There is no instrumented pre-reboot capture confirming its
exact service/filter/playback state. The helper's current raw-device permission
denial does not identify the state that changed at reboot or establish that
ordinary recovery is impossible. Continue differential startup/filter/policy
investigation; do not substitute Shizuku recovery for the requested behavior.

The user subsequently explicitly answered yes when asked whether pre-reboot
fine control also worked after the display had been completely black for more
than ten seconds, without background playback. Preserve that clarification.
Do not explain their reported success as a brief screen-off transition or
visible always-on display. Android's internal pre-reboot Display/interactivity
state was not recorded, so its exact relationship to the black screen remains
unknown.

Vivo V2419A / PD2419, Android 16/API 36, fingerprint:
`vivo/PD2419/PD2419:16/BP2A.250605.031.A3_V000L1/compiler260529100608:user/release-keys`.
Current ordinary snapshot had 50 steps, native range 0–150, connected keeper,
accessibility and helper, Shizuku disabled and raw reader unready. Media was
inactive and the display unlocked/on. This snapshot does not reproduce asleep
buttons. Native volume was initially 22; following a user interruption it was
100. A later snapshot before the 64-step test again read 22; that run restored
22. Preserve the latest pre-test state rather than any earlier snapshot.

## A confirmed ordinary callback-ordering defect

Installed controller history contained ordinary up presses:
3→13→6, then native 6→16 was observed before the next callback. The controller
subsequently wrote 19 instead of the intended 9. Its old snapshot correction
was limited to raw callbacks. Historical logs lacked original accessibility
event times, so retain the ordering finding without claiming that exact
physical trace proved the event timestamp association.

A new real-Android test reproduced the defect deterministically on AOSP 13:
baseline 9, original down event time, native 9→4 and observation, then delivery
of the original callback. The old APK expired without correction and remained
4; expected 6. The pre-fix run passed 12 assertions and failed the next.
Keep that original run.

The controller now recovers an accessibility origin only when current equals
the observed value, the matching transition was observed after the original
KeyEvent within 250 ms, and it follows the app's last write confirmation.
Unobserved changes, changes preceding the key and app acknowledgements are
excluded. Raw key selection, release handling and baseline recovery remain
unchanged. Diagnostics include original event age.

The first callback fix passed units 35/35. AOSP 13/API 33 passed ordinary 17/17, frames 13/13,
layout 56/56 and synthetic screen-off 16/16. Vivo ordinary helper background
tests passed 92/92 at 50 steps and 92/92 at 64 steps, including late up/down
callbacks and a consumed key following a passive preset. Those Vivo tests use
synthetic callbacks and real helper IPC; they do not prove built-in delivery.

The added consecutive late-callback test exposed another defect in that first
fix: at 150/64, starting 22, the second up press reached 26 instead of 27.
The original continuation run passed 19 assertions and failed the twentieth.
Observing the native adjustment before the callback had cleared the cumulative
rounding path. The final controller retains a copy before clearing it, and
restores it only with the verified late callback's preceding real value.
Settings changes/cancellation discard that copy; passive presets cannot select
it. The raw path remains unchanged.

## Final automated and external-input results

| Test with final APK | Result | Input |
|---|---|---|
| Release build/main-helper lint | Passed; no lint errors, existing warnings retained | Gradle |
| Units | 35/35 | Math, display and key-baseline cases |
| AOSP 13 ordinary / frames / layout / screen-off | 17/17, 13/13, 56/56, 16/16 | Real audio/power; synthetic callbacks |
| Vivo ordinary helper, 64 / 50 steps | 104/104 each | Synthetic callbacks; real helper IPC; Shizuku disabled |
| Vivo ordinary idle + controlled playback asleep | Failed, 92/130 assertions; playback later stopped | External UHID |
| Actual Bilibili asleep, ordinary helper | First press failed; 3/4 emitted assertions | External UHID; exact Bilibili UID active |

The six final automated Android runs passed 310 assertions. Their reports
record main, host, relevant helper and Java fixture hashes. This is separate
from ordinary input qualification, which failed.

The installed-phone ordinary HID test began with connected accessibility and
helper, explicitly disabled Shizuku/raw input, and confirmed disabled Vivo
volume-key shortcuts. With the display genuinely off, idle baseline 60 remained
60 for all ten down and ten up presses. The controller received no keys.
The last up value coincided with the original expected endpoint; nineteen
other value assertions failed. That endpoint coincidence is not delivery.

With a real test AudioTrack active, native volume followed coarse changes
60→50→40→30→20→10→0, then 10→20→…→80 on the reverse sequence. No controller keys
were received. Playback was still active through the seventh reverse press,
but was inactive at the eighth; the fixture stopped on that state failure.
Its final 92/130 result includes 37 incorrect-volume assertions and one playback
failure. It is an incomplete playback sequence, not a completed twenty-press
qualification. The cause of playback stopping was not established.

An existing paused Bilibili video was then resumed at safe native volume 3.
Its exact installed UID owned active media, and it continued in the background
with the display off, connected ordinary helper/accessibility, and raw input
disabled. The first external up press reached 13 instead of expected 6; both
initial and one-second readbacks were 13, with no controller key received.
The fixture stopped at that first failure and restored 3. Exact-player audio/
session evidence was also retained before and after the failure. This does not
complete ten presses or certify built-in buttons.

Retained setup failures: the first ordinary HID run found no keeper after
target-main instrumentation and injected no input; the first observer install
returned INSTALL_FAILED_ABORTED and a separately confirmed retry succeeded;
one actual-player invocation failed Python indentation parsing before input,
then passed syntax checks and ran. Keep these separate from native input
failures. Restore the live keeper, unlock and actual accessibility connection
before testing. Serialize phone installation with UI/foreground fixtures.

## Input sources checked in the actual helper UID

Read-only instrumentation targeted the installed helper's real UID (10322 in
this run), not shell or a caller-string substitution. Its built-in volume-up
GPIO node `/dev/input/event0` and volume-down/power PMIC node
`/dev/input/event1` both failed open with EACCES. No keys were read or injected.

| Permission/source | Observed result |
|---|---|
| MONITOR_INPUT | Not granted; protection level 33554434 |
| LISTEN_FOR_KEY_ACTIVITY | Not granted; permission lookup reports undefined |
| USE_BIOMETRIC_INTERNAL | Not granted; protection level 2 (signature) |
| READ_INPUT_STATE | Not granted; protection level 2 (signature) |
| SET_VOLUME_KEY_LONG_PRESS_LISTENER | Not granted; protection level 50 |

Matching extracted firmware confirms: AccessibilityInputFilter skips events
without PASS_TO_USER (1073741824); PhoneWindowManager's off-display volume path
sets user delivery for active music/alarm, while Vivo's hook also handles idle
DOZE shortcuts. Adding another accessibility service does not bypass that
policy. InputManager's gesture monitor requires MONITOR_INPUT. The additive
Vivo fingerprint system-key listener checks USE_BIOMETRIC_INTERNAL. The newer
activity listener reports no key direction and is permission checked. A paused
MediaSession is not selected as the default volume session; active playback
state is required. None supplies a verified ordinary idle key source here.

Do not treat an app-readable audio broadcast as general physical-key evidence.
At this investigation stage, native-jump inference, dummy production playback,
screen waking, hidden activities and system-signature/root assumptions were
excluded. The later final implementation restored only the narrowly guarded
active-playback coarse transition described at the end of this document.
Helper audio-writing permission still does not grant global input access.

## Input-filter and rebind investigation after the user's clarification

The same post-reboot firmware was examined with Shizuku disabled. Both GKD and
the main accessibility service were bound, without crashed/binding services.
The main service retained capability 8 and its request-filter flag.
`dumpsys accessibility` listed `KeyboardInterceptor` and `MotionEventInjector`;
native `dumpsys input` reported `InputFilterEnabled: true`. The matched
`libinputflinger.so` function `shouldSendKeyToInputFilterLocked` simply reads
the enabled flag. These observations exclude a missing filter in this snapshot,
but do not establish that each off-display key reaches the app.

Matched framework decompilation identified a second delivery gate:
`AccessibilityInputFilter` requires `PASS_TO_USER`, then
`KeyboardInterceptor.getEventDelay` calls
`PhoneWindowManager.interceptKeyBeforeDispatching` **before** notifying the app.
Vivo's `VivoInputPolicy.interceptKeyBeforeDispatching` records
`PhoneWindowManager.isScreenOn()` on key-down; for off-display active music,
voice or alarm it directly sends the volume key to `MediaSessionLegacyHelper`
and returns -1. `KeyboardInterceptor` drops negative-delay events.
`PhoneWindowManager.isScreenOn()` uses `isScreenOnEarly`, a different sample
from the application's `PowerManager.isInteractive()`.

The separate `sendVolumeKeyEvent_cust` call was traced through `AudioService`
to `com.vivo.media.FeatureService` in the phone's `vivo-media.jar`. It updates
internal volume/haptics features; this examined call exposes no app key callback.
Do not turn these source observations into an assertion about the unrecorded
pre-reboot state.

Further matched `IInputManager`/`InputManagerService` inspection found that
`monitorInput`, `getKeyCodeState` and `getScanCodeState` are system-internal
methods, absent from this firmware's Binder interface. Public `hasKeys`
describes capability, not the current pressed state.
Both gesture-monitor variants enforce `MONITOR_INPUT` before using a package
argument. Key-gesture listener/handler registration requires the actual
SystemUI app ID or `MANAGE_KEY_GESTURES`; the audio-writing package-prefix
allowance does not appear in these gates. The helper input probe now also
records that permission's real grant/protection state on its next phone run;
it has not been measured on the disconnected phone.

A bounded external-UHID differential probe compared the connected service
before and after explicitly toggling only the main service off/on in native
Settings. It retained the same keeper PID, helper connection, 50 steps and
ordinary mode, with no Shizuku start. At native origin 22:

| State | Before rebind: up/down | After rebind: up/down |
|---|---|---|
| Awake, idle | 25 / 22 | 25 / 22 |
| Asleep, idle, first input about one second after sleep request | 25 / 25 | 25 / 25 |
| Asleep, controlled AudioTrack playback | 32 / 32 | 32 / 32 |

The first asleep idle up had an actual accessibility callback and helper
correction; the following down did not. No new callbacks appeared for either
playback press, though media remained active in both sampled readbacks.
Original Vivo shortcuts were enabled during this probe. Their handling and the
early-screen-state gate can affect these inputs; the brief first-up success is
not stable screen-off qualification. Rebinding did not restore the tested
behavior. This is external input with controlled playback, not built-in buttons
or actual Bilibili. The probe's empty acquisition-error list is not a behavior
pass.

The probe restored native 22, ordinary 50-step preferences, the complete
accessibility list, no playback/panel/pending write, awake/unlocked state,
600000 ms timeout and USB stay-awake 2. Its private record is
`/tmp/pvs-normal-screenoff-20261028/filter-rebind-probe.json`.
A proposed repeat with five-second settling and shortcuts disabled could not
start: the phone disconnected before the first snapshot or mutation. Retain
that setup failure; no five-second result exists.

The native debug-log property change was denied and read back unchanged (`no`).
No root or permission grant was used. Main/helper build and lint still passed;
the unchanged main APK retained its final hash and the cached unit reports
contained 35 tests with no failures/errors. The newly assembled helper hash
`cfe3a1d8b478dda162d790e26ae2953becb241b40bb0cf4afe1a5abbdf87c56e`
was not installed; phone evidence still applies to the installed helper above.

The development host and UI observer were reinstalled for this probe.
They remain pending removal because ADB disconnected after state restoration.
On reconnection, snapshot newer user state before doing cleanup; remove only
these introduced packages after the remaining UI/physical checks.

## Artifacts and remaining verification

The final locally installed 1.4.15/code 27 development build is:
`9afb3a4065593423e9440a4c5a5ebe2b2c3b8478006d7606320a0f9ac6de3038`.
The first callback-fix APK was
`c0a80811a58a82d4f89133e2cbe411e4f971da127fed7daabe15c8c049d2a810`;
its separate 92-check passes do not include the continuation fix.
Installed helper 1.1 remained:
`23852b52e4669f476050affd7476f052323c07992aa6e1e764003a80316a6d80`.
Development host:
`1a7184410b1adbb97c239651f6de7a1e0163a3f02bf32d1878c134b8e659b1c2`.
The earlier 92-check fixture host was
`b04ef404e90cfbd94036fd6d92748c4c48693ec2cdace0750a44a21a40794a4f`.
UI observer:
`0dcc914f87f0dd726baa10b7fee0e5f20ef2b7c0accff4ba4b328263c944fa89`.

The preceding distributed/status-only APK and its verification record have
different bytes. Do not copy their Shizuku/Bilibili/reboot pass counts to this
build. No release or commit is authorized. Raw evidence is retained outside
Git under `/tmp/pvs-normal-screenoff-20261028`; permanent tests and these notes
remain usable without it.

Ordinary built-in idle, actual Bilibili and post-reboot qualification remain
outstanding. A working late-callback correction cannot supply an undelivered
key. Record external HID outcomes separately, with exact power/playback and
restoration evidence; retain failures rather than replacing them with Shizuku.

## Restoration

Introduced Bilibili playback was paused; original native 22/150, 50 steps,
ordinary mode, media scope, full accessibility list and actual GKD/main bound
services were restored. Helper ready, raw input disabled, no playback, pending
correction, panel or key wake lock; display awake/unlocked. Screen timeout
600000 ms and USB stay-awake 2 were unchanged. Original Vivo shortcut values
and camera choice were restored and read back by the HID fixture.

The introduced helper-test, host and UI-observer packages and device-side HID/
preset files were removed. Installed main hash matched the final build; the
original helper hash was unchanged. The built-in button matrix P01–P20/P22 was
not run in this follow-up; P21's native installer acknowledgements and service
restoration were recorded. Bluetooth output and other vendors were not tested.
The ordinary screen-off requirement remains unmet; no release was published.

## Reconnected phone: stable sleep and helper permissions

On reconnection on 2026-10-28, the phone's current native volume was 100/150.
The tests preserved 100 rather than restoring the earlier 22. Ordinary mode,
50 steps, keeper PID 7103, helper and accessibility were still connected.
The boot ID remained `51adfd3a-73c7-4546-a6d9-bc656e7932f7`.

The first repeated probe stopped before any key injection because the USB
connection-mode dialog obscured Vivo's shortcut page. It restored the saved
state and retained the setup error. After dismissing the observed Cancel
control, the five-second-settling probe completed, with both volume shortcuts
disabled and native display/window/accessibility dumps retained per readback:

| State, origin 100 | Before rebind: up/down | After rebind: up/down |
|---|---|---|
| Awake, idle | 103 / 100 | 103 / 100 |
| Stably asleep, idle | 100 / 100 | 100 / 100 |
| Stably asleep, controlled AudioTrack | 110 / 100 | 110 / 100 |

All off-display samples had `interactive=false`, locked keyguard, connected
ordinary services and no fresh accessibility key. Controlled playback remained
active through both presses in each stage. The expected configured changes
were 103 / 100. Neither shortcut disabling nor explicitly rebinding only the
main service restored these external-input cases. The empty acquisition-error
list is separate from the failed behavior. The earlier brief first-up result
does not survive stable sleep with shortcuts disabled.

The completed probe restored 100, 50 steps, ordinary mode, full accessibility
list, original shortcut values and human-street-camera selection, no playback,
awake/unlocked state, timeout 600000 and USB stay-awake 2. Its private record is
`/tmp/pvs-normal-screenoff-20261028/filter-rebind-probe-1793161882582333000.json`;
the preceding setup failure is `filter-rebind-probe-1793161840910044000.json`.

The rebuilt helper test APK, SHA-256
`8f9005d9932831f032c5e4e1cf915e412260d598e789b1bc59c7c621a06adea5`,
was installed with the observed native installer acknowledgement. Read-only
instrumentation in actual helper UID 10322 confirmed `MANAGE_KEY_GESTURES`
ungranted, protection level 2 (signature). The other listed permissions and
both built-in node EACCES results were unchanged. Instrumentation completed
with code 0, and the introduced helper test APK was removed. The production
helper was not replaced; main/helper and host/observer hashes matched above.

The input recorder was independently checked with a temporary external HID
while awake: its exact `getevent -lt` subprocess and parsing expression received
four live edges for one up/down pair, before reader termination. This verifies
that the reader is not silently waiting for stdout flush and that its parser
matches the device's output. It does not verify built-in delivery. The temporary
HID and preset files were removed and native 100 restored.

An initial 45-second built-in idle recording after reconnect contained no
GPIO/PMIC volume releases. It is unverified, with the recorder's explicit
empty-input error retained; do not report a physical pass or fail from it.
Further built-in idle and actual Bilibili input still require physical presses.

A subsequent 180-second idle recorder retained 491 samples and two actual
PMIC power edges, with no built-in volume releases. Its first samples were
awake; after the captured power release, the display became noninteractive and
then locked. Volume stayed 100. Both the empty-volume-input and inconsistent
screen-state errors are retained. This is not a qualified physical-volume
sequence. The wrapper restored the saved 100 and ordinary state with no
cleanup error. The trace is
`/tmp/pvs-normal-screenoff-20261028/physical-reboot-idle-1793162086937600000.json`.

Two further matched-firmware input candidates were inspected without invoking
their Binder methods. `CapacityKeyService` exposes pressure/camera controls,
not a volume-key callback registration. Native `InputManager.createInputChannel`
in `libinputflinger.so` checks the actual calling UID at addresses
`0x1f7114`–`0x1f7124`: only 0 or 2000 proceeds; other UIDs return an exception.
It supplies no ordinary helper entry. No root, shell helper, fake playback or
screen-waking production change was added.

Final reconnect cleanup verified native 100/150, ordinary 50-step preferences,
ready helper, both GKD and the main accessibility service bound, original
shortcut values, USB stay-awake 2 and timeout 600000. The phone was awake and
unlocked, with no playback, pending correction or key wake lock. The introduced
host and UI observer were uninstalled after the last required UI check; the
helper test APK was already removed. Private restoration evidence is
`/tmp/pvs-normal-screenoff-20261028/reconnected-final-restoration.json`.
The ordinary physical idle, Bilibili and reboot requirements remain unmet.

## Autonomous kernel simulation after the user declined manual presses

The user then requested: “别让我操作，你想办法用按键模拟来搞”.
Continue automated input work without requesting manual phone presses.
Keep physical qualification separate from simulation results.

ADB shell can open `/dev/uinput` on this firmware without root. A temporary
probe created BUS_HOST volume devices, including separate GPIO/PMIC-name
simulations with an explicit `pvs/key-simulation` location. Android identified
these as built-in (`external=false`), not VIRTUAL. They are still simulated:
matching a hardware name, bus and layout does not make them physical buttons.
Shell can read the original input nodes but cannot open them for writing;
all attempted write/read-write opens returned EACCES.

The first native simulation began at the newer real native value 0 and restored
0. Do not overwrite it with the earlier 100 or 22. The main/helper APKs were
unchanged and Shizuku remained disabled. At a temporary origin of 37:

| Input source | Awake up/down | Stable idle-off up/down | Controlled audio-off up/down |
|---|---|---|---|
| BUS_HOST uinput, GPIO/PMIC-name simulations | 40 / 37, callbacks | 40 / 40; only first up callback | 47 / 37, no callbacks |
| `adb input keyevent` | 47 / 37, no callbacks | 37 / 27, no callbacks | 47 / 37, no callbacks |
| Shell-injected KeyEvent with original device IDs | 47 / 37, no callbacks | 47 / 37, no callbacks | 47 / 37, no callbacks |
| BUS_HOST UHID | 40 / 37, callbacks | 150 / 150; initial up callback | 47 / 37, no callbacks |
| BUS_USB UHID | 40 / 37, callbacks | 37 / 37, no callbacks | 47 / 37, no callbacks |

Each source used full down/up pairs. The kernel readers independently retained
all six pairs for the first native source and all six pairs for each UHID
source. The 150 result is a failure: the host short press had kernel down/up
edges about 60 ms apart, initially corrected to 40, then native readback rose
40→70→120→150 without further app-key records. Losing a release in system
policy/repeat processing is a candidate explanation, not yet proven.

No enterprise volume-up-long-press service resolved for
`vivo.app.action.VIVO_EMM_VOLUMEUP_LONGPRESS`; the examined reinjection branch
requires a valid matching service. Do not attribute the first-up result to
that branch merely because its decompiled code contains `injectInputEvent`.

A down-first native repeat (`-+-+`) retained 24 independent kernel edges:
awake 34/37/34/37 with callbacks, idle-off 37/37/37/37 without callbacks,
audio-off 27/37/27/37 without callbacks. Before and after every off-state key,
the **default display 0** had `mAwake=false`, `mScreenOnEarly=false` and
`mScreenOnFully=false`. The first off-state display was committed DOZE, then
OFF for the following keys. A separate virtual display 90000 remained awake;
never select its first matching awake field as the phone screen's state.
These findings agree with the examined queue/filter/dispatch policy gates,
but do not establish the user's unrecorded pre-reboot state.

The input Perfetto feature appeared enabled in DeviceConfig, while the native
dump said `InputTracer: Disabled` and no `android.input.inputevent` data source
was advertised. Accessibility `start-trace -t InputFilter` returned success,
but shell could not read `/data/misc/a11ytrace`; tracing was explicitly stopped.
No policy-flag payload was obtained from those attempts. Retain the input
atrace files separately; their Java policy calls are not fully instrumented.
GPIO/PMIC sysfs inspection exposed capability/disable files, not a readable
pressed-state source; even shell reads of their `name`/`phys` files were denied.

Private records: `source-matrix-1793163696747633000.json`,
`source-matrix-1793163935326268000.json`, and
`source-matrix-1793164349657103000.json` under the earlier private directory.
Their empty acquisition-error lists do not imply a behavior pass.

`tests/hid_volume.c --kernel` now provides a persistent generic BUS_HOST uinput
simulation with its own explicit name/location and the existing short/hold
protocol. `tests/screen_off_hid.py --input-source kernel` uses that source,
defaults to five seconds of sleep settling, records capabilities and labels
the report as simulation. The default USB branch remains available.
The built-in recorder now verifies each GPIO/PMIC candidate's actual sysfs
platform path, rejecting a virtual clone even if its name matches hardware.

## Completed actual-player kernel simulation and cleanup

The generic BUS_HOST ordinary idle/controlled-playback fixture retained all
twenty idle readbacks: native 60 stayed 60, with no app callback. Controlled
playback used coarse ten-unit steps and stopped at reverse press seven when
its AudioTrack became inactive. The result was 90/127 passing assertions,
36 incorrect-volume assertions and one playback-state failure. It restored
the latest saved native 0 and ordinary preferences. Keep this incomplete
controlled-playback run separate from the completed actual-player run below.

For actual Bilibili, the first targeted session `play` setup did not start
active media. Its wrapper error is retained. The existing video had reached
its end, position 540075 ms. The agent used the inspected native **重播**
control at temporary native origin 3, verified the exact installed Bilibili
UID owned started media, then slept the phone for five seconds. No user
operation or Shizuku start was needed.

The actual-player BUS_HOST run completed ten up and ten down presses:

| Sequence | Expected native values at 150/50 | Actual settled values |
|---|---|---|
| Ten up from 3 | 6,9,12,15,18,21,24,27,30,33 | 13,23,33,43,53,63,73,83,93,103 |
| Ten down | 30,27,24,21,18,15,12,9,6,3 | 93,83,73,63,53,43,33,23,13,3 |

All twenty screen/panel/exact-player assertions passed, and both main/player
PIDs survived. Every recorded service sample still had no received app key.
Initial readbacks and the additional one-second readbacks agreed. The report
has 27/65 passing assertions, 38 incorrect-volume assertions, no acquisition
or cleanup errors, and `success=false`. Returning to origin after symmetric
coarse steps does not establish the configured spacing. This is a completed
failed simulation, not physical-button qualification or a repair.

Private actual-player report:
`/tmp/pvs-normal-screenoff-20261028/bilibili-kernel/playing-app-kernel-1793165139401750000.json`.
The separate wrapper and exact-player before/after audio/session files remain
in the private directory. The generic controlled-playback report is
`kernel/screen-off-kernel-ordinary-both-50-1793164457061571000.json`.

The updated C fixture compiled with `-Wall -Wextra -Werror`. Its default USB
branch was independently checked awake: +/− produced native 40/37 from 37,
with all four kernel edges. A no-input GPIO/PMIC-name uinput clone was rejected
by the physical recorder (`simulated button node`, exit 1); no key or volume
change occurred during that clone test. The first USB reader check captured
only three edges because it started immediately before the first press.
That original failure is retained; a separate repeat allowed reader startup
and passed all five sanity assertions. This is acquisition validation, not
ordinary off-display support.

Cleanup paused introduced Bilibili playback and restored the original ended
video position 540075 ms using inspected player gestures. Independent native
readback was 0/150. Ordinary 50-step preferences, original volume shortcuts/
camera option, USB stay-awake 2, timeout 600000 ms, and the complete enabled
accessibility list were preserved; both GKD and the main service were bound.
The main/helper hashes matched the installed hashes above. The phone was
awake/unlocked, helper/accessibility ready, raw/Shizuku disabled, with no
active playback, panel, correction or key wake lock. Introduced host/observer
packages, probes/dex files and simulated input devices were removed.
All sixteen final restoration assertions passed, with no errors:
`/tmp/pvs-normal-screenoff-20261028/autonomous-final-restoration-1793165460894095000.json`.

No production input change was established by these experiments. The ordinary
screen-off/reboot requirement remains unmet. The current kernel results and
matched policy gates identify where to investigate delivery; they do not
explain the unrecorded working state before reboot or justify inferring keys
from native volume changes.

## Additional vendor filter and log-source investigation

The current device's framework/services/vivo-services SHA-256 values matched
the extracted jars used for this investigation. `vivo.util.FtInputFilterUtil`
does expose an `onInputEvent(InputEvent, policyFlags)` callback, but registration
calls `IWindowManager.setInputFilterByPackage`. The actual server checks
`android.permission.FILTER_EVENTS` before changing its filter. Its separate
`setVivoHoverInputFilter` Binder entry checks the same permission.
The public-looking `InputManagerService.setVivoHoverInputFilter` method is
internal; it is absent from the examined `IInputManager` Binder interface.
The phone's permission listing reports FILTER_EVENTS as signature protected.
These are not established ordinary-helper input sources.

The denied `setInputFilterByPackage` call merely logs a warning and returns.
`FtInputFilterUtil.setInputFilter` then returns true after a completed Binder
call. A successful method return therefore cannot qualify registration; require
actual callbacks and preserved accessibility/filter behavior. No global filter
was replaced in this investigation.

`VivoInputPolicy` contains early WARN records with volume key code, down/up
boolean, canceled flag and device name. Its `VLog` wrapper prepends `_V_`,
so the actual tag is `_V_VivoInputPolicy`, not `VivoInputPolicy`. Retain empty
captures from the incorrect tag separately; they do not prove missing events.
A fresh generic kernel awake down/up probe with the corrected tag still
captured no policy lines, although real readbacks were 100→97→100 and
accessibility corrected both keys. A separate synthetic shell WARN marker was
visible in logcat, confirming the reader backend was alive. A marker is not
key evidence. No Vivo logging property was changed.

READ_LOGS is signature/privileged/development protected on this phone.
An ADB grant alone does not establish unattended background log access:
the matched `LogcatManagerService` auto-approves instrumented clients, asks
confirmation for a new TOP-state client, and declines other new clients.
A test running inside instrumentation cannot certify the ordinary app's log
reader, background start or post-reboot access. No READ_LOGS grant or production
log reader was added; logging configuration and actual ordinary access remain
under investigation.

The first fresh log probe stopped before mutation because the phone's newer
state was native 100 with active media, rather than the earlier idle native 0.
Its setup error was retained. The subsequent awake pair preserved 100 and
the current playback/screen/backend state; its temporary input device and
device-side files were removed. Preserve this newer user state on continuation.
Private records are `log-key-probe-1793167715667272000.json`,
`log-key-probe-1793167772825893000.json`, `log-backend-marker-check.json` and
`vendor-filter-source-hashes.json` under the existing private directory.

## Root-assisted diagnosis without a root production dependency

The user later made KernelSU root available strictly for diagnosis. Production
ordinary mode must still work as an untrusted app without root, Shizuku, USB ADB
grants or another special permission. Root was used only to inject events into
the phone's existing input nodes and inspect otherwise inaccessible evidence.
SELinux remained Enforcing. No root call or permission was added to either APK.

Writing complete down/up pairs directly to the existing built-in nodes avoided
the identity differences of a new uinput device:

| State at native 144/150 | `/dev/input/event1` down | `/dev/input/event0` up |
|---|---|---|
| Awake, idle | 141 with accessibility callback | 144 with accessibility callback |
| Stable OFF and locked, idle | 144, no callback | 144, no callback |
| Stable OFF and locked, controlled AudioTrack | 144, no callback | 150, no callback |

Each off-display run retained all four kernel edges with the real `mtk-pmic-keys`
or `gpio-keys` identity. The controlled AudioTrack remained started through both
keys. Its asymmetric native result is a failed behavior result, not a reversible
step test; cleanup explicitly restored 144. The host playback was stopped.

Matched `PhoneWindowManager` confirms why the two asleep branches differ.
For default display state OFF, `shouldDispatchInputWhenNonInteractive` returns
false before the ordinary input filter receives the key. With active media,
Vivo's later `interceptKeyBeforeDispatching` sends the key directly to
`MediaSessionLegacyHelper` and returns a negative dispatch result, so
`KeyboardInterceptor` drops the accessibility event. Awake injection reaches
the existing service and is corrected normally, excluding a node/parser or
helper-write failure.

Root also enabled checks of candidates that ordinary UID evidence could not
settle:

- `/proc/interrupts`, the GPIO wakeup event counter and IRQ sysfs files are
  mode 0444 but all fail with EACCES in the actual helper UID under SELinux.
- Full `system`, `secure`, `global` Settings and property snapshots had no
  change after separate stable-OFF idle down and up pairs.
- `AudioService.registerAudioVolumeCallback` reports changed group/index only;
  it supplies no key direction and has no idle event.
- The external audio volume controller does receive direction, but registering
  an audio policy as volume controller requires `MODIFY_AUDIO_ROUTING`.
- PhoneWindowManager's parallel `IStatusBarService.handleSystemKey` delivery is
  internal to SystemUI and exposes no ordinary broadcast or provider.
- A remote `MediaSession` receives direction only after claiming an active
  playback state. That would fabricate playback and take volume routing from
  real players, so it remains excluded.

Temporarily setting both `doze_always_on` and
`vivo_integration_aod_enable` to 1 did not keep display 0 in DOZE on this
firmware. After ten seconds it was OFF and neither idle key reached the app.
Both values were restored to 0. Earlier short-settle first-key success remains
consistent with the transition-time DOZE window, not stable sleep support.

BatteryStats and pstore retained pre-reboot history. From 10:20 through 10:24,
the development test host repeatedly entered the foreground, started a test
AudioTrack, turned display 0 OFF and used shell-driven power-key wakeups. The
Shizuku manager process also existed, but no shell server evidence was retained.
This history is automated fixture activity, not proof that ordinary built-in
volume keys worked before reboot. Settings fallback data showed the same enabled
accessibility list, disabled AOD values and Vivo shortcut switch as the current
state. No KernelSU modules or boot scripts altered input or SELinux policy.

The root-assisted evidence establishes the loss point more directly but does
not supply a compliant ordinary input source. Root injection, read-only root
files, a fake active media session and SystemUI internals remain test evidence,
not production dependencies.

## Final constrained ordinary fix and reboot verification

Distributed-APK simulation comparison found that version 1.4.13 corrected a
stable-off controlled-playback coarse jump, while 1.4.14 did not. This does not
reproduce the user's reported pre-reboot physical success: the untouched
task-start package record identifies 1.4.14/code 26.
The final 1.4.15 controller therefore restores that behavior only for media on
Vivo/iQOO when ordinary mode is selected, the helper is ready, the display is
noninteractive, playback is active, audio mode is normal, the current route is
not wireless, and the observed change is one or two native 15-interval steps
or an endpoint-clipped equivalent. It plans from the real pre-jump value and
uses the same origin-anchored rounding history.

Idle handling is separate. A delivered accessibility down/up pair, or a fresh
key-up without its down, may adjust media after a 300 ms settling wait only
while the display is off, playback is inactive, the helper is ready, audio mode
is normal and the route is not wireless. The current native value must still
equal the captured origin. One helper retry is allowed only after an
expected-current rejection, in the same gesture direction, within 500 ms.

Final development main APK:
`ed6724dd30c307e0b13b0442442bcbc5322c16598817175ddf046326a80a4b86`.
Automated results for these exact bytes:

| Check | Result |
|---|---|
| Release units / main and helper lint / builds | 41/41 units; passed |
| AOSP 13 ordinary / frames / layout / screen-off | 17/17, 13/13, 56/56, 16/16 |
| Vivo ordinary helper, 50 and 64 steps | 104/104 each |
| Vivo screen-off ordinary callbacks | 111/111 |
| Actual Bilibili, stable off, ordinary helper | 3→13 corrected to 6 |
| Controlled playback, stable off, direct existing-node simulation | 60→50→57, then 57→67→60 |

The final real reboot changed boot ID from
`3ef38486-fe88-45c3-9f17-409faeb56736` to
`d101645e-ac56-4c29-a438-2fa201e906aa`. Before first credential unlock, the
50-step setting and native 0/150 survived; keeper, accessibility and helper
were ready, while Shizuku stayed disabled. Post-reboot runs repeated 104/104 at
both step counts, 111/111 screen-off callbacks, the actual-Bilibili 3→13→6
correction, and the controlled 60→57→60 reversal. Temporary root was used only
to install test APKs and inject the controlled node sequence; production code
contains no root, Shizuku or USB-grant dependency.

Two limits remain:

- A coarse programmatic media preset during stable-off playback is
  observationally identical to Vivo's routed key change and can trigger this
  compatibility path. Non-coarse changes, awake changes, idle changes, calls
  and wireless routes remain excluded.
- Built-in PMIC/GPIO presses cannot be generated by ADB or root node writes with
  their real hardware IRQ/wake behavior. Ordinary built-in idle and Bilibili
  still require the final physical-button matrix. Until that is recorded, the
  user-reported pre-reboot success is the only direct built-in-button evidence.

## Later physical tests and withdrawn AudioTrack experiment

The later user-assisted recordings supersede the preceding "no direct built-in
evidence" status. After installation of the 300 ms native-settling fix and a
real reboot, the recorder captured a full PMIC down/up pair and a full GPIO
down/up pair while the display was off, ordinary helper/accessibility ready,
and media inactive. Native volume stayed 60; neither key reached accessibility.
This is a failed ordinary idle physical test.

An active remote MediaSession, with and without a brief static AudioTrack
primer, did not receive volume-direction callbacks. The primer made the session
the default media-button session but did not fix the physical idle sequence.
Disabling both Vivo shortcut settings also left the PMIC down key unhandled.
The GPIO up key sometimes produced a native +10 transition, corrected after
settling by the helper.

A separate test-host continuous AudioTrack run produced 66→56→63 followed by
63→73→66 with full physical pairs. This established one controlled-playback
pass; it did not explain the user's historical pre-reboot state. Subsequent
production AudioTrack experiments (static, stream with client mute, stream with
zero PCM without client mute) did not qualify ordinary idle behavior. The final
zero-PCM run began at 66 and ended at 69: the GPIO up coarse change was corrected,
but the PMIC capture contained only UP. Its missing DOWN makes that acquisition
incomplete; no hardware or input-policy cause can be concluded from it.

The failed constant AudioTrack implementation and the test-only primer session
were removed from source. The retained ordinary change waits for native writes
to settle for 300 ms. Ordinary idle support remains unresolved. Bluetooth was
restored to the connected OPPO route at 128, with speaker volume 66 retained;
there were no started AudioTracks after restoration.

The former phone-side shell recorder opened getevent after waiting for sleep,
and could miss the first DOWN. It also stopped early after fixed event counts.
Use `tests/read_volume_events.c` and `tests/record_physical_volume.sh`: the native
reader opens verified platform GPIO/PMIC nodes read-only before recording,
reports READY after both opens, flushes each event immediately, and terminates
normally on SIGTERM or its bounded duration. Its one-second start/finish,
rejection of the headset node, and SIGTERM exit were checked on the phone.
Input acquisition still requires complete down/up pairs before classification.

The first unbuffered 300-second window completed normally with no events and
871 awake/unlocked samples. The application's later up callback occurred after
the reader's finish timestamp and corrected 66→76→69. This recording expired
before the user sequence; its empty event set cannot qualify or fail input
delivery. Open future physical windows only after the user is ready. Native
event/reader timestamps use CLOCK_MONOTONIC, while `/proc/uptime` includes
suspend and must not be compared directly without accounting for that offset.

The next window, started after the user's readiness confirmation, captured
Linux volume-down DOWN/UP and volume-up DOWN/UP with an unbuffered native reader.
All 215 samples were noninteractive/locked, ordinary helper/accessibility ready,
media inactive and volume 66. No new accessibility event or app write occurred.
This is complete failed physical idle evidence on the no-AudioTrack build,
SHA-256 `9b106fc660f34f5042249b9c799cb8f627bf8ed3bd7559abcbcff5ffa591bc51`.
The plain evdev descriptor's event timeval was wall-clock based on this kernel;
use the reader's CLOCK_MONOTONIC receipt timestamp for comparisons until the
descriptor clock is selected explicitly. Bluetooth was re-enabled afterward;
reconnection of an unavailable headset is a separate restoration condition.

## Original-version startup differential after the user's recalibration

The user reiterated that ordinary helper mode worked before this task's first
reboot and asked to investigate startup rather than extending input/audio
experiments. The untouched `initial-package.txt` confirms main 1.4.14/code 26,
last updated 2026-10-27. A task-start extracted APK hash was not retained.
The preserved distributed 1.4.14 APK, SHA-256
`db4602f21945311e6eaf50555e3e17ae2867a133ba662a91f1420bdc6813d72d`,
is therefore the original-version comparison artifact; its version and signer
are established, but byte identity with the untouched installed APK is not.

Before installing that artifact, the newer phone state was snapshotted:
main `9b106fc660f34f5042249b9c799cb8f627bf8ed3bd7559abcbcff5ffa591bc51`,
helper `23852b52e4669f476050affd7476f052323c07992aa6e1e764003a80316a6d80`,
ordinary mode, 50 steps, media only, native speaker 66, Bluetooth enabled,
full main/GKD accessibility list. The main APK and device-protected preferences
were backed up outside Git. A same-signer downgrade retained app data; the
helper was unchanged. No uninstall, clear-data or production code change was
used for this differential.

| Original 1.4.14 state | BUS_HOST uinput down/up, origin 66 |
|---|---|
| Automatic keeper startup after replacement, awake | 63 / 66 |
| After explicitly opening main, awake | 63 / 66 |
| Main opened then stable screen off and locked | 66 / 66, no new app key |
| Main accessibility rebound while stably off | 66 / 66, no new app key |
| Real reboot, first unlock, main not opened, awake | 63 / 66 |
| Real reboot, main explicitly opened, awake | 63 / 66 |
| Real reboot, main opened then stable screen off | 66 / 66, no new app key |

The real reboot changed boot ID from
`1410479d-6b83-4bfa-9ad2-9e065c08304b` to
`837c293e-5659-4bb8-a7ff-dd925721f713`. Before first credential unlock, the
original-version keeper, accessibility and helper were already connected;
native 66 and 50 steps survived, with Shizuku disabled and raw input unready.
After unlock, the same ordinary connections were ready without opening main.
`InputFilterEnabled: true`, capability 8 and `KeyboardInterceptor` were retained.
GKD was not bound in the early first-unlock sample; its enabled entry was
preserved. Missing startup or input-filter installation was not reproduced.
Opening main and rebinding did not restore the tested simulation.

The user then performed three physical down presses followed by three up
presses on the original-version APK after this real reboot. The unbuffered
reader captured all twelve GPIO/PMIC down/up edges. All 147 service samples
were noninteractive and locked, with ordinary helper/accessibility ready and
no playback. The three down presses produced no app callback or volume change.
Only the first up release produced an app key at uptime 247050 ms and a helper
correction to 69; the other two up pairs had no callback. Actual readback stayed
66 before that correction and 69 afterward. Expected volume after a complete
three-down/three-up reversal was 66. This is a complete failed physical idle
sequence on the original-version APK, separate from its failed simulations.

The original-version physical failure does not explain the user's earlier
working state, and does not establish that every later modification is
behaviorally identical. It does establish that reverting to distributed
1.4.14 and opening main does not restore reliable physical control in the
current post-reboot state. No further production workaround was added.
Private snapshots, raw failed runs and review:
`/tmp/pvs-startup-differential-20261028/original-physical-idle`.

Restoration verified the newer main APK's exact
`9b106fc660f34f5042249b9c799cb8f627bf8ed3bd7559abcbcff5ffa591bc51`
hash and unchanged helper hash. Native speaker volume was restored to 66;
Bluetooth remained enabled. Reboot had changed the disconnected A2DP cached
and persisted index from 128 to 110. A shell `Settings` write returned without
changing it; a bounded shell-only `IAudioService.setDeviceVolume` restoration
then read back speaker 66 and A2DP 128 in AudioService and Settings. This
temporary restoration tool is outside the app and was removed from the phone.
No new permission was granted.

Ordinary 50-step/media settings, main and GKD enabled/bound services, timeout
600000 and USB stay-awake 2 were preserved. The pre-existing Shizuku server
used by GKD was restarted with its official APK's shell starter, while the
main app's Shizuku selection remained disabled. Temporary physical captures
were pulled before introduced phone files were removed. The first restoration
install was rejected while unlock was pending; a retry's client timed out
before its label-only confirmation control was handled. The observed native
confirmation completed restoration and the installed hash was independently
verified. These acquisition failures remain separate from the physical test.
The ordinary post-reboot requirement remains unresolved.

### Startup code audit and the remaining historical question

A subsequent read-only audit compared the actual startup entry points.
`BootReceiver`, `FineVolumeAccessibilityService.onServiceConnected` and
`MainActivity.onResume` all start the same `VolumeKeeperService`. Its `onCreate`
constructs the controller, which captures native readback, registers volume
broadcasts and preference changes, starts polling and creates the helper
connection. MainActivity has no additional global key registration.
`VolumeStepSettings` uses device-protected storage; the original-version locked
boot and post-unlock observations already verified the retained 50-step value.
The ordinary accessibility callback forwards received keys to that controller.
The Shizuku bridge checks explicit selection when connecting, reporting ready
and delivering callbacks; this audit found no ordinary-mode raw-reader path.

The matched firmware supplies two distinct screen checks:
`PhoneWindowManager.shouldDispatchInputWhenNonInteractive` returns false when
the target Display reports OFF, before checking keyguard or dreaming.
For active music, later Vivo dispatch policy samples `isScreenOnEarly` and
routes keys directly to media when that sample is false. Accessibility
`KeyboardInterceptor` calls this dispatch policy before notifying services and
drops its negative result. A bound service and enabled native input filter
therefore do not establish delivery of an individual screen-off key.

These facts explain why repeating application startup has not restored the
current failing idle sequence. They do not identify the state that changed
at the user's first reboot. The task-start power snapshot was awake, and no
matched pre-reboot physical trace contains Display state, `isScreenOnEarly`,
playback and key delivery together. Earlier settings snapshots and simulated
passes cannot supply those missing observations.

The user answered this historical question: pre-reboot idle control worked
after the display had been completely black for more than ten seconds.
The brief transition / visible always-on-display explanation does not match
their report. Do not request another
identical original-version idle sequence: its complete physical failure is
already retained. This audit changed engineering notes only; no APK,
permission, service or phone setting was changed.

### Vivo filter restoration probe, 2026-10-29

After the interruption, native volume was 0 rather than the earlier restored
66; retain the newer value. Ordinary 50-step mode, keeper PID 21662, helper and
accessibility connections were still ready. The matched firmware's
`AccessibilityManagerService.fixAccessibilityInputFilter` sets
`mForceSetInputFilter` and reschedules filter installation.
`InputManagerService.setInputFilter` does nothing if its existing Java filter
object is already the requested object. This is therefore a targeted
overwritten-filter recovery, not a general input-policy reset.

A temporary shell-UID reflection probe invoked the Vivo method successfully
without installing an APK or granting a permission. The diagnostic dex was
removed immediately. Do not infer ordinary-app API availability or restored
physical delivery from that successful shell call.

The first automatic check stopped before input because setting USB stay-awake
to 0 was silently ignored. Its failure report was retained. A separate explicit
sleep run left the setting unchanged and verified a genuinely noninteractive,
locked state after ten seconds. Native InputManager's noninteractive display
set contained 0 and 90000; the input filter remained enabled.
BUS_HOST uinput up/down and shell framework-injected up/down both left volume
at 0 and produced no new controller callback. These are four simulated presses,
not physical-button coverage. This recovery did not restore the tested input.
Screen interactivity was restored to its initial locked/on state, volume 0
and USB stay-awake 2 were preserved, the full accessibility list and live
ordinary connections were checked, and introduced phone files were removed.
Raw stages and both acquisition reports remain outside Git in
`/tmp/pvs-filter-owner-20261029`. No production code was changed.

A subsequent environment check found `/system/bin/su` available again:
diagnostic `su -c id` returned UID 0 and SELinux context `u:r:ksu:s0`.
Kernel boot ID remained `837c293e-5659-4bb8-a7ff-dd925721f713`, with the same
Vivo firmware fingerprint. This was a newly observed environment change,
not root acquired by this investigation. Keep it separate from ordinary-app
verification; do not alter root configuration or other agents' phone work.

### Actual filter identity and live policy trace, 2026-10-29

The installed filter was inspected in system_server with the already available
diagnostic root. InputManagerService and AccessibilityManagerService referenced
the same AccessibilityInputFilter object, identity 129789425. Both installed
flags were true, enabled features were 20, KeyboardInterceptor existed, and
the separate Vivo hover input filter and its host were null. This confirms
actual filter ownership in that snapshot, beyond the native enabled boolean.
The same object remained installed after eleven seconds genuinely asleep:
Display state OFF (1), screenOnEarly/fully/awake all false, ordinary helper
ready, Shizuku selection false, idle native volume 0.

Bounded Java logging hooks called every original method and retained its return
value. They did not intentionally change policy, fields or permission grants.
BUS_HOST uinput supplied a distinct native-input trace:

| State/input | Actual path and result |
|---|---|
| Awake up/down | Queue policy returned 1; filter flags 0x66000000; KeyboardInterceptor delay 0; AccessibilityManagerService.notifyKeyEvent returned true. Readback 0→3→0. |
| Asleep up DOWN/UP | Vivo shortcut hook returned false; shouldDispatchInputWhenNonInteractive returned false; queue policy returned 0. InputManager and AccessibilityInputFilter received flags 0x06000000, without PASS_TO_USER (0x40000000). No KeyboardInterceptor or service notification followed. Volume stayed 0. |
| Asleep down DOWN/UP | Vivo shortcut hook returned true, queue policy returned 0; filter again received 0x06000000 and did not notify KeyboardInterceptor. Volume stayed 0. |

The separate shell-injected pair bypassed the filter even in the awake control.
Its asleep up DOWN returned queue result 1, then Vivo dispatch repeatedly
returned delay 50 before -1; up release returned 1073741824. Its down pair was
dropped by the Vivo shortcut hook. Do not generalize this framework-injection
trace to native/physical delivery or substitute it for the uinput trace.

The current shortcut values were system smartkey_primary_switch=1 and secure
quick_start_camera_switch=2, matching live one-key and camera flags true.
They were the restored user settings, not a demonstrated stale observer.
A shell Settings write to disable the smart key was ignored; that acquisition
failure was retained and the intended disabled-shortcut cell did not run.

#### Diagnostic crash and independent CLI continuation

After the logging session detached, system_server PID 1787 crashed with
SIGBUS/BUS_ADRALN at 2026-10-29 10:26:58. The stack begins with an unknown PC
followed by ART invocation/interpreter and java.lang.reflect.Array.newArray.
It establishes a native crash during this diagnostic phase, not a proven
single-method cause. Observational Frida scripts still instrument ART; calling
the Java queries read-only does not make attachment harmless. The server was
terminated, its port forward and introduced files removed, and no further
system_server attachment was attempted.

Android framework restarted with system_server PID 12686 and zygote PID 12535;
kernel boot ID stayed 837c293e-5659-4bb8-a7ff-dd925721f713. Record this as a
framework restart, not another physical reboot. Main/keeper, helper and GKD
reconnected, and BOOT_COMPLETED was subsequently observed. Volume 0, 50-step
ordinary mode, enabled accessibility list, shortcut values 1/2, timeout 600000,
USB stay-awake 2 and initial locked/on state were verified after recovery.

An independent CLI-only continuation then used diagnostic root to change the
two Settings values to 0 and verified actual Settings readback. It installed
no APK, granted no permission and used no Frida. BUS_HOST uinput up/down while
awake again read 0→3→0. After eleven seconds asleep, both directions remained 0
with no new app key. Thus disabling the shortcuts did not restore the tested
ordinary idle input after framework restart. It does not qualify physical
buttons or identify the user's pre-reboot working state.

Both original Settings values were restored and read back; the full enabled
accessibility list, main/helper/GKD connections, timeout, USB setting, native 0
and no pending correction/wake lock were checked. Introduced uinput/dex/server
files were removed. Raw queries, complete failed traces, ignored-write report,
crash log and CLI rerun are retained privately in
`/tmp/pvs-filter-live-20261029`. No production code or APK changed in this phase.
Ordinary physical screen-off and reboot repair remains unresolved.

Offline decompilation of the phone's vivo-services.jar then resolved the
up-key delay to VivoVolumeUpKeyHandler, registered for key 24 by
VivoWMPHookCreator. Its screen-off intercom branch returns delay 50 while
awaiting long press, -1 after consumption, and 1073741824 for a consumed
release. This is consistent with the retained shell-injection trace; actual
private listener state was not read, so do not assert exact runtime ownership
from the static match alone.

Global button_screen_intercom is absent (Settings reads null). The handler
initialization defaults it to 1, while its ContentObserver defaults it to 0.
This is a concrete firmware initialization/observer difference worth preserving,
but does not prove the user's historical delivery changed for this reason.
The proposed intercom-setting comparison was not executed: adding then deleting
the record can leave a different cached default in the running handler.
No test result or restoration claim applies to that unperformed cell.

On the next explicitly goal-driven continuation, ADB initially reported the
same connected device, boot ID and system_server PID 12686, with root available,
native 0, 50-step ordinary helper/accessibility ready and button_screen_intercom
still null. Read-only review confirmed FtInputFilterUtil.setInputFilter calls
WindowManagerService.setInputFilterByPackage, which requires FILTER_EVENTS.
A helper's Vivo package prefix does not bypass that input permission check.

Before any new setting change, probe installation or reboot, the explicit
device disconnected and three adb-devices checks returned no devices.
The intended intercom comparison plus normal-reboot restoration was not
executed. The user had explicitly required stopping when manual reconnection
was needed; USB reconnection was requested. No production/APK change or
phone-state mutation occurred in that continuation. On reconnect, snapshot the
new state before pursuing the retained initialization difference.

### Three-shortcut comparison after USB reconnection, 2026-10-29

The user reconnected USB and enabled debugging. The selected Vivo returned on
transport 33 with the same kernel boot ID, native speaker 0/150, 50-step
media-only ordinary mode, main/helper/accessibility ready, no playback and an
unlocked/on display. The complete enabled list still contained main and GKD.
Disconnected A2DP's current cached index was 110; preserve this newer baseline,
not the earlier session's restored 128. Main/helper APK hashes were unchanged.

A new bounded CLI probe used the already available diagnostic root to set
smartkey_primary_switch, quick_start_camera_switch and button_screen_intercom
to 0. All three actual Settings readbacks were verified. It installed no APK,
changed no main preference or permission, and used no system_server attachment.
A safe test-only idle preset supplied room for both directions:

| State | External BUS_HOST uinput | Native readback |
|---|---|---|
| Awake | Up | 30→33 |
| Awake | Down | 33→30 |
| Completely asleep for eleven seconds | Up | 30→30 |
| Still asleep | Down | 30→30 |

The asleep samples contained no new controller key. This is a failed external
input comparison with all three shortcuts off, not physical-button evidence.
The test restored native 0 and the original Settings values 1/2/null. Deleting
the intercom record does not restore its constructor default in the live
observer, so an authorized normal reboot followed.

The new kernel boot ID was ef5b741d-3f95-460c-bdcd-c7c7190a7105. The keeper and
helper started before first unlock and remained connected afterward without
opening main. Later UI readback verified enabled 50 steps and media-only scope;
the app's Shizuku selection stayed disabled. Speaker 0, A2DP cache 110, timeout
600000, USB stay-awake 2, original shortcut values and APK hashes were retained.
Main and GKD were both enabled and bound. The original exact-string accessibility
comparison failed because Android changed ordering and shortened the main class
to `.FineVolumeAccessibilityService`; resolving component names confirmed the
same two services. Keep both comparison reports.

The first restoration audit failed when `/system/bin/su` was no longer present
after reboot. The complete private preference-file byte comparison could not be
repeated. No probe wrote main preferences; final native/UI diagnostics confirmed
the relevant saved choices. Root configuration was not modified or reacquired.
The pre-existing Shizuku server used by GKD was restored with its official APK
starter while main stayed in ordinary mode. The first started server later
disappeared for an unestablished reason; a retained retry verified both its
server and GKD user service alive at successive checks and the final audit.

A separate rootless probe confirmed the shell UID can open and read both
verified platform GPIO/PMIC nodes. Attempting O_RDWR to simulate a press on the
original node failed with EACCES before any key was emitted. The independent
reader captured zero volume edges. Retain this as an acquisition failure,
not a failed physical sequence. Its preset was restored to 0 and introduced
phone files were removed.

#### Native intercom control and ignored shell writes

The preinstalled `com.vivo.walkietalkie/.HomePage` opens without activating
radio communication. Its **我的 → 熄屏下快捷对讲** switch was inspected and
showed enabled while button_screen_intercom remained absent. The inner switch
was identified separately from its large preference row. It was not toggled
in this continuation. No channel was joined and the **立即开启** action was
not used.

The matched SettingsProvider.apk contains an additional shell-write guard.
PUT_global, PUT_secure and PUT_system return without writing when calling UID
2000 and VivoCommonManager's `is_shell_appproc_running` query reports true.
The live shell intercom PUT returned exit 0 but left the value null with the
Shizuku server present. The manager's actual query result was not captured;
the matched guard is consistent with this ignored write. The matched DELETE
call has no equivalent early guard and still enforces WRITE_SECURE_SETTINGS.
Shell holds that permission on this phone. Native UI remains the verified way
to operate the shortcut switches; verify deletion readback during restoration.

Raw results, original acquisition failures, both restoration comparisons and
matched provider source remain privately under
`/tmp/pvs-filter-live-20261029/intercom-cli-1793242973618817000` and
`/tmp/pvs-filter-live-20261029/original-node-1793243368582823000`.
A rootless three-shortcut physical-test preparation script is staged privately,
but has not run. No physical recorder is active and all shortcut values remain
original. The next manual step is user readiness for three physical down presses
then three up presses with all three shortcuts disabled, idle and truly asleep.
Start acquisition and verify readiness before asking for those keys. This is
a new physical condition; do not substitute the previous original-version
failure or external simulation for its result. No production code/APK changed
in this continuation; ordinary physical screen-off and reboot repair is pending.

### Completed physical three-shortcut test, 2026-10-29

After the user explicitly said they were ready, preparation initially failed
before changing any shortcut: a Vivo USB connection chooser obscured Settings.
The inspected chooser's **取消** control was dismissed and the setup rerun.
Keep that original acquisition failure separately.

The rerun used the native Settings and walkietalkie inner switches to disable
all three shortcuts and verified actual values 0/0/0. The recorder ran as shell
UID, without root. It opened both verified platform GPIO/PMIC nodes before the
test and had emitted no volume edges when readiness was confirmed. Native
speaker was preset to 30/150 with 50 configured media steps; ordinary helper
and accessibility were ready, playback inactive, and the display had been
noninteractive for eleven seconds before the user was asked for input.

The user pressed down three times then up three times. All twelve native edges
were captured: six complete down/up pairs, with no missing first DOWN. The
expected sequence was 30→27→24→21→24→27→30. Actual native and independent shell
readbacks stayed 30 throughout; the app received no key or correction request.
The endpoint coincidence after six presses is not evidence of delivery.
The recorder's 317 samples all retained ordinary mode, idle playback, connected
helper/accessibility and native 30. Its first sample was on before the explicit
sleep; the following 316 were noninteractive, including the physical sequence.
The reader reported no error, and the stop/complete protocol finished normally.

Thus disabling all three shortcuts does not restore this complete physical
idle sequence on the current main/helper bytes. This does not identify the
user's historical pre-reboot working state.

The full phone recording was pulled before cleanup. Native volume 0, original
smart-key/camera values and human-street-camera selection were restored through
the native UI. Shell DELETE successfully removed the intercom record while
Shizuku was running; an authorized real reboot restored its constructor default.
Main/helper started before first credential unlock and remained ready afterward.
The complete resolved main/GKD accessibility component set, ordinary selection,
50 steps, speaker 0, A2DP cache 110, timeout 600000, USB stay-awake 2 and both
APK hashes passed the final audit. The pre-existing Shizuku/GKD shell services
were restored while main remained ordinary. No APK or production code changed.
All introduced phone recorder/dex files were removed.

Evidence is private under
`/tmp/pvs-filter-live-20261029/intercom-physical-1793245035392925000`; the
earlier acquisition failure is under
`/tmp/pvs-filter-live-20261029/intercom-physical-1793244962562898000`.
Record idle and actual-player physical results separately.

The original reader's event timeval was wall-clock based, while its receipt
time was monotonic. This run's raw records are retained unchanged. The diagnostic
reader now requests CLOCK_MONOTONIC with EVIOCSCLOCKID on each read-only descriptor
and fails setup if that request is rejected. This changes the reader's own
timestamp selection, not Android policy or input generation. Its one-second
rootless open/finish check passed on both physical nodes after compiling with
`-Wall -Wextra -Werror`. A subsequent physical run is needed to verify event
timestamps with actual edges; the earlier timestamps must not be relabeled.

### Actual Bilibili physical sequence after the user's restart request

The first playback window expired with zero physical volume edges. The user
explicitly reported unplugging the phone and requested a fresh test because too
much time had elapsed. Preserve that window as incomplete acquisition, not an
input pass/failure. USB returned on transport 38 with the same kernel boot ID.
The native speaker value 3 was the unfinished test preset; it was restored to
the saved 0 before the next window. In the intervening period Bluetooth had
connected/disconnected and the A2DP cache became 100; preserve that newer route
state rather than overwrite it with the earlier 110. The new test used speaker.

The existing Bilibili video was paused, with no active media. Its inspected
native **播放** button resumed it, and exact UID 10811 owned a started
USAGE_MEDIA AudioTrack after Home and stable screen off. A fresh rootless
monotonic recorder was opened before requesting ten physical up presses and
ten down presses. It also sampled AudioPlaybackConfiguration alongside native
readback so exact-player ownership could be checked throughout.

All forty native edges were captured: twenty complete pairs with no missing
first DOWN, held key or extra repeat. Expected native sequence at 150/50 began
3→6→…→33, then 30→27→…→3. The first ten up presses and first four down presses
reached their expected values. Physical down number five, overall press fifteen,
at monotonic 2472.139460–2472.297124 seconds, produced no AudioService adjustment
request, no native volume change and no accessibility callback. Volume remained
21 instead of expected 18. The following five down presses each triggered a
normal coarse transition and helper correction, ending at 6 instead of 3.
The app made nineteen corrections. Fourteen of twenty expected settled values
matched; six later values were wrong after the missed press. This is a complete
failed physical playback sequence, not a 19/20 pass.

All 503 samples retained Bilibili's exact active media UID, globally active
playback, connected helper/accessibility and ordinary mode. The first sample
preceded sleep; the other 502 were noninteractive. The app/player PIDs were
unchanged at acquisition, and the main PID remained unchanged at restoration.
The inferred compatibility path supplied the corrections; no ordinary
accessibility key callback was observed. AudioService's original volume log
contained ten native raise requests and nine native lower requests, followed
by the corresponding helper writes. The absence at the fifth down is before
the app's write stage, not an inferred helper rejection.

A new `com.vivo.SmartKey` process appeared in system logs near the missed press.
No retained policy line directly assigns that press to the shortcut, so this
is a comparison candidate rather than an established cause. The proposed next
physical playback comparison disables the two volume-down shortcuts while
leaving the absent intercom record untouched. Its result is still pending.
Idle screen-off failure remains separate.

The corrected recorder's forty physical edges used monotonic event and receipt
times; maximum receipt delay was 0.004174 seconds. This verifies timestamp
selection with actual hardware input, without rewriting the older recordings.
The reader had no errors and stop/complete finished normally. A logcat auxiliary
kernel-log permission warning was retained alongside the working app-tag stream.

Capture and original failed sequence:
`/tmp/pvs-filter-live-20261029/bili-physical-1793248289343725000`.
Expired earlier window:
`/tmp/pvs-filter-live-20261029/bili-physical-1793245809381858000`.
After the completed run, the test-started player was paused, native speaker
returned to 0, original shortcuts/intercom/USB/timeout/accessibility components
and both APK hashes passed the audit, and introduced phone files were removed.
No APK or production behavior changed in these physical-test continuations.

### Physical Bilibili repeat with volume-down shortcuts disabled

The next user-assisted playback run used the same main/helper APK hashes,
ordinary mode, 50 configured steps and speaker range 0–150. The native UI
disabled smartkey_primary_switch and quick_start_camera_switch, with actual
readback 0/0. The intercom record remained absent throughout; no reboot was
needed for this comparison. The actual Bilibili video was resumed with time
remaining before a new rootless physical recorder was opened.

All forty volume edges were recorded as twenty complete pairs. Every expected
settled value matched: 3→6→9→12→15→18→21→24→27→30→33, then
30→27→24→21→18→15→12→9→6→3. The independent final Android readback was 3.
Twenty native coarse transitions produced twenty ordinary inferred corrections;
there were no explicit accessibility key callbacks. The native reader reported
no errors and completed the stop protocol. This run had 288 samples, all with
the actual Bilibili UID owning active USAGE_MEDIA, connected helper/accessibility
and ordinary mode. Its first sample was before sleep; the following 287 were
noninteractive. Maximum monotonic event/receipt delay was 0.007208 seconds.

This is a passed physical playback sequence with those two shortcuts disabled.
One failed enabled-shortcut run and one passed disabled-shortcut run do not
establish a deterministic cause or certify all playback timing/hold/route
cases. Retain both original traces. Idle screen-off still failed in the earlier
all-three-shortcuts-off physical sequence; this playback pass does not repair it.

The player was paused, native speaker restored to 0 and both shortcut settings
and the saved human-street-camera choice restored. Native intercom stayed absent.
The audit verified original settings, main/GKD enabled and bound components,
ordinary/helper readiness, main PID, boot ID, APK hashes, no pending correction
or key wake lock and removal of introduced phone files. Evidence:
`/tmp/pvs-filter-live-20261029/bili-physical-1793248868007867000`.
No APK or production code changed.

The previous automatic idle setup used key 223; PowerManager reports its
reason as `sleep_button`. Matched PhoneWindowManager.PowerKeyRule.onPress calls
VivoInputPolicy.notifyScreenOff after processing a power press. That path posts
VivoPolicyUtil.sendPowerKeyScreenOffBroadcast. The sleep key does not execute
that PowerKeyRule. This is a concrete entry-path difference worth a physical
power-button idle comparison; it does not prove the broadcast changes volume
policy. A private rootless recorder for that comparison retains power edges and
PowerManager's actual sleep reason, preserving original shortcuts. Its result
is pending. Keep the user's historical black-idle success as reported.

### Completed physical-power idle comparison

The next user-assisted recorder opened the physical PMIC/GPIO nodes while awake,
with no playback, ordinary helper/accessibility ready, saved shortcuts untouched
and native speaker preset to 30. The user pressed the physical power button,
waited with the display black and pressed three down then three up.

The recorder captured one complete power pair and six complete volume pairs.
PowerManager reported `mLastSleepReason=power_button` and sleep time matching the
physical power DOWN. The first volume DOWN arrived 16.1272 seconds after power
release, beyond the requested twelve-second black-screen wait. Native and
independent shell readbacks stayed 30 for the entire sequence. The app-tag
stream was empty: no new key callback, native observation or helper correction.
The six-press endpoint again matches the start by coincidence, not reversal.
This is a complete failed physical idle sequence with the actual power entry.

All 259 samples were idle with helper and accessibility connected. The first
78 were awake while awaiting the user; the following 181 were noninteractive.
The physical volume sequence falls in that latter interval. The reader had
no errors, all monotonic event records were retained, and stop/complete finished.
The matched power-only screen-off broadcast therefore does not itself establish
restored ordinary idle delivery on this phone; changing from the ADB sleep key
to the physical power key did not recover this test.

The original native 0, ordinary choice, enabled main/GKD component set and actual
bound services, shortcuts 1/2/null, USB 2, timeout 600000, main PID, unchanged
kernel boot ID and both APK hashes passed restoration. No reboot was needed and
introduced phone files were removed after pull. Evidence:
`/tmp/pvs-filter-live-20261029/physical-power-idle-1793249234554850000`.
No production code or APK changed. Continue investigation; do not request an
identical idle sequence without a new relevant condition or implementation.

### Further matched policy and parallel notification audit

The completed physical-power failure was followed by offline examination of
the same matched firmware, without another physical sequence. JADX simple
instruction mode recovered `VivoWMPHook.checkDropKeyBeforeQueueing`, which the
normal Java restructuring mode had left undecompiled. Its volume-down paths
handle smart-key/camera state, call/music/alarm/silent playback, last mode,
underwater camera and DOZE. The recovered method contains no accessibility or
TalkBack bypass. Its separate enterprise volume-up registration reads
`DevicePolicyManager.getInfoDeviceTransaction` and invokes the configured
package's long-press service; it does not provide general short up/down events.
Do not add that enterprise setup as an ordinary-app requirement.

`VivoWMPHookCreator.createInterceptKeyHandler` creates the key handlers inside
system_server, then `VivoInterceptKeyRegister.hookupListeners` registers the
same in-process object with `VivoWMPHook`. `IVivoKeyBeforeQueueingListener` is
an internal callback in this examined chain, not an exposed Binder
registration for independently installed apps. Finding its public Java method
does not establish that the helper can obtain the live system_server object.

PhoneWindowManager also reports keys before the later Vivo queue policy to
three internal modules. The matched implementations were traced separately:

- `GameInjectorImpl` calls `GameManagerService.notifyKeyEvent`, which forwards
  to the game's MEMC controller. The exposed `IGameManagerCallback` methods
  return picture-processing states (MEMC/VSR/VAA/XPQ/texture SR), not key events.
- `QoSInjectorImpl` calls `QoSManager.notifyKeyEvent`; it sends the event to
  internal face/fingerprint-unlock scenes and handles a back-key UI scene.
  Its scene registration is not an established ordinary volume-key listener.
- `VivoStatsInServerImpl.noteInput(int, boolean, boolean)` reports DOWN to
  the internal power event manager as `POWER_ID_INPUT` with `flag` and
  `keycode`. `VivoEventManager.registerEventsListener` looks like a candidate
  app API, but the actual `com.vivo.services.pem.VivoEventService` rejects
  registration whenever `UserHandle.getAppId(Binder.getCallingUid()) >= 10000`,
  throwing `IllegalArgumentException("calling uid not allow")` before posting
  registration. This is a real caller-UID gate, with no package-string
  exemption. No live helper-UID invocation was performed in this continuation.
  `getStats` has no `POWER_ID_INPUT` case and returns no key history for it.

The previously inspected `FeatureVolume.VOLUMEBAR_REBOUND_DYNAMIC_ACTION`
includes direction/action extras, but is emitted by the audio adjustment path.
It does not create an event for an idle key already dropped before audio.
No broadcast alone was promoted to physical-input evidence.

System service enumeration confirmed `vivo_event_service` exists on the
selected phone. Reading the system classpath and pulling the immutable
services-adapter.jar and PowerEffectManager.apk added no app, grant or
setting. The event-service implementation was ultimately found in the
already matched vivo-services.jar; the pulled APK was not installed.
New offline sources and retained original decompilation failures are private
under `/tmp/pvs-filter-live-20261029`.

ADB then lost the selected Vivo while only these reads were in progress.
Three subsequent `adb devices -l` checks listed the emulator alone; the phone's
`/system_ext/framework`, `/vendor/framework` and `/product/framework` listings
failed with `device not found`. No test recorder, playback, APK installation
or setting mutation was started in this continuation, and the preceding
physical-power restoration was already complete before these read-only calls.
Independent offline examination continued through the event-service UID gate.
USB reconnection is required for any new live evidence. Snapshot the current
user state after reconnecting; do not repeat the same physical idle sequence
without a relevant new condition or implementation. Ordinary idle screen-off
and reboot repair remains unresolved.

### Reconnected own-UID event-service check

The user reconnected USB and enabled debugging. Transport 39 returned the same
V2419A fingerprint, kernel boot ID and main PID 4006. The newer actual media
route was Bluetooth A2DP at 106/150; speaker's inactive cached index was 0.
Ordinary mode remained selected with 50 steps, media scope, connected helper
and main/GKD accessibility. Preserve 106 and the existing connection rather
than applying the earlier speaker-test preset or disconnecting the headset.
The initial audio snapshot had a started, client-muted media AudioTrack owned
by the installed Xianyu UID 10656. It later became inactive during this
continuation; no play/pause command or test AudioTrack was issued. Do not
assert playback continuity or silently resume that player.

The read-only `HelperInputAccessInstrumentation` now optionally attempts a
bounded `POWER_ID_INPUT` registration in the actual installed helper UID,
with a callback counter and explicit unregister. It requests no new permission
and reads no other event content. The first manager-reflection attempt failed
at `VivoEventManager.getInstance(Context)` with NoSuchMethodException before
registration. Keep that acquisition failure separately from the next result.
The rerun used the matched `IVivoEventService` Binder descriptor/transactions:
registration 1 and unregister 2. These calls still originate in helper UID
10322, not shell or a supplied package string.

The rerun found the actual `vivo_event_service` Binder and received
`IllegalArgumentException: calling uid not allow` from registration.
Unregister returned normally, zero callbacks were counted, and instrumentation
completed with code 0. Thus the real helper UID is denied this candidate,
matching the static server UID check. Successful instrumentation completion is
capability evidence, not volume-key or screen-off qualification.

The same own-UID probe confirmed MODIFY_PHONE_STATE, STATUS_BAR_SERVICE,
MODIFY_AUDIO_ROUTING and FILTER_EVENTS ungranted, alongside the previously
recorded input permissions. Both reselected GPIO/PMIC volume nodes still failed
open with EACCES. The matched `AudioService.forceVolumeControlStream` requires
MODIFY_PHONE_STATE before changing state; it silently returns for a denied
caller. `setVolumeController` and `setVolumePolicy` require STATUS_BAR_SERVICE.
No forced stream or system volume-controller mutation was attempted.

VivoCommonService.doCommonJob exposes feature/storage queries and the known
shell-process-state query, with no general key subscription in that examined
entry. The volume-down long/double shortcut runnables launch the smart-key or
camera component and reset their counters; they do not re-inject an ordinary
short down press. The separate short volume-up reinjection belongs to the
enterprise long-press configured-package path. No shortcut setting or
enterprise policy was changed to test this asymmetrical path.

The newly built same-signed helper test APK was installed through the inspected
native installer, then removed after the two probe runs. Production main/helper
APKs were not replaced. Both original hashes remained unchanged. The final
audit retained volume 106, original shortcut/timeout/USB settings, the complete
enabled and actually bound main/GKD services, ordinary helper readiness, no
pending correction or key wake lock, and the same main PID/kernel boot ID.
The host/UI observer already present at reconnection were preserved.

Release main/helper builds and lint completed successfully with no errors;
existing warnings were 3 main and 1 helper. Release units were up-to-date and
their cached reports contained 41 tests, zero failures/errors. The changed
helper test compiled and its actual own-UID rerun completed as above.
The rebuilt production helper's different byte hash was not installed.
No new physical sequence, APK fix, vendor certification, commit or publication
occurred. Raw probe, installer, identity and final audits are private under
`/tmp/pvs-reconnected-20261029-1793253908029040000`.

The user then explicitly confirmed that a Bluetooth headset was connected during
the reported pre-reboot black-idle success (“连着”). Preserve this alongside the
ordinary helper, no-Shizuku, black-for-more-than-ten-seconds and no-playback
clarifications. The exact historical headset identity and internal route/session
state were not recorded. The completed physical idle failures used the speaker;
they do not establish the Bluetooth idle result. This newly confirmed route
condition selects a physical Bluetooth idle comparison without fake playback.

Before that comparison, the phone still reported Bluetooth A2DP, native 106/150,
50 steps, inactive playback and ready ordinary helper/accessibility. Production
ordinaryScreenOffBaseEligible currently excludes wireless routes both for
delivered idle fallback and screen-off settling; active coarse-transition
inference has its own wireless exclusion. Collect actual key delivery/native
changes before selecting a narrowly evidenced controller change. Do not remove
the active inference exclusion merely because a headset was historically present.

### Completed physical Bluetooth idle comparison

The user completed the requested physical power-off, black-screen wait, three
down and three up sequence with OPPO Enco X3 still connected. No test AudioTrack,
play/pause command, headset disconnect or production APK replacement was used.
The current Bluetooth native 106/150 was preserved without a preset; ordinary
helper, 50 steps and the saved volume shortcuts stayed selected.

The rootless shell observer opened the verified original GPIO/PMIC nodes with
per-descriptor CLOCK_MONOTONIC. It recorded a complete power pair and six
complete volume pairs. First volume DOWN was 22.247397 seconds after power
release. PowerManager's sleep time matches the physical power DOWN within 2 ms
and the sleep reason is power_button. All samples around the volume sequence
were noninteractive. Shell readback and independent controller readback remained
106 throughout; expected individual values were 103,100,97,100,103,106.
The last matching endpoint is again not evidence of reversal or delivery.

The app-tag log was empty, with no new controller key, native observation or
helper correction. All 146 complete samples retained Bluetooth A2DP, inactive
media and connected helper/accessibility; 101 samples were awake while awaiting
the user and 45 were noninteractive. Each press has surrounding native readback,
power and audio samples in physical-review.json. A separate read-only shell
AudioSystem probe reported media_local=false, media_remote=false,
voice_local_50ms=false, alarm_local=false and alarm_remote=false around every
press. Paused Bilibili and Xianyu audio configurations were preserved; Bilibili's
media session remained PAUSED. A connected headset alone did not activate the
remote-media predicate checked by the matched VivoInputPolicy.isMusicActive.
This is sampled activity evidence, not continuous tracing between samples.

The native reader finished with status 0 and no errors; stop and cleanup
completed. A bounded watchdog restored the saved USB stay-awake value 2 even
if the user response arrived after recording. The final audit restored awake/
unlocked state, retained volume 106, all shortcut/timeout/accessibility settings,
actually bound main/GKD, ordinary helper readiness, no pending correction,
overlay or key wake lock, the same main PID and kernel boot ID, and both
original APK hashes. Introduced phone recorder/dex/watchdog files were removed
after pull; previously present host/UI tools were retained.
Evidence: `/tmp/pvs-filter-live-20261029/physical-bt-idle-1793255170575900000`.

Further offline inspection found RecordLKBVolumeKeyEvent in
VivoInputPolicy.interceptKeyBeforeDispatching, not in its earlier queue method.
VivoAudioPolicyServiceImp saves the owning package for down events and forwards
key code/action to internal audio-anchor metrics. This examined path supplies no
ordinary idle source for keys already excluded before dispatch. No audio-policy
mutation or listener registration was performed for it.

Both completed physical speaker and Bluetooth idle sequences now fail after
this reboot, while the user's historical pre-reboot success with a headset
remains accepted. Its distinguishing internal state has not been identified.
There is no delivered key or native transition here that would justify merely
removing the controller's wireless guard. No production behavior fix, fresh
automated release qualification, vendor-support claim, commit or publication
was made in this continuation. Do not repeat this identical six-press condition
without a newly evidenced input source, policy state or implementation change.

### Revised authorization for the next investigation

The user subsequently explicitly authorized an initial, one-time ADB permission
grant for ordinary helper setup. Production must retain the grant across app and
phone restarts without another authorization, must not require root, and must
not require Shizuku when ordinary mode is selected. Dirty Frag VR root through
the separate privilege assistant is authorized for diagnosis only. This revises
the earlier no-ADB-grant investigation constraint; do not apply that old
constraint to newly authorized candidate permissions. Do not attach Frida to
system_server again: the earlier framework SIGBUS remains an independent
diagnostic-method finding.

The new goal requires continuing until either necessary manual user action or
a proven fix with all required cases passing. A negative candidate result is
not by itself a reason to stop.

Fresh selected-phone snapshot retains native Bluetooth 106/150, 50 steps,
ordinary selection, ready helper/main accessibility and awake/unlocked state.
Actual permission metadata allows shell-grantable DUMP, READ_LOGS and volume
long-press/media-key listener development permissions. MONITOR_INPUT is
signature|recents; FILTER_EVENTS and MANAGE_KEY_GESTURES are signature-only.
The matched input Binder dump checks DUMP and reports physical keyboard
mapper KeyDowns before dispatch. Its RecentQueue is later dispatcher history
and cannot by itself establish input for an event dropped before dispatch.
Investigate the former under the real helper UID and with physical direction/
release evidence, independently of READ_LOGS instrumentation auto-approval.

### Own-UID DUMP candidate, physical qualification pending

A debug-only helper manifest requests DUMP; production release permissions
remain unchanged. The original installed helper APK was saved privately before
upgrading to the same-certificate debug APK. Shell pm grant then succeeded and
package manager reported android.permission.DUMP: granted=true for the actual
helper. This is newly authorized initial configuration; post-reboot persistence
and production key handling have not yet been qualified.

InputDumpInstrumentation uses the actual helper UID 10322, no adopted shell
identity, root or Shizuku. It calls the input Binder's dumpAsync through a bounded
pipe, selects only gpio-keys and mtk-pmic-keys with verified platform sysfs roots
and IsExternal=false, and records their raw KeyDowns sections plus request/
completion times. It grants no permission and makes no audio write. The first
two-second run completed with code 0, 68 samples, mean dump cost 7 ms and maximum
13 ms. Both expected physical mapper records were present. This establishes
own-UID dump access and measured awake cost, not key delivery, edge reliability
or screen-off/reboot support.

The initial debug lint failed on its requested development DUMP permission and
an existing older-API receiver-registration branch. The failure was retained.
The DUMP suppression is scoped to the debug manifest's explicit development
request; the receiver method now documents and suppresses the false-positive
warning for the API 28-32 branch (API 33+ already sets the exported flag).
Debug helper/test build and debug lint then passed. Release main/helper builds,
lint and unit tasks also completed; cached release unit results are not a new
physical qualification.

During this work newer live physical adjustments changed native Bluetooth
volume from the initial 106 to 150. They are retained in keeper diagnostics and
were not generated by this read-only probe. The latest physical-recording
snapshot therefore preserves 150 rather than restoring 106. A first recording
setup found active playback and exited before any phone recorder or USB setting
change; that snapshot is retained separately. A subsequent fresh inactive
snapshot passed and started both recorders.

Current pending physical run uses native shell GPIO/PMIC evidence alongside
helper-UID mapper samples, original shortcuts, ordinary mode and Bluetooth
route/power readbacks. Both readers were verified ready while awake. The native
recorder's cleanup restores saved USB stay-awake 2 after its bounded window.
Main/helper were connected after the first instrumentation completed. This is
a new physical-input source comparison, not a repeat of the unchanged
accessibility-only failure.

Private evidence and original APK:
`/tmp/pvs-adb-once-1793256090301444000`.
Pending native phone directory:
`/data/local/tmp/pvs-dump-physical-1793256687971277000`.
Pending own-UID directory:
`/storage/emulated/0/Android/data/com.vivo.finevolumesteps.helper/files/input-dump-1793256688059`.
The installed debug helper hash is
`dda3311eb3190db9c137a7e5783c7a592bde612c0b7d0d4d8f3f959f72c5ca8a`;
test APK hash is
`6c290e5bee6c798f1814ffb0fb1957e0b104106765ef877f4b7e62d3ed77e3e9`.
Main APK remains unchanged. Do not remove pending fixture files or revoke the
new test grant before collecting the physical result; afterward restore the
latest user state and either retain a justified candidate or restore the
original helper and remove introduced test artifacts. No commit/publication.

### First DUMP physical window expired before the volume sequence

The user reported completing the buttons. The 300-second independent native
record captured a physical power pair at 11012.603399/11012.753881 seconds, then
finished at 11017.775164. Only 5.021283 seconds remained after power release;
the requested fifteen-second wait necessarily put the volume sequence outside
the window. Preserve this as a recorder-window failure, not six failed volume
keys or a qualified idle input result.

The simultaneous helper-UID run completed with code 0 and 10120 samples,
mean dump cost 7 ms and maximum 100 ms. Five samples during that 150 ms physical
power hold changed the PMIC KeyDowns count from 0 to 1, then back to 0.
GPIO stayed 0. This confirms real physical mapper-state observation under the
helper UID. The observed dump supplies counts, not key codes: PMIC contains
both power and volume down. Do not silently classify every PMIC count-1 hold
as volume down. Direction attribution, screen/power changes, screenshot chords,
rapid presses and missed-sample handling require independent evidence before
adding production corrections. GPIO up has a separately verified source.
Matched VivoInputManagerServiceImpl.dump adds only switch-listener metadata;
it has no examined key-state argument command.

Both first captures were pulled under first-physical-native and
first-physical-helper in the existing private evidence directory. The native
phone directory was removed after complete; USB stay-awake automatically
returned to 2. The phone was unlocked, ordinary helper/main connectivity and
native 150 retained. No new APK replacement or controller behavior change
occurred on this response.

A fresh 600-second pair was then verified ready with inactive playback:
native `/data/local/tmp/pvs-dump-physical-1793257239488980000`;
helper
`/storage/emulated/0/Android/data/com.vivo.finevolumesteps.helper/files/input-dump-1793257239549`.
Its actual paths and latest volume/USB snapshot are saved in the private
physical-phone-path.txt, helper-physical-path.txt and latest-physical-state.json.
The user must perform the requested physical sequence within this new window.
Do not discard the expired first capture or report volume delivery from it.

### Completed helper-UID mapper observation of all six physical volume keys

The second user-assisted window captured the physical power pair followed by
three DOWN and three UP pairs. First volume DOWN was 11.503721 seconds after
power release (not the requested fifteen seconds); this still exceeds the
user's clarified greater-than-ten-second black-idle condition. All six holds
and releases matched separate nonzero/zero mapper intervals in helper UID 10322.
PMIC was (1,0) for each volume-down hold and GPIO (0,1) for each volume-up hold.
The independently identified power pair also produced PMIC (1,0).

For the six volume pairs, observed-down completion lag was 33.989,13.642,10.644,
20.689,16.471,15.700 ms, and release lag was 21.237,12.725,8.851,25.236,21.793,
14.724 ms. Each volume hold had 4–6 positive samples, with dump costs at most
12 ms during those holds. The full helper run completed with code 0, 5333
samples, mean dump cost 7 ms and maximum 27 ms. The independent native reader
completed without errors. This is physical mapper observation under a
one-time-granted helper UID; no root, Shizuku or adopted shell identity was used
by that reader.

Around the volume sequence, seventeen independent samples were noninteractive,
idle, Bluetooth A2DP and connected to helper/accessibility. Three initial setup
samples had the expected helper disconnection while target instrumentation
restarted its process; they occurred before the physical sequence and were
retained. The initial reviewer incorrectly asserted readiness for setup too;
the corrected reviewer checks every sequence sample explicitly and preserves
the setup count. All native readbacks remained 150; app-tag output was empty.
No production correction was implemented or qualified by this observation.

The dump supplies only node counts, not key codes. A GPIO count-1 hold has a
verified volume-up source; PMIC count-1 alone cannot distinguish power from
volume down. Resolve this before treating it as explicit direction input.
Power/display transitions and chords, ambiguous or dropped samples, baseline
ordering, holds/rapid keys and post-reboot permissions remain required work.
Do not remove wireless exclusions or fabricate raw KeyEvent timestamps merely
because this node-count observer can see the holds.

Evidence is second-physical-native, second-physical-helper and
second-physical-review.json under `/tmp/pvs-adb-once-1793256090301444000`.
Both captures were pulled and complete; the native phone directory was removed.
USB stay-awake 2, original timeout/shortcuts/accessibility set, awake/unlocked
state, connected ordinary helper/main and actual latest native 150 were
verified afterward. The debug helper and newly granted DUMP remain intentionally
installed for continuing this candidate; original production helper bytes are
saved privately. No observer is still running. Main APK remains unchanged.

### Exact EventHub scan-state candidate after the mapper-count result

Further examination of the same full input dump found `KeyState (pressed)` in
its earlier EventHub device records. The preceding recorder retained only the
InputReader mapper counters; do not reinterpret those old six pairs as recorded
scan codes. Physical nonzero EventHub states require a fresh capture.

The actual phone's `/system/lib64/libinputreader.so` was pulled read-only. Its
SHA-256 is `b941dcfa082d1531138b680bb36cb7508c40423a40b39ff42c1b3888e1bc5f5c`.
Matched native disassembly provides a direct state relationship:
`EventHub::Device::trackInputEvent`, at 0x82930–0x82970, sets the EV_KEY scan-code
bit in Device+0x180 for nonzero values and clears it at 0x82a44 for release.
`EventHub::dump`, at 0x94950–0x94ae4, enumerates that same bitset to print
`KeyState (pressed)`. Thus this candidate has an examined direction/power state
source independent of mapper counts and display-state guesses. Its physical
timing, simultaneous-key and missed-sample behavior remain unqualified.

The debug test instrumentation now additionally retains unparsed EventHub
state lines for the two exact physical device names, with enabled evdev nodes
and platform sysfs paths. It fails when either physical record or its scan-state
field is missing. It still makes no audio writes, adopts no shell identity,
and does not manufacture KeyEvent timestamps. The main and debug helper APKs
were unchanged. Only the helper test APK was upgraded through the inspected
native installer, with install Success; its new SHA-256 is
`972eebe8fb8d307a2e3b37f7731152eff646fc1200ef78c79fe30d1e75c0dc30`.

A fresh two-second run completed with code 0 in helper UID 10322 without another
permission grant. All 61 samples contained both expected EventHub and mapper
records; awake idle scan states were `<none>`. Mean dump cost was 10 ms, maximum
16 ms. This verifies access after a helper instrumentation process restart,
not permission persistence after phone reboot or physical key qualification.
Evidence is `own-uid-scan-first` and `own-uid-scan-first-review.json` in the
existing private one-time-ADB evidence directory.

Debug helper/test builds and lint passed. Release main/helper builds and lint
also completed with no errors; unit tasks were up-to-date and their cached
reports still contain 41 tests, zero failures/errors. The independent native
reader was rebuilt with `-Wall -Wextra -Werror`. Native Bluetooth volume 150,
50 steps, ordinary selection, actual helper/accessibility connectivity and
awake/unlocked state were retained. A fresh physical recording is needed to
match exact scan states against the independent built-in power/volume events.
No production backend or correction was added, and no commit/publication was
authorized.

The fresh exact-scan physical window is now ready for user input, with both
independent readers running for at most 600 seconds. Native output is
`/data/local/tmp/pvs-scan-physical-1793258473213722000`; helper output is
`/storage/emulated/0/Android/data/com.vivo.finevolumesteps.helper/files/input-dump-1793258526121`.
Phone tools are under `/data/local/tmp/pvs-scan-tools-1793258473213722000`.
Actual paths, snapshots, hashes and readiness are saved in private
`scan-physical-state.json` and `scan-physical-ready.json`. The helper terminal
session is 5246393386157905; a separate restore session 3083648897302208 waits
for native completion and restores USB stay-awake with `svc power stayon usb`.
Retain these windows until completion or explicit stop, then pull before cleanup.

Two shell Settings attempts to set USB stay-awake to 0 retained actual value 2;
the final setup used `svc power stayon false` and verified actual value 0.
This is a setup failure/recovery, not a physical key result. The latest ready
audit still has native 150, inactive Bluetooth media, 50 steps, ordinary mode,
ready helper/main accessibility and both raw physical readers. Requested input
is physical power off, wait fifteen seconds, three DOWN then three UP short
presses at roughly one-second intervals, and a final short power press.
This tests exact down/up/power scan attribution with no production correction.

The user then clarified that no keys were pressed and the window had expired.
Both bounded recorders had already completed normally. The native trace contains
only ready/finished records, status 0 and zero input events. All 19021 helper
samples retained both physical records with `<none>` EventHub scan states and
zero mapper counts. Preserve this as an expired no-input acquisition, not a
behavior pass or failure. It was pulled under `expired-exact-scan-native` and
`expired-exact-scan-helper` in the existing private evidence directory before
both phone result directories were removed.

The preceding restore waiter used `svc power stayon usb` before the recorder's
cleanup later wrote the captured 0 setting back, leaving actual USB stay-awake
0. This ordering defect was detected and repaired before the retry: USB
stay-awake was restored to 2, and the private recorder cleanup now maps captured
value 2 back through `svc power stayon usb`. No repository fixture or production
APK was changed.

A replacement 600-second exact-scan window is ready. Native output is
`/data/local/tmp/pvs-scan-physical-1793259630817326000`; helper output is
`/storage/emulated/0/Android/data/com.vivo.finevolumesteps.helper/files/input-dump-1793259636100`.
Both readers were independently verified active with native 150, inactive
playback, ordinary mode, ready helper/accessibility and an awake/unlocked
display. USB stay-awake was then explicitly disabled and read back as 0 for
genuine physical power-off testing. Its cleanup will restore the saved value 2.

### Helper 1.2 exact-key implementation and physical results

The replacement scan window captured POWER, three DOWN, three UP, POWER.
Helper UID 10322 named each hold exactly as `KEY_POWER`, `KEY_VOLUMEDOWN` or
`KEY_VOLUMEUP`, matching independent kernel pairs and resolving the shared PMIC
node ambiguity. First volume DOWN was 9.969 seconds after power release, so use
this run for exact attribution rather than 15-second idle qualification.

Helper 1.2 requests DUMP and exposes a signed asynchronous key listener. Its
parser accepts only the exact PMIC/GPIO names, enabled event nodes and platform
sysfs roots verified on this firmware; unknown, missing or virtual records fail
closed. State changes preserve their observed interval rather than inventing a
KeyEvent timestamp. Listener death, screen-on, helper shutdown and Shizuku
selection stop ordinary input. It holds no persistent wake lock. Initial volume
holds are ignored; an initial power hold is retained to cancel chords.

Ordinary registered helper input owns noninteractive physical keys and feeds
the existing explicit raw-gesture planner. Bright-screen keys remain with
accessibility, while selected Shizuku keeps its original priority. A two-second
health query detects permission changes without rebuilding an unchanged
listener. Revoking DUMP changed diagnostics to
`available=false, registered=false` in about 200 ms; regranting restored both
in about 1.4 seconds. A helper force-stop produced a new PID and automatically
restored write/input readiness.

Automated checks passed: main units 41/41, helper parser/edge units 3/3, release
builds/lint, AOSP ordinary 17/17, frames 13/13, layout 56/56, synthetic
screen-off 16/16, Vivo helper IPC 104/104 at both 50 and 64 steps, and restored
emulator Shizuku 102/102 at both 5 and 7 steps. The first Shizuku 0/0 attempts
without a manager/server remain retained setup failures.

Built-in idle at native 150 completed three DOWN then three UP at configured
147,144,141,144,147,150 with the panel hidden; its first key followed power
release by only 7.519 seconds. A second built-in idle run from 0 waited 28.603
seconds and completed three UP then three DOWN. Coarse UP writes
0→10,3→13,6→16 were corrected to 3,6,9; DOWN settled at 6,3,0. The screen
remained asleep, panel hidden and final readback exactly 0. This is the stable
black-idle physical pass.

Actual Bilibili UID 10811 owned started USAGE_MEDIA throughout a physical
screen-off run. First volume UP followed power release by 24.291 seconds.
Ten rapid UP presses combined to configured target 33; ten rapid DOWN presses
returned exactly to origin 3. All 20 built-in pairs and helper callbacks were
retained, playback stayed active and the panel stayed hidden. Actual intervals
were about 0.4–0.9 seconds, so classify this as rapid-burst/reversal, not twenty
individually settled one-second presses. Playback was paused and user volume 0
restored.

A real reboot changed boot ID from
`c287e03c-f714-4c00-81a1-ea5a252acafa` to
`982a6e60-0777-4291-8f76-8d9b2c715add`. Before first credential unlock, user 0
was RUNNING_LOCKED while 50 steps, native 0, ordinary keeper/accessibility,
helper write/input registration and DUMP were ready. After unlock, main and GKD
were bound. A later three-UP/three-DOWN sequence corrected
0→3→6→9→6→3→0 after reboot, but its native recorder expired before the keys.
Keep it as app-side physical-source evidence; fresh independent post-reboot
capture remains pending.

That pending independent capture was subsequently completed on the same new
boot ID. First volume UP followed power release by 20.878 seconds. The native
reader captured complete POWER, three UP, three DOWN, held UP, held DOWN and
POWER pairs. Helper input produced the short configured sequence
0→3→6→9→6→3→0. The physical UP hold lasted 1175 ms and was counted as eight
configured presses, reaching 24. The DOWN hold lasted 1004 ms and was counted
as six presses, reaching 6; unequal hold durations are not an expected exact
reversal. Independent kernel and helper states matched, the display remained
off, panel stayed hidden and no key wake lock remained. Test-created final 6
was restored to the user's pre-test 0. This completes independent post-reboot
built-in idle and held-key evidence.

The final route/chord cell used connected OPPO Enco X3 A2DP, inactive playback
and native origin 100. After 17.504 seconds stable off, three built-in DOWN
presses combined to configured target 91 and three UP presses restored exactly
to 100. The headset remained the active Bluetooth route. A following physical
POWER+DOWN chord had DOWN and POWER downs only 1.999 ms apart; POWER released
12.999 ms before DOWN. The independent kernel recorder retained all edges,
while the helper made no extra correction and final volume stayed 100. Screen
wake stopped screen-off sampling as designed. This passes wireless idle,
reversal and physical power-chord cancellation.

The main settings page later added a one-time Shizuku grant action. It binds an
ephemeral ADB-shell user service without setting `shizuku_enabled`; that service
accepts no package or command arguments and runs only the fixed helper DUMP
grant. On the physical Vivo, DUMP was first revoked and ordinary diagnostics
settled at `available=false, registered=false`. Tapping the inspected
**使用 Shizuku 授权助手** button granted DUMP, restored
`available=true, registered=true`, retained `Shizuku=enabled=false`, preserved
native Bluetooth volume 79, and left no compatibility-grant process alive.
The manual ADB command remains available as a fallback.

Private evidence is under `/tmp/pvs-adb-once-1793256090301444000` and
`/tmp/pvs-dump-input-fix`. No commit, distribution or publication is authorized.

## Permission guidance follow-up, 2026-10-29

The missing-DUMP button now opens a method chooser instead of immediately
requesting Shizuku. It prints the complete fixed USB ADB command with its own
full-width copy button and separately offers the Shizuku one-time grant through
a second full-width button. If Shizuku is not running or authorized, the
follow-up dialog opens the installed Shizuku app or its download page. The
completed label remains **助手熄屏授权已完成**.

After main-app data is cleared, the first launch opens one permission guide
while correction still defaults off. It contains direct entries for
accessibility, notification, battery optimization, overlay, auto-start and
vendor background settings. Vivo/iQOO additionally show helper screen-off
status. The guide explicitly treats helper DUMP as screen-off-only; the pure
state test proves missing DUMP cannot make on-screen readiness fail.

Final automated results for APK
`5edff8cf7d90abaa5975d3858e1efdce71726a2f3b91769996c76329b99ffc04`:
main units 46/46, helper units 3/3, permission/grant UI 18/18, ordinary 17/17,
frames 13/13, layout 56/56, synthetic screen-off 16/16, Shizuku 102/102 at
both 5 and 7 steps, full lifecycle 101/101, display 7/7, API 36 locked boot
6/6, rejected writes 13/13 and playback 37/37. Main/helper release builds and
lint passed.

Retained failures include the permission test's first attempt to read private
release-app storage, fixtures started after the clean-data test left correction
off or overlay denied, Shizuku after its clean-data authorization was removed,
and stale test expectations for origin-anchored ring rounding, accepted
no-write outcomes, current MediaSession output and a fixed 0-150 range. Each
cause was corrected in the fixture and rerun successfully; no production
volume behavior was weakened.

The Vivo later reconnected. The follow-up APK installed successfully and the
installed package hash matched
`5edff8cf7d90abaa5975d3858e1efdce71726a2f3b91769996c76329b99ffc04`.
PackageManager readback confirmed that helper DUMP was then revoked. Ordinary
diagnostics settled at `available=false, registered=false`, while the helper
write connection and accessibility service remained ready. The final app UI
showed **设置助手熄屏授权** and the inspected chooser contained both the full
USB ADB command and the Shizuku action; it was cancelled without granting.

Final restoration retained ordinary mode, the user's 50 steps and media-only
scope, main and GKD accessibility bindings, and native media volume 133/150.
There was no pending correction, key wake lock or compatibility-grant process.
The phone was left with the app open near the helper authorization entry and
DUMP missing for the user's manual authorization test. A synthetic ADB key used
during the audit changed native volume but did not reach accessibility, so it
is not physical-button evidence; the opposite synthetic key restored 133.

## Bluetooth playback output-gain follow-up, 2026-10-29

The user reported a new distinction after the helper input fix: with OPPO Enco
X3 A2DP and actual Bilibili background playback, the final panel moved by the
configured 2%, but a screen-off physical press sounded much larger than the same
press while awake. The first diagnosis treated the later native-index write as
an audible correction. That was insufficient: an AudioService index is not an
endpoint-output measurement.

The effective connection reported `mAvrcpAbsVolSupported=false`. Vivo's
`AvrcpVolumeManager` device memory followed the local media index, but this is
not an independent earbud analog-gain reading. Its event logger contained no
outgoing AVRCP volume event. A test-only low-level 440 Hz signal therefore
established the phone's actual A2DP output gain after the stream curve:

- local 105/150 produced a stable A2DP signal power of -46.5 dB;
- direct local 102/150 produced a stable -47.4 dB;
- both the AudioService index and Bluetooth device memory matched each preset.

The release-only correction build
`675845c305f00cfdf53068434d388e56ac3f8e4f843f0e272d75eedc6a92d58f`
confirmed the user's concern. A physical screen-off DOWN first changed
105→95. The helper wrote 102 after release and the long-term A2DP power did
eventually settle at the direct -47.4 dB baseline, but the 50 ms history still
contained -48.2 and -49.5 dB. The active A2DP deep-buffer track reported about
585 ms latency, so audio already mixed at the coarse 95 index remained audible
after the local index had been corrected. Five spaced presses could repeatedly
fill much of the listening interval with that lower buffered output.

The final fix masks only an already-observed, same-direction native transition
while all of these conditions hold: companion physical input, noninteractive
Vivo/iQOO, active media, normal audio mode and wireless media route. On physical
key down it restores the pre-key local value without advancing configured
history. Power chords still cancel the gesture. On a valid key release it
applies the configured target, retains the original 300 ms stabilization
deadline and permits only one matching late-write repair.

Final APK
`f7fe2295c96d23bd11f4049d05eeaab9bf5ac89799f787aad52fa860c8bd2b4a`
was installed on the same Vivo. In the controlled physical run, firmware changed
105→95 at `22:58:30.558`; helper-UID masking restored 105 at `.577`, 19 ms
later. Release then applied 102 at `.725`. The A2DP history moved directly from
-46.5 through -47.2 to the direct -47.4 dB target and contained no sample below
the final target. Device memory ended at 102, Absolute Volume remained false and
the Bluetooth volume-event list remained empty.

Actual Bilibili UID 10811 then owned a started A2DP AudioTrack throughout a
stable screen-off built-in-button run. The user pressed more than the requested
five keys, including both directions. Every retained press showed the native
10-index transition masked back to the preceding configured value before the
release target; the user reported, **“这次我没听出来有问题”**. Final readback and
Bluetooth device memory both ended at 96 before restoration.

Final targeted verification passed: main units 50/50, release build and lint,
emulator ordinary 12/12 on the 0–15 AVD, frames 13/13, layout 56/56 and
synthetic screen-off 16/16. Physical helper-backend instrumentation passed
104/104 at both 50 and 64 steps. The first final APK installation attempts while
the secure keyguard was showing were retained `INSTALL_FAILED_ABORTED` setup
failures; installation succeeded after the user unlocked.

Restoration paused the user-started Bilibili playback, restored native A2DP
volume 133/150 and 50-step media-only ordinary mode, revoked helper DUMP, cleared
the temporary debug log property and rebound only the main accessibility service
through its native Settings switch. Final diagnostics showed no pending
correction or key wake lock, `available=false, registered=false`, and both GKD
and Precise Volume Steps actually bound. Earbud-internal analog gain remains
unqueryable while Absolute Volume is false; the measured result is the final PCM
power delivered to the A2DP output path plus the absence of a remote-volume
command.

## Screen-off flashlight shortcut investigation, 2026-10-30

The user reported the torch turning on after repeated screen-off volume presses
while Precise Volume Steps was enabled. The current Vivo V2419A / API 36
firmware was
`vivo/PD2419/PD2419:16/BP2A.250605.031.A3_V000L1/compiler260529100608:user/release-keys`.
Main 1.5.1 and helper 1.4 were installed; assistant mode was selected with
helper DUMP available, Shizuku disabled, 50 steps and media-only scope.

Before any settings changes, camera-service history contained three torch-on
events attributed to SystemUI. SmartKey startup at the corresponding times and
the inspected settings page identified the enabled screen-off long-volume-down
shortcut with the flashlight function selected. Call/message/calendar flash
reminders were disabled. Firmware inspection established the path:
`VivoWMPHook.mVolumeLongPressRunnable` starts the SmartKey service; SmartKey
requests VivoDaemonService's flashlight operation; SystemUI applies the torch.
The helper only observes key state and does not consume this OEM gesture.

A bounded emulated external USB HID comparison reproduced torch-on with an
1100 ms volume-down hold. Six 60 ms presses with 300 ms between them did not
reproduce torch-on. After disabling only `smartkey_primary_switch` through the
inspected native settings switch, both sequences left torch state at zero and
added no camera-service torch-on events. The independent double-press camera
setting remained unchanged.

Retain the original incomplete comparison and setup failures. An initial
“app disabled” label was invalid: tapping the middle of Vivo's native Switch
text did not change its checked state or stop the keeper. Such a label cannot
establish an enabled/disabled comparison. Recheck the actual switch thumb,
checked state and keeper shutdown before accepting a rerun. Likewise, wait for
the settings readback after changing the shortcut switch; the first immediate
readback raised a setup error before the change settled.

The corrected rerun confirmed both the checkbox and keeper shutdown: with
`keeper_running=false` and the shortcut enabled, the same 1100 ms emulated
external hold still turned on the torch through SystemUI. With the keeper
running and the shortcut disabled, it stayed off. Final state retained the
disabled shortcut, restored native volume 141/150, 50-step media-only assistant
mode and both connected accessibility services. Temporary phone HID/preset
tools were removed; main, helper and the pre-existing test host APKs were unchanged.

This is a phone-settings conflict diagnosis, not a new APK release or proof of
the user's built-in short-press sequence. Preserve the intentionally disabled
flashlight shortcut as the requested fix, restore native volume and app state,
and verify both GKD and Precise Volume Steps are actually bound. P24 remains a
separate physical-button verification requirement; playback-dependent shortcut
eligibility and the original short-press timing still require physical evidence.
