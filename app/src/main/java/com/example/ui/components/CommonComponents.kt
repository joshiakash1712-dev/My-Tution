package com.example.ui.components
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserRole
import com.example.data.AppRepository
import com.example.data.DarkThemeMode
import com.example.data.FirestoreService
import com.example.data.FirestoreAnnouncement
import com.example.data.NotificationItem
import com.example.ui.theme.rem
import com.example.ui.theme.em
import com.example.ui.theme.NumberTypography
import com.example.ui.theme.HeroStatText
import com.example.ui.theme.CardStatText
import com.example.ui.theme.InlineStatText
import com.example.ui.theme.MicroLabelText
import com.example.ui.theme.pressSpring
import com.example.ui.theme.pulsingGlow
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*

@Composable
fun GeoHeader(
  currentRole: UserRole,
  onRoleClick: () -> Unit = {},
  onProfileClick: () -> Unit = {},
  searchQuery: String = "",
  onSearchChange: (String) -> Unit = {},
  searchPlaceholder: String = "Search..."
) {
  val notifications by AppRepository.notifications.collectAsState()
  val instituteConfig by AppRepository.instituteConfig.collectAsState()
  val unreadCount = notifications.count { !it.isRead }
  var showNotificationsModal by remember { mutableStateOf(false) }

  if (showNotificationsModal) {
    NotificationsModalDialog(onDismiss = { showNotificationsModal = false })
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
  ) {
    // Top Row: Institute Logo + Name & Subtitle | Notification Bell + Avatar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        // Institute Shield / Emblem (Multi-tenant configurable)
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Outlined.School,
            contentDescription = "${instituteConfig.instituteName} Logo",
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(19.dp)
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = instituteConfig.instituteName.ifBlank { "Fadat Science Institute" },
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = (-0.2).sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = instituteConfig.tagline.ifBlank { "Empowering minds. Building futures." },
            fontSize = 10.5.sp,
            letterSpacing = (-0.1).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Actions: Notification Bell + Profile Avatar
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Notification bell with badge
        Box(contentAlignment = Alignment.TopEnd) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .pressSpring(0.92f)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
              .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
              .clickable { showNotificationsModal = true },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.Notifications,
              contentDescription = "Notifications",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(19.dp)
            )
          }
          if (unreadCount > 0) {
            Box(
              modifier = Modifier
                .offset(x = 2.dp, y = (-2).dp)
                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
                .padding(horizontal = 3.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "$unreadCount",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // Profile Avatar & Role Pill with clear labeled status
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
          modifier = Modifier
            .pressSpring(0.95f)
            .clickable {
              onProfileClick()
              onRoleClick()
            }
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Box(
              modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                  when (currentRole) {
                    UserRole.ADMIN -> Color(0xFF2563EB)
                    UserRole.TEACHER -> Color(0xFF0284C7)
                    UserRole.STUDENT -> Color(0xFF7C3AED)
                    UserRole.PARENT -> Color(0xFF059669)
                  }
                ),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = currentRole.displayName.take(1).uppercase(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Column(modifier = Modifier.padding(end = 2.dp)) {
              Text(
                text = currentRole.displayName,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                )
                Text(
                  text = "Active",
                  fontSize = 8.5.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    }
  }
}

object AppRoleSwitcher {
  var onSwitch: ((UserRole) -> Unit)? = null
  fun triggerSwitch(role: UserRole) {
    onSwitch?.invoke(role)
  }
}

@Composable
fun HeroFeatureCard(
  title: String,
  subtitle: String,
  tagText: String = "LIVE NOW",
  icon: ImageVector = Icons.Default.Analytics,
  onClick: () -> Unit = {}
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(28.dp))
      .background(MaterialTheme.colorScheme.primaryContainer)
      .clickable { onClick() }
      .padding(22.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(10.dp)
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp)
          )
        }

        Surface(
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
          shape = CircleShape
        ) {
          Text(
            text = tagText,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }

      Column {
        Text(
          text = title,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          fontSize = 20.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
          fontSize = 13.sp,
          fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun QuickActionGrid(
  card1Title: String,
  card1Icon: ImageVector,
  card1Click: () -> Unit,
  card2Title: String,
  card2Icon: ImageVector,
  card2Click: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Left Card: Secondary Container background
    Box(
      modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .clickable { card1Click() }
        .padding(16.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
          imageVector = card1Icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(26.dp)
        )
        Text(
          text = card1Title,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    // Right Card: Tertiary Container background
    Box(
      modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.tertiaryContainer)
        .clickable { card2Click() }
        .padding(16.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
          imageVector = card2Icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onTertiaryContainer,
          modifier = Modifier.size(26.dp)
        )
        Text(
          text = card2Title,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun GeoSectionCard(
  title: String,
  actionText: String = "View all",
  onActionClick: (() -> Unit)? = null,
  content: @Composable () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(28.dp))
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
      .padding(18.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      if (onActionClick != null) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = actionText.uppercase(),
          color = MaterialTheme.colorScheme.primary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.clickable { onActionClick() }
        )
      }
    }
    content()
  }
}

@Composable
fun GeoListItem(
  title: String,
  subtitle: String,
  icon: ImageVector,
  iconBg: Color = Color.Unspecified,
  iconTint: Color = Color.Unspecified,
  trailing: @Composable () -> Unit = {
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  },
  onClick: () -> Unit
) {
  val resolvedIconBg = if (iconBg == Color.Unspecified) MaterialTheme.colorScheme.secondaryContainer else iconBg
  val resolvedIconTint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.onSecondaryContainer else iconTint

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .padding(vertical = 8.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(42.dp)
        .clip(CircleShape)
        .background(resolvedIconBg),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = resolvedIconTint,
        modifier = Modifier.size(22.dp)
      )
    }
    Spacer(modifier = Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    trailing()
  }
}

@Composable
fun GeoBottomNav(
  tabs: List<Pair<String, ImageVector>>,
  selectedIndex: Int,
  onTabSelected: (Int) -> Unit
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
  val indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)

  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = Modifier
      .fillMaxWidth()
      .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
  ) {
    tabs.forEachIndexed { idx, tab ->
      val isSelected = selectedIndex == idx
      val targetIconColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else unselectedColor,
        animationSpec = tween(durationMillis = 100),
        label = "nav_icon_color"
      )
      val targetTextColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else unselectedColor,
        animationSpec = tween(durationMillis = 100),
        label = "nav_text_color"
      )

      NavigationBarItem(
        selected = isSelected,
        onClick = { onTabSelected(idx) },
        icon = {
          Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
          ) {
            if (isSelected) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(indicatorColor)
              )
            }
            Icon(
              imageVector = tab.second,
              contentDescription = tab.first,
              modifier = Modifier.size(22.dp),
              tint = targetIconColor
            )
          }
        },
        label = {
          Text(
            text = tab.first,
            fontSize = 9.5.sp,
            letterSpacing = (-0.3).sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = targetTextColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        },
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
          indicatorColor = Color.Transparent,
          selectedIconColor = primaryColor,
          selectedTextColor = primaryColor,
          unselectedIconColor = unselectedColor,
          unselectedTextColor = unselectedColor
        )
      )
    }
  }
}

@Composable
fun MetricBox(title: String, bigVal: String, subVal: String, bg: Color, tx: Color, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .pressSpring(0.96f)
      .clip(RoundedCornerShape(24.dp))
      .background(Color(0xFF0D1526))
      .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(24.dp))
      .padding(16.dp)
  ) {
    Column {
      Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF94A3B8), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(6.dp))
      HeroStatText(text = bigVal, color = if (tx == Color(0xFF001D36) || tx == Color(0xFF0F172A) || tx == Color(0xFF072711) || tx == Color(0xFF2B1236)) Color(0xFF38BDF8) else tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(2.dp))
      MicroLabelText(text = subVal, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsModalDialog(onDismiss: () -> Unit) {
  val notifications by AppRepository.notifications.collectAsState()
  val firestoreAnnouncements by FirestoreService.remoteAnnouncements.collectAsState()
  val announcementsPagination by FirestoreService.announcementsPaginationState.collectAsState()
  val isLowBandwidthMode by FirestoreService.isLowBandwidthMode.collectAsState()
  val cacheHitsCount by FirestoreService.cacheHitsCount.collectAsState()
  val networkCallsSaved by FirestoreService.networkCallsSaved.collectAsState()
  val dataSavedKb by FirestoreService.dataSavedKb.collectAsState()

  var selectedFilter by remember { mutableStateOf("All") }
  var isSyncing by remember { mutableStateOf(false) }
  var showClearAllConfirmDialog by remember { mutableStateOf(false) }

  val activeAnnouncements = if (announcementsPagination.items.isNotEmpty()) announcementsPagination.items else firestoreAnnouncements
  val unreadCount = notifications.count { !it.isRead }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val filterOptions = listOf(
    "All" to notifications.size + activeAnnouncements.size,
    "Unread" to unreadCount,
    "📢 Announcements" to (notifications.count { it.type.equals("Announcement", ignoreCase = true) } + activeAnnouncements.size),
    "💳 Fees" to notifications.count { it.type.equals("Fee", ignoreCase = true) },
    "📝 Tests" to notifications.count { it.type.equals("Test", ignoreCase = true) },
    "📅 Attendance" to notifications.count { it.type.equals("Absence", ignoreCase = true) || it.type.equals("Attendance", ignoreCase = true) },
    "📚 Homework" to notifications.count { it.type.equals("Homework", ignoreCase = true) || it.type.equals("Task", ignoreCase = true) }
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 8.dp,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    dragHandle = { BottomSheetDefaults.DragHandle() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.88f)
        .padding(horizontal = 16.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // --- HEADER & QUICK ACTIONS ---
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "Notification Center",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              if (unreadCount > 0) {
                Surface(
                  color = MaterialTheme.colorScheme.error,
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text(
                    text = "$unreadCount New",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
            Text(
              text = "Institute Alerts, Updates & Academic Notices",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Action Buttons: Sync, Mark All Read, Delete All, Close
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = {
              isSyncing = true
              FirestoreService.loadNextAnnouncementsPage(forceRefresh = true) {
                isSyncing = false
              }
            }
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Sync Cloud Feed",
              tint = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }

          if (notifications.isNotEmpty()) {
            IconButton(
              onClick = { AppRepository.markAllNotificationsAsRead() }
            ) {
              Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Mark All Read",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
            }

            IconButton(
              onClick = { showClearAllConfirmDialog = true }
            ) {
              Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = "Delete All Notifications",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
              )
            }
          }

          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      // --- DATA SAVER & CACHE STATUS BAR (LOW-BANDWIDTH OPTIMIZATION) ---
      Surface(
        color = if (isLowBandwidthMode) Color(0xFF064E3B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isLowBandwidthMode) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable {
          FirestoreService.toggleLowBandwidthMode()
        }
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = if (isLowBandwidthMode) Icons.Default.Bolt else Icons.Outlined.SignalCellularAlt,
              contentDescription = null,
              tint = if (isLowBandwidthMode) Color(0xFF34D399) else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(17.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isLowBandwidthMode) "⚡ Data Saver Active (Low-Bandwidth Mode)" else "Data Saver: Off (Standard Bandwidth)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = if (isLowBandwidthMode) Color.White else MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = if (networkCallsSaved > 0)
                  "Cached responses: $cacheHitsCount • Saved ~$dataSavedKb KB ($networkCallsSaved network queries prevented)"
                else
                  "Caches responses locally & reduces payload size for slow 2G/3G networks",
                fontSize = 9.5.sp,
                color = if (isLowBandwidthMode) Color(0xFFA7F3D0) else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 12.sp
              )
            }
          }
          Switch(
            checked = isLowBandwidthMode,
            onCheckedChange = { FirestoreService.setLowBandwidthMode(it) },
            modifier = Modifier.scale(0.7f)
          )
        }
      }

      // --- QUICK ACTION CHIPS (MARK ALL READ & CLEAR ALL SHORTCUT BUTTONS) ---
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          onClick = { AppRepository.markAllNotificationsAsRead() },
          enabled = unreadCount > 0,
          shape = RoundedCornerShape(10.dp),
          color = if (unreadCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          border = BorderStroke(1.dp, if (unreadCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
          modifier = Modifier.weight(1f)
        ) {
          Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.DoneAll,
              contentDescription = null,
              tint = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Mark All Read",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              color = if (unreadCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
          }
        }

        Surface(
          onClick = { showClearAllConfirmDialog = true },
          enabled = notifications.isNotEmpty(),
          shape = RoundedCornerShape(10.dp),
          color = if (notifications.isNotEmpty()) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          border = BorderStroke(1.dp, if (notifications.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
          modifier = Modifier.weight(1f)
        ) {
          Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteSweep,
              contentDescription = null,
              tint = if (notifications.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Delete All",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              color = if (notifications.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
          }
        }
      }

      // --- FILTER PILLS ---
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        filterOptions.forEach { (filterName, count) ->
          val isSelected = selectedFilter == filterName
          Surface(
            onClick = { selectedFilter = filterName },
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(
              1.dp,
              if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = filterName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
              )
              if (count > 0) {
                Surface(
                  shape = CircleShape,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer
                ) {
                  Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                  )
                }
              }
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // --- NOTIFICATIONS LIST ---
      val filteredList = remember(notifications, firestoreAnnouncements, selectedFilter) {
        when (selectedFilter) {
          "All" -> notifications
          "Unread" -> notifications.filter { !it.isRead }
          "📢 Announcements" -> notifications.filter { it.type.equals("Announcement", ignoreCase = true) }
          "💳 Fees" -> notifications.filter { it.type.equals("Fee", ignoreCase = true) }
          "📝 Tests" -> notifications.filter { it.type.equals("Test", ignoreCase = true) }
          "📅 Attendance" -> notifications.filter { it.type.equals("Absence", ignoreCase = true) || it.type.equals("Attendance", ignoreCase = true) }
          "📚 Homework" -> notifications.filter { it.type.equals("Homework", ignoreCase = true) || it.type.equals("Task", ignoreCase = true) }
          else -> notifications
        }
      }

      val showRemoteAnnouncements = selectedFilter == "All" || selectedFilter == "📢 Announcements"

      if (filteredList.isEmpty() && (!showRemoteAnnouncements || firestoreAnnouncements.isEmpty())) {
        // Premium Empty State
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
              )
            }
            Text(
              text = if (selectedFilter == "Unread") "All Caught Up!" else "No Notifications Found",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = if (selectedFilter == "Unread")
                "You have reviewed all current notices and alerts."
              else
                "There are no items matching this category. Institutional alerts, fee invoices, test schedules, and homework notices will appear here.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              lineHeight = 18.sp
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 16.dp)
        ) {
          // 1. Cloud Firestore Broadcasts
          if (showRemoteAnnouncements && activeAnnouncements.isNotEmpty()) {
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = "LIVE CLOUD BROADCASTS (${activeAnnouncements.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF0284C7)
                  )
                }

                if (cacheHitsCount > 0 || isLowBandwidthMode) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE0F2FE)
                  ) {
                    Text(
                      text = "⚡ Cached (Zero Latency)",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis,
                      color = Color(0xFF0369A1),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }
            }

            items(activeAnnouncements) { annc ->
              val audienceBg = when {
                annc.targetAudience.contains("Parent", ignoreCase = true) -> Color(0xFFFEF3C7)
                annc.targetAudience.contains("Teacher", ignoreCase = true) -> Color(0xFFDCFCE7)
                else -> Color(0xFFDBEAFE)
              }
              val audienceTextColor = when {
                annc.targetAudience.contains("Parent", ignoreCase = true) -> Color(0xFF92400E)
                annc.targetAudience.contains("Teacher", ignoreCase = true) -> Color(0xFF166534)
                else -> Color(0xFF1E40AF)
              }

              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                  // Left Accent Bar (Blue for Cloud Broadcasts)
                  Box(
                    modifier = Modifier
                      .width(5.dp)
                      .fillMaxHeight()
                      .background(Color(0xFF0284C7))
                  )
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                      ) {
                        Box(
                          modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                          contentAlignment = Alignment.Center
                        ) {
                          Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(16.dp)
                          )
                        }
                        Text(
                          text = annc.title,
                          fontWeight = FontWeight.Bold,
                          fontSize = 13.sp,
                          color = MaterialTheme.colorScheme.onSurface,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f, fill = false)
                        )
                      }
                      Spacer(modifier = Modifier.width(6.dp))
                      Surface(
                        color = audienceBg,
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          text = annc.targetAudience,
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = audienceTextColor,
                          maxLines = 1,
                          softWrap = false,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }

                    Text(
                      text = annc.message,
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      lineHeight = 17.sp
                    )

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = "Sender: ${annc.sender}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = annc.dateStr.ifBlank { "Live Broadcast" },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                }
              }
            }

            // Pagination 'Load More' trigger for announcements
            if (announcementsPagination.hasMore) {
              item {
                Surface(
                  onClick = {
                    FirestoreService.loadNextAnnouncementsPage()
                  },
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    if (announcementsPagination.isLoading) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                      )
                      Spacer(Modifier.width(8.dp))
                      Text(
                        text = "Loading older broadcasts...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(
                        text = "Load Older Broadcasts (Page ${announcementsPagination.currentPage + 1})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                      )
                    }
                  }
                }
              }
            }

            if (filteredList.isNotEmpty()) {
              item {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = "LOCAL NOTIFICATIONS & ALERTS (${filteredList.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }

          // 2. Local Notifications List
          items(filteredList, key = { it.id }) { item ->
            val isUnread = !item.isRead

            val categoryColor = when (item.type.lowercase()) {
              "announcement" -> Color(0xFF0284C7)
              "fee" -> Color(0xFFD97706)
              "test", "exam" -> Color(0xFF059669)
              "absence", "attendance" -> Color(0xFFDC2626)
              "homework", "task" -> Color(0xFF7C3AED)
              else -> Color(0xFF0284C7)
            }

            val categoryIcon = when (item.type.lowercase()) {
              "announcement" -> Icons.Default.Campaign
              "fee" -> Icons.Default.Payments
              "test", "exam" -> Icons.Default.AssignmentTurnedIn
              "absence", "attendance" -> Icons.Default.EventBusy
              "homework", "task" -> Icons.Default.MenuBook
              else -> Icons.Default.Notifications
            }

            val categoryLabel = when (item.type.lowercase()) {
              "announcement" -> "NOTICE"
              "fee" -> "FEE ALERT"
              "test", "exam" -> "EXAM"
              "absence", "attendance" -> "ATTENDANCE"
              "homework", "task" -> "TASK"
              else -> item.type.uppercase()
            }

            Surface(
              onClick = { AppRepository.markNotificationAsRead(item.id) },
              color = if (isUnread) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
              },
              border = BorderStroke(
                1.dp,
                if (isUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
              ),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(modifier = Modifier.fillMaxWidth()) {
                // Left Accent Stripe for Unread / Category
                Box(
                  modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (isUnread) categoryColor else Color.Transparent)
                )

                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  // Top Row: Category Badge + Title + Unread Indicator + Single Item Delete
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.weight(1f)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(30.dp)
                          .clip(CircleShape)
                          .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          imageVector = categoryIcon,
                          contentDescription = null,
                          tint = categoryColor,
                          modifier = Modifier.size(16.dp)
                        )
                      }
                      Surface(
                        color = categoryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          text = categoryLabel,
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = categoryColor,
                          maxLines = 1,
                          softWrap = false,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                      if (isUnread) {
                        Surface(
                          color = MaterialTheme.colorScheme.primary,
                          shape = RoundedCornerShape(4.dp)
                        ) {
                          Text(
                            text = "NEW",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                          )
                        }
                      }
                    }

                    // Single Item Delete Button
                    IconButton(
                      onClick = { AppRepository.deleteNotification(item.id) },
                      modifier = Modifier.size(28.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete notification",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  }

                  // Notification Title
                  Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )

                  // Notification Message
                  Text(
                    text = item.message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                  )

                  // Bottom Info Row: Time Stamp + Target Role / Tap Hint
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(12.dp)
                      )
                      Text(
                        text = item.time,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                      )
                    }

                    if (isUnread) {
                      Text(
                        text = "Tap to mark read",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                      )
                    } else {
                      Text(
                        text = "Read",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // --- DELETE ALL CONFIRMATION DIALOG ---
  if (showClearAllConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showClearAllConfirmDialog = false },
      icon = {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(26.dp)
          )
        }
      },
      title = {
        Text(
          text = "Clear All Notifications?",
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
      },
      text = {
        Text(
          text = "This will permanently remove all ${notifications.size} local notifications from your device activity log.",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            AppRepository.deleteAllNotifications()
            showClearAllConfirmDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Delete All", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearAllConfirmDialog = false }) {
          Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(20.dp)
    )
  }
}

@Composable
fun GlobalSearchDialog(onDismiss: () -> Unit) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("All") } // "All", "Students", "Staff", "Batches", "Fees"

  val students by AppRepository.students.collectAsState()
  val teachers by AppRepository.teachers.collectAsState()
  val batches by AppRepository.batches.collectAsState()
  val fees by AppRepository.fees.collectAsState()

  var activeStudentForModal by remember { mutableStateOf<Student?>(null) }

  if (activeStudentForModal != null) {
    DigitalIdCardDialog(student = activeStudentForModal!!) {
      activeStudentForModal = null
    }
  }

  val filteredStudents = remember(students, searchQuery, selectedCategory) {
    if (selectedCategory != "All" && selectedCategory != "Students") emptyList()
    else if (searchQuery.isBlank()) students.take(5)
    else students.filter {
      it.name.contains(searchQuery, true) ||
      it.id.contains(searchQuery, true) ||
      it.batch.contains(searchQuery, true) ||
      it.parentContact.contains(searchQuery, true) ||
      it.mobile.contains(searchQuery, true)
    }
  }

  val filteredTeachers = remember(teachers, searchQuery, selectedCategory) {
    if (selectedCategory != "All" && selectedCategory != "Staff") emptyList()
    else if (searchQuery.isBlank()) teachers.take(3)
    else teachers.filter {
      it.name.contains(searchQuery, true) ||
      it.id.contains(searchQuery, true) ||
      it.subject.contains(searchQuery, true) ||
      it.contact.contains(searchQuery, true)
    }
  }

  val filteredBatches = remember(batches, searchQuery, selectedCategory) {
    if (selectedCategory != "All" && selectedCategory != "Batches") emptyList()
    else if (searchQuery.isBlank()) batches.take(3)
    else batches.filter {
      it.name.contains(searchQuery, true) ||
      it.id.contains(searchQuery, true) ||
      it.schedule.contains(searchQuery, true) ||
      it.teacherName.contains(searchQuery, true)
    }
  }

  val filteredFees = remember(fees, searchQuery, selectedCategory) {
    if (selectedCategory != "All" && selectedCategory != "Fees") emptyList()
    else if (searchQuery.isBlank()) fees.filter { it.pendingAmount > 0 }.take(4)
    else fees.filter {
      it.studentName.contains(searchQuery, true) ||
      it.paymentStatus.contains(searchQuery, true) ||
      it.id.contains(searchQuery, true)
    }
  }

  val totalResultsCount = filteredStudents.size + filteredTeachers.size + filteredBatches.size + filteredFees.size

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      shape = RoundedCornerShape(24.dp),
      color = Color(0xFF0F172A),
      border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(22.dp)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Global Institute Search",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "Search across students, staff, batches & fee dues",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color(0xFF1E293B))
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input Bar
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search by name, roll no, subject, batch...", color = Color(0xFF64748B), fontSize = 13.sp) },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF1E293B),
            unfocusedContainerColor = Color(0xFF1E293B),
            focusedBorderColor = Color(0xFF3B82F6),
            unfocusedBorderColor = Color(0xFF334155),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("All", "Students", "Staff", "Batches", "Fees").forEach { category ->
            val isSelected = selectedCategory == category
            Surface(
              onClick = { selectedCategory = category },
              shape = RoundedCornerShape(20.dp),
              color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
              border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFF334155))
            ) {
              Text(
                text = category,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Results Container
        if (totalResultsCount == 0) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
              Text("No matching records found", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
              Text("Try searching for student names, roll numbers, subjects, or batch names.", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 32.dp))
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // Students Section
            if (filteredStudents.isNotEmpty()) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("STUDENTS (${filteredStudents.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
              }
              items(filteredStudents) { student ->
                Surface(
                  onClick = { activeStudentForModal = student },
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF1E293B),
                  border = BorderStroke(1.dp, Color(0xFF334155)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      modifier = Modifier.weight(1f)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(38.dp)
                          .clip(CircleShape)
                          .background(Color(0xFF2563EB).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          student.name.take(1).uppercase(),
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF93C5FD),
                          fontSize = 14.sp,
                          maxLines = 1,
                          softWrap = false
                        )
                      }
                      Column(modifier = Modifier.weight(1f)) {
                        Text(student.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        Text("${student.id} • ${student.batch}", color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                      }
                    }
                    Spacer(Modifier.width(8.dp))

                    Surface(
                      color = when (student.status) {
                        "Active" -> Color(0xFF166534)
                        "Inactive" -> Color(0xFF991B1B)
                        else -> Color(0xFF854D0E)
                      },
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = student.status,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
              }
            }

            // Staff Section
            if (filteredTeachers.isNotEmpty()) {
              item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("FACULTY & STAFF (${filteredTeachers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              }
              items(filteredTeachers) { teacher ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF1E293B),
                  border = BorderStroke(1.dp, Color(0xFF334155)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      modifier = Modifier.weight(1f)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(38.dp)
                          .clip(CircleShape)
                          .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF7DD3FC), modifier = Modifier.size(18.dp))
                      }
                      Column(modifier = Modifier.weight(1f)) {
                        Text(teacher.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        Text("${teacher.subject} • ${teacher.contact}", color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                      }
                    }
                    Spacer(Modifier.width(8.dp))

                    Surface(
                      color = Color(0xFF075985),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = "Faculty",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
              }
            }

            // Batches Section
            if (filteredBatches.isNotEmpty()) {
              item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("ACADEMIC BATCHES (${filteredBatches.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              }
              items(filteredBatches) { batch ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF1E293B),
                  border = BorderStroke(1.dp, Color(0xFF334155)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      modifier = Modifier.weight(1f)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(38.dp)
                          .clip(RoundedCornerShape(10.dp))
                          .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(Icons.Default.Class, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                      }
                      Column(modifier = Modifier.weight(1f)) {
                        Text(batch.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        Text("${batch.schedule} • ${batch.teacherName}", color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                      }
                    }
                    Spacer(Modifier.width(8.dp))

                    Surface(
                      color = Color(0xFF78350F),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = "${AppRepository.getLiveBatchStudentCount(batch.name)} Students",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
              }
            }

            // Fees Dues Section
            if (filteredFees.isNotEmpty()) {
              item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("FEE LEDGERS & DUES (${filteredFees.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              }
              items(filteredFees) { fee ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF1E293B),
                  border = BorderStroke(1.dp, Color(0xFF334155)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      modifier = Modifier.weight(1f)
                    ) {
                      Box(
                        modifier = Modifier
                          .size(38.dp)
                          .clip(RoundedCornerShape(10.dp))
                          .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                      }
                      Column(modifier = Modifier.weight(1f)) {
                        Text(fee.studentName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        Text("Due: ₹${String.format(Locale.US, "%,d", fee.pendingAmount)} • Total: ₹${String.format(Locale.US, "%,d", fee.feeAmount)}", color = Color(0xFFCBD5E1), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                      }
                    }
                    Spacer(Modifier.width(8.dp))

                    Surface(
                      color = if (fee.paymentStatus == "Overdue") Color(0xFF991B1B) else Color(0xFF854D0E),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = fee.paymentStatus,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Reusable, elevated Empty State component for prominent cards across My Tuition (FSI).
 * Designed for dark navy theme (#0A1220 / #111C2E) with glowing badge icon,
 * clear step-by-step guidance, and primary/outlined actionable call-to-action buttons.
 */
@Composable
fun SectionEmptyState(
  icon: ImageVector,
  title: String,
  description: String,
  actionLabel: String? = null,
  onActionClick: (() -> Unit)? = null,
  accentColor: Color = Color(0xFF38BDF8),
  secondaryActionLabel: String? = null,
  onSecondaryActionClick: (() -> Unit)? = null,
  hintSteps: List<String> = emptyList(),
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = Color(0xFF0A1220),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(accentColor.copy(alpha = 0.12f))
          .border(1.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(26.dp)
        )
      }

      Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )

      Text(
        text = description,
        fontSize = 12.sp,
        color = Color(0xFF94A3B8),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        lineHeight = 17.sp,
        modifier = Modifier.padding(horizontal = 12.dp)
      )

      // Optional helpful guidance checklist steps
      if (hintSteps.isNotEmpty()) {
        Surface(
          color = Color(0xFF111C2E),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            hintSteps.forEachIndexed { index, step ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "${index + 1}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                  )
                }
                Text(
                  text = step,
                  fontSize = 11.sp,
                  color = Color(0xFFCBD5E1),
                  lineHeight = 15.sp
                )
              }
            }
          }
        }
      }

      if (actionLabel != null && onActionClick != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Button(
            onClick = onActionClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.height(38.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = if (accentColor == Color(0xFF38BDF8) || accentColor == Color(0xFF4ADE80) || accentColor == Color(0xFFFBBF24)) Color(0xFF0F172A) else Color.White,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = if (accentColor == Color(0xFF38BDF8) || accentColor == Color(0xFF4ADE80) || accentColor == Color(0xFFFBBF24)) Color(0xFF0F172A) else Color.White
              )
            }
          }

          if (secondaryActionLabel != null && onSecondaryActionClick != null) {
            OutlinedButton(
              onClick = onSecondaryActionClick,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
              modifier = Modifier.height(38.dp)
            ) {
              Text(
                text = secondaryActionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}

/**
 * EmptyState preset tailored for specific dashboard cards.
 */
object DashboardEmptyStates {
  @Composable
  fun FeeTracker(
    onRecordPayment: () -> Unit,
    onOpenLedger: (() -> Unit)? = null,
    modifier: Modifier = Modifier
  ) {
    SectionEmptyState(
      icon = Icons.Outlined.AccountBalanceWallet,
      title = "No Fee Records Found",
      description = "No student fee structures or collections have been recorded for this session yet. Record student dues to initialize analytics, dues alerts, and official receipts.",
      actionLabel = "Record First Payment",
      onActionClick = onRecordPayment,
      secondaryActionLabel = if (onOpenLedger != null) "Open Ledger" else null,
      onSecondaryActionClick = onOpenLedger,
      accentColor = Color(0xFF4ADE80),
      hintSteps = listOf(
        "Select a student from your registered batches",
        "Choose tuition / admission fee amount & payment mode",
        "Receipt is auto-generated and logged to Room DB"
      ),
      modifier = modifier
    )
  }

  @Composable
  fun TodaysSchedule(
    onScheduleClass: () -> Unit,
    onAcademicSetup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
  ) {
    SectionEmptyState(
      icon = Icons.Outlined.Schedule,
      title = "No Classes Scheduled Today",
      description = "There are no active batches or dynamic class slots configured for today. Set up your first class or configure batch timetables to activate real-time schedule tracking.",
      actionLabel = "Schedule Class",
      onActionClick = onScheduleClass,
      secondaryActionLabel = if (onAcademicSetup != null) "Timetable Config" else null,
      onSecondaryActionClick = onAcademicSetup,
      accentColor = Color(0xFF38BDF8),
      hintSteps = listOf(
        "Pick batch, subject, classroom & faculty",
        "Set lecture start/end time slot with conflict check",
        "Automatic notifications sent to teachers & students"
      ),
      modifier = modifier
    )
  }
}

