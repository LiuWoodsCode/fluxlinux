package com.ivarna.fluxlinux.core.install

import android.content.Context
import com.ivarna.fluxlinux.core.root.RootShell
import java.io.File

/** The only appearance asset installed by onboarding. Themes remain opt-in. */
object BaseDesktopDefaults {
    const val SCRIPT = "common/setup/setup_base_desktop.sh"
    const val WALLPAPER_ASSET = "xfce4/wallpaper/KasaneTeto.PNG"
    const val WALLPAPER_PATH = "/usr/share/backgrounds/KasaneTeto.PNG"

    fun stageWallpaper(ctx: Context, profile: DistroInstallProfile) {
        if (profile.method == "chroot") {
            val staged = File(ctx.cacheDir, "KasaneTeto.PNG")
            copyWallpaper(ctx, staged)
            val rootfs = requireNotNull(profile.chrootPath)
            val result = RootShell.captureResult(
                "mkdir -p '$rootfs/usr/share/backgrounds' && " +
                    "cp -f '${staged.absolutePath}' '$rootfs$WALLPAPER_PATH' && " +
                    "chmod 644 '$rootfs$WALLPAPER_PATH'",
                timeoutMs = 60_000L
            )
            check(result.exitCode == 0) { "Could not stage the default wallpaper in chroot" }
        } else {
            val rootfs = ProotXfceAssetInstaller.prootRootfs(ctx, profile.prootName)
            check(rootfs.isDirectory) { "Proot rootfs missing while staging wallpaper" }
            copyWallpaper(ctx, File(rootfs, WALLPAPER_PATH.removePrefix("/")))
        }
    }

    private fun copyWallpaper(ctx: Context, dest: File) {
        check(dest.parentFile?.isDirectory == true || dest.parentFile?.mkdirs() == true)
        ctx.assets.open(WALLPAPER_ASSET).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        check(dest.setReadable(true, false)) { "Could not make the default wallpaper readable" }
    }
}
