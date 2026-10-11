# 最终提交验收

2026-10-11自动复核。状态只使用 `PASS / PARTIAL / FAIL / NOT_RUN / MANUAL_PENDING / BLOCKED`。命令、退出码、数据与整理映射见[统一证据](../results/final-20261011/verification.json)，历史证据保留日期和配置。

| 验收项目 | 状态 | 证据及边界 |
| --- | --- | --- |
| 教师要求/棋盘/讲解/模板阅读 | PASS | 三DOCX正文、实际三幅棋盘、五页PDF及模板重新检查；[来源和歧义](teacher_compliance.md) |
| 31项教师文件和原题 | PASS | 当前文件、只读参照与[原SHA清单](../results/submission-20261010/teacher-protected-files.csv)31/31逐字节一致 |
| Java19干净构建 | PASS | 全新快照无teacher_reference、未跟踪工具、旧class或缓存，javac --release19编译62份源码 |
| 原有六测试 | PASS | Foundation43、Stage1 1209、Stage2 182950、Stage3 32280、Closeout5992、Alternative4776，合计227250检查 |
| 教师3×3、真优先队列、16步、1秒 | PASS | 独立BFS=16，A*回放=16；新教师入口一次包含两启发搜索的总墙钟<1s；[历史六组合三次同题表](../results/compare-20261010T061625055Z/comparison.csv)仍可访问 |
| IDA*阈值/剪枝、任意目标、不可解、碰撞 | PASS | 最小超界f、路径去环、每轮TT重置、完整状态碰撞比较均审查；小规模BFS、枚举及随机回归通过，不宣称任意图形式证明 |
| PDB加性成本/分组/最优性 | PASS | 本组将牌移动成本1，其余0，0-1 BFS；重叠完整分组取max；独立小表距离、一致性、排名和坏缓存回归通过 |
| BenchmarkAcceptance新增回归 | PASS | TIMEOUT/OOM/FAIL/UNSOLVABLE拒绝；实际hard另验证输入/目标、完整路径、指标与父端时限 |
| 教师/学生入口、诊断工具、原寻路示例 | PASS | 教师阶段1、学生阶段2、PathFinding均退出0，诊断工具--release19编译通过。收尾工具第一次参数顺序错误记录为调用失败，修正调用后通过，教师代码未改 |
| hard独立冷缓存 | PASS | [冷CSV](../results/run-20261011T071242033Z/stage3.csv)、[环境](../results/run-20261011T071242033Z/environment.json)、[完整70步](../results/solutions/2026-10-11T07-12-42.170991500Z-problems3_10-IDA-DISJOINT_PATTERN.tsv)：JDK19/1024m/60s/BASIC/555T/fast，建库4819ms、搜索17708.691ms、父端22729.4068ms |
| 同源热缓存与历史成绩 | PASS | [热CSV](../results/run-20261011T071242033Z-warm/stage3.csv)：加载29ms、搜索20069.836ms、父端20301.7694ms；历史[32.594秒](../results/run-20261010T061439277Z/stage3.csv)、[34.097秒](../results/run-20261009T070251741Z/stage3.csv)保留。源码与节点数相同，波动不能称为本次算法提速 |
| 第二阶段4×4同题比较 | PARTIAL | 原题索引3成功配置52步，两项Misplaced 10s TIMEOUT保留；教师阶段2“相同实例”表述缺少独立明确4×4图，解释见合规文档 |
| CSV/原始哈希/全部保留路径 | PASS | verify_project.py核查最终树58份CSV、46条保留TSV实际Java回放、77个本地导航及报告来源SHA；274份保留原始记录内容未变。93个旧结果对象的LF/CRLF登记差异按现有-text规则修复，原文件和报告数据哈希不改。汇总重复不算独立实验 |
| GUI解析/回放/状态/速度自动测试 | PASS | Closeout覆盖3×3/4×4、连续性、起末边界、暂停、速度及坏文件 |
| 两张真实Swing终态图 | PASS | [3×3](../report/images/teacher_stage1_3x3_final.png)、[4×4](../report/images/hard_15puzzle_4x4_final.png)原始PNG已嵌入正式报告，终态与目标一致 |
| GUI按钮和速度实际鼠标操作 | MANUAL_PENDING | 当前工具无法控制原生Swing；终态截图不能证明Play/Pause/Previous/Next/Restart/Speed逐项验收 |
| 正文/DOCX/PDF/模板/排版 | PASS | [17页正式报告](../report/README.md)，教师封面/A4保留、四表两图；逐页渲染、文本一致性、数据源/PNG哈希核查。打包renderer缺LibreOffice，隐藏Word导出及独立页图检查通过 |
| 封面身份与学期 | MANUAL_PENDING | 姓名、学号、年份/季学期、组员信息仍待本人填写 |
| 当前导航、报告附件、工具依赖 | PASS | 当前Markdown链接、报告数据源与原CSV路径均存在；干净快照不依赖未提交内容。历史库存JSON只描述当时树，不冒充当前附件 |
| 精简及用户资料保护 | PASS | 已跟踪文件447→404，results352→308；删除60个无当前引用的重复产物，补充必要独立冷热实验及一个统一证据；246项未提交资料归档到.local_archive/final-20261011/user-files，另外265份已有忽略快照/缓存/临时文件归档到同级ignored-results，均逐文件SHA核验；独立失败、源码、测试、许可保留，[取舍](changes.md) |
| 教师参考/缓存/隐私排除 | PASS | teacher_reference未跟踪；归档、IDE、PDB、JFR、class不上传；暂存源码/题目身份核查 |
| GitHub main统一与分支删除 | PASS | submission普通push已核实，main正常快进；GitHub重新读取提交及404个blob路径/SHA全量一致，Private/default-main保持；远程与本地submission已删除，仅main。统一证据记录已验证的发布前驱；回执提交另从远端复核最终SHA |
| 教师资料公开授权 | NOT_RUN | 未请求公开，保持Private及[原版权边界](../THIRD_PARTY_NOTICES.md) |

## 本人仍需完成

1. 执行 `powershell -NoProfile -ExecutionPolicy Bypass -File tools/compile.ps1`。
2. 打开3×3：`java -cp build-test stud.g01.visual.PuzzleVisualizer results/solutions/2026-10-10T06-16-33.017562500Z-problems_teacher_stage1_1-ASTAR-MANHATTAN.tsv`，检查初态/目标/16步与窗口文字。
3. Play后Pause，确认棋盘和步号停止推进。
4. Previous/Next/Restart，确认0步、末步不越界，重置停止播放。
5. 播放中切换0.5x/1x/2x，确认延时约1400/700/350ms，状态连续。
6. 打开4×4：`java -cp build-test stud.g01.visual.PuzzleVisualizer results/solutions/2026-10-11T07-12-42.170991500Z-problems3_10-IDA-DISJOINT_PATTERN.tsv`，到70步自动停止。
7. 用测试生成的 `generated/closeout-test/invalid-4.tsv` 确认坏文件报错，保存真实操作结果后更新人工状态。
8. 填写封面，按report/README重新导出PDF并检查目录页码，按课程要求提交。
