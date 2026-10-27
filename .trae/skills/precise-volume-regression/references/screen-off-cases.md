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
| N05 | Switch speaker↔Bluetooth while asleep and while a correction is pending; test endpoint/AVRCP buttons independently of phone buttons. | Actual route/range/readback, endpoint volume and rebasing. Bluetooth enabled or fresh raw events alone do not prove endpoint behavior. |
| N06 | Pause/resume media, transfer or lose audio focus, enter/leave a call, and change route without a volume key. | Actual AudioTrack and audio mode. Passive changes must not cause writes or open UI; call handling stays with the system. Preserve the user's playback rather than adding production dummy audio. |
| N07 | During a raw hold or pending correction, disable correction, switch Shizuku→helper, revoke/regrant Shizuku or change 50→64 steps; then release and press again. | Cancel obsolete gesture/write generations, retain saved settings and actual failed-result readback, rebase the next accepted press. Test both near-limit clipping and an off-grid origin. |
| N08 | Kill the runner during setup, key work and cleanup; separately change user volume/settings before resuming. Reuse a same-version but different-hash APK or changed player fixture. | Detect incomplete runs/processes, preserve the newer user snapshot, compare exact APK/fixture identity before skipping cells, restore all preferences and report cleanup errors as failures. |

For rapid bursts in new fixtures, sample after the first correct value and
again after at least a second of quiet to detect a late native overwrite.
Record the measured latency; do not loosen freshness limits or add retries
merely to make a timing test pass.

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
