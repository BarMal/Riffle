package com.riffle.app.launcher.stream.todoist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.LauncherBackupDocument
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.stream.TaskConnectionState
import com.riffle.core.domain.launcher.stream.TaskConnectionStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoistTokenStoreTest {
    private val fakeToken = "fake-token-0000"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("test_todoist", Context.MODE_PRIVATE)

    /** Reverses the bytes: not secure, only proves the stored value is the cipher's output. */
    private val cipher =
        object : TokenCipher {
            override fun encrypt(plain: ByteArray) = plain.reversedArray()

            override fun decrypt(blob: ByteArray): ByteArray? = blob.reversedArray()
        }

    @Test
    fun aTokenRoundTripsAndIsNotStoredInThePlain() {
        val store = EncryptedTodoistTokenStore(prefs, cipher)
        assertNull(store.read())
        store.save(TodoistToken(fakeToken))
        assertEquals(TodoistToken(fakeToken), EncryptedTodoistTokenStore(prefs, cipher).read())
        prefs.all.values.forEach { assertFalse(it.toString().contains(fakeToken)) }
    }

    @Test
    fun clearRemovesIt() {
        val store = EncryptedTodoistTokenStore(prefs, cipher)
        store.save(TodoistToken(fakeToken))
        store.clear()
        assertNull(store.read())
        assertNull(EncryptedTodoistTokenStore(prefs, cipher).read())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun anUnreadableBlobReadsAsNoTokenAndIsDropped() {
        val broken =
            object : TokenCipher {
                override fun encrypt(plain: ByteArray) = plain

                override fun decrypt(blob: ByteArray): ByteArray? = null
            }
        EncryptedTodoistTokenStore(prefs, cipher).save(TodoistToken(fakeToken))
        assertNull(EncryptedTodoistTokenStore(prefs, broken).read())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun theTokenNeverShowsInToString() {
        val token = TodoistToken(fakeToken)
        assertFalse(token.toString().contains(fakeToken))
        assertFalse("$token".contains(fakeToken))
        assertFalse(TasksSettingsState(TaskConnectionState.CONNECTED).toString().contains(fakeToken))
        assertFalse(
            HttpRequest(
                "GET",
                "https://x",
                mapOf("Authorization" to "Bearer $fakeToken"),
            ).toString().contains(fakeToken),
        )
    }

    @Test
    fun theControllerSavesTrimmedRemovesAndReportsState() {
        val store = EncryptedTodoistTokenStore(prefs, cipher)
        val status = TaskConnectionStatus()
        var changes = 0
        val controller = TasksSettingsController(store, status, { it.run() }, onTokenChanged = { changes++ })
        controller.save("   ")
        controller.save("two words")
        assertNull(store.read())
        controller.save("  $fakeToken \n")
        assertEquals(TodoistToken(fakeToken), store.read())
        assertEquals(TaskConnectionState.CONNECTED, controller.state.value.connection)
        controller.remove()
        assertNull(store.read())
        assertEquals(TaskConnectionState.NOT_CONNECTED, controller.state.value.connection)
        assertEquals(2, changes)
        status.set(TaskConnectionState.TOKEN_REJECTED)
        assertEquals(TaskConnectionState.TOKEN_REJECTED, controller.state.value.connection)
    }

    @Test
    fun theBackupDocumentHasNoTokenAndNoTaskData() {
        val json =
            encodeLauncherBackupDocument(
                LauncherBackupDocument(
                    homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                    launcherSettings = LauncherSettings(),
                ),
            )
        assertFalse(json.contains(TODOIST_TOKEN_PREFS_NAME))
        assertFalse(json.contains("todoist", ignoreCase = true))
        assertEquals(setOf("type", "version", "homeLayouts", "settings", "hiddenApps"), JSONObject(json).keySet())
    }

    @Test
    fun theTokenFileIsExcludedFromBothBackupMechanisms() {
        val rule = """<exclude domain="sharedpref" path="$TODOIST_TOKEN_PREFS_NAME.xml" />"""
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(legacy.contains(rule))
        val cloud = modern.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = modern.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        assertTrue(cloud.contains(rule))
        assertTrue(transfer.contains(rule))
        assertEquals("riffle_todoist_token", TODOIST_TOKEN_PREFS_NAME)
    }
}
