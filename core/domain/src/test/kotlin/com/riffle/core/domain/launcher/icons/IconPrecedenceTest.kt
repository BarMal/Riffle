package com.riffle.core.domain.launcher.icons

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IconPrecedenceTest {
    private val app = AppIdentity(AppPackageName("com.example.notes"), AppActivityName("com.example.notes.Main"))
    private val other = AppIdentity(AppPackageName("com.example.maps"), AppActivityName("com.example.maps.Main"))
    private val packs =
        FakePacks(
            installed = setOf("com.example.pack.one", "com.example.pack.two"),
            drawables = mapOf(("com.example.pack.one" to app) to "notes_icon"),
            dressing = setOf("com.example.pack.one"),
        )

    @Test
    fun theDefaultStyleIsTheBarePlatformIcon() {
        assertEquals(listOf(IconRecipe.Platform), IconPrecedence.candidates(IconStyleSettings(), app, packs))
        assertTrue(IconStyleSettings().isDefault)
    }

    @Test
    fun aShapeOrThemedIconsMakeTheIconStyled() {
        val shaped = IconStyleSettings(shape = IconShape.CIRCLE)
        val recipe = IconPrecedence.candidates(shaped, app, packs).single()
        assertEquals(IconRecipe.Styled(IconShape.CIRCLE, LegacyIconHandling.PLATE, false, false), recipe)
        val themed = IconPrecedence.candidates(IconStyleSettings(themedIcons = true), app, packs).single()
        assertTrue(themed is IconRecipe.Styled)
        assertFalse(shaped.isDefault)
    }

    @Test
    fun anOverrideBeatsThePackWhichBeatsTheStyle() {
        val ref = IconPackDrawableRef("com.example.pack.two", "special")
        val style =
            IconStyleSettings(shape = IconShape.SQUARE, iconPack = "com.example.pack.one")
                .withIconOverride(app, ref)

        val recipes = IconPrecedence.candidates(style, app, packs)

        assertEquals(
            listOf(
                IconRecipe.PackDrawable(ref, fromOverride = true),
                IconRecipe.PackDrawable(IconPackDrawableRef("com.example.pack.one", "notes_icon"), false),
                IconRecipe.PackComposite("com.example.pack.one"),
                IconRecipe.Styled(IconShape.SQUARE, LegacyIconHandling.PLATE, false, false),
            ),
            recipes,
        )
    }

    @Test
    fun anAppTheOverrideDoesNotNameKeepsThePack() {
        val style =
            IconStyleSettings(iconPack = "com.example.pack.one")
                .withIconOverride(app, IconPackDrawableRef("com.example.pack.two", "special"))

        val recipes = IconPrecedence.candidates(style, other, packs)

        assertEquals(listOf(IconRecipe.PackComposite("com.example.pack.one"), IconRecipe.Platform), recipes)
    }

    @Test
    fun anOverrideWhosePackIsGoneFallsBackAndIsKept() {
        val ref = IconPackDrawableRef("com.example.pack.gone", "special")
        val style = IconStyleSettings().withIconOverride(app, ref)

        assertEquals(listOf(IconRecipe.Platform), IconPrecedence.candidates(style, app, packs))
        assertEquals(ref, style.overrideFor(app)?.icon)
        val restored = FakePacks(installed = setOf("com.example.pack.gone"))
        assertEquals(IconRecipe.PackDrawable(ref, true), IconPrecedence.candidates(style, app, restored).first())
    }

    @Test
    fun aPackThatIsNotInstalledIsSkipped() {
        val style = IconStyleSettings(iconPack = "com.example.pack.gone", shape = IconShape.CIRCLE)

        assertTrue(IconPrecedence.candidates(style, app, packs).single() is IconRecipe.Styled)
    }

    @Test
    fun clearingOverridesKeepsCustomNames() {
        val style =
            IconStyleSettings()
                .withIconOverride(app, IconPackDrawableRef("com.example.pack.one", "x"))
                .withLabelOverride(app, " Jot ")
                .withIconOverride(other, IconPackDrawableRef("com.example.pack.one", "y"))

        val cleared = style.withoutIconOverrides()

        assertEquals(mapOf(app to AppIconOverride(label = "Jot")), cleared.overrides)
        assertEquals("Jot", cleared.labelFor(app, "Notes"))
        assertEquals("Maps", cleared.labelFor(other, "Maps"))
    }

    @Test
    fun theVersionChangesWithTheGlobalChoicesAndOnlyThatAppsOverrideChangesItsOwnVersion() {
        val base = IconStyleSettings()
        val shaped = base.copy(shape = IconShape.CIRCLE)
        val overridden = base.withIconOverride(app, IconPackDrawableRef("com.example.pack.one", "x"))

        assertFalse(base.styleVersion() == shaped.styleVersion())
        assertEquals(base.styleVersion(), overridden.styleVersion())
        assertFalse(base.overrideVersion(app) == overridden.overrideVersion(app))
        assertEquals(base.overrideVersion(other), overridden.overrideVersion(other))
        assertFalse(base.styleVersion(packVersion = 1) == base.styleVersion(packVersion = 2))
    }

    private class FakePacks(
        val installed: Set<String>,
        val drawables: Map<Pair<String, AppIdentity>, String> = emptyMap(),
        val dressing: Set<String> = emptySet(),
    ) : IconPackIndex {
        override fun isAvailable(packPackage: String) = packPackage in installed

        override fun drawableFor(
            packPackage: String,
            identity: AppIdentity,
        ) = drawables[packPackage to identity]

        override fun dressesUnthemedApps(packPackage: String) = packPackage in dressing
    }
}
