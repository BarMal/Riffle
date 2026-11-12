package com.riffle.app.launcher.stream

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.core.domain.launcher.stream.NextAlarm
import com.riffle.core.domain.launcher.stream.NextAlarmProvider
import com.riffle.core.domain.launcher.stream.SharedTypedSource
import com.riffle.core.domain.launcher.stream.TypedSourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The next alarm the platform has scheduled (`AlarmManager.getNextAlarmClock`, which needs no permission). Reads run
 * on [executor], bursts of changes coalesce into one read, and there is one upstream however many sections observe
 * it. The alarm is transient: never persisted, logged or put in diagnostics. A platform that cannot answer reads as
 * "no alarm", not as an error, because there is nothing for the user to fix.
 */
internal fun refreshingAlarmProvider(
    executor: Executor,
    changes: SourceChangeSource,
    read: () -> NextAlarm?,
): NextAlarmProvider {
    val shared =
        SharedTypedSource<NextAlarm> { emit ->
            val active = AtomicBoolean(true)
            val pending = AtomicBoolean(false)
            val refresh = {
                if (active.get() && pending.compareAndSet(false, true)) {
                    executor.execute {
                        pending.set(false)
                        if (active.get()) {
                            emit(TypedSourceState.Ready(listOfNotNull(runCatching(read).getOrNull())))
                        }
                    }
                }
            }
            val stop = changes.observe(refresh)
            refresh()
            SourceSubscription {
                active.set(false)
                stop()
            }
        }
    return object : NextAlarmProvider {
        override fun subscribe(observer: (TypedSourceState<NextAlarm>) -> Unit) = shared.subscribe(observer)
    }
}

/** The platform wiring of [refreshingAlarmProvider], plus opening the clock app for the alarm. */
internal class AndroidNextAlarm(
    private val context: Context,
    executor: Executor,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    val provider: NextAlarmProvider =
        refreshingAlarmProvider(
            executor = executor,
            changes =
                object : SourceChangeSource {
                    override fun observe(onChanged: () -> Unit): () -> Unit {
                        val receiver =
                            object : BroadcastReceiver() {
                                override fun onReceive(
                                    context: Context?,
                                    intent: Intent?,
                                ) = onChanged()
                            }
                        ContextCompat.registerReceiver(
                            context,
                            receiver,
                            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
                            ContextCompat.RECEIVER_NOT_EXPORTED,
                        )
                        return { runCatching { context.unregisterReceiver(receiver) } }
                    }
                },
            read = {
                alarmManager?.nextAlarmClock?.let {
                        info ->
                    NextAlarm(id = "next", triggerEpochMillis = info.triggerTime)
                }
            },
        )

    /** Shows the alarm in the clock app (its own show intent, else the clock's alarm list). */
    fun openClock(): Boolean =
        runCatching {
            val show = alarmManager?.nextAlarmClock?.showIntent
            if (show != null) {
                show.send()
            } else {
                context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }.isSuccess
}
