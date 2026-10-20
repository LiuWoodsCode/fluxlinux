package com.ivarna.fluxlinux.ui.install

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivarna.fluxlinux.core.utils.GuestUser

@Composable
fun GuestUsernameField(username: String, onUsernameChange: (String) -> Unit) {
    val error = GuestUser.validationError(username)
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Linux username") },
        placeholder = { Text("e.g. teto") },
        supportingText = { Text(if (username.isNotEmpty() && error != null) error else "Your login name and home folder for this distro.") },
        isError = username.isNotEmpty() && error != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
