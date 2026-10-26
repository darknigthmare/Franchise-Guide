#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
# QA never inherits the operator's live Steam API credential.
unset FG_STEAM_API_KEY FG_RETRO_API_KEY FG_TMDB_API_KEY FG_TMDB_READ_TOKEN FG_RAWG_API_KEY FG_TRAKT_CLIENT_ID FG_TRAKT_CLIENT_SECRET
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
cd "$ROOT"
python3 tools/validate_project.py
python3 tests/bootstrap_security.py
for f in web/*.js; do node --check "$f"; done
node --test tests/*.test.cjs
kotlinc app/src/main/java/com/franchiseguide/app/model/*.kt app/src/main/java/com/franchiseguide/app/franchise/FranchiseEngine.kt tests/TrackingRulesTest.kt tests/TrackingV08Test.kt tests/IntegrationRulesTest.kt tests/CoreLogicTest.kt tests/ReleaseRulesTest.kt tests/GamingRulesTest.kt tests/SteamMetadataTest.kt tests/ActivityRulesTest.kt tests/LibraryRulesTest.kt tests/ImportReviewRulesTest.kt tests/FranchiseRulesTest.kt tests/InsightsProgressRulesTest.kt tests/PersonalListsTest.kt tests/EpisodeBatchRulesTest.kt tests/WatchAvailabilityRulesTest.kt tests/EpisodeDetailRulesTest.kt tests/PublicTvDetailRulesTest.kt tests/BookEditionRulesTest.kt tests/BookSelectionRulesTest.kt tests/ReliabilityRulesTest.kt tests/SettingsRulesTest.kt tests/DiscoveryRulesV033Test.kt tests/FranchiseScanRulesTest.kt tests/TraktCheckInRulesTest.kt tests/BackupTodayRulesTest.kt tests/ProtectedBackupTest.kt tests/ProtectedBackupInterop.kt -include-runtime -d "$TMP/core.jar"
java -cp "$TMP/core.jar" EpisodeDetailRulesTestKt "$TMP/episode-detail-parity.txt"
java -cp "$TMP/core.jar" BookSelectionRulesTestKt "$TMP/selection-parity.txt"
node tests/book-selection-parity.cjs "$TMP/selection-parity.txt"
java -cp "$TMP/core.jar" BookEditionRulesTestKt "$TMP/book-parity.txt"
node tests/book-editions-parity.cjs "$TMP/book-parity.txt"
java -cp "$TMP/core.jar" PublicTvDetailRulesTestKt "$TMP/public-tv-parity.txt"
node tests/tvmaze-detail-parity.cjs "$TMP/public-tv-parity.txt"
node tests/episode-detail-parity.cjs "$TMP/episode-detail-parity.txt"
java -cp "$TMP/core.jar" WatchAvailabilityRulesTestKt "$TMP/availability-parity.json"
node tests/availability-parity.cjs "$TMP/availability-parity.json"
java -cp "$TMP/core.jar" EpisodeBatchRulesTestKt "$TMP/episodes-parity.json"
node tests/episodes-parity.cjs "$TMP/episodes-parity.json"
java -cp "$TMP/core.jar" PersonalListsTestKt "$TMP/lists-parity.json"
node tests/lists-parity.cjs "$TMP/lists-parity.json"
java -cp "$TMP/core.jar" ReliabilityRulesTestKt
java -cp "$TMP/core.jar" TraktCheckInRulesTestKt "$TMP/checkin-parity.txt"
node tests/checkin-parity.cjs "$TMP/checkin-parity.txt"
java -cp "$TMP/core.jar" BackupTodayRulesTestKt
java -cp "$TMP/core.jar" ProtectedBackupTestKt "$TMP/protected-parity"
node tests/protected-backup-parity.cjs "$TMP/protected-parity"
java -cp "$TMP/core.jar" ProtectedBackupInteropKt "$TMP/protected-parity"
java -cp "$TMP/core.jar" TrackingRulesTestKt
java -cp "$TMP/core.jar" TrackingV08TestKt
java -cp "$TMP/core.jar" CoreLogicTestKt
java -cp "$TMP/core.jar" IntegrationRulesTestKt
java -cp "$TMP/core.jar" ReleaseRulesTestKt
java -cp "$TMP/core.jar" GamingRulesTestKt
java -cp "$TMP/core.jar" SteamMetadataTestKt
java -cp "$TMP/core.jar" LibraryRulesTestKt
java -cp "$TMP/core.jar" ImportReviewRulesTestKt "$TMP/import-parity.json"
java -cp "$TMP/core.jar" InsightsProgressRulesTestKt "$TMP/insights-parity.json"
node tests/insights-parity.cjs "$TMP/insights-parity.json"
java -cp "$TMP/core.jar" FranchiseRulesTestKt "$TMP/franchise-parity.txt"
node tests/franchise-parity.cjs "$TMP/franchise-parity.txt"
node tests/import-count-parity.cjs "$TMP/import-parity.json"
java -cp "$TMP/core.jar" ActivityRulesTestKt "$TMP/calendar-parity.ics"
node tests/calendar-parity.cjs "$TMP/calendar-parity.ics"
# Codec validation uses the real JSON implementation, not signatures.
: "${FG_JSON_JAR:?Set FG_JSON_JAR to org.json:json:20240303 (see docs/QA_REPORT.md)}"
kotlinc app/src/main/java/com/franchiseguide/app/model/*.kt app/src/main/java/com/franchiseguide/app/data/GamingCodec.kt app/src/main/java/com/franchiseguide/app/data/MediaIdentityCodec.kt app/src/main/java/com/franchiseguide/app/data/BookSelectionCodec.kt tests/GamingCodecRetroTest.kt tests/MediaIdentityCodecTest.kt -classpath "$FG_JSON_JAR" -include-runtime -d "$TMP/codec.jar"
java -cp "$TMP/codec.jar:$FG_JSON_JAR" GamingCodecRetroTestKt
java -cp "$TMP/codec.jar:$FG_JSON_JAR" MediaIdentityCodecTestKt
bash tools/typecheck_native.sh
bash tools/build_pc.sh
python3 tests/desktop_http.py
python3 tests/desktop_startup.py
javac --release 17 -d "$TMP/steam-classes" desktop/src/com/franchiseguide/desktop/*.java tests/SteamBridgeTest.java tests/SteamAuthTest.java tests/RetroBridgeTest.java tests/AutoServicesTest.java
java -cp "$TMP/steam-classes" com.franchiseguide.desktop.SteamBridgeTest
java -cp "$TMP/steam-classes" com.franchiseguide.desktop.SteamAuthTest
java -cp "$TMP/steam-classes" com.franchiseguide.desktop.RetroBridgeTest
java -cp "$TMP/steam-classes" com.franchiseguide.desktop.AutoServicesTest
python3 tests/browser_harness.py --chromium "${CHROMIUM_EXECUTABLE:-/usr/bin/chromium}"
python3 tests/browser_v08.py
python3 tests/browser_v09.py
python3 tests/browser_v010.py
python3 tests/browser_v011.py
python3 tests/browser_v012.py
python3 tests/browser_v015.py
python3 tests/browser_v016.py
python3 tests/browser_v017.py
python3 tests/browser_v018.py
python3 tests/browser_v019.py
python3 tests/browser_v020.py
python3 tests/browser_v021.py
python3 tests/browser_v022.py
python3 tests/browser_v023.py
python3 tests/browser_v024.py
python3 tests/browser_v025.py
python3 tests/browser_v026.py
python3 tests/browser_v027.py
python3 tests/browser_v028.py
python3 tests/browser_v029.py
python3 tests/browser_v030.py
python3 tests/browser_v031.py
python3 tests/browser_v033.py
python3 tests/browser_v034.py
python3 tests/browser_v035.py
echo 'All declared V0.35 checks passed; see docs/QA_REPORT.md for scope and untested platforms.'
