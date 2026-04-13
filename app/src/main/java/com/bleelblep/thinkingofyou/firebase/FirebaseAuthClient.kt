package com.bleelblep.thinkingofyou.firebase

import android.content.Context
import android.util.Log
import com.bleelblep.thinkingofyou.BuildConfig
import com.bleelblep.thinkingofyou.data.ThinkingOfYouPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles Firebase Anonymous Authentication via REST API.
 * No Firebase SDK required.
 */
class FirebaseAuthClient(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseAuthClient"
        private const val AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
        private const val REFRESH_URL = "https://securetoken.googleapis.com/v1/token"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiKey: String
        get() = BuildConfig.FIREBASE_API_KEY

    data class AuthResult(
        val idToken: String,
        val refreshToken: String,
        val localId: String,
        val expiresIn: Int
    )

    /**
     * Custom exception for Firebase authentication errors.
     */
    class FirebaseAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Sign in anonymously to Firebase.
     */
    suspend fun signInAnonymously(): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isEmpty()) {
                val errorMsg = "Firebase API key not configured. Please check gradle.properties has FIREBASE_API_KEY set."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(FirebaseAuthException(errorMsg))
            }

            val url = "$AUTH_URL?key=$apiKey"
            val body = """{"returnSecureToken":true}"""
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Firebase sign in failed: HTTP ${response.code}. ${parseFirebaseError(responseBody)}"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(FirebaseAuthException(errorMsg))
            }

            val json = JSONObject(responseBody)
            val authResult = AuthResult(
                idToken = json.getString("idToken"),
                refreshToken = json.getString("refreshToken"),
                localId = json.getString("localId"),
                expiresIn = json.getString("expiresIn").toIntOrNull() ?: 3600
            )

            // Store the auth data
            ThinkingOfYouPreferences.storeAuthData(
                context,
                uid = authResult.localId,
                authToken = authResult.idToken,
                refreshToken = authResult.refreshToken,
                expiresInSeconds = authResult.expiresIn
            )

            Log.d(TAG, "Anonymous sign in successful, uid: ${authResult.localId}")
            Result.success(authResult)
        } catch (e: Exception) {
            Log.e(TAG, "Sign in error", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh the auth token using refresh token.
     */
    suspend fun refreshToken(refreshToken: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isEmpty()) {
                return@withContext Result.failure(Exception("Firebase API key not configured"))
            }

            val url = "$REFRESH_URL?key=$apiKey"
            val body = "grant_type=refresh_token&refresh_token=$refreshToken"
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Firebase token refresh failed: HTTP ${response.code}. ${parseFirebaseError(responseBody)}"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(FirebaseAuthException(errorMsg))
            }

            val json = JSONObject(responseBody)
            val authResult = AuthResult(
                idToken = json.getString("id_token"),
                refreshToken = json.getString("refresh_token"),
                localId = json.getString("user_id"),
                expiresIn = json.getString("expires_in").toIntOrNull() ?: 3600
            )

            // Store the refreshed auth data
            ThinkingOfYouPreferences.storeAuthData(
                context,
                uid = authResult.localId,
                authToken = authResult.idToken,
                refreshToken = authResult.refreshToken,
                expiresInSeconds = authResult.expiresIn
            )

            Log.d(TAG, "Token refresh successful")
            Result.success(authResult)
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            Result.failure(e)
        }
    }

    /**
     * Get a valid auth token, refreshing if necessary.
     */
    suspend fun getValidToken(): Result<String> {
        val currentToken = ThinkingOfYouPreferences.getAuthToken(context)
        val storedRefreshToken = ThinkingOfYouPreferences.getRefreshToken(context)

        // If no token exists, sign in fresh
        if (currentToken == null || storedRefreshToken == null) {
            val signInResult = signInAnonymously()
            return signInResult.map { it.idToken }
        }

        // If token is expired or near expiry, refresh it
        if (ThinkingOfYouPreferences.isTokenExpired(context)) {
            Log.d(TAG, "Token expired or near expiry, refreshing...")
            val refreshResult = refreshToken(storedRefreshToken)
            return refreshResult.map { it.idToken }
        }

        // Token is still valid
        return Result.success(currentToken)
    }

    /**
     * Initialize auth - either load from prefs or sign in fresh.
     */
    suspend fun initializeAuth(): Result<String> {
        val existingUid = ThinkingOfYouPreferences.getFirebaseUid(context)
        val existingToken = ThinkingOfYouPreferences.getAuthToken(context)

        if (existingUid != null && existingToken != null) {
            Log.d(TAG, "Found existing auth for uid: $existingUid")
            return getValidToken()
        }

        Log.d(TAG, "No existing auth, signing in anonymously...")
        val result = signInAnonymously()
        return result.map { it.idToken }
    }

    fun hasValidAuth(): Boolean {
        val uid = ThinkingOfYouPreferences.getFirebaseUid(context)
        val token = ThinkingOfYouPreferences.getAuthToken(context)
        val refreshToken = ThinkingOfYouPreferences.getRefreshToken(context)
        return uid != null && token != null && refreshToken != null
    }

    private fun parseFirebaseError(responseBody: String?): String {
        if (responseBody.isNullOrEmpty()) return "Empty response"
        return try {
            val json = JSONObject(responseBody)
            val error = json.optJSONObject("error")
            if (error != null) {
                val message = error.optString("message", "Unknown error")
                val code = error.optInt("code", 0)
                when {
                    message.contains("API_KEY_INVALID") -> "Invalid API key. Check your Firebase configuration."
                    message.contains("MISSING_API_KEY") -> "Missing API key. Configure FIREBASE_API_KEY in gradle.properties."
                    message.contains("INVALID_REFRESH_TOKEN") -> "Session expired. Please re-authenticate."
                    message.contains("TOKEN_EXPIRED") -> "Token expired. Refreshing..."
                    else -> "Error $code: $message"
                }
            } else {
                "Unexpected response format"
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message}"
        }
    }
}
