"""Bounded diagnostic run; unchanged OptimizationWorker and search implementation.

From the repository root: python results/misplaced-60s-20261108/run_experiment.py prepare|run
Requires the existing Python environment's psutil; Java 19. Never overwrites evidence.
"""
from pathlib import Path
import csv
import hashlib
import json
import platform
import re
import shutil
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
import psutil

SUITE = Path(__file__).resolve().parent
ROOT = SUITE.parents[1]
OLD = ROOT / 'results/optimization-20261106-20261106T115705068678Z'
JAVA = Path('C:/Program Files/Java/jdk-19/bin/java.exe')
CLASSES = 'build-misplaced-60s-20261108'


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def write(p, value):
    with p.open('x', encoding='utf-8') as f:
        json.dump(value, f, ensure_ascii=False, indent=2)
        f.write('\n')


def props(p):
    return dict(s.split('=', 1) for s in p.read_text(encoding='latin1').splitlines()
                if '=' in s and not s.startswith('#')) if p.exists() else {}


def git(*args):
    return subprocess.check_output(['git', '-c', 'safe.directory=' + ROOT.as_posix(), *args],
                                   cwd=ROOT, encoding='utf-8').strip()


def sources():
    return {p.relative_to(ROOT).as_posix(): sha(p)
            for folder in ['src', 'tests', 'resources'] for p in sorted((ROOT / folder).rglob('*')) if p.is_file()}


def prepare():
    assert not (SUITE / 'experiment.json').exists(), 'Already prepared; refuse overwrite'
    history = {p.relative_to(ROOT).as_posix(): sha(p) for p in sorted((ROOT / 'results').rglob('*'))
               if p.is_file() and SUITE not in p.parents}
    protected = {p.relative_to(ROOT).as_posix(): sha(p) for folder in ['report', 'src', 'resources', 'tests', 'tools']
                 for p in sorted((ROOT / folder).rglob('*')) if p.is_file()}
    write(SUITE / 'baseline.json', {'base_commit': git('rev-parse', 'HEAD'), 'old_results': history,
                                   'protected_files': protected})
    frozen = sources()
    write(SUITE / 'source-manifest.json', frozen)
    old_manifest = json.loads((OLD / 'source-manifest.json').read_text(encoding='utf-8-sig'))
    delta15 = [x['path'] for x in old_manifest if sha(ROOT / x['path']) != x['sha256'].lower()]
    old10 = ROOT / 'results/compare-20261010T061625055Z/source-manifest.json'
    m10 = json.loads(old10.read_text(encoding='utf-8-sig'))
    delta10 = [x['path'] for x in m10 if sha(ROOT / x['path']) != x['sha256'].lower()]
    historical = []
    for p in sorted((OLD / 'runs').glob('*/configuration.json')):
        c = json.loads(p.read_text(encoding='utf-8'))
        if c['dataset'] == 'ordinary4' and c['heuristic'] == 'MISPLACED':
            assert all(c[k] == v for k, v in dict(input_file='resources/problems3.txt', input_index=3,
                mode='BASIC', transposition_limit=0, fast=True, pattern='4443', timeout_s=15).items())
            assert c['input_sha256'] == sha(ROOT / c['input_file'])
            historical.append({'configuration': c, 'result': json.loads(p.with_name('result.json').read_text(encoding='utf-8'))})
    assert len(historical) == 6
    with (ROOT / 'results/compare-20261010T061625055Z/comparison.csv').open(encoding='utf-8-sig') as f:
        rows10 = [r for r in csv.DictReader(f) if r['dataset'] == 'teacher4' and r['heuristic'] == 'MISPLACED']
    write(SUITE / 'historical-comparison.json', {'10s': rows10, '15s': historical,
        'java_source_differences_from_15s': delta15, 'java_source_differences_from_10s': delta10,
        'note': 'Archived historical values only; no historical experiments rerun.'})
    assert not [p for p in delta15 if p.startswith('src/')], 'Search/worker source drift from 15s experiment'
    with (SUITE / 'clean-build.log').open('xb') as log:
        subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'tools/compile.ps1',
                        '-OutputDirectory', CLASSES], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                       timeout=120, check=True)
    tests = []
    for name in ['PuzzleFoundationTest', 'PuzzleStage1Test', 'PuzzleStage2Test', 'BenchmarkAcceptanceTest']:
        with (SUITE / (name + '.log')).open('xb') as log:
            result = subprocess.run([str(JAVA), '-Xmx1024m', '-Dwattai.fast=true', '-cp', CLASSES, name],
                                    cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=120)
        tests.append({'test': name, 'exit_code': result.returncode})
        assert result.returncode == 0, name
    write(SUITE / 'tests.json', tests)
    version = subprocess.run([str(JAVA), '-version'], capture_output=True, encoding='utf-8').stderr
    assert 'version "19"' in version
    import winreg
    with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'HARDWARE\DESCRIPTION\System\CentralProcessor\0') as key:
        cpu = winreg.QueryValueEx(key, 'ProcessorNameString')[0].strip()
    write(SUITE / 'experiment.json', {
        'purpose': 'Diagnose whether unchanged A* / IDA* with MISPLACED solve ordinary 4x4 input index 3 within a 60-second parent deadline. This is not a teacher-mandated stage-2 time requirement.',
        'prepared_utc': datetime.now(timezone.utc).isoformat(), 'base_commit': git('rev-parse', 'HEAD'),
        'java': version, 'java_executable': str(JAVA), 'os': platform.platform(), 'cpu': cpu,
        'physical_memory_bytes': psutil.virtual_memory().total, 'python': platform.python_version(),
        'psutil': psutil.__version__, 'classes': CLASSES, 'input_file': 'resources/problems3.txt', 'input_index': 3,
        'input_line': (ROOT / 'resources/problems3.txt').read_text().splitlines()[3],
        'input_sha256': sha(ROOT / 'resources/problems3.txt'), 'heap_argument': '-Xmx1024m',
        'heuristic': 'MISPLACED', 'algorithms': ['ASTAR', 'IDA'], 'mode': 'BASIC', 'transposition_limit': 0,
        'fast': True, 'pattern': '4443', 'cache_state': 'not_applicable', 'timeout_s': 60, 'repetitions': 3,
        'order': ['ASTAR-1', 'IDA-1', 'IDA-2', 'ASTAR-2', 'ASTAR-3', 'IDA-3'],
        'worker': 'stud.g01.runner.OptimizationWorker (existing source, unchanged)',
        'timing': 'Same parent wall-clock deadline as 15s history: from before Popen to exit/kill/reap. Search elapsed_ms only from a complete worker result; TIMEOUT fields remain null.',
        'startup_timing': 'HotSpot startuptime Create VM is VM creation only, not entire launcher/preparation; parent_to_search_observed_ms is 200ms-polled phase observation, not pure JVM startup.',
        'diagnostics': 'Only added JVM unified gc/startuptime logs and parent-side process memory/CPU sampling every 200ms. No new pruning, search source or heuristic changes. Logging/sampling overhead is not independently calibrated; no exact speedup claims.',
        'node_counts': 'A* worker exposes only final counts, unavailable if forcibly killed. IDA* threshold snapshots are coherent partial lower bounds, never final timeout counts.',
        'memory': 'Windows process peak_wset / sampled rss / private bytes are process memory, not peak Java heap or live algorithm data. GC occupancy samples are not exact heap peaks.',
        'source_manifest_sha256': sha(SUITE / 'source-manifest.json'), 'runner_sha256': sha(Path(__file__)),
        'historical_source_differences_15s': delta15, 'historical_source_differences_10s': delta10})
    (SUITE / 'solutions').mkdir()
    (SUITE / 'solutions/README.md').write_text('成功运行的完整路径保存于此；若无成功运行，仅保留本说明，不生成伪造路径。\n', encoding='utf-8')
    print('PREPARED', flush=True)


def sample(process, output, started, stop, record):
    p = psutil.Process(process.pid)
    with (output / 'process-memory.csv').open('x', encoding='utf-8', newline='') as f:
        w = csv.DictWriter(f, fieldnames=['parent_elapsed_ms', 'rss_bytes', 'peak_working_set_bytes', 'private_bytes', 'cpu_user_s', 'cpu_system_s'])
        w.writeheader()
        while not stop.is_set():
            try:
                m = p.memory_info(); c = p.cpu_times()
                row = dict(parent_elapsed_ms=(time.perf_counter()-started)*1000, rss_bytes=m.rss,
                           peak_working_set_bytes=m.peak_wset, private_bytes=m.private,
                           cpu_user_s=c.user, cpu_system_s=c.system)
                w.writerow(row); f.flush()
                record.append(row)
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                break
            if 'parent_to_search_observed_ms' not in record[-1] and (output / 'worker.log').exists():
                if 'PHASE SEARCH' in (output / 'worker.log').read_text(encoding='utf-8', errors='replace'):
                    record[-1]['parent_to_search_observed_ms'] = (time.perf_counter()-started)*1000
            stop.wait(0.2)


def replay(solution, row):
    data = list(map(int, (ROOT / 'resources/problems3.txt').read_text().splitlines()[3].split()))
    n = data[0]; at = data[1:17]; goal = data[17:]
    lines = solution.read_text(encoding='utf-8').splitlines()
    header = dict(s.split('\t', 1) for s in lines[:9])
    assert header['algorithm'] == row['algorithm'] and header['heuristic'] == 'MISPLACED'
    assert list(map(int, header['goal'].split())) == goal
    steps = [s.split('\t') for s in lines[10:]]
    assert steps[0] == ['0', 'START', ' '.join(map(str, at))]
    for step, (idx, move, text) in enumerate(steps[1:], 1):
        child = list(map(int, text.split())); blank = at.index(0); other = child.index(0)
        assert int(idx) == step and sorted(child) == list(range(16))
        assert abs(blank//n-other//n)+abs(blank%n-other%n) == 1
        assert blank + {'UP':-n, 'DOWN':n, 'LEFT':-1, 'RIGHT':1}[move] == other
        expected = at.copy(); expected[blank], expected[other] = expected[other], expected[blank]
        assert expected == child
        at = child
    assert at == goal and len(steps)-1 == int(row['solution_length']) == int(row['path_cost'])
    return {'status':'PASS', 'solution_sha256':sha(solution), 'length':len(steps)-1,
            'method':'Independent Python state-swap / action / initial / goal validation'}


def run():
    config = json.loads((SUITE / 'experiment.json').read_text(encoding='utf-8'))
    frozen = json.loads((SUITE / 'source-manifest.json').read_text(encoding='utf-8'))
    assert sha(Path(__file__)) == config['runner_sha256']
    (SUITE / 'runs').mkdir(exist_ok=False)
    rows = []
    for label in config['order']:
        assert sources() == frozen, 'Sources changed'
        algo, rep = label.split('-'); output = SUITE / 'runs' / label; output.mkdir()
        rel = output.relative_to(ROOT).as_posix()
        command = [str(JAVA), '-Xmx1024m', '-Dwattai.fast=true',
            '-Xlog:gc*,startuptime=info:file=' + rel + '/jvm-gc-startup.log:uptimemillis,level,tags:filecount=0',
            '-cp', CLASSES, 'stud.g01.runner.OptimizationWorker', 'resources/problems3.txt', '3', algo,
            'MISPLACED', '4443', 'BASIC', '0', 'generated/' + SUITE.name + '/' + label, rel]
        write(output / 'configuration.json', dict(config, algorithm=algo, repetition=int(rep), command=command))
        started = time.perf_counter(); stamp = datetime.now(timezone.utc).isoformat(); timed_out = False
        with (output / 'worker.log').open('xb') as log:
            p = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            stop = threading.Event(); samples = []
            monitor = threading.Thread(target=sample, args=(p, output, started, stop, samples), daemon=True)
            monitor.start()
            try:
                p.wait(timeout=60)
            except subprocess.TimeoutExpired:
                timed_out = True
                p.kill(); p.wait(timeout=10)
            parent_ms = (time.perf_counter()-started)*1000
            stop.set(); monitor.join(timeout=5)
        text = (output / 'worker.log').read_text(encoding='utf-8', errors='replace')
        m = props(output / 'metrics.properties')
        status = ('OOM' if 'OutOfMemoryError' in text else 'TIMEOUT') if timed_out else (
            'OOM' if 'OutOfMemoryError' in text else 'FAIL' if p.returncode else m.get('status', 'FAIL'))
        row = dict(algorithm=algo, heuristic='MISPLACED', repetition=int(rep), timeout_s=60, status=status,
                   elapsed_ms=None, parent_ms=parent_ms, generated=None, expanded=None,
                   solution_length=None, exit_code=p.returncode, pid=p.pid, parent_deadline_fired=timed_out,
                   run_directory=rel, started_utc=stamp, last_snapshot_search_ms=None,
                   partial_generated=None, partial_expanded=None)
        if status == 'SOLVED':
            assert not timed_out and p.returncode == 0
            for key in ['elapsed_ms', 'generated', 'expanded', 'solution_length', 'path_cost']:
                row[key] = m[key]
            target = SUITE / 'solutions' / (label + '.tsv')
            assert not target.exists(); shutil.copyfile(output / 'solution.tsv', target)
            row['solution_file'] = target.relative_to(ROOT).as_posix()
            row['independent_replay'] = replay(target, row)
        snapshots = sorted(output.glob('snapshot-*.properties'), key=lambda p: (int(p.name.split('-')[1]), int(p.stem.split('-')[2])))
        if snapshots:
            snap = props(snapshots[-1])
            row.update(last_snapshot_file=snapshots[-1].relative_to(ROOT).as_posix(),
                last_snapshot_search_ms=snap['snapshot_search_ms'], partial_generated=snap['generated'],
                partial_expanded=snap['expanded'], partial_bound=snap['final_bound'],
                partial_peak_path_states=snap['peak_path_states'])
        row['memory_samples'] = len(samples)
        for source, key in [('rss_bytes','sampled_max_rss_bytes'), ('peak_working_set_bytes','os_peak_working_set_bytes'),
                             ('private_bytes','sampled_max_private_bytes')]:
            row[key] = max((s[source] for s in samples), default=None)
        row['parent_to_search_observed_ms'] = next((s['parent_to_search_observed_ms'] for s in samples if 'parent_to_search_observed_ms' in s), None)
        gc = (output / 'jvm-gc-startup.log').read_text(encoding='utf-8')
        vm = re.search(r'Create VM, ([\d.]+) secs', gc)
        row['jvm_create_vm_ms'] = float(vm.group(1))*1000 if vm else None
        pauses = re.findall(r'\[gc\s*\].*?GC\((\d+)\) Pause .*? ([\d.]+)ms', gc)
        row['logged_gc_pause_count'] = len(pauses)
        row['logged_gc_pause_ms'] = sum(float(ms) for _,ms in pauses)
        row['gc_pause_note'] = 'Sum of completed logged GC pauses, not all concurrent GC CPU or exact heap peak.'
        with (output / 'parent.txt').open('x', encoding='utf-8') as f:
            for key, value in dict(started_utc=stamp, pid=p.pid, timeout_s=60, end_to_end_ms=parent_ms,
                exit_code=p.returncode, status=status, deadline_fired=timed_out, killed=timed_out,
                reaped=p.poll() is not None, command=json.dumps(command, ensure_ascii=False)).items():
                f.write(f'{key}={value}\n')
        write(output / 'result.json', row)
        rows.append(row)
        print(label, status, 'parent_ms=', round(parent_ms, 3), 'search_ms=', row['elapsed_ms'],
              'partial_expanded=', row['partial_expanded'], flush=True)
    fields = ['algorithm', 'heuristic', 'repetition', 'timeout_s', 'status', 'elapsed_ms', 'parent_ms',
              'generated', 'expanded', 'solution_length', 'exit_code']
    fields += sorted(set().union(*(r.keys() for r in rows)) - set(fields) - {'independent_replay'})
    with (SUITE / 'comparison.csv').open('x', encoding='utf-8', newline='') as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction='ignore'); w.writeheader(); w.writerows(rows)
    baseline = json.loads((SUITE / 'baseline.json').read_text(encoding='utf-8'))
    for group in ['old_results', 'protected_files']:
        assert all((ROOT / p).is_file() and sha(ROOT / p) == h for p,h in baseline[group].items()), group
    write(SUITE / 'integrity.json', {'status':'PASS', 'runs':len(rows),
        'old_result_files_unchanged':len(baseline['old_results']),
        'protected_source_test_tool_resource_report_files_unchanged':len(baseline['protected_files']),
        'source_manifest_unchanged':sources()==frozen,
        'successful_independent_replays':sum(r['status']=='SOLVED' for r in rows)})
    print('COMPLETE', len(rows), flush=True)


if __name__ == '__main__':
    {'prepare':prepare, 'run':run}[sys.argv[1]]()
