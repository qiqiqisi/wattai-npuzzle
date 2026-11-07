"""Check actual staged Git blobs, including the exact raw bytes of experiment manifests."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from optimization_suite import ROOT,sha,write_json


def git(*args):
    return subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),*args],cwd=ROOT)


def batch(specs):
    specs=list(dict.fromkeys(specs))
    result=subprocess.run(['git','-c','safe.directory='+ROOT.as_posix(),'cat-file','--batch'],cwd=ROOT,
        input=('\n'.join(specs)+'\n').encode('utf-8'),stdout=subprocess.PIPE,check=True).stdout
    objects={};position=0
    for spec in specs:
        end=result.index(b'\n',position);header=result[position:end].split();assert len(header)==3 and header[1]==b'blob',spec
        size=int(header[2]);position=end+1;objects[spec]=result[position:position+size];position+=size
        assert result[position:position+1]==b'\n';position+=1
    assert position==len(result)
    return objects


def run(suite,output):
    names=git('diff','--cached','--name-only','-z').decode('utf-8').split('\0')[:-1]
    forbidden=[]
    for name in names:
        if name.startswith(('generated/','build-','teacher_reference/','.publication-','report/latex/')) or name.endswith(('.pdb','.class','.jfr','.hprof','.zip','.tmp')):
            forbidden.append(name)
        if name.startswith('results/') and name!='results/INDEX.md' and not name.startswith('results/optimization-20261106-'):
            forbidden.append(name)
    assert not forbidden,forbidden
    manifests=['source-manifest.json','runner-manifest.json','delivery-tools-manifest-final.json']
    records=[(manifest,record) for manifest in manifests for record in json.loads((suite/manifest).read_text(encoding='utf-8'))]
    baseline=json.loads((suite/'baseline.json').read_text(encoding='utf-8'))
    ending_paths=[p.as_posix() for p in Path('tests').glob('*.java') if p.name not in ['PuzzleStage2Test.java','PuzzleOptimizationTest.java']]+['tools/compile.ps1']
    objects=batch([':'+r['path'] for m,r in records]+[':'+n for n in baseline['history'] if n!='results/INDEX.md']+
                  [':'+n for n in baseline['protected']]+['HEAD:'+n for n in ending_paths]+[':'+n for n in ending_paths])
    verified=[]
    for manifest,record in records:
        digest=hashlib.sha256(objects[':'+record['path']]).hexdigest()
        assert digest==record['sha256'], 'Staged bytes differ: '+record['path']
        verified.append({'path':record['path'],'sha256':digest,'manifest':manifest})
    for name,digest in baseline['history'].items():
        if name=='results/INDEX.md':continue
        assert hashlib.sha256(objects[':'+name]).hexdigest()==digest,'Historical staged bytes changed: '+name
    for name,digest in baseline['protected'].items():
        assert hashlib.sha256(objects[':'+name]).hexdigest()==digest,'Teacher staged bytes differ: '+name
    endings=[]
    for name in ending_paths:
        before=objects['HEAD:'+name].replace(b'\r\n',b'\n')
        after=objects[':'+name].replace(b'\r\n',b'\n')
        assert before==after,'Unrelated test logic changed: '+name
        endings.append(name)
    subprocess.run(['git','-c','safe.directory='+ROOT.as_posix(),'-c','core.whitespace=cr-at-eol','diff','--cached','--check','--','.',':!results/**'],cwd=ROOT,check=True)
    write_json(output,{'status':'PASS','staged_file_count':len(names),'forbidden_paths':forbidden,
        'staged_paths_sha256':hashlib.sha256('\n'.join(sorted(names)).encode()).hexdigest(),
        'verified_manifest_blobs':verified,'history_sha256_count':len(baseline['history'])-1,
        'teacher_sha256_count':len(baseline['protected']),'line_ending_only_changes_verified':endings,
        'non_result_paths':[n for n in names if not n.startswith('results/')],
        'self_sha256':sha(Path(__file__)),'scope':'This audit file is added after verification; no history is modified.'})
    print('STAGING_PASS',len(names),'files;',len(verified),'manifest blob checks')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('suite',type=Path);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();run((ROOT/a.suite).resolve(),(ROOT/a.output).resolve())
