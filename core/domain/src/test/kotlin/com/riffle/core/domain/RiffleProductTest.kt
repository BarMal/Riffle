package com.riffle.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RiffleProductTest {
    @Test
    fun exposesProductName() {
        assertEquals("Riffle", RiffleProduct.DISPLAY_NAME)
    }
}
