# 实验结果索引

最终状态以 [验收](../docs/acceptance.md) 为准。教师保护字节见 [合规清单](submission-20261010/teacher-protected-files.csv)，报告见 [report](../report/README.md)。

| 用途 | 推荐入口 | 数据解释 |
| --- | --- | --- |
| 最终独立hard复核（2026-10-11） | [冷CSV](run-20261011T071242033Z/stage3.csv)、[环境](run-20261011T071242033Z/environment.json)、[70步路径](solutions/2026-10-11T07-12-42.170991500Z-problems3_10-IDA-DISJOINT_PATTERN.tsv)、[同源热CSV](run-20261011T071242033Z-warm/stage3.csv) | JDK19/1024m/60s/BASIC/555T/fast；冷建库4.819s，搜索17.709s，父端22.729s；热父端20.302s |
| 最终自动验收、整理映射和报告核查 | [统一证据](final-20261011/verification.json) | 干净快照测试、31项本地教师对照、保留数据原字节、全部保留路径回放及链接/报告一致性；历史清单是历史快照，不充当当前附件 |
| 本轮hard最终推荐 | [冷CSV](run-20261010T061439277Z/stage3.csv)、[环境/源码哈希](run-20261010T061439277Z/environment.json)、[完整路径](solutions/2026-10-10T06-14-39.493084100Z-problems3_10-IDA-DISJOINT_PATTERN.tsv) | 原题索引9，70步，建库6.192s，搜索26.075s，父端32.594s |
| 本轮hard热缓存 | [热CSV](run-20261010T061439277Z-warm/stage3.csv) | 70步，加载40ms，搜索27.863s，父端28.186s；不能冒充冷运行 |
| 本轮教师3×3与普通4×4对照 | [24行原始表](compare-20261010T061625055Z/comparison.csv) | teacher3：6组各3次、16步；teacher4标签=原resources索引3、6组各1次，4个52步SOLVED/2个TIMEOUT |
| 最终测试与数据审查 | [本轮证据](submission-20261010/) | CSV列数、路径、源/题目哈希；汇总重复行不能当作独立实验数 |
| 首次本轮入口失败 | [首次cold CSV](run-20261010T061249636Z/stage3.csv)、[入口失败日志](submission-20261010/reproduce-public.log) | 搜索已70步/33.054s，但版本stderr采集导致脚本退出1，保留实际失败 |
| 2026-10-09历史cold | [历史CSV](run-20261009T070251741Z/stage3.csv) | 70步/父端34.0966172s；本轮只核验历史路径，不当成本轮成绩 |
| 旧hard失败与诊断 | [早期Stage3](stage3_pdb_benchmark.csv)、[诊断](diagnostics-20261008/stage3.csv)、[TT矩阵](tt-matrix-20261008/stage3.csv) | TIMEOUT/OOM/FAIL保留；配置/版本/限时不同，不能混算最终中位数 |
| 历史成功演进 | [555T及max](stronger-20261008/stage3.csv)、[accepted](accepted-20261008/stage3.csv) | 原始成功记录保留，最终建议采用本轮独立复测 |
| unit-cost单点BFS/BiBFS | [单点表](pdb_bfs_bibfs_benchmark.csv) | 不是完整加性建表，不用其结论宣传PDB构建更快 |
| 完整加性小表BFS/BiBFS | [完整建表](additive-build-20261009T055706988422Z/complete-build.csv) | 先消去0成本分量，BiBFS逐点构成完整输出；有逐状态距离参考验证 |
| 历史队列与随机题补充 | [队列](frontier_benchmark.csv)、[随机初态/目标](stage1_random_pairs_benchmark.csv) | 固定seed与历史版本，作为补充；不与本轮表混合 |

elapsed_ms为工作JVM内搜索；parent.txt从创建工作JVM前计时，含其启动/准备/搜索/回放/导出；父JVM自身首次启动未单独量测。PDB距离数组/文件大小、TT条目、当前堆快照均不是JVM峰值内存。

保留的原始CSV/日志/路径保持原位置及原字节，独立失败证据未清洗。重复成功组、旧测试副本和无引用路径的删除映射见最终统一证据，取舍见 docs/changes.md。旧库存与验收JSON是相应日期的历史快照，不能当作当前附件清单或独立实验次数；当前状态以 docs/acceptance.md 为准。
