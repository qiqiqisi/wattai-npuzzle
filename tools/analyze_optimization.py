"""Generate inspectable descriptive tables and static research figures from a completed suite."""
from pathlib import Path
import argparse
import csv
import json
import statistics as st
from collections import Counter, defaultdict

ROOT = Path(__file__).resolve().parents[1]
METRICS = ['solution_length', 'elapsed_ms', 'parent_ms', 'generated', 'expanded', 'iterations',
           'cycle_prunes', 'transposition_prunes', 'peak_transposition_entries', 'pdb_build_ms',
           'pdb_load_ms', 'pdb_lookup_count', 'reverse_move_prunes', 'child_states_created',
           'child_nodes_created', 'full_heuristic_evaluations', 'incremental_heuristic_evaluations']


def summarize(rows, keys):
    groups = defaultdict(list)
    for row in rows:
        groups[tuple(row[k] for k in keys)].append(row)
    output = []
    for group, members in sorted(groups.items()):
        success = [r for r in members if r['status'] == 'SOLVED']
        record = dict(zip(keys, group), attempts=len(members), solved=len(success),
                      success_rate=len(success)/len(members), statuses=dict(Counter(r['status'] for r in members)))
        for metric in METRICS:
            values = [float(r[metric]) for r in success if r.get(metric, '') != '']
            record[metric] = st.median(values) if values else None
            if metric in ['elapsed_ms', 'parent_ms']:
                record[metric+'_min'] = min(values) if values else None
                record[metric+'_max'] = max(values) if values else None
        output.append(record)
    return output


def csv_write(path, rows):
    keys = list(dict.fromkeys(k for r in rows for k in r))
    with path.open('x', encoding='utf-8', newline='') as out:
        writer = csv.DictWriter(out, keys); writer.writeheader()
        for row in rows:
            writer.writerow({k: json.dumps(v, ensure_ascii=False) if isinstance(v, (dict, list)) else v
                             for k, v in row.items()})


def pretty(value):
    if value is None or value == '': return '缺失'
    if isinstance(value, dict): return ', '.join(f'{k}:{v}' for k,v in value.items())
    if isinstance(value, float): return f'{value:.3f}'.rstrip('0').rstrip('.')
    return str(value)


def table(rows, fields):
    return '\n'.join(['| '+' | '.join(title for key,title in fields)+' |',
                      '| '+' | '.join('---' for _ in fields)+' |']+
                     ['| '+' | '.join(pretty(r.get(k)) for k,title in fields)+' |' for r in rows])


def figures(out, raw, hard, heuristic):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    plt.rcParams.update({'font.family':'DejaVu Sans', 'font.size':10, 'axes.spines.top':False,
                         'axes.spines.right':False, 'savefig.dpi':170})
    blue, orange = '#2E75B5', '#C77D20'
    fig, ax = plt.subplots(figsize=(10.5,5.8))
    labels=[]
    for y,group in enumerate(hard):
        labels.append(f'{group["mode"]} {group["transposition_limit"]:,} / fast={group["fast"]}')
        members=[r for r in raw if r['group']=='hard' and r['mode']==group['mode']
                 and int(r['transposition_limit'])==group['transposition_limit'] and r['fast']==group['fast']]
        for offset,metric,color,mark in [(-.13,'elapsed_ms',blue,'o'),(.13,'parent_ms',orange,'s')]:
            vals=[float(r[metric])/1000 for r in members if r['status']=='SOLVED']
            if vals:
                ax.scatter(vals,[y+offset]*len(vals),s=22,facecolors='none',edgecolors=color,marker=mark)
                ax.scatter([st.median(vals)],[y+offset],s=55,c=color,marker=mark)
        ax.text(1.01,y,f'{group["solved"]}/{group["attempts"]} solved',transform=ax.get_yaxis_transform(),va='center')
    ax.set_yticks(range(len(labels)),labels);ax.invert_yaxis();ax.set_xlim(left=0)
    ax.set_xlabel('Seconds: successful runs only; filled marker = median; open = actual run')
    ax.set_title('Hard index 9 / IDA* / 555T / warm private cache / n=3 per configuration')
    ax.scatter([],[],c=blue,label='Search',marker='o');ax.scatter([],[],c=orange,label='Parent total',marker='s')
    ax.legend(loc='lower left');ax.grid(axis='x',alpha=.18);fig.tight_layout();fig.subplots_adjust(right=.86)
    fig.savefig(out/'hard-times.png');plt.close(fig)
    fig, axes=plt.subplots(2,2,figsize=(12,8))
    heuristics=['MISPLACED','MANHATTAN','MANHATTAN_LC','DISJOINT_PATTERN']
    for ax,(dataset,algorithm) in zip(axes.flat,[(d,a) for d in ['teacher3','ordinary4'] for a in ['ASTAR','IDA']]):
        for y,h in enumerate(heuristics):
            members=[r for r in raw if r['group']=='heuristic' and r['dataset']==dataset and r['algorithm']==algorithm and r['heuristic']==h]
            for offset,metric,color,mark in [(-.13,'elapsed_ms',blue,'o'),(.13,'parent_ms',orange,'s')]:
                vals=[float(r[metric]) for r in members if r['status']=='SOLVED']
                if vals:
                    ax.scatter(vals,[y+offset]*len(vals),s=18,facecolors='none',edgecolors=color,marker=mark)
                    ax.scatter([st.median(vals)],[y+offset],s=40,c=color,marker=mark)
            failures=Counter(r['status'] for r in members if r['status']!='SOLVED')
            if failures: ax.text(.5,y,' / '.join(f'{k} x{v}' for k,v in failures.items()),transform=ax.get_yaxis_transform(),va='center')
        ax.set_yticks(range(4),['Misplaced','Manhattan','Manhattan + LC','PDB']);ax.invert_yaxis()
        ax.set_xscale('log');ax.set_xlabel('Milliseconds (log scale); successful runs only')
        ax.set_title(dataset+' / '+algorithm+' / n=3');ax.grid(axis='x',alpha=.18)
    axes[0,0].scatter([],[],c=blue,label='Search',marker='o');axes[0,0].scatter([],[],c=orange,label='Parent',marker='s')
    axes[0,0].legend();fig.suptitle('Four heuristics: fresh JVMs; PDB preparation counted in parent (cold per run)')
    fig.tight_layout();fig.savefig(out/'heuristic-times.png');plt.close(fig)
    fig, axes=plt.subplots(1,2,figsize=(12,5))
    for ax,metric in zip(axes,['generated','expanded']):
        for y,group in enumerate(hard):
            if group[metric] is not None:ax.scatter([group[metric]/1e6],[y],color=blue)
        ax.set_yticks(range(len(hard)),labels);ax.invert_yaxis()
        values=[group[metric]/1e6 for group in hard if group[metric] is not None]
        ax.set_xlim(0,max(values)*1.08 if values else 1)
        from matplotlib.ticker import MaxNLocator
        ax.xaxis.set_major_locator(MaxNLocator(nbins=4))
        ax.set_xlabel(metric+' events (millions; linear scale)');ax.grid(axis='x',alpha=.18)
    fig.suptitle('Hard: median successful event counts; IDA* counts repeat work across thresholds')
    fig.tight_layout();fig.savefig(out/'hard-nodes.png');plt.close(fig)


def analyze(suite, out):
    plan=json.loads((suite/'plan.json').read_text(encoding='utf-8'))
    raw=[json.loads(p.read_text(encoding='utf-8')) for p in sorted((suite/'runs').glob('*/result.json'))]
    if len(raw)!=plan['total']:raise ValueError(f'Incomplete suite: {len(raw)}/{plan["total"]}')
    assert {r['label'] for r in raw} == {r['label'] for r in plan['runs']}
    out.mkdir(exist_ok=False)
    for row in raw:
        if row['status']!='SOLVED':
            for key in ['elapsed_ms','generated','expanded','solution_length','iterations']:
                assert row.get(key) is None, 'Failure cannot masquerade as a completed search'
    csv_write(out/'comparison.csv', [{k:v for k,v in r.items() if k!='replay'} for r in raw])
    hard=summarize([r for r in raw if r['group']=='hard'],['mode','transposition_limit','fast'])
    fast=[r for r in hard if r['mode']=='BASIC']
    heuristic=summarize([r for r in raw if r['group']=='heuristic'],['dataset','algorithm','heuristic'])
    all_status=summarize(raw,['group','dataset','algorithm','heuristic','mode','transposition_limit','fast','cache_state'])
    csv_write(out/'hard-modes-and-capacities.csv',hard);csv_write(out/'fast.csv',fast)
    csv_write(out/'heuristics.csv',heuristic);csv_write(out/'success-and-failures.csv',all_status)
    failures=[{k:r.get(k,'') for k in ['label','status','parent_ms','failure_reason','last_snapshot_file']} for r in raw if r['status']!='SOLVED']
    if failures:csv_write(out/'failures.csv',failures)
    primary=[r for r in hard if r['fast']]
    baseline=next(r for r in primary if r['mode']=='BASIC')
    candidates=[r for r in primary if r['solved']==r['attempts']==3]
    # A declared conservative selection rule, descriptive only, not a significance test.
    winner=min(candidates,key=lambda r:r['parent_ms']) if candidates else baseline
    if baseline['solved']==3 and winner['parent_ms']>=.9*baseline['parent_ms']:winner=baseline
    summary={'attempts':len(raw),'statuses':dict(Counter(r['status'] for r in raw)),
             'hard':hard,'heuristics':heuristic,'recommended_mode':winner['mode'],
             'recommended_capacity':winner['transposition_limit'],'recommended_fast':True,
             'selection_rule':'all 3 runs solved; lowest warm parent median must improve >=10% over BASIC to replace BASIC; otherwise BASIC',
             'statistical_inference':'NOT_ESTABLISHED: three trials on fixed instances, no significance or generalization claim',
             'pdb_hotspot':'NOT_MEASURED: lookup counts and total search time cannot identify time share',
             'instrumentation':'same counters/snapshots in every new run; snapshot overhead not isolated in primary matrix'}
    sample=suite/'diagnostics/jfr-search-samples.txt'
    if sample.exists():
        data=dict(line.split('=',1) for line in sample.read_text().splitlines() if '=' in line)
        summary['pdb_hotspot']={'source':sample.relative_to(ROOT).as_posix(),
            'search_stack_samples':int(data['search_stack_samples']),
            'pdb_in_search_stack_samples':int(data['pdb_in_search_stack_samples']),
            'scope':'independent JFR diagnostic CPU samples; not exact wall-time share'}
        summary['instrumentation']='same counters in primary matrix; separate ordinary 2x2 fast/snapshot controls, n=3; no exact Hard recorder overhead isolation'
    (out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    time_fields=[('mode','模式'),('transposition_limit','容量'),('fast','fast'),('solved','成功'),('attempts','次数'),
                 ('statuses','状态'),('elapsed_ms','搜索中位 ms'),('parent_ms','父端中位 ms'),('expanded','扩展'),('transposition_prunes','TT剪枝')]
    heuristic_fields=[('dataset','题目'),('algorithm','算法'),('heuristic','启发函数'),('statuses','状态'),
                      ('solution_length','解长'),('elapsed_ms','搜索中位 ms'),('parent_ms','父端中位 ms'),
                      ('generated','生成'),('expanded','扩展'),('iterations','迭代'),('pdb_build_ms','建表 ms'),('pdb_load_ms','加载 ms')]
    fast_fields=[('fast','fast'),('statuses','状态'),('elapsed_ms','搜索中位 ms'),('parent_ms','父端中位 ms'),
                 ('generated','生成'),('expanded','扩展'),('cycle_prunes','路径剪枝'),('reverse_move_prunes','反向过滤'),
                 ('child_states_created','子状态构造'),('child_nodes_created','子结点构造'),('pdb_lookup_count','PDB查询')]
    text='# 新一轮优化实验自动汇总\n\n'
    text+='原始记录位于相邻 runs/，每行代表一次独立工作 JVM；仅 SOLVED 计算中位数。缺失不填零，TIMEOUT 不用截止时长替代搜索耗时。min/max 是观察范围，不是置信区间。\n\n'
    text+='## Hard 模式与容量（同一索引9，555T，热缓存）\n\n'+table(hard,time_fields)+'\n\n'
    text+='## fast 对照（BASIC，同源同题同缓存）\n\n'+table(fast,fast_fields)+'\n\n'
    text+='反向过滤、路径集合提前检查、逐个创建子结点、增量估值属于 fast 组合开关，没有单因素消融。子对象构造计数对应明确构造调用，不是分配字节或峰值内存。DISJOINT_PATTERN 不实现 IncrementalPredictor，所以 Hard 这组不能归因于增量启发值。\n\n'
    text+='## 四启发函数（两个题目、两个算法、每组3次；PDB每次独立冷建表）\n\n'+table(heuristic,heuristic_fields)+'\n\n'
    text+='## 成功率与失败\n\n'+table(all_status,[('group','组'),('dataset','题'),('algorithm','算法'),('heuristic','启发'),('mode','模式'),('transposition_limit','容量'),('fast','fast'),('cache_state','缓存'),('solved','成功'),('attempts','次数'),('statuses','状态')])+'\n\n'
    text+=f'按预先写入分析代码的保守规则，本题热缓存候选为 {winner["mode"]} / limit={winner["transposition_limit"]} / fast=true。该规则要求替换方案3次全成功，父端中位数至少改善10%；阈值是工程选择标准，不是统计显著性。冷缓存只测 BASIC 一次，不能替其他模式证明冷启动验收。\n\n'
    text+='查询次数不能证明 PDB 是主要耗时，独立JFR采样另见 diagnostics/，采样比例不是精确墙钟时间占比。三次固定实例重复不足以推广至全部15-Puzzle。\n\n'
    text+='![Hard搜索与父端](hard-times.png)\n\n![四启发函数](heuristic-times.png)\n\n![Hard结点事件](hard-nodes.png)\n'
    (out/'analysis.md').write_text(text,encoding='utf-8')
    figures(out,raw,hard,heuristic)
    print(json.dumps({k:summary[k] for k in ['attempts','statuses','recommended_mode','recommended_capacity']},ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('suite',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args();suite=(ROOT/args.suite).resolve()
    analyze(suite,(ROOT/args.output).resolve() if args.output else suite/'analysis')
