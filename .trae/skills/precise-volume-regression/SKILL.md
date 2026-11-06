---
name: precise-volume-regression
description: Verify volume behavior and releases against past user feedback. Use before changing or releasing this app, testing a real phone, or fixing rounding, flicker, startup and screen-off issues.
---

# Verify changes

Read [build and automated suites](references/commands.md) and
[the physical-phone feedback matrix](references/phone-matrix.md).
Read [故障记录与证据更正](references/incident-log.md) before changing key handling.
将每个新故障和失败修复追加到项目记录并从本 skill 链接；保留这条用户要求。
记录对应提交、函数、实测版本、原失败、重跑和未验证项；未经复现的解释只列为假设。
For translations or locale changes, also read
[Android language resources](references/localization.md).
For raw input, playback fixtures or interrupted phone tests, also read
[screen-off regression cases and follow-ups](references/screen-off-cases.md).
For reboot failures, preserve the untouched missing-server state before
restarting Shizuku; use the added recovery and built-in-button recorder cases.
For ordinary-mode feedback, keep Shizuku disabled. Helper 1.2's separately
reported DUMP input is ordinary mode, not Shizuku raw input; record its exact
availability/registration and preserve the one-time grant.
Record idle and actual-player playback separately; passing synthetic helper
writes or a Shizuku rerun cannot qualify ordinary screen-off buttons.
For vendor/startup/identity changes also read the
[device compatibility skill](../precise-volume-compatibility/SKILL.md).

## Required procedure

1. Select an explicit device. Snapshot current volume/range, steps/scope,
   backend, accessibility list/connectivity, permissions, screen/timeout,
   playback/route and installed APK hashes. Preserve the user's normal state.
2. Build main and helper from the same source and signing key. Run release unit
   tests and lint. Compile the test host only for development. Use the standalone
   commands in the reference; no enclosing workspace is needed.
3. Run the first-launch permission guide, frame/readback/layout/screen-off suites
   on a disposable emulator. Use real Android volume/power/IPC, not a mock alone.
   Do not run destructive emulator setup, AppOp rejection or screen-off fixtures
   on a personal phone.
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

## Resume an interrupted test

Check for surviving processes and completed result files before issuing more
input. Snapshot the current phone again; volume, accessibility and permissions
may have changed while the agent was stopped. Do not overwrite newer user
settings with the first session's snapshot. Restore only known test leftovers.
Reuse a completed run only when its APK hash, fixture version, backend, steps
and required assertions match. Keep an incomplete run and its errors separate.

During input sequences, check playback and both main/player PIDs after every
unlock or UI inspection, then around rapid bursts and holds. A visible player
task or an unchanged main PID alone does not establish that playback continued.

## Do not regress

- Normalize percentage from actual maximum independently of configured steps.
- One graphical bar; three stable text rows; `%` denominator; no clipping.
- Direct system readback; hold the previous real sample during pending work;
  display actual result on failure; never fabricate the target.
- Origin-anchored cumulative rounding and exact reversal, including pending
  opposite keys and off-grid starts; no inserted history stops.
- Accessibility keys require a matching native transition. A fresh matched raw
  volume release can authorize an off-screen media change without that transition.
  Privileged holds may update media after Android's repeat timeout; helper holds
  still require their own complete release. Verify volume during the hold and
  after UP separately. Count repeats using the device's Android timeout/delay,
  retain the original rounding path, and never recount already applied repeats.
  The Vivo ordinary-playback fallback may accept only its narrowly guarded
  coarse transition while the display is off; record the known ambiguity with
  an external preset of the same shape. Other passive changes never authorize
  writes. Cancel power chords and stale/unmatched raw input.
- Keep the helper's sampling baseline across screen-on/off while its listener
  remains registered. Test immediate first press with prewarm disabled and idle
  playback; already-held keys at registration must remain passive. Do not weaken
  the verified EventHub device parser to make AOSP input look like Vivo hardware.
  R41 的真实连按仍在持续基线已就绪时失败：读取按键状态的 dump 跨过了
  一个完整短按，另一个松开在 main 排队后过期。须记录每次 dump 起止、
  Binder receipt/main delivery、每个物理 DOWN/UP 和相机快捷开关/实际选项。
  Vivo 原生页面明确说明快捷操作在音乐播放时不可用，息屏双按音量减可启动
  相机。保持用户的快捷设置不变，分别记录无播放和用户实际播放；不要为使
  音量测试通过而关闭相机。合成采样通过不能证明播放场景不漏键；不放宽
  freshness、不估算补齐缺少的按键，也不把系统快捷行为归因于本应用。
  [R43–R50](references/incident-log.md)将实际播放结果与超时空窗口分开：
  助手五连按及Root息屏长按已有同步PMIC证据，Shizuku息屏长按只有
  生产raw路径证据。Shizuku亮屏内置长按的非触底值由R49验证为76→4、
  UP后约76秒仍4；Root亮屏R50为76→16、UP后约113秒仍16。
  均限定当前50阶、真实蓝牙媒体播放；完整组合仍独立待验证。
  R48在UP后仍观察到系统6→0；以实际settled值判断触底，不从文件名、
  计划按住时间或UP时读数推断非触底通过。
  不要据此跳过其他亮屏长按、其他阶数/route或重启验证。
  [R51](references/incident-log.md)补上助手64阶实际播放息屏五连按：
  完整PMIC五组边沿、76→64累计舍入和UP后约28秒稳定；旁路也采到
  原生粗调瞬时值，不能宣称音量过程完全无粗调。当前诊断APK的
  67项release单测、lint和163项基础emulator断言已重跑通过；
  其余长按/路由/重启测试按新hash分别保留。
  R52的Shizuku64阶长按在独立reader到时后才发生，生产raw和最终6/150
  单独保留，不能把空窗口记作物理PMIC通过。发出操作指引前重新确认
  ready，用户操作较晚时核对event时间是否落在实际采集窗口内。
  R53补录有完整64阶物理长按，但0.710秒后触底0，稳定约55秒；
  端点通过不能代替非触底回弹验证，不以提高实际播放音量规避触底。
  R54补上Root64阶实际蓝牙播放、亮屏内置长按：0.628秒，76→16原生
  进度，累计舍入结算15，UP后约74秒保持15。Root64阶息屏与重启、
  Shizuku64阶非触底长按继续单独验。
  R55追加Root64阶真正息屏内置长按：实际1.657秒，76→13，UP后约49秒
  稳定，播放持续、面板隐藏。R56当前hash的Root full仍35/36：
  与R39相同的迟到readback使quiet后补写超过一秒expiry，被Root拒绝。
  保留原失败和计数断言、核对重跑，不延长保护期限；其余套件通过
  不能代替full通过。
  R57补上Shizuku64阶亮屏非触底内置长按：实际0.614秒，76→15，
  完整PMIC窗口和UP后49.648秒稳定；息屏64阶、其他route、重启仍单列。
  R56同hash full重跑仍35/36，不以反复重跑到通过来关闭迟到写入问题。
  R58补上Shizuku64阶真正息屏内置长按：实际1.451秒，76→22，
  UP后61.970秒稳定、用户播放持续、面板隐藏；其他路由和重启仍待验。
  R59补上助手64阶实际扬声器息屏五连按30→18，完整五组PMIC与
  UP后84.613秒稳定；原生过程曾触底，ADBwake也不能充当物理立即开屏。
  R60修正full断言只查日志而漏掉UNCONFIRMED的问题；以实际状态和
  readback判定，保留旧误判结果及对应host hash。
  R60匹配新host的Root full7阶已36/36，原R56超时失败仍保留；
  不从成功重跑推导任意负载下都能在原一秒期限内完成。
  R61已完成助手64阶实际换boot ID重启，先保存未恢复的锁定状态：
  keeper/helper在首次解锁前自行ready，原settings保持；媒体及物理键
  仍分别验证。最早framework未就绪的stderr也须保留，不只查exit code。
  R62补上真实重启后助手64阶speaker内置五连按30→18，物理开屏样本
  仍18、UP后68.760秒稳定；POWER在确认后才发生，不能算pending wake。
  当前新host下Root/Shizuku full7阶各36/36，原超时失败分别保留。
  R63补上Shizuku64阶speaker息屏非触底物理长按：0.841秒、30→7、
  UP后35.637秒稳定，原播放持续。ADB中断后先核对手机reader和原文件，
  本轮正常complete及service events保留，空logcat单列；
  不因连接中断直接重复用户按键或把已有UID0服务当作自动重启证明。
  R64补上Root64阶speaker息屏非触底物理长按：0.904秒、30→2、
  UP后38.247秒稳定，原播放持续。恢复设置须读取NumberPicker实际值，
  并确认原蓝牙设备真正连接后才恢复该route音量；
  原生Connection failed须如实保留，不能写成已恢复连接。
  R65在保留原连接失败后已核对实际蓝牙重连及76/150、speaker0、
  Helper50、原无障碍与播放恢复；相机1/2在secure namespace核对。
  50个确认本轮新增的设备端路径先私有归档再清理，已有reader/script保留。
  [本轮汇总](../../../dist/verification-20261106-volume-keys.md)单列实际
  物理通过、原Root超时失败及仍未验证的矩阵。
- Bounded single writes, expected-current/expiry checks, stale-callback rejection.
  Inspect native writes after the settling retry and its acknowledgement as
  well; R36 records a real external-input counterexample to assuming one
  acknowledgement proves stability. Keep the 300 ms quiet period anchored to
  actual readback changes, the original total timeout and single settling retry.
  Recheck enabled/scope/raw readiness/mode before a pending retry (R37).
  An early expected-current rejection must not discard a still-valid quiet
  observation (R38). Check live hold minimum/maximum and settled readback
  separately; a matching final target can hide a rebound during the hold.
  Test native hold steps larger than, equal to and smaller than configured
  steps (R39). A slower planned target must never reverse faster native progress
  during the hold, on UP or through a stale-expected/settling retry.
  R40 requires checking queued write ordering too: acceptance before a native
  repeat does not ensure application before it. Test native-driven holds
  separately from consumed down/up-only holds.
- Partial CPU wake lock only; hidden panel while off; key-up recovery requires
  recent matching evidence; no display wake.
- Preserve existing working Shizuku behavior and user settings during switching.

A real-device pass is necessary before claiming a changed vendor behavior works.
When a phone is absent, finish builds/documentation/emulator tests and explicitly
leave the physical matrix unverified. This is a reporting rule, not a reason to
stop independently executable work or request redundant permission.
