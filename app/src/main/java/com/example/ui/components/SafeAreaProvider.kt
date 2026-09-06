package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Global SafeAreaProvider / layout wrapper component.
 * Uses standard platform window insets to automatically apply safe top padding
 * to the main application container. This guarantees that all screens, app bars,
 * headers, greeting texts, and buttons start rendering below the device status bar and notch,
 * preventing any overlaps with punch-holes, waterdrops, or custom notches.
 */
@Composable
fun SafeAreaProvider(
    content: @Composable () -> Unit
) {
    // In edge-to-edge mode, provide safe insets without doubling them for inner components
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        content()
    }
}
