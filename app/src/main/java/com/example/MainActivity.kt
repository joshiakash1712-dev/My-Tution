package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.security.FirebaseAppCheckManager
import com.example.ui.admin.*
import com.example.ui.components.*
import com.example.ui.login.UniversalLoginScreen
import com.example.ui.parent.*
import com.example.ui.student.*
import com.example.ui.teacher.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.pressSpring

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      AppRepository.initialize(applicationContext)
      FirebaseAppCheckManager.initialize(applicationContext)
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Startup initialization error: ${e.message}", e)
    }

    try {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
          requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
      }
    } catch (e: Exception) {
      android.util.Log.w("MainActivity", "Notification permission request skipped: ${e.message}")
    }

    enableEdgeToEdge()
    setContent {
      val darkThemeMode by AppRepository.darkThemeMode.collectAsState()
      val isDark = when (darkThemeMode) {
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
      }
      MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          SafeAreaProvider {
            MyTuitionApp()
          }
        }
      }
    }
  }
}

@Composable
fun MyTuitionApp() {
  val isLoggedIn by AppRepository.isLoggedIn.collectAsState()
  val currentUserIdentifier by AppRepository.currentUserIdentifier.collectAsState()
  val currentRole by AppRepository.currentRole.collectAsState()
  val passwordResetNotice by AppRepository.passwordResetNotice.collectAsState()
  val offlineActionBlockedNotice by AppRepository.offlineActionBlockedNotice.collectAsState()

  var currentTabIndex by remember { mutableStateOf(0) }
  var searchQuery by remember { mutableStateOf("") }
  var activeStudentForModal by remember { mutableStateOf<Student?>(null) }
  var isProfileOpen by remember { mutableStateOf(false) }

  // Connect AppRoleSwitcher singleton
  DisposableEffect(Unit) {
    AppRoleSwitcher.onSwitch = { newRole ->
      AppRepository.setRole(newRole)
      currentTabIndex = 0
      isProfileOpen = false
    }
    onDispose { AppRoleSwitcher.onSwitch = null }
  }

  // Define role specific tabs
  val bottomTabs = remember(currentRole) {
    when (currentRole) {
      UserRole.ADMIN -> listOf(
        "Dashboard" to Icons.Outlined.Home,
        "Students" to Icons.Outlined.People,
        "Fees" to Icons.Outlined.ReceiptLong,
        "Staff" to Icons.Outlined.Badge,
        "Batches" to Icons.Outlined.Layers,
        "Timetable" to Icons.Outlined.Schedule
      )
      UserRole.TEACHER -> listOf(
        "Dashboard" to Icons.Outlined.Home,
        "Attendance" to Icons.Outlined.FactCheck,
        "Tests & Marks" to Icons.Outlined.Assessment,
        "Timetable" to Icons.Outlined.Schedule
      )
      UserRole.STUDENT -> listOf(
        "Dashboard" to Icons.Outlined.Home,
        "Timetable" to Icons.Outlined.Schedule,
        "CBT Test" to Icons.Outlined.Quiz,
        "Gemini AI" to Icons.Outlined.AutoAwesome,
        "Study & Doubts" to Icons.Outlined.FactCheck
      )
      UserRole.PARENT -> listOf(
        "Dashboard" to Icons.Outlined.Home,
        "Attendance" to Icons.Outlined.CalendarMonth,
        "Test Standing" to Icons.Outlined.Assessment,
        "Fee Ledger" to Icons.Outlined.Payment,
        "Timetable" to Icons.Outlined.Schedule
      )
    }
  }

  // Toast / Banner alert for secure password reset notice
  LaunchedEffect(passwordResetNotice) {
    if (passwordResetNotice != null) {
      // Automatic clear reset notice after delay
    }
  }

  // 1. Unauthenticated State
  if (!isLoggedIn) {
    UniversalLoginScreen(onLoginSuccess = {
      currentTabIndex = 0
    })
    return
  }

  // 2. Authenticated State: Responsive Shell Layout Container
  if (activeStudentForModal != null) {
    DigitalIdCardDialog(student = activeStudentForModal!!) {
      activeStudentForModal = null
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 720.dp

    if (isWideScreen) {
      // TABLET / DESKTOP SIDEBAR NAVIGATION SHELL (Tailwind Slate-900 / Cosmic theme look)
      Row(modifier = Modifier.fillMaxSize()) {
        // Left Navigation Sidebar (Slate-900 color: 0xFF0F172A)
        Column(
          modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F172A))
            .padding(20.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Sidebar Header
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color(0xFF38BDF8)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.School,
                  contentDescription = null,
                  tint = Color(0xFF0F172A),
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = "My Tuition",
                  color = Color.White,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "FSI Platform v1.2",
                  color = Color(0xFF94A3B8),
                  fontSize = 11.sp
                )
              }
            }

            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

            // Logged-in Session Profile Badge
            Surface(
              color = Color(0xFF1E293B),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF38BDF8)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = currentRole.displayName.take(1),
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = currentUserIdentifier.substringBefore("@"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = currentRole.name,
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sidebar Menu Links (Pill navigation style)
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
              bottomTabs.forEachIndexed { index, (label, icon) ->
                val isSelected = currentTabIndex == index
                val itemBg by animateColorAsState(
                  targetValue = if (isSelected) Color(0xFF334155) else Color.Transparent,
                  label = "sidebar_bg"
                )
                val itemTextColor by animateColorAsState(
                  targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
                  label = "sidebar_txt"
                )
                val iconTint by animateColorAsState(
                  targetValue = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                  label = "sidebar_icon_tint"
                )

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .pressSpring(0.96f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(itemBg)
                    .clickable {
                      currentTabIndex = index
                      isProfileOpen = false
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                  )
                  Text(
                    text = label,
                    color = itemTextColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }

          // Sidebar Bottom Actions (Sign Out)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { AppRepository.logout() },
              shape = RoundedCornerShape(10.dp),
              border = ButtonDefaults.outlinedButtonBorder.copy(),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Log Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // Right-Side Main Content Area
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
        ) {
          // Inner Header bar for search & details
          GeoHeader(
            currentRole = currentRole,
            onProfileClick = { isProfileOpen = !isProfileOpen },
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            searchPlaceholder = when (currentRole) {
              UserRole.ADMIN -> "Search students, classes, files..."
              UserRole.TEACHER -> "Search assigned batches..."
              UserRole.STUDENT -> "Search solution papers..."
              UserRole.PARENT -> "Search child academic records..."
            }
          )

          // Password notice popup card
          passwordResetNotice?.let { notice ->
            Card(
              colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              shape = RoundedCornerShape(8.dp)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0369A1))
                  Text(notice, fontSize = 12.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { AppRepository.clearPasswordNotice() }, modifier = Modifier.size(24.dp)) {
                  Icon(Icons.Default.Close, contentDescription = "Close notice", tint = Color(0xFF0369A1), modifier = Modifier.size(14.dp))
                }
              }
            }
          }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .weight(1f)
          ) {
            AnimatedContent(
              targetState = isProfileOpen,
              transitionSpec = {
                fadeIn(animationSpec = tween(120, easing = FastOutSlowInEasing))
                  .togetherWith(fadeOut(animationSpec = tween(80, easing = FastOutLinearInEasing)))
              },
              label = "desktop_profile_transition"
            ) { showProfile ->
              if (showProfile) {
                ProfileScreen(
                  currentRole = currentRole,
                  userIdentifier = currentUserIdentifier,
                  onLogout = { AppRepository.logout() },
                  onClose = { isProfileOpen = false }
                )
              } else {
                AnimatedContent(
                  targetState = currentTabIndex,
                  transitionSpec = {
                    fadeIn(animationSpec = tween(100, easing = FastOutSlowInEasing))
                      .togetherWith(fadeOut(animationSpec = tween(70, easing = FastOutLinearInEasing)))
                  },
                  label = "desktop_tab_transition"
                ) { tabIndex ->
                  when (currentRole) {
                    UserRole.ADMIN -> AdminMainContent(tabIndex, searchQuery, { stu -> activeStudentForModal = stu }, { index -> currentTabIndex = index })
                    UserRole.TEACHER -> TeacherMainContent(tabIndex, { index -> currentTabIndex = index })
                    UserRole.STUDENT -> StudentMainContent(tabIndex, { index -> currentTabIndex = index })
                    UserRole.PARENT -> ParentMainContent(tabIndex, { index -> currentTabIndex = index })
                  }
                }
              }
            }
          }
        }
      }
    } else {
      // COMPACT PHONE VIEW (Bottom Navigation + Top Header scaffold style)
      Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          Column {
            GeoHeader(
              currentRole = currentRole,
              onProfileClick = { isProfileOpen = !isProfileOpen },
              searchQuery = searchQuery,
              onSearchChange = { searchQuery = it },
              searchPlaceholder = when (currentRole) {
                UserRole.ADMIN -> "Search students, leads, fees..."
                UserRole.TEACHER -> "Search assigned batches, tests..."
                UserRole.STUDENT -> "Search question solutions, notes..."
                UserRole.PARENT -> "Search child performance reports..."
              }
            )

            // Password notice bar
            passwordResetNotice?.let { notice ->
              Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    modifier = Modifier
                      .weight(1f)
                      .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0369A1))
                    Text(notice, fontSize = 11.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                  IconButton(onClick = { AppRepository.clearPasswordNotice() }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close notice", tint = Color(0xFF0369A1), modifier = Modifier.size(12.dp))
                  }
                }
              }
            }
          }
        },
        bottomBar = {
          GeoBottomNav(
            tabs = bottomTabs,
            selectedIndex = currentTabIndex.coerceIn(0, bottomTabs.size - 1),
            onTabSelected = {
              currentTabIndex = it
              isProfileOpen = false
            }
          )
        }
      ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          AnimatedContent(
            targetState = isProfileOpen,
            transitionSpec = {
              fadeIn(animationSpec = tween(120, easing = FastOutSlowInEasing))
                .togetherWith(fadeOut(animationSpec = tween(80, easing = FastOutLinearInEasing)))
            },
            label = "phone_profile_transition"
          ) { showProfile ->
            if (showProfile) {
              ProfileScreen(
                currentRole = currentRole,
                userIdentifier = currentUserIdentifier,
                onLogout = { AppRepository.logout() },
                onClose = { isProfileOpen = false }
              )
            } else {
              AnimatedContent(
                targetState = currentTabIndex,
                transitionSpec = {
                  (fadeIn(animationSpec = tween(90, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.988f, animationSpec = tween(90)))
                    .togetherWith(fadeOut(animationSpec = tween(60, easing = FastOutLinearInEasing)))
                },
                label = "phone_tab_transition"
              ) { tabIndex ->
                when (currentRole) {
                  UserRole.ADMIN -> AdminMainContent(tabIndex, searchQuery, { stu -> activeStudentForModal = stu }, { index -> currentTabIndex = index })
                  UserRole.TEACHER -> TeacherMainContent(tabIndex, { index -> currentTabIndex = index })
                  UserRole.STUDENT -> StudentMainContent(tabIndex, { index -> currentTabIndex = index })
                  UserRole.PARENT -> ParentMainContent(tabIndex, { index -> currentTabIndex = index })
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ProfileScreen(
  currentRole: UserRole,
  userIdentifier: String,
  onLogout: () -> Unit,
  onClose: (() -> Unit)? = null
) {
  var showLogoutConfirm by remember { mutableStateOf(false) }
  var showLogoutAllConfirm by remember { mutableStateOf(false) }
  var showAddAccountDialog by remember { mutableStateOf(false) }

  val savedAccounts by AppRepository.savedAccounts.collectAsState()
  val darkThemeModeState by AppRepository.darkThemeMode.collectAsState()

  if (showLogoutConfirm) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirm = false },
      title = { Text("Confirm Logout", fontWeight = FontWeight.Bold) },
      text = { Text("Are you sure you want to sign out of My Tuition (FSI)? Your active session will be ended securely.") },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirm = false
            onLogout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
          Text("Log Out", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirm = false }) {
          Text("Cancel")
        }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp)
    )
  }

  if (showLogoutAllConfirm) {
    AlertDialog(
      onDismissRequest = { showLogoutAllConfirm = false },
      title = { Text("Log Out of All Accounts", fontWeight = FontWeight.Bold) },
      text = { Text("This will sign you out of all saved accounts on this device. You will need to enter your email/ID and password to log back in.") },
      confirmButton = {
        Button(
          onClick = {
            showLogoutAllConfirm = false
            AppRepository.logoutAllAccounts()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
          Text("Log Out All", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutAllConfirm = false }) {
          Text("Cancel")
        }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp)
    )
  }

  if (showAddAccountDialog) {
    var addEmail by remember { mutableStateOf("") }
    var addPassword by remember { mutableStateOf("") }
    var addErrorMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { if (!isSubmitting) showAddAccountDialog = false },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Add Account (Log In)", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            "Log in to another account by entering its registered email/ID and password. Just like Instagram, you can switch between active accounts at any time.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          addErrorMsg?.let { err ->
            Surface(
              color = Color(0xFFFEF2F2),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = err,
                color = Color(0xFFDC2626),
                fontSize = 11.sp,
                modifier = Modifier.padding(10.dp)
              )
            }
          }

          OutlinedTextField(
            value = addEmail,
            onValueChange = { addEmail = it; addErrorMsg = null },
            label = { Text("Email ID / User Identifier") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = addPassword,
            onValueChange = { addPassword = it; addErrorMsg = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          enabled = !isSubmitting && addEmail.isNotBlank() && addPassword.isNotBlank(),
          onClick = {
            isSubmitting = true
            addErrorMsg = null
            FirebaseAuthService.signIn(
              email = addEmail,
              password = addPassword,
              onSuccess = { detectedRole, loggedEmail ->
                isSubmitting = false
                AppRepository.login(loggedEmail, detectedRole)
                showAddAccountDialog = false
              },
              onFailure = { errorReason ->
                isSubmitting = false
                addErrorMsg = errorReason
              }
            )
          }
        ) {
          if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
          } else {
            Text("Log In & Switch")
          }
        }
      },
      dismissButton = {
        TextButton(
          enabled = !isSubmitting,
          onClick = { showAddAccountDialog = false }
        ) {
          Text("Cancel")
        }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp)
    )
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    if (onClose != null) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable { onClose() }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "Back to Navigation",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
          IconButton(onClick = { onClose() }) {
            Icon(Icons.Default.Close, contentDescription = "Close Profile")
          }
        }
      }
    }
    // 1. Hero Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(24.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(CircleShape)
              .background(Color(0xFF38BDF8)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = userIdentifier.take(2).uppercase(),
              fontSize = 28.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0F172A)
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = userIdentifier.substringBefore("@").replaceFirstChar { it.uppercase() },
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = userIdentifier,
              fontSize = 12.sp,
              color = Color(0xFF94A3B8)
            )
          }

          Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF34D399),
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = currentRole.displayName + " Account",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
        }
      }
    }

    // 2. Instagram-Style Accounts & Switching Center
    item {
      Text(
        text = "Accounts & Profiles",
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "Saved Accounts on Device",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          savedAccounts.forEach { acc ->
            val isActive = acc.identifier.equals(userIdentifier, ignoreCase = true)
            Surface(
              color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
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
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = acc.displayName.take(1).uppercase(),
                      color = Color.White,
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp
                    )
                  }

                  Column {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Text(
                        text = acc.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Text(
                          text = acc.role.name,
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                    Text(
                      text = acc.identifier,
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  if (isActive) {
                    Surface(
                      color = Color(0xFFDCFCE7),
                      shape = RoundedCornerShape(20.dp)
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                      ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(12.dp))
                        Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                      }
                    }
                  } else {
                    Button(
                      onClick = { AppRepository.switchAccount(acc) },
                      shape = RoundedCornerShape(8.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      modifier = Modifier.height(32.dp)
                    ) {
                      Text("Switch", fontSize = 11.sp)
                    }
                  }

                  IconButton(
                    onClick = { AppRepository.removeAccount(acc.identifier) },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Remove Account",
                      tint = Color(0xFF94A3B8),
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }

          HorizontalDivider(color = Color(0xFFF1F5F9))

          // Add Account Action Button
          OutlinedButton(
            onClick = { showAddAccountDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Text("Log In to Another Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }
        }
      }
    }

    // 3. Account Statistics Row
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        when (currentRole) {
          UserRole.STUDENT -> {
            MetricBox("My Batch", "JEE Batch A", "Active Enrollment", Color(0xFFEFF6FF), Color(0xFF1E40AF), Modifier.weight(1f))
            MetricBox("Doubts Resolved", "12 / 14", "Last 30 Days", Color(0xFFFAF5FF), Color(0xFF6B21A8), Modifier.weight(1f))
          }
          UserRole.PARENT -> {
            val linkedStudentName = AppRepository.students.collectAsState().value.firstOrNull()?.name ?: "No Child Linked"
            MetricBox("Child Enrolled", linkedStudentName, "Student Profile", Color(0xFFEFF6FF), Color(0xFF1E40AF), Modifier.weight(1f))
            MetricBox("Fee Status", "Real-Time", "Up-to-date", Color(0xFFECFDF5), Color(0xFF065F46), Modifier.weight(1f))
          }
          UserRole.TEACHER -> {
            MetricBox("My Subjects", "Mathematics, Physics", "Secondary Level", Color(0xFFEFF6FF), Color(0xFF1E40AF), Modifier.weight(1f))
            MetricBox("Doubts Handled", "42 Solved", "All Batches", Color(0xFFECFDF5), Color(0xFF065F46), Modifier.weight(1f))
          }
          UserRole.ADMIN -> {
            MetricBox("System Status", "Healthy", "ERP Cloud Online", Color(0xFFECFDF5), Color(0xFF065F46), Modifier.weight(1f))
            MetricBox("Control Level", "Full Admin", "My Tuition FSI", Color(0xFFFFFBEB), Color(0xFF92400E), Modifier.weight(1f))
          }
        }
      }
    }

    // 4. Details Card
    item {
      Text("Personal Information", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          ProfileInfoRow(label = "Official Institution", value = "My Tuition Center (FSI)", icon = Icons.Default.School)
          HorizontalDivider(color = Color(0xFFF1F5F9))
          ProfileInfoRow(label = "Primary Email ID", value = userIdentifier, icon = Icons.Default.Email)
          HorizontalDivider(color = Color(0xFFF1F5F9))
          ProfileInfoRow(
            label = "Registration ID",
            value = when (currentRole) {
              UserRole.ADMIN -> "ADM-2026-FSI90"
              UserRole.TEACHER -> "TCH-2026-PHY42"
              UserRole.STUDENT -> "STU-2026-JEE101"
              UserRole.PARENT -> "PAR-2026-KUM29"
            },
            icon = Icons.Default.Badge
          )
          HorizontalDivider(color = Color(0xFFF1F5F9))
          ProfileInfoRow(label = "Location / Branch", value = "Primary Hub Campus, India", icon = Icons.Default.Map)
        }
      }
    }

    // 5. Theme Preference Section
    item {
      Text("Appearance & Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
              Text("Theme Preference", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf(
              DarkThemeMode.LIGHT to ("Light" to Icons.Default.LightMode),
              DarkThemeMode.DARK to ("Dark" to Icons.Default.DarkMode),
              DarkThemeMode.SYSTEM to ("System" to Icons.Default.SettingsSuggest)
            ).forEach { (mode, pair) ->
              val (label, icon) = pair
              val isSel = darkThemeModeState == mode
              Surface(
                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .clickable { AppRepository.setDarkThemeMode(mode) }
              ) {
                Row(
                  modifier = Modifier.padding(vertical = 10.dp),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }
    }

    // 6. Action Section
    item {
      Text("Account Settings & Security", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
      ) {
        Column {
          ProfileClickableActionRow(label = "Reset Security Password", icon = Icons.Default.Lock) {
            AppRepository.resetUserPassword(userIdentifier)
          }
          HorizontalDivider(color = Color(0xFFF1F5F9))
          ProfileClickableActionRow(label = "Notification Preferences", icon = Icons.Default.Notifications) {
            // Simulated action
          }
          HorizontalDivider(color = Color(0xFFF1F5F9))
          ProfileClickableActionRow(label = "About FSI Platform ERP", icon = Icons.Default.Info) {
            // Simulated action
          }
        }
      }
    }

    // 6. Logout Options
    item {
      Spacer(modifier = Modifier.height(4.dp))
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = { showLogoutConfirm = true },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = Color.White)
            Text("Sign Out of Current Account", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
          }
        }

        if (savedAccounts.size > 1) {
          TextButton(
            onClick = { showLogoutAllConfirm = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Log Out of All Accounts", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
fun ProfileInfoRow(label: String, value: String, icon: ImageVector) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
    Column {
      Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}

@Composable
fun ProfileClickableActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
      Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
  }
}
