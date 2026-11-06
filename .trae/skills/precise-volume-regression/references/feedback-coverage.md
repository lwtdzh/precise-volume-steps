# 历史反馈与测试用例对照

整理日期：2026-11-06。反馈编号 UF-001–UF-083 对应开发开始至本轮对话
汇总的 83 项内容，保留不同时间、播放/重启/锁屏条件下的复发。
这些是历史现象、要求和工程发现；本表不声明当前仍全部失败或已经通过。
执行操作及预期见 [主测试计划](test-plan.md)，结果填
[执行记录](test-run-template.md)。后续新增反馈接在原汇总之后；目前已记录至
UF-084，下一项使用 UF-085，不改旧编号。

## 用户直接反馈的使用与界面问题

| 反馈 ID | 当时问题/条件 | 对应 PV 用例 |
|---|---|---|
| UF-001 | 仅前台接管，后台失效 | PV-M02、PV-K02、PV-L01 |
| UF-002 | 总开关 ON，daemon/service 却 OFF | PV-A13、PV-M01、PV-D07 |
| UF-003 | Vivo 自启动入口缺失，只有关联启动，多次更新仍缺 | PV-A07、PV-I03、PV-U07 |
| UF-004 | 后台常驻、恢复能力不足 | PV-L01、PV-L02、PV-E01、PV-E05 |
| UF-005 | B 站/音乐开始播放走系统步长，停止又恢复小步长 | PV-R03、PV-S13、PV-M02 |
| UF-006 | 后台突然按实体键偶发默认步长 | PV-K02、PV-L01、PV-S13 |
| UF-007 | 先粗调，稍后才精调，感到跳变 | PV-K09、PV-D04、PV-R05 |
| UF-008 | 没按键面板自行弹出 | PV-K12、PV-S09、PV-R08 |
| UF-009 | 后台实体键没有 App 面板 | PV-D01、PV-D06、PV-M02 |
| UF-010 | Toast 难看，连续按反复弹 | PV-D01、PV-D04 |
| UF-011 | 面板摇晃、高度/位置变 | PV-D03、PV-D08、PV-U06 |
| UF-012 | 数字先粗值再最终值，闪烁 | PV-D04、PV-D05、PV-K09 |
| UF-013 | 顶部缺百分号，系统范围/阶数/百分比混淆 | PV-D02、PV-V01 |
| UF-014 | 通知缺用户当前档位和总档位 | PV-D02、PV-D07、PV-U04 |
| UF-015 | 多条音量条冗余，要求三行加一条 bar | PV-D01、PV-D02、PV-D03 |
| UF-016 | 要第三行实际 readback；68 target/71 actual 是说明需求的假设例子 | PV-D05、PV-V12、PV-K10 |
| UF-017 | 50 阶，14% 第一加应16%，却14→15→16→18 | PV-V02、PV-V03、PV-V04 |
| UF-018 | 加第三行后闪烁、颤抖复发 | PV-D03、PV-D04、PV-D05 |
| UF-019 | 早期锁屏/息屏像未开启，走系统步长 | PV-S03、PV-S04、PV-M03、PV-M04 |
| UF-020 | 无播放81%，息屏减5＋亮屏减1应69%，却79% | PV-S03、PV-S01、PV-V06 |
| UF-021 | 普通助手重启后 B 站后台大步长，150/50应3却10 | PV-L05、PV-R03、PV-S13 |
| UF-022 | 同次重启后 idle 息屏十按没有变化 | PV-L05、PV-S01、PV-S02 |
| UF-023 | 重启后组件看似 ready，行为仍坏，要查初始化 | PV-L05、PV-L09、PV-L12、PV-H01 |
| UF-024 | 撤 Shizuku grant 普通也失效，要保留原 Shizuku | PV-M03、PV-A08、PV-L04 |
| UF-025 | 普通后台仍不可靠，Shizuku 重启手动激活麻烦 | PV-M02、PV-L05、PV-L06 |
| UF-026 | BT absolute volume 开时58/61/63听感相同 | PV-R04、PV-R05 |
| UF-027 | BT B站息屏五减70→60显示对，听感跌幅较亮屏大 | PV-R05、PV-S13、PV-R03 |
| UF-028 | 息屏减后长期较低，系统133→123→130不足证明耳机增益恢复 | PV-R05、PV-R04、PV-H09 |
| UF-029 | 助手听感改善，Shizuku 息屏听感仍降幅大 | PV-R05、PV-S13、PV-M03 |
| UF-030 | 助手关屏等2秒正常，立即按骤降，补测仍疑似 | PV-S01、PV-S02、PV-S13 |
| UF-031 | 后来亮着的锁屏页三个模式均接管失败 | PV-S04、PV-M08、PV-A11 |
| UF-032 | 亮屏锁屏修正慢半拍 | PV-D06、PV-S04、PV-K09 |
| UF-033 | 锁屏面板迟到好几秒 | PV-D06、PV-D01、PV-A11 |
| UF-034 | 仅锁屏显示正在修正，要求一致 | PV-D04、PV-D06 |
| UF-035 | App ON 息屏连按亮手电，OFF不亮 | PV-S10、PV-S11、PV-K07 |
| UF-036 | 已授 Shizuku，开机选择后页面授权状态缺失，含 UID0 | PV-A08、PV-L06、PV-L12 |
| UF-037 | 用 Shizuku 授 helper，已有 grant 仍要求打开 Shizuku | PV-A06、PV-A08 |
| UF-038 | 无障碍按钮只开列表，不定位服务 | PV-A02、PV-A03 |
| UF-039 | SukiSU 授权后仍说无 Root，需分主包/helper | PV-A09、PV-A10、PV-X04 |
| UF-040 | 状态栏图标有时白底有时透明，原因待实际核对 | PV-D07、PV-U06 |
| UF-041 | Vivo 换字体失效，开主页/切总开关不恢复 | PV-L07、PV-M08、PV-A11 |
| UF-042 | 特权亮屏仍依赖易被系统清掉的无障碍 | PV-M08、PV-M05、PV-L07 |
| UF-043 | 特权无需无障碍读键，但锁屏面板要自动恢复服务 | PV-A11、PV-M08、PV-D06 |
| UF-044 | 助手息屏三次倒推只生效两次 | PV-S01、PV-K03、PV-S03 |
| UF-045 | 最新助手刚关屏连续减次数不匹配，旧问题复发 | PV-S01、PV-S02、PV-L05 |
| UF-046 | 最新 Shizuku 长按先很低后回升；Root 是要求检查，非当时亲测结论 | PV-K04、PV-K06、PV-K10、PV-E06 |
| UF-047 | 最新 idle 五连按触发相机；要求保留快捷，以实际播放为主 | PV-S10、PV-S13、PV-S02 |

## 用户补充的功能与使用要求

| 反馈 ID | 要求 | 对应 PV 用例 |
|---|---|---|
| UF-048 | 任意范围/阶数，不能写死150/50 | PV-V01、PV-V10、PV-D02 |
| UF-049 | 不整除累计取整，同位置相同增量 | PV-V04、PV-V01 |
| UF-050 | 立即 `+-/-+` 精确回起点，含 pending | PV-V05、PV-V06 |
| UF-051 | 端点连按不积累隐藏档位 | PV-V07、PV-V10 |
| UF-052 | hold/burst 快速升降，松手不反向回弹，三模式都查 | PV-K03、PV-K04、PV-K05、PV-K06 |
| UF-053 | 保留其他 App 监听与截图，截图不调音量 | PV-K07、PV-K13 |
| UF-054 | 普通日常无 Root/Shizuku；一次助手 ADB 配置跨重启保留 | PV-M02、PV-A05、PV-L05 |
| UF-055 | 缺助手 DUMP 仅影响 input，不阻止亮屏基本调节 | PV-A04、PV-M07 |
| UF-056 | 首装/清数据 guide，必要权限快捷入口 | PV-I01、PV-I02、PV-A01、PV-A02 |
| UF-057 | helper 授权有完整 ADB 复制与 Shizuku 自动两入口 | PV-A05、PV-A06 |
| UF-058 | 直接 Root 模式；一键可授权项，手动项清楚提醒 | PV-A09、PV-A10、PV-A12、PV-M04 |
| UF-059 | 整理开关，三个互斥模式易读 | PV-M01、PV-A13、PV-U07 |
| UF-060 | 降低两个 App 使用成本，主入口集中而兼容身份保留 | PV-I04、PV-B06、PV-U07、PV-X04 |
| UF-061 | 繁体港台澳/日/英，原生 Android 资源，易扩语言 | PV-U01、PV-U02、PV-U03、PV-U05 |
| UF-062 | 已授权是否还2秒检查，关注耗电；不是实测电池耗尽 | PV-E02、PV-E03、PV-E05 |
| UF-063 | ADB自动安装确认，保持屏幕可操作，恢复原设置 | PV-I06、PV-I05、PV-H08 |
| UF-064 | README讲安装用法，历史/测试放Skills并链接 | PV-U07、PV-H02、PV-H07 |
| UF-065 | 保留真实播放器，不用假音频替代，最新相机设置保留 | PV-S13、PV-S10、PV-H03、PV-H05、PV-H08 |

## 用户指出的测试与协作问题

| 反馈 ID | 问题/要求 | 对应 PV 用例 |
|---|---|---|
| UF-066 | 多次说修复却实测仍坏或后续复发 | PV-H07、PV-H09、PV-H02 |
| UF-067 | 真模拟息屏了吗；synthetic/ADB/HID/内置键证据分开 | PV-H03、PV-H09、PV-S01 |
| UF-068 | 先模拟器逻辑，再真机，减少重复手动按 | PV-B05、PV-H03、PV-H04 |
| UF-069 | 另一 Agent 同时动手机污染状态；重新覆盖/重启验证 | PV-H01、PV-H04、PV-H08、PV-I03、PV-L05 |
| UF-070 | 记录窗口过期/拔USB后才操作，反复重开 | PV-H04、PV-S01 |
| UF-071 | 11/4又误报无Root；主包已授，测试过早退出影响判断 | PV-H01、PV-L12、PV-A09 |
| UF-072 | 新故障保存版本/提交/函数/原失败/重跑并链接Skills | PV-H07、PV-H02 |
| UF-073 | 不以部分组合说全部完成；剩余未测要明确 | PV-H09、PV-H02 |

## 排查发现的工程与测试问题

这些来自此前工程排查，不统一归为用户亲自测出的 App bug。

| 反馈 ID | 工程发现 | 对应 PV 用例 |
|---|---|---|
| UF-074 | 粗调值被当 origin，6→16→19而非9 | PV-K09、PV-V08 |
| UF-075 | 首次采样漏DOWN、慢dump跨短按、main排队过期 | PV-S01、PV-S02、PV-S07、PV-E03 |
| UF-076 | Android repeat400/50与旧500/100估算不匹配，7/15回8/15 | PV-K06、PV-K04 |
| UF-077 | 原生长按快于配置，计划较慢反向拉回 | PV-K06、PV-K04 |
| UF-078 | 接受的绝对写入晚于 native repeat 生效 | PV-K06、PV-K10、PV-E06 |
| UF-079 | 匹配 ack 后仍有多批 late writes | PV-K10、PV-E06 |
| UF-080 | 关闭/切模式旧补写，early rejection过早结束quiet观察 | PV-K10、PV-K11、PV-M06 |
| UF-081 | hadRelease提前结算新held键，强制亮屏回Accessibility补丁撤回 | PV-K05、PV-S05、PV-M08、PV-H07 |
| UF-082 | Root超一秒UNCONFIRMED；旧fixture看日志误报 | PV-K10、PV-E06、PV-H06 |
| UF-083 | readiness竞争/恢复引号/影响播放器/虚拟身份/触底与空窗口误判等工具错误 | PV-H01、PV-H04、PV-H05、PV-H06、PV-H08 |

## 后续新增反馈

| 反馈 ID | 日期与问题 | 对应 PV 用例 |
|---|---|---|
| UF-084 | 2026-11-06：助手/Shizuku 就绪状态是绿色，Root 就绪却是普通深色文字；要求 Root 同样显示绿色。 | PV-D07、PV-A09、PV-M01 |

## 已有 P 用例的接续

完整验证仍逐项登记原 P01–P29。以下给出主目录入口；
P 中的历史具体条件继续读原文，PV 目录补充不同功能的异常和持久化。

| 原用例 | PV 入口 |
|---|---|
| P01 | PV-M02、PV-K02、PV-L01 |
| P02 | PV-V01、PV-D02 |
| P03 | PV-V02 |
| P04 | PV-V03 |
| P05 | PV-V04 |
| P06 | PV-V05、PV-V06 |
| P07 | PV-K03、PV-K04、PV-K05 |
| P08 | PV-V07 |
| P09 | PV-V08、PV-V09 |
| P10 | PV-D01、PV-D03 |
| P11 | PV-D04、PV-D05 |
| P12 | PV-V12、PV-D05 |
| P13 | PV-K12、PV-S09、PV-R09 |
| P14 | PV-K07、PV-K08、PV-K13 |
| P15 | PV-S01、PV-S03、PV-S05、PV-S08 |
| P16 | PV-M06、PV-V09、PV-K11 |
| P17 | PV-A08、PV-L02、PV-L04、PV-I09 |
| P18 | PV-L01、PV-L02、PV-L03、PV-I03 |
| P19 | PV-L05、PV-L06、PV-L08、PV-L09 |
| P20 | PV-R01–PV-R06 |
| P21 | PV-I06 |
| P22 | PV-A01、PV-A03、PV-A07 |
| P23 | PV-S04、PV-S07、PV-M05、PV-D06 |
| P24 | PV-S10、PV-S11 |
| P25 | PV-A08、PV-L04、PV-L06 |
| P26 | PV-A02 |
| P27 | PV-A06 |
| P28 | PV-A09 |
| P29 | PV-A11、PV-M08、PV-M05 |

## R/N 历史保护的接续

逐项打开 [screen-off-cases.md](screen-off-cases.md) 的 R/N 表：
登记每个实际列出的编号、适用条件及本轮状态；不能凭编号范围假设所有编号
都有同一个 fixture。后续 incident R43–R65 的每次运行是历史证据，
本轮按主计划展开相同组合并重新取证，不将旧 pass 计为本轮执行。

- 原点/写入/被动与输入区分：PV-V、PV-K08–PV-K13、PV-R09。
- R15–R18 重启反馈：PV-L05/L06/L08/L09。
- R31/R32 release 跨开屏/解锁：PV-S05/S06。
- R33/R34 连按与长按：PV-K04/K05、PV-S01/S02。
- R35 撤回 hadRelease：PV-K05；不能删去反例。
- R36/R38 late writes/early rejection：PV-K10。
- R37 关闭取消：PV-K11、PV-M06。
- R39/R40 native 比计划快/排队绝对写：PV-K06/K10。
- R41/R42 真短按采样与快捷：PV-S02/S10。
- R43–R65：对照对应模式/阶数/route/screen/真实播放/PMIC/恢复条件。
  空窗口、触底、原 Root 超时和工具误判保留；PV-H04/H06/H08 专门检查。

来源包括拆分前原始开发对话、当前项目对话，以及以下永久资料：

- [物理测试 P01–P29](phone-matrix.md)
- [screen-off 故障与补测](screen-off-cases.md)
- [故障记录及撤回结论](incident-log.md)
- [普通模式兼容历史](../../precise-volume-compatibility/references/ordinary-screen-off.md)
- [真实重启历史](../../precise-volume-compatibility/references/reboot-screen-off.md)
- [三模式实现](../../precise-volume-compatibility/references/three-modes.md)

原始对话含私人数据，不复制原记录到仓库；本表只保留测试所需的脱敏事实。
