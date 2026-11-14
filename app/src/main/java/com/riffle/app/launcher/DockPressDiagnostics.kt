package com.riffle.app.launcher

import androidx.compose.ui.semantics.SemanticsPropertyKey

/**
 * What the Dock decided about the latest press, for tests only: nothing in the launcher reads it. A device test that
 * fails on a press puts [describe] in its message, so the cause (was the press claimed, what started choosing, which
 * item was nearest the landing and which was selected at the lift, whether the finger was cancelling, whether the row
 * moved and what the outcome was) is in the failure itself.
 */
internal data class DockPressDiagnostics(
    /** How many presses the Dock has claimed since it was composed; 0 means the touch was never claimed as a press. */
    val pressesClaimed: Int = 0,
    val fromContextual: Boolean = false,
    val startedOnKey: String? = null,
    val landingNearKey: String? = null,
    val landingEntryX: Float? = null,
    val choosingTrigger: String? = null,
    val choosingAtTravelDp: Float = 0f,
    val choosingAtAlongDp: Float = 0f,
    val lifted: Boolean = false,
    val selectedKeyAtLift: String? = null,
    val cancellingAtLift: Boolean = false,
    val committedAtLift: Boolean = false,
    val travelDpAtLift: Float = 0f,
    val entryXAtLift: Float? = null,
    val outcome: String? = null,
) {
    fun describe(): String =
        "Dock press: claimed=$pressesClaimed fromContextual=$fromContextual startedOn=$startedOnKey " +
            "landingNearest=$landingNearKey landingEntryX=$landingEntryX choosingTrigger=$choosingTrigger " +
            "(travel=$choosingAtTravelDp dp, along=$choosingAtAlongDp dp) lifted=$lifted " +
            "selectedAtLift=$selectedKeyAtLift " +
            "cancelling=$cancellingAtLift committed=$committedAtLift travelAtLift=$travelDpAtLift dp " +
            "entryXAtLift=$entryXAtLift outcome=$outcome"
}

internal val DockPressDiagnosticsKey = SemanticsPropertyKey<DockPressDiagnostics>("DockPressDiagnostics")
