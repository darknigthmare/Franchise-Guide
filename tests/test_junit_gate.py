"""Regression tests for the native CI result gate; no Android runtime required."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("native_gate", Path(__file__).resolve().parents[1] / "tools/check_junit_results.py")
gate = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gate)

class NativeResultGateTest(unittest.TestCase):
    def check_report(self, tests=30, failures=0, errors=0, skipped=0, valid=False):
        with tempfile.TemporaryDirectory() as root:
            if tests is not None:
                Path(root, "TEST-fixture.xml").write_text(
                    f'<testsuite tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}"/>')
            with contextlib.redirect_stdout(io.StringIO()):
                if valid:
                    self.assertEqual(gate.check(root)["tests"], tests)
                else:
                    with self.assertRaises(ValueError):
                        gate.check(root)
    def test_absent_reports_fail(self): self.check_report(tests=None)
    def test_missing_suite_fails(self): self.check_report(tests=29)
    def test_failed_assertion_fails(self): self.check_report(failures=1)
    def test_error_fails(self): self.check_report(errors=1)
    def test_skipped_suite_fails(self): self.check_report(skipped=1)
    def test_complete_success_passes(self): self.check_report(valid=True)

if __name__ == '__main__': unittest.main()
