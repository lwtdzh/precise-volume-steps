# 音量故障与修复证据

## 持续记录要求

用户在本次排查中明确要求：每次出现新问题，都要记入项目文档并链接到
skill；这条要求本身也必须保留。后续修改前，阅读相关旧故障并检查修复
是否会再次破坏它们。

每条记录必须包含用户现象、实际测试版本/hash、复现步骤、具体提交与函数、
已证实原因或仍待验证的假设、修复、失败及重跑证据、尚未测试的模式和设备。
不能用 Gradle task 数作为单测数，不能将构建或安装成功写成行为验证通过。
保留原失败运行；对被新证据推翻的判断明确更正，不继续当成后续开发约束。
私人凭据、原始设备和播放器记录保留在 Git 外。

## 2026-11-06：助手立即关屏连按漏计、Shizuku 长按回弹

用户报告：助手模式关屏后立即连续按音量减，开屏后减少量与次数不匹配；
Shizuku 模式长按先迅速变小，随后又升回去，助手模式长按没有此问题。
排查开始时Root同类问题和两个用户问题均待验证。本条按时间顺序保留
原判断及其后续更正；当前实际播放物理测试与恢复结果见R46、R49–R65。

### 已核实的提交关系

- `0add8337095032be23910af4b37e199533bc2a93` 引入 `RawVolumePress` 及
  `beginKey` 的 `gesture.released = !raw`。后续完整 UP 会重新安排检测，
  该赋值本身不会删除 `directions`。不能将它归因于最近的 `db2caf0`。
- `60fa10bbc929a5c9c7ede5b771bc0418e4ca6d28` 保留已完成 release 跨开屏、
  解锁和 power 的修正，见 R31/R32。此次仍需重跑这些保护。
- `db2caf032777369ee369da7b41634610b58fda04` 把特权 raw 读键扩展到未锁的
  亮屏，`onKey` 跳过特权 reader 已拥有的 Accessibility 回调，
  `onExplicitKey` 按所选模式接受 raw 事件。它使原有 release 后结算和
  长按次数估算进入亮屏路径，是 Shizuku 行为变化的具体提交；
  当前仍需用按住期间和松开后的真实读数证明完整原因。
- 助手 raw ownership、`beginKey` 方向累计在上述最近提交中没有相同变化。
  `b56330cb0cca86a1b620f757f980de2c6112907a` 引入 helper 的按需读键及
  `InputDumpKeyState.initial` 忽略初始音量 hold；仅在无线播放时预热。
  若关屏第一键在首次采样前已按下，可能只交付 UP。这是启动漏键候选，
  不是已证实的真机原因，也不能声称由最近提交重新引入。

### 撤回前一版没有证据的修复及结论

前一版未提交补丁（APK `75579a9b1ba72ad7ff19e19679d2fad9770aa4c3e7464b9334f825dc69fa8722`）
加了 `Gesture.hadRelease`，以“以前松过键”代替“当前完整松键”作为检测门控。
在 API 33 模拟器上，真实 helper Binder/AudioManager、合成助手按键复现：
起点 14/15，第一键 DOWN/UP，随后第二键 DOWN 持续 550 ms 未松开，
应用在约 330 ms 时写入 8/15。第二键 UP/repeat 尚未交付却已被结算。
新 `ReleasedKeyWakeInstrumentation` 在 `earlier release does not authorize
a newer held press` 处失败（2/3，最终状态失败）。该结果已保留在 Git 外
`pvs-20261106-release-audit-before`。恢复 `released` 门控后的 APK
`d3ec851fb0c73a7c34eceae0db15a7a5139499ed8d9fb0cad5d3709865d3186d`
用同 fixture 在 5 阶、10 阶分别通过 27/27，原有 R31/R32 与新 R35 同时通过；
记录在 Git 外 `pvs-20261106-release-audit-after-5` 和 `after-10`。
此版本主应用单测 63/63、helper 单测 3/3、release build/lint 通过。
这些结果只证明已撤回补丁的门控及开屏保留保护，不证明 R33/R34 用户问题修好。

前一版同时强制亮屏特权模式回到 Accessibility。这破坏了主题切换关闭
无障碍后仍能运行的要求，且原有特权亮屏 fixture 实际失败。已恢复特权
reader 的全屏 ownership，后续长按修复必须保留无障碍缺失时的运行能力。

旧 R33/R34 中的“10 次长按通过”“助手五次通过”“Root 已修好”没有匹配
当前补丁的运行证据，已撤回。之前通过的时长估算单测、较早 APK 的短时
UHID 运行不能证明本次用户报告已经修好。

### 后续必须完成

- 助手：关屏立即按、等待后按、快速 burst、最后 release 后立即开屏；
  检查每个实际 DOWN/UP、helper 首次采样及 delivery、原生和最终读数。
- 特权：Shizuku 与 Root、无障碍开/关、持续 0.7/1.4/3 秒长按、反向长按；
  同时核对按住期间的目标更新和松开后是否回弹，保留一秒以上最终读数。
- 模拟器注入、外部 HID、内置物理键分别报告；helper 在 AOSP 上缺少
  Vivo 的严格 EventHub 字段不能通过放宽 parser 来“修复”测试。

### 本次新增的复现及补丁

助手启动用例 `HelperStartupInstrumentation` 直接运行 helper 的
`InputDumpMonitor`，注入符合原严格 parser 的 EventHub 样本；Android
权限、线程和关屏/开屏生命周期均实际执行。AOSP 没有 Vivo 内置节点，
这属于合成状态采样证据，不能冒充物理键测试。旧 monitor 在未锁亮屏、
idle、prewarm=false 时不采样；四次关屏按键交付 3 DOWN/4 UP，
7 个断言中只通过 3 个，记录保存在 Git 外
`pvs-20261106-helper-startup-before`。修改 register/screenChanged/
setPrewarm/readLoop，让 listener 注册期间持续保留基线后，同一输入
通过 7/7，记录在 `pvs-20261106-helper-startup-after`。
初次注册时已按住的音量键仍不补 DOWN，unregister 仍停止采样，
`inputActive` 只有取得基线后才为 true。原严格设备验证没有放宽。
这证明 `b56330c` 引入的按需采样会丢首键；尚未证明用户真机本次复发
一定发生在这一阶段，也没有证据把助手问题归因于最近 `db2caf0`。
持续 25 ms 采样增加亮屏轮询工作，需在真机记录运行开销。

特权长按用例 `PrivilegedHoldInstrumentation` 使用实际选定的 Shizuku
volume Binder/AudioManager，合成 raw 按键和 dedicated-reader readiness，
无障碍关闭。第一个试跑被迟到的 AOSP `no readable volume-key device`
状态覆盖，6/10；该次只证明 fixture 有竞争。第一次修正 generation 后
9/10，证明原未提交时长补丁仍只在 UP 后写入；另一次 APK 更新后
迟到的 ServiceConnection 又覆盖 readiness，3/4。
后来明确 disconnect 实际 input ServiceConnection，再放入合成 reader；
volume writer 保留真实特权连接。不要将这些失去 readiness 的运行归因为
生产长按算法。各失败均保留，见 Git 外 `pvs-20261106-privileged-hold-*`。
另有一次 12/13 失败：检查时第一条 Binder 请求刚排队、读数尚未变化。
fixture 增加有上限的 200 ms 真实 readback 等待，仍要求 UP 前已写入；
没有把 pending target 当作实际音量。

从独立 `db2caf0` checkout 构建原版 APK
`b6aff33e75330a54fdc23543b5d7417dbb4795b2a6904f08bee2050bf918fe3f`，
运行 Android repeat cadence 的合成 raw/native-write 用例。模拟器
ViewConfiguration 是 timeout=400、delay=50 ms。原版
`RawVolumePress.up` 仍用 500/100 ms，700 ms hold 只算 4 次：
原生读数已到 7/15，`onExplicitKey` UP 后
`applyRawAtRelease` 计划 target=8，实际 Shizuku 写回 8/15。
此为可复现的向上修正，原版 9/13，记录
`pvs-20261106-privileged-hold-original-native-timing`。
`0add833` 引入时长常量和 release-only 结算；
`db2caf0` 将该路径扩展到未锁亮屏，是该回归进入亮屏的具体提交。
该证据证明上述模拟器路径；Vivo 的实际重复速率和用户播放器/路由仍需实测。

当前补丁使用 Android repeat timeout/delay；选定的 Shizuku/Root 在
fresh DOWN 达到长按时间后持续按原起点的 `VolumeStepHistory` 计划写入。
`Gesture.rawCount` 记录本键已累计次数，UP 只补未累计的次数，
不能因迟到的显式 repeat 减少已经执行的 count。原起点不会被中途
acknowledgement 改写；写入仍受 expected-current、expiry 和 generation
限制。进入 privileged hold 后停止向原 maskOrigin 反复恢复，
面板只显示已确认的 Android readback；息屏仍隐藏。helper 的
release 门控、power chord、stale/unmatched、10 s 上限保持。
当前补丁的最终版本测试及真机矩阵还需记录；不能用较早 fixture 的
10/10 或 helper 的 7/7 代替最终 APK 全套结果。

最终主应用 APK
`ad128c030af7a20450e5f5a5d4e4045889f32c979d9cdd90dbf7614f911acf0a`
已构建并安装在 API 33 模拟器。真实单测 64/64（helper 3/3），main/helper
release lint 通过。当前已完成：

- `pvs-20261106-privileged-hold-final`：Shizuku 合成 raw + 真 Binder，13/13。
- `pvs-20261106-final-release-wake-5`、`-10`：5/10 阶各 27/27，包含 R31/R32、
  R35 及完整 release 后开屏/快速 burst。
- `pvs-20261106-final-helper-startup`：严格合成 dump + 真 monitor/power，7/7。
- `pvs-20261106-root-hold-final`：0 个断言执行，选定 Root writer 未连接。
  这是模拟器 app su 前提失败，不能报 Root 通过。

测试工具另有恢复错误：`UiAutomation.executeShellCommand` 直接处理 argv，
restore command 的单引号被当成数据写进模拟器的 accessibility service list，
重复运行累积引号。已删除该处 shell 引号，增加 service-list 字符校验；
把这个模拟器 list 恢复为本轮初始记录的原 component，master 保持原值 0。
没有在个人手机上执行该 fixture。这是测试工具错误，应修工具并记录，
不能把异常 list 当作应用或 Android 自身的故障。此次 fixture 修正后须
再次核对恢复值；所有引用通过计数的报告都有其各自 host/fixture hash。
Root 可用 app su、反向 hold、不同阶数、面板 frames/layout、普通路径和
物理 Vivo 仍须继续测试。

同一最终主应用 APK 又通过普通路径 17/17、既有面板 frames 13/13、
layout 56/56、screen-off 16/16。记录分别是 Git 外
`pvs-20261106-final-ordinary`、`-frames`、`-layout`、`-screen-off`。
这些既有面板用例使用 Accessibility 合成回调；特权新 hold 的逐帧
表现仍需单独验证。上述运行结束后 accessibility service list 为原
component（无引号），master=0；settings 恢复错误已经在该模拟器清除。

### 扩展长按测试与 Root 实际写入

`PrivilegedHoldInstrumentation` 现有 31 个断言：增加了显式 REPEAT、
相反方向长按沿原 off-grid 路径恢复、stale DOWN、双音量键 chord、
10 s expiry、reader readiness 丢失、关闭总开关、切换模式以及长按面板
实际逐帧读数/固定第三行。面板允许 CONFIRMED 和保存真实读数的
SETTLING，禁止 PENDING；没有把 pending target 当作实际音量。

首轮 7 阶 Shizuku 29/31 的两个失败属于 fixture 条件错误：它把真实
SETTLING 帧也当成 pending。原帧记录显示 displayed/system 均为
8/8，随后 CONFIRMED；修正条件后 31/31。5 阶另一轮 30/31 的反向
失败是合成 REPEAT 在 instrumentation 主线程队列延迟 320 ms，被正确
拒绝。普通 fresh 用例改为在主线程生成时间戳；独立 stale 用例保持
300 ms 延迟，生产 250 ms 门槛未放宽。失败及重跑分别保留在
`pvs-20261106-hold-expanded-*` 和 `pvs-20261106-hold-fresh-shizuku-5`。

API 33 AOSP 的 `/system/xbin` 是 root:shell/0750；单独替换 su 文件仍
无法让普通 App 执行。测试用 Root broker 仅接受 UID 0 和本轮安装的
精确主应用 UID（SO_PEERCRED），转交真实 stdin/out/err，真实 root shell
启动 libsu RootService。临时绑定可执行测试目录并暂设 SELinux permissive，
原 binary/目录权限/SELinux 必须恢复。不能把该结果写成 Magisk/SukiSU
授权或 Vivo Root 物理键认证。其他测试 host UID 被 broker 拒绝。
目录修正前的 0/0 前提失败及其日志均保留。

此前 APK `ad128c...` 的 Shizuku 7 阶及 Root 5/7 阶扩展长按各 31/31；
Root 原后端套件 7 阶 102/102，含真正 UID-0 volume Binder、expected
current、expiry、内部 stream 拒绝及已有行为。`VolumeShellService`
构造函数仍检查真实 `Process.myUid()`，没有替换 writer。

为修正 Root 外接 HID 工具，将 `RootVolumeBridge.diagnostics` 增加实际
`ready` 布尔字段。当前 APK SHA-256 为
`2b92551f1023fbe12af87c533f6f85904671611a42069663441c1f3d5e6bf7fe`，
host 为 `95c7e7935f12ae37b8f2c20e071331c8694b44650184f2b4670358567e541a40`；
main 单测 64/64、main/helper release build/lint 通过。此 hash 下 Root
7 阶和 Shizuku 7 阶扩展长按各 31/31，记录为
`pvs-20261106-current-hold-root-7` 和 `-shizuku-7`。
较早 hash 的普通/frames/layout/screen-off 通过计数不算此 hash 的重跑。

外接 HID 工具的另外几处错误也已修正：Root writer 与 reader 检查
分开，writer 已连接而设备尚未创建不应失败；dumpsys 的缩进不能导致
Root 行匹配失败；恢复原无障碍 list/master，停止 HID 后核对原
所选 backend/reader；临时等于 target 后又变化时继续等待有上限的稳定
读数。ADB-root 模拟器不能运行只允许 shell UID 的 `ShellVolumePreset`，
可显式省略 `--preset-dex`，用 `cmd media_session` 并核对实际 readback。
原失败保存为 `pvs-20261106-external-root-hid*`，不算生产 Root 启动失败。

### R36：快速 raw 连按后，补写之后仍有迟到的系统写入

当前 `2b92551f...`，API 33 AOSP，亮屏解锁、idle、media-only、10 阶，
起点 12/15；外接 UHID 先 UP/DOWN 回到 12，再连续五次 60 ms DOWN。
Root 和 Shizuku 的每个 raw DOWN/UP 均实际收到，最终计划 target=5；
两条路径最后实际均为 4/15，外接 HID 用例失败。
记录为 `pvs-20261106-external-root-hid-stable-wait` 和
`pvs-20261106-external-shizuku-hid-stable-wait`。这属于外部 HID 输入
及真实 raw reader/volume IPC 证据，未按 Vivo 内置物理键。

Root 示例：最后 UP receipt=163604834，target=5 request=163604841；
系统仍写 4/3/2/1/0，163605195 只补写一次；163605262 系统再写 4，
163605268 Binder 返回 5；163605856 超时，实际 4，UNCONFIRMED。
Shizuku 示例：最后 UP=163778751，target=5 request=163778753；
163779092 settling 补写因 expected-current 变化被拒绝，随后一次有界
重试；163779282 读回 5 并确认，163779315 系统再写 4。
因此 matching Binder 返回值/单次 confirmation 不能证明后续稳定。

具体已有代码为 `applyRawAtRelease` 的 release+300 ms、
`confirmCorrection` 的 `stabilizationRetried=true; stabilizeUntil=0`，
以及 `RawCorrectionTiming.shouldRetryAfterSettle` 的单次补写门控。
该工具类/门控由 `b56330cb0cca86a1b620f757f980de2c6112907a` 引入；
`db2caf0` 将 raw release 路径扩展到未锁亮屏。本条只证明当前模拟器
失败及相关旧代码；尚未用原版相同输入证明该新增现象由某一提交引入。
后续应复现多批迟到写入，检查每批相对最后 release/补写/ack 的时间，
保持 expected-current、总超时、旧 gesture/mode cancellation 和助手
门控；不能通过无限重试、延长 freshness 或忽略失败消除报告。
原用户两项问题的物理 Vivo 验证仍未完成。

本轮后段 Vivo 重新接入。已在任何手机设置/安装/启动操作之前只读保存
当前状态到 Git 外的私有目录 `pvs-20261106-vivo-untouched`：Root 选中、
50 阶、原生 0/150、亮屏解锁、无障碍已连接、两个 raw 设备、无 pending。
该状态采集不是物理键测试，也没有启动 Shizuku 或恢复任何失败状态。
手机主 APK 仍为前述已撤回的 `75579a9b...`（hadRelease 试验版），helper 为
`765d772bc1472e7ad0a8d4b4a790d0299ee8c284aa7657d76ea9da0383b3d24b`。
两者原 APK 已私有备份；手机未安装 test host/observer。本轮尚未更新或
改变手机设置，因此不能把模拟器修复当作手机已运行的版本。

临时 Root 测试后的 API 33 模拟器已恢复原 `/system/xbin` 目录
root:shell/0750、原 su root:shell/4750/su_exec、原 binary SHA-256
`b2b5c4dcbd49eefb9ba46b2fc45379b3e3636aa8f0b71847f83ff5175f63119a`，
SELinux Enforcing。已停止本轮启动的 Shizuku server 和 Root broker、
删除 introduced broker/HID 文件，取消测试引入的 root_authorized preference。
保留原无障碍 component/master=0、USB stay-awake=1、原生 10/15。
最后查找临时进程无匹配（grep exit 1 是此处的预期结果）。

#### 同一输入时序的原版/补丁对照

增加 `PrivilegedHoldInstrumentation --scenario settling`：三次合成短
raw press/release，起点 14/15、10 阶、目标 10，然后独立线程通过真实
AudioManager 在 release 后约 150/350/550 ms 分批写入 9/8/7。
真实 writer 仍为官方 Shizuku Binder；合成 raw 及 AudioManager 时序
不能记成外接 HID 或内置物理键。

原 `db2caf0` APK `b6aff33e...`，最终 fixture host
`c3c53369007bf287efb5d514cc09559ea1a610ce27998c135065997364e66e0f`，
写入实际发生在 168/386/572 ms，最终 7，6/8；
当前主 APK
`81a21d5d5c27b7b971bf9d7330cc6ad8d672f54881e1fae7b79a978e1dde3982`，
同一 host、实际 155/357/583 ms，最终 10，8/8。
原失败和重跑保留在 Git 外
`pvs-20261106-original-independent-native`、
`pvs-20261106-quiet-final-independent-native`。
这证明原 release+300 ms 单次门控无法处理此多批写入时序；
其具体引入点仍为 `b56330c`，亮屏扩展为 `db2caf0`。
未对每个历史提交做完整物理重现，不扩展为 Vivo 实测结论。

补丁在 `observeCorrectionReadback` 中把稳定等待延至最后一次实际
读数变化后 300 ms，`RawCorrectionTiming.quietUntil` 保留原一秒
总期限。`confirmCorrection` 仍只允许一次 settling 补写，其 IPC
expiry 为当前时间+500 ms 与原总期限的较小值；补写后继续观察真实
readback，不再清零 `stabilizeUntil`。每次 IPC 的 expected-current、
generation 和单次 stale-expected 重试保持。新 gesture 仍继承原
累计方向/舍入历史，并使旧补写失效。超过总期限仍不匹配必须报告
UNCONFIRMED，不能延长 freshness 或无限补写。

中间 APK `60822b90...` 已通过真实外接 UHID 的完整亮屏 Shizuku
用例，含五次快速 DOWN、反向五次和双向长按；记录
`pvs-20261106-r36-quiet-hid-shizuku`。最终 `81a21d5d...` 的同类测试
及 Root/手机矩阵另记，不复用中间 hash 的通过结果。
最终主单测 65/65、helper 3/3、main/helper release lint/build 通过。

测试工具失败也保留：最初逐次 `runOnMainSync` 安排 native write，
以及后来的主 Handler 定时版本，在 overlay 主线程拥塞时把第一批
150 ms 写入延迟到 501 ms，已经晚于确认。它们不能证明预定的
150/350/550 ms 时序。现用独立 writer 线程，记录每批实际 offset，
不把系统写入排进被测 App 的主消息队列。前版失败与有限成功分别
保留在 `pvs-20261106-r36-{original,quiet}-staggered`、
`*-scheduled`、`pvs-20261106-quiet-final-settling-shizuku`。

### R37：关闭总开关后，已在观察中的旧纠正仍可能补写

最终独立 native fixture 另测 disabled、reader lost 和 mode changed。
原 `db2caf0` 关闭总开关后仍将后续 preset 覆盖，属于既有
`dispatchCorrection`/`confirmCorrection` 未复查 enabled 的问题；
目前没有逐提交定位其首次引入，不写成某一提交新增。
补丁在上述两函数复查 `correctionStillAllowed`：enabled、stream
scope、raw reader readiness、所选 mode 和正常 audio mode，失效即
取消旧工作。最终 fixture 三项均通过，原 disabled 失败记录保留在
R36 原版对照目录。读键丢失/模式切换不是总开关用例的替代证据。

#### 最终 APK 的既有回归与手机安装

同一主 APK `81a21d5d...`，模拟器 Shizuku hold 7 阶 34/34、
released-key-wake 5/10 阶各 27/27、helper-startup 7/7、
ordinary 17/17、frames 13/13、layout 56/56、screen-off 16/16。
记录为 `pvs-20261106-quiet-matrix-*`。该 host 为 `c3c53369...`。
三模式首轮 9/10 后报 null receiver：真实 Root Binder 不可用，
没有 app 可用 su，不能报三模式通过。临时 Root broker 设置好后
重跑结果另记。Root hold 7 阶 34/34、settling 8/8、backend 7 阶
102/102，记录 `pvs-20261106-quiet-root-*`，真实 UID-0 writer 保持。

本轮第一次 phone update 前又只读保存较新状态到私有
`pvs-20261106-vivo-before-update`；仍为 Root/50 阶/0/150/awake idle。
main/helper/host 与原安装 APK 证书 SHA-256 均为
`ba65663993e5a1001eec6828d3e6250c6b12caf00afb15f058c92462804c71b5`。
已直接升级 main/helper，没有卸载用户 app；Root ready、两个 raw
设备和原无障碍连接保持。手机 backend/物理输入/恢复结果另记。
Vivo 更新 test host 时弹出原生安全安装页，需确认该同签名测试包；
不是 App 后端失败。临时测试 host 在最终恢复时须移除。

后台套件 `ShizukuRegressionInstrumentation` 原来对亮屏 helper
`prewarm=false` 要求 monitor 停止，与 R31 为保存首次物理键而保留
baseline 的新行为冲突。已改为检查 baseline 继续运行；listener
unregister/death 停止仍由 helper-startup 独立验证。
该后台套件注入 Accessibility 回调，在实际 raw reader 可用的手机上
会被正确的 privileged ownership 丢弃。测试改为只在每次合成回调的
主线程执行期间清零并恢复 reader device count，保留真正所选 writer。
报告明确标注合成 Accessibility fallback，不能把它记成 raw/物理
读键验证。真实 raw ownership 仍由 hold/HID/内置物理键分别检查。
Root 输出标签也改为实际 Root，不能沿用 Shizuku 名称。

手机主 APK `81a21d5d...`、更新后的 backend fixture：
Vivo V2419A/API 36，Root 50/64 阶各 102/102、
Shizuku 50/64 阶各 102/102、助手 50/64 阶各 107/107；
记录 `pvs-20261106-vivo-backend-{root,shizuku,companion}-{50,64}`。
测试使用合成 Accessibility fallback 与真实所选 IPC；没有证明
内置按钮、Bluetooth 播放、真实 off-screen 或 reboot。
每次 instrumentation 后启动普通主进程，恢复原 Root/50 阶/0，
手机无障碍需在最后重新绑定并核对，不能只看设置列表。
该 APK 的 Root 三模式模拟器重跑 31/31。

### R38：快速反向时连续 expected-current 拒绝提前结束稳定观察

主 APK `81a21d5d...`、API 33 模拟器、亮屏 idle、10 阶，
真实外接 UHID 的 Root 五次 DOWN 到 5 后，五次 UP 计划 12，
最终 15，UNCONFIRMED。所有边沿 fresh 且实际收到。
记录 `pvs-20261106-quiet-final-hid-rerun-root`。
最后 request=165042355；165042374 Root -1，重试时当前 13；
165042381/384 系统继续写 14/15，165042385 再次 -1；
`handleBackendResult` 此时清空 pending，单次 settling 补写再无机会。
`git blame` 将该函数及终止行精确定位到 `b56330cb0cca86a1b620f757f980de2c6112907a`。
这属于原逻辑在新测试中暴露，不能写成 `quietUntil` 新增的代码。

补丁保留 -1 后的稳定观察：两次 early expected-current 拒绝后，
若原稳定窗口仍存在、未到总超时、无新 gesture、mode/readiness/enabled
仍有效，则进入 `confirmCorrection` 等待原有的单次 settling 补写。
权限错误、其他错误和过期仍不反复写；立即 stale retry 与总期限不放宽。
`PrivilegedHoldInstrumentation` 增加 synthetic -1/attempt=1 回调与
真实 AudioManager/所选 writer 的针对性断言，最终的真实 Root HID
必须另行重跑。该测试回调不算真实 IPC 返回 -1 的证明，前述 HID
失败保留了真实拒绝链路。

HID 工具原来只在 700 ms hold 完成后检查 target，漏看长按过程中
已经到过的更低/更高读数。现独立采集长按期间真实 native readback，
DOWN settled 不得超过 live minimum，UP settled 不得低于 live maximum；
目标正确与没有回弹须分别通过。`--root-hid` 仅让已授权的 su 运行
外接 UHID 生成器，避免改变个人手机 `/dev/uhid` 权限；生产所选
volume backend 及物理 GPIO 判定不变。

三模式 fixture 退出后主 instrumentation 进程已经停止，未重新启动
普通 App 的两次 HID 尝试没有取得 service screen/readiness，属于测试
前提失败；记录 `pvs-20261106-quiet-final-hid-{root,shizuku}`。
重启普通 App 后 Root 才出现本条真实失败，Shizuku 全套通过；
两类失败和成功不合并。后续 final hash 及手机物理验证仍须补记。

R38 补丁主 APK
`016ddf763475fb6d351629227f9f61d08b8dae722a7a97e5ced7cc1f1ec353b8`，
host
`0f1af7da73765046c38df049ab5b896e49f5f2a656eae6a7c65416b83ad6c42f`，
Root settling 10/10，记录 `pvs-20261106-r38-root-settling`。
首轮真实 Root HID 的快速 DOWN/反向均通过，随后相反方向 700 ms
hold 没有回到起点，用例失败。该轮工具尚未在 exception 前捕获
service，恢复之后不能重建失败 target/count，不凭该记录猜原因。
原失败 `pvs-20261106-r38-root-hid` 保留。工具现在在任何异常时先
保存 native readback/service，再进入恢复；同 hash 重跑完整 40 项
通过，含 live minimum/maximum，记录
`pvs-20261106-r38-root-hid-failure-trace`。最终计数以 JSON checks
为准；不删掉失败，也不以一次重跑声称所有真机 hold 已解决。
Vivo 的 50/64 阶 live hold 和原两项物理操作仍须继续。

### R39：原生长按步幅大于自定义步幅时，实时纠正仍把音量拉回

R38 APK `016ddf76...`，Vivo V2419A/API 36、亮屏解锁、idle、
50 阶、起点 121/150，真实外接 UHID 700 ms DOWN。
Root 和 Shizuku 的短按、五次快速 DOWN/反向均通过；长按最终
target=97 也通过，但 live minimum 检查失败。
记录 `pvs-20261106-vivo-live-{root,shizuku}-50`。
这是该手机上的外接 HID/真实 raw reader/volume IPC；没有按内置 GPIO。

Root：135220039 原生 121→111；135220271 新实时 hold 补丁按两次
自定义步计算 target=115，写入反向调高；随后原生 105/95，应用
又写回 112。135220591 松键后原生继续到 87；
135220896 settling 补写重新调高到 97。
Shizuku 同样：135396493 原生 121→111，135396726 target=115；
135397055 松键后 97→87，135397353 再写 97。
其 live samples 包含 95→102→109 和最终 87→97。
用户所说“先快速下降，过一会儿又修正回去”仍然存在；之前的
ViewConfiguration/release count 修正不能列为完整解决。

此前模拟器 native hold fixture 每次原生只降 1/15、自定义 7 阶
每步约 2/15，计算目标比原生更快，遗漏了 native step > custom step
的实际 Vivo 情况。必须在模拟器覆盖原生步幅大于、等于、小于
自定义步幅，独立检查整个 hold 与 UP 后的单向变化。
不能只把最终 target 匹配或 fixed duration count 写成“没有回弹”。

具体失败代码包括本轮未提交的 `advancePrivilegedHold`（总按原起点/
rawCount 计划，不检查真实音量已更远）、其调用 `apply`、
`handleBackendResult` 对更远的同方向 current 仍重试旧 target，
以及 `applyRawAtRelease`/`confirmCorrection` 的较慢 final target。
`advancePrivilegedHold` 尚无引入提交，不能虚构提交归因；原有
release-only/500/100 ms 提交与 R35/R38 的精确提交仍按各自记录保留。
下一版长按需保留已经发生的更快原生进展，避免重试较慢旧目标。
短按总数、origin/history、助手 release 门控、freshness、mode/chord
取消、expected-current 与总超时均仍须保持，不能硬编码 150/50/10。
本条尚未修复，不声称手机已满足原两项要求。

Root 用例最后 backend restoration 的短等待失败，但随后只读确认
Root writer ready、两个原输入设备、原 Root/50 阶/0、无 pending；
无障碍 runtime 未连接。已只重新绑定自己原有 component，保留完整
其他 services，核对 connected=true；Shizuku 用例退出后再次需核对。
重连 UI 工具因手机缺少可选 `uitools` observer 而失败，没有完成 UI
操作；改用已有授权的 secure settings 只重绑自身并核对实际连接。
用户中断后手机已息屏/锁定；按较新的屏幕状态继续，不能恢复旧
awake snapshot 覆盖用户变化。

当前 hash 模拟器完整重跑仍有两处失败：Root hold 35/36，失败在
staggered native writes 最终计数；released-key-wake 5 阶 16/17 中途
失败在 unlock 后 native transition，需核对原 trace/timing，不能删掉。
其他结果：Shizuku hold 36/36、settling 10/10、wake 10 阶 27/27、
helper-startup 7/7、ordinary 17/17、frames 13/13、layout 56/56、
screen-off 16/16、Root modes 31/31。
记录为 `pvs-20261106-r38-matrix-*`。这些结果属于 `016ddf76...`，
后续 R39 修正 hash 须分别记录，当前失败不以别的 hash 重跑覆盖。

中断后已确认完整矩阵结束，临时 emulator Root broker/本轮 Shizuku
server 均停止。恢复原 xbin 0750、su 4750/su_exec 原 SHA-256
`b2b5c4dcbd49eefb9ba46b2fc45379b3e3636aa8f0b71847f83ff5175f63119a`、
SELinux Enforcing、原 accessibility list/master=0、stay-awake=1、
volume=10；移除本轮 root_authorized 和 broker bind/files。
手机较新只读状态：仍为 `016ddf76...`、Root/50 阶/0/150/idle、
两个 raw 设备、无 pending；重新绑定自身服务后 connected=true，
最新读数为 awake/unlocked（未为恢复旧快照而主动改变屏幕状态）。
本轮 host 仍安装，留给后续验证，最终恢复时须移除；Shizuku 原
server 未启动或停止。R39 尚未实现修正，原两项物理要求尚未验证。

### R39 第一轮修正与 R40：已接受的绝对写入在系统 repeat 后才生效

R39 第一轮主 APK `e5bcaca25a360685d735d0c3c49a94ac1c9292ac4853d97c7bfc85cc7fb3531c`，
helper `468e6353...`，host `bbfab0bc...`。`Plan.advancePast` 沿原始
舍入路径追上更远的 readback，`preserveNativeHoldProgress` 统一用于
apply、dispatch、rejection、confirmation。新增的逻辑步数独立于
rawCount，防止下一次 tick 或 UP 又使用更慢目标。

模拟器同一 native-hold fixture：旧 `016ddf76...` 16/20，
equal/larger 的 DOWN/UP 单调性四项失败；新 `e5bcaca2...`
Shizuku/Root 各 20/20。原生小/等/大步幅在真实 AudioManager 上
连续采样至 UP 后 1.4 秒。保留 `pvs-20261106-r39-before-native`
（最初 steps listener 重连导致 synthetic readiness 丢失，2 个前提通过、
运行错误）、`-before-native-ready`（有效反例）、`-after-native`、
`-native-root`。更换阶数后 fixture 必须重新安装合成 reader；
不能把输入前提失败归为产品掉键。

主应用 67、helper 3 个单测与 release lint/build 通过。
Shizuku full hold 36/36；Root full 首轮 35/36，独立 settling 10/10、
完整 rerun 36/36。原失败中最后原生写实际为 release+554 ms，
应用在 +706 ms 读到它，300 ms quiet 后 dispatch 到达时已超原
一秒 expiry；保留失败与重跑，不延长期限或删除计数断言。
记录 `pvs-20261106-r39-full-root`、`-root-settling`、
`-full-root-rerun`。`e5bcaca2...` 其他完整重跑：
helper-startup 7/7、ordinary 17/17、frames 13/13、layout 56/56、
screen-off 16/16、modes 31/31、wake 5/10 阶分别 27/27。
这些结果仅属于该 hash，不能移作后续版本结果。

Vivo 主应用普通 ADB install 首次因锁屏时确认拒绝失败，原输出保留。
按当前可见 installer checkbox/button 确认后主应用与 host 均 Success；
已授权的解锁信息未写入工程。原 stay-awake=0 临时设为 3，
最终须恢复；其他无障碍 component 保留，只重新绑定自身并核对连接。

Vivo V2419A/API 36、Root/50、idle、外接 UHID：
`pvs-20261106-r39-vivo-live-root-50`。DOWN 从 121 降至 46，
所有采样单调；短按、五次连按、精确短按反向均通过。
但新独立 UP 从 29 最终到 107，实时仍有 66→59，故整体失败。
trace 136657504 dispatch target=59，系统原生在其实际生效前到 66，
136657522 读回 59；UP 后 136657698 dispatch target=98，
136657715 原生到 105，136657730 应用才生效回 98。
expected-current 检查保护的是 IPC 调用之前的状态，不能令 AudioService
已排队的 native repeat 和 absolute write 成为同一个事务。
此反例已推翻“只在发写前追上 current 就不会回弹”的完整修复判断。
R40 是本轮未提交的混合实时写入补丁暴露的竞态，没有可虚构的引入提交；
旧提交归因仍见 R33/R35/R39。

下一轮修正：已观察到原生长按时保留其 repeat，停止并取消尚未发出的
实时绝对纠正，完整 release 后等待实际 readback quiet 再结算；原生
不动的 down/up-only 仍保留 Android timer。继续保留 anchored rounding、
rawCount 与追加 native steps 分离、助手 release 门控、取消与 expiry。
HID fixture 现逐次检查 DOWN/UP 至 settled 的单调性、至少 raw repeat
进展、原始舍入路径和无 pending；快速短按精确反向仍独立检查。
相反长按的 native 进展可不同，不能再强制相等时长返回原点。
R40 修正和两个原始物理要求尚未验证。

R40 第二轮主 APK
`7ae4dd161a31dc386a6dd17b417fd80bdd1829f9d93f1039c655a6cda50237b9`，
host `9bf2358e...`，helper 仍为 `468e6353...`。
`Gesture.nativeHold` 记录本次已发生的 native progression；
`lastHoldWrite` 排除应用自己的已确认写入，防止 down/up-only timer
被自己的 correction 错判为 native repeat。已识别 native hold 不并发
dispatch，UP 跳过即时写入，detect/observe 按实际 readback quiet 等待；
原生不动的键仍使用 Android timer，最后沿 anchored path 追上更远值。
源代码尚未提交，均不能虚构引入 commit。

此 hash 主应用 67/helper 3 单测、release build/lint 通过。
模拟器 Root/Shizuku native-hold 分别 26/26，完整 hold 分别 36/36，
包括 timer/no-native、native repeat、反向、frame、取消与 settling。
记录 `pvs-20261106-r40-{root,shizuku}-{native-hold,full}`；
`-native-root` 为同 hash 另一次 26/26。

Vivo 首轮 `pvs-20261106-r40-vivo-live-root-50` 在按键前失败：
preferences instrumentation 退出后自身 Accessibility 被系统标记
crashed、secure component 已启用但 connected=false；未执行任何 volume key。
这是需要单独保留的恢复失败，不能用后续按键成功覆盖它。
已仅重新绑定自身 component，保留其余 services。
新增 `--existing-setup` 不发 preferences instrumentation、不移除服务，
在已选择、ready、相同阶数且 connected 的状态验证实际 raw/HID；
默认自动恢复测试保持原要求。

`pvs-20261106-r40-vivo-existing-root-50` 45/45、无 error，实际外接 UHID、
idle/亮屏未锁、50 阶。DOWN 原生 121→111→101→…→41，
UP 后 317 ms 应用沿原舍入路径写 40，持续采样无升回。
独立 UP 原生 29→39→49→…→109，UP 后 305 ms 写 110，无降回。
每次确认后继续采样超过一秒；短按 `+-`、五次连按及精确反向通过。
以上不包含内置物理键，也不证明自动恢复的失败已修好。
Shizuku/64 阶和最终其他矩阵按各自后续记录，两个原始物理要求仍未完成。

汇总工具修正：`write_report.py` 以前只读 instrumentation `results`，
外接 HID 的有效 `checks` 会被列成 0/0。现接受两种字段，
仍要求 matching APK hash、每项 pass、success 和无 errors；
原失败结果不被丢弃。物理矩阵仍单列，不从自动化汇总推导通过。

R40 同 hash 最终矩阵 `pvs-20261106-r40-final-matrix-*`：
helper-startup 7/7、ordinary 17/17、frames 13/13、layout 56/56、
screen-off 16/16、modes 31/31、wake 5 阶 27/27。
wake 10 阶原轮 3/4 失败：fixture `press` 在 instrumentation 线程生成
UP 的 observedAt，再排队到 main，实际排队 383 ms；
生产 rawPress 按 250 ms freshness 丢弃该 release，最终只计第二键/repeat。
记录 `pvs-20261106-r40-final-matrix-released-key-wake-10`，原错误保留。
fixture 的普通 fresh `press` 改为在 main 实际交付时生成时间戳；
专门调用 rawKey 的 stale/mismatched 用例保持原 timestamp，不放宽产品
freshness。这是合成事件排队的控制错误，实际 helper 的 callback 延迟
仍需单独记录；不能用此修正声称真机所有延迟 delivery 都已解决。
修正 host 后的独立重跑待按实际结果追加。

普通合成事件时间戳修正后的 host
`6c19a2d51befd4cdaa4abea114687b86ea5b720f1a555496bbb89c045bbe16e2`，
同一主 APK，wake 10/5 阶独立重跑各 27/27：
`pvs-20261106-r40-wake-fresh-{10,5}`。原 383 ms 过期失败仍保留。
该 host 仅安装于模拟器；此前 phone/完整矩阵的 host 是 `9bf2358e...`，
主 APK/helper 相同，不能把 host hash 混写。

Vivo 同一主 APK 的 backend 合成回调+实际所选 volume IPC：
Root 50/64 阶各 102/102、Shizuku 50/64 阶各 102/102、
companion 50/64 阶各 107/107；记录
`pvs-20261106-r40-vivo-backend-{root,shizuku,companion}-{50,64}`。
与已记录 Root/50 一起，实际外接 UHID 的
Root/64、Shizuku/50、Shizuku/64 各 45/45，
记录 `pvs-20261106-r40-vivo-existing-{root,shizuku}-{50,64}`。
所有 live hold 均逐次检查至 release 后 settled；没有内置物理键输入。
fixtures 使用合成 Accessibility 时暂时清除/恢复对应 raw devices；
真正 raw ownership 独立由 existing-setup HID 检查。

当前主 APK 汇总在 Git 外 `pvs-20261106-r40-report/automated-evidence.md`，
26 个匹配 hash 的原轮/重跑均收录；原 synthetic stale-UP 与 phone
自动 Accessibility 恢复失败仍使汇总为 FAIL，未伪造为全绿。
修正后的 wake 与未改变设置的实际 HID 通过只证明各自条件。

测试后核对：phone 恢复较新 Root/50、enabled/media scope 原设置、
0/150、idle、原完整无障碍列表/master=1、connected=true、stay-awake=0、
timeout=600000、息屏/已锁、无 pending/wake lock。未改动原 Shizuku server，
未启动第三方播放器。主/helper 保留本轮修正，phone host 暂留用于后续
物理配置，最终测试结束须移除本轮引入的 host。
emulator 已移除临时 Root authorization/broker/bind，停止本轮 Shizuku，
恢复原 su SHA `b2b5c4dc...`、xbin 0750/su 4750/su_exec、
SELinux Enforcing、原 accessibility list/master=0、stay-awake=1、volume=10。
外接 UHID 与 preset 设备文件已由 fixtures 清理。

按当前 phone capability 验证 event0 为 gpio-keys/VOLUMEUP，
event1 为 mtk-pmic-keys/VOLUMEDOWN/POWER；只读 recorder 编译开启
`-Wall -Wextra -Werror`，打开两个真实 platform 描述符的 1 秒 smoke
返回 ready/finished status=0。未按任何物理键，不能把 smoke 当行为通过。
只读 recorder/script 已准备供下一轮采集，仍需用户实际按内置键。
两个原始问题的 GPIO/PMIC 操作、实际播放/路线、锁屏和 reboot
矩阵仍未验证，goal 保持未完成。

后续物理采集准备时，Vivo 起初仍在线，keeper 为 Root/50、
0/150、idle、connected=true、关屏锁定、无 pending/wake lock；
随后的只读工具检查报 `device not found`，ADB 列表只剩模拟器。
本次尚未修改手机配置、设置音量或启动录制，没有新的内置按键证据。
主 APK 重新核对仍为 `7ae4dd161a31dc386a6dd17b417fd80bdd1829f9d93f1039c655a6cda50237b9`。
恢复连接后先重新保存完整状态，再确认 recorder ready 并配合实际侧键操作；
这次连接中断不归因为产品故障，亦不把已有自动化结果当作物理通过。

### R41：持续基线候选仍丢失真实连按，不能用采样 fixture 宣布修好

用户重新连接手机并实际操作内置按键。主 APK `7ae4dd161a...`、
helper `468e635392...`；助手/50 阶、idle、原生 0–150，起点 105。
Shizuku disabled，主服务 connected，helper checked/available/registered/active，
prewarm=false。独立 PMIC recorder 捕获五次完整 DOWN/UP：
单次持续约 131/145/134/144/140 ms，首键在电源松开后约 893 ms。
五次预期 90，实际长期稳定 95；本轮为失败，不覆盖此前合成 7/7。

主服务 trace 只有前两次 DOWN 和第一次及时 UP；后续交付一个 UP，
其 observed interval=105 ms、主线程接收 age=578 ms，生产 freshness
拒绝了该事件。没有匹配后面三次短按的 callback。当前证据不能区分
helper dump/调度阻塞与 callback 排队的各自贡献，不能只改 freshness
或用估算次数补齐没有观测到的按键。

屏幕也发生切换：开录时亮屏未锁，首个电源后关屏，连按期间系统在
uptime 142788405 再次唤醒屏幕；最终电源在 142790741 令其关屏。
唤醒来源未定位。此轮不能标记为全程稳定息屏通过，状态切换不能抹去
五个真实完整按键只得到错误音量的失败事实。

完整原失败在 Git 外 `pvs-physical-live-1793945086187671000`，
native recorder 正常结束、原日志等级已恢复、未注入 volume key/启动播放器。
后续候选只增加有上限的 helper sample/dump/edge 时间记录、provider dump，
以及 main Binder receipt/main delivery 记录，不修改音量逻辑或事件时间戳。
实际根因和下一轮结果须按新 hash 独立追加。

上述时间诊断候选主 APK `749a33bf8ebf6b5c6d45cd99b2eec02d8afb54e9711212e9fc6c17a93688bc30`、
helper `34d190bf325d7399c42d02542bf7f27dcb341fa7b6f89f6be2d194ab61d75a9e`：
主67/helper3单测无失败、release lint无error；helper-startup模拟器7/7。
首次诊断构建因计时块放在局部变量声明前而编译失败，原输出保留；
移动该块后的独立构建通过，未以成功日志覆盖失败。
Vivo 两包 ADB install 均 Success，原证书升级、原偏好保留，helper DUMP grant
和 continuous baseline 保持，accessibility connected=true，95/150、idle。
尚未执行该 hash 的新物理按键，不把诊断升级记成修复。

同一诊断候选随后完成第二次用户内置按键操作。用户确认五次连按触发相机，
没有再次按电源。PMIC 仍捕获五次完整 DOWN/UP，约 138/130/128/122/132 ms；
95 起点预期80，实际稳定86，仍为失败。
helper 只交付四个 DOWN/UP 对；第三个 DOWN 的 dump 用时144 ms，
后续 dump 在 uptime 143657333–143657520 用时187 ms，跨过第四键的
完整区间143657397–143657519，只读取到两次松键状态，未交付第四个 DOWN。
第三个 UP 在143657520观察到、143657581收到Binder、
143657804才进入main，进入controller时age284 ms而被freshness拒绝。
最后一键的receipt→delivery也排队162 ms。此轮已分别证明采样遗漏
和main排队拒绝，不能将所有漏键归为首次冷启动。

原生Settings快照：长按快捷 `smartkey_primary_switch=0`，
相机 `quick_start_camera=1`、`quick_start_camera_switch=2`；
实际前台为人文街拍相机，用户现象与系统快捷配置一致。原生页面同时明确
说明快捷操作在音乐播放时不可用，并描述息屏双按音量减启动相机。因而无
播放连按打开相机是 Vivo 原生行为候选，不能仅凭时间重合归因于本应用。
原完整录制 `assistant-timing-final.tar`、helper/main时间记录在同一Git外目录，
reader finished status0、正常complete，原log级别恢复。两次原失败均保留。

此前计划关闭相机快捷做对照，用户随后明确纠正：本次目标是助手播放时连按
漏计和 Shizuku/Root 长按回弹，不应盲目阻止系统相机。该计划撤回，设置始终
未改，恢复连接后读数仍为 `0/1/2`。后续保持快捷原值，比较无播放与用户实际
播放；前者用于确认系统按键所有权，后者才验证完整音量调节。只有证据证明
相机由本应用错误触发时才改应用逻辑。不能把关闭系统快捷当修复或测试前提。

连接恢复时原生音量已由先前86变成0/150，服务仍为助手/50阶、idle、
accessibility connected、helper input ready、无pending/wake lock。该变化发生
在中断期间，按恢复规则保留新状态，不用旧快照覆盖。诊断 trace 还出现亮屏
物理音量键期间原生86→0和较长callback延迟；这些是后续播放对照的调查线索，
不是当前已定位根因。

### R43：保持相机快捷原值的实际播放对照

用户选择并启动实际播放器，系统记录同一 UID/PID 的 `USAGE_MEDIA`
AudioTrack 持续 `state:started`；助手/50阶、helper input ready/prewarm=true，
相机快捷保持 `smartkey=0 / camera=1 / camera_switch=2`，没有为测试关闭。

第一次用户操作从82开始，息屏五次内置音量减；helper 严格 platform
EventHub状态交付五个完整 DOWN/UP，controller依次规划79/76/73/70/67，
独立Android最终读数67，播放仍由原AudioTrack持有。第二次从67开始，
五个完整 callback依次规划64/61/58/55/52，最终读数52，仍无pending/
wake lock，播放保持started。两次都准确减少五个3-unit步进，没有用户报告的
少计，也没有长按回弹。

两个只读 native recorder 都在用户操作前达到自身有界时限：第一轮600秒
结束于uptime152526，用户键在153442；第二轮180秒结束于153715，用户键在
153743。原空窗口和正常complete均保留，不能把后来的helper trace写成独立
PMIC recorder通过。因此当前证据是“用户实际内置键 + 生产helper/app路径”
两次通过，严格旁路PMIC同步证据仍缺失；不得虚构 recorder pass。

这组对照支持用户提出的 Vivo 按键所有权区别：无播放时原生快捷可能接管
息屏连按，实际媒体播放时快捷不可用且五次音量调节完整。它不证明 idle
相机是本应用触发，也不支持关闭相机快捷。助手问题后续验收以实际播放为
主场景，同时保留 idle 系统行为，不将二者混报。

### R44：实际播放下的 Shizuku 内置长按

保持同一用户实际播放器和相机快捷原值，切换为Shizuku；诊断确认
`enabled=true, ready=true, raw devices=2`，生产助手输入停止。用户关屏后
内置音量减长按约1.32秒。Shizuku raw输入以接近50 ms间隔推进，起点52，
写入43/40/37/34/31/28/25/22/19/16/13/10/7；UP结算第16步到4。
每次写入都由实际readback确认，最后4在331 ms确认。

用户随后等待后按电源；POWER DOWN发生在最终确认约1.15秒后。服务和独立
`cmd media_session`在约34秒后仍读4/150，pending none、wake lock false，
同一 UID/PID/session 的 `USAGE_MEDIA` AudioTrack仍started。系统参数证明
本轮没有“先快速减小、稍后修正回去”。

有界native recorder从uptime154213运行到154813，用户长按发生在160053，
仍然在操作前到时，空事件和正常complete单独保留。因此证据类别是“用户
内置物理键 + 生产Shizuku raw/readback路径通过”，不是独立PMIC旁路通过。
外接HID既有通过结果也继续独立记录，不能互相替代。

### R45：Root 息屏长按的完整 PMIC 录制

恢复连接时 Root ready、devices=2、50阶，用户实际媒体仍在播放；新音量为
76/150，保留该值，没有用此前52的测试基线覆盖。相机快捷保持原值。
独立只读 recorder 在操作前 ready，真实 platform `mtk-pmic-keys`
在 `/dev/input/event1` 给出 DOWN=162860.972399、UP=162862.243123，
持续1.270724秒。该设备本次只有 DOWN/UP，没有内核 repeat；生产 Root
按 Android timeout/delay 推进，不能把应用timer称为PMIC repeat。

Root 沿76起点的50阶路径结算19步到19/150，UP后目标仍为19；
最后301 ms确认。独立轮询观测76→67→49→25→19，后续未观测回升；
423次服务状态采样全部 playback active=true，原播放器仍持有started媒体。
约31秒后的手动readback及正常结束的volume-final均为19，recorder末时刻
162907.312536，覆盖UP后约45秒。native reader status=0、errors为空，
主服务pending none、wake lock false。这是内置物理键、真实播放、息屏
down/up-only长按的完整通过；不替代亮屏原生repeat或其他route的测试。

完整录制保存在Git外 `pvs-playback-contrast-1793954742/root-physical-hold-complete.tar`。
此前USB断开导致未就绪的Root录制不计入通过，也不被本轮覆盖。

### R46：助手息屏五连按的完整 PMIC 与 callback 对照

切回助手，Root/Shizuku输入均停止，helper input checked/available/registered、
prewarm=true、真实媒体播放active。测试基线实际读取82/150。
只读recorder在163002.678515就绪；POWER UP为163080.353757，
第一键DOWN为163080.902922，距松开电源约549 ms。

独立PMIC捕获五组完整 DOWN/UP，持续约175/135/155/159/154 ms。
helper也交付五组对应DOWN/UP，全部在freshness期限内；第二键的dump
观察区间80 ms，进入controller时event age86 ms，没有按采样空白补次数。
五次release依次请求79/76/73/70/67，最终67在319 ms确认。
独立系统volume-before=82、volume-final=67；340次状态采样全部
playback active=true，最终播放器AudioTrack仍started。reader正常结束于
163124.438599、status=0、errors为空，覆盖最后UP后约42秒。

这轮证明实际播放、50阶、当前蓝牙路由下，内置息屏五连按没有少计；
此前R43两个到时空窗口仍单独保留。它不证明五次之间所有瞬时native值
单调：生产trace仍记录粗调遮蔽写回82，再在release结算各目标；本轮结论
限定为按键计数与最终稳定值，不把采样间隙或遮蔽写入写成无瞬态波动。
全程未关闭相机快捷；原生 `smartkey=0 / camera=1 / camera_switch=2`、
无障碍完整组件集、stay-awake=0、timeout=600000均保持。

R45/R46实测版本重新读取：主1.5.3/code31，
SHA-256 `749a33bf8ebf6b5c6d45cd99b2eec02d8afb54e9711212e9fc6c17a93688bc30`；
helper1.4/code5，
SHA-256 `34d190bf325d7399c42d02542bf7f27dcb341fa7b6f89f6be2d194ab61d75a9e`。
设备Vivo V2419A/API36，fingerprint
`vivo/PD2419/PD2419:16/BP2A.250605.031.A3_V000L1/compiler261012170621:user/release-keys`。
这是R41追加时间诊断的版本，不能把7ae4dd/468e635的自动化结果
当成本hash重新跑过的完整矩阵。完整R46录制在Git外同目录
`assistant-final-complete.tar`，两轮仅追加验证和文档，未进一步改产品行为。

目前助手三轮实际播放计数均准确，其中R46有完整旁路；Shizuku息屏长按
生产路径通过但旁路到时，Root息屏长按有完整旁路。亮屏未锁的内置
Shizuku/Root长按、64阶与其他route、重启矩阵仍需单独验证；不能宣布
两个原始问题在所有场景已完成。

R46归档后恢复为本轮重新连接时的较新用户音量76/150，实际readback确认76；
助手/50阶和原相机快捷保持，recorder/native reader均已停止。82是后续
助手验证的测试预置，不能称为恢复了重新连接时的用户值。保留真实播放，
不结束用户播放器；开发host与采集工具暂留供未完成的亮屏物理测试，
最终结束时仍需按原快照清理。

### R47：Shizuku 亮屏内置长按触底，非触底结果仍需补测

用户解锁、选择Shizuku并保持实际播放。主/helper为R46相同诊断版本，
50阶、原生0–150；生产reader ready/devices=2，helper输入停止。
只读PMIC recorder在操作前ready，捕获内置音量减
DOWN=163445.478131、UP=163446.406213，持续0.928082秒。
按键处理时noninteractive=false，服务最终interactive=true/keyguard=false。

生产trace依次观察原生长按进度66/46/36/26/6/0，另有poll读到56；
在这段native progression期间没有发出旧的较慢绝对纠正。
UP后等候readback quiet，在163446.720沿路径追加步数得到
current=0/target=0；最终163447.026确认。独立readback轮询变化为
76→66→6→0，473次状态采样均playback active=true，播放器仍started
（原生0导致stream mute，不把静音视为播放器停止）。

正常结束时volume-final仍为0，reader status=0/errors为空，
最后时刻163493.382315，覆盖UP后约47秒，pending none/wake lock false。
原生相机快捷仍为0/1/2。完整归档在Git外同目录
`shizuku-awake-complete.tar`；app-logcat为空，事件依据是同步service
ring/samples和独立PMIC/readback，不把空logcat当作逐事件日志。

本轮没有观测到回升，且native-hold分支的实际运行已得到内置按键证据；
但在UP前已触底，不能据此跳过非触底长按稳定值验证。下一轮同一路由、
阶数和播放，仅缩短长按时间，保留大于0的settled值，再检查是否回弹。

### R48：较短亮屏长按松开后仍有系统写入，最终再次触底

R47相同版本、Shizuku/50阶、实际播放和亮屏未锁，测试基线恢复并读到76。
只读PMIC记录DOWN=163642.248140、UP=163642.951357，
持续0.703217秒。生产trace观察原生66/46/36/26/16/6；
UP进入controller时最后进度为6，随后163643.022的poll读到0。
这证明UP不意味着系统音量已经稳定，不能按松手前的读数立即结束观察。

应用在native progression期间没有并发较慢绝对纠正；等候quiet后，
163643.343读取current=0、沿路径得到target=0，163643.647确认。
最终再次为0而未回升，但不能把这轮文件名中的nonlimit当成非触底通过。
独立轮询观测76→66→16→0，260次状态采样播放均active；
原播放器仍started，原相机快捷未改。reader正常结束于163702.920302，
status=0/errors为空，最终readback=0，覆盖UP后约60秒。

原录制在Git外同目录 `shizuku-awake-nonlimit-complete.tar`，
并与R47保留。该轮补充了晚于UP的原生写入证据，
非零settled长按仍待下一轮更短按住验证；不把到0后的稳定值扩大为
所有非触底长按已修好，也不改repeat速度来迁就测试。

### R49：Shizuku 亮屏长按得到非零稳定值，没有回升

保持R48相同主/helper、50阶、真实媒体播放、亮屏未锁和原相机快捷，
实际起点76/150。PMIC捕获DOWN=163928.831820、
UP=163929.501867，持续0.670047秒；reader在按键前已ready。
生产Shizuku raw输入及时交付，观察native进度66/56/36/26/16，
UP计为raw七步。随后系统仍降到6，不能把UP时16当成稳定值。

163929.814的native-hold结算读取current=6，在原路径追加17步，
总24步得到target=4；163929.816通过Shizuku写4，真实readback确认4，
163930.157最终确认，delay343 ms。没有把原生更快进度写回仅七个
自定义步进对应的55，实际观测也没有0→4的触底回升。

独立轮询观测76→46→6→4，全轮651次状态采样播放均active，
原AudioTrack最终仍started。最终volume-final=4，PMIC reader正常结束于
164005.424567、status=0/errors为空，覆盖UP后约76秒；pending none、
wake lock false，面板实际readback=4/150。

完整归档为Git外同目录 `shizuku-awake-shorter-complete.tar`。
R47/R48触底轮均保留；这轮才补上实际播放、亮屏、50阶、内置音量减
长按的非零稳定值证据。Root亮屏相同场景、其他阶数/route与重启
仍独立待测，不能从共享实现或Shizuku通过推导Root通过。

### R50：Root 亮屏内置长按的非触底结果

在R49归档后恢复实际基线76/150，通过观察到的主应用控件选择Root，
确认ready/devices=2、Shizuku disabled、亮屏未锁、同一真实播放器仍started，
原无障碍组件集和相机快捷不变。版本、路由、50阶与R49相同。

只读PMIC捕获DOWN=164312.909750、UP=164313.533574，
持续0.623824秒。生产Root trace和广播观察76→66→56→46→36→26→16；
native progression期间没有写回仅raw六步对应的58。
UP后164313.879读取current=16，沿原路径追加14步，总20步得到16；
目标与当前真实值相同，无需再覆盖系统。164314.181最终确认，
delay302 ms。独立系统readback及面板真实值均为16/150。

独立轮询观测76→56→16，724次状态采样播放全部active；
最终volume-final=16，reader正常结束于164426.504341、
status=0/errors为空，覆盖UP后约113秒。无触底或观测回升，
pending none、wake lock false、屏幕interactive=true/keyguard=false。
完整归档在Git外同目录 `root-awake-complete.tar`。

至此，本手机当前50阶/实际蓝牙播放的助手息屏五连按（R46）、
Shizuku亮屏非触底长按（R49）、Root亮屏非触底长按（R50）均有
完整内置PMIC、生产路径和真实readback证据；Root息屏长按见R45。
原始采集失败、触底轮和超时空窗口继续保留。其他阶数/route、
反向组合及重启矩阵不由这些通过结果替代，不宣布厂商完整支持。

物理采集结束后，已通过实际控件恢复助手/50阶、enabled=true及较新用户
音量76/150。最终helper checked/available/registered/active、主无障碍
connected=true，完整bound区段同时有GKD与Precise Volume Service；
原组件集/master=1、相机快捷0/1/2、stay-awake=0、timeout=600000不变。
屏幕亮起未锁，pending none、wake lock false，用户播放器仍started，
原Shizuku server PID未改变，没有停止用户媒体或其他服务。

九个本轮正常结束/到时窗口的本地tar逐个验证complete且仍可读，手机上的
对应录制副本、测试reader/script/preset以及本轮引入的testhost已移除。
旧失败与其他目录不删除；原日志等级恢复。移除host的Success又以目标
Android用户的package列表确认，不能将未指定用户的权限错误当成包不存在。

### R51：助手 64 阶息屏五连按，完整物理记录与不均匀舍入

继续使用R46相同主/helper诊断版本和实际蓝牙媒体播放，原相机快捷不变，
从实际76/150开始，通过原生NumberPicker将50改为64。新窗口在按键前
确认reader ready，期间没有注入音量键或切换播放器。
PMIC第一组电源边沿165221.304276/165221.450077；五组DOWN/UP为：
165221.901216/165222.047004、165222.204859/165222.336763、
165222.530475/165222.697175、165222.870013/165223.013609、
165223.159395/165223.309250。最后电源边沿165224.455553/165224.565283
另记；以每个状态采样的interactive/keyguard字段判断屏幕，不能仅据
电源UP推定最终保持亮屏。

生产helper收到五组完整松开，结算请求依次74、71、69、67、64，
与76为原点、150/64累计Java舍入一致。最后165223.649确认64。
独立系统读数和volume-final均64/150，reader正常结束165251.669986，
覆盖最后UP后约28秒，status=0、errors为空、complete存在。
287次状态采样全部playback active=true；旁路还采到59/66等短暂
原生值，因此本记录证明五次计数和最终稳定，不声称音量过程全无
瞬时粗调。收尾pending none、wake lock false、overlay detached，
无障碍connected=true。完整归档保存在Git外
`pvs-current-64-1793968670/assistant64-complete.tar`。

同一主SHA `749a33bf8ebf6b5c6d45cd99b2eec02d8afb54e9711212e9fc6c17a93688bc30`
和helper SHA `34d190bf325d7399c42d02542bf7f27dcb341fa7b6f89f6be2d194ab61d75a9e`
重新构建成功，release单测67/67、主/helper lint通过。
当前同字节APK在API33 disposable emulator重新跑helper-startup7/7、
released-key-wake5/10阶各27/27、ordinary17/17、frames13/13、
layout56/56、screen-off16/16，总163项均通过。每轮JSON含实际APK和
fixture哈希，保存在同目录emulator子目录；此处才补上诊断版本这些
自动化结果，不能继承旧7ae4dd版本的其他通过项目。

切换下一轮模式时系统锁屏超时较短，临时USB stay-awake=2用于已授权
解锁，随后恢复原0。credential bouncer在截图中可能只显示黑色与
指纹图案；原生UI实际包含pattern view。以真实控件bounds和最终
keyguard=false判断解锁，不能把黑色截图单独当作音量或读键失败。
临时UI观察后确认无障碍重新connected且用户播放保持active。
USB连接选择框按观察到的Cancel关闭，未改变连接类型。
Shizuku/Root的64阶物理长按、其他路由和实机重启仍分别待验证。

### R52：Shizuku 64 阶长按发生在独立窗口到期之后

R51后保持实际蓝牙播放，通过原生控件选择Shizuku，确认64阶、
write/raw ready、devices=2、亮屏未锁，恢复已核对的本轮基线76。
新独立reader165652.421334 ready，166252.449950正常到时结束；
其events只有ready/finished，final仍76。因此该完整到时归档
`pvs-current-64-1793968670/shizuku64-expired-complete.tar`没有本次物理键，
不能据主应用记录把它记为独立PMIC长按通过。

用户随后回复按完。生产raw trace在167092.020记录DOWN、
167092.674记录UP；中途原生76→66→46→36→26→16，松开后仍16→6。
167092.987读取current=6，追加23步、总30步，沿76原点/64阶得到6；
没有写回仅raw七步对应的较高目标。167093.291确认6，
本次独立查询也为6/150，播放active、pending none、wake lock false。
这些是生产路径和收尾readback证据，物理边沿与完整过程另行补录。
保留该轮，不把到时空窗口归因为生产控制器失败。

当前诊断APK的Shizuku emulator扩展长按36/36、settling10/10、
native-hold26/26、backend7阶102/102均在R51相同新hash下重跑通过，
每轮JSON和日志保存在同目录emulator子目录。Root同hash测试仍待补。

### R53：Shizuku 64 阶补录有完整边沿，但长按触底

重新确认实际播放、亮屏未锁、Shizuku write/raw ready、64阶，
用flags=0 shell preset恢复本轮实际76/150。只读补录使用最多三个
各600秒的有界窗口；当前第一窗口在167257.633242 ready，
PMIC DOWN=167538.951159、UP=167539.661495，实际0.710336秒。
这轮物理边沿完整，区别于R52的到时空窗口。

生产raw trace在UP前看到76→66→46→36→26→16→6；
UP后系统仍变为0。167539.975读取current=0，原64阶路径追加25步，
总33步得到裁剪目标0，没有写回仅raw八步对应的较高值。
167540.278确认0。925次样本均playback active=true，
独立最终readback为0；reader167595.092355正常结束，覆盖UP后约55秒，
errors为空、complete存在，pending none、wake lock false、面板已隐藏。
完整归档在Git外同目录 `shizuku64-clipped-complete.tar`。

本轮验证端点与松开后稳定，不能代替64阶非触底长按的回弹检查。
不从“约半秒”的操作指引推断实际时长；也不因触底就提高真实音乐
音量去制造非零结果。后续提示以音量开始连续下降时尽快松手。
R52和本轮均保留，Root64阶及其他路由/重启仍待测。
补录首次inline shell loop解析失败，没有启动reader，stderr原样保留；
改用phone-side script后确认ready。它是采集启动失败，不是生产按键故障。

### R54：Root 64 阶亮屏长按得到非零终点，UP 后约 74 秒稳定

保持R53同字节主/helper、实际蓝牙音乐、原相机快捷与无障碍组件，
通过主应用控件选择Root并确认ready/devices=2、Shizuku disabled。
基线恢复并实读76/150，64阶，亮屏未锁；没有为避免触底提高基线。
PMIC DOWN=167962.152672、UP=167962.781090，持续0.628418秒。
生产raw在长按中观察原生进度至16，期间没有覆盖为仅raw六步对应
的較高目标。167963.118在quiet后读取16，沿原点76追加20步，
总26步的64阶目标为15，Root写入15；167963.440确认15。

独立最终读数15/150，末尾连续样本均15，943次样本全部播放active；
reader168036.612133正常结束，覆盖UP后约74秒，status=0、
errors为空、complete存在。pending none、wake lock false，
亮屏未锁，服务面板readback也是15。它补上当前Root/64阶/亮屏/
实际蓝牙播放的非触底物理长按；无观测回升。其他路由与重启未由
此轮替代，Shizuku64阶非触底物理长按仍独立待补。
完整归档在Git外同目录 `root64-nonlimit-complete.tar`。

归档后恢复并确认76/150，继续Root64阶息屏验证。当前hash的Root
emulator矩阵已启动，结果尚未完成，不能预写为通过。

### R55：Root 64 阶真正息屏长按，76→13 非触底稳定

同R54主/helper字节与实际蓝牙音乐，Root64阶、基线76/150，
原相机快捷和无障碍组件不变。独立PMIC保留三组电源边沿，
最后POWER UP=168247.199262，音量DOWN=168248.249823、
UP=168249.906370，实际长按1.656547秒。真实power dump为Asleep、
sleep reason=power_button，生产raw DOWN/UP均noninteractive=true。
不要把指引的“约1秒”当作实际时长。

选定Root按Android400/50 ms计时推进，最后累计27步，
76 + round(-27×150/64)=13。UP前已降至15，UP后结算13；
168250.257确认，独立最终readback13/150。337次样本全部playback
active=true、overlay attached=false，末尾连续13；reader正常结束
168298.846444，覆盖UP后约49秒，status=0、errors为空、complete存在。
最后pending none、wake lock false，屏幕仍真正息屏且锁定。
这轮补上Root64阶实际播放的息屏非触底长按，未见观测回升。
完整归档在Git外同目录 `root-off64-nonlimit-complete.tar`。
其他路由、重启以及Shizuku64阶非触底物理长按仍独立待验。

### R56：当前诊断 APK 的 Root full 原生迟到写入断言再次失败

API33 disposable emulator，主SHA749a33bf...、相同host6c19a2d5...，
临时SO_PEERCRED Root broker只用于合成回调与真实Root volume Binder。
本轮modes31/31、settling10/10、native-hold26/26、backend7阶102/102；
Root full为35/36，不能把其余通过结果记作full通过。
唯一失败：`staggered native writes settle to the complete raw count`。
原失败JSON/日志在Git外
`pvs-current-64-1793968670/emulator-root/emulator-5594-privileged-hold-1793971728279289000.*`。

三次合成短按应落到8/15。独立原生写实际offset155/370/559 ms，
最后写5；生产记录200160.823看到8→5，200161.055开始补写，
200161.056 Root拒绝过期请求，最终保留真实5并显示未确认。
最后原Correction创建约200159.945，原一秒期限约200160.945，
本次补写已超过期限。时间记录与R39第一轮full失败的排队/quiet耗尽
现象一致；没有证据将其归为这轮手机的Root长按回弹。
保留失败、真实计数断言和一秒expiry，继续重跑与核对，不延长时间
或删断言来使结果变绿；当前尚未完成full重跑。

矩阵finally已恢复原su SHA b2b5c4dc...、SELinux Enforcing、原完整
preferences；验证临时/system/xbin bind不存在。临时broker/文件已移除。
不得把临时模拟器Root broker视作SukiSU授权或物理Vivo交付证据。

#### R56 同 hash 重跑仍失败

保留原运行，在独立 `emulator-root-rerun` 目录重跑 modes 和 Root full，
仍为31/31与35/36，同一迟到写入断言失败。JSON为
`emulator-5594-privileged-hold-1793972241477073000.json`，没有替换原失败。
实际三批native write offset为155/401/649 ms，最后实际5/15；
最终Correction在200672.543创建，一秒期限约200673.543。
生产main直到200673.462才记录6→5，200673.550开始quiet后补写，
Root拒绝过期请求，最终仍5并显示UNCONFIRMED。
独立写入时刻和main观测时刻明显不同，后续须检查读数变化时间、
main排队及补写调度；不能只按fixture计划550 ms推断剩余时间足够。
两次失败表明该用例当前仍需处理，不继续重复运行到偶然通过。
重跑finally已核对su、SELinux、原preferences和bind恢复。

下一轮Shizuku64物理采集直接执行非executable的有界wrapper，退出126，
没有ready也没有物理窗口；原stderr保存在Git外
`shizuku64-nonlimit2-recorder.log`。改用显式`sh`解释器启动，
未改生产应用或系统权限。此为工具启动错误，不能算按键测试失败。

### R57：Shizuku 64 阶亮屏非触底长按，76→15 稳定约 50 秒

同R51诊断主/helper字节、实际蓝牙音乐、原相机快捷与无障碍组件，
Shizuku write/raw ready、devices=2，64阶，基线76/150，亮屏未锁。
这次PMIC DOWN=168834.259328、UP=168834.873658，实际0.614330秒，
落在独立reader168770.465568开始的有效窗口内。
生产raw收到完整DOWN/UP，native依次76→66→56→46→36→26→16；
长按中没有覆盖为仅raw六步对应的较高值。
168835.212 quiet后读取16，沿原点76补记20步，总26步得到15，
Shizuku写入15；168835.545确认。

独立最终readback15/150，末尾30次状态样本均15；310次样本全部
playback active=true。reader正常结束168884.522049，
覆盖UP后49.648秒，status=0、errors为空、complete存在。
收尾pending none、wake lock false、overlay detached，
accessibility connected=true，屏幕仍亮且未锁。
完整归档在Git外同目录 `shizuku64-nonlimit-complete.tar`，
原R52空窗口及R53触底记录仍保留。
这轮补上当前Shizuku/64阶/亮屏/实际蓝牙播放的非触底物理长按，
没有观测回升；息屏64阶、其他路由和重启仍分别待验。

### R58：Shizuku 64 阶真正息屏长按，76→22 稳定约 62 秒

同R57主/helper字节、实际蓝牙音乐与原相机快捷，Shizuku write/raw ready、
devices=2，64阶、基线76/150。PMIC POWER UP=168978.881395，
音量DOWN=168980.932974、UP=168982.384223，实际1.451249秒，
落在reader168949.619134开始的有效窗口。生产DOWN/UP均
noninteractive=true；独立power snapshot为Asleep、power_button，
不能用操作指引的“约1秒”替代实际时长。

长按按Android400/50 ms推进，最后累计23步，
76 + round(-23×150/64)=22。UP前已到24，松开结算22；
168982.726确认。独立最终readback22/150，末尾30次样本均22。
254次状态样本全部playback active=true、overlay attached=false；
reader正常结束169044.353747，覆盖UP后61.970秒，status=0、
errors为空、complete存在。收尾pending none、wake lock false，
screen interactive=false、keyguard locked=true、accessibility connected=true。
完整归档在Git外同目录 `shizuku-off64-nonlimit-complete.tar`。
这轮补上当前Shizuku/64阶/真正息屏/实际蓝牙播放的非触底物理长按，
没有观测回升；扬声器及其他路由、实际重启仍分别待验。

### R59：助手 64 阶扬声器息屏五连按，30→18 计数正确

同R51主/helper字节，暂时关闭已保存原状态的Bluetooth，
独立audio确认speaker、mBluetoothName=null；原扬声器音量bank为0，
测试使用30/150，结束须恢复该bank和原蓝牙连接。真实用户播放器
保持播放，助手write/input ready、registered/active=true、prewarm=false，
Shizuku disabled。相机快捷设置保持原值。

PMIC POWER UP=169569.261941，五组音量DOWN/UP：
169569.747334/169569.906910、169570.141584/169570.293389、
169570.505080/169570.663802、169570.864437/169571.028207、
169571.200786/169571.352476，全部位于有效独立窗口。
helper收到五组完整松开，生产累计target=18，
与30 + round(-5×150/64)一致。169571.734请求、
169571.762确认18。原生过程中30→20→10→0，最后助手结算18；
本条证明五次计数与最终读数，不宣称过程中没有听得到的粗调。

889次状态样本全部playback active=true，末尾30次均18；
reader正常结束169655.965046，覆盖最后UP后84.613秒，
status=0、errors为空、complete存在。收尾pending none、
wake lock false、accessibility connected=true，实际仍息屏锁定。
用户没有在该窗口交付第二组物理POWER；随后由ADB唤醒并确认
interactive=true、keyguard locked=true、readback仍18。
该唤醒不能算用户在最后UP后立即物理开屏的验证。
完整归档在Git外同目录 `helper-speaker64-complete.tar`。
重启及扬声器特权长按仍分别待验。

### R60：修正长按测试对 UNCONFIRMED 的误判

R56两轮真实readback=5、panel state=UNCONFIRMED，
但旧 `PrivilegedHoldInstrumentation.staggeredNativeWrites` 中
`settling leaves no unconfirmed correction` 仍报pass。
原断言仅检查diagnostics是否包含日志文字 `key write unconfirmed`；
Root过期返回-1的 `handleBackendResult → markUnconfirmed` 没有该日志，
所以它漏掉了实际失败状态。此为测试工具误判，不能把这一项旧pass
当作已确认音量状态的证据。

将该断言改为同时检查实际 `pending correction=none` 和
`correction state` 未处于UNCONFIRMED。保留最终真实计数断言、
原一秒expiry、原失败及全部旧结果。新host尚待编译和重跑，
主/helper仍为R51相同字节；不会继承旧host的通过计数。

新host SHA `1befd435ba48be3f6d4def48ad1f50d8be41c80488eea9c77b38b1c739eff299`
编译通过。首次emulator运行尚安装旧host6c19a2d5...，被runner的APK
哈希校验拒绝，modes/full均0项执行；不能记作生产Root断言失败。
原结果保存在 `emulator-root-state-assertion`，finally恢复已核对。
显式更新disposable emulator host后在新目录继续重跑，手机未安装host。

匹配新host的 `emulator-root-state-assertion-rerun` 已完成：
modes31/31、Root full7阶36/36，主/helper仍为R51相同hash。
实际三批native write offset156/355/604 ms；最后5，
202008.648补写仍在202007.694创建的原一秒期限内，
真实Root写回8、最终state=CONFIRMED、pending none，两个settling断言
均通过。新JSON为 `emulator-5594-privileged-hold-1793973576037956000.json`。
finally已核对原su、SELinux、preferences和bind恢复。
本轮证明匹配新断言的成功运行；R56两轮超时UNCONFIRMED及原误判
仍保留，不把本次成功写成已修改一秒期限或消除任意排队延迟。

### R61：当前助手真实重启后，首次解锁前监听自行恢复

保持R51主/helper字节、助手64阶、speaker18/150、Bluetooth暂时关闭，
原用户播放器正在播放。先保存原settings、preferences、boot ID和
运行状态，再请求真实ADB reboot；已观察到kernel boot ID改变，
sys.boot_completed=1。没有在启动后手动启动主应用、助手或Shizuku
再采集初始状态。

最早ADB恢复时Android framework尚未注册user/activity/audio等service，
多个dumpsys即使exit=0也在stderr输出Can't find service；空输出及
状态均保留在first-untouched，不能把它记为已读到主应用启动失败。
首次boot_complete snapshot在RUNNING_LOCKED时，keeper已自行启动，
64阶、18/150、accessibility connected=true，helper仍binding。
后续保持锁定、不采取恢复动作的snapshot显示helper ready、registered、
active均true、prewarm=false，Shizuku disabled。主服务记录
LOCKED_BOOT_COMPLETED；这证明当前helper自动连接和基线就绪，
物理按键仍须另测。

经已授权的首次credential unlock后，keeper PID保持4422，记录
BOOT_COMPLETED，helper仍ready/registered/active，steps=64和volume=18。
USB stay-awake临时2已恢复0，screen timeout600000，
原无障碍component list/master=1保持。重启使实际媒体停止，
后续恢复原用户播放器并单独记录播放和物理按键；不能宣称媒体自行恢复。
原始证据保存在Git外同目录 `helper-reboot-*`。其他所选backend的
实际重启、扬声器特权长按仍独立待验。

### R62：真实重启后助手息屏五连按，开屏仍18/150

沿用R61重启后自行启动的keeper PID4422、64阶、speaker，
从30/150开始；原用户播放器已在首次解锁后恢复，独立AudioTrack
仍由原播放器UID持有。没有手动重启keeper/helper或启动Shizuku。
新reader410.543710 ready，PMIC POWER UP=685.481124，五组音量
DOWN/UP为686.115672/686.297050、686.453759/686.599432、
686.755069/686.896743、687.070393/687.224912、
687.373422/687.512177。helper收到五组完整松开，
687.931请求target18、687.967确认，累计舍入与30为原点一致。

第二组PMIC POWER DOWN=688.171885、UP=688.325320，
DOWN距最后音量UP0.659708秒，发生在确认18之后。
样本中有九次实际interactive=true、readback=18，随后因锁屏timeout
再次息屏。它证明重启后五次计数、物理开屏后的真实读数；
不能算仍pending时立即物理开屏的保护验证。
原生过程仍30→20→10→0，助手最后结算18，保留该粗调事实。

1012次状态样本全部playback active=true，末尾30次均18；
reader正常结束756.271735，覆盖最后音量UP后68.760秒，
status=0、errors为空、complete存在。最终readback18/150，
pending none、wake lock false、accessibility connected=true，
keeper PID仍4422。完整归档在Git外同目录
`helper-reboot-speaker64-complete.tar`。扬声器特权长按及其他所选
backend的实际重启仍分别待验。

同R60新host1befd435...、相同main/helper hash的Shizuku full7阶
36/36、success=true、errors为空，JSON为
`emulator-shizuku-state-assertion/emulator-5594-privileged-hold-1793973905486724000.json`。
与当前Root full成功运行单列；原R56失败仍保留。

### R63：Shizuku 64 阶扬声器息屏长按，30→7 非触底稳定

同R51主/helper字节，64阶、speaker、原用户播放器实际播放、
原相机快捷及完整无障碍组件保持。重启后的未恢复状态已由R61保存；
本轮准备时另发现官方Shizuku server已运行且UID=0，没有再次启动它。
该发现只证明准备时已有服务，不能据此宣称所选Shizuku重启后自动恢复。
通过原生页面选择Shizuku，writer/raw均ready、devices=2。
`cmd media_session --set 30`未改变实际18，保留该准备失败；
使用既有shell-UID `ShellVolumePreset`后真实readback为30。
模式页面动态布局导致首次旧坐标误选Root，重新读取控件位置后才选择
Shizuku，准备误选不能当作Shizuku物理测试。

独立reader1169.161551 ready。PMIC音量DOWN=1392.047424、
UP=1392.888645，实际0.841221秒；之前POWER UP=1389.744818。
生产DOWN/UP均noninteractive=true，完整边沿位于有效窗口。
native读数30→20；长按已观察到native progress，
应用未并行把较慢的绝对目标写回。UP结算10步，
30 + round(-10×150/64)=7，1393.217请求、1393.259返回7、
1393.557确认。独立样本30→20→7，没有记录到回升或触底。

ADB连接途中退出255，transport随后重新连接；原stderr/状态保留。
手机上的reader及采样仍存活，收到stop后正常cleanup，
finished status=0、complete存在、reader-errors为空。
731次真实readback及状态样本全部playback active=true，
最后30次均7，reader结束1428.525393，覆盖UP后35.636748秒。
末尾实际Asleep/locked、panel detached、pending none、wake lock false，
原播放器AudioTrack仍started。窗口中后来有开屏样本，值仍7；
不把这些样本当作仍pending时立即物理开屏。
独立app-logcat文件为空，应用receipt/write依据各次service dump的
events记录，不声称取得独立logcat流。

完整归档在Git外同目录 `shizuku-speaker64-complete.tar`，
连接中断保存在 `shizuku-speaker64-interruption-state.txt` 与原recorder log。
本轮补上Shizuku/64阶/speaker/实际播放的息屏非触底长按；
Root同路由、其他所选backend实际重启及pending物理开屏仍独立待验。

### R64：Root 64 阶扬声器息屏长按，30→2 非触底稳定

同R63主/helper字节，原播放器和相机快捷保持，Root ready、devices=2，
Shizuku disabled，speaker真实起点30/150。reader1583.584595 ready，
PMIC POWER UP=1749.676607，音量DOWN=1752.200758、
UP=1753.104861，实际0.904103秒。生产完整DOWN/UP均noninteractive=true。
native30→20，检测到native progress后保持系统长按路径，不发并行
绝对hold写入。UP补记12步，30 + round(-12×150/64)=2；
1753.448请求、1753.489实际返回2、1753.790确认。

独立568次readback/状态样本全部playback active=true，读数30→20→2，
最后30次均2，没有采到触底或回升。finished status=0、
reader-errors为空、complete存在，结束1791.351810，
覆盖UP后38.246949秒。收尾实际Asleep/locked、panel detached、
pending none、wake lock false，原播放器AudioTrack仍started。
完整归档在Git外同目录 `root-speaker64-complete.tar`。
独立logcat与service events按各自实际内容保留，不继承其他窗口的证据。

本轮完成Root/64阶/speaker/实际播放的息屏非触底长按。
恢复助手/50阶时原生NumberPicker点击未全部生效，重新核对实际值，
通过原生拖动恢复50，不能以已发出14次点击推断设置已恢复。
已先恢复原speaker bank=0，再打开Bluetooth；原耳机的原生连接操作
提示Connection failed，保持原配对，等待耳机重新可连接后再恢复其
76/150读数。此处尚未完成蓝牙连接恢复，不记录成已恢复。

报告前首次Gradle调用没有ANDROID_HOME，SDK location not found，
0项测试执行；使用commands.md保存的SDK/JDK路径独立重跑通过。
最终main67/helper3 release单测无失败，main/helper release lint/build
通过，字节仍匹配R51两个hash；原环境失败日志保留。
模拟器原无障碍list/master0、stay1、volume10/15、Helper50保持；
本轮引入的官方Shizuku UID0 server4355停止，permission flags从2恢复0，
原su SHA b2b5c4dc...和SELinux Enforcing未改变。

### R65：原蓝牙连接及设置恢复完成，原失败保留

保留R64的原生Connection failed。后续不取消配对、不重启播放器，
实际AudioService已确认恢复原耳机路由，原AudioTrack保持同一UID/PID、
state=started。随后才用shell-UID exact preset恢复76/150，
readback及最终独立audio snapshot均确认76，speaker bank保持0。
助手50阶、media-only、Shizuku disabled、helper input ready/registered/active，
原主keeper PID4422不变，原播放器继续实际播放。

最终原完整accessibility list/master=1及GKD连接保持，
stay_on_while_plugged_in=0、timeout600000，
smartkey_primary_switch（system）=0，
quick_start_camera/quick_start_camera_switch（secure）=1/2。
一次恢复检查误在system namespace读camera而得到null；
重新按原snapshot的secure namespace核对1/2，期间没有写相机设置。
不能以错误namespace的null宣称相机设置丢失或已关闭。

将已确认本轮新增的50个设备端录制目录、私有界面dump、wrapper、
exact preset工具先全部归档到Git外
`phone-introduced-files-before-cleanup.tar`，验证归档顶层路径完整后删除。
没有删除原有只读reader/script，手机host/UI tools保持未安装。
主/helper字节与当前report一致，原失败、空窗口及本轮所有complete档案仍保留。
模拟器settings、su/SELinux、flags0、停止server及无临时Root bind均已核对。

当前汇总在 `dist/verification-20261106-volume-keys.md`，
自动化原始汇总由`tests/write_report.py`生成，23个同main hash运行逐行保留，
包括原Root两轮35/36及准备失败，不能把报告生成的预期exit1当作文件缺失。
实际播放计数与列出的非触底长按已有物理证据；R41无播放快捷接管/采样遗漏、
真机pending时立即开屏、其他所选backend实际重启和完整其他route/时长/
方向矩阵继续独立列为未全部验证，不宣称全厂商、全部场景支持。
本轮没有提交或推送。

清理后返回原生主页面并息屏，首次即时snapshot已Asleep但keyguard尚未
locked，立即断言锁定会误报；随后独立settled snapshot确认locked=true、
helper active、76/150与实际播放保持，原即时状态及成功复核均保留。

## UF-084：Root 就绪状态颜色

2026-11-06 用户反馈：助手和 Shizuku 就绪状态显示绿色，Root 就绪文字却
使用普通深色；要求 Root 同样显示绿色。

源码原因：`0ed2984` 上的 `MainActivity.refreshShizukuStatus()` 在 Root
分支始终调用 `setTextColor(COLOR_SECONDARY)`，没有使用就绪条件。
`RootVolumeBridge.status()` 仅在 writer 和 input 都 ready 时返回
`root_ready`，`VolumeAdjustmentController.rootInputReady()` 返回相同输入
就绪条件。因此本轮在 Root 分支按 `controller.rootInputReady()` 选择
已有 `COLOR_ACCENT`（绿色）或 `COLOR_SECONDARY`，不通过翻译文字判断状态。
controller 不在、连接中、writer 未连接或 input 不可用时保持普通状态色。

首次验证的补丁基于 `0ed2984`。当时构建版本仍是主应用 1.5.3/code31：
SHA-256 `6af6e3db0291e79d0c95a4596f71393e68fb05828c1110963fa7bb93c5613255`；
本轮重新打包的助手 SHA-256
`940f8a955c0302cb94c00d2ab594445e3a946a6c24e1dd0ed2f08c7d0eaaac62`。
main release build、67/67 单测、main/helper release lint（0 errors）通过。
助手单测 task 为 UP-TO-DATE，未变化源码的既有 XML 为 3/3，
不记录为本轮重新执行。没有新增模拟器或真机安装/按键/UI 测试，
实际页面状态切换的颜色验证仍未执行；编译与单测不证明页面已实际显示绿色。

本次反馈接续在 [UF-084 对照表](feedback-coverage.md)，扩展已有
[PV-D07](test-plan.md) 检查三个模式的 ready 绿色、Root 未就绪回到普通色、
切模式/断线/重连及不同语言下的显示。现有 PV 编号总数仍为 137。

随后用户授权安装到 Vivo V2419A/API36。已确认新旧主 APK 证书一致，
通过当前安装器的风险 checkbox 和 Continue installing 完成覆盖安装，
`adb install -r` 返回 Success；设备端 APK 实际 SHA-256 与上述主 APK 相同。
本轮安装前手机已由用户改为 Shizuku/50 阶、121/150、idle，按这个新现场保留，
没有恢复成更早的助手/蓝牙播放测试状态，也没有切换到 Root。
安装后 Shizuku writer、raw reader、主无障碍重新连接，原完整 enabled list
和 bound services 保持；stay-awake 恢复0、timeout保持600000，音量仍121/150、
无 pending。安装器 UI dump 临时文件已清理，快照、原 APK 和安装核对结果
留在 Git 外。以上证明安装和恢复；Root ready 颜色的实际页面验证仍未执行。

用户随后要求提交、推送并发布新版本。1.5.4/code32 包含本修复、更新的反馈/
计划记录和版本信息；发布提交包含这些源码及实际分发APK。
本轮重新执行main67/helper3 release单测、build和lint，打包hash与证书核对见
[1.5.4发布包记录](../../../../dist/verification-1.5.4.md)。
先前1.5.3开发包的手机安装结果与1.5.4发布包构建分开；未追加Root页面颜色
实际验证、全量物理按键或其他固件通过结论。
