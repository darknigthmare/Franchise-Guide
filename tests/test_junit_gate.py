"""Adversarial result fixtures: each failure must be rejected by the production gate."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

_spec=importlib.util.spec_from_file_location('gate',Path(__file__).resolve().parents[1]/'tools/check_junit_results.py')
gate=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(gate)

class NativeResultGateTest(unittest.TestCase):
    def fixture(self, instrumented=False):
        names=sorted(gate.INSTRUMENTED if instrumented else gate.expected_native())
        root=ET.Element('testsuite',tests=str(len(names)),failures='0',errors='0',skipped='0')
        cls='com.franchiseguide.app.data.CredentialVaultInstrumentedTest' if instrumented else 'NativeRegressionSuitesTest'
        for name in names: ET.SubElement(root,'testcase',classname=cls,name=name if instrumented else f'domainAndJsonRegression[{name}]')
        if instrumented:
            for name in sorted(gate.UI_INSTRUMENTED): ET.SubElement(root,'testcase',classname='com.franchiseguide.app.ui.PrivacyComposeInstrumentedTest',name=name)
            root.set('tests', str(len(root)))
        return root
    def verify(self, root, valid=False, instrumented=False):
        with tempfile.TemporaryDirectory() as folder:
            if root is not None: ET.ElementTree(root).write(Path(folder)/'TEST-test.xml')
            with contextlib.redirect_stdout(io.StringIO()):
                if valid: self.assertEqual(gate.check(folder,instrumented)['tests'],len(root.findall('testcase')))
                else:
                    with self.assertRaises(ValueError): gate.check(folder,instrumented)
    def test_absent_reports_fail(self): self.verify(None)
    def test_all_required_native_suites_pass(self): self.verify(self.fixture(),True)
    def test_required_instrumented_cases_pass(self): self.verify(self.fixture(True),True,True)
    def test_forged_positive_summary_without_cases_fails(self): self.verify(ET.Element('testsuite',tests='1000'))
    def test_false_zero_summary_fails(self):
        r=self.fixture();r.set('tests','0');self.verify(r)
    def test_duplicate_cases_fail(self):
        r=self.fixture();r[1].set('name',r[0].get('name'));self.verify(r)
    def test_missing_suite_cannot_be_replaced_by_unrelated_case(self):
        r=self.fixture();r[0].set('classname','DifferentSuite');self.verify(r)
    def test_missing_device_case_fails(self):
        r=self.fixture(True);r[0].set('name','unrelated');self.verify(r,instrumented=True)
    def test_hidden_assertion_failure_fails_despite_zero_summary(self):
        r=self.fixture();ET.SubElement(r[0],'failure',message='deliberate-regression');self.verify(r)
    def test_hidden_error_fails_despite_zero_summary(self):
        r=self.fixture();ET.SubElement(r[0],'error');self.verify(r)
    def test_hidden_skipped_case_fails_despite_zero_summary(self):
        r=self.fixture();ET.SubElement(r[0],'skipped');self.verify(r)
    def test_reported_error_counts_fail(self):
        for k in ['failures','errors','skipped']:
            with self.subTest(k=k):
                r=self.fixture();r.set(k,'1');self.verify(r)
    def test_negative_counts_fail(self):
        r=self.fixture();r.set('failures','-1');self.verify(r)

if __name__=='__main__': unittest.main()
