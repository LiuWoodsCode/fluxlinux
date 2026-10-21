package com.ivarna.fluxlinux.ui.install

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared install progress UI for onboarding + Distros [InstallConfigScreen].
 */
@Composable
fun InstallProgressPanel(
    percent: Int,
    phasePercent: Int?,
    phaseIndex: Int,
    phaseCount: Int,
    phaseLabel: String,
    detail: String,
    logText: String,
    failed: Boolean,
    errorMessage: String?,
    showLog: Boolean,
    onToggleLog: () -> Unit,
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    isDone: Boolean = false,
    modifier: Modifier = Modifier
) {
    val logScroll = rememberScrollState()
    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            logScroll.animateScrollTo(logScroll.maxValue)
        }
    }

    val colors = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        failed -> "Setup Failed"
                        isDone -> "Install Complete"
                        else -> "Setting Up Environment"
                    },
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        failed -> "An error occurred during execution"
                        isDone -> "All base packages and configs are installed"
                        else -> "Downloading rootfs & configuring packages"
                    })
            }

            // Status Indicator Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            failed -> colors.errorContainer
                            isDone -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            else -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            failed -> colors.errorContainer
                            isDone -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            else -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        failed -> Icons.Filled.ErrorOutline
                        isDone -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Refresh
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Installation progress
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                .background(colors.surface)
                .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, androidx.compose.material3.MaterialTheme.shapes.medium)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Overall progress",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${percent.coerceIn(0, 100)}%",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(10.dp))

                // Installation progress
                LinearProgressIndicator(
                    progress = { percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .semantics { contentDescription = "Overall installation progress" }
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(androidx.compose.material3.MaterialTheme.shapes.extraSmall))

                Spacer(Modifier.height(18.dp))
                Text(
                    text = if (phaseCount > 0) "Current step · ${phaseIndex + 1} of $phaseCount" else "Current step",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = phaseLabel.ifBlank { "Starting…" },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isDone -> "100%"
                            failed -> "Stopped"
                            phasePercent != null -> "${phasePercent.coerceIn(0, 100)}%"
                            else -> "Working…"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                val stepBarModifier = Modifier
                    .semantics { contentDescription = "Current step progress" }
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                if (phasePercent != null || failed || isDone) {
                    LinearProgressIndicator(
                        progress = { if (isDone) 1f else (phasePercent ?: 0).coerceIn(0, 100) / 100f },
                        color = if (failed) colors.error else colors.secondary,
                        modifier = stepBarModifier
                    )
                } else {
                    LinearProgressIndicator(
                        color = colors.secondary,
                        modifier = stepBarModifier
                    )
                }

                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }

                if (failed && errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Log Viewer Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Terminal Output")
            }

            TextButton(
                onClick = onToggleLog) {
                Text(
                    if (showLog) "Hide Console" else "Show Console")
            }
        }

        // Live Log Terminal Window
        AnimatedVisibility(
            visible = showLog,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, androidx.compose.material3.MaterialTheme.shapes.medium)
                    .padding(12.dp)
                    .verticalScroll(logScroll)
            ) {
                Text(
                    text = logText.ifBlank { "Initializing installation stream…" },
                    fontFamily = FontFamily.Monospace,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }

        if (!showLog) {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // Action Buttons
        when {
            failed -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry Setup")
                }

                if (onBack != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Back to Configuration")
                    }
                }
            }

            isDone && onDone != null -> {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)) {
                    Text("Continue")
                }
            }
        }
    }
}

/** Shared dark/light theme selectable card for install wizards. */
@Composable
fun InstallThemePickRow(
    id: String,
    title: String,
    desc: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val shape = androidx.compose.material3.MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    val borderColor = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                else colors.surface
            )
            .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title)
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}
