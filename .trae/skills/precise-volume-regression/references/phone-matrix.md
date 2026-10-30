# User feedback converted to physical-phone tests

Run this matrix on the actual target phone after restoring accessibility from
instrumentation. Select sensible low-volume starting points with no loud media.
Use ordinary installation and real physical volume buttons; do not invoke
controller callbacks for these rows. Record requested and actual results.

For every result record model, manufacturer, fingerprint/API, main/helper
versions and SHA-256, route, native min/max/current, steps/scope, backend,
screen/keyguard state, active/paused media, input source, settled readback,
panel observations and timings. Collect independent Android readback before
and after input and compare the third row to it.

## Required matrix

| ID | Reproduce | Expected / evidence |
|---|---|---|
| P01 | Main open, home screen, another app; idle and playing media; ordinary mode with/without required helper | Same configured correction when backend available; no extra activity/window. Show genuine unconfirmed outcome when system rejects. |
| P02 | Native maxima available on tested routes/devices, including 15/50/100/120/150/255; configured 5/7/30/50/64/100/150 | Top `round(current*100/max) / 100%`; second configured-scale value; third exact native current/max; never hardcode 150 or 50. Log combinations unavailable on this phone as untested; retain universal unit coverage. |
| P03 | Native 0–150, 50 steps, actual 21; physical `+++---` | 21→24→27→30→27→24→21; percentages 14→16→18→20→18→16→14. No 22/15% historical stop. |
| P04 | Same range/count, actual off-grid 22; `+++---` | 22→25→28→31→28→25→22. Full spacing with arbitrary origin. |
| P05 | Uneven case (150/64 if available), start 74; `+++---+++` | 74→76→79→81→79→76→74→76→79→81; repeated increments identical at the same position; use Java rounding. |
| P06 | `+-` and `-+`, both after confirmation and immediately while writes pending; repeat 20 times | Return exactly to original native value; no stale late write or cumulative drift. Test uneven and off-grid starts. |
| P07 | Long hold, quick three-key burst, immediate opposite burst | Ordered combined gesture, bounded writes, confirmed final value stable after dismissal; no duplicate correction. |
| P08 | Near min/max, reach a clipped limit, press repeatedly there then reverse | Native limits respected, endpoint repetition does not advance hidden cursor; reversal retraces last reachable point. |
| P09 | External player/system slider preset after an app correction, then physical key; change configured count/scope | Passive value preserved, path rebased from actual value/new range/settings; no old history stop. |
| P10 | First key with panel hidden; subsequent key with panel visible; digits 9→10 and 99→100, configured 150 | One bar following three rows. Stable panel/child bounds, no text clipping/vertical movement. |
| P11 | Record high-frame-rate video during quick alternating presses, including third row | Last settled real readback held until outcome, then one actual update. No intermediate coarse jump, flicker, tremble, repeated same-text draw, pending-status frame or reopen. Native OEM panel is separately observed. |
| P12 | System rejects an app write (controlled fixture on emulator); compare requested 68 / actual 71 behavior on applicable phone | Final third row must show actual 71, never requested 68. Read failure explicitly says 读取失败. No unbounded retry. |
| P13 | Idle, play/pause, mute/unmute, stream/route switch, player gesture and external preset without volume-key callback | No correction and no new/extended app panel. Passive broadcasts and 250 ms poll only observe. |
| P14 | App consumes key, canceled press, screenshot chord power+volume, unrelated key | No correction absent matching native volume transition; preserve screenshot/other-app behavior; unexpired key cannot authorize unrelated later change. |
| P15 | Locked display on, then truly off; idle and playback. At 50 steps, start at 81%, down five times asleep, wake and down once →69%; reverse. Repeat uneven 64 steps, pending bursts and holds with Shizuku raw input enabled; normal mode tested separately | Same correction; display stays off, no panel while off, partial wake lock expires. Released raw keys authorize writes even when firmware made no native change. Passive routed presets must remain unchanged. External UHID and built-in physical buttons require separate records. |
| P16 | Switch normal→Shizuku→normal while panel visible and keys/writes pending; enable/disable correction, change count live | No UI jump/flicker, duplicate writes, stale backend callback or lost saved settings. Mode/readiness agrees with diagnostics; Shizuku retains priority only when explicitly enabled/ready. |
| P17 | Stop/restart Shizuku, revoke its grant, reconnect; remove/update/kill helper | Connection state accurate; fallback reports actual result; no unexpected permission prompt; reopen + restore accessibility as needed. Helper reconnects, unauthorized provider access denied. |
| P18 | Remove main UI task, kill process, force-stop then reopen, upgrade both signed APKs | Keeper persistence/recovery appropriate to action; force-stop requires reopening; settings survive upgrade, other accessibility services remain enabled and connected. |
| P19 | Real phone reboot; observe boot completed/new boot ID, before/after first unlock; no manual Shizuku restart for helper | Check keeper/helper startup and saved steps separately from key delivery. With Shizuku selected but its server stopped, show missing screen-off input even when helper is ready. Then test actual Bilibili and idle built-in buttons, before/after authorized Shizuku recovery. See R15–R18; a server restart is not a real reboot or automatic recovery. |
| P20 | Speaker, wired endpoint if available, Bluetooth connected/disconnected with active and paused media | Read actual new range/stream and endpoint result; avoid invented maxima or inherited origin across routes. Bluetooth remains historically unverified. |
| P21 | Update on Vivo with risk acknowledgement; screen kept awake during installation; restore service afterward | ADB observes current checkbox/confirm bounds, install Success and matching APK hash. No request for manual confirmation of already authorized install. Preserve timeout/accessibility; avoid blindly tapping. |
| P22 | Required permission denied/restricted, self-start UI absent, helper without 关联启动 | Clear setup guidance and actual unready state; no fake connected indication. Diagnose startup record rather than equating standard permission with OEM UI permission. |
| P23 | Lit locked keyguard, ASSISTANT/SHIZUKU/ROOT, idle and actual-player playback, 50/64 steps; short physical `--++`, hold/reverse, unlock with a visible panel | Built-in keys reach the selected backend once, exact native targets remain settled. App panel uses the connected accessibility service above keyguard, retains three real rows and one bar; OEM panel observed separately. Assistant reader survives screen-on while locked and stops after idle unlock without prewarm. External HID requires its own record; helper readiness must not suppress external accessibility keys. |

P03/P04/P05 are concrete regression anchors, not exclusive supported parameters.
Exercise other actual ranges with analogous values and retain exhaustive math
unit tests. Do not raise live phone volume to a dangerous endpoint while media
is audible; use silent media/appropriate route for limit tests.

## Physical-input evidence

Keep a timestamped log before/after each press with controller stages:
key receipt, native transition, selected backend write, result/readback,
confirmation and displayed actual value. Phone screenshots establish layout but
cannot prove absence of transient flicker; use video or frame sampling.

External USB/UHID consumer keys exercise Android input routing and are useful
unlocked checks. The earlier six-step HID run succeeded with about one second
between presses. They do not prove the built-in buttons' screen-off policy.
`adb shell input keyevent` similarly does not establish physical-button coverage.

Test one changed variable at a time. For an intermittent failure, retain failed
trace, current process/connectivity/screen state and a full rerun. In 1.4.11 the
first 50-step Vivo run missed correction at press 17; the successful repeat did
not identify its cause. Do not dismiss repeats as harmless test lifecycle noise.

## Restore and report

Restore native volume, steps/scope, enabled state, Shizuku selection, permissions
changed by controlled tests, USB stay-awake and screen timeout. Compare the entire
accessibility list and actual connected services. Remove temporary installed test
packages or restore ones present before the session. Inspect helper and main
connectivity, no pending write/wake lock, and stable native readback afterward.

Report P01–P23 as pass/fail/not-run with backend and input source. Automated
fixture pass counts supplement these rows; they cannot replace physical tests.
Keep unverified screen-off/reboot/Bluetooth cases prominent in a vendor profile.
