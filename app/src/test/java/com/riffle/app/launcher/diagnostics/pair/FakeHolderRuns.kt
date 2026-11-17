package com.riffle.app.launcher.diagnostics.pair

/** A holder session driven over a fake host and clock through a whole split-then-collapse run, for log tests. */
internal object FakeHolderRuns {
    private const val FULL = "[0,0][724,784] 724x784 px (724x784 dp)"
    private const val HALF = "[0,0][362,784] 362x784 px (362x784 dp)"
    private const val FULL_CONFIG = "smallestScreenWidthDp=724 screenWidthDp=724"
    private const val HALF_CONFIG = "smallestScreenWidthDp=362 screenWidthDp=362"

    val first = PairTarget("com.example.first", "com.example.first.Main", "First")
    val second = PairTarget("com.example.second", "com.example.second.Main", "Second")

    private class Fake {
        var now = 10_000L
        var multi = false
        var bounds = FULL
        var config = FULL_CONFIG
        val posted = mutableListOf<Pair<Long, Runnable>>()

        fun advanceTo(time: Long) {
            while (true) {
                val next = posted.filter { it.first <= time }.minByOrNull { it.first } ?: break
                posted.remove(next)
                now = maxOf(now, next.first)
                next.second.run()
            }
            now = time
        }

        val host =
            object : HolderHost {
                override fun isInMultiWindowMode() = multi

                override fun boundsText() = bounds

                override fun configText() = config

                override fun holderFacts() =
                    listOf(
                        "activityInfo: PairHolderActivity flags=0x2 (excludeFromRecents=true) launchMode=0 " +
                            "taskAffinity='' screenOrientation=-1 (resizeableActivity: no public API reads it)",
                        "FEATURE_FREEFORM_WINDOW_MANAGEMENT=false",
                        "holds the HOME role: true",
                    )

                override fun start(
                    target: PairTarget,
                    flags: Int,
                ) = Unit

                override fun finishHolder() = Unit

                override fun post(
                    delayMillis: Long,
                    task: Runnable,
                ) {
                    posted += (now + delayMillis) to task
                }

                override fun cancel(task: Runnable) {
                    posted.removeAll { it.second === task }
                }
            }
    }

    /** What the device showed: split forms right after the first start and is gone again before the hold ends. */
    fun collapsingTrace(variant: HeldVariant): HolderTrace {
        val f = Fake()
        var report: HolderTrace? = null
        val s = PairHolderSession(HolderRequest(first, second, variant), f.host, { f.now }) { report = it }
        s.on(HolderEvent.Created)
        s.on(HolderEvent.Started)
        s.on(HolderEvent.Resumed)
        s.on(HolderEvent.TopResumedChanged(true))
        s.on(HolderEvent.FocusChanged(true))
        f.advanceTo(f.now + 320)
        f.multi = true
        f.bounds = HALF
        f.config = HALF_CONFIG
        s.on(HolderEvent.MultiWindowChanged(true))
        s.on(HolderEvent.ConfigurationChanged)
        s.on(HolderEvent.TopResumedChanged(false))
        f.advanceTo(f.now + 500)
        f.multi = false
        f.bounds = FULL
        f.config = FULL_CONFIG
        s.on(HolderEvent.MultiWindowChanged(false))
        s.on(HolderEvent.ConfigurationChanged)
        s.on(HolderEvent.Paused)
        s.on(HolderEvent.FocusChanged(false))
        s.on(HolderEvent.Stopped)
        f.advanceTo(f.now + 1_000)
        s.on(HolderEvent.Destroyed)
        return report!!
    }
}
