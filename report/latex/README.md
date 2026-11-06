# N-Puzzle 研究报告 LaTeX 版

[当前报告](main.pdf) · [封面与入口](main.tex) · [第一章](section-one.tex) · [第二、三、五章与总结](body.tex) · [实验分析](section-four.tex)

报告包含 A* 与 IDA* 原理、N-Puzzle 建模、启发函数与数据结构、同题实验及文件驱动可视化。第四章采用当前实验记录，分别列出算法与启发函数、困难题模式与容量、缓存条件，以及 fast 和快照控制。表内时间、成功次数及失败状态均从原始记录核对，截图只用于说明界面和回放。

## 编译

安装 XeLaTeX 及宋体、Calibri、Arial 后，在本目录执行：

```powershell
.\build.ps1
```

脚本连续编译三次，输出 `main.pdf`；辅助文件与日志位于根目录下被忽略的 `generated/latex-template-build/`。修改第一、三章 TikZ 源图时执行 ` .\build.ps1 -RebuildFigures`。

## 数据与图表

当前实验来源由 [current-sources.json](section4Tables/current-sources.json) 定位。六张数据表为可编辑 LaTeX，三张数值图为矢量 PDF，可使用该索引中的同一运行目录重建：

```powershell
python section4Tables/build_current_tables.py
```

指定另一个完整且已核验的运行目录时，使用 `--suite results/<运行目录>`；路径相对于仓库根目录。表格仅取 SOLVED 的时间中位数，并同时显示成功次数；未完成的搜索不填成功指标。`section4Tables/build_tables.py` 对应独立留存的另一组表，不是当前第四章的数据入口。

## 格式与检查

封面信息在 `main.tex` 中维护。中文宋体，正文 10.5bp、行距 15.6bp，页边距和封面图沿用教师模板。章节图表独立编号，并设置不同超链接标识。第一章五幅图与第三章六幅图保留可编辑 TikZ；第五章使用真实界面截图。

本次编译与页图检查见 [qa-current-experiments-20261107.json](qa-current-experiments-20261107.json)。目录中其他带日期的 QA 只描述对应时点，不作为当前稿的检查结论。`NPuzzle-template.pdf` 是三页空白格式参照，旧预览与独立 DOCX/PDF 不覆盖当前 `main.pdf`。
