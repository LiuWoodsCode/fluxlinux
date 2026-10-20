package com.ivarna.fluxlinux.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.install.DistroInstallProfile
import com.ivarna.fluxlinux.core.install.OnboardingInstallRunner
import com.ivarna.fluxlinux.ui.components.AppScaffold
import com.ivarna.fluxlinux.ui.install.InstallProgressPanel
import com.ivarna.fluxlinux.ui.install.GuestUsernameField
import com.ivarna.fluxlinux.core.utils.GuestUser

/**
 * Thin install wizard: base XFCE, wallpaper, and hardware support.
 * Uses [OnboardingInstallRunner] for both proot and chroot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallConfigScreen(
    distro: Distro,
    onBack: () -> Unit,
    onInstallComplete: () -> Unit,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    var username by remember(distro.id) { mutableStateOf("") }
    var phase by remember { mutableStateOf(InstallPhase.OPTIONS) }
    var percent by remember { mutableIntStateOf(0) }
    var phaseLabel by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(true) }
    val runner = remember { OnboardingInstallRunner(appCtx) }

    fun startInstall() {
        // Generation cancel inside runner; single instance avoids parallel jobs
        failed = false
        errorMessage = null
        percent = 0
        phaseLabel = "Starting…"
        detail = ""
        logText = ""
        phase = InstallPhase.RUNNING
        runner.start(distro.id, username) { progress ->
            percent = progress.overallPercent
            phaseLabel = progress.phaseLabel
            detail = progress.detail
            progress.logLine?.let { line ->
                logText = (logText + line + "\n").takeLast(12_000)
            }
            if (progress.failed) {
                failed = true
                errorMessage = progress.errorMessage ?: progress.detail
            }
            if (progress.finished && !progress.failed) {
                phase = InstallPhase.DONE
                Toast.makeText(context, "Base desktop installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { runner.cancel() }
    }

    AppScaffold(

        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (phase) {
                            InstallPhase.OPTIONS -> "Install ${distro.name}"
                            InstallPhase.RUNNING -> if (failed) "Install failed" else "Installing…"
                            InstallPhase.DONE -> "Install complete"
                        },
                        style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (phase == InstallPhase.RUNNING && !failed) {
                                runner.cancel()
                            }
                            onBack()
                        }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back")
                    }
                },
                modifier = Modifier)
        },
        bottomBar = {
            if (phase == InstallPhase.OPTIONS) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { startInstall() },
                        enabled = GuestUser.validationError(username) == null,
                        modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        val cta = when {
                            distro.id.contains("chroot") -> "Install base desktop (Root)"
                            else -> "Install base desktop"
                        }
                        Text(cta)
                    }
                }
            }
        }
    ) {
        when (phase) {
            InstallPhase.OPTIONS -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Base desktop only",
                    style = MaterialTheme.typography.titleMedium)
                val profile = DistroInstallProfile.forId(distro.id)
                val rootfsLine = when {
                    distro.id.startsWith("alpine") ->
                        "Installs Alpine 3.24 minirootfs and XFCE4 (apk). "
                    distro.id.startsWith("fedora") ->
                        "Installs Fedora 44 rootfs, XFCE4 (dnf), and Mesa. "
                    distro.id.startsWith("void") ->
                        "Installs Void Linux rootfs, XFCE4 (xbps), and Mesa. "
                    distro.id.startsWith("opensuse") ->
                        "Installs openSUSE Tumbleweed rootfs, XFCE4 (zypper), and Mesa. "
                    else ->
                        "Installs ${profile?.displayName ?: distro.name} rootfs and XFCE4. "
                }
                Text(
                    rootfsLine +
                        "Start with a base desktop and the Kasane Teto wallpaper. " +
                        "Install apps and customize the desktop yourself, or add optional components from Distro Settings.",
                    style = MaterialTheme.typography.bodyMedium)
                GuestUsernameField(username, onUsernameChange = { username = it })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, androidx.compose.material3.MaterialTheme.shapes.medium)
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            "Includes")
                        Spacer(Modifier.height(6.dp))
                        val bulletColor = com.ivarna.fluxlinux.ui.theme.fluxMutedText()
                        Text(
                            when {
                                distro.id.startsWith("alpine") ->
                                    "• Alpine 3.24 minirootfs (~4 MB + apk packages)"
                                distro.id.startsWith("fedora") ->
                                    "• Fedora 44 container rootfs"
                                distro.id.startsWith("void") ->
                                    "• Void Linux (glibc aarch64) rootfs"
                                distro.id.startsWith("opensuse") ->
                                    "• openSUSE Tumbleweed rootfs"
                                else ->
                                    "• ${profile?.displayName ?: distro.name} rootfs"
                            })
                        Text("• XFCE4 desktop")
                        Text("• GPU acceleration, X11 display, and audio support")
                        Text("• Kasane Teto wallpaper (fill)")
                        if (distro.id.contains("chroot")) {
                            Text("• Requires root (KernelSU / Magisk)")
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
            InstallPhase.RUNNING, InstallPhase.DONE -> InstallProgressPanel(
                percent = percent,
                phaseLabel = phaseLabel,
                detail = detail,
                logText = logText,
                failed = failed,
                errorMessage = errorMessage,
                showLog = showLog,
                onToggleLog = { showLog = !showLog },
                onRetry = { startInstall() },
                onDone = onInstallComplete,
                isDone = phase == InstallPhase.DONE
            )
        }
    }
}

private enum class InstallPhase { OPTIONS, RUNNING, DONE }
