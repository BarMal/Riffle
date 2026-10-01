package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemImageKeyParserTest {
    private fun identity(profile: AppProfile) =
        AppIdentity(AppPackageName("com.example.mail"), AppActivityName("com.example.mail.Main"), profile)

    @Test
    fun parsesAnActivityIconForThePersonalProfile() {
        val identity = identity(AppProfile.personal())

        val parsed = ItemImageKeyParser.parse(ItemImageKeys.appIcon(identity))

        assertEquals(ParsedImageKey.ActivityIcon(identity), parsed)
    }

    @Test
    fun parsesProfileIdsThatContainColons() {
        val profile = AppProfile(AppProfileId("user:10"), AppProfileType.WORK)

        val parsed = ItemImageKeyParser.parse(ItemImageKeys.appIcon(identity(profile))) as ParsedImageKey.ActivityIcon

        assertEquals(AppProfileId("user:10"), parsed.identity.profile.id)
        assertEquals("com.example.mail", parsed.identity.packageName.value)
        assertEquals("com.example.mail.Main", parsed.identity.activityName.value)
    }

    @Test
    fun parsesAPackageIcon() {
        val handle = ItemImageKeys.packageIcon(AppPackageName("com.example.chat"), AppProfileId("user:11"))

        assertEquals(ParsedImageKey.PackageIcon("user:11", "com.example.chat"), ItemImageKeyParser.parse(handle))
    }

    @Test
    fun artworkAndMalformedKeysAreUnsupported() {
        listOf(
            ItemImageKeys.feedArtwork("abc"),
            ItemImageKeys.notificationArtwork(AppProfileId("personal"), "key"),
            ItemImageHandle("app-icon:"),
            ItemImageHandle("app-icon:personal:com.example"),
            ItemImageHandle("app-icon:com.example/Main"),
            ItemImageHandle("package-icon:onlyone"),
            ItemImageHandle("something-else"),
        ).forEach { handle ->
            assertEquals(handle.key, ParsedImageKey.Unsupported, ItemImageKeyParser.parse(handle))
        }
    }
}
