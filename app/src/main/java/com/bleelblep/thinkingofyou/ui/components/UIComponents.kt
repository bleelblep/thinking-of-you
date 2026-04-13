package com.bleelblep.thinkingofyou.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.bleelblep.thinkingofyou.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Standard card component with Material 3 styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = MaterialTheme.palette
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    val cardShape = RoundedCornerShape(36.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .then(
                Modifier.border(
                    width = 1.dp,
                    color = palette.border,
                    shape = cardShape
                )
            )
            .then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
        onClick = {
            if (onClick != null && enabled) {
                scope.launch {
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                    delay(150)
                    isPressed = false
                }
            }
        },
        enabled = onClick != null && enabled,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surfaceContainerLow
            else
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.brand,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}

/**
 * Expressive switch with title and subtitle
 */
@Composable
fun ExpressiveSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val palette = MaterialTheme.palette

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled)
                    palette.muted
                else
                    palette.muted.copy(alpha = 0.5f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * Connected button group for selecting options
 */
@Composable
fun ExpressiveButtonGroup(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = MaterialTheme.palette

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = label == selectedOption

            val animatedRadius by animateDpAsState(
                targetValue = if (isSelected) 8.dp else 16.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 200f
                ),
                label = "radius_$index"
            )
            val animatedContainerColor by animateColorAsState(
                targetValue = if (isSelected)
                    palette.accent
                else
                    MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = tween(300),
                label = "containerColor_$index"
            )
            val animatedContentColor by animateColorAsState(
                targetValue = if (isSelected)
                    Color.White
                else
                    MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(300),
                label = "contentColor_$index"
            )

            Surface(
                onClick = { onOptionSelected(label) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(animatedRadius),
                color = animatedContainerColor,
                contentColor = animatedContentColor,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
