# N-Puzzle 研究报告 LaTeX 版

[当前报告](main.pdf) · [主文件与封面](main.tex) · [第一部分正文](section-one.tex) · [其余正文](body.tex) · [版式参数](template.sty)

当前版本已填写第一部分“启发式算法综述”、第二部分“N-Puzzle游戏”、第三部分“N-Puzzle求解算法实现”、第四部分“N-Puzzle问题求解探讨”和第五部分“解路径的可视化”，并保留既有总结与参考文献。原目录的章节名称、编号、“不在位将牌”和“.总结及展望”等用词保留。

第三部分包含 3.1 状态与结点、3.2 Open/Closed 表、3.3 三个启发函数、3.4 后继生成、3.5 IDA\* 实现的可见正文，配图 3-1 至 3-6、表 3-1 至 3-3。文中代码均标注了 `src/` 下的出处文件，示意数值不是实验结果。跨算法引用 `nodesGenerated`、`nodesExpanded` 等计数时须注意 IDA\* 为跨轮累计口径，详见 3.5 节末尾。

第一部分包含 A* 流程、可采纳性与一致性条件、针对当前不重开 Closed 实现的最优性证明、重复状态及数据结构代价、IDA* 阈值更新与 BASIC 模式证明。五张配图按论证顺序穿插，图前引出问题、图后说明结论，示意数值不作为实验结果。

## 编译

Windows 安装 XeLaTeX 及宋体、Calibri、Arial 后，在项目根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File report/latex/build.ps1
```

脚本连续编译三次以更新目录与交叉引用，辅助文件和日志写入被忽略的 `generated/latex-template-build/`，最终输出 `report/latex/main.pdf`。最后一轮若仍有未解析引用、重复目标标识或缺失字符，则停止交付。脚本不依赖 Word，也不运行 Java 搜索或实验。

修改第一、三部分图形源文件后，可以同时重建全部矢量图：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File report/latex/build.ps1 -RebuildFigures
```

`section1Figures/`、`section3Figures/` 保留每张图的独立 TikZ 源文件与矢量 PDF；普通编译直接插入 PDF，不必重新绘图。脚本依次扫描这两个目录，新增插图目录要同步写进 `build.ps1`。第一部分对应关系如下：

| 图号 | 所在小节 | 内容 | 文件名（均有 `.tex` 和 `.pdf`） |
| --- | --- | --- | --- |
| 1-1 | 1.2.1 | A* 的 Open / Closed 流程 | `fig-1-1-astar-flow` |
| 1-2 | 1.2.2 | 最优路径边界结点的估值关系 | `fig-1-2-optimality-frontier` |
| 1-3 | 1.3.1 | 同一状态的路径代价与父链更新 | `fig-1-3-duplicate-state` |
| 1-4 | 1.3.2 | 可采纳性与一致性的区别 | `fig-1-4-consistency` |
| 1-5 | 1.4 | IDA* 的阈值递增 | `fig-1-5-ida-threshold` |

第三部分对应关系如下：

| 图号 | 所在小节 | 内容 | 文件名（均有 `.tex` 和 `.pdf`） |
| --- | --- | --- | --- |
| 3-1 | 3 章引言 | 框架层与实现层的对应关系 | `fig-3-1-module-map` |
| 3-2 | 3.2 | A\* 一轮扩展中 Open / Closed 的读写 | `fig-3-2-frontier-cycle` |
| 3-3 | 3.3.1 | 一次移动只改变一枚将牌的距离贡献 | `fig-3-3-manhattan-delta` |
| 3-4 | 3.3.3 | 抽象状态的变进制编码与 `byte[]` 下标 | `fig-3-4-pdb-ranking` |
| 3-5 | 3.3.3 | 0-1 BFS 建表、哨兵约定与磁盘缓存 | `fig-3-5-pdb-zerone-bfs` |
| 3-6 | 3.5 | IDA\* 的阈值迭代与深度优先三条出口 | `fig-3-6-ida-flow` |

第一部分图号为 1-1 至 1-5；第三部分为 3-1 至 3-6；第五部分独立从 5-1 起计数。重置时同步设置不同的超链接标识，避免跳转到其他章节的同序号图片。第五部分已有截图保存在 `visualFigures/`。

## 编辑与格式

封面内容只在 `main.tex` 中修改：学年、学期各一个宏，五组姓名与学号各两个宏。某组姓名、学号都留空时整行跳过；已填的身份信息和其余占位不自动修改。

`TemplateSection` 等命令只登记目录与链接，不打印标题。第一部分由 `section-one.tex`、第四部分由 `section-four.tex` 独立维护，入口均在 `body.tex`；第二、五部分继续在 `body.tex` 中维护。补写第三部分时，应同时放置可见标题，并重新编译检查目录页码。第三部分目前只有目录锚点，不能据此认为已有正文。

第四部分使用教师规定的4.1、4.2、4.3及两个三级标题，解释单位步长的`g(n)`、三种`h(n)`和标准规则`f(n)=g(n)+h(n)`，并分析Open、A*/IDA*、启发函数及困难题冷热缓存。六张表的可编辑源在`section4Tables/`，可用其中的`build_tables.py`从现有CSV重新生成；脚本不运行搜索。采用的图片在`section4Figures/approved/`，03按用户明确接受的版本使用，02的两个原像素面板上下排列以便阅读。数据源、审查记录与后续版面核验分开保存，不把本轮写作称为重新实验。

2026-10-26的第四部分成稿见[section-four.tex](section-four.tex)，共六张表、九幅图；本次PDF中位于物理第14—25页（正文页码11—22，与前后章节共享首尾页）。已完成三轮XeLaTeX编译、目录/交叉引用检查、逐页图像审查和24份历史解路径的合法回放核对，未重跑搜索。核验记录见[qa-section-four-20261026.json](qa-section-four-20261026.json)。第一、二、五部分及总结、参考文献的原文字源保持不变；第三部分仍待组员填写，不应把当前报告称为全篇完成。

按用户要求，当前修订版已去除第四部分正文、表格和配图中反复出现的运行日期，以及末尾“数据出处”段落；原始数据与复现信息继续保留。当前PDF共33页，核验见[qa-section-four-style-20261026.json](qa-section-four-style-20261026.json)。图04、05、06采用另存的`-report.png`裁剪版，原图未覆盖；实验数值、公式、重复次数与冷热缓存条件保持不变。

封面第一组姓名、学号已按用户要求填写，其他组的信息保持原样。重新编译与发布文件检查见[qa-chapter4-publication-20261026.json](qa-chapter4-publication-20261026.json)。发布内容包括第四部分正文、六张表、实际使用的九幅图及当前PDF；收件原图、未采用图片和本地缓存继续保留在本地。

版式沿用教师模板：纸张 595.3×841.9bp，左右页边距 90bp，上下 72bp；中文宋体，正文西文 Calibri，封面 N-Puzzle 使用 Arial Bold。正文 10.5bp、行距 15.6bp，可见章标题为 12bp 粗体。封面校徽及校名使用原 JPEG，只在插入时裁去每边 2 像素，保持位置与比例；原始素材没有重绘。

## 历史与验证

`NPuzzle-template.pdf` 是最初的三页空白模板预览，保留作格式参照；编译当前报告不会覆盖它。2026-10-12 的 [qa.json](qa.json) 仅记录原模板转换时的检查，不能作为当前报告的验证结论。2026-10-19 第一部分插图与正文整合的实际检查见 [qa-section-one-20261019.json](qa-section-one-20261019.json)。

本目录与 `report/NPuzzle研究报告.docx`、其旧 PDF 及 `report/正文.md` 是独立维护的版本，本次没有同步重写旧报告。教师原件、编译缓存和临时预览不纳入此次修改。素材与第三方许可边界见[第三方声明](../../THIRD_PARTY_NOTICES.md)。

## 既有待办

- 第五部分的 TSV 字段说明在补写第三部分后可统一，避免重复。
- Java 注释与编码调整属于后续代码任务，必须遵守教师文件原字节保护要求。
- 需要时补充分工安排；未提供的身份信息继续保留占位。

## 第二章合并（2026-10-19）

合入 PR #1 的 2.1 至 2.4.3 正文：游戏与状态图建模、任意目标的奇偶可解性判据，以及 Manhattan、Misplaced、加性 PDB 的可采纳性和一致性证明。保留原作者正文，正式入口统一为 `main.tex`，第一章仍由 `section-one.tex` 引入；此前单文件预览的占位图配置不作为整合报告入口。

合并后重新构建的 `main.pdf` 同时包含第一、二、五章。`NPuzzle-report-v2.pdf` 保留为原 PR 的第二、五章历史预览，不能替代当前成稿；`NPuzzle-template.pdf` 保持最初三页空白模板，避免覆盖历史格式参照。

本次合并后的 22 页成稿及原文保留、图号、目录、公式例值和逐页渲染检查见 [qa-merged-chapters-20261019.json](qa-merged-chapters-20261019.json)。此前第一章的 QA 记录仅对应当时 19 页版本。
