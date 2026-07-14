package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalogDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTemplateJsonCodecTest {
    @Test
    fun roundTripsSelectedTemplateId() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                templateId = LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId,
            )

        assertEquals(layout.templateId, decodeHomeLayout(encodeHomeLayout(layout)).templateId)
    }
}
