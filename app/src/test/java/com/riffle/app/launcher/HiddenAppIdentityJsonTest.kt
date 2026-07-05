package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenAppIdentityJsonTest {
    @Test
    fun encodesHiddenAppIdentitiesInDeterministicOrder() {
        val workCamera =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".MainActivity",
                profile = AppProfile(AppProfileId("company"), AppProfileType.WORK),
            )
        val personalCalendar =
            identity(
                packageName = "com.riffle.calendar",
                activityName = ".MainActivity",
                profile = AppProfile.personal(),
            )
        val cameraSettings =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".SettingsActivity",
                profile = AppProfile.personal(),
            )
        val personalCamera =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".MainActivity",
                profile = AppProfile.personal(),
            )

        val encoded =
            encodeHiddenAppIdentities(
                linkedSetOf(
                    personalCalendar,
                    workCamera,
                    cameraSettings,
                    personalCamera,
                ),
            )

        assertEquals(
            encoded,
            encodeHiddenAppIdentities(
                linkedSetOf(
                    workCamera,
                    personalCamera,
                    personalCalendar,
                    cameraSettings,
                ),
            ),
        )
        assertEquals(
            listOf(
                "com.riffle.calendar/.MainActivity/PERSONAL/personal",
                "com.riffle.camera/.MainActivity/PERSONAL/personal",
                "com.riffle.camera/.MainActivity/WORK/company",
                "com.riffle.camera/.SettingsActivity/PERSONAL/personal",
            ),
            encoded.hiddenAppJsonKeys(),
        )
    }

    @Test
    fun roundTripsHiddenAppIdentitiesWithPersonalAndWorkProfiles() {
        val personalCamera =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".MainActivity",
                profile = AppProfile.personal(),
            )
        val workCamera =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".MainActivity",
                profile = AppProfile(AppProfileId("company"), AppProfileType.WORK),
            )

        assertEquals(
            setOf(personalCamera, workCamera),
            decodeHiddenAppIdentities(encodeHiddenAppIdentities(setOf(personalCamera, workCamera))),
        )
    }

    @Test
    fun decodesLegacyHiddenAppIdentitiesAsPersonalProfile() {
        val encoded =
            JSONArray()
                .put(
                    mapOf(
                        "packageName" to "com.riffle.camera",
                        "activityName" to ".MainActivity",
                    ),
                )
                .toString()

        assertEquals(
            setOf(
                identity(
                    packageName = "com.riffle.camera",
                    activityName = ".MainActivity",
                    profile = AppProfile.personal(),
                ),
            ),
            decodeHiddenAppIdentities(encoded),
        )
    }

    @Test
    fun ignoresMalformedHiddenAppIdentityRowsWithoutDroppingValidRows() {
        val personalCamera =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".MainActivity",
                profile = AppProfile.personal(),
            )
        val workDocs =
            identity(
                packageName = "com.riffle.docs",
                activityName = ".DocsActivity",
                profile = AppProfile(AppProfileId("company"), AppProfileType.WORK),
            )
        val encoded =
            JSONArray()
                .put(personalCamera.toJsonMap())
                .put(mapOf("packageName" to "com.riffle.broken"))
                .put(
                    mapOf(
                        "packageName" to "com.riffle.invalid",
                        "activityName" to ".MainActivity",
                        "profileId" to "unknown",
                        "profileType" to "NOT_A_PROFILE",
                    ),
                )
                .put(workDocs.toJsonMap())
                .toString()

        assertEquals(
            setOf(personalCamera, workDocs),
            decodeHiddenAppIdentities(encoded),
        )
    }

    private fun identity(
        packageName: String,
        activityName: String,
        profile: AppProfile,
    ): AppIdentity =
        AppIdentity(
            packageName = AppPackageName(packageName),
            activityName = AppActivityName(activityName),
            profile = profile,
        )

    private fun AppIdentity.toJsonMap(): Map<String, String> =
        mapOf(
            "packageName" to packageName.value,
            "activityName" to activityName.value,
            "profileId" to profile.id.value,
            "profileType" to profile.type.name,
        )

    private fun String.hiddenAppJsonKeys(): List<String> {
        val array = JSONArray(this)
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            listOf(
                json.getString("packageName"),
                json.getString("activityName"),
                json.getString("profileType"),
                json.getString("profileId"),
            ).joinToString("/")
        }
    }
}
