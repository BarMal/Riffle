package com.riffle.core.domain.launcher.home

data class LauncherTemplateSeedPlan(
    val templateId: LauncherTemplateId,
    val targetKey: HomeLayoutKey,
    val pages: List<LauncherTemplateSeedPage>,
) {
    val pageTypes: List<LauncherPageType> = pages.map { page -> page.type }
}

data class LauncherTemplateSeedPage(
    val id: LauncherPageId,
    val type: LauncherPageType,
)

class LauncherTemplateSeedPlanner {
    fun plan(
        template: LauncherTemplate,
        targetKey: HomeLayoutKey,
    ): LauncherTemplateSeedPlanResult =
        when {
            targetKey.viewMode !in template.supportedViewModes ->
                LauncherTemplateSeedPlanResult.Rejected(LauncherTemplateSeedPlanRejectionReason.INCOMPATIBLE_VIEW_MODE)

            targetKey.deviceClass !in template.supportedDeviceClasses ->
                LauncherTemplateSeedPlanResult.Rejected(
                    LauncherTemplateSeedPlanRejectionReason.INCOMPATIBLE_DEVICE_CLASS,
                )

            template.seedPageTypes.isEmpty() ->
                LauncherTemplateSeedPlanResult.Rejected(LauncherTemplateSeedPlanRejectionReason.EMPTY_SEED_PAGES)

            else ->
                LauncherTemplateSeedPlanResult.Planned(
                    LauncherTemplateSeedPlan(
                        templateId = template.id,
                        targetKey = targetKey,
                        pages = template.seedPageTypes.toSeedPages(),
                    ),
                )
        }
}

sealed interface LauncherTemplateSeedPlanResult {
    data class Planned(val plan: LauncherTemplateSeedPlan) : LauncherTemplateSeedPlanResult

    data class Rejected(val reason: LauncherTemplateSeedPlanRejectionReason) : LauncherTemplateSeedPlanResult
}

enum class LauncherTemplateSeedPlanRejectionReason {
    INCOMPATIBLE_VIEW_MODE,
    INCOMPATIBLE_DEVICE_CLASS,
    EMPTY_SEED_PAGES,
}

private fun List<LauncherPageType>.toSeedPages(): List<LauncherTemplateSeedPage> {
    val staticPageInstances = mutableMapOf<StaticLauncherPageSeedId, Int>()
    val generatedPageInstances = mutableMapOf<GeneratedLauncherPageKind, Int>()

    return map { type ->
        LauncherTemplateSeedPage(
            id =
                when (type) {
                    LauncherPageType.Home ->
                        StaticLauncherPageSeedId.HOME.nextInstanceId(staticPageInstances)

                    LauncherPageType.AllApps ->
                        StaticLauncherPageSeedId.ALL_APPS.nextInstanceId(staticPageInstances)

                    is LauncherPageType.Generated ->
                        type.kind.nextInstanceId(generatedPageInstances)
                },
            type = type,
        )
    }
}

private enum class StaticLauncherPageSeedId(val prefix: String) {
    HOME("home"),
    ALL_APPS("all-apps"),
}

private typealias StaticPageInstances = MutableMap<StaticLauncherPageSeedId, Int>

private typealias GeneratedPageInstances = MutableMap<GeneratedLauncherPageKind, Int>

private fun StaticLauncherPageSeedId.nextInstanceId(instances: StaticPageInstances): LauncherPageId {
    val instance = instances.nextInstanceFor(this)
    return LauncherPageId(prefix.withInstance(instance))
}

private fun GeneratedLauncherPageKind.nextInstanceId(instances: GeneratedPageInstances): LauncherPageId {
    val instance = instances.nextInstanceFor(this)
    return spec.defaultPageId(instance = instance)
}

private fun <T> MutableMap<T, Int>.nextInstanceFor(key: T): Int =
    (getOrDefault(key, 0) + 1)
        .also { instance -> put(key, instance) }

private fun String.withInstance(instance: Int): String {
    return if (instance == 1) this else "$this:$instance"
}
