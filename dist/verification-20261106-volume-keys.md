# 2026-11-06：息屏连按与长按回弹验证

当前补丁在这台 Vivo 的实际媒体播放中完成了助手五连按，以及 Shizuku、
Root 的亮屏和息屏长按验证。蓝牙和扬声器的通过结果分别列在下面。
助手真实重启后的五连按也已验证。这里的结果限定这台设备、当前 APK 和
已执行场景；原来的失败运行没有删除。

## 测试版本

- 主应用：1.5.3 / code 31，SHA-256
  `749a33bf8ebf6b5c6d45cd99b2eec02d8afb54e9711212e9fc6c17a93688bc30`。
- 助手：1.4 / code 5，SHA-256
  `34d190bf325d7399c42d02542bf7f27dcb341fa7b6f89f6be2d194ab61d75a9e`。
- 最终开发测试 host：SHA-256
  `1befd435ba48be3f6d4def48ad1f50d8be41c80488eea9c77b38b1c739eff299`，
  只用于模拟器，手机当前未安装。
- 手机：Vivo V2419A，Android 16 / API 36；
  firmware `BP2A.250605.031.A3_V000L1/compiler261012170621`。
- 修复代码见包含本报告的Git提交。下面区分引入问题的历史提交与本次修复，
  测试结果来自提交前已完成的运行。

## 查到的代码及修改

助手首次息屏采样：`b56330cb0cca86a1b620f757f980de2c6112907a`
引入按需读取 EventHub 状态。`InputDumpMonitor` 在 idle、prewarm=false 时
没有保持亮屏基线；关屏首键如果先于首次采样按下，可能只观察到 UP。
旧 monitor 的合成采样测试确实丢失首个 DOWN。当前在 listener 注册期间
持续保持严格设备基线，解除注册仍停止采样，注册时已按住的键仍不补 DOWN。
这个原因没有被证明由最近的 `db2caf0` 重新引入。

真机无播放连按还出现过另外的遗漏：一次 dump 跨过完整短按，另一次
callback 在 main 排队后超过 freshness；手机原生相机快捷也实际被触发。
这些失败保留在 R41。用户随后明确要求以实际播放为主场景并保留相机快捷，
当前没有修改相机开关，也没有增加假播放。

特权长按：`0add8337095032be23910af4b37e199533bc2a93` 引入
`RawVolumePress.up` 的固定 500/100 ms 时长估算及 release 后结算；
`db2caf032777369ee369da7b41634610b58fda04` 将 raw 路径扩展到未锁亮屏。
原版对照已复现：实际 native 已到 7/15，松手后却按较少次数写回 8/15。
后续又复现原生步幅更大，以及排队中的绝对写入晚于原生 repeat 生效。

当前读取 Android 的 repeat timeout/delay，保留原点累计舍入。
`VolumeStepHistory.Plan.advancePast` 把计划推进到原生进度之后；
`VolumeAdjustmentController` 在已经观察到原生长按进度时停止并行绝对写入，
松手后按真实 readback 安静期结算。power chord、过期事件、expected-current、
原一秒写入期限、模式切换取消和助手完整 release 门控继续保留。

早期 `hadRelease` 补丁会在下一键仍按住时提前结算，已撤回；
强制特权亮屏改回 Accessibility 的补丁也已撤回。R35–R40记录原失败、
修正后的代码和各次运行，不能用较早“通过”覆盖后来反例。

## 内置物理按键结果

所有下列通过行都有完整独立 PMIC 边沿和 Android readback；
同一真实播放器持续播放。音量范围为 0–150，长按时长按实际边沿计算。

| 记录 | 模式 / 阶数 | 路由 / 屏幕 | 操作 | 起点→最终 | 最后 UP 后稳定观察 |
|---|---|---|---|---:|---:|
| R46 | 助手 / 50 | 蓝牙 / 息屏 | 五连按 | 82→67 | 约42秒 |
| R49 | Shizuku / 50 | 蓝牙 / 亮屏 | 非触底长按 | 76→4 | 约76秒 |
| R50 | Root / 50 | 蓝牙 / 亮屏 | 非触底长按 | 76→16 | 约113秒 |
| R45 | Root / 50 | 蓝牙 / 息屏 | 长按1.271秒 | 76→19 | 约45秒 |
| R51 | 助手 / 64 | 蓝牙 / 息屏 | 五连按 | 76→64 | 约28秒 |
| R54 | Root / 64 | 蓝牙 / 亮屏 | 长按0.628秒 | 76→15 | 约74秒 |
| R55 | Root / 64 | 蓝牙 / 息屏 | 长按1.657秒 | 76→13 | 约49秒 |
| R57 | Shizuku / 64 | 蓝牙 / 亮屏 | 长按0.614秒 | 76→15 | 49.648秒 |
| R58 | Shizuku / 64 | 蓝牙 / 息屏 | 长按1.451秒 | 76→22 | 61.970秒 |
| R59 | 助手 / 64 | 扬声器 / 息屏 | 五连按 | 30→18 | 84.613秒 |
| R62 | 助手 / 64 | 扬声器 / 重启后息屏 | 五连按，随后物理开屏 | 30→18 | 68.760秒 |
| R63 | Shizuku / 64 | 扬声器 / 息屏 | 长按0.841秒 | 30→7 | 35.637秒 |
| R64 | Root / 64 | 扬声器 / 息屏 | 长按0.904秒 | 30→2 | 38.247秒 |

R63和R64的末尾各30次独立读数保持7和2，没有观察到回升；
731和568次状态样本分别全部 playback active=true。
R63的ADB连接退出255后，手机reader和采样仍运行，原窗口正常complete；
没有要求用户重新按一次来替代原运行。独立logcat为空，
应用receipt/write证据来自各次service dump的events记录。

助手五连按的原生过程仍采到粗调瞬态，扬声器测试曾出现30→20→10→0，
随后结算18。这些结果证明完整次数和最终稳定音量，不承诺听感完全无波动。
R62的物理POWER发生在18已经确认之后，不能充当pending时立即开屏证明。

## 自动化及保留的失败

报告前按保存的SDK/JDK配置重新构建，字节仍匹配上述两个hash：
main 67/67、helper 3/3 release单测，main/helper release lint和build通过。
首次缺少ANDROID_HOME的构建失败单独保留，未执行测试。

当前APK基础模拟器套件合计163个断言通过：helper startup 7，
release/wake 5阶和10阶各27，ordinary 17、frames 13、layout 56、screen-off 16。
这些是合成输入、实际Android服务和读数证据，不能代替物理键。

Shizuku和Root的native-hold各26/26、settling各10/10、backend各102/102
使用较早host `6c19a2d5…`；新host的full 7阶各36/36，modes 31/31。
host不同的结果在[自动化原始汇总](verification-20261106-volume-keys-automated.md)
逐行保留，不写成新host全套重跑。

Root full原先两轮仍35/36：迟到的readback使quiet后补写超过一秒，
Root正确拒绝过期请求，最终UNCONFIRMED。旧断言还漏掉此状态，
现已修测试工具，以实际pending和state判定。新host成功重跑在原期限内
得到CONFIRMED，原超时反例仍保留；没有延长保护期限，
也没有宣称任意系统排队延迟下都能确认。

外接UHID、合成输入、内置PMIC分别归档。较早hash、到时空窗口、
触底长按和工具准备失败没有算进上面的非触底通过行。

## 恢复及未验证项

手机已恢复助手模式、50阶、media-only、speaker bank=0、原完整无障碍
服务集/master=1、USB stay-awake=0、timeout=600000。
相机快捷原值0/1/2保持，原真实播放器没有被停止。
Bluetooth已重新开启，原耳机第一次连接提示Connection failed，
随后实际AudioService确认恢复原蓝牙路由；再用真实readback确认76/150。
speaker bank仍0，同一播放器继续持有started媒体，助手input active=true。
50个已确认本轮新增的设备端记录目录、界面dump和临时工具已先完整归档、
再删除；已有reader/script保留，phone test host/UI tools未安装。

模拟器恢复Helper50、volume10/15、原无障碍list/master=0、stay-awake=1；
本轮引入的Shizuku UID0 server停止，授权flags从2恢复0，
原su hash和SELinux Enforcing保持，无临时Root bind/broker。

其他所选backend的实际重启、真机仍pending时立即开屏、完整反向/不同时长/
扬声器亮屏组合，以及其他厂商仍未全部验证。无播放相机快捷接管和R41短按
采样遗漏也没有被“实际播放通过”抹去。

完整历史、提交和失败细节见[故障记录](../.trae/skills/precise-volume-regression/references/incident-log.md)，
两个项目Skills均已链接R63/R64。私有设备记录保存在Git外，
报告不包含解锁凭据或播放器私人数据。用户决定暂停剩余测试，
将当前修复、已完成的验证和未验证项一并提交；未追加测试通过结论。
