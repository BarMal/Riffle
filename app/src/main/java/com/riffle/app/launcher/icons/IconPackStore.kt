package com.riffle.app.launcher.icons

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.icons.AppFilterTag
import com.riffle.core.domain.launcher.icons.IconPackAppFilter
import com.riffle.core.domain.launcher.icons.IconPackIndex
import com.riffle.core.domain.launcher.icons.IconPackMapping
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/** An icon pack found on the device: its package, its name and the icon it shows in the list. */
data class InstalledIconPack(
    val packageName: String,
    val label: String,
)

/** Where the pack store reads a pack's files from; the platform in the app, a fake in tests. */
internal interface IconPackSource {
    fun installedPacks(): List<InstalledIconPack>

    /** The pack's `appfilter.xml` as tags, or null when it has none or it cannot be read. */
    fun appFilterTags(packPackage: String): List<AppFilterTag>?

    /** The pack's `drawable.xml` as tags, or null. */
    fun drawableTags(packPackage: String): List<AppFilterTag>?

    /** The pack's drawable called [name], or null when it has none or it cannot be decoded. */
    fun drawable(
        packPackage: String,
        name: String,
    ): Drawable?

    /** The pack's icon for the list of packs. */
    fun packIcon(packPackage: String): Drawable?

    /** Changes whenever the pack is installed, updated or removed; null when it is not installed. */
    fun versionOf(packPackage: String): Long?
}

/**
 * The installed icon packs and what they say. A pack's `appfilter.xml` is read once per pack version, off the main
 * thread, and held in memory; it is a cache that is rebuilt after a restart and never stored or backed up. A pack that
 * is removed or updated reads as such on the next [onCatalogChanged]: icons fall back at once and the new version is
 * read again. [version] is state, so anything that drew with a pack redraws when a pack finished loading or changed.
 */
@Suppress("TooManyFunctions", "ReturnCount")
internal class IconPackStore(
    private val source: IconPackSource,
) : IconPackIndex {
    private class Loaded(
        val version: Long,
        val mapping: IconPackMapping,
        val drawableNames: List<String>,
    )

    private val loaded = ConcurrentHashMap<String, Loaded>()
    private val loading = ConcurrentHashMap.newKeySet<String>()

    // The version of each pack the last time it was looked at (ABSENT when it was not installed): a change is what
    // makes the icon cache key change, and isAvailable() answers from here instead of asking the platform each time.
    private val observed = ConcurrentHashMap<String, Long>()

    /** Bumped when a pack finished loading, changed, or came or went: part of the icon cache key. */
    var catalogVersion: Long by mutableLongStateOf(0L)
        private set

    /** Bumped on every package change: the list of installed packs is read again by whatever shows it. */
    var packListVersion: Long by mutableLongStateOf(0L)
        private set

    fun installedPacks(): List<InstalledIconPack> = runCatching { source.installedPacks() }.getOrDefault(emptyList())

    fun packIcon(packPackage: String): Drawable? = runCatching { source.packIcon(packPackage) }.getOrNull()

    override fun isAvailable(packPackage: String): Boolean = observedVersion(packPackage) != ABSENT

    /** What the cache key needs to know about [packPackage]: 0 when it is not read, a positive number once it is. */
    fun versionOf(packPackage: String?): Long =
        packPackage?.let { loaded[it] }?.let { it.version.coerceAtLeast(0L) + 1 } ?: 0L

    override fun drawableFor(
        packPackage: String,
        identity: AppIdentity,
    ): String? = mappingOf(packPackage)?.drawableFor(identity.packageName.value, identity.activityName.value)

    override fun dressesUnthemedApps(packPackage: String): Boolean = mappingOf(packPackage)?.dressesUnthemedApps == true

    fun mappingOf(packPackage: String): IconPackMapping? = loaded[packPackage]?.mapping

    /** The drawable names to offer in the picker: the pack's drawable list, else what its appfilter names. */
    fun drawableNames(packPackage: String): List<String> = loaded[packPackage]?.drawableNames.orEmpty()

    fun drawable(
        packPackage: String,
        name: String,
    ): Drawable? = runCatching { source.drawable(packPackage, name) }.getOrNull()

    /**
     * Looks at [packs] (the chosen pack and the packs per-app choices name) and reads [selected] when it is installed
     * and not read at its current version. Blocking: call it off the main thread.
     */
    fun watch(
        packs: Set<String>,
        selected: String?,
    ) {
        var changed = false
        packs.forEach { pack -> if (observe(pack)) changed = true }
        selected?.let { if (load(it)) changed = true }
        if (changed) bump()
    }

    /** A package was installed, updated or removed: drop what is stale and read the chosen pack again if needed. */
    fun onCatalogChanged(selected: String?) {
        var changed = false
        observed.keys.toList().forEach { pack -> if (observe(pack)) changed = true }
        loaded.keys.toList().forEach { pack ->
            val now = observed[pack] ?: ABSENT
            if (loaded[pack]?.version != now) {
                loaded.remove(pack)
                changed = true
            }
        }
        selected?.let { if (load(it)) changed = true }
        if (changed) bump()
        packListVersion += 1
    }

    private fun observedVersion(pack: String): Long =
        observed[pack] ?: run {
            observe(pack)
            observed[pack] ?: ABSENT
        }

    // True when the pack's version differs from the last time it was looked at.
    private fun observe(pack: String): Boolean {
        val now = source.versionOf(pack) ?: ABSENT
        // The first look at a pack is not a change: nothing was drawn with it yet.
        val before = observed.put(pack, now)
        return before != null && before != now
    }

    private fun load(pack: String): Boolean {
        val version = observedVersion(pack)
        if (version == ABSENT) return loaded.remove(pack) != null
        if (loaded[pack]?.version == version || !loading.add(pack)) return false
        try {
            val tags = source.appFilterTags(pack).orEmpty()
            val mapping = IconPackAppFilter.parse(pack, tags)
            val listed = source.drawableTags(pack)?.let(IconPackAppFilter::drawableList).orEmpty()
            val names =
                listed.ifEmpty { IconPackAppFilter.drawableList(tags).ifEmpty { mapping.drawables.values.distinct() } }
            loaded[pack] = Loaded(version, mapping, names)
        } finally {
            loading.remove(pack)
        }
        return true
    }

    private fun bump() {
        catalogVersion += 1
    }

    private companion object {
        const val ABSENT = -1L
    }
}

/** The platform's icon packs: found by the launcher-theme intents the common packs declare. */
internal class PackageManagerIconPackSource(
    private val packageManager: PackageManager,
    private val contextFor: (String) -> android.content.Context?,
) : IconPackSource {
    override fun installedPacks(): List<InstalledIconPack> =
        THEME_ACTIONS
            .flatMap { action -> packageManager.queryIntentActivities(Intent(action), 0) }
            .mapNotNull { info -> info.activityInfo?.packageName }
            .distinct()
            .mapNotNull { pack ->
                runCatching {
                    InstalledIconPack(
                        pack,
                        packageManager.getApplicationInfo(pack, 0).loadLabel(packageManager).toString(),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }

    override fun appFilterTags(packPackage: String): List<AppFilterTag>? = xmlTags(packPackage, "appfilter")

    override fun drawableTags(packPackage: String): List<AppFilterTag>? = xmlTags(packPackage, "drawable")

    private fun xmlTags(
        packPackage: String,
        name: String,
    ): List<AppFilterTag>? =
        runCatching {
            val resources = packageManager.getResourcesForApplication(packPackage)
            val id = resources.getIdentifier(name, "xml", packPackage)
            if (id != 0) {
                readXml(resources, id)
            } else {
                readAsset(packPackage, "$name.xml")
            }
        }.getOrNull()

    private fun readXml(
        resources: Resources,
        id: Int,
    ): List<AppFilterTag> {
        val tags = mutableListOf<AppFilterTag>()
        resources.getXml(id).use { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && tags.size < IconPackAppFilter.MAX_ENTRIES * 2) {
                if (event == XmlPullParser.START_TAG) {
                    tags +=
                        AppFilterTag(
                            parser.name,
                            (0 until parser.attributeCount).associate {
                                parser.getAttributeName(it) to parser.getAttributeValue(it)
                            },
                        )
                }
                event = parser.next()
            }
        }
        return tags
    }

    private fun readAsset(
        packPackage: String,
        file: String,
    ): List<AppFilterTag>? {
        val context = contextFor(packPackage) ?: return null
        val bytes =
            context.assets.open(file).use { input ->
                input.readNBytes(IconPackAppFilter.MAX_XML_CHARS + 1)
            }
        return if (bytes.size > IconPackAppFilter.MAX_XML_CHARS) null else IconPackAppFilter.tagsOf(String(bytes))
    }

    override fun drawable(
        packPackage: String,
        name: String,
    ): Drawable? {
        val resources = packageManager.getResourcesForApplication(packPackage)
        var id = resources.getIdentifier(name, "drawable", packPackage)
        if (id == 0) id = resources.getIdentifier(name, "mipmap", packPackage)
        if (id == 0) return null
        return runCatching { resources.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null) }
            .getOrElse { runCatching { resources.getDrawable(id, null) }.getOrNull() }
    }

    override fun packIcon(packPackage: String): Drawable? =
        runCatching {
            packageManager.getApplicationIcon(packPackage)
        }.getOrNull()

    override fun versionOf(packPackage: String): Long? =
        runCatching { packageManager.getPackageInfo(packPackage, 0).lastUpdateTime }.getOrNull()

    companion object {
        /** The intent actions icon packs declare on an activity so that launchers can find them. */
        val THEME_ACTIONS =
            listOf(
                "org.adw.launcher.THEMES",
                "org.adw.launcher.icons.ACTION_PICK_ICON",
                "com.novalauncher.THEME",
                "com.gau.go.launcherex.theme",
                "com.anddoes.launcher.THEME",
            )
    }
}
