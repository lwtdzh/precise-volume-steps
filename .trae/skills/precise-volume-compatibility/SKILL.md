---
name: precise-volume-compatibility
description: Preserve device findings and diagnose Android volume backends. Use for Vivo, iQOO, other vendors, background writes, screen-off keys, startup or helper changes in this project.
---

# Device compatibility

For iteration verification, use the
[完整测试计划](../precise-volume-regression/references/test-plan.md) and
[历史反馈对应表](../precise-volume-regression/references/feedback-coverage.md).
They retain separate ordinary, Shizuku, Root, physical-input and actual-output
results. Record each run using the linked template; historical device passes
are references, not current-version qualification.

新增设备差异、权限限制、按键/音频策略或其他注意事项时，随时在主测试计划
追加对应条件和用例，并链接实测证据。保留已有编号、失败和未验证组合，
同步反馈映射与执行模板；新设备结果不能直接继承其他固件的通过记录。

[Root 状态色反馈 UF-084](../precise-volume-regression/references/incident-log.md#uf-084root-就绪状态颜色)
单独记录界面颜色与后端就绪条件；不从文字颜色推导厂商按键支持。

Read [implementation and historical findings](references/context.md) before
changing a backend, startup path, key handling, or volume calculation.
Read [故障记录与撤回的修复](../precise-volume-regression/references/incident-log.md)
for repeated missing-key and long-hold reports. 新问题必须记入项目文档并链接到 skill；
禁止将未执行的测试和假设写成通过或已定位原因。
R43–R50记录保留相机快捷的实际播放对照：助手五连按和Root息屏长按
有同步PMIC记录，Shizuku息屏长按的旁路已到时；Shizuku亮屏长按
已有PMIC和非触底76→4的稳定结果，Root亮屏非触底76→16见R50。
按证据类别报告，其他组合仍待测，不要从这些结果推导全部亮屏行为
或重启已验证。
R51补上助手64阶实际播放息屏五连按的完整物理边沿和76→64稳定结果，
并重新核对诊断APK的基础自动化；其他64阶长按与路由仍单独待验证。
R54追加Root64阶亮屏实际蓝牙播放的非触底物理长按，76→15且UP后约
74秒稳定；Shizuku64阶非触底、息屏其他组合和重启仍不可继承为通过。
R55追加Root64阶真正息屏非触底长按76→13、UP后约49秒稳定。
R56当前诊断hash的Root full原生迟到写断言35/36，原失败与已完成的
其他套件单列；不能把临时模拟器Root broker作为Vivo授权证据。
R57追加Shizuku64阶亮屏非触底内置长按76→15、UP后约50秒稳定，
物理边沿落在有效独立窗口；其息屏64阶、其他路由和重启仍分别待验。
R58补上其真正息屏64阶长按76→22、UP后约62秒稳定，真实播放持续、
面板隐藏；保持其他路由和重启的独立状态，不据蓝牙结果推导全部支持。
R59补上助手64阶真实扬声器息屏五连按30→18、UP后约85秒稳定；
保留30→20→10→0的原生过程，恢复原扬声器bank和蓝牙连接。
R60记录测试日志断言漏掉实际UNCONFIRMED的误判，修工具后单独重跑。
R61追加当前助手64阶实际重启，首次解锁前自行恢复keeper/helper/input
基线，保留最早binding与framework尚未就绪的状态；媒体恢复及物理按键
仍单列，不能把连接ready写成内置按键或其他backend重启已通过。
R62追加重启后原keeper/helper实际speaker五连按30→18，
物理开屏仍18、最后UP后约69秒稳定；区分确认后开屏与pending wake，
保持扬声器特权长按和其他backend的实际重启状态独立。
R63补上Shizuku64阶speaker息屏非触底长按30→7，UP后约36秒稳定。
ADB连接中断而手机reader存活时先检查原窗口，保留原exit255；
本轮完整PMIC、readback及service events可用，独立logcat为空单列。
准备时已有UID0 Shizuku server不能证明所选backend自动重启恢复。
R64补上Root64阶speaker息屏非触底物理长按30→2、UP后约38秒稳定；
恢复原Bluetooth时原生连接失败须单列，保留配对并核对实际route，
不能把打开Bluetooth开关写成已恢复原耳机连接。
R65已在保留原连接失败后确认原蓝牙路由和76/150恢复，
speaker0、Helper50、原无障碍及真实播放保持；
camera的1/2读取secure namespace，错误namespace的null须更正。
R48还在UP后观察到系统音量继续6→0；松手与写入稳定必须分开记录。
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
4. Screen-off processing can use explicitly enabled Shizuku to read dedicated
   volume/power devices. A fresh matched raw release authorizes a media write
   even when firmware made no native change. Accessibility-only recovery still
   requires a key-up and recent matching native change. A partial CPU wake lock
   keeps only the bounded correction alive.
   Selected Shizuku/Root holds also progress before UP after Android's repeat
   timeout, including down/up-only input devices. Keep helper release gating and
   independent privileged ownership when accessibility is unavailable.
   R39 shows that native holds can progress faster than configured steps; do
   not write an older, slower target back against that progress. Timer-count
   agreement alone does not establish a hold is free of rebound.
   R40 also demonstrates that an absolute write accepted before a native repeat
   may take effect after it. When native repeats are already progressing, avoid
   concurrent absolute hold writes and settle only after real readback quiet.
5. Core rounding and panel displays use Android's native range. Universal volume
   math does not imply universal OEM background permissions.
6. Helper 1.2 can read exact built-in EventHub key state after a one-time DUMP
   grant on the verified Vivo firmware. Keep its input registration separate
   from helper audio-write readiness. Older helpers and missing grants still
   cannot replace a selected Shizuku raw reader.
7. When the user requires ordinary mode with the helper, keep Shizuku disabled
   throughout that verification. Shizuku recovery and status improvements do
   not satisfy ordinary screen-off playback or idle requirements.
8. A registered helper keeps its exact EventHub baseline while awake, idle and
   on all playback routes. Starting a new baseline only on SCREEN_OFF can lose
   an already-started first press. Continuous sampling trades additional polling
   work for preserving that edge; listener removal/death must still stop it.

Keep the raw callback's 250 ms freshness limit separate from the 300 ms wait
after release before correction. A fresh callback can still precede a delayed
native write. The post-release 300 ms quiet period must follow actual readback
changes, with the original bounded lifetime and single settling retry; an
acknowledgement alone cannot retire this observation. Check both orderings in the
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
