package com.riffle.app.launcher.diagnostics.pair

/**
 * A [PairScriptLauncher] over the experiment: each step is an ordinary [AppPairExperiment.launch], so it writes the
 * normal log entry. The providers are read at every step, because the activity whose context starts things can be
 * recreated while a run is under way.
 */
class ExperimentScriptLauncher(
    private val experiment: AppPairExperiment,
    private val starter: () -> PairStarter,
    private val facts: () -> PairFactsSource,
    private val watcher: PairLaunchWatcher,
    private val resolver: (String) -> PairTarget?,
    private val home: () -> Unit,
) : PairScriptLauncher {
    override fun resolve(packageName: String): PairTarget? = resolver(packageName)

    override suspend fun fire(
        step: PairScriptStep,
        info: PairScriptInfo,
        first: PairTarget,
        second: PairTarget,
    ): Long =
        experiment.launch(
            step.method,
            first,
            second,
            PairScript.DELAY_MS,
            starter(),
            facts(),
            PairLaunchOptions(
                step.custom,
                info.firstOpen,
                info.secondOpen,
                watcher,
                step.logName,
                info,
                step.held,
            ),
        )

    override fun returnToLauncher() = home()
}
