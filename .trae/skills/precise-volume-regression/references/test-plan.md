# 精准音量：迭代测试计划

计划版本：2026-11-06。适用于后续开发、修复、升级和发布前验证。
本文件规定如何测试，不登记某个版本已经通过。每轮结果使用
[执行记录模板](test-run-template.md)，历史反馈对应关系见
[历史反馈与用例对照](feedback-coverage.md)。

## 1. Agent 从这里开始

用户要求“按测试计划测试”时，默认执行本文件的**完整验证**。
用户只要求检查某个改动时，执行每轮必测项和对应加测项，同时列出尚未执行的完整用例。
本轮只有修改文档时，可以只检查文档、编号、链接和覆盖关系，不操作手机。

1. 读根目录 `AGENTS.md`、两个项目 Skills、本计划和与改动有关的历史故障。
2. 查看当前源码、工作区差异及本轮目标；核对计划中的功能是否新增、删改。
   新功能、新故障必须增加用例；已取消的功能保留编号，注明取消版本和理由。
3. 创建一份执行记录，登记计划版本、源码 commit、未提交差异、
   main/helper/host 的版本、签名和 SHA-256。默认所有用例为 `NOT_RUN`。
4. 列出可用设备和能力，展开第 4 节的组合，再开始执行。
   有多个设备时使用明确的 serial。缺少真机或 app 可用的 Root 时，
   继续完成独立可执行部分，把相应组合保留为 `BLOCKED`。
5. 顺序：构建与静态检查 → 隔离模拟器 → 真机安装和权限 →
   真机普通按键 → 锁屏/息屏 → 故障和生命周期 → 长时间运行 → 恢复 → 报告。
6. 每个用例按“前提、操作、预期、证据、恢复”完成，立即记结果。
   失败后先保存现场；修复后建立新 run，重跑该用例及关联用例。
7. 任意失败、未测组合、恢复异常都要进入最终报告。测试数量来自真实结果，
   不把 Gradle task 数、计划用例数或组合数当作已通过的断言数。
8. 只执行本轮授权的测试和恢复。测试授权不包含发布、提交、推送、
   清除个人手机数据或卸载以绕过签名错误。

新 clone 应能从本文件找到永久工具。历史 `/tmp` 路径仅是旧证据位置；
本轮原始采集、临时工具和凭据保存在仓库外。原始诊断先脱敏再写入可分享报告。

## 2. 测试对象和应保持的行为

| 对象 | 当前功能与必须检查的行为 |
|---|---|
| 主页 | 总开关；5–150 阶，默认 30；助手/Shizuku/Root 互斥；权限指南；高级调节范围；导出诊断。首次安装默认关闭修正、仅媒体、助手模式。 |
| 助手模式 | 解锁亮屏使用无障碍；Vivo/iQOO 可安装独立助手改善后台写入。助手 DUMP 是一次性可选按键授权；缺少它不应阻止亮屏基本调节。普通模式验证时 Shizuku 不参与。 |
| Shizuku 模式 | 实际 UID 2000 和 UID 0 的官方服务；授权、volume writer、raw reader、setup 状态分开。raw 已就绪时覆盖亮屏、锁屏和息屏；断线有界恢复。 |
| Root 模式 | 主包 `roro.stellar.volumesteps` 经 app 自己的 `su`/libsu 获得 UID 0 服务。助手授权、ADB root、测试 broker 各有独立证据。 |
| 兼容助手 | 包 `com.vivo.finevolumesteps.helper`、独立 UID、同证书、signature permission、固定 provider authority；无桌面入口；主 App 内安装/更新和打开权限。 |
| 输入与计算 | 一次实际按键只计一次；从实际原点累计舍入；反向、端点、pending、外部预设和路由变更正确；长按不回弹；过期事件和截图组合取消。 |
| 面板 | 三行文字、一条音量条；百分比、配置阶数、Android 实际读数分别正确；修正期间保留之前真实样本，失败显示实际结果；位置稳定；息屏隐藏。 |
| 状态与持久化 | 选中模式、授权、服务连接、写入能力、输入能力不能互相冒充。设置跨升级/进程恢复/重启保存；Direct Boot 和首次解锁分别测。 |
| 系统交互 | 真实播放器继续持有播放；其他无障碍服务完整保留；按键监听、截图、通知、厂商快捷和系统权限按实际策略处理。 |
| 语言与设备 | Android 9+；英文回退、简体、繁体港台澳、日文；Android 13+ 应用语言；Vivo 字体/主题变化；其他厂商逐设备验证。 |

当前 controller 接受公开 stream `3/2/4/5/1/0`，内部 alias 不接受。
默认只处理媒体；“所有音量”仍受实际 stream、按键投递和后端能力限制。
特权 raw 处理要求正常音频模式，通话由系统处理。测试全范围时不得把同一
媒体 raw 事件复制成多个 stream 的写入。

## 3. 每轮要测多少

| 运行类型 | 必须执行 | 报告方式 |
|---|---|---|
| 每轮行为迭代 | PV-B 全部；模拟器 ordinary/backend/frames/layout/screen-off/released-key-wake/helper-startup；特权路径可用时跑 hold full、native-hold、settling、modes；PV-I03、A02、M01–M06、V02–V07、K01–K04、D01–D06、S01–S05、R01–R03、H01–H09 的适用组合。真机三个可用模式、整除/不整除、三个屏幕状态至少各有新证据。 | 缺设备/能力照实列出，不能写本轮完整验证通过。语言、授权、启动改动同时追加下表。 |
| 用户要求完整验证；发布前；准备声称支持新固件/厂商 | 本计划 137 个用例；第 4 节全部适用组合；原 P01–P29；全部相关 R/N 故障保护；升级链和长时间运行。 | 每个用例和组合有独立状态。没有执行或能力缺失的组合保持可见。 |
| 定位故障后的重跑 | 原失败用例、同输入时序的旧版/新版对照、同组其他条件、所有受影响模块及每轮必测项。 | 原失败保留；列出重跑原因、代码/fixture 改动，不能重跑到成功后删除失败。 |
| 纯文档迭代 | PV-H02/H06 的文档部分：编号、链接、命令参数、反馈映射、当前功能一致性。 | 明确是计划检查，行为用例没有执行。 |

| 修改位置/功能 | 在每轮必测项之外加测 |
|---|---|
| controller/history/display、取整或第三行 | PV-V/K/D 全部；PV-R01–R05；不同 min/max；拒写/读失败/迟到写入；物理快按和长按。 |
| raw parser/ownership/helper sampling/计数 | PV-K/S 全部；PV-M05–M09；PV-X01–X04；PV-E01–E04；idle 与实际播放分开；内置键独立证据。 |
| Shizuku/Root/setup/recovery | PV-A/M/L 全部；UID 0/2000；有/无无障碍；writer/reader 分开；全量 hold 三种 scenario；重启缺服务现场。 |
| 安装、包名、manifest、签名、打包 | PV-B/I/X 全部；PV-A04/A07–A10；PV-L05–L12；权限指南；运行时身份和旧版升级链。 |
| 界面、通知、主题、翻译 | PV-D/U 全部；PV-A01–A03；PV-L07；真实语言切换后台通知；新增第三行仍跑 frame suite。 |
| stream/scope/Bluetooth/播放器 | PV-R 全部；PV-V08–V12；PV-K07/K11–K13；PV-S04/S09–S13；实际输出与数值分别记录。 |
| 保活、轮询、wake lock、启动 | PV-L/E/S 全部；后台/播放/idle；测试开始前失败状态；恢复后长期观察。 |
| tests/host、采集器、runner 或报告工具 | PV-H 全部；用同 host 对照旧/新 APK；原失败检测仍有效；恢复对照；工具不干扰实际播放与输入。 |

每轮记下选了什么和为何未选。用例 ID 不随迭代重新编号。
有用户限定的测试范围时照该范围执行，报告限定范围及未执行项。
用例覆盖不代表已有自动化实现；表中标“fixture”的异常需要现有受控工具或
在隔离设备新增最小复现。当前工具无法构造该条件时写 BLOCKED 和补测方法，
不能把源码检查或正常流程替代失败流程的实际执行。

## 4. 组合展开：一个用例可以有多行结果

| 维度 | 完整验证的取值 |
|---|---|
| 后端 | 助手主 App 单独、助手带 helper；Shizuku shell UID 2000/root UID 0；实际 app Root。helper DUMP 有/无，权限受限作为专项用例。 |
| 屏幕 | 亮屏已解锁、亮屏真实锁定、真正息屏；关屏立即输入与等待 2 秒输入分开；确认前/确认后开屏分开。 |
| 前台 | 主 App、桌面、其他 App；真实播放器前台/后台。 |
| 播放 | idle 无 active media；真实用户播放器持续播放；暂停/恢复。音频测试 fixture 仅是模拟器或专门输出实验条件。 |
| 路由 | 扬声器、实际蓝牙耳机；有线/USB 音频设备存在时追加；连接、断连、播放中切换。 |
| 配置阶数 | 5、7、30、50、64、100、150；真机主矩阵至少 50/64。若与实际跨度关系不能覆盖整除/不整除，追加合适阶数。 |
| 原生范围 | 实际设备提供的 min/max；UT 覆盖 min=0/1/5、跨度及 7/15/50/100/120/150/255 等。无法提供的范围记未测设备组合。 |
| 输入 | UT、synthetic callback/dump、ADB input、UHID、USB HID、内置物理键分别记录；音量上/下、短按、burst、长按、反向、power chord。 |
| 授权/服务 | 正常；拒绝；撤销；授权保留但 server 不在；writer ready/reader 不在；reader ready/writer 不在；无障碍开/关/已启用未绑定。 |
| 生命周期 | 冷启动、task removal、进程死亡、force-stop 后重开、升级、真实重启首次解锁前/后。 |
| 平台与外观 | API 28、32、33、当前可用较新 AOSP，以及目标 OEM 固件；窄屏/横屏/大字体、深浅主题、六种已有语言、未知语言回退。 |

**真机主矩阵**：三个模式 × 两个阶数 × 扬声器/蓝牙 × 三种屏幕 ×
idle/实际播放，共 72 个基本组合。每个可用组合先执行 PV-K01/K03/K04/V06；
长按、状态切换、权限失败在相应专项用例追加。助手的主矩阵使用已安装并
完成一次性授权的 helper；主 App 单独和缺 DUMP 另测 PV-A04/M02。
Shizuku 两个 UID 各有一份状态/连接/断线矩阵；声称某 UID 全屏行为已验证时，
其对应主矩阵也必须展开。其他厂商另建矩阵，不继承 Vivo 的结果。
PV-K04 只用于特权实时长按，助手组合执行 PV-K05 的完整 release 长按流程。

主 App/桌面/其他 App 至少在每个可用模式的解锁亮屏 idle/播放各走一遍。
更多阶数、音量范围、语言和故障条件通过专项矩阵追加，无需把所有维度机械
相乘，但下列关系不能用随机两两组合替代：

- 模式 × 三种屏幕 × idle/真实播放 × 两条路由 × 整除/不整除。
- pending × 相反方向/关闭/切换模式/切换范围/切换路由/开屏。
- 授权状态 × 实际 writer/reader × Shizuku UID。
- 长按原生速度小于/等于/大于配置速度 × 两方向 × 按住/松开后。
- 真重启 × 所选模式 × 首次解锁前/后 × 真实按键。

能力缺失与软件失败分开。例如无耳机为 `BLOCKED`，连接已有耳机失败为
`FAIL`；厂商不提供某权限入口仍要测引导及降级，不能整项写 `N/A`。
`N/A` 只用于已证实不适用的功能并写理由，例如非 Vivo 的内置助手入口。
已知失败保持 `FAIL` 并链接故障，不能因“历史上也这样”改成通过。

## 5. 共用执行流程

### C0：建立现场与恢复表

在安装、开主页、重启服务或恢复授权之前，记录：
设备 model/manufacturer/API/fingerprint/Android user/boot ID；main/helper/host
版本、证书、安装 APK hash；总开关、steps、scope、mode/旧模式字段；
各路由实际 min/max/current；Shizuku 实际 UID/grant/绑定；主包 Root grant；
无障碍完整 enabled list/master/bound services；overlay/notification/AppOps/
后台权限/DUMP/关联启动；亮屏与 keyguard、timeout/stay-awake；
真实播放器 UID/PID/active playback；Bluetooth 实际 endpoint；厂商快捷
开关及相机选项的真实 namespace；已有测试工具和后台进程。

只有记录，没有采样能力的字段标记 `UNKNOWN`。不猜测 null 的含义，
不把“component 在 enabled list”当作已经 bound。

只读起步命令如下，输出转存到本轮仓库外目录。所有 shell 调用明确设备：

```sh
adb devices -l
adb -s "$PVS_SERIAL" shell getprop ro.build.fingerprint
adb -s "$PVS_SERIAL" shell cat /proc/sys/kernel/random/boot_id
adb -s "$PVS_SERIAL" shell dumpsys package roro.stellar.volumesteps
adb -s "$PVS_SERIAL" shell dumpsys activity service roro.stellar.volumesteps/.VolumeKeeperService
adb -s "$PVS_SERIAL" shell cmd media_session volume --stream 3 --get
adb -s "$PVS_SERIAL" shell dumpsys audio
adb -s "$PVS_SERIAL" shell dumpsys power
adb -s "$PVS_SERIAL" shell dumpsys accessibility
adb -s "$PVS_SERIAL" shell settings get secure enabled_accessibility_services
adb -s "$PVS_SERIAL" shell settings get secure accessibility_enabled
adb -s "$PVS_SERIAL" shell settings get system screen_off_timeout
adb -s "$PVS_SERIAL" shell settings get global stay_on_while_plugged_in
```

### C1：独立计算预期

先通过 Android 读数确认 min、max、current，再计算预期，不能从写入 target
或被测 `VolumeStepHistory` 的输出倒推通过标准。正常未触界、未 rebasing 的
路径，以原点 `o`、有符号累计次数 `k`、跨度 `r=max-min`、阶数 `s` 计算：
`o + Math.round(k*r/s)`。Java `Math.round` 在负半数处等价于 `floor(x+0.5)`，
Python 内置 `round()` 的银行家舍入不适用。达到边界后按已走过的可达路径
返回，不让端点重复推进隐藏 cursor。

顶部百分比为 `Math.round(current*100.0/max)`；
第二行为 `Math.round((current-min)*s/(max-min)) / s`；
第三行为当前 Android `current / max`。无有效范围、读取失败和受保护 stream
要进入明确异常用例。

固定历史锚点：

| 范围/阶数/起点 | 操作 | 每次预期实际值 |
|---|---|---|
| 0–150 / 50 / 21 | `+++---` | 24、27、30、27、24、21；百分比 16、18、20、18、16、14 |
| 0–150 / 50 / 22 | `+++---` | 25、28、31、28、25、22 |
| 0–150 / 64 / 74 | `+++---+++` | 76、79、81、79、76、74、76、79、81 |
| 0–150 / 50 / 121 | 息屏 `-----`，开屏后 `-` | 118、115、112、109、106，最后 103；81%→69% |
| 0–150 / 64 / 76 | 息屏 `-----` | 74、71、69、67、64 |
| 0–150 / 64 / 30 | 息屏 `-----` | 28、25、23、21、18 |

这些起点不是要求提高个人手机实际播放音量。高音量历史锚点放到可控制设备/
安全路由验证；真实播放选安全低起点，独立计算同类预期。

### C2：按键、readback 和面板

1. 确认所选后端 writer/reader 与当前屏幕、scope；设置安全非触界起点。
2. 采集器确实 ready 且窗口未过期后，再给物理操作指引或发送测试输入。
3. 每次记录 DOWN/UP、原生即时读数、App receipt/main delivery、目标请求、
   实际 write/result、后续 readback、panel 状态和时间。
4. 短按 settled 结果至少在最后 UP 后覆盖 2 秒并有连续稳定样本。
   间歇失败、连按和非触底长按再观察至少 30 秒，期间不得产生新输入。
   这些是证据观察窗口，不能延长生产写入期限。
5. 长按分别检查按住期间的最小/最大/变化方向与 UP 后稳定值；
   最终 target 正确不能遮盖按住期间回弹。
6. 三行逐帧检查采用实际 pre-draw/readback 或高帧率视频。
   静态截图只证明布局。App 面板与 OEM 原生面板分别描述。
7. 每次开屏、解锁、界面检查和 burst/hold 前后确认真实播放器 UID、
   started 状态、route，以及 main/player PID；界面存在不能证明仍播放。

面板/授权/服务连接和资源开销，候选版与上一稳定版用相同设备、权限、route、
步骤及采集器对照。面板 UP→first visible 每条件至少记录 20 次，列 p50/p95/
最大值；不存在原生/写入等待理由却在 2 秒内没有出现面板，记 FAIL。
前台修正慢半拍、秒级迟到和帧丢失必须写实际时间，不能用“感觉正常”通过。
setup/reconnect 采用工具既有有界超时，连接前提不满足与超时分别记录。
资源观察先预热 5 分钟，再每分钟采样至少 30 分钟；比较同条件旧版的 CPU、
RSS、线程/连接数及趋势。连续增长、新增 crash/ANR、重复轮询或 listener
泄漏为 FAIL。若缺少旧版或明确性能标准，绝对开销照实记录，不写改善结论。

若无权限读取内置设备的独立边沿，不为录制临时启动 Shizuku 来修复普通模式。
记录采集受限；仍可按键观察，但不能写成具有完整 PMIC trace 的结论。

### C3：真实重启

保存 reboot 前现场；重启后先记录新 boot ID、framework 是否就绪、
首次解锁前的设置/keeper/helper/writer/reader/授权状态，再进行恢复。
Shizuku server 未运行时先保留缺失现场。之后才按本轮要求启动外部服务，
分别记录自动启动、手动恢复、按键行为。主 App、助手、server 进程重启
和 framework restart 都不能代替设备 reboot。

### C4：失败与恢复

故障现场尚在时保存原始输出、实际状态、APK/fixture hash、时间轴及步骤。
恢复时逐字段比较 C0，先切回原 route 再恢复对应音量 bank，确认原蓝牙
endpoint 真正连接。其他无障碍服务需同时保持 enabled 与 bound。
停止只由本轮引入的 host/player/probe/HID/reader/root broker/forward；
恢复此前已有的工具和服务。检查无 pending 写、面板、wake lock、
后台 fixture；核对设置 picker 的实际值。重新采样实际 readback。

用户在中断期间改过设置时，使用恢复前的新快照，只处理有证据的测试残留。
恢复失败也按 `FAIL` 记录；已安装新测试版本与测试引入的权限变化分别注明。
保留新版本安装还是回退旧版按本轮约定，不能把“恢复”理解为自动卸载回退。

## 6. 用例目录

每行是一个稳定用例。表中“环境”指定执行位置；前提包含 C0，
音量类默认使用 C1/C2，重启使用 C3，所有变更用 C4 收尾。
表里的操作与预期均要写入本轮记录，不能只写“走了流程”。

### PV-B：构建、包和已有测试（6）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-B01 | 本机：按第 7 节从当前源码构建 main/helper/host，登记 SDK/JDK、签名与 hashes。 | 同一源码的实际 APK；嵌入 helper 的 bytes 与本轮 helper 相同；版本不能代替 hash。构建失败保留 stderr。 |
| PV-B02 | 本机：运行 main/helper release 单测，读取所有 XML。 | 无失败/error；逐模块记真实 tests/failures/errors/skipped；历史 35/67 等计数不作为本轮预期。 |
| PV-B03 | 本机：main/helper release lint；核对新增权限、manifest 和资源 warning。 | 无 Error/Fatal；原有 warning 与新增 warning 分开，新增问题记录处置。 |
| PV-B04 | 各目标 API 的隔离模拟器：安装、冷启动、开启、后台按键；API 28/32/33/新 API 分开。 | 无 crash/ANR/FGS 启动超时；API 专属权限合理；缺 API 设备不继承其他版本通过。 |
| PV-B05 | 隔离模拟器：执行第 7 节所有适用 suite 与 e2e 可选 phases。 | 每个真实断言、instrumentation final code、restore_error、进程状态均检查；setup 失败不能算零断言通过。 |
| PV-B06 | 本机与安装后：检查 main/helper package、provider、permission、服务组件、签名以及 release 不含调试 host/凭据。 | 运行身份未意外改变；同签名升级可用；debug 工具未成为用户必装应用。 |

### PV-I：首次安装和升级（9）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-I01 | 隔离模拟器：全新安装/清数据后首次打开，再关闭重开。 | 首次权限指南出现；默认 OFF/30/助手/仅媒体；关闭指南后主页可用，永久入口可再次打开。 |
| PV-I02 | 模拟器：分别拒绝权限、取消指南、返回主页，之后补授。 | 不崩溃、不误报 ready；按钮仍可操作；补授后读取真实状态，无须清数据。 |
| PV-I03 | 模拟器和真机：同签名覆盖升级主 App；设置非默认阶数、scope/mode，并保留其他无障碍服务。 | 保存设置、组件身份和其他服务；服务重新连接；旧进程状态不冒充新版已运行。 |
| PV-I04 | 支持 helper 的设备：主页安装/更新内置助手；首次安装来源拒绝→允许→安装。 | 跳转主 App 的安装来源；固定只读 APK URI 可被当前安装器读；安装完成后 helper hash 正确、无桌面入口。 |
| PV-I05 | 支持 helper 的设备：分别取消安装、安装器退出、风险提示遮挡、安装超时。 | 明确未完成；保留错误和旧 helper 状态；重试入口可用，无“已安装”假状态。 |
| PV-I06 | Vivo：已授权的 ADB 更新，读取实时 checkbox/确认按钮；完成后关闭安装来源许可。 | 自动处理当前风险确认并获得 Success；许可关闭不影响已安装助手；恢复 timeout/stay-awake。 |
| PV-I07 | 隔离设备：分别测试主 App 新/助手旧、助手新/主 App 旧，以及先后更新顺序。 | 兼容或清楚提示需更新；无 Binder 协议 crash/无错误 ready；DUMP/设置保留情况有 readback。 |
| PV-I08 | 隔离设备：不同签名 APK 覆盖安装、伪 helper；重复安装同版本。 | 不通过卸载绕过签名；拒绝不可信 helper；正常同签名重复安装不丢设置。 |
| PV-I09 | 隔离设备：卸载/重装助手，与仅杀进程/覆盖更新分别对比。 | 卸载后的 DUMP 需重新授权；更新/进程恢复的授权保留独立验证；主 App 仍可用或显示准确缺失信息。 |

### PV-A：权限、设置入口和授权（14）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-A01 | 模拟器/真机：指南逐按钮进入通知、overlay、电池、后台/厂商页面后返回。 | 进入所指对象和正确页面；没有入口时有可执行回退；权限仍被拒绝时状态不伪造。 |
| PV-A02 | Vivo 两个入口：助手“开启无障碍”和权限指南按钮；公共 Android 路径另测。 | 定位精准音量服务详情或把对应行带入视野；记录实际目的页，保留原服务开关。 |
| PV-A03 | 受限设置/无障碍断开/组件 enabled 未 bound：按指引恢复。 | 区分 enabled 与 connected；只处理本服务；其他 enabled/bound 服务不变。 |
| PV-A04 | helper 已装但 DUMP 撤销、无 helper：助手亮屏 `+-`，再锁屏/息屏检查状态。 | 亮屏基本调节不由 DUMP 阻断；缺 input 单独显示，不能将 writer ready 当作息屏 ready。 |
| PV-A05 | 模拟器/真机：复制 ADB 命令、实际给当前用户 helper 授 DUMP、返回刷新。 | 剪贴板完整固定命令；实际 permission readback；reader 检查/注册/基线就绪后状态才 ready。 |
| PV-A06 | 模拟器/真机：DUMP 缺失，以 UID 2000/0 Shizuku 分别授权；重复点击；server 不在/拒绝另测。 | 已授权不再要求打开 Shizuku；固定 helper grant 成功；保持助手模式；失败提供真实恢复入口，临时 grant 服务释放。 |
| PV-A07 | Vivo：主 self-start 入口有/无，助手关联启动开/关，标准后台授权前后。 | 分开记录 OEM 启动、标准 permission、实际后台写入和进程启动；缺入口不能显示全部就绪。 |
| PV-A08 | 两个 Shizuku UID：总开关 OFF/ON，授权/拒绝/撤销/无 server，离开返回主页。 | 授权状态独立于 keeper；允许时显示已授权；volume/input/setup 实际状态各自准确。 |
| PV-A09 | SukiSU/KernelSU 或真实 Root：只授权 helper→主包无 grant；再授权准确主包并 setup。 | 前者真实 unavailable；后者 app UID 发起 su 连接 UID 0，不能误报无 Root；授权过程记录 app UID。 |
| PV-A10 | 无 Root/拒绝/撤销/慢 su：点击 Root setup 并取消、返回、重试。 | 有界结束、错误可理解、无主线程 ANR；不改选中模式冒充另一个后端成功。 |
| PV-A11 | Shizuku/Root 完成 setup，已有其他服务：移除本 App 无障碍后等待恢复并按键。 | 自动合并并 reconnect 本服务；其他服务未移除/重复；raw 仍只计一次；助手模式不自动开启无障碍。 |
| PV-A12 | 隔离设备：逐项撤通知、overlay、电池豁免，权限页面返回。 | setup 报告与实际权限一致；能自动设置的结果核对 readback，手动项明确；拒 overlay 不伪造可见面板。 |
| PV-A13 | 所有模式：OFF 状态多次选模式、进入 setup，返回主页；ON/OFF 快切。 | 开关、keeper、授权各自准确；OFF 不应因旧回调持续修正，ON 后按最新配置运行。 |
| PV-A14 | 指南和 setup 在中途 server 退出、屏幕旋转或 Activity 重建；重新打开。 | 不重复弹授权/重复 grant；不丢按钮；报告与最新状态一致，错误可再次操作。 |

### PV-M：后端选择和所有权（9）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-M01 | 主页逐选助手→Shizuku→Root→助手，离开再回来。 | 三个互斥；只出现当前模式设置；持久化与 legacy 字段一致，状态文字易读。 |
| PV-M02 | 助手：Shizuku 不参与，主单独/带 helper 在 App/桌面/其他 App，idle/播放下按键。 | available 后端按配置修正；拒写显示真实结果；不启动额外 Activity/dummy audio 保活。 |
| PV-M03 | Shizuku：helper 有/无，两个 UID，在三屏幕按短键；停 server 后切助手。 | 显式选中且 ready 才拥有输入/写入；旧 Shizuku 回调取消；助手能独立恢复，不要求保留 Shizuku grant。 |
| PV-M04 | Root：helper 有/无、DUMP 有/无；App/桌面/锁屏/息屏。 | 实际 Root writer/reader 工作；不依赖 helper grant 或 Shizuku；各状态与实际一致。 |
| PV-M05 | 特权 raw ready、无障碍 ready，两路同时能收到事件；短按/连按/长按。 | 每个 press 只累计一次；无 duplicate correction；helper readiness 不误吞外部 accessibility 输入。 |
| PV-M06 | 面板可见、写入 pending、键仍按住分别切换三个模式和关闭。 | 旧 generation 不再写；新模式按最新原点；无窗口跳变、重复 reader 或迟到授权提示。 |
| PV-M07 | 隔离设备：writer ready/reader absent 与反向条件；之后恢复。 | 区分输入能力、写能力；实际 fallback/失败有记录；不能仅靠 ready 字段宣称按键有效。 |
| PV-M08 | 特权无障碍关闭：解锁亮屏、锁屏、息屏短按/反向/长按；恢复 overlay 服务。 | raw 按键和写入保持独立；锁屏面板单独验证；恢复服务后不重复接管。 |
| PV-M09 | 隔离设备：旧后端 callback 晚于切换/关闭/新 gesture，reader 重连。 | 过期 generation/callback 被拒绝；新事件按选中模式执行；错误不改变其他模式设置。 |

### PV-V：数值和调节范围（12）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-V01 | UT＋可用设备：5/7/30/50/64/100/150；非零 min、不同 max；设置上下限。 | 所有计算使用实际范围；picker 5–150 不绕回；超界输入安全处理；无硬编码 150/50。 |
| PV-V02 | C1 的 21/150、50 阶 `+++---`，逐次 readback。 | 完整 3 单位间距，第一步 24/16%；不会插入 22/15% 旧历史停点。 |
| PV-V03 | C1 的 off-grid 22/150、50 阶 `+++---`。 | 25/28/31，再原路回 22；任意原点不被拉到全局网格。 |
| PV-V04 | C1 的 74/150、64 阶 `+++---+++`；换负方向。 | 累计取整，同位置同增量，Java rounding；多次访问相同路径一致。 |
| PV-V05 | 安全起点，确认后 `+-`/`-+` 各 20 轮。 | 每轮回精确 origin，无累计漂移；保持每次即时和稳定读数。 |
| PV-V06 | 隔离 fixture＋真机：pending 时立即反向；rapid `++--`/`--++`。 | 合并次数正确，最终原点；迟到写入不覆盖最新计划；无副方向修正。 |
| PV-V07 | 安全隔离设备：临近 min/max，到界连按五次，再反向。 | clamp；端点重复无隐藏 cursor；原路回 last reachable point；两个方向都测。 |
| PV-V08 | App 修正后用系统 slider/播放器 preset 改成 off-grid，再按一次。 | 被动值不被改回；下一键从新实际值 rebasing；不沿旧 gesture/history 走。 |
| PV-V09 | 键前、pending、面板可见时分别更改 steps 与 scope。 | 最新设置生效；旧路径/旧范围取消；下一键正确原点；保存值不丢。 |
| PV-V10 | 范围 0–15 等，设置 100/150，多次按和反向。 | 不声称获得多于系统原生精度；重复值/有限可达点符合计划；返回原点与界限正确。 |
| PV-V11 | 媒体 only/所有范围，媒体/铃声/闹钟/通知/系统/通话 stream，按系统投递或 fixture。 | scope 内公开 stream 才修正；正常 alias 读数一致；不支持 stream/内部 alias 拒绝，其他 stream 不被污染。 |
| PV-V12 | 隔离 fixture：无效/不可读 min/max、read failure、负/超大 current、写入目标被系统拒绝。 | 安全结束不除零；第三行实际读数或读取失败；不拿目标代替结果、不无限重试。 |

### PV-K：输入、快按、长按和竞争（13）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-K01 | 每个主矩阵组合：上/下短按、`--++`，先面板隐藏再可见。 | 实际物理次数、App receipt、native 和 settled 一致；一次键只计算一次。 |
| PV-K02 | 主 App/桌面/其他 App，突然按一次，重复 20 组；idle/播放分开。 | 不偶发回默认步长；保留每次结果，复现失败不能只呈最后一次成功。 |
| PV-K03 | 每个主矩阵：3/5/10 次短连按，约 60/120/250 ms 节奏，快速相反 burst。 | 计数完整、顺序正确、无漂移；记录实际边沿间隔，无法按出节奏不伪造频率。 |
| PV-K04 | Shizuku/Root：实际 0.7/1.4/3 秒长按及反向，整除/不整除，三屏幕。 | 按住有进展，原生进行中无较慢绝对写拉回；UP 后不回升；非触界与触界分别记。 |
| PV-K05 | 助手：DOWN/UP、持续 hold、上一键 release 后下一键 DOWN 不放。 | 该键完整 release 门控；前键 UP 不能授权后键提前结算；统计实际 delivered repeats。 |
| PV-K06 | 隔离模拟器：native hold 速度小于/等于/大于配置速度，上下两方向；down/up-only consumed hold。 | native-driven 和 consumed hold 各自正确；已计数 repeat 不再累计，显式迟到 repeat 不让 count 回退。 |
| PV-K07 | 截图 power+volume、先 volume 再 power、先 power 再 volume、双音量 chord。 | 截图/系统快捷正常；匹配 chord 取消该音量修正，不能借 power 恢复错误键。 |
| PV-K08 | 隔离 fixture：stale DOWN/UP、UP-only、未配对 repeat、取消事件、初始已按住。 | stale/unmatched 不授权；raw 有近期证据的恢复与普通 callback 严格分开；初始 hold 不补猜 DOWN。 |
| PV-K09 | 隔离模拟器：readback 先于回调、回调先于 native 变化、原生粗调已经发生。 | 从前一真实样本取得正确 origin；不会 `6→16→19`；accessibility 只使用匹配的原生变化。 |
| PV-K10 | 隔离模拟器：release 后独立 150/350/550 ms native writes；补写之后再迟到写；expected-current 拒绝。 | 实际 readback quiet 再结算；一秒原期限与有界 retry；超时 UNCONFIRMED；早拒绝不提前丢观察。 |
| PV-K11 | pending/hold 时关闭、mode/scope/route 变、reader 丢失、音频 mode 变。 | 全部旧 retry/hold 取消；无旧模式迟到写；新 gesture 重新建立正确 origin。 |
| PV-K12 | 无物理键时 idle、mute/unmute、play/pause、路由切换、外部预设、250 ms poll。 | 常规被动变化不授权、不弹/延长 App 面板；Vivo 普通播放的窄 coarse fallback 按 PV-R09 独立标注。 |
| PV-K13 | 其他 App 消费 volume key、播放器手势调音量、相机键用途、通话输入。 | 保留其他监听/行为；没有匹配授权证据不修正；通话模式 raw 路径不抢系统音量。 |

### PV-D：面板、通知和诊断（9）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-D01 | 每个可用模式首次 key、后续 key；三个屏幕、idle/播放。 | 背景面板可见，锁屏在正确层；息屏没有面板；无反复 Toast、额外 Activity 或重复窗口。 |
| PV-D02 | 不同 min/max/steps/current，核对三行和通知/主页显示。 | C1 三个公式各自正确；`/100%`、配置阶数和 native current/max 区分；只有一条 bar。 |
| PV-D03 | 9→10、99→100、steps 150、三行长文案；记录 window/child bounds。 | 固定行位置/宽高，无 clipping、左右抖动或高度变化；长数字不触发另一布局。 |
| PV-D04 | pending、短 burst、反向、settling、拒写：录第一帧到消失。 | pending 保留前次真实值，确认或失败更新真实结果；无粗调中间帧、正在修正帧、反复重画/闪烁。 |
| PV-D05 | 第三行 enabled，delay/reject write，重新 attach 面板；逐帧与独立 native readback 比较。 | 第三行不显示 target；失败显示 actual；读失败明示；observer 每次 attach 都有效。 |
| PV-D06 | 锁屏亮着与解锁亮屏/息屏：记录 UP→write→attach→first visible 时间，再解锁。 | 不丢面板、不出现秒级无故延迟；有界等待/实际时序单列；可见面板解锁后不闪/重开。 |
| PV-D07 | 三模式 ready/未 ready 切换，特别是 Root writer/input 就绪、连接中、reader 丢失；深/浅主题、浅/深壁纸、状态栏图标、通知渠道、权限撤销恢复；ON/OFF。 | 三模式 ready 状态使用同一绿色；Root 只有 writer 和 input 都 ready 才绿色，连接中/未连接/按键不可用恢复普通状态色，不依赖翻译文字判断。白底/透明差异分开记录资源和 OEM 呈现；通知反映实际状态，当前档位信息完整。 |
| PV-D08 | 模拟器/真机：横竖屏、窄屏、放大字体/显示比例、刘海/insets；permission guide/setup。 | 按钮可到达、无遮挡/截断/崩溃；面板边距/三行不乱；设置保存。 |
| PV-D09 | 导出诊断：ready、不 ready、server 退出、刚失败后，打开分享后取消。 | 包含当前 mode/readiness/范围/事件与实际版本来源；导出不改音量/播放/设置；不自动向外发送。 |

### PV-S：锁屏、息屏、采样和厂商快捷（13）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-S01 | C0/C2：物理关屏后即时 `---`，再重复立即五次和十次；与等待 2 秒对照。 | 匹配所有 DOWN/UP 与最终次数；helper registered baseline 已就绪；普通与特权分开，漏一次记失败。 |
| PV-S02 | idle/实际播放、speaker/BT：持续基线下约 60–250 ms 短按；记录 dump 起止、Binder receipt/main delivery。 | 不因跨完整短按或主队列过期漏计；若漏计，保留 R41 类型失败，不放宽 freshness 或推测缺失键。 |
| PV-S03 | 息屏五次，开屏后一次；反向；整除/不整除；C1 历史或安全等效起点。 | 每次完整间距；81%→69% 条件按实际 native 计算；开屏不突然补错/丢失已完成 release。 |
| PV-S04 | 三模式亮着的真实 keyguard：`--++`、短 burst、hold/反向；真实播放器后台。 | 修正与面板各自真实有效；锁屏窗口有正确服务支撑；原生面板单列；后台播放不恢复粗步长。 |
| PV-S05 | fixture＋真机：最后 UP 后尚 pending 即立即物理 POWER 开屏、再解锁；确认后开屏另测。 | 已完整 release 仍结算；power chord 不被误认为完成；pending POWER 有实际时间证据，确认后 POWER 不能替代。 |
| PV-S06 | fixture：只有 UP、近期匹配 transition/raw DOWN、无证据 UP，分别跨 wake。 | 仅合法近期匹配恢复；不借屏幕切换给任意 UP 授权；helper 完整 release 与特权规则分开。 |
| PV-S07 | helper 注册前已 hold、注册中屏幕来回变化、prewarm false idle；listener unregister/death。 | 注册基线不猜初始按键；已注册跨亮/息屏保持基线；解除注册/死 listener 停采样，无 reader 泄漏。 |
| PV-S08 | 息屏 key/burst/hold 后，观察 display、panel、partial wake lock 至过期。 | 不点亮屏幕、不显示 App 面板；只保留有界 CPU wake lock；关闭/取消时释放。 |
| PV-S09 | 无键息屏：实际播放/暂停、系统预设、蓝牙 route 切换；wake 后查看。 | 常规被动事件不纠正、不唤屏；原值按系统策略保持；普通 coarse fallback 的例外独立记录。 |
| PV-S10 | Vivo：原快捷设置保留；App ON/OFF，idle/实际播放，重复短 volume-down 与 hold。 | 记录真实 torch/camera 触发者、实际 hold 时长、volume 轨迹；快捷冲突与 App 写入分开判断。 |
| PV-S11 | 只有针对已确认冲突的设置变更另获授权时，改变该快捷再复测，同步保存选项 namespace。 | 对照只改变该项；不把关闭相机当默认前提；恢复其他选项，用户要求保留的值保持。 |
| PV-S12 | 真正长时间息屏/系统 idle，再 first key/burst；USB stay-awake 关闭。 | 实际 noninteractive/idle 有证据，服务 readiness 与 delivered keys 一起检查；无点亮/错误预热播放。 |
| PV-S13 | B 站和实际音乐播放器：播放/暂停、后台/前台、蓝牙/扬声器，息屏立即及等 2 秒。 | 每个场景实际 playback ownership 连续；次数、最终数值、听感分别记录；不能用 dummy audio 替代真播放。 |

### PV-L：后台、断线、持久化和重启（12）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-L01 | 真机：主 task removal，后台 App/桌面后 `+-`；再回主页。 | stopWithTask=false 的 keeper 与实际按键工作独立确认；没有仅前台可用复发。 |
| PV-L02 | 隔离模拟器/允许的真机：主进程死亡、helper 进程死亡，保持真实媒体状态。 | 保存设置、provider/keeper 重连；回到实际 ready；中断期间不伪报成功；无旧 callback 重放。 |
| PV-L03 | 隔离设备：主 App force-stop，按键，再手动重开。 | force-stop 按系统语义暂停，重开恢复；不能要求或宣称 force-stop 后自动复活。 |
| PV-L04 | Shizuku 两 UID：延迟启动、授权恢复、冻结后恢复、漏通知/服务 stalled；保持主页不打开。 | 自动有界重连；停服状态保留；正常连接后停止主动检查，断线恢复检查；实际 server UID 校验。 |
| PV-L05 | C3：助手已一次性 DUMP，真重启；首次解锁前/后、idle/真实播放物理键。 | grant/steps/scope/mode 保留，keeper/helper/input 自行恢复；ready 与按键行为分别测；不能借 Shizuku 恢复。 |
| PV-L06 | C3：Shizuku 选中真重启，外部 server 未启动→按其支持方式启动后测试。 | 未启动提示真实缺失；自动连接与手动启动分别记；不把 server 重启写成 phone reboot 存活。 |
| PV-L07 | Vivo 改主题/字体，主前台/后台，三模式；不先手动修服务。 | 捕获无障碍是否被清除；特权 raw 仍工作并恢复面板服务；助手有准确状态/恢复指引；无假 ON。 |
| PV-L08 | C3：Root 已授主包，真重启，首次解锁前/后，三屏幕物理键。 | 设置保留；实际 app Root/writer/reader 自动恢复证据；若需解锁/手动 setup 分开报告。 |
| PV-L09 | 设置从旧 credential prefs 迁移 device-protected，升级后 reboot、首次解锁再 reboot。 | 正确迁移一次且持久提交；非默认值保留；没有重复迁移覆盖新值或 Direct Boot 崩溃。 |
| PV-L10 | 已启用/关闭总开关分别升级，MY_PACKAGE_REPLACED 后后台观察与按键。 | 依据保存 ON/OFF 恢复；没有偷偷开启修正/丢 notification，事件与实际状态对应。 |
| PV-L11 | 隔离设备升级链：原 legacy Shizuku 配置→三模式版本→当前候选；旧版助手一起更新。 | mode/steps/scope 迁移准确；组件/签名兼容；单次新装不能替代连续升级证据。 |
| PV-L12 | 重启/服务恢复时 framework 未就绪、绑定慢、主页立即打开；静置再按。 | 初始失败/stderr 保存；就绪后正确恢复；不提前 kill instrumentation 导致误报 Root/服务失败。 |

### PV-R：路由、音频模式和真实输出（9）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-R01 | speaker→BT→speaker，idle/播放，短键和反向；保持每个 bank 现场。 | 新 route 实际范围/原点重建；不继承旧路径；最终恢复真实 endpoint 与各 bank。 |
| PV-R02 | BT 连接、断开、重连失败后重试，写入 pending 时切换。 | 连接开关与 AudioService route 分开；失败照实记录；旧 route 请求不能改新 route 音量。 |
| PV-R03 | 实际 B 站/音乐：开始播放→暂停→恢复、前台→后台，三模式三屏幕。 | 实际 active 状态确认；不回默认大步长；开启播放前后的步长可对照。 |
| PV-R04 | BT absolute volume 当前开关；另有授权时分别 ON/OFF 对照同一音源、endpoint。 | App 数值和耳机响度分开；58/61/63 是否实际不同有测量/听感证据；不以百分比变化证明增益变化。 |
| PV-R05 | 低音量同一真实信号，亮屏 key/息屏 key/直接设 settled 目标对照，记录原生 transient 和输出。 | UI 正确之外检查实际输出是否恢复；无长期异常降幅；结果区分 readback、A2DP PCM、耳机模拟输出和主观听感。 |
| PV-R06 | 有线耳机/USB audio 可用：插拔、range/stream 变化、后台按键。 | 实际 route 能力确认；原点/range 重建；无法提供设备记 BLOCKED，不能由 speaker/BT 通过推导。 |
| PV-R07 | 安全隔离设备：来电/通话/VoIP、铃声/闹钟、DND/静音/取消静音。 | 系统通话不被 raw 抢占；scope、公开 stream/alias 按真实政策工作；不被动唤起面板。 |
| PV-R08 | 两播放器切换、媒体 session 切换、播放器手势/系统 slider、mute/unmute。 | 不纠正外部合法 preset；无假的播放所有者；之后 key 从实际值开始；真实音源不中断。 |
| PV-R09 | Vivo 普通播放 non-wireless、helper ready、正常 audio mode、真正息屏：无显式键的 1/2 个 coarse transition 与同形外部 preset 对照；其他条件另测。 | 窄 fallback 的当前行为和外部 preset 歧义分别记录；不能把形状匹配叫物理 delivery，不能扩大到 idle/BT/其他模式。 |

### PV-U：语言、字体和使用流程（8）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-U01 | 静态＋localization fixture：默认英文 key 完整、所有 format args/percent escapes。 | 新增文案各完整语言有译文；编号/类型一致；包名、命令、权限未翻译。 |
| PV-U02 | Android 13+ 系统“应用语言”：简体、港台澳、日文、英文逐个切换。 | 系统生成列表；主页/guide/setup/notification/panel 同步；无手工语言名单遗漏。 |
| PV-U03 | API 28–32 改系统语言，未知语言及 US/UK English；locale 带/不带 Hant 标签。 | 正常 Android 资源回退；zh-HK/MO/TW 推断正确；未知语言英文，设置/音量不改变。 |
| PV-U04 | App 在后台时切语言，观察已有通知与 channel，再开主页/面板。 | 当前通知/渠道文案即时更新；不是重开主页后才正确；真实读数仍正确。 |
| PV-U05 | 各语言 Root/Shizuku setup、授权失败、帮助/诊断分享；港澳台术语对照。 | setup 资源按当前主 App 语言渲染；异常原文保留技术含义；regional overrides 用词一致。 |
| PV-U06 | 每语言大字体、steps150、三行长数、横竖屏；通知/指南按钮。 | 无裁剪、行位跳动；字体主题后功能结合 PV-L07 复测，不能只看译文。 |
| PV-U07 | 从全新安装按 README 完成三个模式设置，再走问题恢复；主 helper 入口。 | 文档命令/版本/按钮与当前 App 一致；无多余用户必装 host；手动项清楚，第一轮用户能走通。 |
| PV-U08 | 仅打开主页/高级设置/guide、复制命令、取消分享/安装；辅助功能导航。 | 不意外改音量、播放、模式；按钮/文字可读可访问；用户取消后仍能正常操作。 |

### PV-E：运行开销、长时间运行（7）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-E01 | 三模式已连接：App 前台/后台、idle/播放，每条件静置至少 30 分钟；同设备对照旧稳定版。 | CPU/RSS/线程/reader/provider/session 数量无持续增长；错误不重复刷屏；按键后的恢复仍可用。 |
| PV-E02 | Shizuku writer/reader 均 ready 静置，断一个后恢复；总开关 OFF。 | ready 后停止两秒主动连接轮询，缺连接时恢复有界检查；OFF 后无 keeper 残留轮询；用实际 trace 验证。 |
| PV-E03 | helper registered idle awake/off 与 unregister；观察 dump 次数/时长/CPU。 | 保留基线的采样成本单独记录；无叠加 reader/无限重试；未注册后停止；不以猜测宣称省电。 |
| PV-E04 | 100 次安全 `+-`、10 组 burst、10 次非触界 hold/反向；交替模式/路由，再静置。 | 无数值漂移、卡死、ANR、内存/线程/窗口泄漏；错误有界；长按升音量在安全条件执行。 |
| PV-E05 | 真机至少 8 小时后台/息屏，保留用户真实使用；记录开始/结束状态和期间 crash/ANR。 | 服务/设置可解释、first key 工作；实际电量/CPU统计与条件保留；未跑足时长标 INCOMPLETE。 |
| PV-E06 | 隔离模拟器 CPU/主队列负载、Binder 慢、迟到 readback、多次拒写。 | 错误有界、主线程可响应；原一秒过期仍有效，UNCONFIRMED 明确；绝不加无限 retry 强行通过。 |
| PV-E07 | 重复打开关闭 Activity、guide/setup，配置重建后关闭总开关。 | listener/handler/Binder/wake lock/window 不泄漏，关闭稳定；重新打开不重复授权和重复计数。 |

### PV-X：调用权限、包身份和诊断安全（7）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-X01 | 隔离设备：未授权不同 UID/certificate 访问 helper provider，伪造 caller package。 | signature permission 加显式 UID/signature 验证拒绝；caller 字符串不代表 own UID；native 音量不变。 |
| PV-X02 | 隔离 fixture：允许的 caller 提交内部 stream、超界值、expired/错误 expected-current 请求。 | stream/限幅/期限/expected-current 保护有效；拒绝实际无写入；真实返回状态可核对。 |
| PV-X03 | 隔离设备：调用 helper grant/setup，尝试非固定 package/command 参数，重复请求。 | grant 仅固定 helper DUMP；setup 仅既有固定能力；无任意 shell 执行通道，临时 service 释放。 |
| PV-X04 | helper 同签名/不同签名、主包 Root grant、Shizuku UID2000/0；实际服务 Process UID。 | 实际身份符合后端；不同签名拒绝；测试 broker 证据不被写成 OEM Root 认证。 |
| PV-X05 | 隔离设备：installer URI 读/写/未知路径/无 grant，主 App 更新后再安装。 | 有临时读权限的安装器可读取固定 helper；写和任意路径拒绝；helper bytes/版本正确。 |
| PV-X06 | 本机＋隔离设备：release manifest、打包、backup/data extraction 与调试配置。 | 用户 APK 不含 test host/probe/私人记录；不意外开启备份、导出内部服务或泄漏签名材料。 |
| PV-X07 | 导出/报告/fixture：检查 unlock credential、第三方 session 信息、raw UI/log、临时脚本。 | 私有数据留仓库外；共享证据脱敏；保留必要固件/范围/trace结论，发布动作另按授权执行。 |

### PV-H：测试工具、证据和每轮收尾（9）

| ID | 环境与操作 | 预期和证据 |
|---|---|---|
| PV-H01 | 每轮开始：列设备、C0、installed hashes、actual app su/reader/player，工具需要什么能力先核对。 | 单一明确目标；前提缺失记 BLOCKED/ERROR；未执行断言不报通过，不误判 app 无 Root。 |
| PV-H02 | 每轮：137 用例目录＋第 4 节组合＋全部历史反馈映射＋P01–P29/R/N 的适用行核对。 | 每项有执行状态；不存在因为只汇报 P01–P23 漏项；新功能/故障有 ID；计划变更有版本记录。 |
| PV-H03 | 模拟器破坏性准备/AppOps/clear-data/e2e；真机使用只读 recorder、实际物理键流程。 | 工具位置正确；不把个人手机当隔离模拟器；不会为了采集启动假播放或恢复缺 Shizuku 的失败现场。 |
| PV-H04 | 采集 ready 后操作；窗口超时、终端中断、ADB255、USB拔出分别检查设备端进程/结果。 | 实际 event 落在有效窗口；先救原记录再考虑新输入；complete 和按键/行为成功分开；空窗口 INCOMPLETE。 |
| PV-H05 | 每次 UI 观察/解锁/fixture 后：actual playback/main/player PID/accessibility。 | 非 suppressing observer；真实播放器不中断；不因测试进程退出破坏证据，影响照实保存。 |
| PV-H06 | 检查所有结果 JSON、final code、restore_error、hash、脚本参数和单位统计；故意失败样本对照 parser。 | 假 pass、零断言、UNCONFIRMED、不同 hash/host 不混合；工具修改后仍能识别旧失败。 |
| PV-H07 | 新故障→incident 记录→同输入旧/新版→相关用例重跑。 | 原失败/撤回补丁/重跑分开；定位原因有实际证据，假设注明；记录提交、函数、版本并链接两 Skills。 |
| PV-H08 | 全部测试结束执行 C4；和 C0/中断后快照对照，读取最终 route/banks/services。 | 恢复完整；没有引入的 reader/HID/probe/host 残留；用户新设置保留；恢复失败单列。 |
| PV-H09 | 用模板整理本轮报告，再由覆盖矩阵逐行核对。 | 实际通过/失败/未测/不适用和恢复结果可追溯；旧版成功仅参考；说明测试能力与用户听感证据限制。 |

## 7. 现有工具怎么执行

详细前提和命令在 [commands.md](commands.md)，以下是入口索引。
先读对应工具 `--help` 与源码的使用限制；文档示例的 serial、目录、key
binary/dex 都要换为当前设备和本轮仓库外路径，不能照抄旧工具路径。

### 构建

JDK 21、Android `platforms;android-37.0`、build-tools 36.0.0；
优先当前机器已有配置。主 App 与助手使用可升级已分发 APK 的既有证书。

```sh
./gradlew -PvolumeStepsE2E=true \
  :assembleRelease :lintRelease :testReleaseUnitTest \
  :compatibility-helper:assembleRelease :compatibility-helper:lintRelease \
  :compatibility-helper:testReleaseUnitTest \
  :precise-volume-steps-test-host:assembleDebug \
  :precise-volume-steps-test-ui:assembleDebug
```

outputs 路径和安装指引见 commands。本轮 helper 单测 XML 在
`compatibility-helper/build/test-results/testReleaseUnitTest/`。
测试前后证书和 installed byte hash 必须实际核对。

### 隔离模拟器 suite

先安装 main/helper/host/ui，完成必要权限和 enable。Shizuku 与 Root 各自
准备实际服务；`adb root` 并不能证明普通 App 能调用 `su`。
模拟器 helper synthetic strict dump 不能证明 Vivo EventHub 注册。

```sh
python3 tests/run_instrumentation.py --serial "$PVS_EMULATOR_SERIAL" \
  --suite ordinary \
  --apk build/outputs/apk/release/precise-volume-steps-release.apk \
  --host-apk tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk \
  --output "$PVS_RAW_DIR"
```

| 入口 | 逐次执行的参数/场景 | 对应本计划 |
|---|---|---|
| `run_instrumentation.py` ordinary | 已满足 ordinary 权限；不能把 Shizuku correction 混进 ordinary 资格 | M02、K08–K13、V |
| backend | `--backend companion/shizuku/root --steps`；AOSP 5/7，目标 Vivo 50/64；companion 需 helper | M、V、K；输入仍 synthetic |
| frames/layout/screen-off | 各自 suite；frames/screen-off 限隔离 emulator；layout 单独运行 | D、S08、V12 |
| released-key-wake/helper-startup | release suite 分别 `--steps 5` 和 `10`；startup 无 steps 参数 | S01/S05–S07、K05 |
| privileged-hold | 每个实际可用 privileged backend、至少整除/不整除；不带 scenario 的 full、`--scenario native-hold`、`--scenario settling` 各自运行 | K04–K11、D04–D05；按住/UP后都看 |
| modes/shizuku-status | modes 无 steps 参数；status UID0/2000 × authorized/denied/stopped，逐项实际准备 | A08–A14、M、L04 |
| `permission_guidance.py` | 仅隔离模拟器，先读取该脚本的清数据/撤权限行为 | I01–I02、A01–A06 |
| `LocalizationInstrumentation` | runner 未注册该 suite；直接 `am instrument -w`，保存全部 stdout/stderr、installed/fixture hashes，按同一结果规则检查 | U01–U06 |
| `tests/e2e.py` | `--phase all` 后，另跑 `display`、`locked-boot`、`playback`、`rejected`；准备自己的 native keys | B04、D、L、R；隔离 rootable AOSP |
| `tests/shizuku_reconnect.py` | delayed/silent/missed/stalled × 实际 server UID0/2000 | L04、A08；按脚本前提准备 flags dex |

直接运行未注册 fixture 时，同样要求非空真实结果、全部 pass、final code
正确、无 error/restore_error，保存 installed APK 和 fixture hash。
不要通过修改 suite 参数绕过 runner 的设备限制。

### 真机与物理操作

| 工具/流程 | 用途和能力限制 |
|---|---|
| C2＋`tests/record_physical_volume.sh`/`read_volume_events.c` | 内置键旁路采集；不注入/不 grab/不创建播放。历史脚本固定 event0/1，先核对实际节点和名称；不匹配时不能声称采到 PMIC。录制器会临时改日志级别并恢复，仍检查 C0/C4。 |
| `tests/phone_reboot_observer.py --backend record` | 重启现场的只读观察；按实际 idle/bilibili 前提操作。它不替用户重启或播放，也不验证 Root 全套恢复。 |
| `tests/lockscreen_keys.py` | 外部 UHID/USB HID 锁屏流程；有 controlled playback 和 UI/设置变更，先读脚本。不能替代保留用户真实播放器的物理矩阵。 |
| `tests/screen_off_hid.py`/`playing_app_hid.py` | 外接按键、actual test playback 与 backend 验证；ordinary/shizuku 参数按工具支持；Root 使用专用流程。 |
| `tests/privileged_unlocked_hid.py` | Shizuku/Root 解锁亮屏 raw/HID；actual writer/reader 分开核对。 |
| `tests/phone_passive.py` | 无键被动观察；检查运行前提和是否改变播放，再选择，不能默认用来替代真实播放器。 |
| `DumpUiInstrumentation` | 非 suppressing UI 观察；仍按 C2 检查真实播放器和其他无障碍；首次解锁前可能不可用。 |
| `tests/write_report.py` | 合并同 main hash 的支持格式结果；不代替手工物理矩阵、helper UT、host/helper 身份及恢复报告。 |

Root 物理矩阵若现有 Python 工具没有对应参数，按 C0/C2/C3 手工或设备 UI
执行并填模板，不虚构 `--backend root` 给只支持 ordinary/shizuku 的脚本。
UI 操作优先不抑制服务的观察途径，操作前核对当前窗口/元素，不盲点固定坐标。

## 8. 状态判定与时序问题

| 状态 | 使用条件 |
|---|---|
| PASS | 前提已满足；指定输入和全部预期有证据；采集完整；该用例需要的恢复已核对。 |
| FAIL | 软件行为、稳定值、帧或恢复不符合预期；已知历史失败也用此状态。 |
| BLOCKED | 未具备设备/权限/实际 app Root/安全音量或采集能力，尚无法执行；写具体原因。 |
| NOT_RUN | 尚未开始，或者本轮授权范围未包含；不合并进 PASS。 |
| INCOMPLETE | 已开始但窗口、连接、进程或观察时长不足，不能作行为结论。 |
| ERROR | fixture/采集器/runner 自身失败；保留现场，修工具再测；不会把它当作产品已通过。 |
| N/A | 已核实功能不适用于此平台/组合，写理由和证据。 |

授权成功、callback 收到、Binder matching target、complete 文件、单次正确
回读各自只证明一个阶段。实际按键与最终稳定必须各自证实。
若多个阶段结果不同，报告“输入已收到，修正超时/最终不符”等具体事实。

当前保护值：raw freshness 250 ms、readback quiet 300 ms、写入原总期限
1000 ms、有界 settling retry；hold timeout/repeat 使用 Android 配置，
最长按键证据还有既有 10 秒上限。测量实际时序、失败和状态，不延长这些
生产保护来使负载测试通过。将来正式改变保护值时，改用例并说明产品依据，
同时保留原失败及新旧时序对照。

既有 fixture 内部 bounded wait 是工具容许的调度等待，不能让目标取代真实
readback；看到 UNCONFIRMED 就按该场景预期判定，不能用日志中的计划值判 pass。

## 9. 历史未解决项必须继续可见

以下仅是截至 2026-11-06 的起点，执行新版本时全部重新取证：

- R41：无播放持续基线下仍丢真实短按，另有 main 排队超时及相机快捷。
  对应 PV-S01/S02/S10、K03。实际播放五连按通过不关闭此失败。
- R56：Root full 的迟到 readback/quiet 导致补写超过原一秒期限，
  两个失败与后来的匹配新 host 成功重跑均保留。对应 PV-K10/E06/H06。
- 真机 pending 时立即物理开屏、其他所选 backend 真实重启、全部反向/
  时长/扬声器亮屏组合还未完整执行。对应 PV-S05、L06/L08、K04 和主矩阵。
- 蓝牙真实输出与系统 readback 有不同证据层；实际播放部分物理路径通过
  不等于全部耳机、绝对音量开关、听感问题均已解决。对应 PV-R04/R05。

本轮已完成与未完成的历史证据见
[2026-11-06 报告](../../../../dist/verification-20261106-volume-keys.md)。
旧 P/R 条目中“当时未验证”“当时 helper 版本”按历史记录读；
当前完整组合状态以本轮执行表为准。

## 10. 持续维护

每个新用户问题按反馈表末尾继续追加编号和对应 PV 用例；新工程故障追加
incident，并在本计划加复现条件和关联测试。旧 PV、P、R 编号保留。
新增功能至少包含正常流程、拒绝/失败、切换/中断、持久化/恢复和旧功能保护，
若某方面不适用写理由。

用例新增时同步修改总数、反馈表、执行模板的生成规则及 Skill 链接。
报告必须附计划版本与实际展开组合；连续多次迭代用同 ID 对照结果、时间、
资源开销和原始证据。只有同 source/差异、APK、helper、host、设备固件、
输入/条件都满足要求的原运行才可复用，注明来源和复用理由。

本计划保留原 [P01–P29](phone-matrix.md)、
[screen-off R/N 用例](screen-off-cases.md)和[故障记录](incident-log.md)：
PV 编号用于完整功能目录，P/R/N 负责历史细节，不删除原失败和撤回结论。
