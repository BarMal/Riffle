package com.riffle.app.screenshots.containers

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.containers.PageContainerHost
import com.riffle.app.launcher.containers.PageSetContainerHost
import com.riffle.app.launcher.containers.WidgetContainerHost
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.expressions.renderExpression
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.container.StaticLensResultProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three containers (widget, page, page-set) over fixed lens results, at compact and unfolded widths.
 * The containers are not on the home surface yet, so this is their only visual coverage.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ContainersScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val services = ContainerFixtures.services

    @Test
    fun widgetCompact() {
        renderWidget()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun widgetUnfolded() {
        renderWidget()
    }

    @Test
    fun widgetLoading() {
        renderWidget(ContainerServices(StaticLensResultProvider(LensOutput.Loading), services.environment))
    }

    @Test
    fun widgetPermissionRequired() {
        renderWidget(ContainerServices(StaticLensResultProvider(LensOutput.PermissionRequired), services.environment))
    }

    @Test
    fun boundPageCompact() {
        renderBoundPage()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun boundPageUnfolded() {
        renderBoundPage()
    }

    @Test
    fun widgetGridPageCompact() {
        renderGridPage()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun widgetGridPageCompactDark() {
        renderGridPage()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun widgetGridPageUnfolded() {
        renderGridPage()
    }

    @Test
    fun pageSetCompact() {
        renderPageSet()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun pageSetCompactLargeFont() {
        renderPageSet()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun pageSetUnfolded() {
        renderPageSet()
    }

    @Test
    fun pageSetUnavailable() {
        renderPageSet(ContainerServices(StaticLensResultProvider(LensOutput.Unavailable), services.environment))
    }

    private fun renderWidget(with: ContainerServices = services) {
        composeRule.renderExpression {
            WidgetContainerHost(container = ContainerFixtures.listWidget, services = with)
        }
    }

    private fun renderBoundPage() {
        composeRule.renderExpression {
            PageContainerHost(container = ContainerFixtures.boundPage, services = services)
        }
    }

    private fun renderGridPage() {
        composeRule.renderExpression {
            PageContainerHost(container = ContainerFixtures.widgetPage, services = services)
        }
    }

    private fun renderPageSet(with: ContainerServices = services) {
        composeRule.renderExpression {
            PageSetContainerHost(container = ContainerFixtures.pageSet, services = with)
        }
    }
}
