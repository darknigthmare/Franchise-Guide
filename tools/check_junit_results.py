#!/usr/bin/env python3
"""Fail a green build when JVM regression results are missing, skipped, or failed."""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

def check(root):
    files = sorted(Path(root).glob("TEST-*.xml"))
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in files:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, "0"))
    if totals["tests"] < 30 or any(totals[key] for key in ("failures", "errors", "skipped")):
        raise ValueError(f"Native regression gate failed: {totals}; {len(files)} reports")
    print(f"Native JVM regression results verified: {totals}")
    return totals

if __name__ == "__main__":
    check(sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest")
