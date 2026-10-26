"""Regenerate Chapter 4 tables from retained records; never run a search."""
from pathlib import Path
from collections import defaultdict
from decimal import Decimal
import csv
import hashlib
import json
import statistics

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
AUDIT = ROOT / 'generated/chapter4-authoring-20261026'
AUDIT.mkdir(parents=True, exist_ok=True)

def read_csv(path):
    with (ROOT/path).open(encoding='utf-8-sig', newline='') as handle:
        return list(csv.DictReader(handle))

def value(text):
    return Decimal(text)

def median(rows, field):
    return statistics.median(value(row[field]) for row in rows)

def fmt(number, digits=3):
    return f'{number:.{digits}f}'

def save_table(name, columns, headings, rows):
    # Formatting is local to the chapter; values remain editable text.
    lines = ['% Generated from retained CSV records by build_tables.py.',
             r'\begin{tabular}{' + columns + '}', r'\hline',
             ' & '.join(r'\textbf{' + h + '}' for h in headings) + r' \\', r'\hline']
    lines += [' & '.join(row) + r' \\' for row in rows]
    lines += [r'\hline', r'\end{tabular}', '']
    (OUT/name).write_text('\n'.join(lines), encoding='utf-8')

comparison_path = 'results/compare-20261010T061625055Z/comparison.csv'
frontier_path = 'results/frontier_benchmark.csv'
comparison = read_csv(comparison_path)
frontier = read_csv(frontier_path)
assert len(comparison) == 24 and len(frontier) == 72
assert sum(row['status'] == 'TIMEOUT' for row in comparison) == 2
groups = defaultdict(list)
for row in comparison:
    groups[(row['dataset'], row['algorithm'], row['heuristic'])].append(row)
frontier_groups = defaultdict(list)
for row in frontier:
    frontier_groups[(row['problem_id'], row['heuristic'], row['frontier'])].append(row)
assert len(frontier_groups) == 24 and all(len(rows) == 3 for rows in frontier_groups.values())
assert all(row['status'] == 'SOLVED' for row in frontier)

save_table('conditions.tex', r'p{49bp}p{112bp}p{88bp}p{124bp}',
    ['实验组', '题目', '重复与准备', '配置与上限'], [
    ['Open对照', '6道浅$3\\times3$题', '同一JVM；各组预热1次，再测3次', '两种Open、两种启发；只改变Open实现'],
    ['教师三阶', '教师给出的初态与目标', '每配置3次独立新JVM；无同JVM预热', 'PDB冷构建4-4；保护上限5秒'],
    ['普通四阶', '题目文件第4题', '每配置1次独立新JVM', 'PDB冷构建4-4-4-3；上限10秒'],
    ['困难四阶', '教师困难题，题目文件第10题', '冷、热缓存各1次', 'IDA*、555T；父端上限60秒'],
])

save_table('operations.tex', r'p{64bp}p{86bp}p{105bp}p{118bp}',
    ['操作', '有序列表', '优先队列＋状态索引', '主要代价'], [
    ['查找同一状态', '$O(N)$', '平均$O(1)$', '列表需逐个比对；索引另占空间'],
    ['插入新状态', '$O(N)$', '$O(\\log N)$', '列表即使二分定位，仍需搬移元素'],
    ['取出最优结点', '$O(N)$', '$O(\\log N)$', '列表删除首项；队列调整堆结构'],
    ['替换更优重复结点', '$O(N)$', '$O(N)$', '当前队列仍需查找并删除旧堆项'],
    ['Closed查找与加入', '平均$O(1)$', '平均$O(1)$', '两方案均为HashSet，未单独改变'],
])

rows = []
for number in range(1, 7):
    row = [str(number)]
    for heuristic in ('MISPLACED', 'MANHATTAN'):
        pair = [frontier_groups[(f'frontier_{number}', heuristic, name)]
                for name in ('ListFrontier', 'PqFrontier')]
        assert len({r['nodes_expanded'] for group in pair for r in group}) == 1
        assert {r['solution_length'] for group in pair for r in group} == {'10'}
        row += [fmt(median(group, 'elapsed_ms'), 4) for group in pair]
        row.append(pair[0][0]['nodes_expanded'])
    rows.append(row)
save_table('frontier.tex', 'crrrrrr',
    ['题号', '错位List', '错位Pq', '扩展次数', '曼哈顿List', '曼哈顿Pq', '扩展次数'], rows)

algorithm_labels = {'ASTAR': 'A*', 'IDA': 'IDA*'}
heuristic_labels = {'MISPLACED': '错位数', 'MANHATTAN': '曼哈顿', 'DISJOINT_PATTERN': 'PDB'}
three_rows, four_rows, summaries = [], [], []
for dataset in ('teacher3', 'teacher4'):
    for algorithm in ('ASTAR', 'IDA'):
        for heuristic in ('MISPLACED', 'MANHATTAN', 'DISJOINT_PATTERN'):
            selected = groups[(dataset, algorithm, heuristic)]
            assert len(selected) == (3 if dataset == 'teacher3' else 1)
            base = [algorithm_labels[algorithm], heuristic_labels[heuristic]]
            if dataset == 'teacher3':
                assert {r['status'] for r in selected} == {'SOLVED'}
                assert {r['solution_length'] for r in selected} == {'16'}
                assert len({r['expanded'] for r in selected}) == 1
                times = [value(r['elapsed_ms']) for r in selected]
                three_rows.append(base + [fmt(median(selected, 'elapsed_ms')),
                    fmt(min(times)) + '--' + fmt(max(times)),
                    fmt(median(selected, 'parent_ms')), selected[0]['expanded']])
                summaries.append(dict(dataset=dataset, algorithm=algorithm, heuristic=heuristic,
                    search_median=str(median(selected, 'elapsed_ms')),
                    search_min=str(min(times)), search_max=str(max(times)),
                    parent_median=str(median(selected, 'parent_ms')), expanded=selected[0]['expanded']))
            else:
                r = selected[0]
                solved = r['status'] == 'SOLVED'
                assert not solved or r['solution_length'] == '52'
                four_rows.append(base + [r['status'], r['solution_length'] if solved else '---',
                    fmt(value(r['elapsed_ms'])) if solved else '---', fmt(value(r['parent_ms'])),
                    r['pdb_build_ms'] if solved else '---', r['expanded'] if solved else '---'])
                summaries.append(dict(dataset=dataset, algorithm=algorithm, heuristic=heuristic,
                    status=r['status'], search=r['elapsed_ms'], parent=r['parent_ms'], expanded=r['expanded']))
save_table('teacher3.tex', 'llrrrr',
    ['算法', '启发函数', '搜索中位数', '搜索范围', '父端中位数', '扩展次数'], three_rows)
save_table('ordinary4.tex', 'lllrrrrr',
    ['算法', '启发', '状态', '步数', '搜索', '父端', '建表', '扩展次数'], four_rows)

hard_rows, hard_summary = [], []
for suffix, label in (('', '冷缓存'), ('-warm', '热缓存')):
    directory = ROOT / ('results/run-20261011T071242033Z'+suffix)
    with (directory/'stage3.csv').open(encoding='utf-8-sig', newline='') as handle:
        r = list(csv.DictReader(handle))[0]
    log = next(directory.glob('*-parent.txt')).read_text(encoding='utf-8-sig')
    parent = value(next(line.split('=', 1)[1] for line in log.splitlines() if line.startswith('end_to_end_ms=')))
    other = parent-value(r['elapsed_ms'])-value(r['pdb_build_ms'])-value(r['pdb_load_ms'])
    assert r['status'] == 'SOLVED' and r['solution_length'] == '70'
    assert r['expanded'] == '48759798' and r['iterations'] == '8'
    hard_rows.append([label, r['pdb_build_ms'], r['pdb_load_ms'], fmt(value(r['elapsed_ms'])),
                      fmt(parent), fmt(other)])
    hard_summary.append(dict(cache=label, parent=str(parent), search=r['elapsed_ms'], other=str(other)))
save_table('hard.tex', 'lrrrrr', ['缓存', '建表', '加载', '搜索', '父端总时间', '其余开销'], hard_rows)

derived = {}
for algorithm in ('ASTAR', 'IDA'):
    baseline = groups[('teacher4', algorithm, 'MANHATTAN')][0]
    candidate = groups[('teacher4', algorithm, 'DISJOINT_PATTERN')][0]
    derived[algorithm] = dict(search_ratio=str(value(baseline['elapsed_ms'])/value(candidate['elapsed_ms'])),
        expanded_reduction_percent=str((1-value(candidate['expanded'])/value(baseline['expanded']))*100))

manifest = json.loads((ROOT/'results/compare-20261010T061625055Z/source-manifest.json').read_text(encoding='utf-8-sig'))
differences = [item['path'] for item in manifest
               if hashlib.sha256((ROOT/item['path']).read_bytes()).hexdigest().upper() != item['sha256'].upper()]
assert all('/visual/' in path for path in differences), differences
audit = dict(derived_from_existing_records=True, new_search='NOT_RUN',
    comparison_rows=24, frontier_rows=72, timeout_rows=2,
    summaries=summaries, hard=hard_summary, derived=derived,
    historical_manifest_current_source_differences=differences,
    inputs={path: hashlib.sha256((ROOT/path).read_bytes()).hexdigest().upper()
            for path in (comparison_path, frontier_path, 'resources/problems_teacher_stage1.txt', 'resources/problems3.txt')})
(AUDIT/'data-check.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print('CHAPTER4_TABLES: 6 editable tables; 24 comparison rows, 72 frontier rows, 2 hard rows checked.')
print('Historical Java manifest differences:', differences)
