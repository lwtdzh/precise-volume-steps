# Screen-off regressions and follow-up tests

Use these cases alongside P01–P22. R01–R14 describe checks or recovery findings
from the 1.4.14 work; each row states the evidence type. N01–N08 are additional
tests to run, with no pass recorded. Do not turn unit, synthetic callback or
external-HID results into built-in physical-button results.

Select an explicit device and save its current state. For input sequences,
record raw event age, pre-key sample, native transitions, selected backend,
requested/confirmed value, actual power state and playback. Use the normal
installed service for delivery tests; keep destructive fixtures on an emulator.
Neither fixture code nor reports may contain a private unlock credential.

## Cases learned from the completed work

| ID | Reproduce and expected result | Existing evidence |
|---|---|---|
| R01 | At native 0–150 and 50 steps, start 121. Down five times asleep gives 118,115,112,109,106; wake/unlock and down once gives 103. Reverse back to exactly 121. Run idle and active AudioTrack separately. | Final external HID on Vivo, both states; 81%→69% |
| R02 | At 150/64, start 74. Asleep downs give 72,69,67,65,62; awake sixth gives 60. Retrace every value to 74; repeat mixed directions without drift. | Final external HID plus universal planner units; built-in buttons not pressed |
| R03 | Native policy changes 115→105 before the raw snapshot while the poll still holds 115. One configured down press at 50 steps must target 112 from the last real pre-key sample. | `RawVolumeBaselineTest.policyWriteBeforeCallbackAndPollUsesLastRealSample`; earlier phone failure retained |
| R04 | Poll/broadcast already observed a same-direction policy write after the key. Use its previous real sample. In a separate run, observe a passive preset to 104 before the key and use 104 as the new origin. | `policyWriteAlreadyObservedAfterKeyUsesPreviousSample` and `observedPassivePresetBeforeKeyBecomesNewOrigin` |
| R05 | Make an opposite-direction external change or fail a native read. Do not manufacture a pre-key value from an unrelated sample. | `oppositeChangeAndFailedReadDoNotInventBaseline` units |
| R06 | Send unmatched release, wrong-node release, duplicate release or raw callback older than 250 ms. None may authorize an extra write. | `RawVolumeKeyTest` freshness/matching units |
| R07 | Hold volume up and down together, or press power on a separate dedicated node while volume is held. Cancel the raw gesture and preserve system chord behavior. | Raw press/chord units; physical screenshot behavior still needs P14 |
| R08 | Hold about 700 ms without repeat events, then release: four configured steps. With explicit repeats, count each once. An opposite hold restores the origin; a hold beyond 10 s cannot authorize a write. | Short holds/reversal in final HID; repeat and overlong-hold bounds in units |
| R09 | Send `---+--+` without per-key settling; end three steps below origin. Send `+++` to restore exactly. Native dispatch can arrive after release: retain the 300 ms settling wait and inspect late writes, not only callback freshness. | Final idle/playback HID bursts; earlier code-25 late native 112→122 failure retained |
| R10 | An older write confirms while a new gesture is pending on the uneven grid. Preserve the gesture's copied history. From origin 74, confirmed 79 and two further ups target 83; two downs return 74. | `VolumeStepHistoryTest.pendingGestureKeepsItsRoundedPathWhenAnOlderWriteConfirms` |
| R11 | While truly asleep, apply an external preset to origin−1 without a key. Leave it unchanged, keep the panel hidden, and verify the CPU key wake lock expires after key work. | Final idle/playback HID passive, power and wake-lock assertions |
| R12 | Keep actual playback through sleep, UI inspection and secure unlock. Check both main/player PIDs, playback after unlock, before bursts and after holds. The observer must target `uitools`, not the player. | Final HID; earlier host-targeted UI runs stopped playback and were rejected |
| R13 | Reuse the host task and switch idle→playback→stop. Deliver extras through `onNewIntent` with `FLAG_ACTIVITY_SINGLE_TOP`; a brought-forward task is insufficient. Verify actual audio state. | Final HID launch/cleanup and final playback audit; earlier launch-state assertion failed |
| R14 | Disable conflicting Vivo long/double-press shortcuts through the native UI, verify readback, then restore selected camera option and other services. Any setup/unlock/restoration error fails the run, including a 17/17 partial assertion list. | Final HID cleanup and separate restoration audit; failed fixtures retained |

The callbacks' 250 ms freshness test and the 300 ms wait after release serve
different purposes. A callback received in 1–4 ms was still followed by a late
firmware write. Inspect raw receipt, native write and app correction timestamps
separately before choosing a fix.

Use the installed test host and observer hashes when judging fixture identity.
The final two HID runs each have 83 assertions, including playback continuity.
Earlier passing counts with fewer assertions are separate historical evidence.

## Additional tests to run

These rows have no recorded pass. Extend a suitable fixture or capture built-in
buttons as specified; do not change old pass counts or report them as completed.

| ID | Additional scenario | Required observations |
|---|---|---|
| N01 | Built-in buttons after short sleep and 10/30 minutes idle, both without media and with playback; repeat 50/64 steps with volume shortcuts disabled. | Every settled value, raw event age, actual off state, main connection/freeze state and exact reversal. External HID cannot certify this row. |
| N02 | Cross screen/lock state during a press: down asleep, wake or unlock before up; repeat at release+100/300/500 ms with both raw and accessibility callbacks visible. | Capture which input path owns the gesture. Require no duplicate write or accumulated drift; record how cancellation affects the actual value before defining any new correction policy. |
| N03 | Test raw freshness at 249/250/251 ms, drop a release, delay events while the main process is suspended/frozen, then deliver a fresh press. | Reject expired/unmatched volume events; verify bounded wake-lock/gesture cleanup and successful fresh recovery. Use synthetic timing fixtures on an emulator and record physical behavior separately. |
| N04 | Disconnect/reconnect input nodes or Shizuku; leave only a power node, deny a reader, then restore a readable volume node. | Accurate raw readiness, closed old readers, no stale listener callback/write, reconnection without duplicate readers. Preserve ordinary/helper write readiness separately. |
| N05 | Switch speaker↔Bluetooth while asleep and while a correction is pending; test endpoint/AVRCP buttons independently of phone buttons. Repeat Absolute Volume on/off, including the reported 58%/61%/63% plateau, after verifying the effective connection state. | Actual route/range/readback, outgoing volume commands, reported headset volume, endpoint gain and rebasing. Use constant audio and measured output for equal-loudness claims. Bluetooth enabled, a UI toggle, a persisted Settings value or fresh raw events alone do not prove endpoint behavior. |
| N06 | Pause/resume media, transfer or lose audio focus, enter/leave a call, and change route without a volume key. | Actual AudioTrack and audio mode. Passive changes must not cause writes or open UI; call handling stays with the system. Preserve the user's playback rather than adding production dummy audio. |
| N07 | During a raw hold or pending correction, disable correction, switch Shizuku→helper, revoke/regrant Shizuku or change 50→64 steps; then release and press again. | Cancel obsolete gesture/write generations, retain saved settings and actual failed-result readback, rebase the next accepted press. Test both near-limit clipping and an off-grid origin. |
| N08 | Kill the runner during setup, key work and cleanup; separately change user volume/settings before resuming. Reuse a same-version but different-hash APK or changed player fixture. | Detect incomplete runs/processes, preserve the newer user snapshot, compare exact APK/fixture identity before skipping cells, restore all preferences and report cleanup errors as failures. |

For rapid bursts in new fixtures, sample after the first correct value and
again after at least a second of quiet to detect a late native overwrite.
Record the measured latency; do not loosen freshness limits or add retries
merely to make a timing test pass.

N05 has one completed ordinary-helper cell from 2026-10-29: OPPO Enco X3,
Absolute Volume false, built-in phone buttons, stable screen-off playback and a
constant A2DP test signal. Compare direct 105/102 baselines against the physical
press using AudioFlinger's A2DP signal-power history, AudioService events and
`AvrcpVolumeManager` device/event state. The release-only implementation exposed
coarse -48.2/-49.5 dB samples despite a final -47.4 dB readback. Restoring the
pre-key value on helper key down removed that dip; release then reached the same
-47.4 dB direct baseline. Actual Bilibili remained started during the final
mixed-direction physical run and the user reported no audible problem.

This does not complete N05's route-switch, endpoint-button, Absolute Volume on,
or acoustic microphone cells. Device memory is a local index when Absolute
Volume is false, not an independent earbud gain sensor. Keep A2DP PCM power,
remote volume commands and acoustic output separate.

## Reading the results

Treat an incorrect origin or a late native overwrite as a controller defect
when its trace establishes that ordering. Treat stopped playback, undelivered
play extras, an obscuring USB dialog or a stale XML path as fixture failures.
Keep both kinds of original failures. Restore the phone before retrying and
retain strict input, playback, power and cleanup assertions.

Normalize serialized Settings values before comparing them, and parse the
multiline bound-services section separately from enabled components. A Python
tuple/list mismatch or a label/package mismatch is a validation defect, not
evidence that the phone changed a setting or disconnected a service.

## Reboot feedback from 2026-10-28

Read [the untouched Vivo findings](../../precise-volume-compatibility/references/reboot-screen-off.md).
Treat Bilibili playback and ten idle presses as separate scenarios. The saved
50-step count, helper availability and raw-key availability are separate facts.
Do not start Shizuku before capturing the failed post-reboot state.

| ID | Test | Evidence to retain |
|---|---|---|
| R15 | Main 1.4.15, selected Shizuku, connected helper. Stop only the shell server, inspect page/notification, then start the installed server through its official ADB starter. | Both raw/write readiness go false; helper does not hide “Shizuku 未运行”; existing notification reports missing listener. Both connections recover with unchanged main PID/steps and warning clears. This is server lifecycle, not real reboot. |
| R16 | While truly asleep, down ten times with per-key readback, then up ten times. Run idle and active controlled AudioTrack, 50/64 steps, arbitrary native origin/range. | Each value follows origin-anchored Java rounding, exact reversal, screen off and panel hidden. External HID remains separate from built-in buttons. |
| R17 | Real reboot with Shizuku selected; capture new boot ID, keeper/helper/accessibility and raw state before first unlock and before opening either app. | Saved steps survive; report which boot action actually ran. A ready helper plus absent shell server must display missing raw input. Restarting Shizuku afterward is recovery, not automatic reboot survival. |
| R18 | Post-reboot built-in buttons with actual Bilibili background playback, then idle; repeat after authorized Shizuku recovery. Use the physical recorder below. | Dedicated GPIO/PMIC down/up times, actual off state, playback UID/session, per-press native readback before/after settling, reader readiness and app receipt/write trace. General `isMusicActive` or an existing Bilibili PID alone is insufficient. |
| R19 | Already-playing actual Bilibili, truly asleep, with authorized raw input ready. Up ten times then down ten times at a safe low volume. | `tests/playing_app_hid.py` checks the exact installed player UID owns a started media AudioTrack, native readback immediately and after one second, screen/panel state and player/main PID continuity at every press. This is external HID, not R18's built-in buttons. |

The installed-phone HID fixture accepts `--shizuku-starter /path/to/official-starter`
to add R15. It kills only the current shell-owned Shizuku server and restores
it even if an assertion fails. This option changes the running server state
and requires the already-authorized ADB backend. The fixture never reboots a
phone. Ten-press/reversal assertions run in both idle/playback phases.
Read notification records only for the main package; do not retain unrelated
notification contents. A UI observer must continue to target `uitools`.

For R18, start the desired playback/screen state and press the **built-in**
buttons while this read-only recorder runs:

```sh
python3 tests/phone_reboot_observer.py --serial SERIAL --scenario bilibili \
  --seconds 30 --output /tmp/pvs-physical-reboot
python3 tests/phone_reboot_observer.py --serial SERIAL --scenario idle \
  --seconds 30 --output /tmp/pvs-physical-reboot
```

It selects only the observed Vivo GPIO/PMIC volume nodes, reads keys and real
AudioManager/keeper observations, and changes no settings, power, player or
Shizuku state. It fails an empty-key recording or mismatched screen/playback
state. `recording_complete` means the trace is complete, not that correction
passed. Review each release against both its immediate and settled readback.
Private audio/session evidence for Bilibili stays outside Git. Capture the
same scenario before and after recovery; do not replace a failed trace.

For R19, first start a suitable quiet video in Bilibili and turn the display
off. Use a low enough actual origin that ten up presses do not clip the maximum:

```sh
python3 tests/playing_app_hid.py --serial SERIAL --player-package tv.danmaku.bili \
  --keys /tmp/pvs-test-hid --preset-dex /tmp/pvs-preset-dex/classes.dex \
  --presses 10 --output /tmp/pvs-bilibili
```

This fixture changes/restores media volume, introduces/removes its own HID and
preset files, and leaves player/settings/power state alone. It uses the actual
native min/max and configured count, rejects a clipping baseline, and waits
one second after the first matching value to detect a late native overwrite.
It identifies the exact package's UID rather than accepting any active player.
Caller cleanup must stop playback if it was introduced for the test.

Real-reboot fixture follow-up: wait for USB/ADB re-enumeration, changed boot ID,
the relevant boot broadcast and the application's own notification. Initial
samples can precede broadcast delivery/notification posting. The development
observer cannot run before credential unlock; use a shell UI dump only as a
controlled first-unlock fallback and verify accessibility afterward. Retain
the original empty notification capture and failed observer attempt. Never
copy private unlock credentials into a fixture, skill or report.

Recorded 1.4.15 results on 2026-10-28: R15/R16 passed in the 135-check 50-step
run and 127-check 64-step run, with idle and controlled playback. R17 passed
16 startup/recovery observations; keeper/helper started before first unlock,
while Shizuku required an explicit restart. R19 passed 65 checks with actual
Bilibili playback, origin 3/150 and 50 steps: up ten to 33, down ten to 3,
each audited after one second. R18's built-in buttons remain unverified.
The emulator suites passed 97 checks after restoring their disabled setup.
See [the complete record](../../../../dist/verification-1.4.15.md) for hashes,
original failures and restoration.

The 1.5.0 Shizuku Bluetooth follow-up used APK
`a3a32b611b3050f2bd9612c830d438311671124ae05042512eaa1eef13f113d5`.
On Vivo V2419A with OPPO Enco X3 and Absolute Volume off, one external-HID
five-down run matched each direct A2DP PCM target at 50 ms resolution. A strict
rerun retained a first-key 50 ms sample 1.1 dB below its target and failed that
assertion. These remain separate external-input records.

The following built-in recorder held exact Bilibili UID playback, stable
screen-off state, A2DP, Shizuku write readiness and two physical GPIO/PMIC
readers for 120 seconds at 111/150, but no physical volume release occurred.
Volume and both processes stayed unchanged. Record this as an empty input
window, not an R18 pass or failure; the physical sequence remains pending.

The next phone-side window completed on the same APK. A built-in power pair
preceded five full `mtk-pmic-keys` volume-down pairs by 17.3 seconds. Exact
Bilibili playback and OPPO Enco X3 A2DP remained active, the display stayed off,
and settled volume followed 111→108→105→102→99→96. AudioService showed each
firmware ten-unit coarse write restored in 4–28 ms before the three-unit fine
target. The user reported no perceptible extra drop. Evidence is
`/tmp/pvs-shizuku-physical-live-current`.

Final APK `031fbf6c454afa78bc68dab6aeb7ff729c820eee71f8c639bd012a9baf5792c2`
then fixed only the assistant input-health reconnection race. Its Shizuku
50/64-step backend runs passed 102 checks each; companion runs passed 105 each,
including live Shizuku→assistant input recovery. A final-hash physical window
received no key and remains an empty acquisition, not a replacement for the
completed physical evidence.

## Ordinary-mode correction from the same user

The user explicitly rejected Shizuku-only recovery and requires the ordinary
helper to work while asleep, including idle and actual Bilibili after reboot.
Read [the ordinary findings](../../precise-volume-compatibility/references/ordinary-screen-off.md).
Keep this requirement outstanding whenever ordinary input is absent.

| ID | Test | Evidence to retain |
|---|---|---|
| R20 | Shizuku disabled, helper/accessibility connected, truly asleep. Ten built-in down presses and ten up presses, idle and actual Bilibili separately; repeat after reboot. | Original GPIO/PMIC events, native per-press readback after one second, original KeyEvent time/app receipt, helper request/result. No Shizuku start during the ordinary cell. |
| R21 | Native adjustment and its observation occur before the original accessibility callback arrives. Repeat up/down through direct AudioManager and ordinary helper, even/uneven counts. | Use original event time and real readback. Correct from the preceding real sample; do not add a configured step to the already-coarse value or expire the gesture without correction. |
| R22 | Preset preceding the original key, preset not yet observed at callback, own previous write confirmed while a consumed key callback is delayed. | No extra correction from those values. Do not import raw input's unobserved-transition recovery. Retain timestamp/readback metadata and negative assertions. |
| R23 | Own-UID read-only input capability probe in the real independently signed helper. | Actual UID, defined/protection/grant state of listener permissions, explicit built-in node open/close result. Shell-readable nodes do not establish app-readable nodes. |
| R24 | Ordinary helper after reboot: compare awake/off input, then rebind only the main accessibility service while awake and repeat. Compare short settling with at least five seconds and verified completed display-off state; isolate enabled/disabled Vivo shortcuts. | Native filter enabled state, main/helper PID and readiness, complete accessibility list, original input/app callback times, window/display/power state, every settled readback. A single shortly-after-sleep correction does not qualify stable screen-off delivery. Keep external HID and built-in runs separate. |
| R25 | Autonomous kernel simulation with Shizuku off: full down/up pairs while awake, then idle and controlled playback after five seconds of sleep. Start with down as well as up. | BUS_HOST/device identity, independent kernel edges, every settled readback and app callback. `adb input` acceptance alone does not prove accessibility routing. Select default display 0 when examining window dumps. |
| R26 | A short simulated press briefly enters accessibility, then native volume continues changing after its kernel release. | Preserve down/up event times, native progression and app-key/write records. A 60 ms kernel pair followed by native repeats is a failure; do not treat the first corrected value as a pass or assume the app generated the repeats. |
| R27 | Create a no-input uinput device with a GPIO/PMIC hardware name, then invoke the physical recorder. | Reject the virtual sysfs source before recording; emit no keys and preserve volume. An identical input-device name must never turn a simulation into physical evidence. |
| R28 | Actual Bilibili owns active media, ordinary helper ready with Shizuku off, five-second stable sleep. Complete ten up/down kernel-simulated presses with retained volume failures. | Every initial/one-second readback, exact-player ownership, screen/panel state and process continuity. Wrong values fail the result even if the reversal ends at origin. Restore caller-introduced playback and its original position. |
| R29 | Final ordinary playback compatibility: stable off, active media, ready helper, normal audio mode and non-wireless route. Apply one firmware-sized coarse transition in each direction. | Correct from the real pre-transition origin, preserve rounded reversal, keep the panel hidden and bound retries. Repeat with actual Bilibili and controlled playback. Record the same-shaped passive-preset ambiguity. |
| R30 | Final ordinary idle fallback: deliver a full accessibility key and key-up-only while stable off, no playback and unchanged native volume. | After 300 ms, adjust exactly one configured step without waking the display. Reject if playback, wireless route, non-normal audio mode, helper loss or an intervening native change appears. Built-in physical delivery remains a separate requirement. |

For R20 use the physical recorder with `--backend ordinary`; it fails if raw
input becomes enabled or the helper/accessibility disconnects. It still needs
actual built-in presses and review of each settled readback. An empty physical
recording is unverified. For external HID use `--backend ordinary --scenario both
--ten-presses-only`; preserve that evidence separately from R20.

`Device.restore_accessibility(reconnect=True)` deliberately toggles only the
main native service even when it is connected, for R24. Its normal default
still returns immediately for a connected service. Keep the observer installed,
start the keeper and unlock before invoking this diagnostic operation.
The first recorded R24 external probe did not recover after rebind; the planned
five-second/disabled-shortcut repeat did not start because the phone disconnected.
See the ordinary findings for the original readings and pending fixture cleanup.

After reconnection, that repeat completed with the current user origin 100.
Awake up/down reached 103/100; stable idle sleep stayed 100/100; controlled
playback sleep reached 110/100. Results were unchanged after rebind, with no
fresh accessibility key while asleep. Playback remained active at both
readbacks. R24 is still a failed external-input recovery attempt; R20 physical
idle/Bilibili remains unverified. Preserve the intervening shortcut-page setup
failure caused by the observed USB connection dialog. Native 100, shortcuts,
ordinary preferences and other accessibility services were restored.

The reconnected R23 probe additionally measured `MANAGE_KEY_GESTURES` ungranted
with signature protection in actual helper UID 10322. Other grants and both
built-in-node EACCES results were unchanged. Its test APK was removed afterward.
An awake temporary-HID sanity check verified that the physical recorder's
`getevent -lt` invocation and parser received four live key edges before reader
termination. This validates acquisition without certifying built-in buttons.

The built-in recorder now verifies that the exact installed Bilibili UID owns
started media before, throughout and after recording, and checks player/main
process continuity. A different app playing while Bilibili is paused must fail
the Bilibili cell. Its audio-ownership parser was checked against the retained
real Bilibili capture and rejected other UID, paused and non-media variants;
the updated recorder itself still needs a physical-phone run.
The PID helper now treats `pidof` status 1 as an absent process so disappearance
can be reported. An initial read-only emulator check incorrectly assumed the
main app was running and failed that assertion; the repeat used the already
running SystemUI and passed both present/absent-process checks.

After the user requested autonomous simulation, the kernel source was added to
the existing installed-phone fixture. Its first ordinary run retained all
twenty idle readbacks (native 60 stayed 60, no callbacks). Controlled playback
used coarse ten-unit changes, with no callbacks. It stopped at reverse press
seven when actual playback became inactive: 90/127 emitted assertions passed,
36 volume assertions and one playback assertion failed. This is an incomplete
playback sequence, not a twenty-press playback result. Keep the original run.
The fixture restored the latest saved native 0, ordinary 50-step preferences,
shortcuts and accessibility, with no pending correction/playback or cleanup error.

R28 subsequently completed all twenty presses with actual Bilibili media:
from native 3, the up sequence was 13,23,…,103 rather than 6,9,…,33;
downs were 93,83,…,3 rather than 30,27,…,3. All twenty exact-player/off-display/
hidden-panel checks and final PID checks passed, but no app callback appeared.
The report failed: 27/65 assertions passed, 38 incorrect-volume assertions,
with no acquisition/restoration errors. The last matching origin is a
coincidence of symmetric coarse changes. Preserve the preceding session-play
setup failure and the separate incomplete controlled-AudioTrack run.

R27 rejected a temporary virtual GPIO-name source before input, with exit 1
and no volume change. The C fixture's unchanged default USB route was checked
awake at 37→40→37 with four independently recorded kernel edges. The first
reader-start attempt missed the initial down edge and failed; its retained
repeat allowed startup and passed five sanity assertions.

Final cleanup passed sixteen assertions: latest saved native 0, ordinary
50-step settings, complete accessibility list and bound GKD/main services,
original shortcut values/camera option, timeout/stay-awake, unchanged installed
main/helper hashes, original ended Bilibili position and paused playback,
awake/unlocked state, no pending work or simulated devices, and removal of
introduced host/observer/probes. R20's built-in ordinary behavior and the
required repair remain unverified/unmet; R28 does not qualify those claims.

The final APK later restored a constrained form of the 1.4.13 playback path and
added the idle callback fallback. R29 passed before and after a real reboot:
actual Bilibili corrected 3→13→6, and a controlled existing-node sequence
corrected 60→50→57 then 57→67→60 while stable off. R30's synthetic real-Android
suite passed 111/111; ordinary helper runs passed 104/104 at both 50 and 64
steps. These results use APK SHA-256 `ed6724dd30c307e0b13b0442442bcbc5322c16598817175ddf046326a80a4b86`.
R20 remains pending because no automation can reproduce the built-in key's
physical IRQ/wake behavior.
