package com.anant.freescale.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Holds the display awake for as long as [enabled] is true.
 *
 * Used during a weigh-in: the user is standing on the scale watching the readout,
 * not touching the phone, so the screen would otherwise dim and sleep partway
 * through.
 *
 * This sets `View.keepScreenOn` rather than taking a `WakeLock` or adding
 * `FLAG_KEEP_SCREEN_ON` to the window:
 *
 *  - no `WAKE_LOCK` permission needed, which keeps the manifest clean and matters
 *    for an F-Droid build;
 *  - the platform drops the request automatically when the view detaches or the
 *    app goes to the background, so a stuck flag cannot pin the screen on;
 *  - [DisposableEffect] clears it when [enabled] goes false or this leaves
 *    composition, so there is no path that leaks the request.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
