"""Verify retained course evidence without changing source data.

Requires Python 3 and an already compiled Java 19 class directory.
Teacher references are optional, local read-only inputs and never publication inputs.
"""
from pathlib import Path
import argparse
import csv
import hashlib
import json
import math
import re
import subprocess
import sys
import zipfile
from xml.etree import ElementTree as ET
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(classes, teacher_root=None):
    errors = []
    protected = []
    manifest = ROOT / 'results/submission-20261010/teacher-protected-files.csv'
    with manifest.open(encoding='utf-8-sig', newline='') as stream:
        for row in csv.DictReader(stream):
            current = ROOT / row['path']
            expected = row['teacher_sha256'].lower()
            passed = current.is_file() and digest(current) == expected
            if teacher_root:
                original = teacher_root / row['path']
                passed = passed and original.is_file() and digest(original) == expected
            protected.append({'path': row['path'], 'identical': passed})
            if not passed:
                errors.append('Teacher protected bytes differ: ' + row['path'])
    if len(protected) != 31:
        errors.append('Expected exactly 31 protected files')

    sources = json.loads((ROOT / 'report/data_sources.json').read_text(encoding='utf-8'))
    hashes = json.loads((ROOT / 'report/data_hashes.json').read_text(encoding='utf-8'))
    for key, name in sources.items():
        if key == 'date':
            continue
        path = ROOT / name
        if not path.is_file() or hashes.get(key) != digest(path):
            errors.append('Report source/hash mismatch: ' + name)

    csv_count = 0
    status_rows = {}
    for path in sorted((ROOT / 'results').rglob('*.csv')):
        csv_count += 1
        with path.open(encoding='utf-8-sig', newline='') as stream:
            reader = csv.DictReader(stream)
            if not reader.fieldnames or len(reader.fieldnames) != len(set(reader.fieldnames)):
                errors.append('Invalid CSV header: ' + str(path.relative_to(ROOT)))
                continue
            for number, row in enumerate(reader, 2):
                if None in row or any(value is None for value in row.values()):
                    errors.append(f'CSV column mismatch: {path.relative_to(ROOT)}:{number}')
                status = row.get('status')
                if status:
                    status_rows[status] = status_rows.get(status, 0) + 1
                solution = row.get('solution_file')
                if solution and not (ROOT / solution).is_file():
                    errors.append('Missing CSV solution: ' + solution)
                if status == 'SOLVED':
                    for key in ['elapsed_ms', 'solution_length', 'path_cost', 'generated', 'expanded']:
                        if row.get(key):
                            value = float(row[key])
                            if not math.isfinite(value) or value < 0:
                                errors.append('Invalid SOLVED metric: ' + key)

    traces = []
    for path in sorted((ROOT / 'results/solutions').glob('*.tsv')):
        command = ['java', '-Xmx1024m', '-cp', str(classes),
                   'stud.g01.visual.PuzzleVisualizer', '--validate', str(path.relative_to(ROOT))]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=15)
        output = result.stdout.decode('ascii', errors='replace').strip()
        traces.append({'path': path.relative_to(ROOT).as_posix(), 'exit': result.returncode,
                       'output': output, 'sha256': digest(path)})
        if result.returncode or not output.startswith('TRACE_VALID='):
            errors.append('Invalid replay: ' + str(path.relative_to(ROOT)))

    # Only live Markdown navigation is interpreted as attachments. Historical JSON
    # inventories/cleanup mappings intentionally describe earlier trees.
    links = 0
    for path in [ROOT / 'README.md', *(ROOT / 'docs').glob('*.md'),
                 *(ROOT / 'report').glob('*.md'), *(ROOT / 'results').glob('*.md')]:
        text = path.read_text(encoding='utf-8-sig')
        for raw in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)', text):
            target = raw.strip().strip('<>')
            if target.startswith(('http:', 'https:', 'mailto:', '#')):
                continue
            target = unquote(target.split('#', 1)[0])
            links += 1
            if not (path.parent / target).exists():
                errors.append(f'Missing Markdown target: {path.relative_to(ROOT)} -> {target}')

    source_manifests = []
    for key in ['cold', 'final_cold']:
        if key not in sources:
            continue
        path = (ROOT / sources[key]).with_name('source-manifest.json')
        identity = json.loads(path.read_text(encoding='utf-8-sig'))
        same = all((ROOT / item['path']).is_file() and
                   digest(ROOT / item['path']) == item['sha256'].lower() for item in identity)
        source_manifests.append({'path': path.relative_to(ROOT).as_posix(), 'matches_source': same})
        if not same:
            errors.append('Source identity changed: ' + str(path.relative_to(ROOT)))
    with zipfile.ZipFile(ROOT / 'report/NPuzzle研究报告.docx') as archive:
        xml = ET.fromstring(archive.read('word/document.xml'))
        docx_text = ''.join(node.text or '' for node in xml.iter()
                            if node.tag.endswith('}t'))
        media = {hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist()
                 if name.startswith('word/media/')}
        for key in ['gui_teacher3', 'gui_hard4']:
            if key in sources and hashes[key] not in media:
                errors.append('Report screenshot missing or changed: ' + key)
        normalize = lambda text: ''.join(char for char in text if char.isalnum())
        normalized_docx = normalize(docx_text)
        markdown = (ROOT / 'report/正文.md').read_text(encoding='utf-8')
        for paragraph in markdown.split('\n\n'):
            paragraph = paragraph.strip()
            if not paragraph or paragraph.startswith(('#', '|', '![')):
                continue
            if normalize(paragraph) not in normalized_docx:
                errors.append('Report Markdown/DOCX paragraph differs: ' + paragraph[:40])

    return {'status': 'PASS' if not errors else 'FAIL', 'errors': errors,
            'protected_files': protected, 'teacher_local_rechecked': bool(teacher_root),
            'csv_files': csv_count, 'status_row_counts_with_summary_duplicates': status_rows,
            'validated_solution_paths': traces, 'markdown_links': links,
            'report_sources_sha256': hashes, 'source_manifests': source_manifests}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes', type=Path, default=Path('build-test'))
    parser.add_argument('--teacher-root', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = verify(args.classes, args.teacher_root)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'{result["status"]}: protected={len(result["protected_files"])}, '
          f'CSV={result["csv_files"]}, paths={len(result["validated_solution_paths"])}, '
          f'links={result["markdown_links"]}')
    for error in result['errors']:
        print(error, file=sys.stderr)
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
