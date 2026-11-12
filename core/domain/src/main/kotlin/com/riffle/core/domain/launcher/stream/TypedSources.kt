package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.SourceSubscription

/** What a typed (non-`Item`) source reports. Mirrors `SourceState`, plus [NotConnected] for account-backed sources. */
sealed interface TypedSourceState<out T> {
    data object Loading : TypedSourceState<Nothing>

    data class Ready<T>(val values: List<T>) : TypedSourceState<T>

    data object PermissionRequired : TypedSourceState<Nothing>

    /** No account is connected, or the one that was is no longer accepted ([reason]). Never a prompt. */
    data class NotConnected(val reason: NotConnectedReason = NotConnectedReason.NO_ACCOUNT) :
        TypedSourceState<Nothing>

    data object Unavailable : TypedSourceState<Nothing>

    data object Off : TypedSourceState<Nothing>
}

/**
 * Platform seam for a source that is not item-shaped. Implementations share one upstream however many observers
 * attach, replay the latest state to a new observer, and are called and call back off the main thread.
 */
interface TypedSource<out T> {
    fun subscribe(observer: (TypedSourceState<T>) -> Unit): SourceSubscription
}

/**
 * The seam [TodoistTaskProvider] implements. With no account, [NotConnectedTaskProvider] is used and the Tasks section
 * reads [StreamSectionResult.NotConnected]. Task text is user content: it is redacted through the
 * [StreamSourceIds.TASKS] content level like any other source.
 */
interface TaskProvider : TypedSource<TaskContent>

/** The task provider when no account is connected. */
object NotConnectedTaskProvider : TaskProvider {
    override fun subscribe(observer: (TypedSourceState<TaskContent>) -> Unit): SourceSubscription {
        observer(TypedSourceState.NotConnected())
        return SourceSubscription { }
    }
}

/** The next alarm the platform has scheduled. Transient. */
data class NextAlarm(
    val id: String,
    val triggerEpochMillis: Long,
    val label: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Alarm ids must not be blank." }
        require(triggerEpochMillis >= 0L) { "Alarm time cannot be negative." }
    }
}

/**
 * The seam for the next alarm. No platform adapter exists yet (it would read `AlarmManager.getNextAlarmClock`),
 * so [NoAlarmProvider] is the default and the Now section simply has no alarm row.
 */
interface NextAlarmProvider : TypedSource<NextAlarm>

object NoAlarmProvider : NextAlarmProvider {
    override fun subscribe(observer: (TypedSourceState<NextAlarm>) -> Unit): SourceSubscription {
        observer(TypedSourceState.Ready(emptyList()))
        return SourceSubscription { }
    }
}

/** Why a section that needs an account is not connected. */
enum class NotConnectedReason {
    /** Nothing has been connected (or it was removed). */
    NO_ACCOUNT,

    /** The service answered 401 or 403: the saved token is no longer accepted and must be replaced. */
    TOKEN_REJECTED,
}
