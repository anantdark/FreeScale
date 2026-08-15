package com.anant.freescale.ui.loading

import androidx.compose.runtime.Composable

/** A registered wait animation that can be picked randomly for one or more slots. */
interface LoadingAnimation {
    val id: String
    /** Human-readable label for developer Settings. */
    val displayName: String
    val slots: Set<LoadingAnimationSlot>
    val defaultCaptions: List<String> get() = emptyList()
    /**
     * When true, instrument-card overlays (weight, captions) should use light ink
     * on this animation's dark surface. Tiranga-style bright washes flip this off.
     */
    val lightContent: Boolean get() = true

    @Composable
    fun Content(scope: LoadingAnimationScope)
}
