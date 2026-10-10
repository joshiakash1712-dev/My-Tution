package com.example.data.db

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Safe representation of synchronization operation types for persistent Room-backed sync queueing.
 */
enum class SyncOperationType {
  CREATE,
  UPDATE,
  DELETE;

  companion object {
    fun fromString(value: String?): SyncOperationType {
      return when (value?.trim()?.uppercase()) {
        "CREATE", "INSERT", "ADD" -> CREATE
        "DELETE", "REMOVE", "DEACTIVATE" -> DELETE
        "UPDATE", "UPSERT", "MODIFY" -> UPDATE
        else -> UPDATE
      }
    }
  }
}

/**
 * Lifecycle status of a persistent synchronization queue entry.
 */
enum class SyncQueueStatus {
  PENDING,
  PROCESSING,
  FAILED,
  COMPLETED;

  companion object {
    fun fromString(value: String?): SyncQueueStatus {
      return when (value?.trim()?.uppercase()) {
        "PENDING", "PENDING_SYNC", "QUEUED" -> PENDING
        "PROCESSING", "IN_PROGRESS", "SYNCING" -> PROCESSING
        "FAILED", "SYNC_FAILED", "ERROR" -> FAILED
        "COMPLETED", "SYNCED", "DONE", "SUCCESS" -> COMPLETED
        else -> PENDING
      }
    }
  }
}

/**
 * Persistent Room-backed synchronization queue entry capable of surviving process death,
 * app restart, and temporary network loss.
 *
 * Security Invariant: Never persists raw passwords, password hashes/salts, authentication tokens,
 * Firebase secrets, or sensitive credentials.
 */
@Entity(
  tableName = "sync_queue",
  indices = [
    Index(value = ["status", "createdAt"]),
    Index(value = ["instituteId"]),
    Index(value = ["entityType", "entityId"])
  ]
)
data class SyncQueueEntity(
  @PrimaryKey
  val queueId: String = generateQueueId(),
  val instituteId: String = com.example.data.AppRepository.getCurrentInstituteId(),
  val entityType: String,
  val entityId: String,
  val operationType: SyncOperationType = SyncOperationType.UPDATE,
  val serializedPayload: String = "{}",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val retryCount: Int = 0,
  val lastAttemptAt: Long? = null,
  val status: SyncQueueStatus = SyncQueueStatus.PENDING,
  val errorMessage: String? = null
) {
  @get:Ignore
  val id: String
    get() = queueId

  fun sanitize(): SyncQueueEntity {
    val cleanPayload = sanitizeJsonPayload(serializedPayload)
    val cleanInstituteId = instituteId.trim().ifBlank { com.example.data.AppRepository.getCurrentInstituteId() }
    return if (cleanPayload == serializedPayload && cleanInstituteId == instituteId) {
      this
    } else {
      copy(
        instituteId = cleanInstituteId,
        serializedPayload = cleanPayload
      )
    }
  }

  companion object {
    private val SENSITIVE_KEY_PATTERNS = listOf(
      "password",
      "passwordhash",
      "password_hash",
      "passwordsalt",
      "password_salt",
      "rawpassword",
      "raw_password",
      "token",
      "authtoken",
      "auth_token",
      "idtoken",
      "id_token",
      "refreshtoken",
      "refresh_token",
      "sessiontoken",
      "session_token",
      "accesstoken",
      "access_token",
      "secret",
      "firebasesecret",
      "firebase_secret",
      "apikey",
      "api_key",
      "privatekey",
      "private_key",
      "credential",
      "credentials"
    )

    fun generateQueueId(prefix: String = "SYNC"): String {
      return "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    }

    fun isSensitiveKey(key: String): Boolean {
      val normalized = key.trim().lowercase()
      val compact = normalized.replace("_", "").replace("-", "")
      return SENSITIVE_KEY_PATTERNS.any { pattern ->
        normalized == pattern || compact == pattern || normalized.endsWith("_$pattern")
      } || compact.contains("password") ||
        compact.contains("secret") ||
        compact.contains("token") ||
        compact.contains("apikey") ||
        compact.contains("privatekey") ||
        compact.contains("credential")
    }

    fun sanitizePayloadMap(payload: Map<String, Any?>): Map<String, Any?> {
      val result = LinkedHashMap<String, Any?>()
      for ((key, value) in payload) {
        if (isSensitiveKey(key)) continue
        result[key] = sanitizePayloadValue(value)
      }
      return result
    }

    private fun sanitizePayloadValue(value: Any?): Any? {
      return when (value) {
        is Map<*, *> -> {
          sanitizePayloadMap(
            value.entries
              .filter { it.key is String }
              .associate { (it.key as String) to it.value }
          )
        }
        is Iterable<*> -> value.map { sanitizePayloadValue(it) }
        else -> value
      }
    }

    fun serializePayload(payload: Map<String, Any?>): String {
      return try {
        val sanitized = sanitizePayloadMap(payload)
        JSONObject(sanitized).toString()
      } catch (_: Exception) {
        "{}"
      }
    }

    fun sanitizeJsonPayload(rawJson: String): String {
      val trimmed = rawJson.trim()
      if (trimmed.isEmpty() || trimmed == "{}") return "{}"
      return try {
        val jsonObject = JSONObject(trimmed)
        sanitizeJsonObject(jsonObject).toString()
      } catch (_: Exception) {
        trimmed
      }
    }

    private fun sanitizeJsonObject(jsonObject: JSONObject): JSONObject {
      val cleaned = JSONObject()
      val keys = jsonObject.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        if (isSensitiveKey(key)) continue
        val value = jsonObject.opt(key)
        when (value) {
          is JSONObject -> cleaned.put(key, sanitizeJsonObject(value))
          is JSONArray -> cleaned.put(key, sanitizeJsonArray(value))
          else -> cleaned.put(key, value)
        }
      }
      return cleaned
    }

    private fun sanitizeJsonArray(jsonArray: JSONArray): JSONArray {
      val cleaned = JSONArray()
      for (i in 0 until jsonArray.length()) {
        val value = jsonArray.opt(i)
        when (value) {
          is JSONObject -> cleaned.put(sanitizeJsonObject(value))
          is JSONArray -> cleaned.put(sanitizeJsonArray(value))
          else -> cleaned.put(value)
        }
      }
      return cleaned
    }

    fun deserializePayload(serializedPayload: String): Map<String, Any> {
      val trimmed = serializedPayload.trim()
      if (trimmed.isEmpty()) return emptyMap()
      return try {
        val json = JSONObject(trimmed)
        jsonObjectToMap(json)
      } catch (_: Exception) {
        emptyMap()
      }
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any> {
      val map = LinkedHashMap<String, Any>()
      val keys = json.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        if (isSensitiveKey(key)) continue
        val value = json.opt(key)
        if (value != null && value !== JSONObject.NULL) {
          map[key] = when (value) {
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> jsonArrayToList(value)
            else -> value
          }
        }
      }
      return map
    }

    private fun jsonArrayToList(array: JSONArray): List<Any> {
      val list = ArrayList<Any>(array.length())
      for (i in 0 until array.length()) {
        val value = array.opt(i)
        if (value != null && value !== JSONObject.NULL) {
          list.add(
            when (value) {
              is JSONObject -> jsonObjectToMap(value)
              is JSONArray -> jsonArrayToList(value)
              else -> value
            }
          )
        }
      }
      return list
    }
  }
}
