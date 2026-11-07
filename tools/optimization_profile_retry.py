"""One bounded JFR startup correction. Never replaces the failed diagnostic or primary runs."""
import json
import os
from pathlib import Path
import argparse
import subprocess
import time
from datetime import datetime, timezone
from optimization_suite import ROOT, sha, write_json, properties, replay


def run(suite):
    previous=json.loads((suite/'diagnostics/results.json').read_text(encoding='utf-8'))
    assert len(previous)==13 and previous[-1]['profile'] and previous[-1]['status']=='FAIL'
    assert not list((suite/'diagnostics').glob('profile-retry-*/configuration.json')), 'Only one JVM correction is permitted'
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    out=suite/'diagnostics'/('profile-retry-'+stamp);out.mkdir()
    env=json.loads((suite/'environment.json').read_text(encoding='utf-8'))
    classes=json.loads((suite/'diagnostics/compile-retry.json').read_text(encoding='utf-8'))['precompiled_class']
    recording='generated/'+suite.name+'/profile-retry.jfr'
    command=[env['java_executable'],'-Xmx1024m','-Dwattai.fast=true',
             '-XX:StartFlightRecording=settings=profile,filename='+recording+',duration=55s,dumponexit=true',
             '-cp',env['classes']+os.pathsep+classes,'OptimizationDiagnostics','9',
             'generated/'+suite.name+'/shared-555T',out.relative_to(ROOT).as_posix(),'false']
    row=dict(previous[-1]);row.pop('exit_code',None);row.pop('parent_ms',None);row.pop('status',None);row.pop('replay',None)
    row.update(command=command,correction='relative ASCII recording path; original startup failure retained',
               runner_sha256=sha(Path(__file__)),retry_timestamp=stamp)
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
    if row['status']!='SOLVED' and (out/'preparation.properties').exists():row.update(properties(out/'preparation.properties'))
    row['replay']=replay(row);write_json(out/'result.json',row)
    if (ROOT/recording).exists():
        result=subprocess.run([env['java_executable'],'-cp',env['classes']+os.pathsep+classes,'OptimizationDiagnostics',
             'summarize',recording,(suite/'diagnostics/jfr-search-samples.txt').relative_to(ROOT).as_posix()],cwd=ROOT,capture_output=True,timeout=30)
        with (out/'summary.log').open('xb') as log:log.write(result.stdout+result.stderr)
        if result.returncode:raise RuntimeError('JFR summary failed; keep the raw recording locally')
    write_json(suite/'diagnostics/all-results.json',previous+[row])
    print(row['status'],row.get('elapsed_ms','MISSING'),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('suite',type=Path);a=p.parse_args();run((ROOT/a.suite).resolve())
