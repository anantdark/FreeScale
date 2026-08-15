package com.anant.freescale.ui.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anant.freescale.ui.loading.animations.measuringLabel

/**
 * Shows a wait animation for [slot] according to [animationChoice]
 * (`off` / `random` / animation id). Off → status text only on the card.
 *
 * Callers should size the host to the instrument card (`fillMaxSize()`).
 */
@Composable
fun LoadingAnimationHost(
    slot: LoadingAnimationSlot,
    animationChoice: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    captions: List<String>? = null,
    speedMultiplier: Float = 1f,
) {
    val animation = remember(slot, animationChoice) {
        LoadingAnimationRegistry.resolve(slot, animationChoice)
    }

    if (animation == null) {
        MeasuringSpinnerCard(
            label = label,
            modifier = modifier,
        )
        return
    }

    val resolvedCaptions = when {
        !captions.isNullOrEmpty() -> captions
        animation.defaultCaptions.isNotEmpty() -> animation.defaultCaptions
        else -> emptyList()
    }
    animation.Content(
        LoadingAnimationScope(
            modifier = modifier,
            label = label,
            captions = resolvedCaptions,
            speedMultiplier = speedMultiplier,
        ),
    )
}

@Composable
private fun MeasuringSpinnerCard(
    label: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = measuringLabel(label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        )
    }
}
