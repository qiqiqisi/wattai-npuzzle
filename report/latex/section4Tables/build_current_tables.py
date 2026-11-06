"""Build report tables and vector charts from the fixed experiment records."""
import json
import statistics
import argparse
import math
from pathlib import Path

from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

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
pdfmetrics.registerFont(TTFont('ChartFont', 'C:/Windows/Fonts/calibri.ttf'))
pdfmetrics.registerFont(TTFont('ChartBold', 'C:/Windows/Fonts/calibrib.ttf'))


def selected(**filters):
    return [r for r in ROWS if all(r[k] == v for k, v in filters.items())]


def med(rows, field):
    values = [float(r[field]) for r in rows if r['status'] == 'SOLVED']
    return statistics.median(values) if values else None


def fmt(v, digits=3):
    return '---' if v is None else f'{v:.{digits}f}'


def table(name, columns, headers, rows):
    text = ['% Generated from the experiment result.json files.',
            r'\begin{tabular}{' + columns + '}', r'\hline',
            ' & '.join(r'\textbf{' + h + '}' for h in headers) + r' \\', r'\hline']
    text.extend(' & '.join(row) + r' \\' for row in rows)
    text += [r'\hline', r'\end{tabular}', '']
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

BLUE, ORANGE = HexColor('#3178a8'), HexColor('#d78a32')


def panel(c, x, y, w, h, title, labels, series, ymax, ylabel, failures=None):
    c.setFillColor(HexColor('#243746')); c.setFont('ChartBold', 10)
    c.drawString(x, y + h + 17, title)
    left, bottom = x + 43, y + 35
    width, height = w - 49, h - 40
    c.setFont('ChartFont', 9)
    for i in range(5):
        yy = bottom + height * i / 4
        c.setStrokeColor(HexColor('#e0e7eb')); c.line(left, yy, left + width, yy)
        c.setFillColor(HexColor('#415562')); c.drawRightString(left - 5, yy - 2, f'{ymax*i/4:g}')
    c.drawString(x, y + h - 3, ylabel)
    slot = width / len(labels)
    bw = min(23, slot * .7 / len(series))
    for j, (name, values, color) in enumerate(series):
        c.setFillColor(color); c.rect(left + j * 92, bottom + height + 3, 7, 7, fill=1, stroke=0)
        c.setFont('ChartFont', 9); c.setFillColor(HexColor('#243746'))
        c.drawString(left + j * 92 + 10, bottom + height + 3, name)
        for i, value in enumerate(values):
            xx = left + slot * (i + .5) + (j - len(series)/2) * bw
            if value is None:
                c.setFillColor(HexColor('#687780')); c.setFont('ChartFont', 8)
                c.drawCentredString(xx + bw/2, bottom + 7 + j*9, (failures or {}).get((j, i), 'TIMEOUT'))
                continue
            bh = height * value / ymax
            c.setFillColor(color); c.rect(xx, bottom, bw - 1.5, bh, fill=1, stroke=0)
            c.setFillColor(HexColor('#243746')); c.setFont('ChartFont', 8)
            c.drawCentredString(xx + (bw - 1.5)/2, bottom + bh + 3, f'{value:.1f}')
    c.setFont('ChartFont', 9); c.setFillColor(HexColor('#243746'))
    for i, label in enumerate(labels):
        c.drawCentredString(left + slot*(i+.5), bottom - 13, label)


for field, filename, title in [('elapsed_ms', 'heuristic-search', 'Search time'),
                              ('parent_ms', 'heuristic-parent', 'Parent time')]:
    c = canvas.Canvas(str(FIG / (filename + '.pdf')), pagesize=(520, 345))
    for k, dataset in enumerate(['teacher3', 'ordinary4']):
        values = [[med(selected(group='heuristic', dataset=dataset, algorithm=a, heuristic=h), field)
                   for h, _ in HEURS] for a in ALGS]
        failures = {}
        for j, a in enumerate(ALGS):
            for i, (h, _) in enumerate(HEURS):
                outcomes = {r['status'] for r in selected(group='heuristic', dataset=dataset,
                                                          algorithm=a, heuristic=h)}
                if 'SOLVED' not in outcomes:
                    failures[(j, i)] = next(iter(outcomes)) if len(outcomes) == 1 else 'MIXED'
        vmax = max(v for row in values for v in row if v is not None) * 1.22
        step = 10 ** math.floor(math.log10(vmax)) / 2
        vmax = math.ceil(vmax / step) * step
        panel(c, 5 + k*260, 15, 250, 290, f'{dataset}: {title}', ['Mis', 'Man', 'LC', 'PDB'],
              [('A*', values[0], BLUE), ('IDA*', values[1], ORANGE)], vmax, 'ms', failures)
    c.save()

c = canvas.Canvas(str(FIG / 'lc-nodes.pdf'), pagesize=(520, 290))
values = [[med(selected(group='heuristic', dataset='ordinary4', algorithm=a, heuristic=h), 'expanded') / 1000
           for h in ['MANHATTAN', 'MANHATTAN_LC']] for a in ALGS]
panel(c, 15, 10, 490, 245, 'Ordinary 4x4: expanded events', ['Manhattan', 'Linear conflict'],
      [('A*', values[0], BLUE), ('IDA*', values[1], ORANGE)], 1200, 'x 1,000')
c.save()

manifest = {'suite': SUITE.relative_to(ROOT).as_posix(), 'runs': len(ROWS),
            'statuses': {s: sum(r['status'] == s for r in ROWS) for s in sorted({r['status'] for r in ROWS})},
            'tables': ['teacher3', 'ordinary4', 'modes', 'cache', 'controls', 'counts'],
            'figures': ['heuristic-search', 'heuristic-parent', 'lc-nodes']}
(OUT / 'current-sources.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('CURRENT_REPORT_DATA_PASS: 80 raw results; 6 editable tables; 3 vector charts')
