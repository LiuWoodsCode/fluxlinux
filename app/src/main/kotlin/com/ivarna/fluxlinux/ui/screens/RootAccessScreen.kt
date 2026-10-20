package com.ivarna.fluxlinux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootAccessScreen(
    onBack: () -> Unit,
    onEnableChroot: () -> Unit
) {
    var isRooted by remember { mutableStateOf(false) }
    var checkComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRooted = checkForRoot()
        checkComplete = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Root Access") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                modifier = Modifier.background(Color.Transparent))
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(androidx.compose.material3.MaterialTheme.shapes.large)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (checkComplete && isRooted) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (!checkComplete) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking for root access...")
                } else {
                    if (isRooted) {
                        Text(
                            "Root Access Detected",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your device is rooted. You can enable Chroot mode for better performance and full hardware access.",
                            textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = onEnableChroot,
                            modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text("Enable Chroot Mode")
                        }
                    } else {
                        Text(
                            "No Root Access",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your device does not appear to be rooted. Chroot mode requires root access to function.",
                            textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Please continue using PRoot mode (Default). It works on all devices without root.",
                            textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text("Return to Settings")
                        }
                    }
                }
            }
        }
    }
}

private fun checkForRoot(): Boolean {
    val paths = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su"
    )
    return try {
        paths.any { File(it).exists() }
    } catch (e: Exception) {
        false
    }
}
