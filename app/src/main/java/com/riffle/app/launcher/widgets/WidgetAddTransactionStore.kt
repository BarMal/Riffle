package com.riffle.app.launcher.widgets

import com.riffle.app.launcher.WidgetAddTarget
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

data class PendingWidgetAddTransaction(
    val hostedWidgetId: HostedWidgetId,
    val provider: WidgetProviderIdentity,
    val label: String,
    val preferredSpan: GridSpan,
    val target: WidgetAddTarget,
    val step: PendingWidgetAddStep,
    val createdAtEpochMillis: Long,
    val version: Int = CURRENT_WIDGET_ADD_TRANSACTION_VERSION,
)

enum class PendingWidgetAddStep {
    PERMISSION,
    CONFIGURATION,
}

interface WidgetAddTransactionStore {
    fun read(): PendingWidgetAddTransaction?

    fun write(transaction: PendingWidgetAddTransaction)

    fun clear()
}

class InMemoryWidgetAddTransactionStore : WidgetAddTransactionStore {
    private var transaction: PendingWidgetAddTransaction? = null

    override fun read(): PendingWidgetAddTransaction? = transaction

    override fun write(transaction: PendingWidgetAddTransaction) {
        this.transaction = transaction
    }

    override fun clear() {
        transaction = null
    }
}

object RetainedWidgetAddTransactionStore : WidgetAddTransactionStore by InMemoryWidgetAddTransactionStore()

private const val CURRENT_WIDGET_ADD_TRANSACTION_VERSION = 1
