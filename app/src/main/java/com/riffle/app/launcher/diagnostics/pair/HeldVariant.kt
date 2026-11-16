package com.riffle.app.launcher.diagnostics.pair

/** What a holder does when it never saw the split after its first start. */
enum class TimeoutAction {
    /** Start the second app anyway, so the owner still sees both apps. */
    START_SECOND,

    /** Start nothing more and finish. */
    STOP,
}

/**
 * One recipe of the "Held trampoline" method (round 3): the flags of the two starts the holder makes, how long it
 * stays after the second one and what it does when no split shows up. [secondAfterMillis] is null for the recipes that
 * wait for the split before the second start; a value starts the second app that long after the first one instead.
 */
data class HeldVariant(
    val id: String,
    val title: String,
    val firstFlags: Int,
    val secondFlags: Int,
    val finishDelayMillis: Long,
    val secondAfterMillis: Long? = null,
    val onTimeout: TimeoutAction = TimeoutAction.START_SECOND,
) {
    /** The title without the "Held: " prefix, for the step toast ("Held 2 of 6: adjacent, then adjacent"). */
    val short: String get() = title.removePrefix("Held: ")
}

/** The six variants, in the order the held script runs them. */
object HeldVariants {
    private const val ADJACENT = PairFlags.ADJACENT_NEW_TASK
    private const val PLAIN = PairFlags.NEW_TASK
    private const val CLEAR = android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
    private const val MULTIPLE = android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    private const val FINISH_MS = 300L
    private const val FINISH_LONG_MS = 800L

    /** The wait before the control variant's second start, which does not wait for a split. */
    const val CONTROL_SECOND_AFTER_MS = 600L

    val all: List<HeldVariant> =
        listOf(
            HeldVariant("H1", "Held: adjacent, then plain", ADJACENT, PLAIN, FINISH_MS),
            HeldVariant("H2", "Held: adjacent, then adjacent", ADJACENT, ADJACENT, FINISH_MS),
            HeldVariant("H3", "Held: reset both", ADJACENT or CLEAR, PLAIN or CLEAR, FINISH_MS),
            HeldVariant("H4", "Held: fresh both", ADJACENT or MULTIPLE, PLAIN or MULTIPLE, FINISH_MS),
            HeldVariant("H5", "Held: adjacent, then plain, hold longer", ADJACENT, PLAIN, FINISH_LONG_MS),
            HeldVariant(
                "H6",
                "Held: plain first, then adjacent",
                PLAIN,
                ADJACENT,
                FINISH_MS,
                secondAfterMillis = CONTROL_SECOND_AFTER_MS,
            ),
        )

    fun byId(id: String?): HeldVariant? = all.firstOrNull { it.id == id }
}

/** What the holder activity carries: the two apps and the recipe. */
data class HolderRequest(
    val first: PairTarget,
    val second: PairTarget,
    val variant: HeldVariant,
)

/** Starts the holder activity, which makes the variant's two starts itself and reports back to the log. */
class HeldStrategy(
    private val variant: HeldVariant,
) : PairStrategy {
    /** Held entries are logged as [PairMethod.CUSTOM] entries that carry their [HeldVariant]. */
    override val method = PairMethod.CUSTOM

    override suspend fun launch(
        first: PairTarget,
        second: PairTarget,
        starter: PairStarter,
        delayMillis: Long,
    ): List<StartOutcome> =
        listOf(
            attempt("holder activity ${variant.id} [NEW_TASK]") {
                starter.startHolder(HolderRequest(first, second, variant))
            },
        )
}
