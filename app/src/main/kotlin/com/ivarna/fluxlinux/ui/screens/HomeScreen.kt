package com.ivarna.fluxlinux.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.ScriptManager
import com.ivarna.fluxlinux.core.data.TermuxIntentFactory

import com.ivarna.fluxlinux.core.desktop.DesktopLauncher
import com.ivarna.fluxlinux.core.desktop.DesktopSession
import com.ivarna.fluxlinux.core.desktop.DesktopSessionQuery
import com.ivarna.fluxlinux.core.root.RootShell
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.ui.components.ActiveDesktopCard
import com.ivarna.fluxlinux.ui.components.MethodTab
import com.ivarna.fluxlinux.ui.components.MethodTabs
import com.ivarna.fluxlinux.ui.components.isChrootCard
import com.ivarna.fluxlinux.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    permissionState: PermissionState,

    scriptRefreshTrigger: Int = 0,
    onStartService: (android.content.Intent) -> Unit,
    onStartActivity: (android.content.Intent) -> Unit,
    onNavigateToInstall: (com.ivarna.fluxlinux.core.data.Distro) -> Unit,
    onNavigateToSettings: (com.ivarna.fluxlinux.core.data.Distro) -> Unit,
    onOpenTerminal: (distroId: String, root: Boolean) -> Unit = { _, _ -> },
    /** Switch bottom-nav to Terminal after an in-app session is opened (e.g. Qwen). */
    onShowTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State for Launch Popup
    val distroToLaunch = remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
    // State for KDE GPU mode picker (sub-dialog)
    val showKdeGpuPicker = remember { mutableStateOf<com.ivarna.fluxlinux.core.data.Distro?>(null) }
    // Live desktop start/stop logs (termux-lib VIEW LOGS parity)
    val desktopUi by DesktopLauncher.uiState.collectAsState()
    var showDesktopLogs by remember { mutableStateOf(false) }
    // Only auto-open on a *new* tick (start/fail). Do NOT open when Home is
    // re-entered while tick is already > 0 from a prior start — that made the
    // Graphical Desktop Log dialog appear every time the user went Home.
    var lastSeenAutoShowTick by remember { mutableIntStateOf(-1) }
    LaunchedEffect(desktopUi.autoShowLogsTick) {
        val tick = desktopUi.autoShowLogsTick
        if (lastSeenAutoShowTick < 0) {
            // First observation after this Home composition: consume current tick.
            lastSeenAutoShowTick = tick
            return@LaunchedEffect
        }
        if (tick > lastSeenAutoShowTick) {
            lastSeenAutoShowTick = tick
            showDesktopLogs = true
        }
    }

    // Refresh key to trigger recomposition
    val refreshKey = remember { mutableStateOf(0) }

    // React to external refresh trigger (from MainActivity)
    LaunchedEffect(scriptRefreshTrigger) {
        if (scriptRefreshTrigger > 0) {
            refreshKey.value++
        }
    }
    // Recompose cards when desktop phase changes
    LaunchedEffect(desktopUi.phase, desktopUi.displayReady) {
        refreshKey.value++
    }

    val stateRefresh by StateManager.refreshTrigger.collectAsState()
    val session = remember(desktopUi, stateRefresh, refreshKey.value) {
        DesktopSessionQuery.current(context, desktopUi)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Trigger initial refresh on mount
        LaunchedEffect(Unit) {
            refreshKey.value++
        }

        // Installed Distros Detection — filesystem truth (plan P4-T13): a stale
        // "installed" pref without a rootfs on disk must show Install, not a broken shell.
        val installedDistros = remember(refreshKey.value) {
            DistroRepository.sortForDistroPage(
                DistroRepository.supportedDistros.filter {
                    com.ivarna.fluxlinux.core.terminal.TerminalLauncher.isDistroInstalledOnFs(context, it.id)
                }
            )
        }

        if (session != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ActiveDesktopCard(
                session = session,
                onOpen = { DesktopLauncher.reopenDisplay(context) },
                onStop = {
                    try {
                        DesktopLauncher.stop(context, session.distroId) {
                            refreshKey.value++
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Stop failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onLogs = { showDesktopLogs = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Installed Distros",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        val prootInstalled = installedDistros.filter { !it.isChrootCard() }
        val chrootInstalled = installedDistros.filter { it.isChrootCard() }
        var methodTab by remember { mutableStateOf(MethodTab.PROOT) }
        var rootAvailable by remember { mutableStateOf(false) }
        val visibleDistros = if (methodTab == MethodTab.CHROOT) {
            chrootInstalled
        } else {
            prootInstalled
        }

        fun probeRoot(force: Boolean = false) {
            RootShell.probeRootAvailable(forceClearCache = force) { ok ->
                rootAvailable = ok
            }
        }
        LaunchedEffect(Unit) { probeRoot() }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    probeRoot(force = true)
                    // Drop a "desktop running" card whose X server died with the app.
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopSessionQuery.reconcileStale(context)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        MethodTabs(
            selected = methodTab,
            onSelected = { tab ->
                methodTab = tab
                if (tab == MethodTab.CHROOT) probeRoot(force = true)
            },
            prootCount = prootInstalled.size,
            chrootCount = chrootInstalled.size
        )

        if (methodTab == MethodTab.CHROOT && !rootAvailable && chrootInstalled.isNotEmpty()) {
            Text(
                text = "Installed, but no root given to the app. Grant superuser to FluxLinux to start these guests.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (installedDistros.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No distros installed yet",
                    textAlign = TextAlign.Center,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Install a distribution from the Distros tab",
                    textAlign = TextAlign.Center)
            }
        } else if (visibleDistros.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (methodTab == MethodTab.CHROOT) {
                        "No chroot guests installed"
                    } else {
                        "No PRoot guests installed"
                    },
                    textAlign = TextAlign.Center)
            }
        } else {
            visibleDistros.forEach { distro ->
                val desktopForThis =
                    desktopUi.distroId == null || desktopUi.distroId == distro.id
                val isStarting =
                    desktopForThis && desktopUi.phase == DesktopLauncher.Phase.Starting
                val isRunning =
                    StateManager.isGuiRunning(context, distro.id) ||
                        (desktopForThis && desktopUi.phase == DesktopLauncher.Phase.Running)
                val logsOk =
                    desktopForThis && (desktopUi.logsAvailable || desktopUi.logText.isNotBlank())
                val needsRoot = distro.isChrootCard()
                val canStart = !needsRoot || rootAvailable
                com.ivarna.fluxlinux.ui.components.DistroCard(
                    distro = distro,
                    isInstalled = true,
                    isGuiRunning = isRunning,
                    isGuiStarting = isStarting,
                    logsAvailable = logsOk ||
                        com.ivarna.fluxlinux.core.desktop.GuiDesktopLog.hasContent(context),
                    startEnabled = canStart,
                    statusMessage = if (needsRoot && !rootAvailable) {
                        "Installed · no root given to app"
                    } else {
                        null
                    },
                    statusIsError = needsRoot && !rootAvailable,
                    onInstall = { onNavigateToInstall(distro) },
                    onUninstall = { /* Handled in Settings */ },
                    onNavigateToSettings = { onNavigateToSettings(distro) },
                    onNavigateToStart = {
                        if (!canStart) {
                            android.widget.Toast.makeText(
                                context,
                                "Grant superuser to FluxLinux first",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else if (session != null && session.distroId != distro.id) {
                            android.widget.Toast.makeText(
                                context,
                                "Stop ${session.distroName} ${session.type} first",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            distroToLaunch.value = distro
                        }
                    },
                    onOpenDisplay = {
                        DesktopLauncher.reopenDisplay(context)
                    },
                    onViewLogs = { showDesktopLogs = true },
                    onStop = {
                        try {
                            DesktopLauncher.stop(context, distro.id) {
                                refreshKey.value++
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Stop failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Launch mode dialog (shell / root / desktop)
    if (distroToLaunch.value != null) {
        val distro = distroToLaunch.value!!
        val accent = MaterialTheme.colorScheme.secondary
        val titleColor = MaterialTheme.colorScheme.onBackground
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val llmInstalled = StateManager.isComponentInstalled(context, distro.id, "vulkan_llamacpp")
        val modelInstalled = StateManager.isComponentInstalled(context, distro.id, "qwen25_model")
        val kdeInstalled = StateManager.isComponentInstalled(context, distro.id, "kde_plasma")
        val isGuiRunning = StateManager.isGuiRunning(context, distro.id)
        val runningType = StateManager.getGuiRunningType(context, distro.id)

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { distroToLaunch.value = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = androidx.compose.material3.MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(androidx.compose.material3.MaterialTheme.shapes.large)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer)
                            .border(
                                BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer),
                                androidx.compose.material3.MaterialTheme.shapes.large
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (distro.iconRes != null) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = distro.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Start ${distro.name}",
                        style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Choose launch mode",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp))

                    Spacer(Modifier.height(22.dp))

                    // Shell — in-app terminal (no external Termux)
                    LaunchModeAction(
                        icon = Icons.Filled.Terminal,
                        label = "Open Shell",
                        subtitle = "User session",
                        onClick = {
                            onOpenTerminal(distro.id, false)
                            distroToLaunch.value = null
                        })

                    Spacer(Modifier.height(10.dp))

                    LaunchModeAction(
                        icon = Icons.Filled.AdminPanelSettings,
                        label = "Open Root Shell",
                        subtitle = "Privileged session",
                        onClick = {
                            onOpenTerminal(distro.id, true)
                            distroToLaunch.value = null
                        })

                    if (llmInstalled && modelInstalled) {
                        Spacer(Modifier.height(10.dp))
                        LaunchModeAction(
                            icon = Icons.Filled.Psychology,
                            label = "Run Qwen2.5-1.5B",
                            subtitle = "In-app model session",
                            onClick = {
                                val scriptManager = ScriptManager(context)
                                val scriptContent = scriptManager.getScriptContent(
                                    "debian/common/addon/launch_qwen25.sh"
                                )
                                val scriptB64 = android.util.Base64.encodeToString(
                                    scriptContent.toByteArray(),
                                    android.util.Base64.NO_WRAP
                                )
                                val payload = "echo '$scriptB64' | base64 -d | bash"
                                com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager.openSessionAfterHost(
                                    context,
                                    type = "shell",
                                    title = "Qwen2.5-1.5B",
                                    shellCmd = payload,
                                    method = com.ivarna.fluxlinux.core.data.terminalComponentFor(distro.id).method,
                                    onResult = { result ->
                                        if (result == com.ivarna.fluxlinux.core.terminal.FluxTerminalSessionManager.SessionOpenResult.OPENED) {
                                            onShowTerminal()
                                        }
                                    }
                                )
                                distroToLaunch.value = null
                            })
                    }

                    Spacer(Modifier.height(10.dp))

                    val isCurrentSessionXfce = session != null && session.distroId == distro.id && session.type == DesktopSession.Type.XFCE4
                    LaunchModeAction(
                        icon = Icons.Filled.DesktopWindows,
                        label = if (isCurrentSessionXfce) "Open XFCE4" else "Launch XFCE4",
                        subtitle = if (isCurrentSessionXfce) "Running on :0" else "Graphical desktop",
                        onClick = {
                            if (session != null && session.distroId != distro.id) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Stop ${session.distroName} ${session.type} first",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else if (isCurrentSessionXfce) {
                                DesktopLauncher.reopenDisplay(context)
                                distroToLaunch.value = null
                            } else {
                                try {
                                    DesktopLauncher.start(context, distro.id) { ok ->
                                        refreshKey.value++
                                        if (!ok) showDesktopLogs = true
                                    }
                                    showDesktopLogs = true
                                    distroToLaunch.value = null
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Launch failed: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        })

                    Spacer(Modifier.height(10.dp))

                    val isCurrentSessionKde = session != null && session.distroId == distro.id && session.type == DesktopSession.Type.KDE
                    LaunchModeAction(
                        icon = Icons.Filled.Widgets,
                        label = if (isCurrentSessionKde) "Open KDE Plasma" else if (kdeInstalled) "Launch KDE Plasma" else "Launch KDE",
                        subtitle = if (isCurrentSessionKde) "Running on :0" else if (kdeInstalled) "GPU mode picker" else "Not installed",
                        enabled = true,
                        onClick = {
                            if (session != null && session.distroId != distro.id) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Stop ${session.distroName} ${session.type} first",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else if (isCurrentSessionKde) {
                                DesktopLauncher.reopenDisplay(context)
                                distroToLaunch.value = null
                            } else if (session != null || DesktopLauncher.isSessionActive()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Stop ${session?.distroName ?: "active desktop"} ${session?.type ?: ""} first",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else if (kdeInstalled) {
                                showKdeGpuPicker.value = distro
                            } else if (!kdeInstalled) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Install KDE Plasma Desktop first from Settings.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        })

                    if (isGuiRunning) {
                        Spacer(Modifier.height(10.dp))
                        LaunchModeAction(
                            icon = Icons.Filled.Stop,
                            label = if (runningType == "kde") "Stop KDE Plasma" else "Stop XFCE4",
                            subtitle = "End desktop session",
                            onClick = {
                                DesktopLauncher.stop(context, distro.id)
                                distroToLaunch.value = null
                            })
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { distroToLaunch.value = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // ─── Graphical Desktop live logs (termux-lib VIEW LOGS) ─────────────────
    if (showDesktopLogs) {
        DesktopLogsDialog(
            logText = if (desktopUi.logText.isNotBlank()) {
                desktopUi.logText
            } else {
                DesktopLauncher.readLog(context)
            },
            phase = desktopUi.phase,
            onDismiss = { showDesktopLogs = false },
            onOpenX11 = {
                DesktopLauncher.reopenDisplay(context)
            },
            onCopy = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = if (desktopUi.logText.isNotBlank()) {
                    desktopUi.logText
                } else {
                    DesktopLauncher.readLog(context)
                }
                cm.setPrimaryClip(ClipData.newPlainText("desktop_log", text))
                android.widget.Toast.makeText(context, "Log copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ─── KDE GPU Mode Picker Sub-Dialog ───────────────────────────────────────
    if (showKdeGpuPicker.value != null) {
        val distro = showKdeGpuPicker.value!!
        // Same built-in X11 path as XFCE; gpu null = installed driver, "software" = llvmpipe.
        val launchKde = { gpu: String? ->
            val curr = DesktopSessionQuery.current(context, DesktopLauncher.uiState.value)
            if (curr != null || DesktopLauncher.isSessionActive()) {
                if (curr?.distroId == distro.id && curr.type == DesktopSession.Type.KDE) {
                    DesktopLauncher.reopenDisplay(context)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Stop ${curr?.distroName ?: "active desktop"} first",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                DesktopLauncher.start(context, distro.id, "kde", gpu) { ok ->
                    refreshKey.value++
                    if (!ok) showDesktopLogs = true
                }
            }
            showKdeGpuPicker.value = null
            distroToLaunch.value = null
        }
        KdeGpuPickerDialog(
            distro = distro,
            onDismiss = { showKdeGpuPicker.value = null },
            onSelectHardware = { launchKde(null) },
            onSelectSoftware = { launchKde("software") }
        )
    }
}

// ─── Desktop start/stop log dialog ─────────────────────────────────────────────
@Composable
private fun DesktopLogsDialog(
    logText: String,
    phase: DesktopLauncher.Phase,
    onDismiss: () -> Unit,
    onOpenX11: () -> Unit,
    onCopy: () -> Unit
) {
    val scroll = rememberScrollState()
    LaunchedEffect(logText) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    val titleColor = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val logFg = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val logBg = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val phaseLabel = when (phase) {
        DesktopLauncher.Phase.Starting -> "Starting…"
        DesktopLauncher.Phase.Running -> "Running"
        DesktopLauncher.Phase.Idle -> "Idle"
    }
    val phaseIcon = when (phase) {
        DesktopLauncher.Phase.Starting -> Icons.Filled.Sync
        DesktopLauncher.Phase.Running -> Icons.Filled.PlayArrow
        DesktopLauncher.Phase.Idle -> Icons.Filled.Schedule
    }
    val phaseTint = when (phase) {
        DesktopLauncher.Phase.Starting -> accent
        DesktopLauncher.Phase.Running -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        DesktopLauncher.Phase.Idle -> muted
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.75f),
            shape = androidx.compose.material3.MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DesktopWindows,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Graphical Desktop Log",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = phaseIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    phaseLabel,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                        .background(logBg)
                        .border(
                            BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                            androidx.compose.material3.MaterialTheme.shapes.medium
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = logText.ifBlank { "Waiting for desktop output…" },
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = onOpenX11,
                        enabled = phase != DesktopLauncher.Phase.Idle,
                        modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open X11")
                    }
                }
            }
        }
    }
}

// ─── Launch mode action row ────────────────────────────────────────────────────
@Composable
private fun LaunchModeAction(
    icon: ImageVector,
    label: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                    ,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    label)
                Text(
                    subtitle,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ─── KDE GPU Picker Dialog ─────────────────────────────────────────────────────
@Composable
private fun KdeGpuPickerDialog(
    distro: com.ivarna.fluxlinux.core.data.Distro,
    onDismiss: () -> Unit,
    onSelectHardware: () -> Unit,
    onSelectSoftware: () -> Unit
) {
    val ctx = LocalContext.current
    val driverBadge = remember(distro.id) {
        com.ivarna.fluxlinux.core.gpu.GpuDriverInstaller.badge(ctx, distro.id)
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(androidx.compose.material3.MaterialTheme.shapes.large)
                .background(
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                    androidx.compose.material3.MaterialTheme.shapes.large
                )
        ) {
            // Top glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                    )
            )

            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Widgets,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "KDE Plasma",
                        style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose GPU Renderer",
                    style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(28.dp))

                // GPU Option Cards — Vertical List of Horizontal Cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Turnip Card ─────────────────────────
                    GpuOptionCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Bolt,
                        title = "Hardware",
                        subtitle = "Installed GPU driver",
                        description = "Turnip / PanVK / VirGL, whichever is installed. Best performance.",
                        badgeText = driverBadge,
                        onClick = onSelectHardware)

                    // ── Software Card ───────────────────────
                    GpuOptionCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Memory,
                        title = "Software",
                        subtitle = "LLVMpipe",
                        description = "CPU-only. No GPU required. Works on all devices.",
                        badgeText = "Safe Fallback",
                        onClick = onSelectSoftware)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Info note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                        .border(BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant), androidx.compose.material3.MaterialTheme.shapes.medium)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Hardware needs a GPU driver installed. Software works on any device (slower).",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

// ─── Reusable GPU Option Card ──────────────────────────────────────────────────
@Composable
private fun GpuOptionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    badgeText: String?,
    onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (badgeText != null) {
                    Text(badgeText, style = MaterialTheme.typography.labelSmall)
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
