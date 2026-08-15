package com.anant.freescale.ui.loading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
 * (`off` / `random` / animation id). Off → spinner with status text.
 */
@Composable
fun LoadingAnimationHost(
    slot: LoadingAnimationSlot,
    animationChoice: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    captions: List<String>? = null,
) {
    val animation = remember(slot, animationChoice) {
        LoadingAnimationRegistry.resolve(slot, animationChoice)
    }

    if (animation == null) {
        MeasuringSpinnerBanner(
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
        ),
    )
}

@Composable
private fun MeasuringSpinnerBanner(
    label: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = measuringLabel(label),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}
