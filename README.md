# WattAi — N-Puzzle

Java 19 课程工程，包含 A*、IDA*、加性模式数据库和 Swing 解路径播放器。
这是教师框架上的学生实现，不是纯净教师原版；教师原始 ZIP 与公开授权仍未确认，因此仓库保持私有。

## 目录

| 路径 | 内容 |
|---|---|
| `src/core` | 课程通用框架及已有兼容扩展 |
| `src/stud` | 寻路示例、N-Puzzle 状态、求解器、基准与播放器 |
| `src/algs4` | 原第三方工具，GPL 声明保留 |
| `resources` | 四份原始题目和两份已有阶段输入，逐字节保留 |
| `tests` | 六套正确性测试，包含自定义目标、碰撞、路径和播放控制 |
| `tools` | 复现入口、完整加性建表对照、PDB 查询及 JFR 诊断 |
| `results` | 原始 CSV、成功/失败日志和解路径；不含文档加工缓存 |

DOCX 生成/排版代码、会话脚本、内部审计目录、IDE、class、PDB、JFR 和构建缓存不进入仓库。

## 构建与测试

安装 JDK 19 或更高版本，将 java/javac 加入 PATH。在项目根目录的 PowerShell 执行：

```powershell
& .\tools\reproduce.ps1
```

按 Java 19 兼容目标编译并运行六套测试，不依赖 Python、Office 或原 out 目录。

## 最难 15-Puzzle

```powershell
& .\tools\reproduce.ps1 -RunHard
```

每次创建独立冷缓存，使用 BASIC IDA*、fast=true、555T、1024m heap 上限和 60s 父超时。
原输入是 `resources/problems3.txt` 第 10 行，零起索引 9。父进程退出 0 不等于解出，必须查看新 CSV 的 status。

| 原实测运行 | 状态 | 解长 | 父端到端时间 |
|---|---|---:|---:|
| 2026-10-08 冻结版，无 PDB 文件 | SOLVED | 70 | 32.568s |
| 同版加载已有缓存 | SOLVED | 70 | 19.512s |
| 2026-10-09 独立发布包冷运行 | SOLVED | 70 | 34.097s |

这些是本机单实例结果，包含建库/加载、搜索、回放和导出。历史 TIMEOUT/OOM/FAIL 保留，不能视为无解或伪造缺失指标。
原始证据与限制见 [results/README.md](results/README.md)。

## 路径验证与播放器

```powershell
java -cp build-test stud.g01.visual.PuzzleVisualizer --validate results/solutions/2026-10-09T07-02-51.918489200Z-problems3_10-IDA-DISJOINT_PATTERN.tsv
java -cp build-test stud.g01.visual.PuzzleVisualizer results/solutions/2026-10-09T07-02-51.918489200Z-problems3_10-IDA-DISJOINT_PATTERN.tsv
```

自动回放和控件模型测试通过；4×4 窗口/按钮完整人工验收仍待完成，不能据自动测试声称 GUI 已全部验收。

## 小规模完整加性建表对照

```powershell
javac -encoding UTF-8 --release 19 -cp build-test -d build-test tools/AdditivePdbBuildComparison.java
java -Xmx512m -cp build-test AdditivePdbBuildComparison results/my-additive-comparison
```

只比较 3×3 同一抽象图上输出相同完整距离表的 BFS 与 BiBFS，不把历史单点查询当作完整建库。

## 许可与边界

第三方声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [GPL 正文](COPYING-GPL-3.0.txt)。
教师框架历史合规、公开许可、报告真实封面和 GUI 人工仍未齐全；上传不代表课程实验全部验收。
