# 每轮测试执行记录模板

复制本模板开始新 run；默认结果全部 NOT_RUN，不复制历史 pass。
原始采集目录、脚本、解锁信息在仓库外；脱敏报告可保存到 `dist/verification-日期-主题.md`。
主计划：[test-plan.md](test-plan.md)；反馈映射：[feedback-coverage.md](feedback-coverage.md)。

## 1. 本轮身份

- Run ID / 开始与结束时间、时区：
- 主计划版本 / 本轮执行范围 / 用户限定：
- 源码 commit / 分支 / 未提交差异文件或 patch hash：
- 修改的功能 / 每轮必测和加测项 / 未选项及理由：
- 构建命令、SDK/JDK/Gradle、build 日志：
- main versionName/code / APK SHA-256 / certificate digest：
- helper versionName/code / APK SHA-256 / certificate digest / main 内嵌 bytes：
- host/ui/native recorder/fixture SHA-256；无 host 时写未安装：
- 设备 alias、serial（私有原记录）、model、manufacturer、API、fingerprint、user：
- 测试开始/重启后 boot ID / 实际 framework readiness：
- Root 来源、主 App UID/实际 su 是否可用；Shizuku 实际 UID/grant：
- 原始采集目录（仓库外）/ 脱敏报告位置：
- 是否有并行设备操作者 / 测试中断和恢复情况：

## 2. 原始现场与最终恢复

每行保留原快照、恢复前快照和最终实际读数。空白不是“没变化”。

| 字段 | 原现场 | 恢复前现场/用户新设置 | 测试引入变化 | 最终读回 | 恢复状态/证据 |
|---|---|---|---|---|---|
| enabled/steps/media_only/operating_mode/legacy字段 | | | | | |
| 各route min/max/current与bank、实际endpoint | | | | | |
| 主/助手/host/ui安装版本与hash，升级保留约定 | | | | | |
| Shizuku actual UID/grant/writer/reader/setup | | | | | |
| app Root grant/writer/reader | | | | | |
| helper signature/DUMP/关联启动/registered/baseline | | | | | |
| accessibility完整list/master/bound services | | | | | |
| notification/overlay/AppOps/battery/background | | | | | |
| screen/keyguard/timeout/stay-awake | | | | | |
| playback UID/PID/started、main PID、route | | | | | |
| camera/torch快捷namespace/值/原生选择 | | | | | |
| locale/font/theme/display size | | | | | |
| introduced host/probe/HID/reader/broker/forward | | | | | |
| pending/window/wake lock/轮询/线程 | | | | | |

恢复未完成：逐项写实际状态、原因、对日常使用的影响。原失败和恢复失败
都保留；不要通过重置手机或覆盖其他服务来令表格相同。

## 3. 用例和组合执行表

完整 run 首先建立主目录的全部 137 行，再展开每个用例需要的组合。
每行只表示一个实际条件；不同 mode/UID/steps/route/screen/input 不共用 PASS。
全量 PV 表之外，另外保留 P01–P29 和实际列出的 R/N 条目状态及引用。

推荐仓库外 CSV 列：

```text
run_id,case_id,variant_id,status,device_alias,api,firmware,source_commit,
main_hash,helper_hash,host_hash,fixture_hash,backend,server_uid,steps,scope,
native_min,native_max,origin,route,screen,keyguard,foreground,playback,
input_kind,prerequisites,operations,expected,observed,edge_times,
receipt_write_times,immediate_readback,settled_readback,panel_evidence,
stable_observation_seconds,evidence_path,restore_status,incident,notes
```

`status` 仅取 `PASS/FAIL/BLOCKED/NOT_RUN/INCOMPLETE/ERROR/N/A`。
`variant_id` 使用本轮固定组合键，例如
`vivoA-assistant-64-speaker-off-playing-pmic`；
Shizuku 要加实际 UID。空设备/条件行只能是 NOT_RUN，不能在摘要中算 PASS。

可以在本轮仓库外目录按主计划自动生成初始清单：

```sh
python3 - "$PVS_RUN_DIR/cases.csv" <<'PY'
import csv
import re
import sys
from pathlib import Path

plan = Path(".trae/skills/precise-volume-regression/references/test-plan.md")
cases = []
for line in plan.read_text().splitlines():
    if re.match(r"^\| PV-[A-Z]\d{2} \|", line):
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        cases.append(cells)
assert len(cases) == 137
assert len({row[0] for row in cases}) == len(cases)
target = Path(sys.argv[1]).expanduser().resolve()
repo = Path.cwd().resolve()
if target == repo or repo in target.parents:
    raise SystemExit("Execution evidence must stay outside the repository")
if not target.parent.is_dir():
    raise SystemExit("Create and verify the run directory first")
with target.open("x", newline="") as output:
    writer = csv.writer(output)
    writer.writerow(["case_id", "variant_id", "status", "operations", "expected",
                     "observed", "evidence_path", "restore_status", "notes"])
    for case_id, operations, expected in cases:
        writer.writerow([case_id, "", "NOT_RUN", operations, expected,
                         "", "", "NOT_RUN", ""])
print(f"{len(cases)} cases initialized: {target}")
PY
```

这个脚本只初始化清单，不操作设备、不展开全部组合、不执行或判断用例。
以 CSV 为记录时，逐步增加完整条件列和组合行；主目录 NOT_RUN 行只有全部
适用组合完成后才能替换为该用例的明确汇总。保留任何失败组合，不能被后续
PASS 覆盖。`N/A` 说明证据；每个 BLOCKED 写清缺失能力。

## 4. 单项证据卡

每个失败/间歇失败都填，关键真机通过项也填。一般通过项可引用 runner
真实 JSON，并补足 runner 没登记的设备/输入/route/恢复信息。

```text
case_id / variant_id / 对应UF、P、R：
版本、hash、fixture、设备和固件：
前提：mode/readiness、screen/keyguard、route/playback、range/origin/steps：
输入来源：UT / synthetic / ADB / UHID / USB-HID / 内置物理键：
操作：每次方向、实际DOWN/UP时间，是否pending、hold实测时长：
独立预期：目标序列、舍入、取消/失败的预期：
即时native、App receipt/delivery、request/result、后续settled序列：
面板：first frame、三行/bar/bounds、实际渲染状态、截图或video：
稳定观察时间：最后UP后多少秒，是否有迟到write：
实际播放：每次解锁/观察前后UID/PID/started/route：
结果状态和理由：
原始证据/脱敏证据：
恢复字段和实际读回：
故障ID、定位证据/待验证假设、后续重跑run：
```

听感/蓝牙输出项再列：音源、信号、endpoint、绝对音量状态、
采样方法、native readback、A2DP PCM 功率、模拟输出或主观观察。
没有测得的层面写 UNKNOWN，不从一个层面推导另一个。

## 5. 自动化结果

| 模块/suite/scenario | device/backend/steps | APK/helper/host/fixture hash | input | assertions pass/total/skipped | final code/error | 原始结果 | 状态 |
|---|---|---|---|---|---|---|---|
| main release UT | | | UT | | | | NOT_RUN |
| helper release UT | | | UT | | | | NOT_RUN |
| main/helper lint、build | | | | | | | NOT_RUN |
| ordinary/backend/frames/layout/screen-off | | | | | | | NOT_RUN |
| released-key-wake/helper-startup | | | | | | | NOT_RUN |
| privileged-hold full/native-hold/settling | | | | | | | NOT_RUN |
| modes/shizuku-status/reconnect | | | | | | | NOT_RUN |
| permission-guidance/localization/e2e phases | | | | | | | NOT_RUN |

每个 suite/scenario/UID/steps 独立一行。不能把较早 host 的通过断言算入
新 host 全套重跑；helper UT 不能被 main UT 替代；零断言/restore_error
必须失败或标工具错误。记录真实退出状态与最终 instrumentation code。

## 6. 本轮结果与下一轮接续

- 主目录：全部 137 用例，各状态数量（这是用例汇总，不是 UT 断言数量）。
- 实际展开组合：计划/已执行/未执行；主矩阵 72 个基本组合逐项状态。
- P01–P29/实际列出的R/N/全部历史反馈：执行结果或对应证据。
- 本轮通过：限定设备、版本、输入和条件的具体事实。
- 本轮失败：原失败、工具错误、恢复异常，分别写 run、原因证据、影响。
- 重跑：原 run→新 run，改动/理由；原失败仍保留。
- 未验证：BLOCKED/NOT_RUN/INCOMPLETE 的具体用例和条件。
- 长期观察：实际时长、CPU/RSS/线程/连接/电池统计与测试条件。
- 与上次同条件结果比较：新增失败、延迟变化、资源增长、旧故障是否复发。
- 恢复：逐字段核对结果、未恢复项、用户新设置是否保留。
- 下一位 Agent：应首先查看的证据、未完成步骤和设备现状；不保存私人凭据。

最终结论按本轮实际结果写“完成这些组合，以下失败/未测仍保留”；
发现 FAIL 或完整验证要求有组合未完成时，不写“全量通过”。
