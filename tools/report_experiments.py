"""Repeat the report matrix on the installed JDK with explicit 60-second deadlines."""
import hashlib
import json
import platform
import random
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import optimization_suite as suite_tools

ROOT = Path(__file__).resolve().parents[1]


def write(path, value):
    suite_tools.write_json(path, value)


def plan(suite):
    configs = [('BASIC', 0)] + [(m, c) for m in ['TT', 'ZOBRIST']
                              for c in [100000, 500000, 1000000]]
    rows = []

    def add(group, dataset, mode='BASIC', cap=0, fast=True, algorithm='IDA',
            heuristic='DISJOINT_PATTERN', rep=1, cold=False):
        rows.append(dict(group=group, dataset=dataset, mode=mode,
                         transposition_limit=cap, fast=fast, algorithm=algorithm,
                         heuristic=heuristic, repetition=rep,
                         pattern='555T' if dataset in ['preflight', 'hard'] else
                         ('44' if dataset == 'teacher3' else '4443'),
                         input_file='resources/problems_teacher_stage1.txt' if dataset == 'teacher3'
                         else 'resources/problems3.txt',
                         input_index=0 if dataset == 'teacher3' else (9 if dataset == 'hard' else 3),
                         timeout_s=60, cache_state=('cold' if cold else 'warm')
                         if heuristic == 'DISJOINT_PATTERN' else 'not_applicable'))

    for i, (mode, cap) in enumerate(configs):
        add('preflight', 'preflight', mode, cap, cold=i == 0)
    rng = random.Random(20261107)
    for rep in range(1, 4):
        block = [(m, c, True) for m, c in configs] + [('BASIC', 0, False)]
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
    for n, row in enumerate(rows, 1):
        row['label'] = (f'{n:03}-{row["group"]}-{row["dataset"]}-{row["algorithm"]}-'
                        f'{row["heuristic"]}-{row["mode"]}-{row["transposition_limit"]}-'
                        f'fast{str(row["fast"]).lower()}-r{row["repetition"]}')
    write(suite / 'plan.json', {'seed': 20261107, 'runs': rows, 'total': len(rows),
          'deadline_s': 60, 'hard_primary_cache': 'private warm 555T; hashes verified',
          'heuristic_cache': 'new cold directory for each PDB run',
          'repetitions': 3, 'statistics': 'descriptive; failures excluded from successful medians'})
    return rows


def main():
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    suite = ROOT / 'results' / ('optimization-report-' + stamp)
    suite.mkdir(exist_ok=False)
    print('SUITE', suite.relative_to(ROOT).as_posix(), flush=True)
    (ROOT / 'generated/report-current-20261107/suite.txt').write_text(
        suite.relative_to(ROOT).as_posix(), encoding='utf-8')
    sources = suite_tools.java_sources()
    write(suite / 'source-manifest.json', sources)
    write(suite / 'runner-manifest.json', [
        {'path': p, 'sha256': suite_tools.sha(ROOT / p)} for p in
        ['tools/report_experiments.py', 'tools/optimization_suite.py', 'tools/compile.ps1',
         'tools/OptimizationDiagnostics.java']])
    classes = 'build-report-' + stamp
    with (suite / 'clean-build.log').open('xb') as log:
        result = subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                                 '-File', 'tools/compile.ps1', '-OutputDirectory', classes],
                                cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=90)
    if result.returncode:
        raise RuntimeError('Clean build failed; log preserved')
    settings = subprocess.run(['java', '-XshowSettings:properties', '-version'], cwd=ROOT,
                              capture_output=True, text=True).stderr
    import re
    java = Path(re.search(r'java.home = (.+)', settings).group(1).strip()) / 'bin/java.exe'
    java_version = subprocess.run([str(java), '-version'], capture_output=True, text=True).stderr
    cpu = subprocess.run(['powershell', '-NoProfile', '-Command',
                          'Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name'],
                         capture_output=True, text=True, timeout=20).stdout.strip()
    write(suite / 'environment.json', {'java': java_version, 'java_executable': str(java),
          'os': platform.platform(), 'cpu': cpu, 'heap_argument': '-Xmx1024m',
          'classes': classes, 'base_commit': suite_tools.git(ROOT, 'rev-parse', 'HEAD'),
          'source_sha256': suite_tools.sha(suite / 'source-manifest.json'),
          'python': platform.python_version(), 'compile_release': 19,
          'timing': 'search includes synchronous snapshots; parent includes prepare and replay',
          'deadline_s': 60})
    tests = []
    for path in sorted((ROOT / 'tests').glob('*.java')):
        command = [str(java), '-Xmx1024m', '-Dwattai.fast=true', '-cp', classes, path.stem]
        if path.stem == 'PuzzleOptimizationTest':
            command.append((suite / 'tests/correctness').relative_to(ROOT).as_posix())
        with (suite / ('test-' + path.stem + '.log')).open('xb') as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=180)
        tests.append({'test': path.stem, 'exit_code': result.returncode,
                      'status': 'PASS' if result.returncode == 0 else 'FAIL'})
        print('TEST', path.stem, tests[-1]['status'], flush=True)
        if result.returncode:
            write(suite / 'tests-failed.json', tests)
            raise RuntimeError('Test failed; no performance run started')
    write(suite / 'tests.json', tests)
    suite_tools.plan = plan
    suite_tools.run_experiments(suite)

    # Match the report's 2x2 fast/recorder controls, without a CPU profiler.
    compiled = subprocess.run([str(java.with_name('javac.exe')), '--release', '19',
                                '-cp', classes, '-d', classes, 'tools/OptimizationDiagnostics.java'],
                               cwd=ROOT, capture_output=True, timeout=60)
    (suite / 'diagnostics-compile.log').write_bytes(compiled.stdout + compiled.stderr)
    if compiled.returncode:
        raise RuntimeError('Diagnostics compilation failed')
    controls = []
    rng = random.Random(20261107)
    for rep in range(1, 4):
        block = [(f, r) for f in [False, True] for r in [False, True]]
        rng.shuffle(block)
        for fast, snapshots in block:
            out = suite / 'diagnostics' / f'r{rep}-fast{fast}-snapshots{snapshots}'
            out.mkdir(parents=True)
            command = [str(java), '-Xmx1024m', '-Dwattai.fast=' + str(fast).lower(),
                       '-cp', classes, 'OptimizationDiagnostics', '3',
                       'generated/' + suite.name + '/shared-555T',
                       out.relative_to(ROOT).as_posix(), str(snapshots).lower()]
            row = dict(fast=fast, snapshots=snapshots, profile=False, repetition=rep,
                       dataset='ordinary4', algorithm='IDA', heuristic='DISJOINT_PATTERN',
                       pattern='555T', mode='BASIC', transposition_limit=0,
                       input_file='resources/problems3.txt', input_index=3,
                       timeout_s=60, cache_state='warm', command=command)
            write(out / 'configuration.json', row)
            import time
            start = time.perf_counter()
            with (out / 'worker.log').open('xb') as log:
                process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
                try:
                    process.wait(timeout=60)
                    row['status'] = 'SOLVED' if process.returncode == 0 else 'FAIL'
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait()
                    row['status'] = 'TIMEOUT'
            row['parent_ms'] = (time.perf_counter() - start) * 1000
            row['exit_code'] = process.returncode
            if row['status'] == 'SOLVED':
                row.update(suite_tools.properties(out / 'metrics.properties'))
                row['replay'] = suite_tools.replay(row)
            write(out / 'result.json', row)
            controls.append(row)
            print('CONTROL', rep, fast, snapshots, row['status'], flush=True)
    write(suite / 'diagnostics/all-results-final.json', controls)
    final_sources = suite_tools.java_sources()
    if final_sources != sources:
        raise RuntimeError('Source changed during execution')
    rows = [json.loads(p.read_text(encoding='utf-8')) for p in (suite / 'runs').glob('*/result.json')]
    paths = [suite_tools.replay(r) for r in rows if r['status'] == 'SOLVED']
    write(suite / 'verification.json', {'status': 'PASS', 'source_frozen': True,
          'runs': len(rows), 'statuses': {s: sum(r['status'] == s for r in rows)
                                        for s in sorted({r['status'] for r in rows})},
          'java_tests': tests, 'successful_paths_replayed': len(paths),
          'controls': len(controls), 'scope': 'Correctness and evidence checks; not all performance configs succeed'})
    print('REPORT_EXPERIMENTS_COMPLETE', suite.relative_to(ROOT).as_posix(), flush=True)


if __name__ == '__main__':
    main()
