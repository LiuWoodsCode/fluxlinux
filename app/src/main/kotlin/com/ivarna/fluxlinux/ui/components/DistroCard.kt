package com.ivarna.fluxlinux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.Distro


fun Distro.isChrootCard(): Boolean =
    id.contains("chroot", ignoreCase = true) || (chrootSupported && !prootSupported)

@Composable
fun DistroCard(
    distro: Distro,
    isInstalled: Boolean = false,
    isGuiRunning: Boolean = false,
    /** True while start_gui is streaming (termux-lib Starting phase). */
    isGuiStarting: Boolean = false,
    isGlobalInstalling: Boolean = false,
    isCurrentlyInstalling: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStart: () -> Unit,
    onStop: () -> Unit = {},
    onOpenDisplay: () -> Unit = {},
    onViewLogs: () -> Unit = {},
    logsAvailable: Boolean = false,
    startEnabled: Boolean = true,
    statusMessage: String? = null,
    statusIsError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isChroot = distro.isChrootCard()
    val methodColor = MaterialTheme.colorScheme.secondary
    val colors = MaterialTheme.colorScheme
    val showLogs = logsAvailable || isGuiStarting || isGuiRunning

    androidx.compose.material3.OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                    if (distro.iconRes != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = distro.iconRes),
                            contentDescription = "${distro.name} logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.material3.MaterialTheme.shapes.small)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.material3.MaterialTheme.shapes.small)
                                .background(
                                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = distro.name.removeSuffix(" (Rooted)"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            Spacer(modifier = Modifier.width(6.dp))
                            MethodChip(
                                label = if (isChroot) "CHROOT" else "PROOT")
                            if (isInstalled && isChroot && !startEnabled) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MethodChip(
                                    label = "NO ROOT")
                            }
                            if (isInstalled && (isGuiRunning || isGuiStarting)) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MethodChip(
                                    label = if (isGuiStarting) "STARTING" else "RUNNING")
                            }
                            if (distro.comingSoon) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MethodChip(
                                    label = "SOON")
                            }
                        }
                        Text(
                            text = statusMessage ?: distro.id,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }

                    if (isInstalled) {
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (!isInstalled && distro.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = distro.description,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isInstalled) {
                    CompactAction(
                        label = when {
                            isCurrentlyInstalling -> "Progress"
                            isGlobalInstalling -> "Busy"
                            distro.comingSoon -> "Soon"
                            else -> "Install"
                        },
                        onClick = if (distro.comingSoon || (isGlobalInstalling && !isCurrentlyInstalling)) {
                            {}
                        } else {
                            onInstall
                        },
                        enabled = !distro.comingSoon && (!isGlobalInstalling || isCurrentlyInstalling),
                        modifier = Modifier.fillMaxWidth())
                } else if (isGuiRunning || isGuiStarting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactAction(
                            label = "Open",
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            onClick = onOpenDisplay,
                            enabled = isGuiRunning || isGuiStarting,
                            modifier = Modifier.weight(1f))
                        CompactAction(
                            label = "Stop",
                            icon = Icons.Default.Stop,
                            onClick = onStop,
                            modifier = Modifier.weight(0.85f))
                        if (showLogs) {
                            CompactAction(
                                label = "Logs",
                                icon = Icons.AutoMirrored.Filled.Article,
                                onClick = onViewLogs,
                                modifier = Modifier.weight(0.85f))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactAction(
                            label = if (!startEnabled && isChroot) "Need root" else "Start",
                            icon = Icons.Default.PlayArrow,
                            onClick = onNavigateToStart,
                            enabled = startEnabled,
                            modifier = Modifier.weight(1.2f))
                        if (showLogs) {
                            CompactAction(
                                label = "Logs",
                                icon = Icons.AutoMirrored.Filled.Article,
                                onClick = onViewLogs,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

enum class MethodTab { PROOT, CHROOT }

@Composable
fun MethodTabs(
    selected: MethodTab,
    onSelected: (MethodTab) -> Unit,
    modifier: Modifier = Modifier,
    prootCount: Int? = null,
    chrootCount: Int? = null,
    prootLabel: String = "PRoot",
    chrootLabel: String = "Chroot",
    chrootEnabled: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) {
    val prootText = if (prootCount != null) "$prootLabel  $prootCount" else prootLabel
    val chrootText = if (chrootCount != null) "$chrootLabel  $chrootCount" else chrootLabel
    androidx.compose.material3.TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
    ) {
        androidx.compose.material3.Tab(
            selected = selected == MethodTab.PROOT,
            onClick = { onSelected(MethodTab.PROOT) },
            text = { Text(prootText) }
        )
        androidx.compose.material3.Tab(
            selected = selected == MethodTab.CHROOT,
            onClick = { onSelected(MethodTab.CHROOT) },
            enabled = chrootEnabled,
            text = { Text(chrootText) }
        )
    }
}

@Composable
fun MethodChip(
    label: String,
    modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
internal fun CompactAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        androidx.compose.material3.OutlinedCard(onClick = onClick, modifier = modifier) {
            content()
        }
    } else {
        androidx.compose.material3.OutlinedCard(modifier = modifier) {
            content()
        }
    }
}
