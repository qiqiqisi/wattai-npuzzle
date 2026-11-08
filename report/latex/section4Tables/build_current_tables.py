"""Build report tables and vector charts from the fixed experiment records."""
import json
import statistics
import argparse
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
FIG = OUT.parent / 'section4Figures' / 'current'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--suite', type=Path)
args = parser.parse_args()
source_index = OUT / 'current-sources.json'
suite_name = args.suite or Path(json.loads(source_index.read_text(encoding='utf-8'))['suite'])
SUITE = ROOT / suite_name
ROWS = [json.loads(p.read_text(encoding='utf-8'))
        for p in sorted((SUITE / 'runs').glob('*/result.json'))]
assert len(ROWS) == 80
FIG.mkdir(parents=True, exist_ok=True)


def selected(**filters):
    return [r for r in ROWS if all(r[k] == v for k, v in filters.items())]


def med(rows, field):
    values = [float(r[field]) for r in rows if r['status'] == 'SOLVED']
    return statistics.median(values) if values else None


def fmt(v, digits=3):
    return '---' if v is None else f'{v:.{digits}f}'


def table(name, columns, headers, rows):
    text = ['% Generated from the experiment result.json files.',
            r'\begin{tabular}{' + columns + '}', r'\toprule',
            ' & '.join(r'\textbf{' + h + '}' for h in headers) + r' \\', r'\midrule']
    text.extend(' & '.join(row) + r' \\' for row in rows)
    text += [r'\bottomrule', r'\end{tabular}', '']
    (OUT / ('current-' + name + '.tex')).write_text('\n'.join(text), encoding='utf-8')


ALGS = {'ASTAR': 'A*', 'IDA': 'IDA*'}
HEURS = [('MISPLACED', '错位数'), ('MANHATTAN', '曼哈顿'),
         ('MANHATTAN_LC', 'LC'), ('DISJOINT_PATTERN', 'PDB')]
for dataset in ['teacher3', 'ordinary4']:
    data = []
    for a in ALGS:
        for h, label in HEURS:
            rs = selected(group='heuristic', dataset=dataset, algorithm=a, heuristic=h)
            assert len(rs) == 3
            ok = sum(r['status'] == 'SOLVED' for r in rs)
            outcomes = {r['status'] for r in rs}
            outcome = f'{ok}/3' if outcomes == {'SOLVED'} else (
                f'{ok}/3 ' + ('OOM' if outcomes == {'OOM'} else 'TO' if outcomes == {'TIMEOUT'} else '混合'))
            data.append([ALGS[a], label, outcome, fmt(med(rs, 'elapsed_ms')),
                         fmt(med(rs, 'parent_ms')), fmt(med(rs, 'expanded'), 0),
                         fmt(med(rs, 'pdb_build_ms'), 0)])
    table(dataset, 'llcrrrr', ['算法', '启发', '成功', '搜索/ms', '父端/ms', '扩展次数', '建表/ms'], data)

mode_rows = []
for mode, cap, fast in [('BASIC', 0, False), ('BASIC', 0, True)] + [
        (m, c, True) for m in ['TT', 'ZOBRIST'] for c in [100000, 500000, 1000000]]:
    rs = selected(group='hard', mode=mode, transposition_limit=cap, fast=fast)
    ok = sum(r['status'] == 'SOLVED' for r in rs)
    mode_rows.append([mode, str(cap // 10000), '开' if fast else '关', f'{ok}/3',
                      fmt(med(rs, 'elapsed_ms') / 1000) if ok else '---',
                      fmt(med(rs, 'parent_ms') / 1000) if ok else '---',
                      fmt(med(rs, 'expanded'), 0)])
table('modes', 'lcccrrr', ['模式', '容量/万', 'fast', '成功', '搜索/s', '父端/s', '扩展次数'], mode_rows)

cold = selected(group='hard_cold')[0]
warm = selected(group='hard', mode='BASIC', fast=True)
table('cache', 'llcrrrr', ['缓存', '状态', '次数', '建表/ms', '加载/ms', '搜索/ms', '父端/ms'], [
    ['冷', cold['status'], '1', cold.get('pdb_build_ms', '---'), cold.get('pdb_load_ms', '---'),
     fmt(float(cold['elapsed_ms'])) if cold['status'] == 'SOLVED' else '---', fmt(cold['parent_ms'])],
    ['热', 'SOLVED', '3', fmt(med(warm, 'pdb_build_ms'), 0), fmt(med(warm, 'pdb_load_ms'), 0),
     fmt(med(warm, 'elapsed_ms')), fmt(med(warm, 'parent_ms'))]])

diagnostics = json.loads((SUITE / 'diagnostics/all-results-final.json').read_text(encoding='utf-8'))
controls = []
for fast in [False, True]:
    for snapshots in [False, True]:
        rs = [r for r in diagnostics if not r.get('profile') and r['fast'] == fast and r['snapshots'] == snapshots]
        assert len(rs) == 3 and all(r['status'] == 'SOLVED' for r in rs)
        controls.append(['开' if fast else '关', '开' if snapshots else '关', '3/3',
                         fmt(med(rs, 'elapsed_ms')), fmt(med(rs, 'child_states_created'), 0),
                         fmt(med(rs, 'pdb_lookup_count'), 0)])
table('controls', 'cccrrr', ['fast', '快照', '成功', '搜索/ms', '状态构造', 'PDB查询'], controls)

pair = [selected(group='hard', mode='BASIC', fast=f, repetition=3)[0] for f in [False, True]]
assert all(r['status'] == 'SOLVED' for r in pair)
table('counts', 'lrr', ['指标', 'fast关闭', 'fast开启'], [
    [label, fmt(float(pair[0][field]), 0), fmt(float(pair[1][field]), 0)]
    for field, label in [('generated', '生成事件'), ('expanded', '扩展事件'),
                         ('reverse_move_prunes', '提前反向过滤'), ('child_states_created', '子状态构造'),
                         ('child_nodes_created', '子结点构造'), ('pdb_lookup_count', 'PDB查询')]])


def chart_source(name, panels):
    source = [r'\documentclass[border=7bp]{standalone}',
              r'\input{../../figure-style.tex}', r'\usepackage{pgfplots}',
              r'\usepgfplotslibrary{groupplots}', r'\usepackage{booktabs}',
              r'\pgfplotsset{compat=1.18}', r'\begin{document}',
              r'\begin{tikzpicture}',
              r'\begin{groupplot}[group style={group size=' + str(len(panels)) + r' by 1,horizontal sep=1.45cm},',
              'width=' + ('6.4cm' if len(panels)==2 else '13.8cm') + r',height=5.6cm,scale only axis,ymin=0,ybar,/pgf/bar width=7pt,',
              r'axis line style={draw=Muted,line width=.65pt},axis x line*=bottom,axis y line*=left,',
              r'ymajorgrids,grid style={draw=PaleGray},tick align=outside,',
              r'every axis/.append style={font=\fontsize{10bp}{13bp}\selectfont},',
              r'tick label style={font=\fontsize{9bp}{12bp}\selectfont},',
              r'legend style={at={(.5,-.19)},anchor=north,legend columns=2,column sep=7pt,draw=none,fill=none,font=\fontsize{9bp}{12bp}\selectfont},',
              r'enlarge x limits=.2,xtick=data]']
    for title, labels, values, ymax, ylabel, failures in panels:
        source.append(r'\nextgroupplot[title={' + title + '},ylabel={' + ylabel +
                      '},ymax=' + str(ymax) + ',xmin=-.65,xmax=' + str(len(labels)-.35) + ',xtick={'+','.join(str(i) for i in range(len(labels)))+
                      '},xticklabels={' + ','.join(labels) + '}]')
        for j, color in enumerate(['Blue', 'Muted']):
            coords = ' '.join(f'({i},{value:.6f})' for i,value in enumerate(values[j]) if value is not None)
            source.append(r'\addplot[fill=' + color + ',draw=' + color + '] coordinates {' + coords + '};')
            source.append(r'\addlegendentry{' + ('A*' if j == 0 else 'IDA*') + '}')
        for (j,i), status in failures.items():
            # Failure labels describe status, never a completed run at the limit.
            source.append(r'\node[font=\fontsize{8bp}{10bp}\selectfont,text=Muted,anchor=south] at (axis cs:'+
                          str(i) + ',' + str(ymax*(.025+j*.065)) + ') {'+('A*：' if j==0 else 'IDA*：')+('超时' if status == 'TIMEOUT' else status)+'};')
    source += [r'\end{groupplot}',r'\end{tikzpicture}',r'\end{document}','']
    (FIG/(name+'.tex')).write_text('\n'.join(source),encoding='utf-8')


for field, filename, ylabel in [('elapsed_ms','heuristic-search','搜索时间 / ms'),
                               ('parent_ms','heuristic-parent','父端总时间 / ms')]:
    panels = []
    for dataset, title in [('teacher3',r'教师题 $3\times3$'),('ordinary4',r'普通题 $4\times4$')]:
        values = [[med(selected(group='heuristic',dataset=dataset,algorithm=a,heuristic=h),field)
                   for h,_ in HEURS] for a in ALGS]
        failures = {}
        for j,a in enumerate(ALGS):
            for i,(h,_) in enumerate(HEURS):
                outcomes = {r['status'] for r in selected(group='heuristic',dataset=dataset,algorithm=a,heuristic=h)}
                if 'SOLVED' not in outcomes:
                    failures[(j,i)] = next(iter(outcomes)) if len(outcomes)==1 else 'MIXED'
        vmax = max(v for row in values for v in row if v is not None)*1.24
        step = 10**math.floor(math.log10(vmax))/2
        ymax = math.ceil(vmax/step)*step
        panels.append((title,['错位数','曼哈顿','LC','PDB'],values,ymax,ylabel,failures))
    chart_source(filename,panels)

values = [[med(selected(group='heuristic',dataset='ordinary4',algorithm=a,heuristic=h),'expanded')/1000
           for h in ['MANHATTAN','MANHATTAN_LC']] for a in ALGS]
chart_source('lc-nodes',[(r'普通题 $4\times4$',['曼哈顿','LC'],values,1250,'扩展事件 / 千次',{})])

manifest = {'suite': SUITE.relative_to(ROOT).as_posix(), 'runs': len(ROWS),
            'statuses': {s: sum(r['status'] == s for r in ROWS) for s in sorted({r['status'] for r in ROWS})},
            'tables': ['teacher3', 'ordinary4', 'modes', 'cache', 'controls', 'counts'],
            'figures': ['heuristic-search', 'heuristic-parent', 'lc-nodes']}
(OUT / 'current-sources.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('CURRENT_REPORT_DATA_PASS: 80 raw results; 6 editable tables; 3 editable vector chart sources')
