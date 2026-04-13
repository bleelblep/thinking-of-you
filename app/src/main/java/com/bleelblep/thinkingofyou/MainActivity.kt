package com.bleelblep.thinkingofyou

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.bleelblep.thinkingofyou.data.ThinkingOfYouPreferences
import com.bleelblep.thinkingofyou.firebase.FirebaseAuthClient
import com.bleelblep.thinkingofyou.firebase.FirebaseRestClient
import com.bleelblep.thinkingofyou.service.PingService
import com.bleelblep.thinkingofyou.ui.ThinkingOfYouScreen
import com.bleelblep.thinkingofyou.ui.generatePairCode
import com.bleelblep.thinkingofyou.ui.theme.ThinkingOfYouTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var authClient: FirebaseAuthClient
    private lateinit var restClient: FirebaseRestClient

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase clients
        authClient = FirebaseAuthClient(this)
        restClient = FirebaseRestClient(this, authClient)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ThinkingOfYouTheme {
                ThinkingOfYouApp()
            }
        }
    }

    @Composable
    fun ThinkingOfYouApp() {
        val coroutineScope = rememberCoroutineScope()

        // State
        var isPaired by remember { mutableStateOf(ThinkingOfYouPreferences.isPaired(this@MainActivity)) }
        var pairCode by remember { mutableStateOf(ThinkingOfYouPreferences.getPairCode(this@MainActivity)) }
        var hasUnreadPing by remember { mutableStateOf(ThinkingOfYouPreferences.hasUnreadPing(this@MainActivity)) }

        // Start ping service if paired and enabled
        LaunchedEffect(isPaired) {
            if (isPaired && ThinkingOfYouPreferences.isFeatureEnabled(this@MainActivity)) {
                PingService.start(this@MainActivity)
            }
        }

        ThinkingOfYouScreen(
            isPaired = isPaired,
            pairCode = pairCode,
            hasUnreadPing = hasUnreadPing,
            onGenerateCode = {
                // Generate a new pairing code
                val code = generatePairCode()

                // Initialize auth if needed
                val authResult = authClient.initializeAuth()
                if (authResult.isFailure) {
                    return@ThinkingOfYouScreen Result.failure(authResult.exceptionOrNull() ?: Exception("Auth failed"))
                }

                val uid = ThinkingOfYouPreferences.getFirebaseUid(this@MainActivity)
                    ?: return@ThinkingOfYouScreen Result.failure(Exception("No UID"))

                // Create pair in Firebase
                val createResult = restClient.createPair(code, uid)
                if (createResult.isFailure) {
                    return@ThinkingOfYouScreen Result.failure(createResult.exceptionOrNull() ?: Exception("Create pair failed"))
                }

                // Store the pending pair code
                ThinkingOfYouPreferences.storePendingPairCode(this@MainActivity, code)

                Result.success(code)
            },
            onJoinWithCode = { enteredCode ->
                // Initialize auth if needed
                val authResult = authClient.initializeAuth()
                if (authResult.isFailure) {
                    return@ThinkingOfYouScreen Result.failure(authResult.exceptionOrNull() ?: Exception("Auth failed"))
                }

                val uid = ThinkingOfYouPreferences.getFirebaseUid(this@MainActivity)
                    ?: return@ThinkingOfYouScreen Result.failure(Exception("No UID"))

                // Join the pair
                val joinResult = restClient.joinPair(enteredCode, uid)
                if (joinResult.isFailure) {
                    return@ThinkingOfYouScreen Result.failure(joinResult.exceptionOrNull() ?: Exception("Join failed"))
                }

                // Store pairing data
                ThinkingOfYouPreferences.storePairingData(this@MainActivity, enteredCode, "deviceB")
                isPaired = true
                pairCode = enteredCode

                // Start the ping service
                if (ThinkingOfYouPreferences.isFeatureEnabled(this@MainActivity)) {
                    PingService.start(this@MainActivity)
                }

                Result.success(Unit)
            },
            onCheckPartnerJoined = { code ->
                val status = restClient.checkPairAvailable(code)
                status.getOrNull()?.deviceBFilled == true
            },
            onPairingComplete = { code ->
                ThinkingOfYouPreferences.storePairingData(this@MainActivity, code, "deviceA")
                ThinkingOfYouPreferences.clearPendingPairCode(this@MainActivity)
                isPaired = true
                pairCode = code

                // Start the ping service
                if (ThinkingOfYouPreferences.isFeatureEnabled(this@MainActivity)) {
                    PingService.start(this@MainActivity)
                }
            },
            onSendPing = {
                PingService.sendPing(this@MainActivity)
            },
            onClearUnread = {
                ThinkingOfYouPreferences.setUnreadPing(this@MainActivity, false)
                hasUnreadPing = false
            },
            onUnpair = {
                coroutineScope.launch {
                    // Stop the ping service
                    PingService.stop(this@MainActivity)

                    // Delete pair from Firebase
                    pairCode?.let { code ->
                        restClient.deletePair(code)
                    }

                    // Clear local pairing data
                    ThinkingOfYouPreferences.clearPairing(this@MainActivity)
                    isPaired = false
                    pairCode = null
                }
            },
            onFeatureToggle = { enabled ->
                if (enabled && isPaired) {
                    PingService.start(this@MainActivity)
                } else {
                    PingService.stop(this@MainActivity)
                }
            }
        )
    }
}
