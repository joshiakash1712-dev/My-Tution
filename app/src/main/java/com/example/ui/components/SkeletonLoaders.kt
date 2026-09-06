package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun shimmerBrush(targetValue: Float = 1000f): Brush {
  val shimmerColors = listOf(
    Color(0xFF111C2E),
    Color(0xFF1E2D42),
    Color(0xFF111C2E)
  )

  val transition = rememberInfiniteTransition(label = "shimmer")
  val translateAnimation = transition.animateFloat(
    initialValue = 0f,
    targetValue = targetValue,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shimmerTranslation"
  )

  return Brush.linearGradient(
    colors = shimmerColors,
    start = Offset.Zero,
    end = Offset(x = translateAnimation.value, y = translateAnimation.value)
  )
}

@Composable
fun ShimmerBox(
  modifier: Modifier = Modifier,
  shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
  Box(
    modifier = modifier
      .clip(shape)
      .background(shimmerBrush())
  )
}

@Composable
fun StudentProfileSkeleton() {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp)),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Circular Avatar placeholder
          ShimmerBox(
            modifier = Modifier.size(42.dp),
            shape = CircleShape
          )
          
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Name placeholder
            ShimmerBox(
              modifier = Modifier.width(140.dp).height(16.dp),
              shape = RoundedCornerShape(4.dp)
            )
            // ID badge placeholder
            ShimmerBox(
              modifier = Modifier.width(70.dp).height(12.dp),
              shape = RoundedCornerShape(4.dp)
            )
          }
        }
        
        // Right header buttons placeholder
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          ShimmerBox(modifier = Modifier.size(32.dp), shape = RoundedCornerShape(8.dp))
          ShimmerBox(modifier = Modifier.size(32.dp), shape = RoundedCornerShape(8.dp))
        }
      }
      
      Spacer(modifier = Modifier.height(14.dp))
      
      // Fields Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ShimmerBox(modifier = Modifier.width(80.dp).height(10.dp), shape = RoundedCornerShape(2.dp))
          ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp), shape = RoundedCornerShape(4.dp))
        }
        Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ShimmerBox(modifier = Modifier.width(80.dp).height(10.dp), shape = RoundedCornerShape(2.dp))
          ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp), shape = RoundedCornerShape(4.dp))
        }
      }
      
      Spacer(modifier = Modifier.height(14.dp))
      
      // Subjects badging
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ShimmerBox(modifier = Modifier.width(100.dp).height(10.dp), shape = RoundedCornerShape(2.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          listOf(60.dp, 80.dp, 70.dp).forEach { w ->
            ShimmerBox(
              modifier = Modifier.width(w).height(20.dp),
              shape = RoundedCornerShape(8.dp)
            )
          }
        }
      }
      
      Spacer(modifier = Modifier.height(14.dp))
      
      // Parent footer box placeholder
      ShimmerBox(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(10.dp)
      )
    }
  }
}

@Composable
fun AttendanceRecordSkeleton() {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp)),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          // Name placeholder
          ShimmerBox(modifier = Modifier.width(160.dp).height(16.dp), shape = RoundedCornerShape(4.dp))
          // ID / Batch placeholder
          ShimmerBox(modifier = Modifier.width(120.dp).height(12.dp), shape = RoundedCornerShape(4.dp))
          // Date placeholder
          ShimmerBox(modifier = Modifier.width(140.dp).height(12.dp), shape = RoundedCornerShape(4.dp))
        }
        
        // Right-aligned status badge placeholder
        ShimmerBox(modifier = Modifier.width(90.dp).height(20.dp), shape = RoundedCornerShape(8.dp))
      }
      
      HorizontalDivider(color = Color(0xFF1E2D42), modifier = Modifier.padding(vertical = 10.dp))
      
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // "Current Status: " label placeholder
          ShimmerBox(modifier = Modifier.width(80.dp).height(12.dp), shape = RoundedCornerShape(4.dp))
          // Status box placeholder
          ShimmerBox(modifier = Modifier.width(60.dp).height(18.dp), shape = RoundedCornerShape(6.dp))
        }
        
        // Present/Absent/Late controller placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(50.dp, 50.dp, 50.dp).forEach { w ->
            ShimmerBox(modifier = Modifier.width(w).height(24.dp), shape = RoundedCornerShape(6.dp))
          }
        }
      }
    }
  }
}
