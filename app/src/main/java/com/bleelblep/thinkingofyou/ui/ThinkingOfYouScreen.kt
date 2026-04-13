package com.bleelblep.thinkingofyou.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.thinkingofyou.data.HistoryEntry
import com.bleelblep.thinkingofyou.data.HistoryStore
import com.bleelblep.thinkingofyou.data.ThinkingOfYouPreferences
import com.bleelblep.thinkingofyou.ui.components.ContentCard
import com.bleelblep.thinkingofyou.ui.components.ExpressiveButtonGroup
import com.bleelblep.thinkingofyou.ui.components.ExpressiveSwitch
import com.bleelblep.thinkingofyou.ui.theme.palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingOfYouScreen(
    isPaired: Boolean,
    pairCode: String?,
    hasUnreadPing: Boolean,
    onGenerateCode: suspend () -> Result<String>,
    onJoinWithCode: suspend (String) -> Result<Unit>,
    onCheckPartnerJoined: suspend (String) -> Boolean,
    onPairingComplete: (String) -> Unit,
    onSendPing: () -> Unit,
    onClearUnread: () -> Unit,
    onUnpair: () -> Unit,
    onFeatureToggle: (Boolean) -> Unit = {}
) {
    val p = MaterialTheme.palette
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var enteredCode by remember { mutableStateOf("") }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var featureEnabled by remember { mutableStateOf(ThinkingOfYouPreferences.isFeatureEnabled(context)) }
    var soundEnabled by remember { mutableStateOf(ThinkingOfYouPreferences.isSoundEnabled(context)) }
    var vibrationEnabled by remember { mutableStateOf(ThinkingOfYouPreferences.isVibrationEnabled(context)) }
    var loopAnimationEnabled by remember { mutableStateOf(ThinkingOfYouPreferences.isLoopAnimationEnabled(context)) }
    var screenOverlayEnabled by remember { mutableStateOf(ThinkingOfYouPreferences.isScreenOverlayEnabled(context)) }

    var animationType by remember { mutableStateOf(ThinkingOfYouPreferences.getAnimationType(context)) }
    var customText by remember { mutableStateOf(ThinkingOfYouPreferences.getCustomText(context)) }

    var historyEntries by remember { mutableStateOf(HistoryStore.getAll(context)) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(hasUnreadPing) {
        historyEntries = HistoryStore.getAll(context)
    }

    LaunchedEffect(generatedCode) {
        if (generatedCode != null) {
            while (generatedCode != null) {
                kotlinx.coroutines.delay(2000)
                val joined = onCheckPartnerJoined(generatedCode!!)
                if (joined) {
                    onPairingComplete(generatedCode!!)
                    generatedCode = null
                    break
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear all history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        HistoryStore.clear(context)
                        historyEntries = emptyList()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (generatedCode != null) {
        AlertDialog(
            onDismissRequest = {
                ThinkingOfYouPreferences.clearPendingPairCode(context)
                generatedCode = null
            },
            title = {
                Text(
                    text = "Your Pairing Code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Share this code with your partner:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.muted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = generatedCode!!,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 8.sp
                                ),
                                color = p.accent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Waiting for partner to join...",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.muted,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Pairing Code", generatedCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Code")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    ThinkingOfYouPreferences.clearPendingPairCode(context)
                    generatedCode = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.surface)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            item(key = "header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(p.brand)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Column {
                                Text(
                                    text = "Thinking of You",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "Send silent pings to your partner",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(p.brand, p.surface)
                                )
                            )
                    )
                }
            }

            // Unread ping indicator
            if (isPaired && hasUnreadPing) {
                item(key = "unread") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = p.accent.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = p.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Someone is thinking of you!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(onClick = onClearUnread) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            if (!isPaired) {
                // PAIRING SECTION
                item(key = "pairing") {
                    ContentCard(
                        title = "Connect with Someone",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Pair with another person to send silent \"thinking of you\" pings. " +
                                    "When you send a ping, they'll see a heart animation on their phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.muted
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        errorMessage?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                isGenerating = true
                                errorMessage = null
                                coroutineScope.launch {
                                    // Try up to 3 times in case of code collision
                                    var attempts = 0
                                    var lastError: String? = null
                                    while (attempts < 3) {
                                        attempts++
                                        val result = onGenerateCode()
                                        result.fold(
                                            onSuccess = { code ->
                                                generatedCode = code
                                                lastError = null
                                            },
                                            onFailure = { e ->
                                                lastError = e.message ?: "Failed to generate code"
                                                // Only retry on collision errors
                                                if (!lastError!!.contains("already in use", ignoreCase = true)) {
                                                    attempts = 3 // Stop retrying
                                                }
                                            }
                                        )
                                        if (generatedCode != null) break
                                    }
                                    if (generatedCode == null && lastError != null) {
                                        errorMessage = lastError
                                    }
                                    isGenerating = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGenerating && !isJoining
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Generate Pairing Code")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "— or —",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = enteredCode,
                            onValueChange = { value ->
                                if (value.length <= 6) {
                                    enteredCode = value.uppercase().filter { it.isLetterOrDigit() }
                                }
                            },
                            label = { Text("Enter Partner's Code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isJoining && !isGenerating
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = {
                                isJoining = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val result = onJoinWithCode(enteredCode)
                                    result.fold(
                                        onSuccess = { },
                                        onFailure = { e ->
                                            errorMessage = e.message ?: "Failed to join"
                                        }
                                    )
                                    isJoining = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enteredCode.length == 6 && !isJoining && !isGenerating
                        ) {
                            if (isJoining) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Join")
                        }
                    }
                }
            } else {
                // CONNECTED SECTION
                item(key = "connected") {
                    ContentCard(
                        title = "Send a Ping",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Button(
                            onClick = {
                                onSendPing()
                                historyEntries = HistoryStore.getAll(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = featureEnabled
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Send Ping",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (featureEnabled) {
                                "Tap to send a ping now"
                            } else {
                                "Enable the feature below to send pings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = p.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Settings section label
                item(key = "label_settings") {
                    Column(modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(
                            text = "SETTINGS",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color = p.muted
                        )
                    }
                }

                // Feature toggle card
                item(key = "feature_toggle") {
                    ContentCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ExpressiveSwitch(
                            title = "Enable Thinking of You",
                            subtitle = if (featureEnabled) "Listening for pings" else "Feature disabled",
                            checked = featureEnabled,
                            onCheckedChange = { enabled ->
                                featureEnabled = enabled
                                ThinkingOfYouPreferences.setFeatureEnabled(context, enabled)
                                onFeatureToggle(enabled)
                            }
                        )
                    }
                }

                // Notification settings (only when enabled)
                if (featureEnabled) {
                    item(key = "notification_settings") {
                        ContentCard(
                            title = "Notification Settings",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            ExpressiveSwitch(
                                title = "Sound",
                                subtitle = "Play sound on receive",
                                checked = soundEnabled,
                                onCheckedChange = { enabled ->
                                    soundEnabled = enabled
                                    ThinkingOfYouPreferences.setSoundEnabled(context, enabled)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            ExpressiveSwitch(
                                title = "Vibration",
                                subtitle = "Vibrate on receive",
                                checked = vibrationEnabled,
                                onCheckedChange = { enabled ->
                                    vibrationEnabled = enabled
                                    ThinkingOfYouPreferences.setVibrationEnabled(context, enabled)
                                }
                            )
                        }
                    }

                    item(key = "animation_settings") {
                        val isPhone3Device = remember { isPhone3() }

                        LaunchedEffect(isPhone3Device) {
                            if (!isPhone3Device && !screenOverlayEnabled) {
                                screenOverlayEnabled = true
                                ThinkingOfYouPreferences.setScreenOverlayEnabled(context, true)
                            }
                        }

                        ContentCard(
                            title = "Animation Settings",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Animation Style",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveButtonGroup(
                                options = listOf("Heart", "Confetti", "Text"),
                                selectedOption = when (animationType) {
                                    ThinkingOfYouPreferences.ANIMATION_HEART -> "Heart"
                                    ThinkingOfYouPreferences.ANIMATION_CONFETTI -> "Confetti"
                                    ThinkingOfYouPreferences.ANIMATION_TEXT -> "Text"
                                    else -> "Heart"
                                },
                                onOptionSelected = { option ->
                                    val type = when (option) {
                                        "Heart" -> ThinkingOfYouPreferences.ANIMATION_HEART
                                        "Confetti" -> ThinkingOfYouPreferences.ANIMATION_CONFETTI
                                        "Text" -> ThinkingOfYouPreferences.ANIMATION_TEXT
                                        else -> ThinkingOfYouPreferences.ANIMATION_HEART
                                    }
                                    animationType = type
                                    ThinkingOfYouPreferences.setAnimationType(context, type)
                                }
                            )

                            if (animationType == ThinkingOfYouPreferences.ANIMATION_TEXT) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { newText ->
                                        if (newText.length <= ThinkingOfYouPreferences.MAX_CUSTOM_TEXT_LENGTH) {
                                            customText = newText.uppercase()
                                            ThinkingOfYouPreferences.setCustomText(context, newText)
                                        }
                                    },
                                    label = { Text("Custom Text") },
                                    placeholder = { Text("HI") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    supportingText = {
                                        Text("${customText.length}/${ThinkingOfYouPreferences.MAX_CUSTOM_TEXT_LENGTH} characters")
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            ExpressiveSwitch(
                                title = "Loop animation",
                                subtitle = "Tap screen to stop",
                                checked = loopAnimationEnabled,
                                onCheckedChange = { enabled ->
                                    loopAnimationEnabled = enabled
                                    ThinkingOfYouPreferences.setLoopAnimationEnabled(context, enabled)
                                }
                            )

                            if (isPhone3Device) {
                                Spacer(modifier = Modifier.height(8.dp))

                                ExpressiveSwitch(
                                    title = "Screen animation",
                                    subtitle = "Show heart on display",
                                    checked = screenOverlayEnabled,
                                    onCheckedChange = { enabled ->
                                        screenOverlayEnabled = enabled
                                        ThinkingOfYouPreferences.setScreenOverlayEnabled(context, enabled)
                                    }
                                )
                            }
                        }
                    }
                }

                // History card (collapsible)
                item(key = "history") {
                    ContentCard(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { historyExpanded = !historyExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (historyEntries.isEmpty()) "No activity yet" else "${historyEntries.size} item${if (historyEntries.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.muted
                                )
                            }
                            Icon(
                                imageVector = if (historyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (historyExpanded) "Collapse" else "Expand",
                                tint = p.muted
                            )
                        }

                        AnimatedVisibility(
                            visible = historyExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = p.muted.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(12.dp))

                                if (historyEntries.isEmpty()) {
                                    Text(
                                        text = "Nothing sent or received yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = p.muted,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        historyEntries.take(20).forEach { entry ->
                                            HistoryRow(entry = entry, muted = p.muted, accent = p.accent)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    OutlinedButton(
                                        onClick = { showClearHistoryDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = p.muted
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Clear History")
                                    }
                                }
                            }
                        }
                    }
                }

                // Connection section label
                item(key = "label_connection") {
                    Column(modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(
                            text = "CONNECTION",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color = p.muted
                        )
                    }
                }

                // Connection info card
                item(key = "connection_info") {
                    ContentCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pair Code",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pairCode ?: "Unknown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.muted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = onUnpair,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Unpair")
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "This will disconnect you from your partner",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.muted.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // How it works
            item(key = "how_it_works") {
                ContentCard(
                    title = "How It Works",
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (!isPaired) {
                            "1. Generate a pairing code or enter your partner's code\n" +
                            "2. Once paired, tap the Send Ping button\n" +
                            "3. Your partner's phone will show a heart animation"
                        } else {
                            "1. Tap the Send Ping button\n" +
                            "2. Your partner's phone lights up with a heart animation\n" +
                            "3. They'll know you're thinking of them!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.muted
                    )
                }
            }
        }
    }
}

fun generatePairCode(): String {
    val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars.random() }.joinToString("")
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    muted: Color,
    accent: Color
) {
    val relativeTime = remember(entry.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            entry.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (entry.type == "sent") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = if (entry.type == "sent") "Sent" else "Received",
            tint = if (entry.type == "sent") accent else muted,
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.type == "sent") "Sent" else "Received",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = relativeTime,
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (entry.deliveryMode == "live") {
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            } else {
                muted.copy(alpha = 0.15f)
            }
        ) {
            Text(
                text = if (entry.deliveryMode == "live") "live" else "delivered later",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.deliveryMode == "live") Color(0xFF4CAF50) else muted,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun isPhone3(): Boolean {
    return Build.DEVICE.equals("Pong", ignoreCase = true)
}
