package com.riffle.core.domain.launcher.widgets

class WidgetProviderCatalog {
    fun sortedProviders(providers: List<InstalledWidgetProvider>): List<InstalledWidgetProvider> =
        providers.sortedWith(
            compareBy<InstalledWidgetProvider> { provider -> provider.label.lowercase() }
                .thenBy { provider -> provider.identity.packageName.value }
                .thenBy { provider -> provider.identity.className.value }
                .thenBy { provider -> provider.identity.profile.id.value },
        )
}
