"""Bounded recorder/fast controls and one JFR CPU-sampling run, separate from primary timing."""
from pathlib import Path
import argparse
import json
import random
import subprocess
import time
import os
from optimization_suite import ROOT, write_json, sha, properties, replay


def run(suite, precompiled=None):
    assert len(list((suite/'runs').glob('*/result.json')))==80, 'Finish the primary matrix first'
    out=suite/'diagnostics'
    if (out/'plan.json').exists():raise RuntimeError('Refuse to rerun started diagnostics')
    out.mkdir(exist_ok=True)
    env=json.loads((suite/'environment.json').read_text(encoding='utf-8'))
    classes=(ROOT/precompiled).resolve() if precompiled else ROOT/('build-diagnostics-'+suite.name)
    command=[str(Path(env['java_executable']).with_name('javac.exe')),'--release','19','-cp',env['classes'],
             '-d',str(classes),'tools/OptimizationDiagnostics.java']
    if precompiled:
        assert (classes/'OptimizationDiagnostics.class').is_file()
        write_json(out/'compile-retry.json',{'status':'PASS','precompiled_class':str(classes),
                   'class_sha256':sha(classes/'OptimizationDiagnostics.class'),
                   'source_sha256':sha(ROOT/'tools/OptimizationDiagnostics.java'),
                   'reason':'Original compile.log retained. Sandbox javac could not enumerate packages; same command compiled outside sandbox. Search diagnostics still run inside the same sandbox as the primary matrix.'})
    else:
        result=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60)
        with (out/'compile.log').open('xb') as log:log.write(result.stdout+result.stderr)
        assert result.returncode==0
    cp=env['classes']+os.pathsep+str(classes)
    plan=[];rng=random.Random(20261106)
    for rep in range(1,4):
        block=[(fast,record) for fast in [False,True] for record in [False,True]];rng.shuffle(block)
        plan += [dict(fast=f,snapshots=s,repetition=rep,dataset='ordinary4',input_index=3,profile=False) for f,s in block]
    plan.append(dict(fast=True,snapshots=False,repetition=1,dataset='hard',input_index=9,profile=True))
    write_json(out/'plan.json',{'runs':plan,'source_sha256':env['source_sha256'],
               'probe_source_sha256':sha(ROOT/'tools/OptimizationDiagnostics.java'),
               'runner_sha256':sha(Path(__file__)),'primary_comparison':False,
               'limits':'15s ordinary controls; 60s parent JFR hard; recording duration55s; no limit increases'})
    records=[]
    for i,config in enumerate(plan,1):
        destination=out/f'{i:02}-fast{config["fast"]}-snapshots{config["snapshots"]}-profile{config["profile"]}'
        destination.mkdir()
        jfr=ROOT/'generated'/suite.name/'profile.jfr'
        command=[env['java_executable'],'-Xmx1024m','-Dwattai.fast='+str(config['fast']).lower()]
        if config['profile']:
            command.append('-XX:StartFlightRecording=settings=profile,filename='+jfr.relative_to(ROOT).as_posix()+',duration=55s,dumponexit=true')
        command+=['-cp',cp,'OptimizationDiagnostics',str(config['input_index']),
                  'generated/'+suite.name+'/shared-555T',destination.relative_to(ROOT).as_posix(),str(config['snapshots']).lower()]
        row=dict(config,algorithm='IDA',heuristic='DISJOINT_PATTERN',pattern='555T',mode='BASIC',transposition_limit=0,
                 input_file='resources/problems3.txt',input_sha256=sha(ROOT/'resources/problems3.txt'),
                 source_sha256=env['source_sha256'],cache_state='warm',command=command,heap='-Xmx1024m',
                 java=env['java'],cpu=env['cpu'],os=env['os'],timeout_s=60 if config['profile'] else 15)
        write_json(destination/'configuration.json',row)
        started=time.perf_counter();timeout=False
        with (destination/'worker.log').open('xb') as log:
            process=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
            try:process.wait(timeout=row['timeout_s'])
            except subprocess.TimeoutExpired:timeout=True;process.kill();process.wait()
        row['parent_ms']=(time.perf_counter()-started)*1000;row['exit_code']=process.returncode
        if timeout:row['status']='TIMEOUT'
        elif process.returncode:row['status']='OOM' if 'OutOfMemoryError' in (destination/'worker.log').read_text(errors='replace') else 'FAIL'
        else:row.update(properties(destination/'metrics.properties'))
        if row['status']!='SOLVED' and (destination/'preparation.properties').exists():
            row.update(properties(destination/'preparation.properties'))
        row['replay']=replay(row)
        write_json(destination/'result.json',row);records.append(row)
        print('DIAGNOSTIC',i,row['status'],row.get('elapsed_ms','MISSING'),flush=True)
        if config['profile'] and jfr.exists():
            subprocess.run([env['java_executable'],'-cp',cp,'OptimizationDiagnostics','summarize',str(jfr),
                            str(out/'jfr-search-samples.txt')],cwd=ROOT,check=True,timeout=30)
    write_json(out/'results.json',records)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('suite',type=Path);p.add_argument('--precompiled',type=Path)
    args=p.parse_args();run((ROOT/args.suite).resolve(),args.precompiled)
