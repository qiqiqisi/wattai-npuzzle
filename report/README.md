# 研究报告

[可编辑 DOCX](NPuzzle研究报告.docx) · [PDF](NPuzzle研究报告.pdf) · [正文源稿](正文.md)

采用本地教师参考模板的封面与 A4 格式，重建正常目录并补齐正文。姓名、学号、学期均为 `待填写`。数据来自 [data_sources.json](data_sources.json)，逐输入哈希见 [data_hashes.json](data_hashes.json)。CSV、计时和路径证据入口见 [INDEX](../results/INDEX.md)。GUI 人工验收尚未完成。

本轮用 Python 3 的 `python-docx` 生成，以 Microsoft Word 更新目录、导出 PDF，再逐页检查 PNG。打包 LibreOffice 不可用，使用 Word 的事实记录在本轮渲染日志中。

重新生成需要只读的 `teacher_reference/NPuzzle求解研究报告.docx`（原件不随仓库上传）及 `python-docx`：

```powershell
python tools/build_report.py
powershell -NoProfile -ExecutionPolicy Bypass -File tools/render_report.ps1 -InputDocx report/NPuzzle研究报告.docx -OutputPdf report/NPuzzle研究报告.pdf -UpdateFields
```

填写封面后，应重新导出 PDF 并核对目录与页码。构建脚本不会运行搜索或更改历史 CSV。
