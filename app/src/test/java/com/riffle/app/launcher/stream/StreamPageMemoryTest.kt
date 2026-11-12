package com.riffle.app.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamPageMemoryTest {
    @Test
    fun theLastPageIsKeptPerPostureAndAPhoneInLandscapeSharesThePhones() {
        val memory = StreamPageMemory()
        assertNull(memory.last(HomeLayoutDeviceClass.PHONE))

        memory.remember(HomeLayoutDeviceClass.PHONE, "notifications/chat:personal")
        memory.remember(HomeLayoutDeviceClass.FOLDABLE, "tasks")

        assertEquals("notifications/chat:personal", memory.last(HomeLayoutDeviceClass.PHONE))
        assertEquals("notifications/chat:personal", memory.last(HomeLayoutDeviceClass.PHONE_LANDSCAPE))
        assertEquals("tasks", memory.last(HomeLayoutDeviceClass.FOLDABLE))
        assertNull(memory.last(HomeLayoutDeviceClass.TABLET))
    }
}
