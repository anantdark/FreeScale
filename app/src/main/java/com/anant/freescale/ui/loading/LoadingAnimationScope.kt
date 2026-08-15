package com.anant.freescale.ui.loading

import androidx.compose.ui.Modifier

/** Runtime inputs passed to a [LoadingAnimation] when it is displayed. */
data class LoadingAnimationScope(
    val modifier: Modifier = Modifier,
    /** Optional status line (e.g. phase headline while measuring). */
    val label: String? = null,
    /** Cycling caption lines; falls back to the animation's defaults when empty. */
    val captions: List<String> = emptyList(),
    /**
     * Playback multiplier from press-and-hold on the instrument card.
     * `1f` = natural tempo; higher values speed the animation.
     */
    val speedMultiplier: Float = 1f,
)
