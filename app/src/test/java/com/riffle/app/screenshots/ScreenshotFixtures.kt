package com.riffle.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.riffle.app.launcher.AppIconLoader
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockConfigurationEngine
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.LauncherSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic launcher state for the screenshot tests.
 *
 * Everything that could make two renders of the same test differ is fixed here: notification
 * timestamps are constants (and the UI only ever shows the pre-computed [NotificationAgeBucket],
 * never a wall-clock-relative time), icons are flat bundled shapes from [SolidColorAppIconLoader]
 * instead of whatever the Robolectric package manager would hand back, and nothing is loaded over
 * the network -- no feeds are configured and no notification carries artwork.
 */
internal object ScreenshotFixtures {
    /** A fixed "now" the notification timestamps are laid out behind; never read from a clock. */
    const val FIXED_NOW_EPOCH_MILLIS = 1_767_225_600_000L // 2026-01-01T00:00:00Z

    val camera = app("camera", "Camera")
    val mail = app("mail", "Mail")
    val chat = app("chat", "Chat")
    val browser = app("browser", "Browser")
    val calendar = app("calendar", "Calendar")
    val photos = app("photos", "Photos")
    val maps = app("maps", "Maps")
    val music = app("music", "Music")
    val clock = app("clock", "Clock")
    val notes = app("notes", "Notes")
    val weather = app("weather", "Weather")
    val files = app("files", "Files")

    val dockApps = listOf(camera, mail, chat, browser)
    val gridApps = listOf(calendar, photos, maps, music, clock, notes, weather, files)
    val installedApps = dockApps + gridApps

    val notificationGroups: List<AppNotificationGroup> =
        listOf(
            group(
                app = chat,
                category = NotificationCategory.MESSAGE,
                ageBucket = NotificationAgeBucket.NOW,
                notifications =
                    listOf(
                        notification(
                            app = chat,
                            key = "chat-alex",
                            title = "Alex Rivera",
                            text = "Are we still on for lunch?",
                            minutesAgo = 1,
                            category = NotificationCategory.MESSAGE,
                        ).copy(
                            messages =
                                listOf(
                                    message("Alex Rivera", "Found a new place near the office", minutesAgo = 4),
                                    message("Alex Rivera", "Are we still on for lunch?", minutesAgo = 1),
                                ),
                            canDismiss = true,
                        ),
                    ),
            ),
            group(
                app = mail,
                category = NotificationCategory.EMAIL,
                ageBucket = NotificationAgeBucket.RECENT,
                notifications =
                    listOf(
                        notification(mail, "mail-1", "Design review notes", "Cards spacing and dock handle", 12),
                        notification(mail, "mail-2", "Build finished", "All checks passed on main", 25),
                        notification(mail, "mail-3", "Weekly digest", "Five new releases this week", 40),
                    ),
            ),
            group(
                app = calendar,
                category = NotificationCategory.EVENT,
                ageBucket = NotificationAgeBucket.TODAY,
                notifications =
                    listOf(
                        notification(
                            app = calendar,
                            key = "calendar-standup",
                            title = "Stand-up in 10 minutes",
                            text = "Room 4 - Riffle team",
                            minutesAgo = 90,
                            category = NotificationCategory.EVENT,
                        ),
                    ),
            ),
        )

    /** Standard grid Home with a filled dock and two rows of apps on the first page. */
    fun homeState(
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
        dockPosition: DockPosition? = null,
    ): LauncherShellState =
        shellState(
            layout = homeLayout(deviceClass, dockPosition, LauncherViewMode.STANDARD_APP_DRAWER),
            deviceClass = deviceClass,
        )

    /** Cards mode with the three notifying apps as stages; the chat stage is newest and selected. */
    fun cardsState(
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
        launcherSettings: LauncherSettings = LauncherSettings(),
        notificationGroups: List<AppNotificationGroup> = this.notificationGroups,
        notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.GRANTED,
    ): LauncherShellState =
        shellState(
            layout = homeLayout(deviceClass, dockPosition = null, viewMode = LauncherViewMode.CARD_INTERFACE),
            deviceClass = deviceClass,
            launcherSettings = launcherSettings,
            notificationGroups = notificationGroups,
            notificationAccessStatus = notificationAccessStatus,
        )

    fun shortcut(
        app: InstalledApp,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(app.identity.packageName.value),
            appIdentity = app.identity,
            label = app.label,
            placement = placement,
        )

    private fun shellState(
        layout: HomeLayout,
        deviceClass: HomeLayoutDeviceClass,
        launcherSettings: LauncherSettings = LauncherSettings(),
        notificationGroups: List<AppNotificationGroup> = this.notificationGroups,
        notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.GRANTED,
    ): LauncherShellState {
        val key = HomeLayoutKey(viewMode = layout.viewMode, deviceClass = deviceClass)
        return LauncherShellState(
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout)),
            launcherSettings = launcherSettings,
            notificationAccessStatus = notificationAccessStatus,
            notificationGroupsByApp = notificationGroups,
            installedApps = installedApps,
            profileContentVisibility =
                mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
        )
    }

    private fun homeLayout(
        deviceClass: HomeLayoutDeviceClass,
        dockPosition: DockPosition?,
        viewMode: LauncherViewMode,
    ): HomeLayout {
        val seeded =
            HomeLayoutDefaults.standard(deviceClass).let { standard ->
                standard.copy(
                    viewMode = viewMode,
                    dock = standard.dock.copy(items = dockApps.map { app -> shortcut(app) }),
                )
            }
        // Through the engine, so a side dock leaves the grid the column a real position change would.
        val positioned =
            dockPosition
                ?.let { position -> DockConfigurationEngine().setDockPosition(layout = seeded, position = position) }
                ?.let { result -> (result as DockEditResult.Updated).layout }
                ?: seeded
        val firstPage = positioned.selectedPage
        val columns = firstPage.grid.columns
        val placed =
            gridApps.take(columns * 2).mapIndexed { index, app ->
                shortcut(app, GridPlacement(cell = GridCell(column = index % columns, row = index / columns)))
            }
        return positioned.copy(
            pages = positioned.pages.map { page -> if (page.id == firstPage.id) page.copy(items = placed) else page },
        )
    }

    private fun app(
        packageSuffix: String,
        label: String,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.sample.$packageSuffix"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = AppProfile.personal(),
                ),
            label = label,
        )

    private fun group(
        app: InstalledApp,
        category: NotificationCategory,
        ageBucket: NotificationAgeBucket,
        notifications: List<LauncherNotification>,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = app.identity.packageName,
            profileId = app.identity.profile.id,
            latestCategory = category,
            latestAgeBucket = ageBucket,
            notifications = notifications,
        )

    private fun notification(
        app: InstalledApp,
        key: String,
        title: String,
        text: String,
        minutesAgo: Long,
        category: NotificationCategory = NotificationCategory.EMAIL,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = app.identity.packageName,
            profileId = app.identity.profile.id,
            category = category,
            title = title,
            text = text,
            postedAtEpochMillis = minutesAgo.minutesBeforeNow(),
        )

    private fun message(
        sender: String,
        text: String,
        minutesAgo: Long,
    ): LauncherNotificationMessage =
        LauncherNotificationMessage(
            sender = sender,
            text = text,
            timestampEpochMillis = minutesAgo.minutesBeforeNow(),
        )

    private fun Long.minutesBeforeNow(): Long = FIXED_NOW_EPOCH_MILLIS - this * MILLIS_PER_MINUTE

    private const val MILLIS_PER_MINUTE = 60_000L
}

/**
 * Bundled stand-in for app icons: a flat circle in a fixed colour per app, so icons and the
 * icon-derived card accents render identically on every run and every machine.
 *
 * A class rather than an object so each test gets bitmaps created inside its own Robolectric
 * environment instead of reusing ones a previous test's sandbox allocated.
 */
internal class SolidColorAppIconLoader : AppIconLoader {
    private val palette =
        listOf(
            Color(0xFF1A73E8),
            Color(0xFFD93025),
            Color(0xFF188038),
            Color(0xFFF9AB00),
            Color(0xFF9334E6),
            Color(0xFF12B5CB),
            Color(0xFFE8710A),
            Color(0xFF5F6368),
        )

    // Concurrent: icon loads can come from Dispatchers.Default as well as the main thread.
    private val icons = ConcurrentHashMap<AppIdentity, ImageBitmap>()

    override fun iconFor(identity: AppIdentity): ImageBitmap = icons.getOrPut(identity) { circle(colorFor(identity)) }

    override fun cachedIconFor(identity: AppIdentity): ImageBitmap = iconFor(identity)

    override fun colorFor(identity: AppIdentity): Color {
        val index = ScreenshotFixtures.installedApps.indexOfFirst { app -> app.identity == identity }
        return palette[index.coerceAtLeast(0) % palette.size]
    }

    override fun cachedColorFor(identity: AppIdentity): Color = colorFor(identity)

    override fun preloadIcons(identities: List<AppIdentity>) = Unit

    private fun circle(fill: Color): ImageBitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fill.toArgb()
            }
        val radius = ICON_SIZE_PX / 2f
        Canvas(bitmap).drawCircle(radius, radius, radius, paint)
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val ICON_SIZE_PX = 96
    }
}
