"""One explicitly permissioned JFR diagnostic after sandbox temp-directory failure."""
import argparse
import json
import os
import subprocess
import time
from pathlib import Path
from optimization_suite import ROOT, write_json, sha, properties, replay


def run(suite):
    previous=json.loads((suite/'diagnostics/all-results.json').read_text(encoding='utf-8'))
    assert len(previous)==14 and previous[-1]['status']=='FAIL'
    out=suite/'diagnostics/profile-external';out.mkdir(exist_ok=False)
    env=json.loads((suite/'environment.json').read_text(encoding='utf-8'))
    classes=json.loads((suite/'diagnostics/compile-retry.json').read_text(encoding='utf-8'))['precompiled_class']
    recording='generated/'+suite.name+'/profile-external.jfr'
    command=[env['java_executable'],'-Xmx1024m','-Dwattai.fast=true',
        '-XX:StartFlightRecording=settings=profile,filename='+recording+',duration=55s,dumponexit=true',
        '-cp',env['classes']+os.pathsep+classes,'OptimizationDiagnostics','9',
        'generated/'+suite.name+'/shared-555T',out.relative_to(ROOT).as_posix(),'false']
    row={k:v for k,v in previous[-1].items() if k not in ['status','exit_code','parent_ms','replay']}
    row.update(command=command,execution_context='outside sandbox, explicitly permitted after JFR temp access failure; diagnostic only',
               runner_sha256=sha(Path(__file__)))
    write_json(out/'configuration.json',row)
    start=time.perf_counter();timeout=False
    with (out/'worker.log').open('xb') as log:
        process=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        try:process.wait(timeout=60)
        except subprocess.TimeoutExpired:timeout=True;process.kill();process.wait()
    row['parent_ms']=(time.perf_counter()-start)*1000;row['exit_code']=process.returncode
    if timeout:row['status']='TIMEOUT'
    elif process.returncode:row['status']='OOM' if 'OutOfMemoryError' in (out/'worker.log').read_text(errors='replace') else 'FAIL'
    else:row.update(properties(out/'metrics.properties'))
    row['replay']=replay(row);write_json(out/'result.json',row)
    if (ROOT/recording).exists():
        result=subprocess.run([env['java_executable'],'-cp',env['classes']+os.pathsep+classes,'OptimizationDiagnostics',
             'summarize',recording,(suite/'diagnostics/jfr-search-samples.txt').relative_to(ROOT).as_posix()],cwd=ROOT,capture_output=True,timeout=30)
        with (out/'summary.log').open('xb') as log:log.write(result.stdout+result.stderr)
        if result.returncode:raise RuntimeError('Summary failed; recording retained locally')
    write_json(suite/'diagnostics/all-results-final.json',previous+[row])
    print(row['status'],row.get('elapsed_ms','MISSING'),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('suite',type=Path);a=p.parse_args();run((ROOT/a.suite).resolve())
