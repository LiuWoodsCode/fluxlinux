package com.ivarna.fluxlinux.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BaseDesktopDefaultsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun asset(path: String): File =
        listOf(File("src/main/assets", path), File("app/src/main/assets", path))
            .first { it.isFile }

    private fun run(script: File) {
        val process = ProcessBuilder("sh", script.absolutePath).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
    }

    @Test
    fun firstInstall_setsFillWallpaper_andKeepsOtherSettings_andRetryKeepsUserWallpaper() {
        val home = tmp.newFolder("home")
        val config = tmp.newFolder("etc")
        val wallpaper = tmp.newFile("KasaneTeto.PNG").also { it.writeText("image") }
        val xfconf = File(home, ".config/xfce4/xfconf/xfce-perchannel-xml").also { it.mkdirs() }
        val theme = File(xfconf, "xsettings.xml").also { it.writeText("user theme") }
        val panel = File(xfconf, "xfce4-panel.xml").also { it.writeText("user panel") }
        val bashrc = File(home, ".bashrc").also { it.writeText("user shell") }
        // Redirect guest paths into an isolated root; run the shipped shell body.
        val script = tmp.newFile("setup.sh").also {
            it.writeText(asset("scripts/${BaseDesktopDefaults.SCRIPT}").readText()
                .replace("/home/\${FLUX_USER}", home.absolutePath)
                .replace("/etc/fluxlinux", config.absolutePath)
                .replace(BaseDesktopDefaults.WALLPAPER_PATH, wallpaper.absolutePath))
        }
        run(script)

        val desktop = File(xfconf, "xfce4-desktop.xml")
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(desktop)
        val properties = xml.getElementsByTagName("property")
        var images = 0
        var fillStyles = 0
        for (i in 0 until properties.length) {
            val attrs = properties.item(i).attributes
            when (attrs.getNamedItem("name").nodeValue) {
                "last-image" -> {
                    images++
                    assertEquals(wallpaper.absolutePath, attrs.getNamedItem("value").nodeValue)
                }
                "image-style" -> {
                    fillStyles++
                    assertEquals("5", attrs.getNamedItem("value").nodeValue)
                }
            }
        }
        assertTrue(images > 0)
        assertEquals(images, fillStyles)
        assertTrue(File(config, "base-desktop").isFile)
        assertEquals("user theme", theme.readText())
        assertEquals("user panel", panel.readText())
        assertEquals("user shell", bashrc.readText())
        assertFalse(File(home, ".zshrc").exists())
        assertFalse(File(home, ".oh-my-zsh").exists())

        desktop.writeText("user wallpaper settings")
        run(script)
        assertEquals("user wallpaper settings", desktop.readText())
    }

    @Test
    fun chrootLogin_doesNotSeedShellCustomizationsOnBaseInstall() {
        val root = tmp.newFolder("rootfs")
        File(root, "etc/fluxlinux").mkdirs()
        File(root, "etc/fluxlinux/base-desktop").writeText("")
        val home = File(root, "home/flux").also { it.mkdirs() }
        val helper = asset("scripts/chroot/fluxlinux_chroot.sh").readText()
        val function = "ensure_flux_zsh_profile() {" + helper
            .substringAfter("ensure_flux_zsh_profile() {").substringBefore("\nguest_login() {")
        val script = tmp.newFile("login.sh").also {
            it.writeText("FLUX_CHROOT='${root.absolutePath}'\nUSER_NAME=flux\n$function\nensure_flux_zsh_profile\n")
        }
        run(script)
        assertFalse(File(home, ".zshrc").exists())
        assertFalse(File(home, ".zprofile").exists())
    }
}
