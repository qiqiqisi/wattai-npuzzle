# 教师框架合规审查

依据为本地教师《NPuzzle求解的要求.docx》的“请使用课程提供的基本框架，框架已有代码不要修改”。已读取三份 DOCX 正文和嵌入棋盘图，逐页查看五页 PDF 讲解及报告模板的三页渲染。

`teacher_reference/WattAi` 是用户提供的只读参照，根 `WattAi` 是学生工作工程。原参照包含待实现 g01 桩，且 EngineFeeder 引用了参照中缺失的 stud.solver.IdAStar，不能直接认为原框架本身已完整编译。其来源是用户本轮提供的目录，没有独立官方 ZIP 签名；本次以该目录字节建立可核查基线，不把此前学生快照冒称教师原版。

教师第一阶段图中的空位 `#` 对应代码 `0`：初态 `8 6 7 / 2 5 4 / 3 0 1`，目标 `6 4 7 / 8 5 0 / 3 2 1`。第三阶段图为逆序 15-Puzzle，与原 `problems3.txt` 第10行（零起9）初态一致，目标保持该行原始标准目标。

PDF 的第3页示例建议修改 EngineFeeder 第55行，且 SearchTester 注释允许自行调整；本轮遵守更严格的原文件恢复规则，通过学生兼容适配器及 StudentSearchTester 实现同等入口功能，没有利用讲解局部建议去放宽保护边界。第二阶段原要求“4阶…解决相同的问题实例”未另给清晰独立棋盘图；采用原题4×4索引3同题对照，加上hard实测；此解释明确记录，不虚构第二阶段另有新图片。

完整清单包含相对路径、原始 SHA-256、工作前 SHA-256、差异类型、最终 SHA-256、是否一致：

- [保护文件与原题清单](../results/submission-20261010/teacher-protected-files.csv)：31/31 逐字节一致。
- [教师文档身份](../results/submission-20261010/teacher-materials.json)：三DOCX和一PDF的大小/哈希。
- [学生工程文件清单](../results/submission-20261010/inventory.json)：用途类别、大小与哈希。
- [二进制检查](../results/submission-20261010/binary-audit.json)：历史class仅核对魔数/版本，不声称对应当前源码；新hard PDB核对头与CRC。

最初 core 的实质变化为 Problem 增量预测、HeuristicType 扩展、EngineFeeder import、SearchTester 路由/Scanner关闭。其余 core/教师示例主要是编码、空白或注释修复。BestFirstSearcher 搜索行为原来未改，但注释也已恢复。algs4 原本一致，版权声明保留。

允许实现的 `src/stud/g01` 原桩继续使用当前已验证工程。新增学生代码在 `src/stud/g01/heuristic`、`src/stud/g01/runner/StudentSearchTester.java` 和 `src/stud/solver/IdAStar.java`，详见 [变更](changes.md)。新增教师实例夹具位于独立 `resources/problems_teacher_stage1.txt`，没有改原题顺序或数字。

教师参照/本地归档/IDE/PDB/JFR/cache均被忽略，`git ls-files teacher_reference` 为空； `.gitattributes` 保留源码和题目原始字节。提交前对暂存blob再次检查，避免 core.autocrlf 改掉已恢复的字节。

框架字节恢复、Java19兼容、六测试、hard自动验收已通过。未解决项目：GUI人工操作和封面真实身份待用户完成；教师公开授权未确认，仓库继续private，不改变第三方许可。以上边界不会因私有push自动变成已验收。

2026-10-11最终复核：再次读取三DOCX、棋盘图、五页讲解与模板；当前文件、只读参照及SHA清单31/31一致。src、tests、resources与e66ca758保持一致。干净构建、八回归、双入口、原寻路与独立冷hard通过，见[统一证据](../results/final-20261011/verification.json)。旧inventory/report-qa/data-validation为2026-10-10历史快照，当前附件以INDEX与新验收为准。
