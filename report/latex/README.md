# N-Puzzle 研究报告 LaTeX 版

[当前报告](main.pdf) · [主文件与封面](main.tex) · [第一部分正文](section-one.tex) · [其余正文](body.tex) · [版式参数](template.sty)

当前版本已填写第一部分“启发式算法综述”和第五部分“解路径的可视化”，并保留既有总结与参考文献。第二至四部分目前仍是目录锚点，尚未填写可见正文，因此这不是全篇完成的报告。原目录的章节名称、编号、“不在位将牌”和“.总结及展望”等用词保留。

第一部分包含 A* 流程、可采纳性与一致性条件、针对当前不重开 Closed 实现的最优性证明、重复状态及数据结构代价、IDA* 阈值更新与 BASIC 模式证明。五张配图按论证顺序穿插，图前引出问题、图后说明结论，示意数值不作为实验结果。

## 编译

Windows 安装 XeLaTeX 及宋体、Calibri、Arial 后，在项目根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File report/latex/build.ps1
```

脚本连续编译三次以更新目录与交叉引用，辅助文件和日志写入被忽略的 `generated/latex-template-build/`，最终输出 `report/latex/main.pdf`。最后一轮若仍有未解析引用、重复目标标识或缺失字符，则停止交付。脚本不依赖 Word，也不运行 Java 搜索或实验。

修改第一部分图形源文件后，可以同时重建五张矢量图：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File report/latex/build.ps1 -RebuildFigures
```

`section1Figures/` 保留每张图的独立 TikZ 源文件与矢量 PDF；普通编译直接插入 PDF，不必重新绘图。对应关系如下：

| 图号 | 所在小节 | 内容 | 文件名（均有 `.tex` 和 `.pdf`） |
| --- | --- | --- | --- |
| 1-1 | 1.2.1 | A* 的 Open / Closed 流程 | `fig-1-1-astar-flow` |
| 1-2 | 1.2.2 | 最优路径边界结点的估值关系 | `fig-1-2-optimality-frontier` |
| 1-3 | 1.3.1 | 同一状态的路径代价与父链更新 | `fig-1-3-duplicate-state` |
| 1-4 | 1.3.2 | 可采纳性与一致性的区别 | `fig-1-4-consistency` |
| 1-5 | 1.4 | IDA* 的阈值递增 | `fig-1-5-ida-threshold` |

第一部分图号为 1-1 至 1-5；第五部分独立从 5-1 起计数。重置时同步设置不同的超链接标识，避免跳转到其他章节的同序号图片。第五部分已有截图保存在 `visualFigures/`。

## 编辑与格式

封面内容只在 `main.tex` 中修改：学年、学期各一个宏，五组姓名与学号各两个宏。某组姓名、学号都留空时整行跳过；已填的身份信息和其余占位不自动修改。

`TemplateSection` 等命令只登记目录与链接，不打印标题。第一部分由 `section-one.tex` 独立维护；其余部分在 `body.tex` 中填写。补写第二至四部分时，应同时放置可见标题，并重新编译检查目录页码。当前空置章节的目录仍指向第五部分开始页，不能据此认为已有正文。

版式沿用教师模板：纸张 595.3×841.9bp，左右页边距 90bp，上下 72bp；中文宋体，正文西文 Calibri，封面 N-Puzzle 使用 Arial Bold。正文 10.5bp、行距 15.6bp，可见章标题为 12bp 粗体。封面校徽及校名使用原 JPEG，只在插入时裁去每边 2 像素，保持位置与比例；原始素材没有重绘。

## 历史与验证

`NPuzzle-template.pdf` 是最初的三页空白模板预览，保留作格式参照；编译当前报告不会覆盖它。2026-10-12 的 [qa.json](qa.json) 仅记录原模板转换时的检查，不能作为当前报告的验证结论。2026-10-19 第一部分插图与正文整合的实际检查见 [qa-section-one-20261019.json](qa-section-one-20261019.json)。

本目录与 `report/NPuzzle研究报告.docx`、其旧 PDF 及 `report/正文.md` 是独立维护的版本，本次没有同步重写旧报告。教师原件、编译缓存和临时预览不纳入此次修改。素材与第三方许可边界见[第三方声明](../../THIRD_PARTY_NOTICES.md)。

## 既有待办

- 第五部分的 TSV 字段说明在补写第三部分后可统一，避免重复。
- Java 注释与编码调整属于后续代码任务，必须遵守教师文件原字节保护要求。
- 需要时补充分工安排；未提供的身份信息继续保留占位。
