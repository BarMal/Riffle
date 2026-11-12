package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class DockAppearanceTest {
    @Test
    fun anOlderStoredDockEffectIsIgnoredWhenALayoutIsDecoded() {
        val layout = HomeLayoutDefaults.standard()
        for (effect in listOf("FLAT", "ELEVATED", "OUTLINED", "from_the_future")) {
            val encoded = org.json.JSONObject(encodeHomeLayout(layout).toString())
            encoded.getJSONObject("dock").put("visualEffect", effect)

            assertEquals(layout, decodeHomeLayout(encoded.toString()))
        }
    }

    @Test
    fun theDockNoLongerWritesAnEffect() {
        val encoded = org.json.JSONObject(encodeHomeLayout(HomeLayoutDefaults.standard()).toString())

        assertEquals(false, encoded.getJSONObject("dock").has("visualEffect"))
    }
}
