package com.riffle.app.launcher.widgets

import android.content.Context
import android.content.SharedPreferences
import com.riffle.app.launcher.WidgetAddTarget
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.json.JSONObject

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

    /**
     * Removes an unreadable persisted transaction and returns its hosted ID only when that ID can
     * be safely recovered for cleanup.
     */
    fun discardInvalidTransaction(): HostedWidgetId? = null

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

class PersistentWidgetAddTransactionStore(
    private val preferences: SharedPreferences,
) : WidgetAddTransactionStore {
    constructor(context: Context) : this(
        context.getSharedPreferences(WIDGET_ADD_TRANSACTION_PREFERENCES, Context.MODE_PRIVATE),
    )

    override fun read(): PendingWidgetAddTransaction? =
        preferences.getString(WIDGET_ADD_TRANSACTION_KEY, null)
            ?.let(::decodeWidgetAddTransaction)

    override fun discardInvalidTransaction(): HostedWidgetId? =
        preferences.getString(WIDGET_ADD_TRANSACTION_KEY, null)
            ?.takeIf { decodeWidgetAddTransaction(it) == null }
            ?.let { value ->
                clear()
                decodeInvalidWidgetAddTransactionHostedId(value)
            }

    override fun write(transaction: PendingWidgetAddTransaction) {
        preferences.edit().putString(WIDGET_ADD_TRANSACTION_KEY, encodeWidgetAddTransaction(transaction)).apply()
    }

    override fun clear() {
        preferences.edit().remove(WIDGET_ADD_TRANSACTION_KEY).apply()
    }
}

internal fun encodeWidgetAddTransaction(transaction: PendingWidgetAddTransaction): String =
    JSONObject()
        .put("version", transaction.version)
        .put("hostedWidgetId", transaction.hostedWidgetId.value)
        .put("packageName", transaction.provider.packageName.value)
        .put("className", transaction.provider.className.value)
        .put("profileId", transaction.provider.profile.id.value)
        .put("profileType", transaction.provider.profile.type.name)
        .put("label", transaction.label)
        .put("columns", transaction.preferredSpan.columns)
        .put("rows", transaction.preferredSpan.rows)
        .put("target", transaction.target.name)
        .put("step", transaction.step.name)
        .put("createdAtEpochMillis", transaction.createdAtEpochMillis)
        .toString()

internal fun decodeWidgetAddTransaction(value: String): PendingWidgetAddTransaction? =
    runCatching {
        JSONObject(value).let { json ->
            PendingWidgetAddTransaction(
                hostedWidgetId = HostedWidgetId(json.getInt("hostedWidgetId").also { require(it > 0) }),
                provider =
                    WidgetProviderIdentity(
                        packageName = AppPackageName(json.getString("packageName")),
                        className = WidgetProviderClassName(json.getString("className")),
                        profile =
                            AppProfile(
                                id = AppProfileId(json.getString("profileId")),
                                type = AppProfileType.valueOf(json.getString("profileType")),
                            ),
                    ),
                label = json.getString("label"),
                preferredSpan =
                    GridSpan(
                        columns = json.getInt("columns").also { require(it > 0) },
                        rows = json.getInt("rows").also { require(it > 0) },
                    ),
                target = WidgetAddTarget.valueOf(json.getString("target")),
                step = PendingWidgetAddStep.valueOf(json.getString("step")),
                createdAtEpochMillis = json.getLong("createdAtEpochMillis").also { require(it >= 0) },
                version = json.getInt("version").also { require(it == CURRENT_WIDGET_ADD_TRANSACTION_VERSION) },
            )
        }
    }.getOrNull()

internal fun decodeInvalidWidgetAddTransactionHostedId(value: String): HostedWidgetId? =
    runCatching {
        HostedWidgetId(JSONObject(value).getInt("hostedWidgetId").also { require(it > 0) })
    }.getOrNull()

private const val CURRENT_WIDGET_ADD_TRANSACTION_VERSION = 1
private const val WIDGET_ADD_TRANSACTION_PREFERENCES = "widget_add_transaction"
private const val WIDGET_ADD_TRANSACTION_KEY = "pending"
