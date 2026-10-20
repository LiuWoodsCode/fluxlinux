package com.ivarna.fluxlinux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.core.chroot.GuestStorageCatalog
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.proot.ProotInfoStore
import com.ivarna.fluxlinux.core.proot.ProotSettingsModel
import com.ivarna.fluxlinux.ui.components.SettingsCard
import com.ivarna.fluxlinux.ui.screens.storage.MetaRow
import com.ivarna.fluxlinux.ui.screens.storage.StatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Parameterized detail screen for PRoot container storage (individual or universal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProotStorageDetailScreen(
    distroId: String,
    onBack: () -> Unit,
    onNavigateToInstall: (() -> Unit)? = null
) {
    val isUniversal = distroId == GuestStorageCatalog.ALL_PROOT_ID
    val distro = if (!isUniversal) {
        DistroRepository.supportedDistros.firstOrNull { it.id == distroId }
    } else null

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val containerPath = if (!isUniversal) {
        GuestStorageCatalog.prootContainerPath(context, distroId)
    } else {
        "${context.filesDir.absolutePath}/usr/var/lib/proot-distro/containers"
    }

    if (!isUniversal && (distro == null || containerPath == null)) {
        LaunchedEffect(distroId) {
            onBack()
        }
        return
    }

    val installableIds = remember { GuestStorageCatalog.installableProots().map { it.id } }

    val initialSizeUi = remember(distroId) {
        if (isUniversal) {
            val cachedBytes = ProotInfoStore.cachedBytes(context, GuestStorageCatalog.ALL_PROOT_ID)
            ProotSettingsModel.SizeUi(
                installed = true,
                dirExists = true,
                bytes = cachedBytes,
                dimmedCache = cachedBytes != null,
                hint = "Tap refresh to measure"
            )
        } else {
            ProotSettingsModel.loadCached(context, distroId)
        }
    }

    var sizeUi by remember { mutableStateOf(initialSizeUi) }
    var busy by remember { mutableStateOf(false) }

    fun refreshSize() {
        if (busy) return
        busy = true
        sizeUi = sizeUi.copy(measuring = true, hint = "Measuring container storage…")
        scope.launch {
            val s = withContext(Dispatchers.IO) {
                if (isUniversal) {
                    ProotSettingsModel.refreshUniversal(context, installableIds)
                } else {
                    ProotSettingsModel.refreshSize(context, distroId)
                }
            }
            sizeUi = s
            busy = false
        }
    }

    LaunchedEffect(distroId) {
        refreshSize()
    }

    val pageTitle = if (isUniversal) "All containers" else distro!!.name
    val pageSubtitle = if (isUniversal) {
        "// Userspace Linux — inside app storage"
    } else {
        "// Userspace ${distro!!.name} — inside app storage"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        pageTitle,
                        style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back")
                    }
                })
        }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val accent = MaterialTheme.colorScheme.secondary
            val body = MaterialTheme.colorScheme.onBackground
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            val label = MaterialTheme.colorScheme.onSurfaceVariant

            Text(
                pageSubtitle,
                fontFamily = FontFamily.Monospace,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)

            // Info banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        androidx.compose.material3.MaterialTheme.shapes.small
                    )
                    .padding(12.dp)
            ) {
                Text(
                    "PRoot lives in app storage (container + rootfs) and is removed if you uninstall the app.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            // Storage card
            SettingsCard {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    Text(
                        "Container storage")
                    Spacer(Modifier.height(12.dp))

                    MetaRow("Status") {
                        val statusText = when {
                            isUniversal -> if (sizeUi.installed) "ACTIVE" else "READY"
                            sizeUi.installed -> "INSTALLED"
                            sizeUi.dirExists -> "PRESENT"
                            else -> "NOT INSTALLED"
                        }
                        StatusBadge(
                            text = statusText,
                            ok = sizeUi.installed
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Size panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                androidx.compose.material3.MaterialTheme.shapes.medium
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PROOT STORAGE",
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            IconButton(
                                onClick = { refreshSize() },
                                enabled = !busy) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh size")
                            }
                        }
                        if (sizeUi.measuring || busy && sizeUi.bytes == null) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                        }
                        val (v, u) = ProotInfoStore.formatStorageBytes(sizeUi.bytes)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                v,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.alpha(if (sizeUi.dimmedCache) 0.85f else 1f),
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                            if (u.isNotEmpty()) {
                                Text(
                                    " $u",
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        Text(
                            sizeUi.hint,
                            fontFamily = FontFamily.Monospace,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                androidx.compose.material3.MaterialTheme.shapes.small
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            "CONTAINER PATH",
                            fontFamily = FontFamily.Monospace,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Text(
                            containerPath ?: "",
                            fontFamily = FontFamily.Monospace,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }

                    if (!isUniversal && !sizeUi.installed && onNavigateToInstall != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onNavigateToInstall() },
                            modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("Install PRoot distro")
                        }
                    }
                }
            }

            Button(
                onClick = { refreshSize() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Refresh size")
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}
