package com.bleelblep.thinkingofyou.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages preferences for the Thinking of You feature.
 * Uses the same preference name as Glupo for cross-app data sharing.
 */
object ThinkingOfYouPreferences {

    private const val PREFS_NAME = "glupo_thinking_of_you_prefs"

    // Pairing
    private const val KEY_PAIR_CODE = "pair_code"
    private const val KEY_DEVICE_SLOT = "device_slot"
    private const val KEY_PENDING_PAIR_CODE = "pending_pair_code"

    // Firebase Auth
    private const val KEY_FIREBASE_UID = "firebase_uid"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"

    // State
    private const val KEY_UNREAD_PING = "unread_ping"
    private const val KEY_LAST_PING_RECEIVED_AT = "last_ping_received_at"
    private const val KEY_PARTNER_ONLINE = "partner_online"

    // Feature Toggles
    const val PREF_FEATURE_ENABLED = "thinking_of_you_enabled"
    const val PREF_SOUND_ENABLED = "thinking_of_you_sound_enabled"
    const val PREF_VIBRATION_ENABLED = "thinking_of_you_vibration_enabled"
    const val PREF_LOOP_ANIMATION = "thinking_of_you_loop_animation"
    const val PREF_SCREEN_OVERLAY = "thinking_of_you_screen_overlay"

    // Animation Settings
    private const val PREF_ANIMATION_TYPE = "thinking_of_you_animation_type"
    private const val PREF_CUSTOM_TEXT = "thinking_of_you_custom_text"
    const val MAX_CUSTOM_TEXT_LENGTH = 8

    // Animation types
    const val ANIMATION_HEART = "heart"
    const val ANIMATION_CONFETTI = "confetti"
    const val ANIMATION_TEXT = "text"

    // History
    private const val KEY_HISTORY = "thinking_of_you_history"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Pairing

    fun getPairCode(context: Context): String? {
        return getPrefs(context).getString(KEY_PAIR_CODE, null)
    }

    fun setPairCode(context: Context, code: String?) {
        if (code == null) {
            getPrefs(context).edit().remove(KEY_PAIR_CODE).apply()
        } else {
            getPrefs(context).edit().putString(KEY_PAIR_CODE, code).apply()
        }
    }

    fun getDeviceSlot(context: Context): String? {
        return getPrefs(context).getString(KEY_DEVICE_SLOT, null)
    }

    fun setDeviceSlot(context: Context, slot: String?) {
        if (slot == null) {
            getPrefs(context).edit().remove(KEY_DEVICE_SLOT).apply()
        } else {
            getPrefs(context).edit().putString(KEY_DEVICE_SLOT, slot).apply()
        }
    }

    fun isPaired(context: Context): Boolean {
        val pairCode = getPairCode(context)
        val deviceSlot = getDeviceSlot(context)
        return !pairCode.isNullOrEmpty() && !deviceSlot.isNullOrEmpty()
    }

    // Pending pair code

    fun getPendingPairCode(context: Context): String? {
        return getPrefs(context).getString(KEY_PENDING_PAIR_CODE, null)
    }

    fun storePendingPairCode(context: Context, code: String) {
        getPrefs(context).edit().putString(KEY_PENDING_PAIR_CODE, code).apply()
    }

    fun clearPendingPairCode(context: Context) {
        getPrefs(context).edit().remove(KEY_PENDING_PAIR_CODE).apply()
    }

    fun hasPendingPairCode(context: Context): Boolean {
        return !getPendingPairCode(context).isNullOrEmpty()
    }

    // Firebase Auth

    fun getFirebaseUid(context: Context): String? {
        return getPrefs(context).getString(KEY_FIREBASE_UID, null)
    }

    fun setFirebaseUid(context: Context, uid: String?) {
        if (uid == null) {
            getPrefs(context).edit().remove(KEY_FIREBASE_UID).apply()
        } else {
            getPrefs(context).edit().putString(KEY_FIREBASE_UID, uid).apply()
        }
    }

    fun getAuthToken(context: Context): String? {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, null)
    }

    fun setAuthToken(context: Context, token: String?) {
        if (token == null) {
            getPrefs(context).edit().remove(KEY_AUTH_TOKEN).apply()
        } else {
            getPrefs(context).edit().putString(KEY_AUTH_TOKEN, token).apply()
        }
    }

    fun getRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    fun setRefreshToken(context: Context, token: String?) {
        if (token == null) {
            getPrefs(context).edit().remove(KEY_REFRESH_TOKEN).apply()
        } else {
            getPrefs(context).edit().putString(KEY_REFRESH_TOKEN, token).apply()
        }
    }

    fun getTokenExpiry(context: Context): Long {
        return getPrefs(context).getLong(KEY_TOKEN_EXPIRY, 0L)
    }

    fun setTokenExpiry(context: Context, expiry: Long) {
        getPrefs(context).edit().putLong(KEY_TOKEN_EXPIRY, expiry).apply()
    }

    fun isTokenExpired(context: Context): Boolean {
        val expiry = getTokenExpiry(context)
        if (expiry == 0L) return true
        return System.currentTimeMillis() >= (expiry - 5 * 60 * 1000)
    }

    // State

    fun hasUnreadPing(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_UNREAD_PING, false)
    }

    fun setUnreadPing(context: Context, unread: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_UNREAD_PING, unread).apply()
    }

    fun getLastPingReceivedAt(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_PING_RECEIVED_AT, 0L)
    }

    fun setLastPingReceivedAt(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_PING_RECEIVED_AT, timestamp).apply()
    }

    fun isPartnerOnline(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PARTNER_ONLINE, false)
    }

    fun setPartnerOnline(context: Context, online: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PARTNER_ONLINE, online).apply()
    }

    // Feature Toggles

    fun isFeatureEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_FEATURE_ENABLED, true)
    }

    fun setFeatureEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_FEATURE_ENABLED, enabled).apply()
    }

    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_SOUND_ENABLED, enabled).apply()
    }

    fun isVibrationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_VIBRATION_ENABLED, true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_VIBRATION_ENABLED, enabled).apply()
    }

    fun isLoopAnimationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_LOOP_ANIMATION, false)
    }

    fun setLoopAnimationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_LOOP_ANIMATION, enabled).apply()
    }

    fun isScreenOverlayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_SCREEN_OVERLAY, true)
    }

    fun setScreenOverlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_SCREEN_OVERLAY, enabled).apply()
    }

    // Animation Settings

    fun getAnimationType(context: Context): String {
        return getPrefs(context).getString(PREF_ANIMATION_TYPE, ANIMATION_HEART) ?: ANIMATION_HEART
    }

    fun setAnimationType(context: Context, type: String) {
        getPrefs(context).edit().putString(PREF_ANIMATION_TYPE, type).apply()
    }

    fun getCustomText(context: Context): String {
        return getPrefs(context).getString(PREF_CUSTOM_TEXT, "HI") ?: "HI"
    }

    fun setCustomText(context: Context, text: String) {
        val trimmed = text.take(MAX_CUSTOM_TEXT_LENGTH).uppercase()
        getPrefs(context).edit().putString(PREF_CUSTOM_TEXT, trimmed).apply()
    }

    // Clear pairing data

    fun clearPairing(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PAIR_CODE)
            .remove(KEY_DEVICE_SLOT)
            .remove(KEY_UNREAD_PING)
            .remove(KEY_LAST_PING_RECEIVED_AT)
            .remove(KEY_PARTNER_ONLINE)
            .apply()
    }

    // Clear all data

    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    // Store auth data

    fun storeAuthData(
        context: Context,
        uid: String,
        authToken: String,
        refreshToken: String,
        expiresInSeconds: Int
    ) {
        val expiry = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        getPrefs(context).edit()
            .putString(KEY_FIREBASE_UID, uid)
            .putString(KEY_AUTH_TOKEN, authToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiry)
            .apply()
    }

    // Store pairing data

    fun storePairingData(context: Context, pairCode: String, deviceSlot: String) {
        getPrefs(context).edit()
            .putString(KEY_PAIR_CODE, pairCode)
            .putString(KEY_DEVICE_SLOT, deviceSlot)
            .apply()
    }
}

/**
 * Data class representing a history entry
 */
data class HistoryEntry(
    val type: String,
    val timestamp: Long,
    val deliveryMode: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("timestamp", timestamp)
            put("deliveryMode", deliveryMode)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): HistoryEntry {
            return HistoryEntry(
                type = json.getString("type"),
                timestamp = json.getLong("timestamp"),
                deliveryMode = json.getString("deliveryMode")
            )
        }
    }
}

/**
 * Manages history entries for the Thinking of You feature.
 */
object HistoryStore {
    private const val PREFS_NAME = "glupo_thinking_of_you_prefs"
    private const val PREF_KEY = "thinking_of_you_history"
    private val lock = Any()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun addEntry(context: Context, entry: HistoryEntry) {
        synchronized(lock) {
            val existing = getAllInternal(context).toMutableList()
            existing.add(0, entry)
            val jsonArray = JSONArray()
            existing.forEach { jsonArray.put(it.toJson()) }
            getPrefs(context).edit().putString(PREF_KEY, jsonArray.toString()).apply()
        }
    }

    fun getAll(context: Context): List<HistoryEntry> {
        synchronized(lock) {
            return getAllInternal(context)
        }
    }

    private fun getAllInternal(context: Context): List<HistoryEntry> {
        val raw = getPrefs(context).getString(PREF_KEY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(raw)
            (0 until jsonArray.length()).map { i ->
                HistoryEntry.fromJson(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            getPrefs(context).edit().remove(PREF_KEY).apply()
        }
    }
}
