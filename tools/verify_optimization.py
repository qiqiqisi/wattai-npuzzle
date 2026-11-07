"""Independent final audit: immutable history, teacher bytes, run identity and legal paths."""
from pathlib import Path
import argparse
import csv
import hashlib
import json
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(suite):
    from optimization_suite import replay, java_sources, properties
    errors=[]
    baseline=json.loads((suite/'baseline.json').read_text(encoding='utf-8'))
    history=[]
    for name,expected in baseline['history'].items():
        if name=='results/INDEX.md':continue # User explicitly requires updating this navigation file.
        path=ROOT/name
        valid=path.is_file() and sha(path)==expected
        history.append({'path':name,'sha256':expected,'unchanged':valid})
        if not valid:errors.append('Historical file changed/missing: '+name)
    protected=[]
    teacher=Path(baseline['original_root'])/'teacher_reference/WattAi'
    for name,expected in baseline['protected'].items():
        valid=(ROOT/name).is_file() and sha(ROOT/name)==expected
        local=((teacher/name).is_file() and sha(teacher/name)==expected) if teacher.exists() else None
        protected.append({'path':name,'sha256':expected,'unchanged':valid,'local_teacher_matches':local})
        if not valid or local is False:errors.append('Teacher bytes differ: '+name)
    original=Path(baseline['original_root'])
    for section in ['original_history','original_report','old_caches']:
        for name,expected in baseline[section].items():
            if not (original/name).is_file() or sha(original/name)!=expected:
                errors.append('Original checkout changed: '+name)
    frozen=json.loads((suite/'source-manifest.json').read_text(encoding='utf-8'))
    if frozen!=java_sources():errors.append('Java source differs from frozen experiment version')
    for entry in json.loads((suite/'runner-manifest.json').read_text(encoding='utf-8')):
        if sha(ROOT/entry['path'])!=entry['sha256']:errors.append('Runner changed since freeze: '+entry['path'])
    plans=json.loads((suite/'plan.json').read_text(encoding='utf-8'))['runs']
    results={p.parent.name:json.loads(p.read_text(encoding='utf-8')) for p in (suite/'runs').glob('*/result.json')}
    if len(plans)!=len(results):errors.append('Plan/result count mismatch')
    paths=[];failures=[]
    for plan in plans:
        row=results.get(plan['label'])
        if row is None:errors.append('Missing run: '+plan['label']);continue
        for key,value in plan.items():
            if row[key]!=value:errors.append('Config differs: '+row['label']+' '+key)
        if row['source_sha256']!=sha(suite/'source-manifest.json'):errors.append('Wrong source identity')
        if row['input_sha256']!=sha(ROOT/row['input_file']):errors.append('Wrong input hash')
        if row['status']=='SOLVED':
            try:
                valid=replay(row)
                valid['path']=row['solution_file'];paths.append(valid)
                if row['parent_ms']>int(row['timeout_s'])*1000:raise AssertionError('Successful parent exceeded deadline')
                if row['java_version']!='19' or row['jvm_heap_limit_bytes']!='1073741824':raise AssertionError('Wrong JVM settings')
                if row['algorithm']=='IDA':
                    bounds=json.loads(row['threshold_sequence'])
                    assert len(bounds)==int(row['iterations']) and bounds[0]==int(row['initial_bound']) and bounds[-1]==int(row['final_bound'])
                    assert all(a<b for a,b in zip(bounds,bounds[1:]))
                    assert int(row['peak_transposition_entries'])<=int(row['transposition_limit'])
                    if row['mode']=='BASIC':assert int(row['transposition_prunes'])==0
                    if row['dataset']=='hard':assert bounds==[56,58,60,62,64,66,68,70]
            except Exception as error:errors.append('Path/metrics failure '+row['label']+': '+str(error))
        elif row['status'] in ['TIMEOUT','OOM','FAIL','UNSOLVABLE']:
            for key in ['elapsed_ms','generated','expanded','solution_length','iterations']:
                if row.get(key) is not None:errors.append('Failure has completed metric '+row['label']+' '+key)
            failures.append({'label':row['label'],'status':row['status'],'parent_ms':row['parent_ms'],'reason':row.get('failure_reason')})
        else:errors.append('Unrecognized status: '+str(row['status']))
    tests=json.loads((suite/'tests.json').read_text(encoding='utf-8'))
    if len(tests)!=9 or any(t['status']!='PASS' for t in tests):errors.append('Nine Java test suites did not pass')
    if json.loads((suite/'tests/watchdog/verification.json').read_text())['status']!='PASS':errors.append('Watchdog failed')
    for name in ['cache-manifest-before-hard.json','cache-manifest-after-hard.json']:
        if not (suite/name).is_file():errors.append('Missing cache identity')
    if (suite/'cache-manifest-before-hard.json').read_bytes()!=(suite/'cache-manifest-after-hard.json').read_bytes():
        errors.append('PDB cache changed during warm comparisons')
    private_cache=ROOT/'generated'/suite.name/'shared-555T'
    expected_cache=json.loads((suite/'cache-manifest-before-hard.json').read_text())
    if expected_cache!={p.name:sha(p) for p in private_cache.glob('*.pdb')}:
        errors.append('Private PDB cache changed after diagnostics')
    diagnostic_file=next(suite/'diagnostics'/name for name in ['all-results-final.json','all-results.json','results.json'] if (suite/'diagnostics'/name).exists())
    diagnostics=json.loads(diagnostic_file.read_text(encoding='utf-8'))
    diagnostic_paths=[]
    if len([r for r in diagnostics if not r['profile']])!=12 or not 13<=len(diagnostics)<=15:
        errors.append('Expected 12 controls and 1 to 3 separately preserved profile attempts')
    if (suite/'diagnostics/runner-source.py').exists():
        recorded=json.loads((suite/'diagnostics/plan.json').read_text(encoding='utf-8'))['runner_sha256']
        if sha(suite/'diagnostics/runner-source.py')!=recorded:errors.append('Archived diagnostic runner differs from its manifest')
    for row in diagnostics:
        if row['status']=='SOLVED':
            try:
                assert row['source_sha256']==sha(suite/'source-manifest.json')
                assert row['java_version']=='19' and row['jvm_heap_limit_bytes']=='1073741824'
                result=replay(row);result['path']=row['solution_file'];diagnostic_paths.append(result)
            except Exception as error:errors.append('Diagnostic replay failure: '+str(error))
    # A late-written trace from the aborted launcher run is evidence, but never a timed success.
    aborted=ROOT/'results/optimization-20261106-20261106T114453353805Z/runs/001-preflight-preflight-IDA-DISJOINT_PATTERN-BASIC-0-fasttrue-r1'
    late=json.loads((aborted/'result.json').read_text(encoding='utf-8'));late.update(properties(aborted/'metrics.properties'))
    try:late_path=dict(replay(late),timed_acceptance=False,reason='worker completed after parent timeout')
    except Exception as error:late_path={'status':'FAIL'};errors.append('Late trace replay: '+str(error))
    sources=json.loads((ROOT/'report/data_sources.json').read_text(encoding='utf-8'))
    hashes=json.loads((ROOT/'report/data_hashes.json').read_text(encoding='utf-8'))
    old_hashes=json.loads(subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),'show',
                         baseline['base_commit']+':report/data_hashes.json'],cwd=ROOT).decode('utf-8-sig'))
    if any(hashes.get(k)!=v for k,v in old_hashes.items()):errors.append('Historical report data hash changed')
    for key,name in sources.items():
        if key=='date':continue
        if not (ROOT/name).is_file() or sha(ROOT/name)!=hashes.get(key):errors.append('Report data hash mismatch: '+name)
    links=0
    for path in [ROOT/'README.md',*(ROOT/'docs').glob('*.md'),*(ROOT/'report').glob('*.md'),ROOT/'results/INDEX.md',suite/'analysis/analysis.md']:
        for target in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)',path.read_text(encoding='utf-8-sig')):
            target=target.strip('<>').split('#')[0]
            if not target or target.startswith(('http:','https:','mailto:')):continue
            links+=1
            if not (path.parent/target).exists():errors.append('Missing link: '+str(path.relative_to(ROOT))+' -> '+target)
    # Git must show only new results and the explicitly authorized index edit under results/.
    command=['git','-c','safe.directory='+ROOT.as_posix(),'-c','core.quotePath=false','diff','--name-status',baseline['base_commit'],'--','results']
    diff=subprocess.check_output(command,cwd=ROOT,text=True,encoding='utf-8').strip()
    for line in diff.splitlines():
        if line!='M\tresults/INDEX.md' and not line.startswith('A\tresults/optimization-'):
            errors.append('Unexpected tracked result diff: '+line)
    report_diff=subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),'diff','--name-only',
        baseline['base_commit'],'--','report/latex','report/*.docx','report/*.pdf'],cwd=ROOT,text=True,encoding='utf-8').strip()
    if report_diff:errors.append('Archived binary/LaTeX report changed: '+report_diff)
    original_status=subprocess.check_output(['git','-c','safe.directory='+original.as_posix(),'status','--porcelain','-uall'],cwd=original,text=True,encoding='utf-8').strip()
    if original_status!=baseline['original_status']:errors.append('Original working tree status changed')
    final_checks=suite/'final-checks/verification.json'
    final_data=json.loads(final_checks.read_text(encoding='utf-8')) if final_checks.exists() else None
    if final_data and (final_data['status']!='PASS' or not all(v['identical'] for v in final_data['bytecodes_identical_to_actual_experiments'])):
        errors.append('Final clean build or bytecode identity failed')
    return {'status':'PASS' if not errors else 'FAIL','errors':errors,'planned_runs':len(plans),'actual_runs':len(results),
            'successful_paths':paths,'failures':failures,'history_sha256_verified':len(history),'historical_files':history,
            'diagnostic_paths':diagnostic_paths,'late_timeout_path_replay':late_path,
            'total_new_experiment_attempts_including_diagnostic_startup_failures':len(results)+len(diagnostics)+1,
            'diagnostic_statuses':[{'status':r['status'],'profile':r['profile'],'context':r.get('execution_context','primary sandbox')} for r in diagnostics],
            'final_clean_build_and_tests':{'path':final_checks.relative_to(ROOT).as_posix(),'sha256':sha(final_checks),
                 'status':final_data['status'],'identical_class_files':len(final_data['bytecodes_identical_to_actual_experiments'])} if final_data else None,
            'teacher_protected':protected,'original_checkout_report_and_results_unchanged':True if not any('Original' in e for e in errors) else False,
            'tests':tests,'markdown_links_checked':links,'tracked_results_diff':diff,
            'binary_reports':'UNCHANGED; Markdown supplement is deliberately separate from archived DOCX/PDF',
            'optimality_scope':'Every Hard path is 70; fixed admissible PDB plus IDA* threshold bound, exhaustive small BFS and independent Manhattan A* tests. No independent full 15-Puzzle BFS claim.',
            'source_sha256':sha(suite/'source-manifest.json')}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('suite',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args();suite=(ROOT/args.suite).resolve();result=verify(suite)
    if args.output:
        with (ROOT/args.output).open('x',encoding='utf-8') as out:json.dump(result,out,ensure_ascii=False,indent=2);out.write('\n')
    print(result['status'], 'runs='+str(result['actual_runs']), 'paths='+str(len(result['successful_paths'])),
          'historical_sha256='+str(result['history_sha256_verified']), 'teacher='+str(len(result['teacher_protected'])))
    for error in result['errors']:print(error,file=sys.stderr)
    raise SystemExit(0 if result['status']=='PASS' else 1)
