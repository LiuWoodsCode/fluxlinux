package com.ivarna.fluxlinux.ui.screens.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MetaRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                androidx.compose.material3.MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        trailing()
    }
}

@Composable
fun StatusBadge(text: String, ok: Boolean) {

    val accent = MaterialTheme.colorScheme.secondary
    val fg = if (ok) accent else MaterialTheme.colorScheme.error
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(
                androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                androidx.compose.material3.MaterialTheme.shapes.extraSmall
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
}
