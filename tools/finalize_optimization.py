"""Fresh final compilation, all Java suites, Python guards, and bytecode identity audit."""
import argparse
import json
import subprocess
from pathlib import Path
from datetime import datetime,timezone
from optimization_suite import ROOT,write_json,sha,java_sources


def run(suite):
    assert len(list((suite/'runs').glob('*/result.json')))==80
    assert java_sources()==json.loads((suite/'source-manifest.json').read_text(encoding='utf-8'))
    out=suite/'final-checks';out.mkdir(exist_ok=False)
    env=json.loads((suite/'environment.json').read_text(encoding='utf-8'))
    classes='build-final-optimization-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    with (out/'clean-build.log').open('xb') as log:
        result=subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File','tools/compile.ps1',
                              '-OutputDirectory',classes],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=90)
    assert result.returncode==0,'Final clean build failed'
    records=[]
    for test in sorted((ROOT/'tests').glob('*.java')):
        command=[env['java_executable'],'-Xmx1024m','-Dwattai.fast=true','-cp',classes,test.stem]
        if test.stem=='PuzzleOptimizationTest':command.append((out/'exhaustive-correctness').relative_to(ROOT).as_posix())
        with (out/(test.stem+'.log')).open('xb') as log:
            result=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=180)
        records.append({'test':test.stem,'exit':result.returncode,'status':'PASS' if not result.returncode else 'FAIL'})
        print(test.stem,records[-1]['status'],flush=True)
        if result.returncode:write_json(out/'failed-tests.json',records);raise RuntimeError('Final test failed')
    import sys
    with (out/'python-tests.log').open('xb') as log:
        result=subprocess.run([sys.executable,'tests/test_optimization_tools.py'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=30)
    assert result.returncode==0,'Python failure/identity/report guard regression failed'
    original=ROOT/env['classes'];bytecodes=[]
    for path in sorted((ROOT/classes).rglob('*.class')):
        relative=path.relative_to(ROOT/classes)
        identical=(original/relative).is_file() and sha(path)==sha(original/relative)
        bytecodes.append({'path':relative.as_posix(),'identical':identical,'sha256':sha(path)})
    assert all(item['identical'] for item in bytecodes),'Final bytecode differs from actual experiment bytecode'
    assert java_sources()==json.loads((suite/'source-manifest.json').read_text(encoding='utf-8'))
    write_json(out/'verification.json',{'status':'PASS','classes':classes,'java_tests':records,'python_tests':3,
               'source_frozen':True,'bytecodes_identical_to_actual_experiments':bytecodes,
               'archived_report_builder_guard':'tested using extracted build function and temporary sentinel fixture; no report regeneration'})
    print('FINAL_BUILD_AND_TEST_PASS',len(bytecodes),'identical class files',flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('suite',type=Path);a=p.parse_args();run((ROOT/a.suite).resolve())
