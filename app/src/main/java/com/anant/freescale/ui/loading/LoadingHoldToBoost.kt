package com.anant.freescale.ui.loading

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/** Playback multiplier applied while the instrument card is held. */
internal const val LoadingHoldBoostMultiplier: Float = 3.2f

/** Ignore quick taps; only boost after a short sustained press. */
private const val HoldBoostArmMs: Long = 120L

/**
 * Arms [onBoostChange] only while the pointer stays down past a short
 * hold threshold. Quick taps never boost; release / cancel always resets.
 */
fun Modifier.loadingHoldToBoost(
    enabled: Boolean = true,
    onBoostChange: (boosting: Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            // Finger lifted during the grace window → treat as a tap, no boost.
            val liftedDuringGrace = withTimeoutOrNull(HoldBoostArmMs) {
                waitForUpOrCancellation()
            } != null
            if (liftedDuringGrace) return@awaitEachGesture

            onBoostChange(true)
            try {
                waitForUpOrCancellation()
            } finally {
                onBoostChange(false)
            }
        }
    }
}
