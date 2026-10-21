package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ivarna.fluxlinux.core.gpu.GpuDriverInstaller
import com.ivarna.fluxlinux.core.root.RootShell
import com.ivarna.fluxlinux.core.service.BaseInstallService
import com.ivarna.fluxlinux.core.terminal.GpuAccelDetector
import com.ivarna.fluxlinux.core.terminal.HostCommandBuilder
import com.ivarna.fluxlinux.core.terminal.ShellCommandRunner
import com.ivarna.fluxlinux.core.terminal.TerminalLauncher
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import com.ivarna.fluxlinux.core.utils.StateManager
import com.ivarna.fluxlinux.core.utils.GuestUser
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the simplified onboarding / base install with phase progress + live log.
 * Proot: prepareHost → flux_install (family) → base defaults + GPU setup.
 * Chroot: root check → prepareHost → setup_*_chroot → family → base defaults + GPU setup.
 *
 * Distro identity from [DistroInstallProfile] (Debian + Alpine).
 *
 * Cancel: generation token + destroy active process; never mutates StateManager after cancel.
 * Overlapping [start] cancels the previous run first.
 */
class OnboardingInstallRunner(private val ctx: Context) {

    data class Progress(
        val phaseId: String,
        val phaseLabel: String,
        val phaseIndex: Int,
        val phaseCount: Int,
        val overallPercent: Int,
        val detail: String,
        /** null when the active command does not report a measurable fraction. */
        val phasePercent: Int? = null,
        val logLine: String? = null,
        val failed: Boolean = false,
        val finished: Boolean = false,
        val errorMessage: String? = null
    )

    private val appCtx = ctx.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val activeProcess = AtomicReference<Process?>(null)
    private val busy = AtomicBoolean(false)
    /** Throttle FGS notification updates (percent/label change only). */
    @Volatile private var lastNotifPercent: Int = -1
    @Volatile private var lastNotifLabel: String = ""
    /** Last overall percent from real phase updates (log lines must not thrash). */
    @Volatile private var lastOverallPercent: Int = 0
    @Volatile private var lastPhasePercent: Int? = null
    @Volatile private var lastPhaseIndex: Int = 0
    @Volatile private var lastDetail: String = ""

    fun cancel() {
        cancelled.set(true)
        activeProcess.getAndSet(null)?.destroyForcibly()
        // Drop keep-alive when user aborts (also safe if FGS was never started)
        BaseInstallService.stop(appCtx)
    }

    fun isBusy(): Boolean = busy.get()

    /**
     * Start (or restart) install. If a previous job is still running, it is cancelled
     * and the new job is queued on the single-thread executor.
     *
     * Starts [BaseInstallService] FGS immediately so the system does not kill the
     * process during long rootfs / package work.
     */
    fun start(
        distroId: String,
        username: String,
        onProgress: (Progress) -> Unit
    ) {
        GuestUser.requireValid(username)
        // Invalidate any in-flight job before resetting cancel flag
        val gen = generation.incrementAndGet()
        cancelled.set(true)
        activeProcess.getAndSet(null)?.destroyForcibly()
        cancelled.set(false)
        lastOverallPercent = 0
        lastPhasePercent = null
        lastPhaseIndex = 0
        lastDetail = ""
        lastNotifPercent = -1
        lastNotifLabel = ""

        val profile = DistroInstallProfile.forId(distroId)
        if (profile == null) {
            busy.set(false)
            main.post {
                onProgress(
                    Progress(
                        phaseId = "ERR",
                        phaseLabel = "Failed",
                        phaseIndex = 0,
                        phaseCount = 1,
                        overallPercent = 0,
                        detail = "Unsupported distro: $distroId",
                        failed = true,
                        finished = true,
                        errorMessage = "Unsupported distro: $distroId"
                    )
                )
            }
            return
        }
        val method = profile.method
        GuestUser.set(appCtx, distroId, username)
        val phases = BaseDesktopInstallPlan.phasesFor(method, profile.displayName)
        busy.set(true)
        lastNotifPercent = -1
        lastNotifLabel = ""
        // FGS first — before any long work — so backgrounding mid-install is safe
        try {
            BaseInstallService.start(
                appCtx,
                title = "FluxLinux — Installing",
                text = "${profile.displayName} base desktop",
                percent = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "BaseInstallService.start failed", e)
        }
        executor.execute {
            try {
                if (isStale(gen)) {
                    postCancelled(onProgress, phases)
                    return@execute
                }
                if (method == "chroot") {
                    runChroot(distroId, phases, onProgress, gen)
                } else {
                    runProot(distroId, phases, onProgress, gen)
                }
            } catch (e: Exception) {
                if (!isStale(gen)) {
                    Log.e(TAG, "install failed", e)
                    postFail(onProgress, phases, e.message ?: "Unknown error")
                } else {
                    postCancelled(onProgress, phases)
                }
            } finally {
                if (generation.get() == gen) {
                    busy.set(false)
                    BaseInstallService.stop(appCtx)
                }
            }
        }
    }

    private fun isStale(gen: Int): Boolean =
        cancelled.get() || generation.get() != gen

    /** Phase index by [Phase.id] — never a hardcoded numeric index (D10). */
    private fun phaseIdx(phases: List<BaseDesktopInstallPlan.Phase>, id: String): Int =
        phases.indexOfFirst { it.id == id }.also { i ->
            require(i >= 0) { "install plan missing phase '$id'" }
        }

    private fun abortIfCancelled(
        gen: Int,
        phases: List<BaseDesktopInstallPlan.Phase>,
        onProgress: (Progress) -> Unit
    ): Boolean {
        if (!isStale(gen)) return false
        postCancelled(onProgress, phases)
        return true
    }

    private fun runProot(
        distroId: String,
        phases: List<BaseDesktopInstallPlan.Phase>,
        onProgress: (Progress) -> Unit,
        gen: Int
    ) {
        val hostIdx = phaseIdx(phases, "HOST")
        val dlIdx = phaseIdx(phases, "DL")
        val rootfsIdx = phaseIdx(phases, "ROOTFS")
        val finishIdx = phaseIdx(phases, "FINISH")

        enter(phases, hostIdx, onProgress, "Extracting bootstrap + deploying scripts…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        var lastHostPhase = ""
        val hostOk = TerminalLauncher.prepareHostBlocking(appCtx, forceHostSetup = false) { done, total, phase ->
            if (isStale(gen)) return@prepareHostBlocking
            val frac = if (total > 0) done.toFloat() / total else null
            updateFraction(phases, hostIdx, frac, onProgress, phase)
            if (phase.isNotBlank() && phase != lastHostPhase) {
                lastHostPhase = phase
                log(phases, hostIdx, onProgress, phase)
            }
        }
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!hostOk) {
            postFail(onProgress, phases, "Host bootstrap failed")
            return
        }
        completePhase(phases, hostIdx, onProgress, "Host ready")

        val profile = DistroInstallProfile.require(distroId)
        enter(phases, dlIdx, onProgress, "Downloading ${profile.displayName} rootfs…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val destDir = TermuxHostPaths.homeDir(appCtx)
        val dlOk = RootfsDownloader.ensurePresent(
            destDir, profile, RootfsDownloader.defaultClient,
            isCancelled = { isStale(gen) }
        ) { p ->
            if (isStale(gen)) return@ensurePresent
            val frac = if (p.totalBytes > 0) p.downloadedBytes.toFloat() / p.totalBytes else null
            updateFraction(
                phases, dlIdx, frac, onProgress,
                "Downloaded ${p.downloadedBytes / 1_048_576} / " +
                    "${p.totalBytes.coerceAtLeast(0) / 1_048_576} MiB"
            )
        }
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!dlOk) {
            postFail(
                onProgress, phases,
                "Rootfs download failed — place ${profile.rootfsFileName} in the app " +
                    "home directory (${TermuxHostPaths.HOME}) or retry online"
            )
            return
        }
        completePhase(phases, dlIdx, onProgress, "Rootfs ready (${profile.rootfsFileName})")

        enter(phases, rootfsIdx, onProgress, "Installing ${profile.displayName} via proot-distro…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val bash = TermuxHostPaths.libBash(appCtx).absolutePath
        val installScript = TermuxHostPaths.hostScript(appCtx, "flux_install.sh").absolutePath
        val setupB64 = BaseDesktopInstallPlan.familySetupB64(appCtx, distroId)
        val env = HostCommandBuilder.envMap(appCtx, forceHostSetup = false, includeTerm = false).apply {
            put("PYTHONUNBUFFERED", "1")
            put("FLUX_ROOTFS_PATH", "${TermuxHostPaths.HOME}/${profile.rootfsFileName}")
            put("FLUX_ROOTFS_NAME", profile.rootfsFileName)
            put("FLUX_ROOTFS_SHA256", profile.rootfsSha256)
            put("FLUX_ROOTFS_URL", profile.rootfsUrl)
        }
        // Never exec $PREFIX/bin/* as argv0 — W^X (targetSdk 36) only allows
        // nativeLibraryDir (libbash.so / libproot.so). stdbuf lives under PREFIX
        // and fails with EACCES (error 13). Stream lines as the pipe delivers them.
        val (exitInstall, _) = ShellCommandRunner.runCaptureExit(
            appCtx,
            arrayOf(bash, installScript, profile.prootName, setupB64),
            env,
            processHolder = activeProcess,
            onLine = { line ->
                if (!isStale(gen) && line.isNotBlank()) log(phases, rootfsIdx, onProgress, line)
            }
        )
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (exitInstall != 0) {
            postFail(onProgress, phases, "${profile.displayName} install failed (exit $exitInstall)")
            return
        }
        if (!TerminalLauncher.isProotInstalled(appCtx, profile.prootName)) {
            postFail(onProgress, phases, "${profile.displayName} rootfs missing after install")
            return
        }
        if (!isProotXfceInstalled(appCtx, profile.prootName)) {
            postFail(
                onProgress, phases,
                "XFCE not found after install (startxfce4 missing). Retry setup."
            )
            return
        }
        completePhase(phases, rootfsIdx, onProgress, "${profile.displayName} + XFCE installed")

        enter(phases, finishIdx, onProgress, "Setting the default wallpaper…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        BaseDesktopDefaults.stageWallpaper(appCtx, profile)
        if (abortIfCancelled(gen, phases, onProgress)) return
        val defaultsOk = runProotGuestScript(
            phases, finishIdx, onProgress, gen,
            prootName = profile.prootName,
            scriptAssetPath = BaseDesktopDefaults.SCRIPT,
            envPrefix = ""
        )
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!defaultsOk) {
            postFail(onProgress, phases, "Base desktop defaults failed. Retry setup.")
            return
        }

        if (abortIfCancelled(gen, phases, onProgress)) return
        val hwOk = runHwAccelIfPresent(
            profile, method = "proot", phases, finishIdx, onProgress, gen, chrootPath = null
        )

        completePhase(phases, finishIdx, onProgress, "Base desktop ready")

        if (abortIfCancelled(gen, phases, onProgress)) return
        StateManager.setDistroInstalled(appCtx, distroId, true)
        StateManager.setComponentInstalled(appCtx, distroId, "xfce4_desktop", true)
        if (hwOk) {
            StateManager.setComponentInstalled(appCtx, distroId, "hw_accel", true)
        }
        StateManager.triggerRefresh()
        postSuccess(onProgress, phases)
    }

    private fun runChroot(
        distroId: String,
        phases: List<BaseDesktopInstallPlan.Phase>,
        onProgress: (Progress) -> Unit,
        gen: Int
    ) {
        val r0Idx = phaseIdx(phases, "R0")
        val hostIdx = phaseIdx(phases, "HOST")
        val dlIdx = phaseIdx(phases, "DL")
        val rootfsIdx = phaseIdx(phases, "ROOTFS")
        val xfceIdx = phaseIdx(phases, "XFCE")
        val finishIdx = phaseIdx(phases, "FINISH")

        enter(phases, r0Idx, onProgress, "Probing KernelSU / Magisk…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!RootShell.isRootAvailable()) {
            postFail(
                onProgress, phases,
                "Root not available. Grant superuser to FluxLinux, then retry."
            )
            return
        }
        completePhase(phases, r0Idx, onProgress, "Root OK")
        RootShell.ensureBusyBoxResolver(appCtx)
        val resolvedBb = RootShell.resolveBusyBox()

        enter(phases, hostIdx, onProgress, "Extracting bootstrap + deploying scripts…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        var lastHostPhase = ""
        val hostOk = TerminalLauncher.prepareHostBlocking(appCtx, forceHostSetup = false) { done, total, phase ->
            if (isStale(gen)) return@prepareHostBlocking
            val frac = if (total > 0) done.toFloat() / total else null
            updateFraction(phases, hostIdx, frac, onProgress, phase)
            if (phase.isNotBlank() && phase != lastHostPhase) {
                lastHostPhase = phase
                log(phases, hostIdx, onProgress, phase)
            }
        }
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!hostOk) {
            postFail(onProgress, phases, "Host bootstrap failed")
            return
        }
        completePhase(phases, hostIdx, onProgress, "Host ready")

        val profile = DistroInstallProfile.require(distroId)

        // DL after HOST only — R0 stays first so non-rooted devices fail
        // before any download (R6).
        enter(phases, dlIdx, onProgress, "Downloading ${profile.displayName} rootfs…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val destDir = TermuxHostPaths.homeDir(appCtx)
        val dlOk = RootfsDownloader.ensurePresent(
            destDir, profile, RootfsDownloader.defaultClient,
            isCancelled = { isStale(gen) }
        ) { p ->
            if (isStale(gen)) return@ensurePresent
            val frac = if (p.totalBytes > 0) p.downloadedBytes.toFloat() / p.totalBytes else null
            updateFraction(
                phases, dlIdx, frac, onProgress,
                "Downloaded ${p.downloadedBytes / 1_048_576} / " +
                    "${p.totalBytes.coerceAtLeast(0) / 1_048_576} MiB"
            )
        }
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (!dlOk) {
            postFail(
                onProgress, phases,
                "Rootfs download failed — place ${profile.rootfsFileName} in the app " +
                    "home directory (${TermuxHostPaths.HOME}) or retry online"
            )
            return
        }
        completePhase(phases, dlIdx, onProgress, "Rootfs ready (${profile.rootfsFileName})")

        val chrootPath = profile.chrootPath
            ?: com.ivarna.fluxlinux.core.root.ChrootPaths.CHROOT_PATH
        val setupAsset = profile.chrootSetupAsset
            ?: "scripts/chroot/setup_debian13_chroot.sh"
        val setupName = File(setupAsset).name

        enter(phases, rootfsIdx, onProgress, "Extracting chroot rootfs (may take several minutes)…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val staged = RootShell.stageAsset(appCtx, setupAsset)
            ?: TermuxHostPaths.hostScript(appCtx, setupName).absolutePath
        val envHome = TermuxHostPaths.HOME
        // Do not wrap with $PREFIX/bin/stdbuf — host W^X denies exec from app data.
        val label = profile.distroId.removeSuffix("_chroot")
        val bbExport = if (!resolvedBb.isNullOrEmpty()) "export FLUX_BB='$resolvedBb'; " else ""
        val rootCmd =
            bbExport +
            "export FLUX_ROOTFS_PATH='$envHome/${profile.rootfsFileName}'; " +
                "export FLUX_ROOTFS_NAME='${profile.rootfsFileName}'; " +
                "export FLUX_ROOTFS_SHA256='${profile.rootfsSha256}'; " +
                "export FLUX_ROOTFS_URL='${profile.rootfsUrl}'; " +
                "export FLUX_CHROOT='$chrootPath'; " +
                "export FLUX_USER='${GuestUser.get(appCtx, distroId)}'; " +
                "export FLUX_DISTRO_LABEL='$label'; " +
                "export TERMUX_APP__PACKAGE_NAME='${TermuxHostPaths.PACKAGE}'; " +
                "export TERMUX__HOME='$envHome'; " +
                "export PYTHONUNBUFFERED=1; " +
                "sh '$staged'"
        val rootResult = RootShell.captureResult(
            rootCmd,
            timeoutMs = 0L,
            onLine = { line ->
                if (!isStale(gen) && line.isNotBlank()) log(phases, rootfsIdx, onProgress, line)
            },
            processHolder = activeProcess
        )
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (rootResult.exitCode != 0) {
            postFail(onProgress, phases, "Chroot install failed (exit ${rootResult.exitCode})")
            return
        }
        // App SELinux cannot see /data/local/tmp — always re-probe as root after install.
        TerminalLauncher.invalidateChrootInstalledCache()
        val installed = TerminalLauncher.isChrootInstalled(chrootPath)
        if (!installed) {
            log(phases, rootfsIdx, onProgress, "Root probe: chroot missing at $chrootPath")
            postFail(
                onProgress, phases,
                "Chroot rootfs missing after install (check root grant + $chrootPath)"
            )
            return
        }
        log(phases, rootfsIdx, onProgress, "Chroot rootfs verified (root probe)")
        completePhase(phases, rootfsIdx, onProgress, "Chroot rootfs ready")

        enter(phases, xfceIdx, onProgress, "Installing XFCE packages…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        val familyPayload = BaseDesktopInstallPlan.familySetupPayload(appCtx, distroId)
        val familyExit = runChrootGuestBlocking(
            familyPayload, user = "root", phases, xfceIdx, onProgress, chrootPath
        )
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (familyExit != 0) {
            postFail(onProgress, phases, "XFCE setup failed (exit $familyExit)")
            return
        }
        if (!TerminalLauncher.isChrootXfceInstalled(chrootPath)) {
            postFail(
                onProgress, phases,
                "XFCE not found after family setup (startxfce4 missing). Retry."
            )
            return
        }
        completePhase(phases, xfceIdx, onProgress, "XFCE installed")

        enter(phases, finishIdx, onProgress, "Setting the default wallpaper…")
        if (abortIfCancelled(gen, phases, onProgress)) return
        BaseDesktopDefaults.stageWallpaper(appCtx, profile)
        if (abortIfCancelled(gen, phases, onProgress)) return
        val defaultsPayload = com.ivarna.fluxlinux.core.data.ScriptManager(appCtx)
            .getScriptContent(BaseDesktopDefaults.SCRIPT)
        val defaultsExit = runChrootGuestBlocking(
            defaultsPayload, user = "root", phases, finishIdx, onProgress, chrootPath
        )
        if (abortIfCancelled(gen, phases, onProgress)) return
        if (defaultsExit != 0) {
            postFail(onProgress, phases, "Base desktop defaults failed (exit $defaultsExit). Retry setup.")
            return
        }

        if (abortIfCancelled(gen, phases, onProgress)) return
        val hwOk = runHwAccelIfPresent(
            profile, method = "chroot", phases, finishIdx, onProgress, gen, chrootPath = chrootPath
        )

        completePhase(phases, finishIdx, onProgress, "Base desktop ready")

        if (abortIfCancelled(gen, phases, onProgress)) return
        StateManager.setDistroInstalled(appCtx, distroId, true)
        StateManager.setComponentInstalled(appCtx, distroId, "xfce4_desktop", true)
        if (hwOk) {
            StateManager.setComponentInstalled(appCtx, distroId, "hw_accel", true)
        }
        StateManager.triggerRefresh()
        postSuccess(onProgress, phases)
    }

    /**
     * Run the guest hw-accel installer with host-detected FLUX_GPU.
     * Failure is logged and does not fail onboarding (family first-paint is VirGL).
     */
    private fun runHwAccelIfPresent(
        profile: DistroInstallProfile,
        method: String,
        phases: List<BaseDesktopInstallPlan.Phase>,
        phaseIndex: Int,
        onProgress: (Progress) -> Unit,
        gen: Int,
        chrootPath: String?
    ): Boolean {
        val script = profile.hwAccelScript ?: return false
        val gpu = GpuAccelDetector.detect()
        GpuAccelDetector.persist(appCtx, gpu)
        log(
            phases, phaseIndex, onProgress,
            "Hardware acceleration: mode=${gpu.mode} vendor=${gpu.vendorHint}"
        )
        // Adreno: app picks the stable Turnip release (empty map elsewhere).
        val driverEnv = GpuDriverInstaller.guestEnv(appCtx, profile.distroId)
        (driverEnv["FLUX_TURNIP_MSG"] ?: driverEnv["FLUX_PANVK_MSG"])?.let { log(phases, phaseIndex, onProgress, it) }
        val ok = try {
            if (method == "chroot") {
                val path = chrootPath
                    ?: com.ivarna.fluxlinux.core.root.ChrootPaths.CHROOT_PATH
                val payload = BaseDesktopInstallPlan.hwAccelPayload(
                    appCtx, profile.distroId, gpu.mode, gpu.vendorHint, driverEnv
                )
                runChrootGuestBlocking(payload, user = "root", phases, phaseIndex, onProgress, path) == 0
            } else {
                stageFluxGpuCommon()
                runProotGuestScript(
                    phases, phaseIndex, onProgress, gen,
                    prootName = profile.prootName,
                    scriptAssetPath = script,
                    envPrefix = "FLUX_GPU=${gpu.mode} FLUX_GPU_VENDOR=${gpu.vendorHint}" +
                        driverEnv.entries.joinToString("") {
                            " ${it.key}='${it.value.replace("'", "")}'"
                        }
                )
            }
        } catch (e: Exception) {
            log(phases, phaseIndex, onProgress, "Hardware acceleration error: ${e.message}")
            false
        }
        if (driverEnv.isNotEmpty()) {
            GpuDriverInstaller.refreshInstalled(
                appCtx, profile.distroId, profile.prootName, method == "chroot"
            )
        }
        if (GpuDriverInstaller.pendingDistro(appCtx) != null) {
            GpuDriverInstaller.onPending?.invoke()
        }
        if (ok) {
            log(phases, phaseIndex, onProgress, "Hardware acceleration installed (${gpu.mode})")
        } else {
            log(
                phases, phaseIndex, onProgress,
                "Hardware acceleration skipped or failed — VirGL first-paint remains"
            )
        }
        return ok
    }

    private fun stageFluxGpuCommon() {
        val tmp = File(TermuxHostPaths.TMPDIR)
        if (!tmp.exists()) tmp.mkdirs()
        val dest = File(tmp, "flux_gpu_common.sh")
        appCtx.assets.open("scripts/common/setup/flux_gpu_common.sh").use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        dest.setExecutable(true)
    }

    private fun runProotGuestScript(
        phases: List<BaseDesktopInstallPlan.Phase>,
        phaseIndex: Int,
        onProgress: (Progress) -> Unit,
        gen: Int,
        prootName: String,
        scriptAssetPath: String,
        envPrefix: String
    ): Boolean {
        return try {
            val tmp = File(TermuxHostPaths.TMPDIR)
            if (!tmp.exists()) tmp.mkdirs()
            val name = File(scriptAssetPath).name
            val dest = File(tmp, name)
            appCtx.assets.open("scripts/$scriptAssetPath").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.setExecutable(true)
            val bash = TermuxHostPaths.libBash(appCtx).absolutePath
            // Prefer bash if present; Alpine family setup also installs it.
            // libbash.so only — never prefix stdbuf (W^X EACCES on $PREFIX/bin)
            val guest =
                "env $envPrefix PYTHONUNBUFFERED=1 " +
                    "sh -c 'if [ -x /bin/bash ]; then exec /bin/bash /tmp/$name; " +
                    "else exec /bin/sh /tmp/$name; fi'"
            val cmd = arrayOf(
                bash, "-c",
                "exec python ${TermuxHostPaths.PROOT_DISTRO} login $prootName --shared-tmp -- $guest"
            )
            val env = HostCommandBuilder.envMap(appCtx, includeTerm = false).apply {
                put("PYTHONUNBUFFERED", "1")
            }
            val (exit, _) = ShellCommandRunner.runCaptureExit(
                appCtx, cmd, env,
                processHolder = activeProcess,
                onLine = { line ->
                    if (!isStale(gen) && line.isNotBlank()) {
                        log(phases, phaseIndex, onProgress, line)
                    }
                }
            )
            if (isStale(gen)) return false
            exit == 0
        } catch (e: Exception) {
            if (!isStale(gen)) {
                log(phases, phaseIndex, onProgress, "Guest script error: ${e.message}")
            }
            false
        }
    }

    private fun runChrootGuestBlocking(
        scriptBody: String,
        user: String,
        phases: List<BaseDesktopInstallPlan.Phase>,
        phaseIndex: Int,
        onProgress: (Progress) -> Unit,
        chrootPath: String = com.ivarna.fluxlinux.core.root.ChrootPaths.CHROOT_PATH
    ): Int {
        // captureInChroot already base64-wraps once via fluxlinux_chroot b64.
        // Nested echo|base64 is redundant and bloated host su -c strings.
        // guest_b64 prefers /bin/bash when present, else /bin/sh (Alpine minirootfs).
        val body = "export PYTHONUNBUFFERED=1\n$scriptBody"
        val result = RootShell.captureInChroot(
            body,
            user = user,
            context = appCtx,
            chrootPath = chrootPath,
            timeoutMs = 0L,
            onLine = { line ->
                if (line.isNotBlank()) log(phases, phaseIndex, onProgress, line)
            },
            processHolder = activeProcess
        )
        return result.exitCode
    }

    // ── progress helpers ────────────────────────────────────────────────────

    private fun enter(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        onProgress: (Progress) -> Unit,
        detail: String
    ) {
        val p = phases[index]
        val pct = weightedPercent(phases, index, 0f)
        lastOverallPercent = pct
        lastPhasePercent = null
        lastPhaseIndex = index
        lastDetail = detail
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = pct,
                detail = detail,
                logLine = "── ${p.label}: $detail"
            )
        )
    }

    private fun completePhase(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        onProgress: (Progress) -> Unit,
        detail: String
    ) {
        val p = phases[index]
        val pct = weightedPercent(phases, index, 1f)
        lastOverallPercent = pct
        lastPhasePercent = 100
        lastPhaseIndex = index
        lastDetail = detail
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = pct,
                phasePercent = 100,
                detail = detail,
                logLine = "✓ $detail"
            )
        )
    }

    private fun updateFraction(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        frac: Float?,
        onProgress: (Progress) -> Unit,
        detail: String
    ) {
        val p = phases[index]
        val fraction = frac?.coerceIn(0f, 1f)
        val pct = weightedPercent(phases, index, fraction ?: 0f)
        lastOverallPercent = pct
        lastPhasePercent = fraction?.let { (it * 100).toInt() }
        lastPhaseIndex = index
        lastDetail = detail
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = pct,
                phasePercent = lastPhasePercent,
                detail = detail
            )
        )
    }

    private fun log(
        phases: List<BaseDesktopInstallPlan.Phase>,
        index: Int,
        onProgress: (Progress) -> Unit,
        line: String
    ) {
        val p = phases.getOrNull(index) ?: return
        // Keep prior overall percent — pure log lines must not snap the bar mid-phase.
        post(
            onProgress,
            Progress(
                phaseId = p.id,
                phaseLabel = p.label,
                phaseIndex = index,
                phaseCount = phases.size,
                overallPercent = lastOverallPercent.coerceIn(0, 100),
                phasePercent = lastPhasePercent,
                detail = lastDetail.ifBlank { p.label },
                logLine = line
            )
        )
    }

    private fun postSuccess(
        onProgress: (Progress) -> Unit,
        phases: List<BaseDesktopInstallPlan.Phase>
    ) {
        val last = phases.last()
        lastOverallPercent = 100
        lastPhasePercent = 100
        post(
            onProgress,
            Progress(
                phaseId = last.id,
                phaseLabel = "Complete",
                phaseIndex = phases.size - 1,
                phaseCount = phases.size,
                overallPercent = 100,
                phasePercent = 100,
                detail = "Environment ready",
                finished = true
            )
        )
    }

    private fun postFail(
        onProgress: (Progress) -> Unit,
        phases: List<BaseDesktopInstallPlan.Phase>,
        message: String
    ) {
        val index = lastPhaseIndex.coerceIn(0, (phases.size - 1).coerceAtLeast(0))
        val p = phases.getOrNull(index)
        post(
            onProgress,
            Progress(
                phaseId = p?.id ?: "ERR",
                phaseLabel = "Failed",
                phaseIndex = index,
                phaseCount = phases.size.coerceAtLeast(1),
                overallPercent = lastOverallPercent,
                phasePercent = lastPhasePercent,
                detail = message,
                failed = true,
                finished = true,
                errorMessage = message,
                logLine = "ERROR: $message"
            )
        )
    }

    private fun postCancelled(
        onProgress: (Progress) -> Unit,
        phases: List<BaseDesktopInstallPlan.Phase>
    ) {
        postFail(onProgress, phases, "Install cancelled")
    }

    private fun weightedPercent(
        phases: List<BaseDesktopInstallPlan.Phase>,
        currentIndex: Int,
        fractionInPhase: Float
    ): Int {
        val total = phases.sumOf { it.weight }.coerceAtLeast(1)
        var done = 0
        for (i in phases.indices) {
            if (i < currentIndex) done += phases[i].weight
            else if (i == currentIndex) done += (phases[i].weight * fractionInPhase).toInt()
        }
        return ((done * 100) / total).coerceIn(0, 100)
    }

    private fun post(onProgress: (Progress) -> Unit, progress: Progress) {
        main.post {
            onProgress(progress)
            updateInstallNotification(progress)
        }
    }

    private fun updateInstallNotification(progress: Progress) {
        val label = when {
            progress.failed -> "Install failed"
            progress.finished -> "Install complete"
            progress.phaseLabel.isNotBlank() -> progress.phaseLabel
            else -> "Installing…"
        }
        val detail = progress.detail.ifBlank { "Base desktop setup" }
        val percent = progress.overallPercent
        // Avoid spamming NotificationManager on every log line
        if (!progress.finished &&
            !progress.failed &&
            percent == lastNotifPercent &&
            label == lastNotifLabel
        ) {
            return
        }
        lastNotifPercent = percent
        lastNotifLabel = label
        try {
            BaseInstallService.update(
                appCtx,
                title = "FluxLinux — $label",
                text = detail,
                percent = percent
            )
        } catch (e: Exception) {
            Log.w(TAG, "FGS update failed: ${e.message}")
        }
    }

    private fun isProotXfceInstalled(ctx: Context, prootName: String = "debian"): Boolean {
        val root = File(ctx.filesDir, "usr/var/lib/proot-distro/containers/$prootName/rootfs")
        return File(root, "usr/bin/startxfce4").exists() ||
            File(root, "usr/sbin/startxfce4").exists()
    }

    companion object {
        private const val TAG = "OnboardingInstall"
    }
}
