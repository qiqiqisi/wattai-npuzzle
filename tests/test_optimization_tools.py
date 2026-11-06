"""Regression tests for censored-time exclusion and independent identity/path rejection."""
from pathlib import Path
import json
import sys
import unittest
import uuid
import ast

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from analyze_optimization import summarize
from optimization_suite import replay


class OptimizationToolsTest(unittest.TestCase):
    def test_archived_builder_refuses_new_supplement(self):
        destination=ROOT/'generated'/('report-guard-'+uuid.uuid4().hex)
        destination.mkdir(parents=True)
        (destination/'正文.md').write_text('<!-- OPTIMIZATION_20261106_SUPPLEMENT_START -->',encoding='utf-8')
        module=ast.parse((ROOT/'tools/build_report.py').read_text(encoding='utf-8'))
        function=next(node for node in module.body if isinstance(node,ast.FunctionDef) and node.name=='build')
        namespace={'REPORT':destination}
        exec(compile(ast.Module(body=[function],type_ignores=[]),'build_report.py','exec'),namespace)
        with self.assertRaisesRegex(RuntimeError,'overwrite the new optimization supplement'):
            namespace['build']()
        self.assertEqual(list(destination.iterdir()),[destination/'正文.md'])

    def test_failures_are_not_success_times(self):
        rows=[{'mode':'BASIC','status':'SOLVED','elapsed_ms':'10','parent_ms':15},
              {'mode':'BASIC','status':'TIMEOUT','parent_ms':60020},
              {'mode':'BASIC','status':'SOLVED','elapsed_ms':'30','parent_ms':35},
              {'mode':'BASIC','status':'OOM','parent_ms':15000}]
        result=summarize(rows,['mode'])[0]
        self.assertEqual(result['elapsed_ms'],20)
        self.assertEqual(result['parent_ms'],25)
        self.assertEqual(result['solved'],2)
        self.assertEqual(result['success_rate'],.5)
        missing=summarize([rows[1],rows[3]],['mode'])[0]
        self.assertIsNone(missing['elapsed_ms'])
        self.assertIsNone(missing['parent_ms'])

    def test_wrong_input_and_illegal_move_rejected(self):
        suite=ROOT/'results/optimization-20261106-20261106T115705068678Z'
        source=next((suite/'runs').glob('001-*/result.json'))
        row=json.loads(source.read_text(encoding='utf-8'))
        self.assertEqual(replay(row)['length'],52)
        wrong=dict(row,input_index=9)
        with self.assertRaises(AssertionError):replay(wrong)
        destination=ROOT/'generated'/('path-rejection-'+uuid.uuid4().hex)
        destination.mkdir(parents=True)
        text=(ROOT/row['solution_file']).read_text(encoding='utf-8').splitlines()
        cells=text[11].split('\t');cells[1]='DOWN' if cells[1]=='UP' else 'UP';text[11]='\t'.join(cells)
        bad=destination/'illegal.tsv';bad.write_text('\n'.join(text)+'\n',encoding='utf-8')
        with self.assertRaises(AssertionError):replay(dict(row,solution_file=bad.relative_to(ROOT).as_posix()))


if __name__=='__main__':unittest.main(verbosity=2)
