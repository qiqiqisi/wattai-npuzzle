# 原始实验数据

本目录只保留实验原始输入观测、失败、解路径和少量验证证据。报告正文、正式 DOCX、模板及排版材料保留在本地，不属于此精简代码仓库。

| 范围 | 证据 | 解释 |
|---|---|---|
| 冻结 hard 冷/加载 | [原始 CSV](accepted-20261008/stage3.csv)及同目录 parent/timings | 70 步、32.5683064/19.5118538s 父端到端 |
| 独立新冷运行 | [原始 CSV](run-20261009T070251741Z/stage3.csv)及同目录 parent/timings | 70 步、34.0966172s；build=6401ms、load=0、search=27410.9265ms |
| 版本配置与历史失败 | [逐次汇总](audit-20261008/run_summary_v2.csv)及各原始 run 目录 | TIMEOUT 计数只是最后采样；FAIL/OOM 与 TIMEOUT 区分 |
| Stage1/2 同题重复 | [Stage1 raw](audit-20261008/stage1-final.csv.raw.csv)、[Stage2 raw](audit-20261008/stage2-final.csv.raw.csv) | 中位数从原始重复重算；preliminary 不等于跨进程预热 |
| 历史随机实验 | stage1_random* CSV | 固定 seed，历史结果；随机目标为 shuffle，初态是从目标游走，联合样本非均匀独立 |
| 完整加性建库 | [CSV](additive-build-20261009T055706988422Z/complete-build.csv) | 两目标、每方法3次；3024 ranks 对照均通过，仅3×3 |
| 本次代码包复核 | [JSON](validation.json) | release19 编译、六套测试、30条实际路径；与性能运行区分 |

源代码/测试/输入在复制时逐字节核对；日志中的系统绝对路径只在仓库副本替换为 `<WORKSPACE>`/USER，源 SHA 与副本 SHA 保存在本地上传审计。历史原始日志未改。
visited/peak frontier/TT 项目数不是真实 JVM 峰值内存；1024m 是上限，物理内存与真实峰值未测。早期中间源码没有逐版完整快照，不把最终代码 SHA 赋给历史运行。
完整报告、所有审计和被移除文档工具的备份仍在本地；本包未带入集成测试构造的 TIMEOUT fixture。
