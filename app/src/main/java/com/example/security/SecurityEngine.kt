package com.example.security

import android.util.Log
import com.example.data.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
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
  private const val SESSION_TTL_MS = 7L * 24 * 60 * 60 * 1000L // 7 days

  // Brute Force Protection Tracking
  private val failedAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
  private val lockoutUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

  // Cryptographic BCrypt Salted Credential Vault (No Plaintext Passwords Stored)
  private val credentialVault = java.util.concurrent.ConcurrentHashMap<String, SaltedCredential>()
  private const val PREFS_VAULT = "fsi_security_credential_vault"
  private const val KEY_ACTIVE_SESSION = "active_session_record"
  private var prefs: android.content.SharedPreferences? = null
  private val secureRandom = SecureRandom()

  @Volatile
  private var activeSessionRecord: SessionTokenRecord? = null

  private data class SessionTokenRecord(
    val identifier: String,
    val tokenSha256: String,
    val role: UserRole,
    val expiresAt: Long
  )

  fun initialize(context: android.content.Context) {
    if (prefs == null) {
      prefs = context.applicationContext.getSharedPreferences(PREFS_VAULT, android.content.Context.MODE_PRIVATE)
    }
    purgeLegacyPlaintextCacheKeys()
    loadPersistedCredentials()
    loadPersistedSessionRecord()
  }

  /**
   * Purges any legacy keys that ever used a prefix like "def_hash_" from SharedPreferences.
   */
  private fun purgeLegacyPlaintextCacheKeys() {
    val p = prefs ?: return
    val legacyKeys = p.all.keys.filter { it.startsWith("def_hash_") }
    if (legacyKeys.isNotEmpty()) {
      val editor = p.edit()
      legacyKeys.forEach { editor.remove(it) }
      editor.apply()
    }
  }

  private fun loadPersistedCredentials() {
    val p = prefs ?: return
    p.all.forEach { (key, value) ->
      if (key.startsWith("cred_") && value is String) {
        val parts = value.split("|||")
        if (parts.size >= 4) {
          val cleanKey = key.removePrefix("cred_")
          val hash = parts[0]
          val roleStr = parts[1]
          val fullName = parts[2]
          val algorithm = parts[3]
          val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.STUDENT }
          credentialVault[cleanKey] = SaltedCredential(
            identifier = cleanKey,
            bcryptHash = hash,
            role = role,
            fullName = fullName,
            algorithm = algorithm
          )
        }
      }
    }
  }

  private fun loadPersistedSessionRecord() {
    val p = prefs ?: return
    val raw = p.getString(KEY_ACTIVE_SESSION, null) ?: return
    val parts = raw.split("|||")
    if (parts.size == 4) {
      val id = parts[0]
      val hash = parts[1]
      val role = try { UserRole.valueOf(parts[2]) } catch (_: Exception) { return }
      val expiresAt = parts[3].toLongOrNull() ?: return
      if (System.currentTimeMillis() < expiresAt) {
        activeSessionRecord = SessionTokenRecord(id, hash, role, expiresAt)
      } else {
        p.edit().remove(KEY_ACTIVE_SESSION).apply()
      }
    }
  }

  private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  /**
   * Issues a cryptographically random session token bound to the authenticated identifier and role.
   */
  fun issueSessionToken(identifier: String, role: UserRole): String {
    val cleanId = identifier.trim().lowercase()
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    val rawToken = bytes.joinToString("") { "%02x".format(it) }
    val tokenHash = sha256Hex(rawToken)
    val expiresAt = System.currentTimeMillis() + SESSION_TTL_MS
    val record = SessionTokenRecord(
      identifier = cleanId,
      tokenSha256 = tokenHash,
      role = role,
      expiresAt = expiresAt
    )
    activeSessionRecord = record
    prefs?.edit()?.putString(
      KEY_ACTIVE_SESSION,
      "$cleanId|||$tokenHash|||${role.name}|||$expiresAt"
    )?.apply()
    return rawToken
  }

  /**
   * Verifies a session token against the active cryptographic session record.
   * Returns the verified UserRole if valid and unexpired, or null otherwise.
   */
  fun verifySessionToken(identifier: String, rawToken: String): UserRole? {
    val cleanId = identifier.trim().lowercase()
    if (cleanId.isBlank() || rawToken.isBlank()) return null
    val record = activeSessionRecord ?: run {
      loadPersistedSessionRecord()
      activeSessionRecord
    } ?: return null

    if (System.currentTimeMillis() >= record.expiresAt) {
      revokeSessionToken()
      return null
    }
    val candidateHash = sha256Hex(rawToken)
    if (record.identifier != cleanId || !MessageDigest.isEqual(
        record.tokenSha256.toByteArray(Charsets.UTF_8),
        candidateHash.toByteArray(Charsets.UTF_8)
      )
    ) {
      return null
    }
    return record.role
  }

  /**
   * Invalidates the active session token upon logout or session expiration.
   */
  fun revokeSessionToken() {
    activeSessionRecord = null
    prefs?.edit()?.remove(KEY_ACTIVE_SESSION)?.apply()
  }

  /**
   * Clears all stored credentials and session tokens (used when resetting app data).
   */
  fun clearAllCredentials() {
    credentialVault.clear()
    failedAttempts.clear()
    lockoutUntil.clear()
    activeSessionRecord = null
    prefs?.edit()?.clear()?.apply()
  }

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

  // --- CRYPTOGRAPHIC BCRYPT SALTING & HASHING ENGINE (OFF MAIN THREAD) ---

  /**
   * Hashes a raw password using BCrypt with automatic cryptographically secure salting (cost 10)
   * on Dispatchers.Default so expensive key stretching never blocks the UI thread.
   */
  suspend fun hashPasswordWithBCrypt(rawPassword: String): String = withContext(Dispatchers.Default) {
    val chars = rawPassword.toCharArray()
    try {
      at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(10, chars)
    } finally {
      chars.fill('\u0000')
    }
  }

  /**
   * Verifies a raw password against a BCrypt salted hash string on Dispatchers.Default.
   */
  suspend fun verifyBCryptPassword(rawPassword: String, bcryptHash: String): Boolean = withContext(Dispatchers.Default) {
    if (rawPassword.isEmpty() || bcryptHash.isEmpty()) return@withContext false
    val pwdChars = rawPassword.toCharArray()
    val hashChars = bcryptHash.toCharArray()
    try {
      val result = at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(pwdChars, hashChars)
      result.verified
    } finally {
      pwdChars.fill('\u0000')
    }
  }

  /**
   * Registers or updates an account credential in the vault with BCrypt salting & hashing
   * on a background dispatcher. Plaintext passwords are NEVER retained in memory or storage.
   */
  suspend fun registerUserCredential(
    identifier: String,
    rawPassword: String,
    role: UserRole,
    fullName: String = ""
  ) {
    registerSaltedCredential(identifier, rawPassword, role, fullName)
  }

  suspend fun registerSaltedCredential(
    identifier: String,
    rawPassword: String,
    role: UserRole,
    fullName: String = ""
  ) = withContext(Dispatchers.Default) {
    val cleanKey = identifier.trim().lowercase()
    if (cleanKey.isBlank() || rawPassword.isBlank()) return@withContext

    val bcryptHash = hashPasswordWithBCrypt(rawPassword)

    val cred = SaltedCredential(
      identifier = cleanKey,
      bcryptHash = bcryptHash,
      role = role,
      fullName = fullName,
      algorithm = "BCrypt-2a"
    )
    credentialVault[cleanKey] = cred
    prefs?.edit()?.putString("cred_$cleanKey", "${cred.bcryptHash}|||${cred.role.name}|||${cred.fullName}|||${cred.algorithm}")?.apply()

    logEvent(
      severity = SecuritySeverity.INFO,
      eventType = "CREDENTIAL_BCRYPT_STORED",
      actor = cleanKey,
      details = "Account credential salted & BCrypt hashed into secure vault."
    )
  }

  /**
   * Checks whether an account identifier exists in the tuition credential vault.
   */
  fun hasRegisteredAccount(identifier: String): Boolean {
    val cleanKey = identifier.trim().lowercase()
    if (cleanKey.isBlank()) return false
    if (credentialVault.containsKey(cleanKey)) return true
    val stored = prefs?.getString("cred_$cleanKey", null)
    return !stored.isNullOrBlank()
  }

  fun getVerifiedRole(identifier: String): UserRole? {
    val cleanKey = identifier.trim().lowercase()
    if (cleanKey.isBlank()) return null
    credentialVault[cleanKey]?.let { return it.role }
    val stored = prefs?.getString("cred_$cleanKey", null)
    if (stored != null) {
      val parts = stored.split("|||")
      if (parts.size >= 4) {
        return try { UserRole.valueOf(parts[1]) } catch (_: Exception) { null }
      }
    }
    return null
  }

  /**
   * Verifies provided password against stored BCrypt salted hash on Dispatchers.Default.
   * Returns Pair(isValidPassword, UserRole?)
   */
  suspend fun verifySaltedCredential(identifier: String, rawPassword: String): Pair<Boolean, UserRole?> = withContext(Dispatchers.Default) {
    val cleanKey = identifier.trim().lowercase()
    if (cleanKey.isBlank() || rawPassword.isEmpty()) return@withContext Pair(false, null)
    var cred = credentialVault[cleanKey]
    if (cred == null) {
      val stored = prefs?.getString("cred_$cleanKey", null)
      if (stored != null) {
        val parts = stored.split("|||")
        if (parts.size >= 4) {
          val role = try { UserRole.valueOf(parts[1]) } catch (_: Exception) { UserRole.STUDENT }
          cred = SaltedCredential(
            identifier = cleanKey,
            bcryptHash = parts[0],
            role = role,
            fullName = parts[2],
            algorithm = parts[3]
          )
          credentialVault[cleanKey] = cred
        }
      }
    }
    if (cred == null) return@withContext Pair(false, null)

    val isMatch = verifyBCryptPassword(rawPassword, cred.bcryptHash)

    if (isMatch) {
      logEvent(
        severity = SecuritySeverity.INFO,
        eventType = "BCRYPT_AUTH_PASS",
        actor = cleanKey,
        details = "BCrypt Salted Hash matched successfully for role ${cred.role.name}."
      )
      Pair(true, cred.role)
    } else {
      logEvent(
        severity = SecuritySeverity.WARNING,
        eventType = "BCRYPT_AUTH_FAIL",
        actor = cleanKey,
        details = "BCrypt password verification failed for $cleanKey."
      )
      Pair(false, null)
    }
  }

  fun getRegisteredAccountCount(): Int = credentialVault.size

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
