#!/usr/bin/env python3
"""Require real, successful testcases, not merely a positive testsuite counter."""
import argparse
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
INSTRUMENTED = {
    "freshSecretsAreCiphertextAndSurviveReopen", "legacyStringsMigrateTogetherWithoutChangingMetadata",
    "unknownEnvelopePreservesOriginalAndBlocksAllWrites", "alteredCiphertextPreservesOriginalAndDoesNotResetVault",
    "ciphertextCannotMoveBetweenPreferenceNames", "missingKeystoreKeyDoesNotCreateReplacementOnRead",
    "markedPlaintextFailsClosedWithoutReencryptingIt", "removalIsDurableAndLibraryStorageIsUntouched",
}
UI_INSTRUMENTED = {"twoIndependentConfirmationsAreRequired", "cancelAndReopenResetBothConfirmationsWithoutChangingLibrary", "preparationFreezesWritesAndCancellationRestoresThem"}

def expected_native():
    source = (ROOT / 'app/src/test/java/NativeRegressionSuitesTest.kt').read_text()
    names = re.findall(r'arrayOf\("([^"\n]+)"\)', source)
    if len(names) < 32 or len(set(names)) != len(names):
        raise ValueError('Native suite registry missing, reduced, or duplicated')
    return set(names)

def check(root, instrumented=False):
    files = sorted(Path(root).rglob('TEST-*.xml'))
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    identities, native, device, ui = set(), set(), set(), set()
    for path in files:
        try:
            suite = ET.parse(path).getroot()
            if suite.tag != 'testsuite': raise ValueError('Expected one testsuite per Gradle report')
            declared = {k: int(suite.get(k, '0')) for k in totals}
            cases = suite.findall('testcase')
            if any(v < 0 for v in declared.values()) or declared['tests'] != len(cases):
                raise ValueError('Declared counts do not match actual testcases')
            for case in cases:
                identity = (case.get('classname', ''), case.get('name', ''))
                if not all(identity) or identity in identities: raise ValueError('Missing or duplicated testcase identity')
                identities.add(identity)
                if any(case.find(k) is not None for k in ('failure','error','skipped')):
                    raise ValueError('Failed, errored, or skipped testcase')
                if identity[0] == 'NativeRegressionSuitesTest':
                    match = re.fullmatch(r'domainAndJsonRegression\[([^\]]+)\]', identity[1])
                    if match: native.add(match.group(1))
                if identity[0] == 'com.franchiseguide.app.data.CredentialVaultInstrumentedTest':
                    device.add(identity[1])
                if identity[0] == 'com.franchiseguide.app.ui.PrivacyComposeInstrumentedTest': ui.add(identity[1])
            for key in totals: totals[key] += declared[key]
        except (ET.ParseError, TypeError) as error:
            raise ValueError(f'Unreadable test report: {path.name}') from error
    if instrumented and UI_INSTRUMENTED - ui:
        raise ValueError(f"Missing Compose cases: {sorted(UI_INSTRUMENTED-ui)}")
    required = INSTRUMENTED if instrumented else expected_native()
    observed = device if instrumented else native
    if not files or required - observed or any(totals[k] for k in ('failures','errors','skipped')):
        raise ValueError(f'Regression gate failed: {totals}; missing={sorted(required-observed)}')
    print(f"{'Android device' if instrumented else 'Native JVM'} results verified: {totals}; {len(required)} required cases")
    return totals

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', nargs='?', default='app/build/test-results/testDebugUnitTest')
    parser.add_argument('--instrumented', action='store_true')
    args=parser.parse_args()
    check(args.root, args.instrumented)
