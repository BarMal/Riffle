package com.riffle.app.launcher.rss

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import com.riffle.core.domain.launcher.rss.DefaultFeedRefreshPolicy
import com.riffle.core.domain.launcher.rss.FeedRefreshConstraints
import com.riffle.core.domain.launcher.rss.FeedRefreshDecision
import com.riffle.core.domain.launcher.rss.FeedRefreshPolicy
import com.riffle.core.domain.launcher.rss.FeedRefreshTrigger

class AndroidFeedRefreshPolicy(
    private val constraintsProvider: () -> FeedRefreshConstraints,
    private val delegate: FeedRefreshPolicy = DefaultFeedRefreshPolicy,
) : FeedRefreshPolicy {
    constructor(context: Context) : this(
        constraintsProvider = {
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            val powerManager = context.getSystemService(PowerManager::class.java)
            FeedRefreshConstraints(
                meteredNetwork = connectivityManager.isActiveNetworkMetered,
                batterySaver = powerManager.isPowerSaveMode,
            )
        },
    )

    override fun decide(
        trigger: FeedRefreshTrigger,
        constraints: FeedRefreshConstraints,
    ): FeedRefreshDecision =
        delegate.decide(
            trigger = trigger,
            constraints = constraintsProvider(),
        )
}
