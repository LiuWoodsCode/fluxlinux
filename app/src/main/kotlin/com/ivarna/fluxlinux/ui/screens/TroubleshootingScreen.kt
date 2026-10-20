package com.ivarna.fluxlinux.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.ui.components.SettingsCard

data class TroubleshootingItem(
    val title: String,
    val content: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen(
    onBack: () -> Unit
) {


    val issues = listOf(
        TroubleshootingItem("Termux Connection Failed", "Run the fix command from the Home screen connection panel. Ensure 'Allow External Apps' is true in ~/.termux/termux.properties."),
        TroubleshootingItem("GUI Not Starting", "Check if Termux:X11 is installed. Ensure you selected the correct display ID (usually 0). Try 'export DISPLAY=:0' in Termux."),
        TroubleshootingItem("Audio Issues", "Settings → Audio shows whether host Pulse is running, can start or restart it, and has supervisor logs. Guests use PULSE_SERVER=tcp:127.0.0.1. Do not install a Pulse server inside the guest."),
        TroubleshootingItem("Performance is Low", "Enable hardware acceleration in Settings if supported. Ensure you are not running heavy generic kernels without GPU drivers."),
        TroubleshootingItem("Touch Input Offset", "Check screen resolution settings in Termux:X11 preferences. Matches device resolution?")
    )

    Box(
        modifier = Modifier.fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Troubleshooting") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    })
            }) { innerPadding ->
            LazyColumn(
                contentPadding = innerPadding,
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(issues) { issue ->
                    ExpandableIssueCard(issue)
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun ExpandableIssueCard(issue: TroubleshootingItem) {
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = issue.title)
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null)
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = issue.content)
                }
            }
        }
    }
}
