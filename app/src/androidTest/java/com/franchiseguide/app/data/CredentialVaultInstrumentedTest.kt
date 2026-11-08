package com.franchiseguide.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import java.security.KeyStore

/** Executed on an isolated emulator; never targets the user's installed application. */
@RunWith(AndroidJUnit4::class)
class CredentialVaultInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("franchise_guide_credentials", Context.MODE_PRIVATE)
    private val marker = "__fg_credentials_encryption_version"
    private fun eraseTestKey() { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("franchiseguide.credentials.aes.v1") } }
    @Before fun before() { check(prefs.edit().clear().commit()); eraseTestKey() }
    @After fun after() { check(prefs.edit().clear().commit()); eraseTestKey() }
    @Test fun freshSecretsAreCiphertextAndSurviveReopen() {
        val vault=ProtectedCredentials.open(context)
        Assert.assertNull(vault.problem)
        Assert.assertTrue(vault.edit().putString("trakt_access_token", "fictional-private-token").putLong("expires", 123L).commit())
        val raw=prefs.getString("trakt_access_token", "")!!
        Assert.assertTrue(raw.startsWith(CredentialCipher.PREFIX)); Assert.assertFalse(raw.contains("fictional-private-token"))
        val reopened=ProtectedCredentials.open(context)
        Assert.assertEquals("fictional-private-token", reopened.getString("trakt_access_token",null))
        Assert.assertEquals(123L,reopened.getLong("expires",0L))
    }
    @Test fun legacyStringsMigrateTogetherWithoutChangingMetadata() {
        check(prefs.edit().putString("settings", "{\"key\":\"fictional\"}").putString("trakt_access_token", "old-fictional").putBoolean("flag",true).commit())
        val vault=ProtectedCredentials.open(context)
        Assert.assertNull(vault.problem); Assert.assertEquals("old-fictional",vault.getString("trakt_access_token",null))
        Assert.assertTrue(prefs.getString("settings", "")!!.startsWith(CredentialCipher.PREFIX))
        Assert.assertTrue(vault.getBoolean("flag",false)); Assert.assertEquals(1,prefs.getInt(marker,0))
    }
    @Test fun unknownEnvelopePreservesOriginalAndBlocksAllWrites() {
        check(prefs.edit().putString("token", "fgenc:v99:unreadable").putString("other","plain-original").commit())
        val original=HashMap(prefs.all);val vault=ProtectedCredentials.open(context)
        Assert.assertNotNull(vault.problem); Assert.assertFalse(vault.edit().putString("token","replacement").commit())
        Assert.assertEquals(original,prefs.all)
    }
    @Test fun alteredCiphertextPreservesOriginalAndDoesNotResetVault() {
        val vault=ProtectedCredentials.open(context);check(vault.edit().putString("token","fictional").commit())
        check(prefs.edit().putString("token",CredentialCipher.PREFIX+"AAAA").commit())
        val original=HashMap(prefs.all);val reopened=ProtectedCredentials.open(context)
        Assert.assertNotNull(reopened.problem); Assert.assertNull(reopened.getString("token",null));Assert.assertEquals(original,prefs.all)
    }
    @Test fun ciphertextCannotMoveBetweenPreferenceNames() {
        val vault=ProtectedCredentials.open(context);check(vault.edit().putString("first","one").putString("second","two").commit())
        val first=prefs.getString("first",null);val second=prefs.getString("second",null)
        check(prefs.edit().putString("first",second).putString("second",first).commit())
        Assert.assertNotNull(ProtectedCredentials.open(context).problem)
        Assert.assertEquals(second,prefs.getString("first",null))
    }
    @Test fun missingKeystoreKeyDoesNotCreateReplacementOnRead() {
        val vault=ProtectedCredentials.open(context);check(vault.edit().putString("token","fictional").commit());eraseTestKey()
        val original=HashMap(prefs.all);val reopened=ProtectedCredentials.open(context)
        Assert.assertNotNull(reopened.problem);Assert.assertEquals(original,prefs.all)
        Assert.assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias("franchiseguide.credentials.aes.v1"))
    }
    @Test fun markedPlaintextFailsClosedWithoutReencryptingIt() {
        check(prefs.edit().putInt(marker,1).putString("token","plain-in-marked-vault").commit())
        val vault=ProtectedCredentials.open(context)
        Assert.assertNotNull(vault.problem);Assert.assertEquals("plain-in-marked-vault",prefs.getString("token",null))
    }
    @Test fun removalIsDurableAndLibraryStorageIsUntouched() {
        val library=context.getSharedPreferences("franchise_guide",Context.MODE_PRIVATE)
        val original=HashMap(library.all)
        val vault=ProtectedCredentials.open(context);check(vault.edit().putString("token","fictional").commit());check(vault.edit().remove("token").commit())
        Assert.assertNull(ProtectedCredentials.open(context).getString("token",null));Assert.assertFalse(prefs.contains("token"));Assert.assertEquals(original,library.all)
    }
}
