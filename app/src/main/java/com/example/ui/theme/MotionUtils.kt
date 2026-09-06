package com.example.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Tactical spring physics for interactive elements.
 * Provides a tactile, bouncy press response when tapped or held.
 */
fun Modifier.pressSpring(
  pressScale: Float = 0.96f
): Modifier = composed {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val scale by animateFloatAsState(
    targetValue = if (isPressed) pressScale else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessHigh
    ),
    label = "spring_press_scale"
  )

  this.graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

/**
 * Clickable component with built-in spring tactile bounce and optional ripple.
 */
fun Modifier.bounceClick(
  scaleDown: Float = 0.96f,
  onClick: () -> Unit
): Modifier = composed {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val scale by animateFloatAsState(
    targetValue = if (isPressed) scaleDown else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessHigh
    ),
    label = "bounce_click_scale"
  )

  this
    .graphicsLayer {
      scaleX = scale
      scaleY = scale
    }
    .clickable(
      interactionSource = interactionSource,
      indication = null,
      onClick = onClick
    )
}

/**
 * Animated counter that smoothly increments or updates numbers (e.g. stats, currency, student count).
 */
@Composable
fun AnimatedCounterText(
  targetValue: Int,
  modifier: Modifier = Modifier,
  style: TextStyle = TextStyle.Default,
  color: Color = Color.Unspecified,
  format: (Int) -> String = { it.toString() }
) {
  val animValue by animateIntAsState(
    targetValue = targetValue,
    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
    label = "counter_anim"
  )

  Text(
    text = format(animValue),
    style = style,
    color = color,
    modifier = modifier
  )
}

/**
 * Pulsing glow effect for live session tags, online indicators, and urgent badges.
 */
fun Modifier.pulsingGlow(
  color: Color = Color(0xFF38BDF8),
  minAlpha: Float = 0.2f,
  maxAlpha: Float = 0.8f
): Modifier = composed {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
  val alpha by infiniteTransition.animateFloat(
    initialValue = minAlpha,
    targetValue = maxAlpha,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_alpha"
  )

  this.graphicsLayer {
    this.alpha = alpha
  }
}

/**
 * Shimmer gradient brush for skeleton loading and high-grade visual accents.
 */
@Composable
fun rememberShimmerBrush(
  shimmerColors: List<Color> = listOf(
    Color(0xFF1E293B).copy(alpha = 0.6f),
    Color(0xFF334155).copy(alpha = 0.3f),
    Color(0xFF1E293B).copy(alpha = 0.6f)
  )
): Brush {
  val transition = rememberInfiniteTransition(label = "shimmer_transition")
  val translateAnim by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1000f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shimmer_translate"
  )

  return Brush.linearGradient(
    colors = shimmerColors,
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset(x = translateAnim, y = translateAnim)
  )
}
