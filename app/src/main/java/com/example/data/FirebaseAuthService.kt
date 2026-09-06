package com.example.data

import android.util.Log
import com.example.security.SecurityEngine
import com.example.security.SecuritySeverity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseAuthService {
  private const val TAG = "FirebaseAuthService"
  private const val ROLES_COLLECTION = "user_roles"

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
        Log.i(TAG, "FirebaseApp not initialized. Falling back to offline/simulation auth.")
        _authStatus.value = "Auth Offline Mode (google-services.json not configured)"
        null
      } else {
        val instance = FirebaseAuth.getInstance()
        _authStatus.value = "Firebase Auth Connected Successfully"
        instance
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to initialize Firebase Auth. Fallback to offline/simulation auth.", e)
      _authStatus.value = "Auth Offline Mode (google-services.json not configured)"
      null
    }
  }

  fun isAvailable(): Boolean {
    return auth != null
  }

  /**
   * Checks for an existing active Firebase Auth session upon app launch.
   * If a session is present, fetches or detects their UserRole and invokes onSuccess
   * so returning users are automatically logged in without re-verifying their identifier.
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
      }, { error ->
        val detectedRole = smartDetectRole(userEmail)
        onSessionFound(userEmail, detectedRole, uid)
      })
    } else {
      Log.d(TAG, "No active Firebase Auth session found on app launch.")
      onNoSession()
    }
  }

  // Real Sign In with Role-Based Access Control Retrieval
  fun signIn(
    email: String,
    password: String,
    onSuccess: (UserRole, String) -> Unit,
    onFailure: (String) -> Unit
  ) {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank() || password.isBlank()) {
      onFailure("Email/ID and password are required.")
      return
    }

    // 1. MANDATORY DATABASE USER ACCOUNT EXISTENCE CHECK
    // Verify if account is registered in tuition database or credential vault
    val existsInVault = SecurityEngine.hasRegisteredAccount(cleanEmail)
    val existsInStudents = AppRepository.students.value.any { it.email.lowercase() == cleanEmail || it.id.lowercase() == cleanEmail }
    val existsInTeachers = AppRepository.teachers.value.any { it.email.lowercase() == cleanEmail || it.id.lowercase() == cleanEmail }
    val isMasterAdmin = cleanEmail == "joshiakash1209@gmail.com" || cleanEmail == "joshiakash1712@gmail.com" || cleanEmail == "admin@fsi.com" || cleanEmail == "admin"

    if (!existsInVault && !existsInStudents && !existsInTeachers && !isMasterAdmin) {
      Log.w(TAG, "Login denied for '$cleanEmail': User account not registered in tuition database.")
      _authStatus.value = "Sign-In Denied: Unregistered Account"
      SecurityEngine.logEvent(
        severity = SecuritySeverity.WARNING,
        eventType = "UNREGISTERED_ACCOUNT_LOGIN_BLOCKED",
        actor = cleanEmail,
        details = "Login attempt stopped for $cleanEmail: Account not found in database records."
      )
      onFailure("Account '$cleanEmail' not found in tuition database. Please contact institute administration to create your login account.")
      return
    }

    // 2. SALTED HASHING PASSWORD VERIFICATION
    // Check against cryptographically salted SHA-256 password vault
    val (isSaltedValid, saltedRole) = SecurityEngine.verifySaltedCredential(cleanEmail, password)
    if (isSaltedValid && saltedRole != null) {
      Log.i(TAG, "Salted password authentication succeeded for $cleanEmail as ${saltedRole.name}")
      _authStatus.value = "Signed in successfully as ${saltedRole.name}"
      onSuccess(saltedRole, cleanEmail)
      return
    }

    // Fallback or Cloud Sync with Firebase Auth
    val firebaseAuth = auth
    if (firebaseAuth != null) {
      _authStatus.value = "Signing in with Firebase Auth..."
      firebaseAuth.signInWithEmailAndPassword(email, password)
        .addOnSuccessListener { authResult ->
          val uid = authResult.user?.uid ?: ""
          val userEmail = authResult.user?.email ?: email
          Log.d(TAG, "Firebase Auth sign in successful for UID: $uid")
          
          fetchUserRole(uid, userEmail, { role ->
            SecurityEngine.registerSaltedCredential(userEmail, password, role)
            _authStatus.value = "Signed in successfully as ${role.name}"
            onSuccess(role, userEmail)
          }, { error ->
            val detected = smartDetectRole(userEmail)
            SecurityEngine.registerSaltedCredential(userEmail, password, detected)
            _authStatus.value = "Signed in (Role: ${detected.name})"
            onSuccess(detected, userEmail)
          })
        }
        .addOnFailureListener { e ->
          Log.e(TAG, "Firebase Auth sign in failed", e)
          _authStatus.value = "Sign-In Failed: Invalid credentials"
          onFailure("Invalid email/ID or password. Please verify your credentials or contact institute administration.")
        }
    } else {
      Log.w(TAG, "Sign-in failed for $cleanEmail: Invalid salted password hash.")
      _authStatus.value = "Sign-In Failed: Incorrect password"
      onFailure("Invalid email/ID or password. Please verify your credentials.")
    }
  }

  // Real User Registration with designated UserRole
  fun signUp(
    email: String,
    password: String,
    fullName: String,
    role: UserRole,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
  ) {
    val cleanEmail = email.trim().lowercase()

    // Register BCrypt salted credential locally
    SecurityEngine.registerSaltedCredential(cleanEmail, password, role, fullName)

    val firebaseAuth = auth
    if (firebaseAuth == null) {
      // Local Fallback simulation
      Log.d(TAG, "FirebaseAuth offline fallback sign up. Role: ${role.name}")
      _authStatus.value = "Offline Sign-Up simulated for $cleanEmail (BCrypt Salted)"
      onSuccess()
      return
    }

    _authStatus.value = "Creating account in Firebase Auth..."
    firebaseAuth.createUserWithEmailAndPassword(email, password)
      .addOnSuccessListener { authResult ->
        val uid = authResult.user?.uid ?: ""
        Log.d(TAG, "Firebase account created. UID: $uid. Writing BCrypt salted user role mapping to Firestore...")

        // Save User Role mapping in Firestore with BCrypt Hash
        val bcryptHash = SecurityEngine.hashPasswordWithBCrypt(password)
        saveUserRole(uid, cleanEmail, fullName, role, bcryptHash, {
          _authStatus.value = "Registered successfully. Role: ${role.name}"
          onSuccess()
        }, { error ->
          Log.e(TAG, "Failed to save user role in Firestore", error)
          _authStatus.value = "Account created. Role save fallback applied."
          onSuccess()
        })
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Firebase sign up failed", e)
        _authStatus.value = "Sign-Up Failed: ${e.localizedMessage}"
        onFailure(e.localizedMessage ?: "Failed to create account")
      }
  }

  // Trigger password reset email
  fun sendPasswordResetEmail(
    email: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
  ) {
    val firebaseAuth = auth
    if (firebaseAuth == null) {
      Log.d(TAG, "FirebaseAuth offline fallback password reset.")
      onSuccess()
      return
    }

    _authStatus.value = "Sending password reset email..."
    firebaseAuth.sendPasswordResetEmail(email)
      .addOnSuccessListener {
        _authStatus.value = "Password reset email sent to $email"
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

  // Helper: Save user role mapping in Firestore with BCrypt Salted Hash
  private fun saveUserRole(
    uid: String,
    email: String,
    fullName: String,
    role: UserRole,
    bcryptHash: String = "",
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
  ) {
    val firestore = FirestoreService.db
    if (firestore == null) {
      onSuccess()
      return
    }

    val roleData = mapOf(
      "uid" to uid,
      "email" to email,
      "fullName" to fullName,
      "role" to role.name,
      "bcryptHash" to bcryptHash,
      "passwordStorage" to "BCrypt-2a (Salted & Hashed)",
      "createdAt" to System.currentTimeMillis()
    )

    firestore.collection(ROLES_COLLECTION)
      .document(uid)
      .set(roleData)
      .addOnSuccessListener {
        Log.d(TAG, "User role & BCrypt hash recorded successfully in Firestore for UID: $uid")
        onSuccess()
      }
      .addOnFailureListener { e ->
        onFailure(e)
      }
  }

  // Helper: Fetch user role from Firestore mapping
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
          try {
            val role = UserRole.valueOf(roleStr)
            onSuccess(role)
          } catch (e: Exception) {
            Log.e(TAG, "Invalid role string: $roleStr", e)
            onSuccess(smartDetectRole(email))
          }
        } else {
          // Document doesn't exist, try smart detection
          Log.w(TAG, "User role document not found for UID: $uid. Using fallback smart detection.")
          onSuccess(smartDetectRole(email))
        }
      }
      .addOnFailureListener { e ->
        onFailure(e.localizedMessage ?: "Firestore error")
      }
  }

  // Fallback Role Detector based on email/username conventions
  private fun smartDetectRole(email: String): UserRole {
    val clean = email.trim().lowercase()
    return when {
      clean == "admin" || clean.contains("admin") || clean == "joshiakash1209@gmail.com" || clean == "joshiakash1712@gmail.com" -> UserRole.ADMIN
      clean.contains("teacher") || clean.contains("faculty") -> UserRole.TEACHER
      clean.contains("parent") -> UserRole.PARENT
      else -> UserRole.STUDENT
    }
  }
}
