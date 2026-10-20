package com.ivarna.fluxlinux.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.DistroComponent
import com.ivarna.fluxlinux.core.gpu.DriverRelease
import com.ivarna.fluxlinux.core.gpu.GpuDriverInstaller
import com.ivarna.fluxlinux.core.gpu.PanvkInstaller
import com.ivarna.fluxlinux.core.terminal.GpuAccelDetector
import com.ivarna.fluxlinux.core.utils.InstallationQueueManager
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.ui.components.AppScaffold
import com.ivarna.fluxlinux.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistroSettingsScreen(
    distro: Distro,
    onBack: () -> Unit,
    onInstallComponent: (DistroComponent, Map<String, String>) -> Unit,
    onUninstallComponent: (DistroComponent) -> Unit = {},
    onUninstallDistro: () -> Unit,
    onReinstallDistro: () -> Unit,
    onNavigateToStart: (() -> Unit)? = null,
    onStartActivity: (android.content.Intent) -> Unit,
    onLaunchXfce: (() -> Unit)? = null,
    onStopXfce: (() -> Unit)? = null,
    onLaunchKde: (() -> Unit)? = null,
    onStopKde: (() -> Unit)? = null,
    onLaunchCli: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val installState by InstallationQueueManager.installState.collectAsState()
    var showUninstallDialog by remember { mutableStateOf(false) }
    var pendingUninstallComponent by remember { mutableStateOf<DistroComponent?>(null) }

    // Config Dialog States
    var showThemeDialog by remember { mutableStateOf(false) }
    var showGpuDialog by remember { mutableStateOf(false) }
    var activeComponent by remember { mutableStateOf<DistroComponent?>(null) }

    // Selections
    var selectedTheme by remember { mutableStateOf("dark") }
    var selectedGpu by remember { mutableStateOf("auto") }

    // Auto-clear stale "Busy..." state when screen opens with no active install
    LaunchedEffect(Unit) {
        if (!InstallationQueueManager.hasPending() && InstallationQueueManager.currentTask == null) {
            InstallationQueueManager.clear()
        }
    }

    AppScaffold(

        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Manage ${distro.name}",
                        style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier)
        },
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
            ) {

                // Distro details
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.material3.MaterialTheme.shapes.large)
                            .background(
                                androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                androidx.compose.material3.MaterialTheme.shapes.large
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (distro.iconRes != null) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = distro.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp))
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(32.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    distro.name,
                                    style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Installed & Ready",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }


                // ─── Components Section Title ──────────────────────────────
                item {
                    Text(
                        text = "Features & Components",
                        style = MaterialTheme.typography.titleMedium)
                }



                items(distro.components) { component ->
                    // Include both component.id and distro.id in the remember key so the
                    // cached isInstalled value resets when the user navigates to a different
                    // distro. Without distro.id, switching from termux to debian would re-use
                    // termux's cached value for the same component (e.g. kde_plasma) and
                    // make the debian screen falsely show the component as installed.
                    val refreshKey by StateManager.refreshTrigger.collectAsState()
                    val isInstalled = remember(component.id, distro.id, refreshKey) {
                        StateManager.isComponentInstalled(context, distro.id, component.id)
                    }
                    val details = componentDetailsMap[component.id]

                    ComponentManagementCard(
                        component = component,
                        isInstalled = isInstalled,
                        isGlobalInstalling = installState.isInstalling,
                        details = details,
                        onAction = {
                            when {
                                component.id == "customization" -> {
                                    // XFCE4 customization — show theme + GPU dialog
                                    activeComponent = component
                                    showThemeDialog = true
                                }
                                component.id == "hw_accel" -> {
                                    // Hardware acceleration — show GPU dialog
                                    activeComponent = component
                                    showGpuDialog = true
                                }
                                component.id == "kde_customization" -> {
                                    // KDE customization — show theme dialog (kde script handles FLUX_THEME)
                                    activeComponent = component
                                    showThemeDialog = true
                                }
                                else -> {
                                    // Generic install (kde_plasma, app_dev, web_dev, etc.)
                                    onInstallComponent(component, emptyMap())
                                }
                            }
                        },
                        onUninstall = {
                            // Stage the uninstall — confirmation dialog follows
                            pendingUninstallComponent = component
                        }
                    )
                }

                // Danger Zone
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Danger Zone",
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Reinstall Button (Redo Base Install)
                        Button(
                            onClick = onReinstallDistro,
                            modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Re-run Installation Script")
                            }
                        }

                        // Uninstall Button
                        Button(
                            onClick = { showUninstallDialog = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Uninstall Distribution")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(50.dp))
                }
            }
        }
    )

    // --- DIALOGS ---

    // Component Uninstall Dialog (per-component, not whole-distro)
    if (pendingUninstallComponent != null) {
        val comp = pendingUninstallComponent!!
        SettingsDialog(onDismiss = { pendingUninstallComponent = null }) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Uninstall ${comp.name}?",
                    style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "This will remove ${comp.name} (${comp.sizeEstimate}) and all its data. " +
                        "You can re-install it later from this screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { pendingUninstallComponent = null },
                        modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val toUninstall = pendingUninstallComponent
                            pendingUninstallComponent = null
                            if (toUninstall != null) onUninstallComponent(toUninstall)
                        },
                        modifier = Modifier.weight(1f)) { Text("Uninstall") }
                }
            }
        }
    }

    // Uninstall Dialog
    if (showUninstallDialog) {
        SettingsDialog(onDismiss = { showUninstallDialog = false }) {
             Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Uninstall ${distro.name}?",
                    style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "This will remove all data and files for ${distro.name}. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showUninstallDialog = false },
                        modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Embedded uninstall only (plan §4.5): chroot → SSOT
                            // uninstall script via Root Shell; proot → proot-distro remove.
                            // No Termux intent, no clipboard.
                            onUninstallDistro()
                            showUninstallDialog = false
                        },
                        modifier = Modifier.weight(1f)) {
                        Text("Uninstall")
                    }
                }
            }
        }
    }

    // Theme Configuration Dialog
    if (showThemeDialog && activeComponent != null) {
        SettingsDialog(onDismiss = { showThemeDialog = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Customize Desktop", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                SettingsThemeOption(name = "Dark Mode (Default)", desc = "Sleek and professional.", id = "dark", selected = selectedTheme == "dark", onSelect = { selectedTheme = "dark" })
                SettingsThemeOption(name = "Light Mode", desc = "Clean and bright.", id = "light", selected = selectedTheme == "light", onSelect = { selectedTheme = "light" })

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showThemeDialog = false
                        onInstallComponent(activeComponent!!, mapOf("FLUX_THEME" to selectedTheme))
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Apply Theme")
                }
            }
        }
    }

    // GPU Configuration Dialog
    if (showGpuDialog && activeComponent != null) {
        SettingsDialog(onDismiss = { showGpuDialog = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Hardware Acceleration", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                SettingsThemeOption(name = "Auto Detect (Recommended)", desc = "Detects Snapdragon (Turnip) or uses VirGL.", id = "auto", selected = selectedGpu == "auto", onSelect = { selectedGpu = "auto" })
                SettingsThemeOption(name = "VirGL (Universal)", desc = "Compatible with most devices.", id = "virgl", selected = selectedGpu == "virgl", onSelect = { selectedGpu = "virgl" })
                SettingsThemeOption(name = "Turnip/Zink (Snapdragon)", desc = "High performance for Adreno.", id = "turnip", selected = selectedGpu == "turnip", onSelect = { selectedGpu = "turnip" })
                SettingsThemeOption(name = "Force Re-Detect", desc = "Ask interactively during install.", id = "ask", selected = selectedGpu == "ask", onSelect = { selectedGpu = "ask" })

                val adreno = remember { GpuAccelDetector.detect().mode == GpuAccelDetector.MODE_TURNIP }
                val waiting by DriverRelease.waiting.collectAsState()
                val driverPending = GpuDriverInstaller.pendingDistro(context) != null
                val elig = remember { PanvkInstaller.current(GpuAccelDetector.detect().signals) }
                val panvk = elig is PanvkInstaller.Elig.Ok && PanvkInstaller.glibc(distro.id)
                if (adreno || panvk) {
                    val name = if (adreno) "Turnip" else "PanVK"
                    val inst = if (adreno) GpuDriverInstaller.installedVersion(context, distro.id)
                        else PanvkInstaller.installedVersion(context, distro.id)
                    val latest = if (adreno) GpuDriverInstaller.latestVersion(context)
                        else PanvkInstaller.latestVersion(context)
                    val upd = if (adreno) GpuDriverInstaller.updateAvailable(context, distro.id)
                        else PanvkInstaller.updateAvailable(context, distro.id)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "GPU driver ($name): installed ${inst ?: "none"} · latest ${latest ?: "unknown"}" +
                            when {
                                waiting -> "\nWaiting for internet…"
                                driverPending -> "\nNo internet — driver not installed. Software rendering until you retry."
                                upd && inst != null -> "\nUpdate available."
                                else -> ""
                            },
                        style = MaterialTheme.typography.bodySmall)
                } else if (elig is PanvkInstaller.Elig.Skip) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("GPU driver (PanVK): ${elig.msg}", style = MaterialTheme.typography.bodySmall)
                } else if (elig is PanvkInstaller.Elig.Ok) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("GPU driver (PanVK): needs a glibc distro (${distro.id} is musl) — software rendering stays", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showGpuDialog = false
                        val det = GpuAccelDetector.detect()
                        GpuAccelDetector.persist(context, det)
                        val mode = GpuAccelDetector.resolveFluxGpu(selectedGpu)
                        onInstallComponent(
                            activeComponent!!,
                            mapOf(
                                "FLUX_GPU" to mode,
                                "FLUX_GPU_VENDOR" to det.vendorHint
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(if ((adreno || panvk) && driverPending) "Retry" else "Apply Configuration")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            content()
        }
    }
}

@Composable
fun SettingsThemeOption(name: String, desc: String, id: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp)
            .background(if(selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, androidx.compose.material3.MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(name)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ComponentManagementCard(
    component: DistroComponent,
    isInstalled: Boolean,
    isGlobalInstalling: Boolean = false,
    details: ComponentDetail?,
    onAction: () -> Unit,
    onUninstall: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.material3.MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.material3.MaterialTheme.shapes.large)
            .clickable(enabled = !component.comingSoon) { expanded = !expanded }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Icon + Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (details != null) {
                            Icon(
                                imageVector = details.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Text(
                            text = component.name,
                            style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = component.description,
                        style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (component.comingSoon) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.tertiaryContainer, androidx.compose.material3.MaterialTheme.shapes.extraSmall)
                                    .border(1.dp, MaterialTheme.colorScheme.tertiaryContainer, androidx.compose.material3.MaterialTheme.shapes.extraSmall)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Coming Soon",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                text = "Size: ${component.sizeEstimate}",
                                style = MaterialTheme.typography.labelSmall)
                            if (isInstalled) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.secondaryContainer, androidx.compose.material3.MaterialTheme.shapes.extraSmall)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Installed",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Action area (right aligned): Re-run/Install + optional Uninstall,
                // with the expand chevron to the right of the buttons.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!component.comingSoon) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onAction,
                                enabled = !isGlobalInstalling,
                                modifier = Modifier.height(40.dp)) {
                                if (isGlobalInstalling) {
                                    Text("Busy...",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                } else if (isInstalled) {
                                    Text("Re-run",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("Install",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            }

                            // Secondary Uninstall button — only for installed, non-mandatory components.
                            // Mandatory components (e.g., hw_accel) cannot be removed.
                            // xfce4_desktop is also hidden: its script (setup_debian_family.sh /
                            // setup_xfce4_termux.sh / setup_arch_family.sh) is the base install
                            // and has no uninstall branch — tapping Uninstall would re-install XFCE.
                            // Termux xfce customisation (id="customization", scriptName contains
                            // "termux") and Termux kde customisation (id="kde_customization",
                            // scriptName contains "termux") are hidden for the same reason — see
                            // T8 + T9 v4. The scripts have no uninstall branch; tapping would
                            // re-run the install. setup_kde_termux.sh DOES have a real uninstall
                            // branch, so the kde_plasma button remains visible.
                            if (isInstalled && !component.isMandatory && !component.comingSoon && component.id != "xfce4_desktop" && !(component.id == "customization" && component.scriptName.contains("termux")) && !(component.id == "kde_customization" && component.scriptName.contains("termux")) && onUninstall != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(
                                    onClick = onUninstall,
                                    enabled = !isGlobalInstalling,
                                    modifier = Modifier.height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Uninstall ${component.name}",
                                        modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Uninstall",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            }

                            // Expand chevron — sits at the right end of the action row.
                            if (details != null && !component.comingSoon) {
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (expanded) "Collapse" else "Expand")
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (details != null) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Included Packages",
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        details.packages.forEach { (pkg, size) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• $pkg",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = size,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
