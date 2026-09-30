package com.riffle.app.screenshots.containers

import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.screenshots.expressions.ExpressionFixtures
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.container.StaticLensResultProvider

/**
 * Containers and a fixed-answer provider for the container screenshot tests. Lenses are keyed by their
 * first source id, so a fixture names its data by the source it "reads"; nothing subscribes.
 */
internal object ContainerFixtures {
    private const val APPS = "apps"
    private const val MESSAGES = "messages"
    private const val GROUPED = "grouped-messages"
    private const val SINGLE = "single"

    val services =
        ContainerServices(
            provider = StaticLensResultProvider { lens -> LensOutput.ready(resultFor(lens)) },
            environment = ExpressionFixtures.environment,
        )

    private fun resultFor(lens: Lens): LensResult =
        when (lens.sources.first().value) {
            APPS -> ExpressionFixtures.flatApps()
            MESSAGES -> ExpressionFixtures.flatMessages()
            GROUPED -> ExpressionFixtures.groupedMessages()
            SINGLE -> ExpressionFixtures.singleCard()
            else -> LensResult.Flat(emptyList())
        }

    private fun binding(
        source: String,
        kind: ExpressionKind,
        group: LensGroup = LensGroup.None,
    ) = LensBinding(Lens(listOf(SourceId(source)), group = group), kind)

    private fun widget(
        id: String,
        columns: Int,
        rows: Int,
        source: String,
        kind: ExpressionKind,
    ) = WidgetContainer(ContainerId(id), WidgetSpan(columns, rows), binding(source, kind))

    /** A widget holding the first messages as a list. */
    val listWidget = widget("messages", 2, 3, MESSAGES, ExpressionKind.LIST)

    /** A widget holding one rich card. */
    val cardWidget = widget("now", 2, 2, SINGLE, ExpressionKind.CARD)

    /** A full page bound to one lens, drawn as a list. */
    val boundPage =
        PageContainer(ContainerId("recents"), PageContent.Bound(binding(MESSAGES, ExpressionKind.LIST)))

    /** A page of widgets: a card and a stack side by side over an icon row and a list. */
    val widgetPage =
        PageContainer(
            ContainerId("now-page"),
            PageContent.WidgetGrid(
                columns = 4,
                rows = 4,
                placements =
                    listOf(
                        WidgetPlacement(cardWidget, column = 0, row = 0),
                        WidgetPlacement(widget("stack", 2, 2, MESSAGES, ExpressionKind.CARD_STACK), 2, 0),
                        WidgetPlacement(widget("apps", 4, 1, APPS, ExpressionKind.ICON_ROW), 0, 2),
                        WidgetPlacement(widget("list", 4, 1, MESSAGES, ExpressionKind.LIST), 0, 3),
                    ),
            ),
        )

    /** Notifications grouped by app, one card stack per group. */
    val pageSet =
        PageSetContainer(
            ContainerId("inbox"),
            binding(GROUPED, ExpressionKind.CARD_STACK, LensGroup.ByGroupKey),
        )
}
