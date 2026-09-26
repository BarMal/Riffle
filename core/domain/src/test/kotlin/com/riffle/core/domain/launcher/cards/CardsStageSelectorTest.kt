package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardsStageSelectorTest {
    private val mail = stageId("mail")
    private val chat = stageId("chat")
    private val notes = stageId("notes")

    @Test
    fun allIsAlwaysTheFirstEntryEvenWithNoStages() {
        val entries = CardsStageSelector.entries(stages = emptyList(), selection = CardsStageSelection.None)

        assertEquals(listOf(CardsStageSelector.ALL_ENTRY_KEY), entries.map(CardsStageSelectorEntry::key))
        assertEquals(CardsStageSelectorEntry.All(cardCount = 0, isSelected = false), entries.single())
    }

    @Test
    fun stagesFollowAllInThePlannersOrderIncludingAPinnedEmptyStage() {
        val stages =
            listOf(
                pinned(notes),
                dynamic(mail, cardCount = 2),
                dynamic(chat, cardCount = 1),
            )

        val entries = CardsStageSelector.entries(stages, CardsStageSelection.Stage(mail))

        assertEquals(
            listOf(
                CardsStageSelectorEntry.All(cardCount = 3, isSelected = false),
                CardsStageSelectorEntry.Stage(notes, cardCount = 0, isPinned = true, isSelected = false),
                CardsStageSelectorEntry.Stage(mail, cardCount = 2, isPinned = false, isSelected = true),
                CardsStageSelectorEntry.Stage(chat, cardCount = 1, isPinned = false, isSelected = false),
            ),
            entries,
        )
        assertEquals(2, CardsStageSelector.selectedIndex(entries))
    }

    @Test
    fun selectingAllMarksOnlyTheAllEntry() {
        val entries = CardsStageSelector.entries(listOf(dynamic(mail, 1)), CardsStageSelection.All)

        assertEquals(listOf(true, false), entries.map(CardsStageSelectorEntry::isSelected))
        assertEquals(0, CardsStageSelector.selectedIndex(entries))
    }

    @Test
    fun nothingSelectedHasNoSelectedIndex() {
        val entries = CardsStageSelector.entries(listOf(dynamic(mail, 1)), CardsStageSelection.None)

        assertEquals(-1, CardsStageSelector.selectedIndex(entries))
    }

    @Test
    fun everyEntryKeyResolvesBackToItsSelection() {
        val stages = listOf(pinned(notes), dynamic(mail, 1))

        CardsStageSelector.entries(stages, CardsStageSelection.None).forEach { entry ->
            val expected =
                when (entry) {
                    is CardsStageSelectorEntry.All -> CardsStageSelection.All
                    is CardsStageSelectorEntry.Stage -> CardsStageSelection.Stage(entry.stageId)
                }
            assertEquals(expected, CardsStageSelector.resolve(entry.key, stages))
        }
    }

    @Test
    fun unknownOrStaleKeysResolveToNothing() {
        val stages = listOf(dynamic(mail, 1))

        assertNull(CardsStageSelector.resolve("notifications:com.riffle.mail:0", stages))
        assertNull(CardsStageSelector.resolve(CardsStageSelector.stageEntryKey(chat), stages))
    }

    @Test
    fun dockItemOffersShowForAnExistingStageAndPinWhenNotPinned() {
        val stages = listOf(dynamic(mail, 1), pinned(notes))

        assertEquals(
            listOf(CardsDockItemStageAction.SHOW_STAGE, CardsDockItemStageAction.PIN_STAGE),
            CardsStageSelector.dockItemStageActions(mail, stages),
        )
        assertEquals(
            listOf(CardsDockItemStageAction.SHOW_STAGE),
            CardsStageSelector.dockItemStageActions(notes, stages),
        )
        assertEquals(
            listOf(CardsDockItemStageAction.PIN_STAGE),
            CardsStageSelector.dockItemStageActions(chat, stages),
        )
    }

    @Test
    fun spineIsOptInButFallsBackWhenTheDockCannotHostTheSelector() {
        assertFalse(CardsStageSelector.showsSpine(spineEnabled = false, dockHostsSelector = true))
        assertTrue(CardsStageSelector.showsSpine(spineEnabled = true, dockHostsSelector = true))
        assertTrue(CardsStageSelector.showsSpine(spineEnabled = false, dockHostsSelector = false))
    }

    @Test
    fun addableAppsSearchLikeTheDrawerAndSkipPinnedStages() {
        val apps =
            listOf(
                app("mail", "Mail"),
                app("chat", "Chat"),
                app("maps", "Maps"),
                app("hidden", "Mailer Hidden", visibility = AppVisibility.HIDDEN),
            )

        assertEquals(
            listOf("Maps"),
            CardsStageSelector.addableApps(apps, pinnedStageIds = setOf(mail), query = "ma").map(InstalledApp::label),
        )
        assertEquals(
            listOf("Chat", "Maps"),
            CardsStageSelector.addableApps(apps, pinnedStageIds = setOf(mail), query = "").map(InstalledApp::label),
        )
    }

    @Test
    fun addableAppsListEachStageOnceEvenWithSeveralLauncherActivities() {
        val apps =
            listOf(
                app("mail", "Mail"),
                app("mail", "Mail", activity = "Compose"),
            )

        assertEquals(1, CardsStageSelector.addableApps(apps, pinnedStageIds = emptySet(), query = "").size)
    }

    private fun stageId(name: String) =
        AppStageId(packageName = AppPackageName("com.riffle.$name"), profileId = AppProfile.personal().id)

    private fun pinned(id: AppStageId) = AppStage(id, setOf(AppStageOrigin.PINNED), AppStageLifecycle.EMPTY)

    private fun dynamic(
        id: AppStageId,
        cardCount: Int,
    ) = AppStage(
        id = id,
        origins = setOf(AppStageOrigin.DYNAMIC),
        lifecycle = AppStageLifecycle.ACTIVE,
        content =
            (0 until cardCount).map { index ->
                AppStageContent(
                    id = LauncherCardId("${id.packageName.value}-$index"),
                    stageId = id,
                    kind = AppStageContentKind.NOTIFICATION,
                    meaningfulActivityAtEpochMillis = index.toLong(),
                )
            },
    )

    private fun app(
        name: String,
        label: String,
        activity: String = "Launcher",
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ) = InstalledApp(
        identity =
            AppIdentity(
                packageName = AppPackageName("com.riffle.$name"),
                activityName = AppActivityName("com.riffle.$name.$activity"),
            ),
        label = label,
        visibility = visibility,
    )
}
