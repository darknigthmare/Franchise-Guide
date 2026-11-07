import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.InvocationTargetException

/** Run the existing executable rule suites with the real org.json implementation.
 * These are JVM tests, not a phone or provider-account acceptance test.
 */
@RunWith(Parameterized::class)
class NativeRegressionSuitesTest(private val suite: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<String>> = listOf(
            arrayOf("ActivityRulesTestKt"),
            arrayOf("BackupTodayRulesTestKt"),
            arrayOf("BookEditionRulesTestKt"),
            arrayOf("BookSelectionRulesTestKt"),
            arrayOf("CoreLogicTestKt"),
            arrayOf("DiscoveryRulesV033TestKt"),
            arrayOf("EpisodeAiringRulesTestKt"),
            arrayOf("EpisodeBatchRulesTestKt"),
            arrayOf("EpisodeDetailRulesTestKt"),
            arrayOf("FranchiseRulesTestKt"),
            arrayOf("FranchiseScanRulesTestKt"),
            arrayOf("GamingCodecRetroTestKt"),
            arrayOf("GamingRulesTestKt"),
            arrayOf("ImportReviewRulesTestKt"),
            arrayOf("InsightsProgressRulesTestKt"),
            arrayOf("IntegrationRulesTestKt"),
            arrayOf("LibraryRulesTestKt"),
            arrayOf("MediaIdentityCodecTestKt"),
            arrayOf("PersonalListsTestKt"),
            arrayOf("ProtectedBackupTestKt"),
            arrayOf("PublicTvDetailRulesTestKt"),
            arrayOf("ReleaseRulesTestKt"),
            arrayOf("ReliabilityRulesTestKt"),
            arrayOf("SettingsRulesTestKt"),
            arrayOf("SteamMetadataTestKt"),
            arrayOf("TrackingRulesTestKt"),
            arrayOf("TrackingV08TestKt"),
            arrayOf("TraktCheckInCodecTestKt"),
            arrayOf("TraktCheckInRulesTestKt"),
            arrayOf("WatchAvailabilityRulesTestKt")
        )
    }
    @Test fun domainAndJsonRegression() {
        val type = Class.forName(suite)
        try {
            val noArgs = type.methods.firstOrNull { it.name == "main" && it.parameterCount == 0 }
            if (noArgs != null) noArgs.invoke(null)
            else type.getMethod("main", Array<String>::class.java).invoke(null, emptyArray<String>() as Any)
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }
}
