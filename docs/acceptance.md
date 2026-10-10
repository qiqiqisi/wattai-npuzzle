# 提交验收

状态仅使用 `PASS / PARTIAL / FAIL / NOT_RUN / MANUAL_PENDING / BLOCKED`。自动通过不等于人工界面或课程提交已完成。

| 项目 | 状态 | 真实证据与边界 |
| --- | --- | --- |
| 教师 DOCX 正文/棋盘图、五页 PDF、报告模板阅读 | PASS | [文档身份](../results/submission-20261010/teacher-materials.json)、[合规审查](teacher_compliance.md)，本地保留页图 |
| 原教师保护文件与原题恢复 | PASS | [31/31 字节一致](../results/submission-20261010/teacher-protected-files.csv) |
| Java19 全新目录编译及六套测试 | PASS | [最终编译/8测试/诊断工具/双入口](../results/submission-20261010/final-tests.json)：--release19，六套227250检查、两个新增回归、工具与教师/学生入口通过；首次工具枚举失败日志保留 |
| 教师3×3 A* 真优先队列、最短16步、合法路径、1秒 | PASS | [24行同题对照](../results/compare-20261010T061625055Z/comparison.csv)、TeacherStage1Test 独立 BFS=16；A* 最大搜索28.752ms，所有三启发组合父端记录<1s |
| 第二阶段4×4 IDA* 与A*适用性对照 | PARTIAL | 同题索引3成功配置52步；Misplaced两项10秒TIMEOUT保留。原材料“相同实例”表述没有另一张独立明确4×4图，[解释与边界](teacher_compliance.md) |
| hard 原题索引9、BASIC/555T/fast，60秒 | PASS | [冷原始CSV](../results/run-20261010T061439277Z/stage3.csv)：70步，建库6192ms、搜索26074.878ms、父端32593.8569ms；[环境](../results/run-20261010T061439277Z/environment.json) |
| cold/warm 区分、分时、堆与源码身份 | PASS | [冷目录](../results/run-20261010T061439277Z/)、[热CSV](../results/run-20261010T061439277Z-warm/stage3.csv)；父 JVM 自身首次启动未单独计时，未将父端差值称为纯启动 |
| RunHard 真实status/题目/路径/实际时限门禁 | PASS | BenchmarkAcceptance、BenchmarkAcceptanceTest；[完整入口日志](../results/submission-20261010/)中最终退出0，首次 stderr 脚本失败退出1也保留 |
| CSV/TSV 数据、历史失败保留 | PASS | [62 CSV与55路径验证](../results/submission-20261010/data-validation.json)，历史状态汇总是全文件行计数，存在汇总/原始重复，不能视为独立实验数 |
| A*三启发函数与IDA*可比表 | PASS | 每个teacher3组合独立JVM三次、sample4一次，新PDB冷缓存、10秒上限；[原始表](../results/compare-20261010T061625055Z/comparison.csv) |
| GUI 解析、状态机、速度和坏文件自动测试 | PASS | PuzzleCloseoutTest、55份真实TSV回放；覆盖3×3/4×4 |
| 实际Swing按钮/布局/真实截图 | MANUAL_PENDING | `MANUAL_GUI_PENDING`，工具无原生桌面操作能力；没有伪造截图。操作步骤见下 |
| 完整可编辑DOCX及同内容PDF | PASS | [15页逐页排版与元数据审查](../results/submission-20261010/report-qa.json)、[报告](../report/README.md)；真实封面、更新目录、完整正文/表格，无模拟GUI图 |
| 封面姓名、学号、学期 | MANUAL_PENDING | 均为待填写 |
| 教师材料忽略且未跟踪 | PASS | `git ls-files teacher_reference`为空、check-ignore命中；源/题目字节保留规则已加入 |
| Git diff、暂存blob/隐私/导航审查 | PASS | [暂存审查](../results/submission-20261010/git-audit.json)、[纯暂存树构建](../results/submission-20261010/clean-checkout.json)：源码与实测清单一致，31教师blob一致，64个导航链接有效，无原件/cache/凭据/私密路径 |
| existing private分支提交/普通push/远端SHA | PASS | [远端提交/完整tree回执](../results/submission-20261010/branch-receipt.json)：submission/teacher-compliance-20261010 普通push，main保持原SHA、仓库private；随后仅追加验收记录提交 |
| 教师材料公开授权 | NOT_RUN | 未请求公开，private保持；[第三方边界](../THIRD_PARTY_NOTICES.md) |

## 最短人工收尾

1. 若在新克隆中操作，先执行 `powershell -NoProfile -ExecutionPolicy Bypass -File tools/compile.ps1`。
2. 打开3×3：`java -cp build-test stud.g01.visual.PuzzleVisualizer results/solutions/2026-10-10T06-16-33.017562500Z-problems_teacher_stage1_1-ASTAR-MANHATTAN.tsv`，核对初态、目标、16步与顶部文字无截断。
3. 点击 Play 后 Pause，确认棋盘停止，停留时步号不变。
4. 点击 Previous/Next/Restart，检查0步不后退、末步不前进、重置停止播放。
5. 在播放中切换0.5x/1x/2x，确认后续步进约对应1400/700/350ms，状态连续。
6. 打开4×4：`java -cp build-test stud.g01.visual.PuzzleVisualizer results/solutions/2026-10-10T06-14-39.493084100Z-problems3_10-IDA-DISJOINT_PATTERN.tsv`，播放到70步，确认目标一致、自动停下且不越界。
7. 用本地测试生成的 `generated/closeout-test/invalid-4.tsv` 运行，确认报错而非展示错误棋盘；保留真实截图后再更新人工验收。
8. 填写报告封面，并执行report/README中的Word导出命令更新PDF；按课程要求提交。不能把填表或push等同于教师最终验收。
