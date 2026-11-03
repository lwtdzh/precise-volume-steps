# Vivo screen-off behavior after reboot

## User report and untouched state, 2026-10-28

The user reported working built-in screen-off buttons before restart. After
restart, Bilibili background playback used much larger steps; idle screen-off
presses appeared to do nothing even after ten presses and waking the display.
The user explicitly said the detailed description might be imprecise. Preserve
both scenarios and that qualification; the pre-restart physical pass is
user-reported, not an instrumented result.

The user subsequently clarified that **Shizuku was not enabled before reboot;
ordinary mode with the helper worked**. Preserve that explicit report. The
post-reboot Shizuku-selected snapshot below does not establish the pre-reboot
mode, and the successful later Shizuku recovery does not explain why ordinary
mode changed after reboot. Investigate system/input-filter/startup state
differences rather than attributing the reported earlier success to Shizuku.

Untouched connected Vivo V2419A / PD2419, Android 16 fingerprint:
`vivo/PD2419/PD2419:16/BP2A.250605.031.A3_V000L1/compiler260529100608:user/release-keys`.
Installed main 1.4.14/code 26, native media 3/150, minimum 0, 50 configured
steps, media-only, Shizuku selected. Keeper/accessibility and helper were
connected. Shizuku write connection and raw key reader were both unready;
the Shizuku manager process existed but its shell server did not.
At this snapshot the display was unlocked/on and playback inactive. Do not
present this snapshot as a measured reproduction of Bilibili playback.

The audio log retained native 28→18→8→0 without app correction writes, and the
controller retained other coarse changes without corresponding received keys.
Screen/playback state at those earlier transitions was not captured. This
supports a delivery investigation but does not prove their input source.

The 50-step preference survived. At range 150/50, configured spacing is three
native units, displayed as roughly two percentage points. Android's native
maximum remains 150. Larger native jumps do not establish loss of the setting.

## Established recovery and UI defect

Starting the installed Shizuku shell server through its official ADB starter
restored both write and raw connections, with two built-in dedicated nodes,
without changing keeper PID or the 50-step setting. No accessibility reset or
main-app restart was needed for this initial recovery.

The main page previously displayed only “独立兼容助手已连接” when the helper was
ready. This hid a selected but stopped Shizuku connection. Version 1.4.15
shows both states and uses the existing low-priority foreground notification
to report the unavailable screen-off reader. After the controller is created,
the service refreshes that notification so a boot-time placeholder is replaced
by the actual connection state. Reconnection removes the warning.

The helper explanation now describes background audio writing separately from
screen-off input. Startup broadcasts/time are exported in the keeper dump.
The recorded action is the most recent boot/unlock/package-replacement action,
not proof of reboot; use a changed kernel boot ID for real reboot evidence.

## Real reboot experiment with 1.4.15

A further ADB-requested reboot produced a different kernel boot ID. Before
first credential unlock, `dumpsys user` showed `RUNNING_LOCKED`; the keeper,
accessibility and helper were connected with native 3/150 and saved 50 steps.
Shizuku and raw input were unready. The initial service capture still contained
the previous package-replacement action; a later locked-state observation
recorded `LOCKED_BOOT_COMPLETED`. Boot broadcasts and notification posting can
arrive after the first service sample.

The first notification capture was empty during boot. A later capture, still
before first unlock, showed “熄屏按键监听未就绪” and “Shizuku 未运行”.
After unlocking, the receiver recorded `BOOT_COMPLETED`. Starting Shizuku
restored both connections with the same keeper PID. Sixteen startup/recovery
checks passed, including native volume, saved steps, timeout, USB stay-awake
and the complete enabled-accessibility list. This establishes automatic
keeper/helper availability on this firmware, not automatic Shizuku startup
or built-in volume-button correction.

The original first-unlock fixture failed: the development UI observer's
instrumentation cannot start while credential storage is locked. A shell
`uiautomator dump` fallback allowed the existing private unlock procedure to
run. The credential remained outside the repository. Preserve this failed
attempt and check actual accessibility connectivity after the fallback;
ordinary UiAutomation may suppress services. A temporary missing ADB device
during USB re-enumeration is not proof that reboot failed.

## What a recovery does and does not establish

ADB-started non-root Shizuku stops at phone reboot. The ordinary main/helper
UIDs cannot read these raw devices or launch the shell server themselves.
The current implementation reconnects when an authorized server starts; it
does not implement unattended wireless-ADB pairing/startup. Do not promise
automatic screen-off recovery solely from self-start or helper 关联启动.
Do not add an unguarded native-jump heuristic, raise the display, add dummy
production playback or acquire root. The later ordinary-mode fix intentionally
restores only the documented active-playback coarse-transition case and keeps
its passive-preset ambiguity explicit.

A ready helper can correct an authorized write. Playback may make Android
adjust media directly without accessibility delivery; idle policy may drop
the key before audio changes. These explain the two reported symptoms when
raw delivery is unavailable, but actual built-in button traces in both states
are required to verify their precise behavior.

See the regression skill's screen-off cases for the recovery, ten-press and
physical-recorder procedures. Keep actual Bilibili, controlled AudioTrack,
external UHID and built-in physical-button results distinct. Installing a host,
instrumenting the main package or opening a video is not proof of key delivery.

The later actual-Bilibili experiment used its started media AudioTrack while
truly asleep, after the real reboot and authorized Shizuku recovery. The saved
`tests/playing_app_hid.py` repeat passed 65 checks at native 3/150 and 50 steps:
ten up presses reached 33 and ten down presses returned exactly to 3. Each
readback was checked again after one second, with continuous playback, hidden
panel and unchanged player/main PIDs. Idle/controlled-playback HID runs passed
135 checks at 50 steps and 127 at 64 steps. Built-in presses remain unverified.
See [the verification record](../../../../dist/verification-1.4.15.md).

## Delayed startup and silent authorization

The 1.5.1 reconnect fix covers a running keeper whose selected mode is Shizuku,
but whose server starts later or whose permission is granted silently by the
manager. Permission-result listeners only cover requests made by this process;
the manager can update its authorization without invoking those listeners.

The existing controller poll checks authorization and missing user-service
connections every two seconds while Shizuku mode is enabled. Once both Binders
are connected, it skips inspection and relies on Binder death/disconnect
callbacks plus local `isBinderAlive` checks. This also applies when the raw
service reports no readable input device: input availability is separate from
connection health. The two-second recovery check introduces no separate timer
or wake lock. If the API has no
live server Binder, `ShizukuBinderDiscovery` requests it through the official
manager's explicit `REQUEST_BINDER` receiver protocol, also used by rish.
It accepts only callbacks from the installed manager UID, waits for user unlock,
and rejects deliveries after mode exit or controller shutdown. The manager's
provider requires `INTERACT_ACROSS_USERS_FULL`; querying it from the ordinary
application UID is not a usable discovery path. The main Shizuku provider is
direct-boot aware so privileged server delivery can reach a running locked-boot
process.

Both volume and raw-input bridges abandon an unanswered bind after ten seconds,
then retry on the next inspection. Every bind has its own connection object;
late callbacks from an older attempt cannot replace the current connection.
Discovery and reconnection do not request permission, open an Activity, start
Shizuku, or use Root.

`tests/shizuku_reconnect.py` uses the real official server and manager on a
disposable emulator. Its silent-grant case changes real manager authorization
five seconds after the keeper begins waiting and requires zero request-result
callbacks. Delayed startup, missed Binder delivery and an injected unanswered
bind are separate cases. All require live volume and input Binders, unchanged
volume/mode, no main Activity and rejection of stale deliveries after mode exit.
These cases establish automatic connection recovery; they do not establish
built-in physical-button delivery or automatic startup of Shizuku itself.

The recorded reconnect test APK was installed on Vivo V2419A / Android 16:
the pre-update keeper remained unready with the real Root server already
running and authorization granted. Updating the APK recovered both services
without opening the main Activity or changing the selected mode. Stopping that
real server, waiting five seconds and starting it again recovered in 998 ms
with the same keeper PID, unchanged preferences/native volume and accessibility.
On AOSP API 33, a changed-boot-ID reboot followed by delayed official Root
startup passed 13 checks after verifying the pre-boot grant was persisted.
Its raw service connected but reported no readable device, since the earlier
temporary input node did not survive reboot. See
[the reconnect test record](../../../../dist/precise-volume-steps-1.5.1-shizuku-reconnect-test-report.md)
for the original failed setup runs and unresolved ordinary-mode stress results.
Vivo actual reboot and built-in physical-button delivery remain unverified.
