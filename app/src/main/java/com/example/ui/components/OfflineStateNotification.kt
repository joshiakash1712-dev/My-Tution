package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppRepository
import com.example.data.NetworkConnectivityManager
import com.example.data.OfflineBlockedEvent
import kotlinx.coroutines.delay

/**
 * Global 'OfflineState' Overlay & Snackbar Notification
 * 
 * Automatically displays whenever a user attempts an action (such as adding a student,
 * recording a payment, marking attendance, or submitting test marks) while disconnected
 * from the internet.
 *
 * Explicitly informs the user why the action cannot be performed, highlights that the app
 * is running in strict read-only mode to prevent data desynchronization, displays real-time
 * connectivity telemetry, and offers 1-tap connection verification and dismissal.
 */
@Composable
fun GlobalOfflineStateNotification(
  modifier: Modifier = Modifier
) {
  val offlineBlockedEvent by AppRepository.offlineBlockedEvent.collectAsState()
  val isOnline by NetworkConnectivityManager.isOnline.collectAsState()
  val isSimulatedOffline by NetworkConnectivityManager.isSimulatedOffline.collectAsState()
  val lastSyncFormatted by NetworkConnectivityManager.lastSyncFormatted.collectAsState()
  val isSyncing by NetworkConnectivityManager.isSyncing.collectAsState()

  // Local state for interactive feedback when user taps "Check Connection"
  var checkStatusMessage by remember { mutableStateOf<String?>(null) }

  // Auto-dismiss countdown timer (7 seconds)
  var progress by remember { mutableFloatStateOf(1f) }
  val eventId = offlineBlockedEvent?.id

  LaunchedEffect(eventId) {
    if (eventId != null) {
      checkStatusMessage = null
      progress = 1f
      val totalDurationMs = 7500L
      val intervalMs = 50L
      val steps = (totalDurationMs / intervalMs).toInt()
      for (i in steps downTo 0) {
        progress = i.toFloat() / steps
        delay(intervalMs)
      }
      // Auto dismiss when countdown finishes
      AppRepository.clearOfflineBlockedEvent()
    }
  }

  // If connection is restored while dialog is active, auto-dismiss
  LaunchedEffect(isOnline) {
    if (isOnline && offlineBlockedEvent != null) {
      checkStatusMessage = "Connection restored! Dismissing..."
      delay(1200)
      AppRepository.clearOfflineBlockedEvent()
    }
  }

  AnimatedVisibility(
    visible = offlineBlockedEvent != null,
    enter = slideInVertically(
      initialOffsetY = { -it },
      animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    ) + fadeIn(animationSpec = tween(250)),
    exit = slideOutVertically(
      targetOffsetY = { -it },
      animationSpec = tween(200, easing = FastOutLinearInEasing)
    ) + fadeOut(animationSpec = tween(150)),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .testTag("offline_state_overlay")
  ) {
    val event = offlineBlockedEvent ?: return@AnimatedVisibility

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_halo")
    val pulseAlpha by infiniteTransition.animateFloat(
      initialValue = 0.2f,
      targetValue = 0.8f,
      animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "pulse_alpha"
    )

    Card(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFFEF4444).copy(alpha = 0.5f)),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = Color(0xFF180A0D)
      ),
      border = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.85f))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color(0xFF2A0D12),
                Color(0xFF180A0D)
              )
            )
          )
      ) {
        // TOP ACCENT & PROGRESS TIMER BAR
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0xFF450A0A))
        ) {
          Box(
            modifier = Modifier
              .fillMaxHeight()
              .fillMaxWidth(progress)
              .background(Color(0xFFEF4444))
          )
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // 1. Header row: Tag, pulsing icon, and close button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFF7F1D1D).copy(alpha = pulseAlpha)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.CloudOff,
                  contentDescription = "Offline Action Blocked",
                  tint = Color(0xFFFCA5A5),
                  modifier = Modifier.size(20.dp)
                )
              }

              Column {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Surface(
                    color = Color(0xFF991B1B),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = "OFFLINE STATE • READ-ONLY",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }

                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFEF4444))
                  )
                }

                Text(
                  text = "Action Blocked: ${event.actionName}",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            // Dismiss Button
            IconButton(
              onClick = { AppRepository.clearOfflineBlockedEvent() },
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF450A0A))
                .testTag("offline_dismiss_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss notification",
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(16.dp)
              )
            }
          }

          // 2. Explicit Explanation Callout
          Surface(
            color = Color(0xFF2B1015).copy(alpha = 0.8f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF7F1D1D).copy(alpha = 0.5f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.WarningAmber,
                  contentDescription = null,
                  tint = Color(0xFFF87171),
                  modifier = Modifier
                    .size(16.dp)
                    .padding(top = 1.dp)
                )
                Text(
                  text = "Cannot execute '${event.actionName}' while disconnected from the internet.",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFFFECACA),
                  lineHeight = 16.sp
                )
              }

              Text(
                text = "Tuition data integrity requires an active internet connection to create or update student records, process fee transactions, and publish announcements. Existing records remain safely readable from local storage.",
                fontSize = 11.sp,
                color = Color(0xFFE2E8F0),
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 24.dp)
              )
            }
          }

          // 3. Telemetry & Connection Status Bar
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF14080B), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(13.dp)
              )
              Text(
                text = "Last synced: $lastSyncFormatted",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
              )
            }

            Text(
              text = if (isSimulatedOffline) "Simulator: Active" else "Network: Offline",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSimulatedOffline) Color(0xFFFBBF24) else Color(0xFFF87171)
            )
          }

          // Optional status message (e.g. while verifying or reconnected)
          if (checkStatusMessage != null) {
            Text(
              text = checkStatusMessage!!,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = if (isOnline) Color(0xFF34D399) else Color(0xFFFBBF24)
            )
          }

          // 4. Quick Action Controls
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Check Connection / Reconnect button
            Button(
              onClick = {
                checkStatusMessage = "Checking connectivity & pinging servers..."
                NetworkConnectivityManager.performLiveSync { success ->
                  if (success) {
                    checkStatusMessage = "Online! Live connection restored."
                  } else {
                    checkStatusMessage = "Still disconnected. Check Wi-Fi or Mobile Data."
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626)
              ),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.weight(1f),
              enabled = !isSyncing
            ) {
              if (isSyncing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(14.dp),
                  strokeWidth = 2.dp,
                  color = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Text("Checking...", fontSize = 11.sp, color = Color.White)
              } else {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Check Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            // Quick toggle if simulated offline was enabled
            if (isSimulatedOffline) {
              OutlinedButton(
                onClick = {
                  NetworkConnectivityManager.setSimulatedOffline(false)
                  checkStatusMessage = "Simulator disabled. Reconnecting..."
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text("Turn Online", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            // Understood / Dismiss Button
            OutlinedButton(
              onClick = { AppRepository.clearOfflineBlockedEvent() },
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFECACA)),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text("Understood", fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}
