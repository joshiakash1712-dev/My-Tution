package com.example.ui.login

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppRepository
import com.example.data.UserRole
import com.example.data.FirebaseAuthService
import com.example.data.DarkThemeMode
import com.example.security.SecurityEngine

@Composable
fun UniversalLoginScreen(
  onLoginSuccess: () -> Unit,
  authViewModel: AuthViewModel = viewModel()
) {
  val identifier by authViewModel.identifier.collectAsState()
  val password by authViewModel.password.collectAsState()
  val isLoading by authViewModel.isLoading.collectAsState()
  val loginError by authViewModel.loginError.collectAsState()
  val successMessage by authViewModel.successMessage.collectAsState()
  val lockoutRemainingSeconds by authViewModel.lockoutRemainingSeconds.collectAsState()

  var isPasswordVisible by remember { mutableStateOf(false) }
  var showForgotDialog by remember { mutableStateOf(false) }
  var rememberMe by remember { mutableStateOf(true) }

  val darkThemeModeState by AppRepository.darkThemeMode.collectAsState()
  val isSystemDark = isSystemInDarkTheme()
  val isDark = when (darkThemeModeState) {
    DarkThemeMode.LIGHT -> false
    DarkThemeMode.DARK -> true
    DarkThemeMode.SYSTEM -> isSystemDark
  }

  // Ticker for lockout timer only running when an active lockout countdown is present
  LaunchedEffect(lockoutRemainingSeconds) {
    if (lockoutRemainingSeconds > 0) {
      authViewModel.startLockoutTimerTicker()
    }
  }

  val accountStatus by authViewModel.accountStatus.collectAsState()
  val detectedRole = accountStatus.role

  // Atmospheric background gradient brushes
  val backgroundBrush = if (isDark) {
    Brush.verticalGradient(
      colors = listOf(
        Color(0xFF0B1120),
        Color(0xFF0F172A),
        Color(0xFF090D16)
      )
    )
  } else {
    Brush.verticalGradient(
      colors = listOf(
        Color(0xFFEBF2FA),
        Color(0xFFF1F5F9),
        Color(0xFFE2E8F0)
      )
    )
  }

  val cardContainerColor = if (isDark) Color(0xFF0D1526) else Color(0xFFFFFFFF)
  val cardBorderColor = if (isDark) Color(0xFF1E2D42) else Color(0xFFE2E8F0)
  val brandColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0061A4)
  val brandGradient = if (isDark) {
    Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1)))
  } else {
    Brush.linearGradient(listOf(Color(0xFF0061A4), Color(0xFF0284C7)))
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(backgroundBrush)
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    // Theme Switcher Button at top-right (1-tap instant switch between Light & Dark)
    IconButton(
      onClick = {
        val nextMode = if (isDark) DarkThemeMode.LIGHT else DarkThemeMode.DARK
        AppRepository.setDarkThemeMode(nextMode)
      },
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .size(44.dp)
        .background(
          color = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF),
          shape = CircleShape
        )
        .border(
          width = 1.dp,
          color = cardBorderColor,
          shape = CircleShape
        )
        .shadow(elevation = 3.dp, shape = CircleShape)
    ) {
      val icon = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode
      Icon(
        imageVector = icon,
        contentDescription = if (isDark) "Switch to Light theme" else "Switch to Dark theme",
        tint = brandColor,
        modifier = Modifier.size(20.dp)
      )
    }

    // Main Sign In Card Container
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 460.dp)
        .shadow(
          elevation = if (isDark) 12.dp else 8.dp,
          shape = RoundedCornerShape(28.dp),
          ambientColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFF64748B).copy(alpha = 0.2f),
          spotColor = if (isDark) Color.Black.copy(alpha = 0.6f) else Color(0xFF0F172A).copy(alpha = 0.15f)
        )
        .verticalScroll(rememberScrollState()),
      colors = CardDefaults.cardColors(containerColor = cardContainerColor),
      border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
      shape = RoundedCornerShape(28.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // App Identity Header
        Box(
          modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(brandGradient)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.School,
            contentDescription = "My Tuition App Logo",
            tint = Color.White,
            modifier = Modifier.size(38.dp)
          )
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "My Tuition (FSI)",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )

          Text(
            text = "Universal AI-Powered ERP Portal",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
        }

        // Lockout Notice
        if (lockoutRemainingSeconds > 0) {
          Surface(
            color = if (isDark) Color(0xFF450A0A) else Color(0xFFFEF2F2),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF991B1B) else Color(0xFFFCA5A5)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFDC2626)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Brute-Force Protection Active",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                  text = "Lockout expires in $lockoutRemainingSeconds second${if (lockoutRemainingSeconds == 1L) "" else "s"}.",
                  fontSize = 11.sp,
                  color = if (isDark) Color(0xFFF87171) else Color(0xFF7F1D1D),
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
              }
            }
          }
        }

        // Loading Indicator Banner
        if (isLoading) {
          Surface(
            color = brandColor.copy(alpha = if (isDark) 0.2f else 0.1f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, brandColor.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = brandColor,
                strokeWidth = 2.5.dp
              )
              Text(
                text = "Authenticating secure credentials...",
                fontSize = 13.sp,
                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            }
          }
        }

        // Success message banner
        if (successMessage != null && !isLoading) {
          Surface(
            color = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF059669) else Color(0xFF86EFAC)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
              Text(
                successMessage ?: "",
                fontSize = 12.sp,
                color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D),
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        // Error message banner
        if (loginError != null) {
          val isUnregisteredNotice = loginError?.contains("not found in tuition database", ignoreCase = true) == true
          Surface(
            color = if (isDark) {
              if (isUnregisteredNotice) Color(0xFF451A03) else Color(0xFF450A0A)
            } else {
              if (isUnregisteredNotice) Color(0xFFFFFBEB) else Color(0xFFFEE2E2)
            },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              if (isDark) {
                if (isUnregisteredNotice) Color(0xFFB45309) else Color(0xFF991B1B)
              } else {
                if (isUnregisteredNotice) Color(0xFFFCD34D) else Color(0xFFFCA5A5)
              }
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = if (isUnregisteredNotice) Icons.Default.AdminPanelSettings else Icons.Default.Error,
                contentDescription = null,
                tint = if (isUnregisteredNotice) Color(0xFFD97706) else Color(0xFFDC2626),
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = loginError ?: "",
                fontSize = 12.sp,
                color = if (isDark) {
                  if (isUnregisteredNotice) Color(0xFFFDE68A) else Color(0xFFFCA5A5)
                } else {
                  if (isUnregisteredNotice) Color(0xFF92400E) else Color(0xFFB91C1C)
                },
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        val manualActivationOverride by authViewModel.manualActivationOverride.collectAsState()
        val activationCode by authViewModel.activationCode.collectAsState()
        val newPasswordInput by authViewModel.newPasswordInput.collectAsState()
        val confirmPasswordInput by authViewModel.confirmPasswordInput.collectAsState()

        // Dynamic check: Does this account require first-time activation code or already has a password?
        val isActivationMode = manualActivationOverride ?: (accountStatus.exists && !accountStatus.isActivated)

        // --- SMART IDENTIFIER INPUT (EMAIL / PHONE / USERNAME / ID) ---
        OutlinedTextField(
          value = identifier,
          onValueChange = { text ->
            authViewModel.onIdentifierChanged(text)
          },
          label = { Text("Email, Mobile Number or ID") },
          placeholder = { Text("e.g. name@domain.com or phone number") },
          leadingIcon = {
            Icon(
              imageVector = if (accountStatus.exists) Icons.Default.AccountCircle else Icons.Default.Person,
              contentDescription = "Identifier Icon",
              tint = if (identifier.isNotEmpty()) brandColor else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
            )
          },
          trailingIcon = {
            if (identifier.isNotEmpty()) {
              IconButton(onClick = { authViewModel.onIdentifierChanged("") }) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear identifier",
                  modifier = Modifier.size(18.dp),
                  tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
              }
            }
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = brandColor,
            unfocusedBorderColor = cardBorderColor,
            focusedLabelColor = brandColor,
            cursorColor = brandColor,
            focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
            unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.3f) else Color(0xFFF8FAFC)
          )
        )

        // --- DYNAMIC CREDENTIAL INPUT (PASSWORD vs ACTIVATION CODE FLOW) ---
        if (isActivationMode) {
          // 1. First-Time Setup: Activation Code + Create Password Flow
          OutlinedTextField(
            value = activationCode,
            onValueChange = { authViewModel.onActivationCodeChanged(it) },
            label = { Text("6-Digit Institute Activation Code") },
            placeholder = { Text("Enter 6-digit PIN") },
            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null, tint = Color(0xFFD97706)) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFFD97706),
              focusedLabelColor = Color(0xFFD97706)
            )
          )

          OutlinedTextField(
            value = newPasswordInput,
            onValueChange = { authViewModel.onNewPasswordInputChanged(it) },
            label = { Text("Create New Password") },
            placeholder = { Text("At least 6 characters") },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = brandColor) },
            trailingIcon = {
              IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                Icon(if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
              }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          OutlinedTextField(
            value = confirmPasswordInput,
            onValueChange = { authViewModel.onConfirmPasswordInputChanged(it) },
            label = { Text("Confirm New Password") },
            placeholder = { Text("Re-enter password") },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = brandColor) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          // Toggle back to password option if user already set one
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
          ) {
            TextButton(
              onClick = { authViewModel.setManualActivationOverride(false) }
            ) {
              Text(
                "Already set your password? Switch to Standard Sign In",
                fontSize = 11.sp,
                color = brandColor,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            }
          }
        } else {
          // 2. Standard Login: Secure Password Flow
          OutlinedTextField(
            value = password,
            onValueChange = { text ->
              authViewModel.onPasswordChanged(text)
            },
            label = { Text("Secure Password") },
            placeholder = { Text("Enter account password") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Password Icon",
                tint = if (password.isNotEmpty()) brandColor else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
              )
            },
            trailingIcon = {
              IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                Icon(
                  imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = "Toggle password visibility",
                  tint = if (isPasswordVisible) brandColor else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                )
              }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              capitalization = KeyboardCapitalization.None,
              autoCorrectEnabled = false,
              imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
              onDone = {
                if (!isLoading && lockoutRemainingSeconds == 0L) {
                  authViewModel.performSignIn(onLoginSuccess = onLoginSuccess)
                }
              }
            ),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = brandColor,
              unfocusedBorderColor = cardBorderColor,
              focusedLabelColor = brandColor,
              cursorColor = brandColor,
              focusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
              unfocusedContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.3f) else Color(0xFFF8FAFC)
            )
          )

          // Remember Me & Forgot Password Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null
                ) { rememberMe = !rememberMe }
            ) {
              Checkbox(
                checked = rememberMe,
                onCheckedChange = { rememberMe = it },
                colors = CheckboxDefaults.colors(checkedColor = brandColor)
              )
              Text(
                text = "Remember me",
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
              )
            }

            Text(
              text = "Forgot Password?",
              color = brandColor,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
              modifier = Modifier
                .clickable { showForgotDialog = true }
                .padding(vertical = 4.dp, horizontal = 6.dp)
            )
          }

          // Optional manual activation code trigger
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
          ) {
            TextButton(
              onClick = { authViewModel.setManualActivationOverride(true) }
            ) {
              Text(
                "First time user? Activate with Code",
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            }
          }
        }

        val failedCount = SecurityEngine.getFailedAttemptCount(identifier)
        if (failedCount > 0 && lockoutRemainingSeconds == 0L && !isActivationMode) {
          Text(
            text = "⚠️ $failedCount failed attempt${if (failedCount == 1) "" else "s"}. Lockout triggers at 3 attempts.",
            fontSize = 11.sp,
            color = Color(0xFFD97706),
            fontWeight = FontWeight.Medium
          )
        }

        // --- DYNAMIC ACTION BUTTON ---
        Button(
          onClick = {
            if (isActivationMode) {
              authViewModel.activateAccountWithCode(onSuccess = { onLoginSuccess() })
            } else {
              authViewModel.performSignIn(onLoginSuccess = onLoginSuccess)
            }
          },
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (lockoutRemainingSeconds > 0) Color(0xFFDC2626) else if (isActivationMode) Color(0xFF059669) else brandColor
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
              elevation = if (isLoading || lockoutRemainingSeconds > 0) 0.dp else 4.dp,
              shape = RoundedCornerShape(16.dp),
              spotColor = if (isActivationMode) Color(0xFF059669).copy(alpha = 0.4f) else brandColor.copy(alpha = 0.4f)
            ),
          enabled = !isLoading && lockoutRemainingSeconds == 0L
        ) {
          if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
          } else if (lockoutRemainingSeconds > 0) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(imageVector = Icons.Default.LockClock, contentDescription = null, tint = Color.White)
              Text(
                "Locked Out (${lockoutRemainingSeconds}s)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            }
          } else if (isActivationMode) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
              Text(
                "Verify Code & Set Password",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            }
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(imageVector = Icons.Default.Login, contentDescription = null, tint = Color.White)
              Text(
                "Secure Sign In",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            }
          }
        }

        // --- VERIFIED ROLE & SECURITY NOTICE ---
        HorizontalDivider(
          color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
          modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.VerifiedUser,
            contentDescription = null,
            tint = brandColor,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (accountStatus.exists && detectedRole != null) {
              "Verified Directory Account: ${accountStatus.displayName} (${detectedRole.name})"
            } else {
              "Sign in with your registered Institute Email, Mobile, or ID"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
        }
      }
    }
  }

  // --- FORGOT PASSWORD / FORMAL SECURITY ASSISTANCE DIALOG ---
  if (showForgotDialog) {
    var resetEmail by remember(identifier) { mutableStateOf(if (identifier.contains("@")) identifier else "") }
    var resetStatusMsg by remember { mutableStateOf<String?>(null) }
    var resetIsSuccess by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showForgotDialog = false },
      icon = {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(brandColor.copy(alpha = if (isDark) 0.2f else 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Security Protocol",
            tint = brandColor,
            modifier = Modifier.size(26.dp)
          )
        }
      },
      title = {
        Text(
          text = "Password Reset Protocol",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          maxLines = 1,
          softWrap = false,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedTextField(
            value = resetEmail,
            onValueChange = {
              resetEmail = it
              resetStatusMsg = null
            },
            label = { Text("Registered Email Address") },
            placeholder = { Text("name@domain.com") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )

          if (resetStatusMsg != null) {
            Surface(
              color = if (resetIsSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
              shape = RoundedCornerShape(10.dp),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (resetIsSuccess) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = if (resetIsSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                  contentDescription = null,
                  tint = if (resetIsSuccess) Color(0xFF16A34A) else Color(0xFFDC2626),
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = resetStatusMsg ?: "",
                  fontSize = 11.sp,
                  color = if (resetIsSuccess) Color(0xFF15803D) else Color(0xFFB91C1C),
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }

          Surface(
            color = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.VerifiedUser,
                  contentDescription = null,
                  tint = brandColor,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Institutional Security Notice",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                  color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
                )
              }
              Text(
                text = "Password reset requires an active Firebase Authentication connection or verified Institute Administrator assistance.",
                fontSize = 12.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                lineHeight = 17.sp
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            authViewModel.performPasswordReset(resetEmail) { ok, msg ->
              resetIsSuccess = ok
              resetStatusMsg = msg
            }
          },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = brandColor),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
        ) {
          Text(
            text = "Send Password Reset Link",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showForgotDialog = false },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Close")
        }
      },
      containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF),
      shape = RoundedCornerShape(20.dp)
    )
  }
}

