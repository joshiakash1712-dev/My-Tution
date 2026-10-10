package com.example.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.security.SecurityEngine
import com.example.security.SecuritySeverity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FirebaseAuthService {
  private const val TAG = "FirebaseAuthService"
  private const val ROLES_COLLECTION = "user_roles"
  private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _authStatus = MutableStateFlow("Firebase Auth: Initializing...")
  val authStatus = _authStatus.asStateFlow()

  private val isFirebaseInitialized: Boolean by lazy {
    try {
      FirebaseApp.getInstance()
      true
    } catch (e: IllegalStateException) {
      false
    }
  }

  // Safely initialize FirebaseAuth to prevent crashes if configuration is absent
  val auth: FirebaseAuth? by lazy {
    try {
      if (!isFirebaseInitialized) {
        Log.i(TAG, "FirebaseApp not initialized. Using local BCrypt vault verifier.")
        _authStatus.value = "Auth Local Vault Mode (Firebase not initialized)"
        null
      } else {
        val instance = FirebaseAuth.getInstance()
        _authStatus.value = "Firebase Auth Connected Successfully"
        instance
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to initialize Firebase Auth. Using local BCrypt vault verifier.", e)
      _authStatus.value = "Auth Local Vault Mode (Firebase not initialized)"
      null
    }
  }

  fun isAvailable(): Boolean {
    return auth != null
  }

  private fun runOnMainOrDirect(block: () -> Unit) {
    val mainLooper = try { Looper.getMainLooper() } catch (_: Exception) { null }
    if (mainLooper != null && Looper.myLooper() != mainLooper) {
      Handler(mainLooper).post(block)
    } else {
      block()
    }
  }

  /**
   * Checks for an existing active Firebase Auth session upon app launch.
   * If a session is present, fetches or detects their UserRole and invokes onSessionFound.
   */
  fun checkExistingSession(
    onSessionFound: (email: String, role: UserRole, uid: String) -> Unit,
    onNoSession: () -> Unit
  ) {
    val firebaseAuth = auth
    if (firebaseAuth == null) {
      onNoSession()
      return
    }

    val currentUser = firebaseAuth.currentUser
    if (currentUser != null) {
      val uid = currentUser.uid
      val userEmail = currentUser.email ?: currentUser.phoneNumber ?: uid
      Log.d(TAG, "Active Firebase Auth session discovered on app launch for: $userEmail (UID: $uid)")
      _authStatus.value = "Active session restored for $userEmail"

      fetchUserRole(uid, userEmail, { role ->
        onSessionFound(userEmail, role, uid)
      }, { _ ->
        val detectedRole = smartDetectRole(userEmail)
        onSessionFound(userEmail, detectedRole, uid)
      })
    } else {
      Log.d(TAG, "No active Firebase Auth session found on app launch.")
      onNoSession()
    }
  }

  // Authoritative Sign In with Role-Based Access Control Retrieval
  fun signIn(
    email: String,
    password: String,
    onSuccess: (UserRole, String) -> Unit,
    onFailure: (String) -> Unit
  ) {
    val rawClean = email.trim().lowercase()
    val cleanEmail = if (rawClean == "joshiakash1712") "joshiakash1712@gmail.com" else rawClean
    if (cleanEmail.isBlank() || password.isBlank()) {
      onFailure("Email/ID and password are required.")
      return
    }

    val accountStatus = AppRepository.getAccountStatus(cleanEmail)
    val existsInVault = SecurityEngine.hasRegisteredAccount(cleanEmail)
    val firebaseAuth = auth

    authScope.launch {
      try {
        // 1. Check local BCrypt credential vault first for instant, reliable authentication
        val (isSaltedValid, saltedRole) = SecurityEngine.verifySaltedCredential(cleanEmail, password)
        if (isSaltedValid && saltedRole != null) {
          val resolvedRole = accountStatus.role ?: saltedRole
          Log.i(TAG, "Salted password authentication succeeded for $cleanEmail as ${resolvedRole.name}")
          _authStatus.value = "Signed in successfully as ${resolvedRole.name}"
          runOnMainOrDirect { onSuccess(resolvedRole, cleanEmail) }
          return@launch
        }

        if (accountStatus.exists && accountStatus.role != null) {
          val canonicalId = when (accountStatus.role) {
            UserRole.TEACHER -> accountStatus.teacherId?.lowercase() ?: ""
            UserRole.STUDENT -> accountStatus.studentId?.lowercase() ?: ""
            UserRole.PARENT -> accountStatus.studentId?.let { "parent_${it.lowercase()}@mytuition.com" } ?: ""
            UserRole.ADMIN -> AppRepository.resolveCanonicalEmailForIdentifier(cleanEmail) ?: ""
          }
          if (canonicalId.isNotBlank() && canonicalId != cleanEmail) {
            val (isCanonicalValid, _) = SecurityEngine.verifySaltedCredential(canonicalId, password)
            if (isCanonicalValid) {
              Log.i(TAG, "Canonical salted password authentication succeeded for $cleanEmail as ${accountStatus.role.name}")
              _authStatus.value = "Signed in successfully as ${accountStatus.role.name}"
              runOnMainOrDirect { onSuccess(accountStatus.role, cleanEmail) }
              return@launch
            }
          }
        }

        // 2. If Firebase Authentication is available, attempt remote Firebase Auth sign-in
        if (firebaseAuth != null) {
          val resolvedEmail = if (cleanEmail.contains("@")) {
            cleanEmail
          } else {
            AppRepository.resolveCanonicalEmailForIdentifier(cleanEmail) ?: cleanEmail
          }
          _authStatus.value = "Signing in with Firebase Auth..."
          runOnMainOrDirect {
            firebaseAuth.signInWithEmailAndPassword(resolvedEmail, password)
              .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                val userEmail = (authResult.user?.email ?: resolvedEmail).trim().lowercase()
                Log.d(TAG, "Firebase Auth sign in successful for UID: $uid")

                fetchUserRole(uid, userEmail, { role ->
                  _authStatus.value = "Signed in successfully as ${role.name}"
                  onSuccess(role, userEmail)
                }, { _ ->
                  val detected = smartDetectRole(userEmail)
                  _authStatus.value = "Signed in (Role: ${detected.name})"
                  onSuccess(detected, userEmail)
                })
              }
              .addOnFailureListener { e ->
                Log.e(TAG, "Firebase Auth sign in failed", e)
                _authStatus.value = "Sign-In Failed: Invalid credentials"
                onFailure("Invalid email/ID or password. Please verify your credentials or contact institute administration.")
              }
          }
          return@launch
        }

        // 3. When Firebase Auth is unavailable, report unregistered or invalid password
        if (!existsInVault && !accountStatus.exists) {
          Log.w(TAG, "Login denied for '$cleanEmail': User account not registered in tuition database.")
          _authStatus.value = "Sign-In Denied: Unregistered Account"
          SecurityEngine.logEvent(
            severity = SecuritySeverity.WARNING,
            eventType = "UNREGISTERED_ACCOUNT_LOGIN_BLOCKED",
            actor = cleanEmail,
            details = "Login attempt stopped for $cleanEmail: Account not found in database records."
          )
          runOnMainOrDirect {
            onFailure("Account '$cleanEmail' not found in tuition database. Please contact institute administration to create your login account.")
          }
          return@launch
        }

        Log.w(TAG, "Sign-in failed for $cleanEmail: Invalid salted password hash.")
        _authStatus.value = "Sign-In Failed: Incorrect password"
        runOnMainOrDirect { onFailure("Invalid email/ID or password. Please verify your credentials.") }
      } catch (t: Throwable) {
        Log.e(TAG, "Unexpected error during sign-in for $cleanEmail", t)
        runOnMainOrDirect { onFailure("Authentication error: ${t.localizedMessage ?: "Please try again."}") }
      }
    }
  }

  // Real User Registration with designated UserRole & instituteId scoping (blocks client self-escalation to ADMIN)
  fun signUp(
    email: String,
    password: String,
    fullName: String,
    role: UserRole,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
    instituteId: String = AppRepository.getCurrentInstituteId()
  ) {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank() || password.length < 6) {
      onFailure("A valid email and password (minimum 6 characters) are required.")
      return
    }

    val activeSession = AppRepository.buildCurrentFirestoreAuthContext()
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val targetInstituteId = instituteId.trim().ifEmpty { activeInstituteId }

    // Prevent cross-institute user provisioning
    if (activeSession != null && !targetInstituteId.equals(activeSession.instituteId, ignoreCase = true)) {
      SecurityEngine.logEvent(
        severity = SecuritySeverity.CRITICAL,
        eventType = "CROSS_TENANT_ACCESS_DENIED",
        actor = activeSession.uid,
        details = "Blocked attempt to register account '$cleanEmail' in foreign institute '$targetInstituteId' from '${activeSession.instituteId}'."
      )
      onFailure("Cross-institute registration denied: Cannot create accounts for another institute.")
      return
    }

    // Prevent client-side privilege escalation to ADMIN
    val directoryStatus = AppRepository.getAccountStatus(cleanEmail)
    val isAuthorizedAdminProvisioning = (AppRepository.isAdminSession() && activeSession != null && activeSession.instituteId.equals(targetInstituteId, ignoreCase = true))
    if (role == UserRole.ADMIN && !isAuthorizedAdminProvisioning) {
      SecurityEngine.logEvent(
        severity = SecuritySeverity.CRITICAL,
        eventType = "ROLE_ESCALATION_BLOCKED",
        actor = activeSession?.uid ?: cleanEmail,
        details = "Blocked unauthorized client attempt to self-register or escalate '$cleanEmail' to ADMIN."
      )
      onFailure("Privilege escalation blocked: Client self-registration as ADMIN is prohibited.")
      return
    }

    // Ensure non-admin callers cannot claim a higher role than their directory record allows
    if (!isAuthorizedAdminProvisioning && directoryStatus.exists && directoryStatus.role != null && directoryStatus.role != role) {
      SecurityEngine.logEvent(
        severity = SecuritySeverity.CRITICAL,
        eventType = "ROLE_ESCALATION_BLOCKED",
        actor = cleanEmail,
        details = "Blocked attempt to register '$cleanEmail' with role ${role.name} instead of assigned role ${directoryStatus.role.name}."
      )
      onFailure("Role mismatch: Account '$cleanEmail' is only authorized for role ${directoryStatus.role.displayName}.")
      return
    }

    val firebaseAuth = auth
    if (firebaseAuth == null) {
      authScope.launch {
        SecurityEngine.registerSaltedCredential(
          identifier = cleanEmail,
          rawPassword = password,
          role = role,
          fullName = fullName,
          instituteId = targetInstituteId
        )
        Log.d(TAG, "Local vault registration completed for $cleanEmail in institute $targetInstituteId. Role: ${role.name}")
        _authStatus.value = "Registered in local vault for $cleanEmail"
        runOnMainOrDirect { onSuccess() }
      }
      return
    }

    _authStatus.value = "Creating account in Firebase Auth..."
    firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password)
      .addOnSuccessListener { authResult ->
        val uid = authResult.user?.uid ?: ""
        Log.d(TAG, "Firebase account created. UID: $uid. Writing user role mapping to Firestore...")

        saveUserRole(
          uid = uid,
          email = cleanEmail,
          fullName = fullName,
          role = role,
          instituteId = targetInstituteId,
          onSuccess = {
            _authStatus.value = "Registered successfully. Role: ${role.name}"
            onSuccess()
          },
          onFailure = { error ->
            Log.e(TAG, "Failed to save user role in Firestore", error)
            _authStatus.value = "Role registration failed: ${error.localizedMessage}"
            onFailure(error.localizedMessage ?: "Failed to assign role in Firestore")
          }
        )
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Firebase sign up failed", e)
        _authStatus.value = "Sign-Up Failed: ${e.localizedMessage}"
        onFailure(e.localizedMessage ?: "Failed to create account")
      }
  }

  // Trigger password reset email — NEVER pretends success when Firebase is unavailable
  fun sendPasswordResetEmail(
    email: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
  ) {
    val cleanEmail = email.trim()
    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
      _authStatus.value = "Reset Failed: Invalid email address"
      onFailure("Please provide a valid registered email address.")
      return
    }

    val firebaseAuth = auth
    if (firebaseAuth == null) {
      val errorMsg = "Password reset service is unavailable: Firebase Authentication is not configured."
      Log.w(TAG, errorMsg)
      _authStatus.value = "Reset Failed: Firebase Auth unavailable"
      onFailure(errorMsg)
      return
    }

    _authStatus.value = "Sending password reset email..."
    firebaseAuth.sendPasswordResetEmail(cleanEmail)
      .addOnSuccessListener {
        _authStatus.value = "Password reset email sent to $cleanEmail"
        onSuccess()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to send password reset email", e)
        _authStatus.value = "Reset Failed: ${e.localizedMessage}"
        onFailure(e.localizedMessage ?: "Failed to send reset email")
      }
  }

  // Logout from Firebase Auth
  fun signOut() {
    auth?.signOut()
    _authStatus.value = "Signed out of Firebase Auth"
  }

  // Helper: Save user role mapping in Firestore (strictly enforces instituteId and blocks self-escalation)
  fun saveUserRole(
    uid: String,
    email: String,
    fullName: String,
    role: UserRole,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit,
    instituteId: String = AppRepository.getCurrentInstituteId(),
    linkedEntityId: String = "",
    linkedStudentIds: List<String> = emptyList(),
    assignedBatchIds: List<String> = emptyList()
  ) {
    val cleanUid = uid.trim()
    val targetInstituteId = instituteId.trim()
    if (cleanUid.isEmpty() || targetInstituteId.isEmpty()) {
      onFailure(SecurityException("INVALID_ROLE_BINDING: uid and instituteId are required for /user_roles documents."))
      return
    }

    val activeSession = AppRepository.buildCurrentFirestoreAuthContext()
    if (activeSession != null && !activeSession.instituteId.equals(targetInstituteId, ignoreCase = true)) {
      SecurityEngine.logEvent(
        severity = SecuritySeverity.CRITICAL,
        eventType = "CROSS_TENANT_ACCESS_DENIED",
        actor = activeSession.uid,
        details = "Blocked attempt to write /user_roles/$cleanUid for foreign institute '$targetInstituteId'."
      )
      onFailure(SecurityException("CROSS_TENANT_ACCESS_DENIED: Cannot write user role for another institute."))
      return
    }

    if (activeSession == null || !AppRepository.isAdminSession()) {
      SecurityEngine.logEvent(
        severity = SecuritySeverity.CRITICAL,
        eventType = "ROLE_ESCALATION_BLOCKED",
        actor = activeSession?.uid ?: email,
        details = "Blocked non-admin attempt to write /user_roles/$cleanUid with role ${role.name}."
      )
      onFailure(SecurityException("ROLE_ESCALATION_BLOCKED: Only an authorized institute administrator or backend process can assign user roles."))
      return
    }

    val firestore = FirestoreService.db
    if (firestore == null) {
      onSuccess()
      return
    }

    val roleData = mapOf(
      "uid" to cleanUid,
      "email" to email.trim().lowercase(),
      "fullName" to fullName,
      "role" to role.name,
      "instituteId" to targetInstituteId,
      "linkedEntityId" to linkedEntityId,
      "linkedStudentIds" to linkedStudentIds,
      "assignedBatchIds" to assignedBatchIds,
      "createdAt" to System.currentTimeMillis()
    )

    firestore.collection(ROLES_COLLECTION)
      .document(cleanUid)
      .set(roleData)
      .addOnSuccessListener {
        Log.d(TAG, "User role recorded successfully in Firestore for UID: $cleanUid (institute: $targetInstituteId)")
        onSuccess()
      }
      .addOnFailureListener { e ->
        onFailure(e)
      }
  }

  // Helper: Fetch user role from Firestore mapping (verifying instituteId membership)
  private fun fetchUserRole(
    uid: String,
    email: String,
    onSuccess: (UserRole) -> Unit,
    onFailure: (String) -> Unit
  ) {
    val firestore = FirestoreService.db
    if (firestore == null) {
      onSuccess(smartDetectRole(email))
      return
    }

    firestore.collection(ROLES_COLLECTION)
      .document(uid)
      .get()
      .addOnSuccessListener { document ->
        if (document != null && document.exists()) {
          val roleStr = document.getString("role") ?: ""
          val docInstituteId = document.getString("instituteId")?.trim().orEmpty()
          if (docInstituteId.isEmpty()) {
            onFailure("Account membership document is missing required instituteId.")
            return@addOnSuccessListener
          }
          try {
            val role = UserRole.valueOf(roleStr)
            onSuccess(role)
          } catch (e: Exception) {
            Log.e(TAG, "Invalid role string: $roleStr", e)
            onFailure("Invalid user role configuration in institute membership record.")
          }
        } else {
          Log.w(TAG, "User role document not found for UID: $uid. Using verified directory lookup.")
          onSuccess(smartDetectRole(email))
        }
      }
      .addOnFailureListener { e ->
        onFailure(e.localizedMessage ?: "Firestore error")
      }
  }

  // Role lookup strictly backed by verified directory records and SecurityEngine vault
  private fun smartDetectRole(email: String): UserRole {
    val clean = email.trim().lowercase()
    val status = AppRepository.getAccountStatus(clean)
    if (status.exists && status.role != null) {
      return status.role
    }
    val vaultRole = SecurityEngine.getVerifiedRole(clean)
    if (vaultRole != null) {
      return vaultRole
    }
    return UserRole.STUDENT
  }
}
