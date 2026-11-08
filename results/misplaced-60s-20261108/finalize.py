"""Validate the completed six-run archive and write its descriptive analysis."""
from pathlib import Path
import csv
import hashlib
import json
import re
import shutil
import statistics

S = Path(__file__).resolve().parent
R = S.parents[1]


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def load(p):
    return json.loads(p.read_text(encoding='utf-8'))


def write(p, x):
    with p.open('x', encoding='utf-8') as f:
        json.dump(x, f, ensure_ascii=False, indent=2)
        f.write('\n')


def replay(p, row):
    lines = p.read_text(encoding='utf-8').splitlines()
    h = dict(x.split('\t', 1) for x in lines[:9])
    values = list(map(int, (R / 'resources/problems3.txt').read_text().splitlines()[3].split()))
    at, goal = values[1:17], values[17:]
    assert h['algorithm'] == row['algorithm'] and h['heuristic'] == row['heuristic']
    assert list(map(int, h['goal'].split())) == goal
    steps = [x.split('\t') for x in lines[10:]]
    assert steps[0] == ['0', 'START', ' '.join(map(str, at))]
    for i, (idx, move, text) in enumerate(steps[1:], 1):
        child = list(map(int, text.split())); a, b = at.index(0), child.index(0)
        assert int(idx) == i and sorted(child) == list(range(16))
        assert abs(a//4-b//4)+abs(a%4-b%4) == 1
        assert a + {'UP':-4, 'DOWN':4, 'LEFT':-1, 'RIGHT':1}[move] == b
        expected = at.copy(); expected[a], expected[b] = expected[b], expected[a]
        assert expected == child
        at = child
    assert at == goal and len(steps)-1 == int(row['solution_length']) == int(h['solution_length'])
    return {'path':p.relative_to(R).as_posix(), 'sha256':sha(p), 'length':len(steps)-1, 'status':'PASS'}


rows = [load(p) for p in sorted((S / 'runs').glob('*/result.json'))]
assert len(rows) == 6
assert {(r['algorithm'],r['repetition']) for r in rows} == {(a,i) for a in ['ASTAR','IDA'] for i in range(1,4)}
assert load(S / 'integrity.json')['status'] == 'PASS'
with (S / 'comparison.csv').open(encoding='utf-8', newline='') as f:
    table = list(csv.DictReader(f))
assert len(table) == 6
for row in rows:
    d = R / row['run_directory']
    for name in ['configuration.json','result.json','worker.log','parent.txt','preparation.properties',
                 'jvm-gc-startup.log','process-memory.csv']:
        assert (d / name).is_file(), (d,name)
    c = load(d / 'configuration.json')
    assert c['input_index'] == 3 and c['mode'] == 'BASIC' and c['transposition_limit'] == 0
    assert c['heap_argument'] == '-Xmx1024m' and c['fast'] and c['timeout_s'] == 60
    assert c['input_sha256'] == sha(R / 'resources/problems3.txt')
    assert row['memory_samples'] > 0 and row['jvm_create_vm_ms'] is not None
    if row['status'] != 'SOLVED':
        for field in ['elapsed_ms','generated','expanded','solution_length']:
            assert row[field] is None
        assert not (d / 'solution.tsv').exists()
    if row['status'] == 'OOM':
        assert 'java.lang.OutOfMemoryError: Java heap space' in (d / 'worker.log').read_text()
        assert row['exit_code'] != 0 and not row['parent_deadline_fired']
    if row['status'] == 'TIMEOUT':
        assert row['parent_deadline_fired'] and row['parent_ms'] >= 60000
        snap = R / row['last_snapshot_file']; assert snap.is_file()
        assert 0 < float(row['last_snapshot_search_ms']) < row['parent_ms']
        assert row['partial_expanded'] in snap.read_text()
for row in table:
    if row['status'] != 'SOLVED':
        assert all(row[f] == '' for f in ['elapsed_ms','generated','expanded','solution_length'])

# Keep the actual pre-run metadata intact, then correct its incomplete description.
shutil.copyfile(S / 'experiment.json', S / 'experiment-pre-run.json')
experiment = load(S / 'experiment.json')
experiment['node_counts'] = ('Existing IdAStar calls progress at each threshold, every 16384 expansions subject to a 500ms interval, and on success. '
    'Its worker saves append-only search-thread snapshots. A* has no partial count exporter. Failure final fields stay null; partial IDA counters are lower bounds.')
experiment['metadata_correction'] = 'The pre-run description mentioned threshold snapshots only; source inspection confirms periodic snapshots too. experiment-pre-run.json and raw run configuration.json preserve that earlier wording. No search or raw measurement was edited.'
experiment['diagnostic_overhead'] = 'GC/startuptime logging and parent-side sampling overhead were not calibrated against a separate uninstrumented search. All six runs share them; do not infer exact historical speedups.'
(S / 'experiment.json').write_text(json.dumps(experiment, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

old = load(S / 'historical-comparison.json')
history15 = [x['result'] for x in old['15s']]
for row in history15:
    assert row['status'] == 'TIMEOUT'
old_dir = R / 'results/optimization-20261106-20261106T115705068678Z/runs'
manhattan = [load(p) for p in old_dir.glob('*/result.json')]
manhattan = [r for r in manhattan if r['dataset']=='ordinary4' and r['heuristic']=='MANHATTAN']
assert len(manhattan) == 6
replays = [replay(R / r['solution_file'],r) for r in manhattan]
write(S / 'historical-manhattan-replay.json', {'note':'Only independent replay of existing historical solutions, no new searches.', 'runs':replays})

fmt = lambda x: '未取得' if x is None else f'{float(x):.3f}'
lines = [
    '# 普通4×4 MISPLACED：60秒诊断实验', '',
    '本轮六次运行已完成采集。以下逐条保留成功、超时或异常结果，不把截止时间当作完成搜索的耗时。60秒是本轮统一诊断上限，不是教师第二阶段的明确时限。', '',
    '## 配置和可比范围', '',
    f"基于 main 提交 `{experiment['base_commit']}`，Java 19、`-Xmx1024m`、`fast=true`、IDA* `BASIC / limit=0`，原 `resources/problems3.txt` 零起索引3，MISPLACED。每种算法三次，各用新的独立JVM，无同JVM预热；按 A1、I1、I2、A2、A3、I3 顺序串行运行。题目SHA-256为 `"+experiment['input_sha256']+'`。', '',
    '父端从创建工作进程前计时，60秒后强制终止并回收同一个JVM；不额外给予60秒纯搜索预算。这与15秒历史口径一致。启动、准备、搜索以及可能的回放/导出都占该预算，终止/回收使实际父端总时长可略超60秒。`elapsed_ms`只用于完整成功搜索；失败时为空。', '',
    '逐文件比较确认11月6日源清单中的所有Java源码和测试与本轮相同；输入文件哈希、JDK版本和主要算法参数一致。额外诊断只有统一GC/VM启动日志与父端每200ms进程内存/CPU采样，其性能开销未通过额外搜索校准，不能据此计算严格历史加速比。10月10日与本轮的IDA*源码不同，详见 historical-comparison.json。', '',
    '## 六次完整结果', '',
    '所有行 heuristic=MISPLACED、timeout_s=60。空字段表示未取得，不是0；解长为空表示没有完整合法解。', '',
    '| 算法 | 重复 | 状态 | elapsed_ms | parent_ms | generated（最终） | expanded（最终） | 解长 | exit_code |',
    '| --- | --- | --- | --- | --- | --- | --- | --- | --- |']
for r in rows:
    lines.append(f"| {r['algorithm']} | {r['repetition']} | {r['status']} | {fmt(r['elapsed_ms'])} | {r['parent_ms']:.3f} | {r['generated'] if r['generated'] is not None else '未取得'} | {r['expanded'] if r['expanded'] is not None else '未取得'} | {r['solution_length'] if r['solution_length'] is not None else '无完整解'} | {r['exit_code']} |")
lines += ['', '## 超时前的节点进度', '',
    'IDA*既在阈值边界，也在扩展过程中每16384次扩展检查约0.5秒间隔，并在搜索线程保存一致快照。下表是最后一份可读快照，计数包含此前各轮重复扩展，不是不同状态数，也不是被终止瞬间的最终值。A*现有运行器不导出异常前节点数，因此无法量化其扩展量，不能填写0或据此给出两算法扩展量比值。', '',
    '| 算法/重复 | 最后快照搜索ms | generated下界 | expanded下界 | 当时阈值 |',
    '| --- | --- | --- | --- | --- |']
for r in rows:
    lines.append(f"| {r['algorithm']}/{r['repetition']} | {fmt(r['last_snapshot_search_ms'])} | {r.get('partial_generated') or '未取得'} | {r.get('partial_expanded') or '未取得'} | {r.get('partial_bound') or '未取得'} |")
lines += ['', '## 启动、GC和内存', '',
    'Create VM来自HotSpot的startuptime日志，仅测量VM创建阶段；父端观察到SEARCH包括进程启动、类加载和准备，以及约200ms轮询间隔和调度延迟，不能当作纯启动。GC暂停总和仅含完整日志行，不等于GC并发线程CPU总量。操作系统峰值工作集和采样private bytes是整个JVM进程的指标，不能称Java堆峰值、算法独占内存或存活结点内存。', '',
    '| 算法/重复 | Create VM ms | 父端观察SEARCH ms | OS峰值工作集 MiB | 采样最大private MiB | 已记录GC暂停次数 | 暂停总ms |',
    '| --- | --- | --- | --- | --- | --- | --- |']
for r in rows:
    lines.append(f"| {r['algorithm']}/{r['repetition']} | {fmt(r['jvm_create_vm_ms'])} | {fmt(r['parent_to_search_observed_ms'])} | {r['os_peak_working_set_bytes']/2**20:.2f} | {r['sampled_max_private_bytes']/2**20:.2f} | {r['logged_gc_pause_count']} | {r['logged_gc_pause_ms']:.3f} |")
lines += ['', 'A*保留Open/Closed及路径关联结点；本次OOM的具体发生位置以worker.log为准。IDA* BASIC保留递归路径和路径去环集合，没有启用跨路径TT；小的存活路径仍可伴随大量对象分配及频繁GC。两者结构差异由源码支持，实际内存差异由上表支持，不用1024MB上限代替实测。', '',
    '## 成功次数与三次波动', '']
for a in ['ASTAR','IDA']:
    group = [r for r in rows if r['algorithm']==a]
    counts = {status:sum(r['status']==status for r in group) for status in sorted({r['status'] for r in group})}
    p = [r['parent_ms'] for r in group]
    lines.append(f"- {a}：成功{sum(r['status']=='SOLVED' for r in group)}/3，状态分布{counts}；父端总时长范围{min(p):.3f}—{max(p):.3f}ms，中位数{statistics.median(p):.3f}ms。")
    if all(r['status']=='TIMEOUT' for r in group):
        partial = [int(r['partial_expanded']) for r in group]
        lines.append(f"  截止前扩展下界范围{min(partial):,}—{max(partial):,}，极差/最小值为{(max(partial)/min(partial)-1)*100:.2f}%；快照时间也略有差别，不能当作完整求解耗时的波动。父端时长受固定截止限制，接近60秒不意味着搜索性能稳定。")
lines += ['', '只有三次且未控制温度、调度等机器状态，仅作描述性比较，不给显著性或总体稳定性结论。若所有运行失败，则无法计算成功搜索的均值/中位数，也不能据此判断最终谁先求出解。', '',
    '## 与10秒、15秒历史记录对照', '',
    '| 日期/上限 | 算法 | 重复 | 状态 | 父端ms | 完成搜索ms |',
    '| --- | --- | --- | --- | --- | --- |']
for r in old['10s']:
    lines.append(f"| 2026-10-10 / 10s | {r['algorithm']} | {r['repetition']} | {r['status']} | {float(r['parent_ms']):.3f} | 未取得 |")
for r in sorted(history15,key=lambda x:(x['algorithm'],x['repetition'])):
    lines.append(f"| 2026-11-06 / 15s | {r['algorithm']} | {r['repetition']} | {r['status']} | {r['parent_ms']:.3f} | 未取得 |")
lines += ['', '10秒两条、15秒六条均为TIMEOUT，不表明题目无解。本轮延长父端上限后仍无成功记录；若出现OOM，意味着在当次运行条件下先遇到了堆容量边界，不能继续称为TIMEOUT。历史15秒未记录OOM，不能倒推出当时第几秒必然OOM；即使源码与硬件标称一致，也没有证据将状态差异唯一归因于时限、GC日志或某一系统因素。10月和11月IDA*实现不同，禁止将它们直接拼接为纯时限曲线。', '',
    '## MISPLACED瓶颈的证据与边界', '',
    '该初态MISPLACED值为15（不含空位），而现有同题Manhattan成功路径为52步；本轮另用独立Python动作交换回放核验了六条历史Manhattan路径，全部合法。历史成功记录不是本次新搜索。', '']
for a in ['ASTAR','IDA']:
    group = [r for r in manhattan if r['algorithm']==a]
    lines.append(f"- 11月6日同源码 {a}+MANHATTAN：3/3 SOLVED，搜索中位数{statistics.median(float(r['elapsed_ms']) for r in group):.3f}ms，三次expanded均为{group[0]['expanded']}，解长52。")
lines += ['', '结合MISPLACED反复未求解、IDA*大量重复扩展，以及A*的堆资源边界，证据支持“当前实现、1024MB及该实例上，MISPLACED存在明显的实际性能瓶颈”。A*的具体资源瓶颈由OOM直接支持；弱启发式导致更大搜索范围的解释与算法结构及历史同题对照一致，但本轮没有新的其他启发函数消融，不能量化各因素独立贡献，也不能推广所有15-Puzzle。', '',
    '## 归档和核验', '',
    'comparison.csv保留全部六次及必需字段。runs/保留每次完整stdout/stderr、preparation.properties、所有实际生成的snapshot properties、GC/启动日志、父端记录、配置、进程采样和result.json。失败运行没有生成metrics.properties或solution.tsv时，不伪造这些工作进程输出；parent.txt和result.json保留实际状态。solutions/只保存本次成功路径；若成功数为0，该目录仅含明确说明。', '',
    '编译Java19及四项相关回归通过，watchdog/为独立的非搜索延迟探针，核对受监控PID就是JVM、截止后被终止并回收，延迟标记未出现。integrity.json记录旧结果和源码/测试/工具/题目/正式报告逐文件未变；validation.json与sha256-manifest.json核对归档。JVM原始快照在properties中自动写入系统日期，保留原文。', '',
    '本实验仅新增独立数据归档和results/INDEX.md导航，不修改正式报告、教师代码、搜索算法或旧实验。GitHub提交及远端逐字节重读核验在最终交付时另报。']
(S / 'analysis.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
successes = sum(r['status']=='SOLVED' for r in rows)
(S / 'solutions/README.md').write_text(f'本次六次运行中成功{successes}次。'+('完整路径为本目录TSV，已逐步独立合法性回放。' if successes else '没有完整解路径；本目录不放历史路径或伪造路径。')+'\n',encoding='utf-8')
index = R / 'results/INDEX.md'
with index.open('a',encoding='utf-8') as f:
    f.write('\n\n## 2026-11-08 普通4×4 MISPLACED 60秒诊断\n\n[六次完整比较表](misplaced-60s-20261108/comparison.csv) · [分析及口径](misplaced-60s-20261108/analysis.md) · [环境与配置](misplaced-60s-20261108/experiment.json) · [完整原始记录](misplaced-60s-20261108/runs/) · [完整性核验](misplaced-60s-20261108/validation.json)。60秒为本轮父端诊断上限，不是教师第二阶段时限；OOM、TIMEOUT及缺失指标分别保留。旧记录和正式报告未修改。\n')
write(S / 'validation.json', {'status':'PASS', 'run_count':6, 'successful_runs':successes,
    'all_required_csv_columns_present':True, 'failure_final_metrics_blank':True, 'all_raw_files_present':True,
    'historical_manhattan_solutions_independently_replayed':len(replays),
    'raw_logs_properties_parent_preserved':True, 'startup_and_process_memory_present_all_runs':True,
    'new_experimental_runs':6, 'additional_experimental_runs':0, 'regression_tests_may_run_search_fixtures':True, 'watchdog_fixture_is_not_search':True,
    'note':'See integrity.json for unchanged protected and historical files; hashes include every archive file except this manifest itself.'})
files = {p.relative_to(S).as_posix():sha(p) for p in sorted(S.rglob('*')) if p.is_file()}
write(S / 'sha256-manifest.json',files)
print(json.dumps({'status':'PASS','archive_file_count':len(files)+1,'successes':successes,
    'results':[(r['algorithm'],r['repetition'],r['status'],r['parent_ms'],r.get('partial_expanded')) for r in rows]},ensure_ascii=False))
