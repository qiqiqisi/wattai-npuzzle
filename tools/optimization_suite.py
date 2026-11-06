"""Isolated, append-free optimization experiments. Run with Python 3 from any cwd."""
from pathlib import Path
import argparse
import csv
import hashlib
import json
import platform
import subprocess
import shutil
import time
import random
import re
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]


def git(root, *args):
    return subprocess.check_output(['git', '-c', 'safe.directory=' + root.as_posix(), *args],
                                   cwd=root, text=True, encoding='utf-8').strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as out:
        json.dump(value, out, ensure_ascii=False, indent=2)
        out.write('\n')


def inventory(root, paths):
    return {p.relative_to(root).as_posix(): sha(p)
            for name in paths for p in sorted((root / name).rglob('*')) if p.is_file()}


def initialize():
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    suite = ROOT / 'results' / ('optimization-20261106-' + stamp)
    suite.mkdir(exist_ok=False)
    original = ROOT.parent
    protected = list(csv.DictReader((ROOT / 'results/submission-20261010/teacher-protected-files.csv')
                                    .open(encoding='utf-8-sig')))
    baseline = {'base_commit': git(ROOT, 'rev-parse', 'HEAD'),
                'history': inventory(ROOT, ['results']),
                'original_history': inventory(original, ['results']),
                'original_root': str(original),
                'protected': {r['path']: r['teacher_sha256'].lower() for r in protected},
                'old_caches': {p.relative_to(original).as_posix(): sha(p)
                               for p in sorted((original / 'generated').rglob('*.pdb'))},
                'original_status': git(original, 'status', '--porcelain', '-uall'),
                'original_report': inventory(original, ['report'])}
    # Do not inventory our own empty suite. No experiment evidence existed yet.
    write_json(suite / 'baseline.json', baseline)
    (ROOT / 'generated').mkdir(exist_ok=True)
    write_json(ROOT / 'generated' / ('optimization-' + stamp + '.json'), {'suite': suite.relative_to(ROOT).as_posix()})
    print(suite.relative_to(ROOT).as_posix(), flush=True)


def properties(path):
    return dict(line.strip().split('=', 1) for line in path.read_text(encoding='latin1').splitlines()
                if '=' in line and not line.startswith('#'))


def java_sources():
    return [{'path': p.relative_to(ROOT).as_posix(), 'sha256': sha(p)}
            for name in ['src', 'tests'] for p in sorted((ROOT / name).rglob('*.java'))]


def prepare(suite):
    if (suite / 'source-manifest.json').exists():
        raise RuntimeError('Already frozen; do not overwrite a suite')
    write_json(suite / 'source-manifest.json', java_sources())
    write_json(suite / 'test-manifest.json', {'tests': [p.stem for p in sorted((ROOT / 'tests').glob('*.java'))]})
    classes = 'build-optimization-' + suite.name.rsplit('-', 1)[-1]
    command = ['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'tools/compile.ps1',
               '-OutputDirectory', classes]
    with (suite / 'clean-build.log').open('xb') as out:
        build = subprocess.run(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, timeout=90)
    if build.returncode:
        raise RuntimeError('Clean compilation failed; see clean-build.log')
    settings = subprocess.run(['java', '-XshowSettings:properties', '-version'], capture_output=True, cwd=ROOT).stderr.decode('utf-8', errors='replace')
    java_executable = Path(re.search(r'java.home = (.+)', settings).group(1).strip()) / 'bin/java.exe'
    if not java_executable.is_file():
        raise RuntimeError('Cannot resolve the actual JDK executable')
    tool_paths = ['tools/optimization_suite.py', 'tools/DeadlineProbe.java', 'tools/compile.ps1']
    write_json(suite / 'runner-manifest.json', [{'path': p, 'sha256': sha(ROOT / p)} for p in tool_paths])
    subprocess.run([str(java_executable.with_name('javac.exe')), '--release', '19', '-d', classes,
                    'tools/DeadlineProbe.java'], cwd=ROOT, check=True, capture_output=True, timeout=30)
    watchdog = suite / 'tests' / 'watchdog'; watchdog.mkdir(parents=True)
    marker = watchdog / 'late-marker.txt'
    command = [str(java_executable), '-cp', classes, 'DeadlineProbe', str(watchdog / 'jvm-pid.txt'), str(marker)]
    with (watchdog / 'worker.log').open('xb') as out:
        probe = subprocess.Popen(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
        try:
            probe.wait(timeout=2)
            raise RuntimeError('Watchdog fixture returned before its delayed marker')
        except subprocess.TimeoutExpired:
            probe.kill(); probe.wait(timeout=10)
    assert int((watchdog / 'jvm-pid.txt').read_text()) == probe.pid, 'Must monitor the JVM itself, not a launcher'
    tests = []
    for test in sorted((ROOT / 'tests').glob('*.java')):
        name = test.stem
        command = [str(java_executable), '-Xmx1024m', '-Dwattai.fast=true', '-cp', classes, name]
        if name == 'PuzzleOptimizationTest':
            command.append((suite / 'tests' / 'optimization-final').relative_to(ROOT).as_posix())
        with (suite / ('test-' + name + '.log')).open('xb') as out:
            result = subprocess.run(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, timeout=180)
        tests.append({'test': name, 'exit_code': result.returncode, 'status': 'PASS' if result.returncode == 0 else 'FAIL'})
        print('TEST', name, tests[-1]['status'], flush=True)
        if result.returncode:
            write_json(suite / 'tests-failed.json', tests)
            raise RuntimeError('Test failed, counterexample/log preserved: ' + name)
    write_json(suite / 'tests.json', tests)
    assert not marker.exists(), 'A JVM survived the deadline'
    write_json(watchdog / 'verification.json', {'status': 'PASS', 'direct_jvm_pid_matches_monitored_pid': True,
               'killed_and_reaped': True, 'late_marker_absent_after_6_seconds': True})
    java = subprocess.run([str(java_executable), '-version'], capture_output=True, cwd=ROOT).stderr.decode('utf-8', errors='replace')
    if 'version "19"' not in java:
        raise RuntimeError('Java 19 required')
    cpu = subprocess.run(['powershell', '-NoProfile', '-Command',
                          'Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name'],
                         capture_output=True, timeout=20).stdout.decode('utf-8', errors='replace').strip()
    if not cpu and platform.system() == 'Windows':
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'HARDWARE\DESCRIPTION\System\CentralProcessor\0') as key:
            cpu = winreg.QueryValueEx(key, 'ProcessorNameString')[0].strip()
    write_json(suite / 'environment.json', {'java': java, 'java_executable': str(java_executable), 'os': platform.platform(), 'cpu': cpu or platform.processor(),
               'heap_argument': '-Xmx1024m', 'classes': classes, 'base_commit': git(ROOT, 'rev-parse', 'HEAD'),
               'source_sha256': sha(suite / 'source-manifest.json'), 'python': platform.python_version(),
               'timing': 'parent perf_counter from before Popen until exit/kill/reap; elapsed_ms search only',
               'timeout_metrics': 'final search fields null/blank; coherent partial snapshots retained separately'})
    print('PREPARED', suite.relative_to(ROOT).as_posix(), flush=True)


def plan(suite):
    configs = [('BASIC', 0)] + [(mode, cap) for mode in ['TT', 'ZOBRIST'] for cap in [100000, 500000, 1000000]]
    rows = []
    def add(group, dataset, mode='BASIC', cap=0, fast=True, algorithm='IDA', heuristic='DISJOINT_PATTERN', rep=1, cold=False):
        rows.append(dict(group=group, dataset=dataset, mode=mode, transposition_limit=cap, fast=fast,
                         algorithm=algorithm, heuristic=heuristic, repetition=rep,
                         pattern='555T' if dataset in ['preflight', 'hard'] else ('44' if dataset == 'teacher3' else '4443'),
                         input_file='resources/problems_teacher_stage1.txt' if dataset == 'teacher3' else 'resources/problems3.txt',
                         input_index=0 if dataset == 'teacher3' else (9 if dataset == 'hard' else 3),
                         timeout_s=60 if dataset == 'hard' else (30 if dataset == 'preflight' and cold else 15),
                         cache_state=('cold' if cold else 'warm') if heuristic == 'DISJOINT_PATTERN' else 'not_applicable'))
    for i, (mode, cap) in enumerate(configs):
        add('preflight', 'preflight', mode, cap, cold=i == 0)
    # Three blocks, deterministic shuffle in each block, fresh JVMs, no in-JVM warmup.
    rng = random.Random(20261106)
    for rep in range(1, 4):
        block = [(mode, cap, True) for mode, cap in configs] + [('BASIC', 0, False)]
        rng.shuffle(block)
        for mode, cap, fast in block:
            add('hard', 'hard', mode, cap, fast, rep=rep)
    add('hard_cold', 'hard', cold=True)
    for rep in range(1, 4):
        block = [(d, a, h) for d in ['teacher3', 'ordinary4'] for a in ['ASTAR', 'IDA']
                 for h in ['MISPLACED', 'MANHATTAN', 'MANHATTAN_LC', 'DISJOINT_PATTERN']]
        rng.shuffle(block)
        for dataset, algorithm, heuristic in block:
            add('heuristic', dataset, algorithm=algorithm, heuristic=heuristic, rep=rep, cold=True)
    for number, row in enumerate(rows, 1):
        row['label'] = f'{number:03}-{row["group"]}-{row["dataset"]}-{row["algorithm"]}-{row["heuristic"]}-{row["mode"]}-{row["transposition_limit"]}-fast{str(row["fast"]).lower()}-r{row["repetition"]}'
    write_json(suite / 'plan.json', {'seed': 20261106, 'runs': rows, 'total': len(rows),
                                   'hard_primary_cache': 'warm suite-private 555T cache built in preflight; hash checked before/after',
                                   'heuristic_cache': 'fresh private cold cache for every PDB repetition',
                                   'pdb_3x3': 'EIGHT_PUZZLE 4-4 partition (pattern label 44)',
                                   'repetitions': 3, 'significance': 'descriptive only; no population significance claim'})
    return rows


def replay(row):
    if row['status'] != 'SOLVED':
        return None
    input_line = (ROOT / row['input_file']).read_text().splitlines()[int(row['input_index'])]
    data = list(map(int, input_line.split())); n = data[0]; count = n*n
    initial, goal = data[1:count+1], data[count+1:]
    lines = (ROOT / row['solution_file']).read_text(encoding='utf-8').splitlines()
    header = dict(line.split('\t', 1) for line in lines[:9])
    assert list(map(int, header['goal'].split())) == goal
    assert header['algorithm'] == row['algorithm'] and header['heuristic'] == row['heuristic']
    assert int(header['generated']) == int(row['generated']) and int(header['expanded']) == int(row['expanded'])
    steps = [line.split('\t') for line in lines[10:]]
    assert steps[0] == ['0', 'START', ' '.join(map(str, initial))]
    at = initial
    for step, (index, move, board) in enumerate(steps[1:], 1):
        assert int(index) == step
        child = list(map(int, board.split())); assert sorted(child) == list(range(count))
        blank, other = at.index(0), child.index(0)
        assert abs(blank//n-other//n)+abs(blank%n-other%n) == 1
        delta = {'UP': -n, 'DOWN': n, 'LEFT': -1, 'RIGHT': 1}[move]
        assert blank+delta == other
        expected = at.copy(); expected[blank], expected[other] = expected[other], expected[blank]
        assert expected == child
        at = child
    length = len(steps)-1
    assert at == goal and length == int(row['solution_length']) == int(row['path_cost'])
    assert length == (70 if row['dataset'] == 'hard' else 16 if row['dataset'] == 'teacher3' else 52)
    return {'status': 'PASS', 'length': length, 'initial_goal_match': True, 'all_moves_legal': True,
            'solution_sha256': sha(ROOT / row['solution_file'])}


def run_experiments(suite):
    env = json.loads((suite / 'environment.json').read_text(encoding='utf-8'))
    if (suite / 'plan.json').exists():
        raise RuntimeError('This suite already started: use a new suite; no silent reruns')
    rows = plan(suite)
    cache = ROOT / 'generated' / suite.name / 'shared-555T'
    if cache.exists():
        raise RuntimeError('Private preflight cache unexpectedly exists')
    cache_before = None
    for config in rows:
        if java_sources() != json.loads((suite / 'source-manifest.json').read_text(encoding='utf-8')):
            raise RuntimeError('Frozen Java source changed')
        output = suite / 'runs' / config['label']; output.mkdir(parents=True, exist_ok=False)
        shared = config['group'] in ['preflight', 'hard']
        task_cache = cache if shared else ROOT / 'generated' / suite.name / config['label']
        if config['group'] == 'hard' and cache_before is None:
            cache_before = {p.name: sha(p) for p in cache.glob('*.pdb')}
            if len(cache_before) != 3:
                raise RuntimeError('Preflight did not prepare the complete three-table 555T cache')
            write_json(suite / 'cache-manifest-before-hard.json', cache_before)
        if config['cache_state'] == 'cold' and task_cache.exists():
            raise RuntimeError('Cold cache exists, refuse reuse')
        command = [env['java_executable'], '-Xmx1024m', '-Dwattai.fast=' + str(config['fast']).lower(), '-cp', env['classes'],
                   'stud.g01.runner.OptimizationWorker', config['input_file'], str(config['input_index']), config['algorithm'],
                   config['heuristic'], config['pattern'], config['mode'], str(config['transposition_limit']),
                   task_cache.relative_to(ROOT).as_posix(), output.relative_to(ROOT).as_posix()]
        row = dict(config, source_sha256=env['source_sha256'], input_sha256=sha(ROOT / config['input_file']),
                   java_version=env['java'], os=env['os'], cpu=env['cpu'], jvm_heap_limit='-Xmx1024m',
                   environment_file=(suite / 'environment.json').relative_to(ROOT).as_posix())
        write_json(output / 'configuration.json', dict(row, command=command,
                   input_line=(ROOT / config['input_file']).read_text().splitlines()[config['input_index']]))
        started = time.perf_counter(); timed_out = False
        with (output / 'worker.log').open('xb') as log:
            process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            try:
                process.wait(timeout=config['timeout_s'])
            except subprocess.TimeoutExpired:
                timed_out = True; process.kill(); process.wait()
        row['parent_ms'] = (time.perf_counter()-started)*1000
        row['exit_code'] = process.returncode
        text = (output / 'worker.log').read_text(encoding='utf-8', errors='replace')
        final = output / 'metrics.properties'
        if timed_out:
            row['status'] = 'TIMEOUT'
        elif process.returncode:
            row['status'] = 'OOM' if 'OutOfMemoryError' in text else 'FAIL'
        elif final.exists():
            row.update(properties(final))
        else:
            row['status'] = 'FAIL'
        # Only completed successful searches have final performance fields. Never promote timeout snapshots.
        if row['status'] != 'SOLVED':
            if (output / 'preparation.properties').exists():
                prep = properties(output / 'preparation.properties')
                row.update({k: v for k, v in prep.items() if k.startswith('pdb_') or k == 'prepare_ms'})
                row.pop('pdb_lookup_count', None)
            snapshots = sorted(output.glob('snapshot-*.properties'), key=lambda p: p.stat().st_mtime_ns)
            row['last_snapshot_file'] = snapshots[-1].relative_to(ROOT).as_posix() if snapshots else ''
            row['failure_reason'] = ('parent deadline; worker killed and reaped' if timed_out else
                                     'OutOfMemoryError in worker log' if row['status'] == 'OOM' else 'worker failed or no complete metrics')
        row['replay'] = replay(row)
        write_json(output / 'result.json', row)
        print(config['label'], row['status'], 'search_ms=' + str(row.get('elapsed_ms', 'MISSING')),
              'parent_ms=' + str(round(row['parent_ms'], 2)), flush=True)
        if config['group'] == 'preflight' and row['status'] != 'SOLVED':
            raise RuntimeError('Preflight failed; preserved outcome; inspect before starting Hard')
    if cache_before != {p.name: sha(p) for p in cache.glob('*.pdb')}:
        raise RuntimeError('Warm cache was modified')
    write_json(suite / 'cache-manifest-after-hard.json', cache_before)
    print('EXPERIMENTS_COMPLETE', len(rows), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['init', 'prepare', 'run'])
    parser.add_argument('--suite', type=Path)
    args = parser.parse_args()
    if args.action == 'init':
        initialize()
    else:
        if not args.suite:
            parser.error('--suite is required')
        suite = (ROOT / args.suite).resolve()
        if suite.parent != ROOT / 'results' or not suite.name.startswith('optimization-'):
            raise ValueError('Suite must be an immediate results/optimization-* directory')
        if args.action == 'prepare': prepare(suite)
        elif args.action == 'run': run_experiments(suite)
