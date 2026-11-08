package com.franchiseguide.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.franchiseguide.app.data.AppRepository
import org.junit.*
import org.junit.runner.RunWith

/** Actual Compose UI, but deliberately never presses the final destructive OS action. */
@RunWith(AndroidJUnit4::class)
class PrivacyComposeInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val repo get() = AppRepository.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
    @Before fun setup() { compose.setContent { MaterialTheme { NativePrivacyPanel(repo) } } }
    @Test fun twoIndependentConfirmationsAreRequired() {
        compose.onNodeWithTag("privacy-open").performClick()
        compose.onNodeWithTag("privacy-erase").assertIsNotEnabled()
        compose.onNodeWithTag("privacy-confirmation").performTextInput("EFFACER")
        compose.onNodeWithTag("privacy-erase").assertIsNotEnabled()
        compose.onNodeWithTag("privacy-acknowledge").performClick()
        compose.onNodeWithTag("privacy-erase").assertIsEnabled()
        compose.onNodeWithTag("privacy-cancel").performClick()
    }
    @Test fun cancelAndReopenResetBothConfirmationsWithoutChangingLibrary() {
        val before=repo.exportJson()
        compose.onNodeWithTag("privacy-open").performClick()
        compose.onNodeWithTag("privacy-confirmation").performTextInput("EFFACER")
        compose.onNodeWithTag("privacy-acknowledge").performClick()
        compose.onNodeWithTag("privacy-cancel").performClick()
        compose.onNodeWithTag("privacy-open").performClick()
        compose.onNodeWithTag("privacy-confirmation").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("privacy-acknowledge").assertIsOff()
        compose.onNodeWithTag("privacy-erase").assertIsNotEnabled()
        Assert.assertEquals(before,repo.exportJson())
    }
    @Test fun preparationFreezesWritesAndCancellationRestoresThem() {
        val before=repo.exportJson()
        compose.runOnIdle {
            Assert.assertTrue(runCatching { repo.prepareLocalErase("effacer",true) }.isFailure)
            Assert.assertTrue(runCatching { repo.prepareLocalErase("EFFACER",false) }.isFailure)
            repo.prepareLocalErase("EFFACER",true)
            try { Assert.assertTrue(repo.createLocalBackup().isFailure) } finally { repo.cancelLocalErase() }
            Assert.assertEquals(before,repo.exportJson())
            Assert.assertTrue(repo.createLocalBackup().isSuccess)
        }
    }
}
