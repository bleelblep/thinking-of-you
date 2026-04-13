package com.bleelblep.thinkingofyou.firebase

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bleelblep.thinkingofyou.data.ThinkingOfYouPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles Firebase Realtime Database operations via REST API.
 * Includes SSE listener for real-time ping updates.
 */
class FirebaseRestClient(
    private val context: Context,
    private val authClient: FirebaseAuthClient
) {

    companion object {
        private const val TAG = "FirebaseRestClient"
        private const val DATABASE_URL = "https://glupo-524a7-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // SSE client with no read timeout for streaming
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var eventSource: EventSource? = null
    private var reconnectAttempts = 0
    private val maxReconnectDelay = 60000L
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var pendingReconnectRunnable: Runnable? = null

    data class PairStatus(
        val exists: Boolean,
        val deviceAFilled: Boolean,
        val deviceBFilled: Boolean
    )

    data class PingData(
        val sentAt: Long,
        val animationType: String,
        val customText: String?
    )

    /**
     * Create a new pair - called by Person A (code generator).
     * First checks if code already exists to prevent overwriting existing pairs.
     */
    suspend fun createPair(pairCode: String, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if this pair code already exists
            val existingStatus = checkPairAvailable(pairCode).getOrNull()
            if (existingStatus != null && existingStatus.exists) {
                Log.w(TAG, "Pair code $pairCode already exists, cannot create")
                return@withContext Result.failure(Exception("Code already in use, please generate a new one"))
            }

            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode/devices/deviceA.json?auth=$token"
            val body = JSONObject().apply {
                put("uid", uid)
                put("online", true)
                put("lastSeen", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Create pair failed: ${response.code}")
                return@withContext Result.failure(Exception("Create pair failed: ${response.code}"))
            }

            Log.d(TAG, "Pair created: $pairCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Create pair error", e)
            Result.failure(e)
        }
    }

    /**
     * Join an existing pair - called by Person B (code enterer).
     */
    suspend fun joinPair(pairCode: String, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // First check if pair exists and has room
            val status = checkPairAvailable(pairCode).getOrThrow()
            if (!status.exists || !status.deviceAFilled) {
                return@withContext Result.failure(Exception("Invalid pair code"))
            }
            if (status.deviceBFilled) {
                return@withContext Result.failure(Exception("Pair already full"))
            }

            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode/devices/deviceB.json?auth=$token"
            val body = JSONObject().apply {
                put("uid", uid)
                put("online", true)
                put("lastSeen", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Join pair failed: ${response.code}")
                return@withContext Result.failure(Exception("Join pair failed: ${response.code}"))
            }

            Log.d(TAG, "Joined pair: $pairCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Join pair error", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a pair code exists and has room for another device.
     */
    suspend fun checkPairAvailable(pairCode: String): Result<PairStatus> = withContext(Dispatchers.IO) {
        try {
            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode/devices.json?auth=$token"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                if (response.code == 401) {
                    return@withContext Result.success(PairStatus(exists = false, deviceAFilled = false, deviceBFilled = false))
                }
                return@withContext Result.failure(Exception("Check pair failed: ${response.code}"))
            }

            if (responseBody == null || responseBody == "null") {
                return@withContext Result.success(PairStatus(exists = false, deviceAFilled = false, deviceBFilled = false))
            }

            val json = JSONObject(responseBody)
            val deviceAFilled = json.has("deviceA") && !json.isNull("deviceA")
            val deviceBFilled = json.has("deviceB") && !json.isNull("deviceB")

            Result.success(PairStatus(
                exists = deviceAFilled,
                deviceAFilled = deviceAFilled,
                deviceBFilled = deviceBFilled
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Check pair error", e)
            Result.failure(e)
        }
    }

    /**
     * Send a ping to the paired partner.
     */
    suspend fun sendPing(
        pairCode: String,
        fromUid: String,
        animationType: String = ThinkingOfYouPreferences.ANIMATION_HEART,
        customText: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode/pending.json?auth=$token"
            val body = JSONObject().apply {
                put("from", fromUid)
                put("sentAt", System.currentTimeMillis())
                put("delivered", false)
                put("animationType", animationType)
                if (animationType == ThinkingOfYouPreferences.ANIMATION_TEXT && !customText.isNullOrEmpty()) {
                    put("customText", customText)
                }
            }.toString()

            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Send ping failed: ${response.code}")
                return@withContext Result.failure(Exception("Send ping failed: ${response.code}"))
            }

            Log.d(TAG, "Ping sent from $fromUid with animation=$animationType")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Send ping error", e)
            Result.failure(e)
        }
    }

    /**
     * Mark a pending ping as delivered.
     */
    suspend fun markDelivered(pairCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode/pending/delivered.json?auth=$token"

            val request = Request.Builder()
                .url(url)
                .put("true".toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Mark delivered failed: ${response.code}")
                return@withContext Result.failure(Exception("Mark delivered failed: ${response.code}"))
            }

            Log.d(TAG, "Ping marked as delivered")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Mark delivered error", e)
            Result.failure(e)
        }
    }

    /**
     * Update online status and lastSeen.
     */
    suspend fun updatePresence(pairCode: String, deviceSlot: String, online: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode/devices/$deviceSlot.json?auth=$token"
            val uid = ThinkingOfYouPreferences.getFirebaseUid(context) ?: return@withContext Result.failure(Exception("No UID"))

            val body = JSONObject().apply {
                put("uid", uid)
                put("online", online)
                put("lastSeen", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Update presence failed: ${response.code}")
                return@withContext Result.failure(Exception("Update presence failed: ${response.code}"))
            }

            Log.d(TAG, "Presence updated: online=$online")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Update presence error", e)
            Result.failure(e)
        }
    }

    /**
     * Start SSE listener for pending pings.
     */
    fun startPingListener(
        pairCode: String,
        ownUid: String,
        onPingReceived: (pingData: PingData) -> Unit,
        onConnectionLost: () -> Unit
    ) {
        stopPingListener()

        val token = ThinkingOfYouPreferences.getAuthToken(context)
        if (token == null) {
            Log.e(TAG, "No auth token for SSE listener")
            onConnectionLost()
            return
        }

        val url = "$DATABASE_URL/pairs/$pairCode/pending.json?auth=$token"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE connection opened")
                reconnectAttempts = 0
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                Log.d(TAG, "SSE event: type=$type, data=$data")

                if (type == "put" || type == null) {
                    try {
                        val json = JSONObject(data)
                        val eventData = json.opt("data")

                        if (eventData != null && eventData != JSONObject.NULL && eventData is JSONObject) {
                            val from = eventData.optString("from", "")
                            val sentAt = eventData.optLong("sentAt", 0L)
                            val delivered = eventData.optBoolean("delivered", false)
                            val animationType = eventData.optString("animationType", ThinkingOfYouPreferences.ANIMATION_HEART)
                            val customText = eventData.optString("customText", null)

                            if (from.isNotEmpty() && from != ownUid && !delivered && sentAt > 0) {
                                Log.d(TAG, "Ping received from $from at $sentAt, animation=$animationType")
                                onPingReceived(PingData(sentAt, animationType, customText))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing SSE event", e)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE connection closed")
                scheduleReconnect(pairCode, ownUid, onPingReceived, onConnectionLost)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE connection failure: ${t?.message}", t)
                onConnectionLost()
                scheduleReconnect(pairCode, ownUid, onPingReceived, onConnectionLost)
            }
        }

        val factory = EventSources.createFactory(sseClient)
        eventSource = factory.newEventSource(request, listener)
        Log.d(TAG, "SSE listener started for pair: $pairCode")
    }

    fun stopPingListener() {
        cancelPendingReconnect()
        eventSource?.cancel()
        eventSource = null
        reconnectAttempts = 0
        Log.d(TAG, "SSE listener stopped")
    }

    private fun scheduleReconnect(
        pairCode: String,
        ownUid: String,
        onPingReceived: (PingData) -> Unit,
        onConnectionLost: () -> Unit
    ) {
        cancelPendingReconnect()

        reconnectAttempts++
        val delay = minOf(
            (1000L * (1 shl minOf(reconnectAttempts - 1, 6))),
            maxReconnectDelay
        )
        Log.d(TAG, "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempts)")

        val runnable = Runnable {
            pendingReconnectRunnable = null
            if (ThinkingOfYouPreferences.isPaired(context)) {
                startPingListener(pairCode, ownUid, onPingReceived, onConnectionLost)
            }
        }
        pendingReconnectRunnable = runnable
        reconnectHandler.postDelayed(runnable, delay)
    }

    private fun cancelPendingReconnect() {
        pendingReconnectRunnable?.let {
            reconnectHandler.removeCallbacks(it)
            pendingReconnectRunnable = null
        }
    }

    /**
     * Delete the pairing completely.
     */
    suspend fun deletePair(pairCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode.json?auth=$token"

            val request = Request.Builder()
                .url(url)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Delete pair failed: ${response.code}")
                return@withContext Result.failure(Exception("Delete pair failed: ${response.code}"))
            }

            Log.d(TAG, "Pair deleted: $pairCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Delete pair error", e)
            Result.failure(e)
        }
    }

    /**
     * Get partner's online status.
     */
    suspend fun getPartnerOnlineStatus(pairCode: String, ownSlot: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val partnerSlot = if (ownSlot == "deviceA") "deviceB" else "deviceA"
            val token = authClient.getValidToken().getOrThrow()
            val url = "$DATABASE_URL/pairs/$pairCode/devices/$partnerSlot/online.json?auth=$token"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.success(false)
            }

            Result.success(responseBody == "true")
        } catch (e: Exception) {
            Log.e(TAG, "Get partner status error", e)
            Result.failure(e)
        }
    }
}
