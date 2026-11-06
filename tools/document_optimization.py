"""Connect reviewed generated results to project navigation and a Markdown-only supplement."""
from pathlib import Path
import argparse
import json
import statistics as st
from analyze_optimization import table, pretty
from optimization_suite import ROOT, sha


def append(path,text):
    original=path.read_bytes()
    newline='\r\n' if b'\r\n' in original else '\n'
    with path.open('ab') as out:out.write((newline+text.replace('\n',newline)).encode('utf-8'))


def document(suite):
    assert '## 实测结论与推荐' not in (ROOT/'docs/optimization_analysis.md').read_text(encoding='utf-8'), 'Refuse duplicate documentation insertion'
    summary=json.loads((suite/'analysis/summary.json').read_text(encoding='utf-8'))
    diag_name=next(name for name in ['all-results-final.json','all-results.json','results.json'] if (suite/'diagnostics'/name).exists())
    diagnostic=json.loads((suite/'diagnostics'/diag_name).read_text(encoding='utf-8'))
    diag_count=len(diagnostic);profile_attempts=sum(r['profile'] for r in diagnostic)
    rel=suite.relative_to(ROOT).as_posix()
    hard=summary['hard'];heuristic=summary['heuristics'];winner=summary['recommended_mode'];cap=summary['recommended_capacity']
    basic=next(r for r in hard if r['mode']=='BASIC' and r['fast'])
    slow=next(r for r in hard if r['mode']=='BASIC' and not r['fast'])
    cold=json.loads(next((suite/'runs').glob('032-*/result.json')).read_text(encoding='utf-8'))
    def worth(mode):
        eligible=[r for r in hard if r['mode']==mode and r['fast'] and r['solved']==r['attempts']==3]
        if not eligible:return '没有三次全成功的配置，本题不建议启用。'
        best=min(eligible,key=lambda r:r['parent_ms'])
        return f'完成三次的最佳容量为 {best["transposition_limit"]}，父端中位 {best["parent_ms"]:.3f} ms；'+(
            '达到本轮工程选择门槛，可作为本题热缓存候选。' if best['parent_ms']<.9*basic['parent_ms'] else '未达到比 BASIC 改善至少10%的工程门槛，本题不建议启用。')
    findings='## 实测结论与推荐\n\n'
    findings+=f'正式矩阵共 {summary["attempts"]} 次，状态为 {pretty(summary["statuses"])}；另保留首次启动器 TIMEOUT 预实验1次，以及{diag_count}次独立诊断（12次普通题控制、{profile_attempts}次JFR Hard尝试）。这些诊断不计入正式中位数。\n\n'
    if profile_attempts>1:
        findings+='JFR前两次在JVM初始化阶段FAIL：第一次录制参数解析失败，第二次无法创建沙箱临时录制目录，都没有进入搜索，原日志不改写。第三次经明确权限请求在沙箱外采样；它与正式矩阵执行条件不同，采样只描述这次诊断，不把它的耗时或样本比例当作沙箱中正式运行的精确结果。诊断编译首次也因沙箱类路径枚举失败，失败日志保留；同源码在沙箱外编译通过，12次普通控制仍在原沙箱环境运行。\n\n'
    findings+='| 问题 | 本轮证据支持的回答 |\n| --- | --- |\n'
    findings+=f'| Zobrist 是否值得启用 | {worth("ZOBRIST")} |\n| TT 是否值得启用 | {worth("TT")} |\n'
    findings+=f'| 最合适容量 | 最终热缓存候选 {winner} / limit={cap}；容量排序仅针对本题、1024m、三个采样时刻，不是普适最佳。 |\n'
    findings+=f'| fast 是否有收益 | BASIC+fast=true 成功 {basic["solved"]}/3，false 成功 {slow["solved"]}/3。存在超时删失时，不能把各自成功中位数相除当作无偏加速比。普通52步题另有完整开关对照，见下表。 |\n'
    findings+='| Linear Conflict 是否更有优势 | 对全部测试状态，h 至少为 Manhattan；实际时间与扩展收益按算法/题目分开，见四启发实测。更强下界不保证每个实例耗时更小。 |\n'
    findings+='| PDB 建表是否值得 | 四启发对照每次为冷建表；用父端总时长判断一次性成本，不能只看搜索。Hard 的本轮比较没有 Manhattan/LC，因此不从普通题外推 Hard 的统一加速比。 |\n'
    findings+=f'| 最终 Hard 配置 | 保留已支持的 IDA* / {winner} / limit={cap} / 555T / fast=true / Java19 / 1024m / 60s；本轮 BASIC 冷缓存实际状态 {cold["status"]}，'+(
        f'父端 {float(cold["parent_ms"]):.3f} ms。' if cold['status']=='SOLVED' else f'父端到终止 {cold["parent_ms"]:.3f} ms，不能宣称本轮冷启动达标。')+' |\n\n'
    fields=[('mode','模式'),('transposition_limit','容量'),('fast','fast'),('statuses','状态'),('elapsed_ms','成功搜索中位ms'),('parent_ms','成功父端中位ms'),('expanded','扩展'),('transposition_prunes','TT剪枝')]
    findings+=table(hard,fields)+'\n\n'
    # Completed paired counters are reported for Hard only when both settings completed.
    raw=[json.loads(p.read_text(encoding='utf-8')) for p in (suite/'runs').glob('*/result.json')]
    successful=[r for r in raw if r['group']=='hard' and r['mode']=='BASIC' and r['status']=='SOLVED']
    paired=[]
    for rep in range(1,4):
        on=next((r for r in successful if r['repetition']==rep and r['fast']),None)
        off=next((r for r in successful if r['repetition']==rep and not r['fast']),None)
        if on and off:
            paired.append({'rep':rep,'generated_false':int(off['generated']),'generated_true':int(on['generated']),
                           'generated_saved':int(off['generated'])-int(on['generated']),
                           'reverse_prunes':int(on['reverse_move_prunes']),
                           'states_saved':int(off['child_states_created'])-int(on['child_states_created']),
                           'nodes_saved':int(off['child_nodes_created'])-int(on['child_nodes_created']),
                           'lookup_saved':int(off['pdb_lookup_count'])-int(on['pdb_lookup_count'])})
    if paired:
        findings+='### fast：完整配对的 Hard 计数\n\n'+table(paired,[('rep','重复'),('generated_false','关闭生成'),('generated_true','开启生成'),
            ('generated_saved','生成减少'),('reverse_prunes','反向过滤'),('states_saved','子状态构造减少'),('nodes_saved','子结点构造减少'),('lookup_saved','PDB查询减少')])+'\n\n'
    controls=[]
    for fast in [False,True]:
        for snapshots in [False,True]:
            members=[r for r in diagnostic if not r['profile'] and r['fast']==fast and r['snapshots']==snapshots]
            solved=[r for r in members if r['status']=='SOLVED']
            record={'fast':fast,'snapshots':snapshots,'success':f'{len(solved)}/{len(members)}'}
            for key in ['elapsed_ms','generated','expanded','child_states_created','child_nodes_created','pdb_lookup_count']:
                record[key]=st.median(float(r[key]) for r in solved) if solved else None
            controls.append(record)
    findings+='### fast 与记录器的独立普通题控制\n\n'+table(controls,[('fast','fast'),('snapshots','记录快照'),('success','成功'),
                 ('elapsed_ms','搜索中位ms'),('generated','生成'),('expanded','扩展'),('child_states_created','子状态构造'),('child_nodes_created','子结点构造'),('pdb_lookup_count','PDB查询')])+'\n\n'
    findings+='每格3次新JVM，固定种子打乱顺序，使用本轮私有555T热缓存。快照控制复用正式运行器的计数序列化，反射调用会增加少量诊断开销；它只能描述普通题观察到的记录器差异，不能推算 Hard 的精确开销。fast 仍是多个机制的组合开关：立即反向移动在创建状态前过滤，其他路径环仍由 pathStates 排除，子结点逐个创建；三者不能各自分摊为独立加速比例。PDB 没有增量预测接口，这组不含 Manhattan 的增量距离收益。\n\n'
    sample=suite/'diagnostics/jfr-search-samples.txt'
    if sample.exists():
        data=dict(line.split('=',1) for line in sample.read_text().splitlines() if '=' in line)
        total=int(data['search_stack_samples']);pdb=int(data['pdb_in_search_stack_samples'])
        findings+=f'独立 JFR Hard 诊断中，带 IDA* 搜索栈的 CPU 样本 {total} 个，其中出现 PDB 栈 {pdb} 个（{100*pdb/total if total else 0:.2f}%）。'+(
            'PDB 是该诊断中的主要采样 CPU 热点。' if total and pdb/total>.5 else '该采样不支持将 PDB 认定为占过半 CPU 的主要热点。')+'这不是精确墙钟时间占比，JIT 内联和采样存在偏差；JFR 启动开销与该次耗时不进入正式对照。原始二进制留在忽略的 generated/，仓库保留栈计数摘要。\n\n'
    else:findings+='JFR 没有生成可读采样，PDB 时间占比未测量，不能根据查询次数判断主要耗时。\n\n'
    comparisons=[]
    for dataset in ['teacher3','ordinary4']:
        for algorithm in ['ASTAR','IDA']:
            records={r['heuristic']:r for r in heuristic if r['dataset']==dataset and r['algorithm']==algorithm}
            m,lc,pdb=(records[h] for h in ['MANHATTAN','MANHATTAN_LC','DISJOINT_PATTERN'])
            comparisons.append({'dataset':dataset,'algorithm':algorithm,'m_ms':m['elapsed_ms'],'lc_ms':lc['elapsed_ms'],
                                'm_exp':m['expanded'],'lc_exp':lc['expanded'],'m_parent':m['parent_ms'],'lc_parent':lc['parent_ms'],
                                'pdb_parent':pdb['parent_ms'],'pdb_build':pdb['pdb_build_ms']})
    findings+='### LC 与 PDB 成本对照\n\n'+table(comparisons,[('dataset','题'),('algorithm','算法'),('m_ms','Manhattan搜索ms'),('lc_ms','LC搜索ms'),
             ('m_exp','Manhattan扩展'),('lc_exp','LC扩展'),('m_parent','Manhattan父端ms'),('lc_parent','LC父端ms'),('pdb_parent','PDB冷父端ms'),('pdb_build','PDB建表ms')])+'\n\n'
    findings+='PDB 是否值得应分别回答：一次性普通题，直接比较上表冷父端；多次相同目标可复用时，只计一次建表再按次加载；Hard 冷运行是否达标单独看实际状态。没有测多个目标和不同难度分布的摊销盈亏点，不给普适的“运行几次必然回本”。\n\n'
    findings+='本轮仍只有固定实例各3次，给出原始点、成功率、成功中位数和观察范围，不声称统计显著性。历史同题 CSV、Hard 冷/热基线仍原路径保留；历史快慢差异包含运行环境负载与JIT/GC等未隔离因素，不能把新旧时长差当作优化净收益。\n\n'
    findings+=f'[完整自动表与图](../{rel}/analysis/analysis.md) · [失败明细](../{rel}/analysis/failures.csv) · [独立诊断](../{rel}/diagnostics/{diag_name}) · [历史基线](../results/run-20261011T071242033Z/stage3.csv)\n'
    append(ROOT/'docs/optimization_analysis.md',findings)
    short=f'## 新一轮优化实验（2026-11-06）\n\n正式80次，{pretty(summary["statuses"])}，另保留1次启动器超时预实验和{diag_count}次诊断尝试。三个完整8-Puzzle目标、4×4精确/随机验证以及8套原有Java测试通过。Hard热缓存推荐 **{winner} / limit={cap} / 555T / fast=true**；BASIC独立冷运行状态为 **{cold["status"]}**。三次重复仅作描述，不声称普遍优势。\n\n'
    short+=f'[分析与推荐](docs/optimization_analysis.md) · [新原始数据]({rel}/runs/) · [自动汇总与图表]({rel}/analysis/analysis.md) · [历史保护基线]({rel}/baseline.json)\n'
    readme=ROOT/'README.md';content=readme.read_bytes()
    needle='## 四阶段结果（2026-10-11 最终复核）'.encode('utf-8');assert content.count(needle)==1
    newline='\r\n' if b'\r\n' in content else '\n'
    readme.write_bytes(content.replace(needle,(short.replace('\n',newline)+newline+'## 四阶段历史结果（2026-10-11 复核）').encode('utf-8'))
                      .replace('## hard 真实性能'.encode('utf-8'),'## hard 历史性能（2026-10-11）'.encode('utf-8')))
    append(ROOT/'results/INDEX.md',f'## 2026-11-06 优化矩阵\n\n正式80次：{pretty(summary["statuses"])}；[原始记录]({suite.name}/runs/)、[源码与环境]({suite.name}/environment.json)、[完整比较表与图]({suite.name}/analysis/analysis.md)、[诊断]({suite.name}/diagnostics/results.json)。BASIC冷运行 {cold["status"]}，不以热缓存代替冷启动验收。历史文件保留并以逐文件SHA-256检查。\n\n另有[首次预实验TIMEOUT](optimization-20261106-20261106T114453353805Z/diagnosis.json)，原因及迟到路径保留；该目录计划80项但只实际启动1项，不能按计划数充当已运行数。\n')
    append(ROOT/'docs/acceptance.md',f'## 2026-11-06 优化专项验收\n\n| 项目 | 状态 | 证据与边界 |\n| --- | --- | --- |\n| 干净Java19编译、8套原有及新增测试 | PASS | [构建](../{rel}/clean-build.log)、[9套记录](../{rel}/tests.json) |\n| LC完整8-Puzzle一致性及4×4核对 | PASS | [7,022,745项检查](../{rel}/tests/optimization-final/correctness.json)，4×4非全空间证明 |\n| 7种模式普通题预实验 | PASS | 各52步，独立回放 |\n| Hard矩阵及四启发实际执行 | PASS | 正式80次全部执行，{pretty(summary["statuses"])}；执行完成不等于所有配置成功 |\n| 新成功路径 | PASS | 题目/目标/动作/连续性/最优长度逐条独立回放 |\n| 本轮BASIC冷启动60秒条件 | {"PASS" if cold["status"]=="SOLVED" else "FAIL"} | 实际 {cold["status"]}，父端 {cold["parent_ms"]:.3f} ms |\n| 置换表性能推荐 | PASS | 按当前证据选择 {winner} / limit={cap}，不强制启用新增模式 |\n| 历史与教师保护 | PASS | [冻结基线](../{rel}/baseline.json)，最终统一核验记录放在该目录；只更新INDEX导航 |\n| DOCX/PDF重生成 | NOT_RUN | 历史二进制及原数据哈希不改；Markdown补充见[正文](../report/正文.md) |\n| 显著性与普适最佳容量 | NOT_RUN | 三次固定题重复不足以作这些结论 |\n\n完整方法、失败处理与复现见[优化分析](optimization_analysis.md)。本节不替代既有GUI人工验收记录。\n')
    supplement='<!-- OPTIMIZATION_20261106_SUPPLEMENT_START -->\n\n## 4.6 新增优化验证与同题实验\n\n'
    supplement+='本节为独立的新实验补充；第4.1—4.5节的历史表格、CSV与报告DOCX/PDF保持原字节和原日期。新实验采用同一份源码、Java19、1024m，新JVM，无同JVM预热。Hard索引9、555T、60秒；普通索引3与教师3×3各做四启发、两个算法、三次重复；PDB准备分列，不把失败记成60秒搜索完成。\n\n'
    supplement+=f'LC对三个目标各181,440个8-Puzzle可达状态及每条边通过验证，4×4另用浅层BFS、48组A* Manhattan精确解和随机边核对。TT/Zobrist的相同状态、较大/较小g、碰撞、容量、逐轮清空与阈值处理通过直接和随机图测试；有限正确性测试不意味着性能更好。正式状态计数为 {pretty(summary["statuses"])}。\n\n'
    supplement+=table(hard,fields)+'\n\n'
    supplement+='四启发均保持同题；下表时间为成功结果中位数，TIMEOUT仍保留且不参与计算。\n\n'
    supplement+=table(heuristic,[('dataset','题'),('algorithm','算法'),('heuristic','启发'),('statuses','状态'),('solution_length','解长'),('elapsed_ms','搜索ms'),('parent_ms','父端ms'),('expanded','扩展'),('pdb_build_ms','建表ms')])+'\n\n'
    supplement+=f'本轮热缓存选择 {winner} / limit={cap} / fast=true。BASIC冷缓存实际为 {cold["status"]}，不能把热结果冒称冷启动成功。fast的即时反向过滤、路径集合提前检查、逐个生成及增量估值属于组合开关；Hard使用的PDB没有增量接口。普通题有独立记录器与fast对照，CPU采样另存，不并入正式中位数。更强的LC下界是否节省总时间须结合计算代价；PDB建表是否值得须比较冷父端或明确复用次数。\n\n'
    supplement+=f'[完整方法与逐项回答](../docs/optimization_analysis.md) · [原始数据与自动图表](../{rel}/analysis/analysis.md) · [诊断](../{rel}/diagnostics/results.json)。本轮只有固定实例各三次，没有统计显著性或全体15-Puzzle性能结论。\n\n<!-- OPTIMIZATION_20261106_SUPPLEMENT_END -->\n\n'
    report=ROOT/'report/正文.md';original=report.read_bytes();assert b'OPTIMIZATION_20261106_SUPPLEMENT_START' not in original
    needle='# 5 解路径可视化'.encode('utf-8');assert original.count(needle)==1
    newline='\r\n' if b'\r\n' in original else '\n'
    report.write_bytes(original.replace(needle,supplement.replace('\n',newline).encode('utf-8')+needle))
    append(ROOT/'report/README.md',f'## 新实验补充的版本边界\n\n2026-11-06 的优化实验只加入[Markdown正文](正文.md)第4.6节与[独立分析](../docs/optimization_analysis.md)。现有DOCX、PDF及LaTeX正文/PDF均未重新生成；旧图和旧数据哈希保持不变，不能把历史二进制报告当作已包含本轮结果。原 `build_report.py` 使用历史模板硬编码正文，已增加保护门禁，避免覆盖新补充；要生成包含新实验的二进制报告，需要显式升级该模板流程并重新排版核验。本轮新数据源见[data_sources.json](data_sources.json)的optimization键。\n')
    append(ROOT/'docs/changes.md','## 2026-11-06：优化验证与证据隔离\n\n原先已有LC、TT、Zobrist实现，但正式比较缺少一致性与容量证据。此次保留搜索规则，新增全空间/随机正确性测试、真实构造/剪枝计数及独立运行器，完成有界同题矩阵。优点是配置与失败可回溯、历史结果不改；代价是计数/快照存在开销，三次固定题无法证明普适排序。首次预实验发现javapath启动器不能充当实际JVM截止对象，失败保留后改用真实JDK路径并增加截止夹具。历史报告验证不再要求历史源码哈希等于当前源码；历史文件本身用冻结SHA验收。旧报告生成器增加防覆盖门禁，历史DOCX/PDF不重建。回退源码可使用本轮父提交；不得用删除新实验或重写旧结果来回退证据。\n')
    source_path=ROOT/'report/data_sources.json';hash_path=ROOT/'report/data_hashes.json'
    sources=json.loads(source_path.read_text(encoding='utf-8'));hashes=json.loads(hash_path.read_text(encoding='utf-8'))
    for key,name in {'optimization_comparison':rel+'/analysis/comparison.csv','optimization_summary':rel+'/analysis/summary.json',
                     'optimization_diagnostics':rel+'/diagnostics/'+diag_name}.items():
        assert key not in sources;sources[key]=name;hashes[key]=sha(ROOT/name)
    source_path.write_text(json.dumps(sources,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    hash_path.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('DOCUMENTED',winner,cap)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('suite',type=Path);args=p.parse_args();document((ROOT/args.suite).resolve())
