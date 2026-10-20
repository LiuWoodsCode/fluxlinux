package com.ivarna.fluxlinux.core.utils

import com.ivarna.fluxlinux.core.terminal.ProotCommandBuilder
import com.ivarna.fluxlinux.core.terminal.ChrootCommandBuilder
import org.junit.Assert.*
import org.junit.Test

class GuestUserTest {
    @Test
    fun validatesLinuxNames_andRejectsReservedNamesAndShellSyntax() {
        listOf("teto", "alice", "user_2", "my-user", "_guest").forEach {
            assertNull(it, GuestUser.validationError(it))
        }
        listOf("", "root", "nobody", "www-data", "UPPER", "123", "a b", "a/b", "a;id", "a'", "a\nb", "a".repeat(33)).forEach {
            assertNotNull(it, GuestUser.validationError(it))
        }
    }

    @Test
    fun remembersEachDistro_andKeepsLegacyDefault() {
        val ctx = FakePrefsContext()
        assertEquals("flux", GuestUser.get(ctx, "debian"))
        GuestUser.set(ctx, "debian", "teto")
        GuestUser.set(ctx, "alpine", "alice")
        assertEquals("teto", GuestUser.get(ctx, "debian"))
        assertEquals("alice", GuestUser.get(ctx, "alpine"))
        assertEquals("flux", GuestUser.get(ctx, "fedora"))
        GuestUser.set(ctx, "debian_chroot", "bob")
        assertEquals("bob", GuestUser.get(ctx, "debian13_chroot"))
    }

    @Test
    fun terminalBuilders_useSelectedAccountAndHome_andKeepRootSeparate() {
        val vars = ProotCommandBuilder.guestEnvVars("teto", "xterm")
        assertTrue(vars.contains("HOME=/home/teto"))
        assertTrue(vars.contains("USER=teto"))
        assertTrue(vars.contains("LOGNAME=teto"))
        val args = ProotCommandBuilder.buildArgs("bash", "proot-distro", "exec bash", user = "teto")
        assertTrue(args.last().contains("--user teto"))
        assertTrue(ChrootCommandBuilder.buildRootInner("exec bash", user = "teto").contains("--user teto"))
        assertEquals("/home/teto", ChrootCommandBuilder.buildEnv("teto")["HOME"])
        assertEquals("/root", ChrootCommandBuilder.buildEnv("root")["HOME"])
        assertTrue(ProotCommandBuilder.guestEnvVars("root", "xterm").contains("HOME=/root"))
    }
}
