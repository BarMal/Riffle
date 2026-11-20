package com.riffle.app

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The system creates the activity with its no-argument constructor before it has a base context. A field initialiser
 * that touches the context (its content resolver, package manager, resources) crashes every launch, and no other test
 * constructs the real activity, so this one does exactly what the system does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityConstructionTest {
    @Test
    fun theActivityCanBeConstructedBeforeItHasAContext() {
        assertNotNull(MainActivity::class.java.getDeclaredConstructor().newInstance())
    }
}
