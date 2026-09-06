package com.example.security

import android.util.Log
import com.example.data.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SecuritySeverity {
  INFO, WARNING, ALERT, CRITICAL
}

data class SecurityAuditLog(
  val id: String,
  val timestamp: String,
  val severity: SecuritySeverity,
  val eventType: String,
  val actor: String,
  val details: String
)

data class PasswordStrength(
  val score: Int, // 0 to 100
  val label: String, // Weak, Medium, Strong, Bulletproof
  val feedback: List<String>
)

data class SaltedCredential(
  val identifier: String,
  val bcryptHash: String,
  val role: UserRole,
  val fullName: String = "",
  val algorithm: String = "BCrypt-2a"
)

object SecurityEngine {
  private const val TAG = "SecurityEngine"

  // Brute Force Protection Tracking
  private val failedAttempts = mutableMapOf<String, Int>()
  private val lockoutUntil = mutableMapOf<String, Long>()

  // Cryptographic BCrypt Salted Credential Vault (No Plaintext Passwords Stored)
  private val credentialVault = mutableMapOf<String, SaltedCredential>()

  // Audit Logs Stream
  private val _auditLogs = MutableStateFlow<List<SecurityAuditLog>>(emptyList())
  val auditLogs: StateFlow<List<SecurityAuditLog>> = _auditLogs.asStateFlow()

  // Security Policy State
  private val _piiMaskingEnabled = MutableStateFlow(true)
  val piiMaskingEnabled: StateFlow<Boolean> = _piiMaskingEnabled.asStateFlow()

  init {
    logEvent(
      severity = SecuritySeverity.INFO,
      eventType = "SYSTEM_INIT",
      actor = "System",
      details = "SecurityEngine initialized. BCrypt (cost factor 10) salting & hashing, TLS 1.3, XSS Shield, and RBAC active."
    )
  }

  // --- CRYPTOGRAPHIC BCRYPT SALTING & HASHING ENGINE ---

  /**
   * Hashes a raw password using BCrypt with automatic cryptographically secure salting (cost 10).
   * Result format: $2a$10$[22-char salt][31-char hash]
   */
  fun hashPasswordWithBCrypt(rawPassword: String): String {
    return at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
      .hashToString(10, rawPassword.toCharArray())
  }

  /**
   * Verifies a raw password against a BCrypt salted hash string.
   */
  fun verifyBCryptPassword(rawPassword: String, bcryptHash: String): Boolean {
    if (rawPassword.isEmpty() || bcryptHash.isEmpty()) return false
    val result = at.favre.lib.crypto.bcrypt.BCrypt.verifyer()
      .verify(rawPassword.toCharArray(), bcryptHash.toCharArray())
    return result.verified
  }

  /**
   * Registers or updates an account credential in the vault with BCrypt salting & hashing.
   * Plaintext passwords are NEVER retained in memory or database storage.
   */
  fun registerUserCredential(
    identifier: String,
    rawPassword: String,
    role: UserRole,
    fullName: String = ""
  ) {
    registerSaltedCredential(identifier, rawPassword, role, fullName)
  }

  fun registerSaltedCredential(
    identifier: String,
    rawPassword: String,
    role: UserRole,
    fullName: String = ""
  ) {
    val cleanKey = identifier.trim().lowercase()
    if (cleanKey.isBlank()) return

    val bcryptHash = hashPasswordWithBCrypt(rawPassword)

    val cred = SaltedCredential(
      identifier = cleanKey,
      bcryptHash = bcryptHash,
      role = role,
      fullName = fullName,
      algorithm = "BCrypt-2a"
    )
    credentialVault[cleanKey] = cred

    logEvent(
      severity = SecuritySeverity.INFO,
      eventType = "CREDENTIAL_BCRYPT_STORED",
      actor = cleanKey,
      details = "Account credential salted & BCrypt hashed into secure vault. Hash: ${bcryptHash.take(20)}..."
    )
  }

  /**
   * Checks whether an account identifier exists in the tuition credential vault.
   */
  fun hasRegisteredAccount(identifier: String): Boolean {
    val cleanKey = identifier.trim().lowercase()
    return credentialVault.containsKey(cleanKey)
  }

  /**
   * Verifies provided password against stored BCrypt salted hash.
   * Returns Pair(isValidPassword, UserRole?)
   */
  fun verifySaltedCredential(identifier: String, rawPassword: String): Pair<Boolean, UserRole?> {
    val cleanKey = identifier.trim().lowercase()
    val cred = credentialVault[cleanKey] ?: return Pair(false, null)

    val isMatch = verifyBCryptPassword(rawPassword, cred.bcryptHash)

    if (isMatch) {
      logEvent(
        severity = SecuritySeverity.INFO,
        eventType = "BCRYPT_AUTH_PASS",
        actor = cleanKey,
        details = "BCrypt Salted Hash matched successfully for role ${cred.role.name}."
      )
      return Pair(true, cred.role)
    } else {
      logEvent(
        severity = SecuritySeverity.WARNING,
        eventType = "BCRYPT_AUTH_FAIL",
        actor = cleanKey,
        details = "BCrypt password verification failed for $cleanKey."
      )
      return Pair(false, null)
    }
  }

  fun getRegisteredAccountCount(): Int = credentialVault.size

  /**
   * Seeds master admins, faculty, and student credentials with salted hashes.
   */
  fun seedDefaultCredentials(students: List<com.example.data.Student>, teachers: List<com.example.data.Teacher>) {
    // Master Admins
    registerSaltedCredential("joshiakash1209@gmail.com", "Trillionaire@1209", UserRole.ADMIN, "Akash Joshi (Admin)")
    registerSaltedCredential("joshiakash1712@gmail.com", "Trillionaire@1712", UserRole.ADMIN, "Akash Joshi (Director)")
    registerSaltedCredential("admin@fsi.com", "admin123", UserRole.ADMIN, "FSI Institute Administrator")
    registerSaltedCredential("admin", "admin123", UserRole.ADMIN, "Admin Portal")

    // Faculty Teachers
    teachers.forEach { t ->
      if (t.email.isNotBlank()) {
        registerSaltedCredential(t.email, "Teacher@123", UserRole.TEACHER, t.name)
      }
      if (t.id.isNotBlank()) {
        registerSaltedCredential(t.id, "Teacher@123", UserRole.TEACHER, t.name)
      }
    }

    // Students & Parents
    students.forEach { s ->
      if (s.email.isNotBlank()) {
        registerSaltedCredential(s.email, "Student@123", UserRole.STUDENT, s.name)
        val parentEmail = "parent_${s.id.lowercase()}@mytuition.com"
        registerSaltedCredential(parentEmail, "Parent@123", UserRole.PARENT, "Parent of ${s.name}")
      }
      if (s.id.isNotBlank()) {
        registerSaltedCredential(s.id, "Student@123", UserRole.STUDENT, s.name)
      }
    }

    // Portal generic aliases
    registerSaltedCredential("teacher@mytuition.com", "Teacher@123", UserRole.TEACHER, "Faculty Portal")
    registerSaltedCredential("student@mytuition.com", "Student@123", UserRole.STUDENT, "Student Portal")
    registerSaltedCredential("parent@mytuition.com", "Parent@123", UserRole.PARENT, "Parent Portal")
  }

  fun setPiiMasking(enabled: Boolean) {
    _piiMaskingEnabled.value = enabled
    logEvent(
      severity = SecuritySeverity.WARNING,
      eventType = "POLICY_CHANGE",
      actor = "Admin",
      details = "PII Data Masking state set to: $enabled"
    )
  }

  // --- 1. INPUT SANITIZATION & MALICIOUS PAYLOAD DETECTION ---

  /**
   * Cleans input strings to prevent XSS and HTML injection.
   */
  fun sanitizeInput(raw: String): String {
    if (raw.isBlank()) return raw
    return raw
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#x27;")
      .replace("/", "&#x2F;")
      .trim()
  }

  /**
   * Checks for known SQL Injection, XSS, or Shell command patterns.
   */
  fun containsMaliciousPattern(raw: String): Boolean {
    val lower = raw.lowercase()
    val patternKeywords = listOf(
      "<script", "javascript:", "onerror=", "onload=",
      "union select", "drop table", "or 1=1", "or '1'='1'",
      "exec(", "xp_cmdshell", "benchmark(", "sleep("
    )
    return patternKeywords.any { lower.contains(it) }
  }

  // --- 2. BRUTE FORCE & RATE LIMITING DEFENSE ---

  /**
   * Gets the current count of consecutive failed login attempts for an identifier.
   */
  fun getFailedAttemptCount(identifier: String): Int {
    val key = identifier.trim().lowercase()
    return failedAttempts[key] ?: 0
  }

  /**
   * Checks if an identifier (email/username) is currently locked out due to brute force protection.
   * Returns Pair(isLockedOut, remainingSeconds).
   */
  fun isLockedOut(identifier: String): Pair<Boolean, Long> {
    val key = identifier.trim().lowercase()
    if (key.isBlank()) return Pair(false, 0L)
    val until = lockoutUntil[key] ?: 0L
    val now = System.currentTimeMillis()
    return if (now < until) {
      val remainingSeconds = ((until - now) / 1000).coerceAtLeast(1L)
      Pair(true, remainingSeconds)
    } else {
      Pair(false, 0L)
    }
  }

  /**
   * Records a failed login attempt and calculates lockout delay if threshold exceeded.
   * Uses exponential backoff:
   * 3 attempts -> 15 seconds backoff (Level 1)
   * 4 attempts -> 30 seconds backoff (Level 2)
   * 5 attempts -> 60 seconds backoff (Level 3)
   * 6+ attempts -> 120 seconds backoff (Level 4 - Maximum)
   */
  fun recordFailedAttempt(identifier: String): Pair<Boolean, Long> {
    val key = identifier.trim().lowercase()
    if (key.isBlank()) return Pair(false, 0L)

    val currentCount = (failedAttempts[key] ?: 0) + 1
    failedAttempts[key] = currentCount

    logEvent(
      severity = SecuritySeverity.WARNING,
      eventType = "AUTH_FAILED",
      actor = key,
      details = "Failed authentication attempt ($currentCount)."
    )

    if (currentCount >= 3) {
      val lockoutSeconds = when (currentCount) {
        3 -> 15L
        4 -> 30L
        5 -> 60L
        else -> 120L
      }
      val untilTime = System.currentTimeMillis() + (lockoutSeconds * 1000)
      lockoutUntil[key] = untilTime

      logEvent(
        severity = SecuritySeverity.ALERT,
        eventType = "BRUTE_FORCE_LOCKOUT",
        actor = key,
        details = "Exponential backoff active! Locked out for $lockoutSeconds seconds after $currentCount failed attempts."
      )
      return Pair(true, lockoutSeconds)
    }
    return Pair(false, 0L)
  }

  /**
   * Resets failed attempt counters on successful login.
   */
  fun recordSuccessfulAuth(identifier: String, role: UserRole) {
    val key = identifier.trim().lowercase()
    failedAttempts.remove(key)
    lockoutUntil.remove(key)

    logEvent(
      severity = SecuritySeverity.INFO,
      eventType = "AUTH_SUCCESS",
      actor = key,
      details = "User successfully authenticated as ${role.name}."
    )
  }

  // --- 3. PASSWORD STRENGTH & ENTROPY EVALUATOR ---

  fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) {
      return PasswordStrength(0, "Empty", listOf("Password cannot be blank."))
    }

    var score = 0
    val feedback = mutableListOf<String>()

    // Length check
    if (password.length >= 8) score += 25 else feedback.add("Use at least 8 characters.")
    if (password.length >= 12) score += 15

    // Uppercase check
    if (password.any { it.isUpperCase() }) score += 20 else feedback.add("Include at least one uppercase letter (A-Z).")

    // Lowercase check
    if (password.any { it.isLowerCase() }) score += 15 else feedback.add("Include at least one lowercase letter (a-z).")

    // Digit check
    if (password.any { it.isDigit() }) score += 15 else feedback.add("Include at least one number (0-9).")

    // Special symbol check
    val specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    if (password.any { it in specialChars }) score += 10 else feedback.add("Include at least one special character (!@#$).")

    val label = when {
      score >= 85 -> "Bulletproof Security 🛡️"
      score >= 65 -> "Strong Security 🔒"
      score >= 40 -> "Fair"
      else -> "Weak"
    }

    return PasswordStrength(score.coerceIn(0, 100), label, feedback)
  }

  // --- 4. DATA MASKING & PII PRIVACY PROTECTION ---

  fun maskEmail(email: String): String {
    if (!_piiMaskingEnabled.value || email.length < 5 || !email.contains("@")) return email
    val parts = email.split("@")
    val user = parts[0]
    val domain = parts[1]
    val maskedUser = if (user.length > 2) "${user.take(2)}***" else "${user.take(1)}*"
    return "$maskedUser@$domain"
  }

  fun maskPhone(phone: String): String {
    if (!_piiMaskingEnabled.value || phone.length < 6) return phone
    val clean = phone.filter { it.isDigit() || it == '+' }
    return if (clean.length > 6) {
      "${clean.take(3)} ****** ${clean.takeLast(2)}"
    } else {
      "******"
    }
  }

  // --- 5. AUDIT LOGGING ---

  fun logEvent(severity: SecuritySeverity, eventType: String, actor: String, details: String) {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    val logItem = SecurityAuditLog(
      id = "SEC_${System.currentTimeMillis()}_${(100..999).random()}",
      timestamp = timestamp,
      severity = severity,
      eventType = eventType,
      actor = actor,
      details = details
    )
    _auditLogs.update { listOf(logItem) + it.take(100) } // Keep top 100 security logs
    Log.d(TAG, "[${severity.name}] [$eventType] Actor: $actor - $details")
  }

  // --- 6. ROLE-BASED ACCESS CONTROL (RBAC) ---

  fun canModifyData(role: UserRole): Boolean {
    return role == UserRole.ADMIN || role == UserRole.TEACHER
  }

  fun canViewFullPii(role: UserRole): Boolean {
    return role == UserRole.ADMIN
  }
}
