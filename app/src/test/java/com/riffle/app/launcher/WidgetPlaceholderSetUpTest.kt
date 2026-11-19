package com.riffle.app.launcher

import android.content.Intent
import com.riffle.app.launcher.widgets.PendingWidgetAddTransaction
import com.riffle.app.launcher.widgets.WidgetAddRecoveryResult
import com.riffle.app.launcher.widgets.WidgetAddTransactionStore
import com.riffle.app.launcher.widgets.WidgetBindPermissionResult
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import com.riffle.app.launcher.widgets.WidgetBindingResult
import com.riffle.app.launcher.widgets.WidgetConfigurationResult
import com.riffle.app.launcher.widgets.WidgetHostGateway
import com.riffle.app.launcher.widgets.decodeWidgetAddTransaction
import com.riffle.app.launcher.widgets.encodeWidgetAddTransaction
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetStackEngine
import com.riffle.core.domain.launcher.home.WidgetStackResult
import com.riffle.core.domain.launcher.home.restacked
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Setting up a placeholder (ADR 0003, Amendment 18): the picker's own bind, permission and configuration flow, aimed
 * at an item that is already on Home, with a fake widget host gateway.
 */
class WidgetPlaceholderSetUpTest {
    private val provider = WidgetProviderIdentity(AppPackageName("com.example.widgets"), WidgetProviderClassName(".A"))
    private val other = WidgetProviderIdentity(AppPackageName("com.example.widgets"), WidgetProviderClassName(".B"))
    private val span = GridSpan(2, 2)
    private val placement = GridPlacement(GridCell(1, 2), span)

    private val placeholder =
        WidgetItem(
            id = LauncherItemId("widget:p"),
            appWidgetId = HostedWidgetId(0),
            label = "Weather",
            placement = placement,
            provider = provider,
        )

    private fun layoutOf(item: WidgetItem): HomeLayout =
        HomeLayoutDefaults.standard().let { it.copy(pages = listOf(it.selectedPage.copy(items = listOf(item)))) }

    private class Harness(
        val gateway: FakeGateway,
        var layout: HomeLayout,
        store: WidgetAddTransactionStore = MemoryStore(),
        gone: Boolean = false,
    ) {
        val coordinator = WidgetBindingCoordinator(gateway, store)
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = coordinator,
                selectedGrid = { GridDimensions(4, 5) },
                windowSize = { LauncherWidgetAddWindowSize(400, 1000) },
                completeWidgetAdd = ::complete,
                deleteHostedWidgetId = gateway::deleteHostedWidgetId,
                isProviderGone = { gone },
            )

        fun complete(action: HostedWidgetAddAction): HostedWidgetAddCompletionResult {
            action as LauncherShellAction.BindHostedWidgetToPlaceholder
            return when (
                val result =
                    WidgetStackEngine().bindPlaceholder(
                        layout,
                        action.itemId,
                        action.memberIndex,
                        action.hostedWidgetId,
                        action.resizeConstraints,
                    )
            ) {
                is WidgetStackResult.Updated -> {
                    layout = result.layout
                    HostedWidgetAddCompletionResult.Placed(null)
                }
                is WidgetStackResult.Rejected -> HostedWidgetAddCompletionResult.Rejected
            }
        }

        fun item(): WidgetItem = layout.selectedPage.items.single() as WidgetItem
    }

    private fun request(
        itemId: String = "widget:p",
        memberIndex: Int = 0,
    ) = LauncherShellAction.RequestAddWidget(
        provider = provider,
        label = "Weather",
        dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 100),
        target = WidgetAddTarget.HOME_PLACEHOLDER,
        targetItemId = LauncherItemId(itemId),
        targetMemberIndex = memberIndex,
    )

    @Test
    fun aSilentBindTurnsThePlaceholderIntoTheLiveWidgetInPlace() {
        val harness = Harness(FakeGateway(), layoutOf(placeholder))

        val result = harness.handler.handle(request())

        assertEquals(LauncherWidgetAddHandlingResult.Completed(null), result)
        val item = harness.item()
        assertEquals(HostedWidgetId(1), item.appWidgetId)
        assertEquals(placeholder.id, item.id)
        assertEquals(placement, item.placement)
        assertEquals(provider, item.provider)
        assertTrue(harness.gateway.deleted.isEmpty())
    }

    @Test
    fun aPermissionPromptThatIsGrantedFinishesTheSetUp() {
        val harness = Harness(FakeGateway(bindResult = WidgetBindingResult.RequiresPermission), layoutOf(placeholder))

        val first = harness.handler.handle(request())
        assertEquals(LauncherWidgetAddHandlingResult.RequiresPermission(HostedWidgetId(1), provider), first)
        assertTrue(harness.item().needsSetUp)

        val granted = harness.coordinator.onPermissionResult(HostedWidgetId(1), granted = true)
        val action = (granted as WidgetBindPermissionResult.Bound).action
        harness.complete(action)

        assertEquals(HostedWidgetId(1), harness.item().appWidgetId)
        assertEquals(placement, harness.item().placement)
    }

    @Test
    fun aPermissionPromptThatIsDeniedLeavesThePlaceholderAndReleasesTheId() {
        val harness = Harness(FakeGateway(bindResult = WidgetBindingResult.RequiresPermission), layoutOf(placeholder))
        harness.handler.handle(request())

        val denied = harness.coordinator.onPermissionResult(HostedWidgetId(1), granted = false)

        assertEquals(WidgetBindPermissionResult.Cancelled, denied)
        assertEquals(listOf(HostedWidgetId(1)), harness.gateway.deleted)
        assertEquals(placeholder, harness.item())
    }

    @Test
    fun aConfigurationThatIsFinishedFinishesTheSetUp() {
        val harness = Harness(FakeGateway(configured = setOf(HostedWidgetId(1))), layoutOf(placeholder))

        val first = harness.handler.handle(request())
        assertEquals(LauncherWidgetAddHandlingResult.RequiresConfiguration(HostedWidgetId(1)), first)

        val done = harness.coordinator.onConfigurationResult(HostedWidgetId(1), configured = true)
        harness.complete((done as WidgetConfigurationResult.Bound).action)

        assertEquals(HostedWidgetId(1), harness.item().appWidgetId)
        assertEquals(placement, harness.item().placement)
    }

    @Test
    fun aConfigurationThatIsCancelledLeavesThePlaceholderAndReleasesTheId() {
        val harness = Harness(FakeGateway(configured = setOf(HostedWidgetId(1))), layoutOf(placeholder))
        harness.handler.handle(request())

        val cancelled = harness.coordinator.onConfigurationResult(HostedWidgetId(1), configured = false)

        assertEquals(WidgetConfigurationResult.Cancelled, cancelled)
        assertEquals(listOf(HostedWidgetId(1)), harness.gateway.deleted)
        assertEquals(placeholder, harness.item())
    }

    @Test
    fun aProviderThatIsGoneAllocatesNothingAndSaysSo() {
        val harness = Harness(FakeGateway(), layoutOf(placeholder), gone = true)

        val result = harness.handler.handle(request())

        assertEquals(LauncherWidgetAddHandlingResult.ProviderGone, result)
        assertEquals(0, harness.gateway.allocated)
        assertEquals(placeholder, harness.item())
    }

    @Test
    fun aPlaceholderInAStackKeepsItsPositionAndTheShownWidget() {
        val stack =
            placeholder.restacked(
                listOf(
                    StackedWidget(HostedWidgetId(5), "Calendar", provider = other),
                    StackedWidget(HostedWidgetId(0), "Weather", provider = provider),
                    StackedWidget(HostedWidgetId(6), "Notes", provider = other),
                ),
                shown = 1,
            )
        val harness = Harness(FakeGateway(), layoutOf(stack))

        harness.handler.handle(request(memberIndex = 1))

        val item = harness.item()
        assertEquals(listOf(5, 1, 6), item.members.map { it.appWidgetId.value })
        assertEquals(1, item.shownIndex)
        assertEquals(placement, item.placement)
        assertEquals(listOf("Calendar", "Weather", "Notes"), item.members.map { it.label })
        assertEquals(listOf(other, provider, other), item.members.map { it.provider })
    }

    @Test
    fun theSetUpSurvivesTheProcessDyingDuringTheConfigurationPrompt() {
        val store = MemoryStore()
        val gateway = FakeGateway(configured = setOf(HostedWidgetId(1)))
        Harness(gateway, layoutOf(placeholder), store).handler.handle(request())

        // A new process: the coordinator reads the persisted transaction (encoded and decoded as on disk).
        val revived = Harness(gateway, layoutOf(placeholder), MemoryStore(store.value?.let(::reencode)))
        assertEquals(
            WidgetAddRecoveryResult.ResumeConfiguration(HostedWidgetId(1)),
            revived.coordinator.recoverPendingAdd(),
        )
        val done = revived.coordinator.onConfigurationResult(HostedWidgetId(1), configured = true)
        val action =
            (done as WidgetConfigurationResult.Bound).action as LauncherShellAction.BindHostedWidgetToPlaceholder
        assertEquals(LauncherItemId("widget:p"), action.itemId)
        assertEquals(0, action.memberIndex)
        revived.complete(action)
        assertEquals(HostedWidgetId(1), revived.item().appWidgetId)
    }

    @Test
    fun aRequestWithoutAnItemIsRefusedBeforeAnythingIsAllocated() {
        val harness = Harness(FakeGateway(), layoutOf(placeholder))

        val result = harness.handler.handle(request().copy(targetItemId = null))

        assertEquals(LauncherWidgetAddHandlingResult.Cancelled, result)
        assertEquals(0, harness.gateway.allocated)
    }

    @Test
    fun theSupportOffersSetUpOnlyForAPlaceholderWhoseProviderIsKnownAndInstalled() {
        val installed =
            InstalledWidgetProvider(
                identity = provider,
                label = "Weather",
                dimensions = WidgetProviderDimensions(minWidthDp = 110, minHeightDp = 110),
                supportsHorizontalResize = true,
            )
        val member = placeholder.members.single()

        val ready = WidgetSetUpSupport(catalogReady = true, providers = listOf(installed))
        assertEquals(PlaceholderState.CAN_SET_UP, ready.stateOf(member))
        val asked = ready.requestFor(placeholder.id, 0, member)
        assertEquals(WidgetAddTarget.HOME_PLACEHOLDER, asked?.target)
        assertEquals(placeholder.id, asked?.targetItemId)
        assertEquals(installed.dimensions, asked?.dimensions)

        assertEquals(PlaceholderState.PROVIDER_GONE, WidgetSetUpSupport(catalogReady = true).stateOf(member))
        assertNull(WidgetSetUpSupport(catalogReady = true).requestFor(placeholder.id, 0, member))
        // While the catalog loads nothing is called gone.
        assertEquals(PlaceholderState.CAN_SET_UP, WidgetSetUpSupport(catalogReady = false).stateOf(member))

        val unknown = placeholder.copy(provider = null).members.single()
        assertEquals(PlaceholderState.NO_PROVIDER, ready.stateOf(unknown))
        assertNull(ready.requestFor(placeholder.id, 0, unknown))

        val bound = StackedWidget(HostedWidgetId(3), "Weather", provider = provider)
        assertEquals(PlaceholderState.NOT_A_PLACEHOLDER, ready.stateOf(bound))
    }

    private fun reencode(transaction: PendingWidgetAddTransaction): PendingWidgetAddTransaction =
        checkNotNull(decodeWidgetAddTransaction(encodeWidgetAddTransaction(transaction)))

    private class MemoryStore(
        var value: PendingWidgetAddTransaction? = null,
    ) : WidgetAddTransactionStore {
        override fun read(): PendingWidgetAddTransaction? = value

        override fun write(transaction: PendingWidgetAddTransaction) {
            value = transaction
        }

        override fun clear() {
            value = null
        }
    }

    private class FakeGateway(
        private val bindResult: WidgetBindingResult = WidgetBindingResult.Bound,
        private val configured: Set<HostedWidgetId> = emptySet(),
    ) : WidgetHostGateway {
        var allocated = 0
        val deleted = mutableListOf<HostedWidgetId>()

        override fun allocateHostedWidgetId(): HostedWidgetId = HostedWidgetId(++allocated)

        override fun bindHostedWidget(
            hostedWidgetId: HostedWidgetId,
            provider: WidgetProviderIdentity,
        ): WidgetBindingResult = bindResult

        override fun createBindHostedWidgetIntent(
            hostedWidgetId: HostedWidgetId,
            provider: WidgetProviderIdentity,
        ): Intent = error("Intent creation stays in MainActivity")

        override fun hostedWidgetRequiresConfiguration(hostedWidgetId: HostedWidgetId): Boolean =
            hostedWidgetId in configured

        override fun createConfigureHostedWidgetIntent(hostedWidgetId: HostedWidgetId): Intent =
            error("Intent creation stays in MainActivity")

        override fun deleteHostedWidgetId(hostedWidgetId: HostedWidgetId) {
            deleted += hostedWidgetId
        }
    }
}
