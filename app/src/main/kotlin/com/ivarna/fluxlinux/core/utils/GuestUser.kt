package com.ivarna.fluxlinux.core.utils

import android.content.Context
import java.io.File

/** Login identity is stored per distro; older installations keep the flux account. */
object GuestUser {
    const val DEFAULT = "flux"
    private val namePattern = Regex("[a-z_][a-z0-9_-]{0,31}")
    private val reserved = setOf(
        "root", "nobody", "daemon", "bin", "sys", "sync", "shutdown", "halt",
        "mail", "news", "uucp", "operator", "www", "www-data", "backup", "games",
        "ftp", "sshd", "dbus", "messagebus", "polkitd", "systemd-network"
    )

    fun validationError(name: String): String? = when {
        name.isEmpty() -> "Enter a username."
        !namePattern.matches(name) -> "Use 1–32 lowercase letters, digits, _ or -. Start with a letter or _."
        name in reserved -> "Choose a username that is not reserved by the system."
        else -> null
    }

    fun requireValid(name: String): String {
        require(validationError(name) == null) { validationError(name) ?: "Invalid username" }
        return name
    }

    fun requireLogin(name: String): String = if (name == "root") name else requireValid(name)

    private fun key(distroId: String) = if (distroId == "debian_chroot") "debian13_chroot" else distroId

    fun get(ctx: Context, distroId: String?): String =
        ctx.applicationContext.getSharedPreferences("flux_guest_users", Context.MODE_PRIVATE)
            .getString(key(distroId ?: "debian"), DEFAULT)
            ?.takeIf { validationError(it) == null } ?: DEFAULT

    fun set(ctx: Context, distroId: String, name: String) {
        ctx.applicationContext.getSharedPreferences("flux_guest_users", Context.MODE_PRIVATE)
            .edit().putString(key(distroId), requireValid(name)).apply()
    }

    fun home(name: String): String = if (name == "root") "/root" else "/home/${requireValid(name)}"

    fun inRootfs(rootfs: File): String = runCatching {
        File(rootfs, "etc/fluxlinux/username").readText().trim()
            .takeIf { validationError(it) == null } ?: DEFAULT
    }.getOrDefault(DEFAULT)
}
