import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}


group = "cn.netdiscovery.monica"
version = "${rootProject.extra["app.version"]}"

val mOutputDir = project.buildDir.resolve("output")

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven( "https://jitpack.io" )
}

val osName = System.getProperty("os.name")
val targetOs = when {
    osName == "Mac OS X" -> "macos"
    osName.startsWith("Win") -> "windows"
    osName.startsWith("Linux") -> "linux"
    else -> error("Unsupported OS: $osName")
}

val osArch = System.getProperty("os.arch")
var targetArch = when (osArch) {
    "x86_64", "amd64" -> "x64"
    "aarch64" -> "arm64"
    else -> error("Unsupported arch: $osArch")
}

val skikoVersion = "0.8.4"
val target = "${targetOs}-${targetArch}"
val resourcesRootDir = project.layout.projectDirectory.dir("resources").asFile
val bundledNodeVersion = providers.gradleProperty("bundledNodeVersion").orElse("20.18.0")
val stagedWebRuntimeDir = layout.buildDirectory.dir("generated/web-screenshot-runtime/$target")
val packagedWebRuntimeDir = layout.buildDirectory.dir("generated/web-screenshot-payload/$target")
val packagedWebRuntimeZip = layout.buildDirectory.file("generated/web-screenshot-payload/$target/runtime.zip")
val packagedWebRuntimeResourceDir = File(resourcesRootDir, "common/web-screenshot-runtime/$target")
val packagedWebRuntimeResourceZip = File(packagedWebRuntimeResourceDir, "runtime.zip")

fun getBundledNodeDistPlatform(): String = when (targetOs) {
    "macos" -> if (targetArch == "arm64") "darwin-arm64" else "darwin-x64"
    "windows" -> if (targetArch == "arm64") "win-arm64" else "win-x64"
    "linux" -> if (targetArch == "arm64") "linux-arm64" else "linux-x64"
    else -> error("Unsupported target OS for bundled Node.js: $targetOs")
}

fun getBundledNodeArchiveExtension(): String = if (targetOs == "windows") "zip" else "tar.gz"

fun getBundledNodeExtractedDirName(version: String): String =
    "node-v$version-${getBundledNodeDistPlatform()}"

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun findWebScreenshotRuntimeRoot(baseDir: File): File? {
    val candidates = listOf(
        baseDir,
        File(baseDir, "common")
    )

    return candidates.firstOrNull { candidate ->
        File(candidate, "web-screenshot.js").exists()
    }
}

fun findBundledNodeExecutable(baseDir: File): File? {
    val candidates = if (targetOs == "windows") {
        listOf(
            File(baseDir, "$target/node/node.exe"),
            File(baseDir, "$target/node.exe")
        )
    } else {
        listOf(
            File(baseDir, "$target/node/bin/node"),
            File(baseDir, "$target/node/node"),
            File(baseDir, "$target/bin/node")
        )
    }

    return candidates.firstOrNull { it.exists() }
}

fun findBundledNodeExecutableInRuntime(runtimeRoot: File): File? {
    val candidates = if (targetOs == "windows") {
        listOf(
            File(runtimeRoot, "node/node.exe"),
            File(runtimeRoot, "node.exe")
        )
    } else {
        listOf(
            File(runtimeRoot, "node/bin/node"),
            File(runtimeRoot, "node/node"),
            File(runtimeRoot, "bin/node")
        )
    }
    return candidates.firstOrNull { it.exists() }
}

fun findPlaywrightBrowsersInRuntime(runtimeRoot: File): File? {
    val candidates = listOf(
        File(runtimeRoot, "node_modules/playwright-core/.local-browsers"),
        File(runtimeRoot, "ms-playwright")
    )
    return candidates.firstOrNull { it.exists() }
}

fun hasInstalledPlaywrightBrowsersInRuntime(runtimeRoot: File): Boolean {
    val browsersDir = findPlaywrightBrowsersInRuntime(runtimeRoot) ?: return false
    val entries = browsersDir.listFiles().orEmpty().filter { !it.name.startsWith(".") }
    val hasChromium = entries.any { it.name.startsWith("chromium-") }
    val hasHeadlessShell = entries.any { it.name.startsWith("chromium_headless_shell-") }
    val hasFfmpeg = entries.any { it.name.startsWith("ffmpeg-") }
    return hasChromium && hasHeadlessShell && hasFfmpeg
}

fun getNpmCliScript(nodeDir: File): File {
    val script = File(nodeDir, "lib/node_modules/npm/bin/npm-cli.js")
    if (!script.exists()) {
        throw GradleException("Bundled npm CLI not found at ${script.absolutePath}")
    }
    return script
}

fun getNpxCliScript(nodeDir: File): File {
    val directScript = File(nodeDir, "lib/node_modules/npm/bin/npx-cli.js")
    if (directScript.exists()) {
        return directScript
    }

    val fallbackScript = File(nodeDir, "lib/node_modules/npm/bin/npm-cli.js")
    if (fallbackScript.exists()) {
        return fallbackScript
    }

    throw GradleException("Bundled npx/npm CLI not found under ${nodeDir.absolutePath}")
}

fun getBundledNodeCommand(nodeDir: File): File {
    val executable = if (targetOs == "windows") {
        File(nodeDir, "node.exe")
    } else {
        File(nodeDir, "bin/node")
    }

    if (!executable.exists()) {
        throw GradleException("Bundled Node.js executable not found at ${executable.absolutePath}")
    }

    return executable
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":domain"))
                implementation(project(":config"))
                implementation(project(":imageprocess"))
                implementation(project(":opencv"))
                implementation(project(":i18n"))

                implementation ("org.jetbrains.kotlin:kotlin-reflect")

                // skiko
                implementation("org.jetbrains.skiko:skiko-awt-runtime-$target:$skikoVersion")

                // 缓存
                implementation("com.github.fengzhizi715.RxCache:core:${rootProject.extra["rxcache"]}")
                implementation("com.github.fengzhizi715.RxCache:okio:${rootProject.extra["rxcache"]}")
                implementation("com.github.fengzhizi715.RxCache:extension:${rootProject.extra["rxcache"]}")

                // di
                implementation("io.insert-koin:koin-compose:${rootProject.extra["koin.compose"]}")

                // color math
                implementation("com.github.ajalt.colormath:colormath-ext-jetpack-compose:${rootProject.extra["colormath"]}")

                // coroutines utils
                implementation ("com.github.fengzhizi715.Kotlin-Coroutines-Utils:common:${rootProject.extra["coroutines.utils"]}")
                // 为 Desktop/Swing 提供 Dispatchers.Main（绑定到 EDT）
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["kotlinx.coroutines.core.version"]}")

                // log config
                implementation("ch.qos.logback:logback-classic:${rootProject.extra["logback"]}")
                implementation("ch.qos.logback:logback-core:${rootProject.extra["logback"]}")
                implementation("ch.qos.logback:logback-access:${rootProject.extra["logback"]}")

                // okhttp-extension
                implementation("com.github.fengzhizi715.okhttp-extension:core:1.3.2")
                implementation("com.github.fengzhizi715.okhttp-logging-interceptor:core:v1.1.4")
                implementation ("com.squareup.okhttp3:okhttp:4.10.0")
                implementation ("com.google.code.gson:gson:2.10.1")
                implementation ("org.json:json:20240303")

                // generate gif
                implementation ("com.madgag:animated-gif-lib:1.4")

                // sqlite
                implementation("org.xerial:sqlite-jdbc:3.50.3.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

val verifyBundledWebScreenshotRuntime by tasks.registering {
    group = "verification"
    description = "Verify offline web screenshot payload resources for desktop packaging."

    doLast {
        val runtimeRoot = findWebScreenshotRuntimeRoot(resourcesRootDir)
            ?: throw GradleException(
                "Missing web screenshot runtime root. Expected web-screenshot.js under " +
                    "${resourcesRootDir.absolutePath} or ${File(resourcesRootDir, "common").absolutePath}."
            )

        val missing = mutableListOf<String>()

        if (!File(runtimeRoot, "package.json").exists()) {
            missing += "${runtimeRoot.absolutePath}/package.json"
        }
        val payloadZip = packagedWebRuntimeResourceZip
        if (!payloadZip.exists()) {
            missing += payloadZip.absolutePath
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Bundled web screenshot runtime is incomplete for target '$target'.")
                    appendLine("Missing resources:")
                    missing.forEach { appendLine("- $it") }
                    appendLine("Suggested setup:")
                    appendLine("1. Prepare the offline runtime payload")
                    appendLine("   ./gradlew prepareBundledWebScreenshotRuntime")
                    appendLine("2. Re-run packaging")
                }
            )
        }
    }
}

tasks.matching {
    it.name in setOf(
        "createDistributable",
        "packageDistributionForCurrentOS",
        "packageDmg",
        "packageMsi",
        "packageExe",
        "packageRpm"
    )
}.configureEach {
    dependsOn(verifyBundledWebScreenshotRuntime)
}

val downloadBundledNode by tasks.registering {
    group = "distribution"
    description = "Download and unpack bundled Node.js into the staged offline runtime payload."

    val version = bundledNodeVersion.get()
    val distPlatform = getBundledNodeDistPlatform()
    val archiveExtension = getBundledNodeArchiveExtension()
    val archiveFile = layout.buildDirectory.file("tmp/bundled-node/node-v$version-$distPlatform.$archiveExtension")
    val extractDir = layout.buildDirectory.dir("tmp/bundled-node/extracted/$target")
    val targetNodeDir = stagedWebRuntimeDir.map { it.dir("node").asFile }

    doLast {
        val versionValue = bundledNodeVersion.get()
        val runtimeRoot = stagedWebRuntimeDir.get().asFile
        val nodeRoot = targetNodeDir.get()
        val versionMarker = File(nodeRoot, ".bundled-node-version")
        val existingNode = findBundledNodeExecutableInRuntime(runtimeRoot)
        if (existingNode != null && versionMarker.exists() && versionMarker.readText().trim() == versionValue) {
            logger.lifecycle("Bundled Node.js already prepared at ${nodeRoot.absolutePath}, skipping download.")
            return@doLast
        }

        val downloadUrl = "https://nodejs.org/dist/v$versionValue/${getBundledNodeExtractedDirName(versionValue)}.$archiveExtension"
        val archive = archiveFile.get().asFile
        val extractedRoot = extractDir.get().asFile

        archive.parentFile.mkdirs()
        extractedRoot.mkdirs()

        logger.lifecycle("Downloading bundled Node.js from $downloadUrl")
        URL(downloadUrl).openStream().use { input ->
            FileOutputStream(archive).use { output ->
                input.copyTo(output)
            }
        }

        project.delete(extractedRoot)
        extractedRoot.mkdirs()

        copy {
            from(
                if (archiveExtension == "zip") {
                    zipTree(archive)
                } else {
                    tarTree(resources.gzip(archive))
                }
            )
            into(extractedRoot)
        }

        val extractedNodeDir = File(extractedRoot, getBundledNodeExtractedDirName(versionValue))
        if (!extractedNodeDir.exists()) {
            throw GradleException("Downloaded Node.js archive did not contain ${extractedNodeDir.absolutePath}")
        }

        project.delete(nodeRoot)
        nodeRoot.parentFile.mkdirs()

        copy {
            from(extractedNodeDir)
            into(nodeRoot)
        }

        if (targetOs != "windows") {
            listOf(
                File(nodeRoot, "bin/node"),
                File(nodeRoot, "node")
            ).filter { it.exists() }.forEach { file ->
                file.setExecutable(true)
            }
        }

        versionMarker.writeText("$versionValue\n")

        logger.lifecycle("Bundled Node.js prepared at ${nodeRoot.absolutePath}")
    }
}

val installBundledPlaywrightRuntime by tasks.registering {
    group = "distribution"
    description = "Stage the offline Playwright runtime that will be extracted on first use."

    dependsOn(downloadBundledNode)
    val sourceRuntimeRoot = findWebScreenshotRuntimeRoot(resourcesRootDir) ?: resourcesRootDir
    val runtimeRoot = stagedWebRuntimeDir.get().asFile
    val lockFile = File(sourceRuntimeRoot, "package-lock.json")
    val packageFile = File(sourceRuntimeRoot, "package.json")
    val runtimeStampFile = File(runtimeRoot, ".playwright-runtime-stamp")

    inputs.file(packageFile)

    doLast {
        runtimeRoot.mkdirs()

        copy {
            from(File(sourceRuntimeRoot, "web-screenshot.js"))
            from(packageFile)
            if (lockFile.exists()) {
                from(lockFile)
            }
            into(runtimeRoot)
        }

        val nodeDir = findBundledNodeExecutableInRuntime(runtimeRoot)?.parentFile?.let { parent ->
            if (targetOs == "windows") parent else parent.parentFile
        } ?: throw GradleException("Bundled Node.js is missing. Run downloadBundledNode first.")

        val lockHash = if (lockFile.exists()) sha256(lockFile) else sha256(packageFile)
        val expectedStamp = buildString {
            append("node=")
            append(bundledNodeVersion.get())
            append('\n')
            append("lock=")
            append(lockHash)
            append('\n')
            append("target=")
            append(target)
            append('\n')
        }

        if (runtimeStampFile.exists() &&
            runtimeStampFile.readText() == expectedStamp &&
            File(runtimeRoot, "node_modules/playwright/package.json").exists() &&
            File(runtimeRoot, "node_modules/playwright-core/package.json").exists() &&
            hasInstalledPlaywrightBrowsersInRuntime(runtimeRoot)
        ) {
            logger.lifecycle("Bundled Playwright runtime already installed in ${runtimeRoot.absolutePath}, skipping npm install.")
            return@doLast
        }

        val nodeExecutable = getBundledNodeCommand(nodeDir)
        val npmCli = getNpmCliScript(nodeDir)
        val npxCli = getNpxCliScript(nodeDir)

        logger.lifecycle("Installing web screenshot npm dependencies in ${runtimeRoot.absolutePath}")
        exec {
            workingDir = runtimeRoot
            executable = nodeExecutable.absolutePath
            args = listOf(npmCli.absolutePath, "ci")
            environment("PLAYWRIGHT_BROWSERS_PATH", "0")
        }

        logger.lifecycle("Installing bundled Chromium in ${runtimeRoot.absolutePath}")
        val playwrightInstallArgs = if (npxCli.name == "npm-cli.js") {
            listOf(npxCli.absolutePath, "exec", "playwright", "install", "chromium")
        } else {
            listOf(npxCli.absolutePath, "playwright", "install", "chromium")
        }
        exec {
            workingDir = runtimeRoot
            executable = nodeExecutable.absolutePath
            args = playwrightInstallArgs
            environment("PLAYWRIGHT_BROWSERS_PATH", "0")
        }

        runtimeStampFile.writeText(expectedStamp)
    }
}

val packageBundledWebScreenshotRuntime by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Create the offline runtime zip that the app will extract on first screenshot use."

    dependsOn(installBundledPlaywrightRuntime)
    from(stagedWebRuntimeDir)
    destinationDirectory.set(packagedWebRuntimeDir)
    archiveFileName.set("runtime.zip")
}

val cleanupLegacyWebScreenshotPackagingResources by tasks.registering {
    group = "distribution"
    description = "Remove legacy bundled web screenshot executables from source resources so packaging stays close to main."

    doNotTrackState("This task cleans legacy packaging artifacts from source resources in place.")

    doLast {
        listOf(
            File(resourcesRootDir, "node_modules"),
            File(resourcesRootDir, "common/node_modules"),
            File(resourcesRootDir, "ms-playwright"),
            File(resourcesRootDir, "common/ms-playwright"),
            File(resourcesRootDir, ".playwright-runtime-stamp"),
            File(resourcesRootDir, "macos-arm64/node"),
            File(resourcesRootDir, "macos-x64/node"),
            File(resourcesRootDir, "linux-arm64/node"),
            File(resourcesRootDir, "linux-x64/node"),
            File(resourcesRootDir, "windows/node")
        ).forEach { path ->
            if (path.exists()) {
                project.delete(path)
            }
        }
    }
}

val preparePackagedResources by tasks.registering {
    group = "distribution"
    description = "Write the offline web screenshot payload into standard resources layout."

    dependsOn(packageBundledWebScreenshotRuntime, cleanupLegacyWebScreenshotPackagingResources)
    doNotTrackState("This task prepares standard source resources in place for Compose Desktop packaging.")

    doLast {
        packagedWebRuntimeResourceDir.mkdirs()
        copy {
            from(packagedWebRuntimeZip)
            into(packagedWebRuntimeResourceDir)
        }
    }
}

val prepareBundledWebScreenshotRuntime by tasks.registering {
    group = "distribution"
    description = "Prepare the offline web screenshot payload for packaging."

    dependsOn(preparePackagedResources)
}

val cleanNativeDistributionOutputs by tasks.registering {
    group = "distribution"
    description = "Remove stale native distribution outputs so old app resources are not reused across packaging runs."

    doNotTrackState("This task deletes generated packaging outputs before creating new native bundles.")

    doLast {
        listOf(
            File(mOutputDir, "main/app"),
            File(mOutputDir, "main/dmg"),
            File(mOutputDir, "main/msi"),
            File(mOutputDir, "main/exe"),
            File(mOutputDir, "main/rpm")
        ).forEach { dir ->
            if (dir.exists()) {
                project.delete(dir)
            }
        }
    }
}

verifyBundledWebScreenshotRuntime.configure {
    dependsOn(prepareBundledWebScreenshotRuntime)
}

val packageCurrentOsWithBundledWebRuntime by tasks.registering {
    group = "distribution"
    description = "Prepare bundled web screenshot runtime and package for the current OS."

    val packageTaskName = when (targetOs) {
        "macos" -> "packageDmg"
        "windows" -> "packageExe"
        "linux" -> "packageRpm"
        else -> error("Unsupported target OS: $targetOs")
    }

    dependsOn(packageTaskName)
}

val currentOsPackageTaskName = when (targetOs) {
    "macos" -> "packageDmg"
    "windows" -> "packageExe"
    "linux" -> "packageRpm"
    else -> error("Unsupported target OS: $targetOs")
}

tasks.matching { it.name == currentOsPackageTaskName }.configureEach {
    dependsOn(cleanNativeDistributionOutputs, prepareBundledWebScreenshotRuntime)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
        nativeDistributions {
            outputBaseDir.set(mOutputDir)   //build/output
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Rpm)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            packageName = "Monica-$targetArch"
            packageVersion = "${rootProject.extra["app.version"]}"
            description = "Monica is a cross-platform image editor"
            copyright = "© 2024 Tony Shen. All rights reserved."

            jvmArgs += listOf("-Xms4G","-Xmx4G")
            jvmArgs += listOf("-Dlogback.debug=true")

            includeAllModules = true    //包含所有模块

            macOS {
                bundleID = "cn.netdiscovery.monica"
                dockName = "monica"
            }

            windows {
                console = false    // 为应用程序添加一个控制台启动器
                shortcut = true    // 桌面快捷方式
                dirChooser = true  // 允许在安装过程中自定义安装路径
                perUserInstall = false   //允许在每个用户的基础上安装应用程序
                menuGroup = "start-menu-group"
                upgradeUuid = "b329caf3-6681-49b9-98d0-adb34d32e130"
                iconFile.set(project.file("src/jvmMain/resources/images/launcher.ico"))
            }
        }
    }
}
