package cn.netdiscovery.monica.utils

import cn.netdiscovery.monica.config.arch
import cn.netdiscovery.monica.config.isLinux
import cn.netdiscovery.monica.config.isMac
import cn.netdiscovery.monica.config.isWindows
import cn.netdiscovery.monica.exception.ErrorSeverity
import cn.netdiscovery.monica.exception.ErrorType
import cn.netdiscovery.monica.exception.showError
import cn.netdiscovery.monica.state.ApplicationState
import cn.netdiscovery.monica.utils.extensions.launchWithSuspendLoading
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * 网页截图工具类
 * 
 * @author: Tony Shen
 * @date: 2026/01/12
 * @version: V1.0
 */
private val logger: Logger = LoggerFactory.getLogger(object : Any() {}.javaClass.enclosingClass)

private data class NodeRuntime(
    val executable: String,
    val bundled: Boolean
)

private data class BundledWebRuntime(
    val runtimeRoot: File,
    val scriptFile: File,
    val nodeFile: File?,
    val browsersDir: File?
)

/**
 * Cookie 数据类
 */
data class Cookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expires: Long? = null,
    val httpOnly: Boolean = false,
    val secure: Boolean = false,
    val sameSite: String? = null // "Strict", "Lax", "None"
)

/**
 * 网页截图配置
 */
data class WebScreenshotOptions(
    val fullPage: Boolean = true,
    val waitUntil: String = "networkidle", // load, domcontentloaded, networkidle
    val timeout: Long = 30000, // 30秒
    val viewportWidth: Int? = null,
    val viewportHeight: Int? = null,
    val deviceScaleFactor: Double = 2.0,
    val cookies: List<Cookie> = emptyList() // Cookie 列表
)

/**
 * 检查 Node.js 是否已安装
 */
private fun checkNodeInstalled(runtime: NodeRuntime): Boolean {
    return try {
        val process = ProcessBuilder(runtime.executable, "--version")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        val result = finished && process.exitValue() == 0
        process.destroy()
        result
    } catch (e: Exception) {
        logger.debug("Node.js 检查失败: ${runtime.executable}", e)
        false
    }
}

fun checkNodeInstalled(): Boolean = checkNodeInstalled(resolveNodeRuntime())

/**
 * 获取当前平台的资源目录名
 */
private fun getCurrentPlatformResourceDirName(): String? {
    val normalizedArch = arch.lowercase()
    return when {
        isMac && (normalizedArch == "aarch64" || normalizedArch == "arm64") -> "macos-arm64"
        isMac -> "macos-x64"
        isWindows -> "windows"
        isLinux && (normalizedArch == "aarch64" || normalizedArch == "arm64") -> "linux-arm64"
        isLinux -> "linux-x64"
        else -> null
    }
}

/**
 * 获取网页截图资源搜索目录
 */
private fun getWebScreenshotResourceDirs(): List<File> {
    val resourceDirs = buildList {
        val composeResourcesDir = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }

        composeResourcesDir?.let {
            add(it)
            add(File(it, "common"))
        }

        val projectResourcesDir = File("resources")
        add(projectResourcesDir)
        add(File(projectResourcesDir, "common"))
    }

    return resourceDirs
        .map { it.absoluteFile }
        .distinctBy { it.path }
}

/**
 * 在资源目录中查找文件
 */
private fun findResourceFile(relativePath: String): File? {
    getWebScreenshotResourceDirs().forEach { dir ->
        val file = File(dir, relativePath)
        if (file.exists()) {
            return file
        }
    }
    return null
}

private fun getUserWebRuntimeBaseDir(): File {
    val userHome = File(System.getProperty("user.home"))
    return when {
        isMac -> File(userHome, "Library/Application Support/Monica/web-screenshot-runtime")
        isWindows -> {
            val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            val baseDir = appData?.let(::File) ?: File(userHome, "AppData/Roaming")
            File(baseDir, "Monica/web-screenshot-runtime")
        }
        else -> {
            val xdgDataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            val baseDir = xdgDataHome?.let(::File) ?: File(userHome, ".local/share")
            File(baseDir, "Monica/web-screenshot-runtime")
        }
    }
}

private fun getUserWebRuntimeRoot(): File? {
    val platformDir = getCurrentPlatformResourceDirName() ?: return null
    return File(getUserWebRuntimeBaseDir(), platformDir)
}

private fun getOfflineRuntimePayloadZip(): File? {
    val platformDir = getCurrentPlatformResourceDirName() ?: return null
    return findResourceFile("web-screenshot-runtime/$platformDir/runtime.zip")
}

private fun getBundledNodeExecutable(runtimeRoot: File): File? {
    val candidates = if (isWindows) {
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

    candidates.forEach { nodeFile ->
        if (nodeFile.exists()) {
            if (!isWindows && !nodeFile.canExecute()) {
                nodeFile.setExecutable(true)
            }
            return nodeFile
        }
    }

    return null
}

private fun getBundledPlaywrightBrowsersPath(runtimeRoot: File): File? {
    val candidates = listOf(
        File(runtimeRoot, "node_modules/playwright-core/.local-browsers"),
        File(runtimeRoot, "ms-playwright")
    )

    candidates.forEach { browsersDir ->
        if (browsersDir.exists()) {
            return browsersDir
        }
    }

    return null
}

private fun isRuntimeReady(runtimeRoot: File): Boolean {
    val script = File(runtimeRoot, "web-screenshot.js")
    val packageJson = File(runtimeRoot, "package.json")
    val playwrightModule = File(runtimeRoot, "node_modules/playwright/package.json")
    return script.exists() && packageJson.exists() && playwrightModule.exists()
}

private fun computeRuntimePayloadStamp(payloadZip: File): String =
    "${payloadZip.length()}:${payloadZip.lastModified()}"

private fun restoreRuntimeExecutablePermissions(runtimeRoot: File) {
    if (isWindows) return

    runtimeRoot.walkTopDown()
        .filter { it.isFile }
        .forEach { file ->
            val relativePath = file.relativeTo(runtimeRoot).invariantSeparatorsPath
            val fileName = file.name
            val shouldBeExecutable =
                relativePath == "node/bin/node" ||
                    relativePath.endsWith("/headless_shell") ||
                    relativePath.endsWith("/chrome-headless-shell") ||
                    relativePath.endsWith("/Chromium") ||
                    relativePath.endsWith("/chrome") ||
                    fileName.startsWith("ffmpeg")

            if (shouldBeExecutable && !file.canExecute()) {
                file.setExecutable(true)
            }
        }
}

private fun extractOfflineRuntime(payloadZip: File, runtimeRoot: File) {
    val tempRoot = File(runtimeRoot.parentFile, "${runtimeRoot.name}.tmp-${System.currentTimeMillis()}")
    if (tempRoot.exists()) {
        tempRoot.deleteRecursively()
    }
    tempRoot.mkdirs()

    ZipInputStream(payloadZip.inputStream().buffered()).use { zipInput ->
        while (true) {
            val entry = zipInput.nextEntry ?: break
            val outputFile = File(tempRoot, entry.name)
            if (entry.isDirectory) {
                outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { output ->
                    zipInput.copyTo(output)
                }
            }
            zipInput.closeEntry()
        }
    }

    restoreRuntimeExecutablePermissions(tempRoot)
    File(tempRoot, ".payload-stamp").writeText(computeRuntimePayloadStamp(payloadZip))

    runtimeRoot.parentFile?.mkdirs()
    if (runtimeRoot.exists()) {
        runtimeRoot.deleteRecursively()
    }
    Files.move(tempRoot.toPath(), runtimeRoot.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

private fun getLegacyBundledRuntime(): BundledWebRuntime? {
    val resourceRoot = getWebScreenshotResourceDirs().firstOrNull { dir ->
        File(dir, "web-screenshot.js").exists()
    } ?: return null
    return BundledWebRuntime(
        runtimeRoot = resourceRoot,
        scriptFile = File(resourceRoot, "web-screenshot.js"),
        nodeFile = getBundledNodeExecutable(resourceRoot),
        browsersDir = getBundledPlaywrightBrowsersPath(resourceRoot)
    )
}

private fun ensureBundledWebRuntime(): BundledWebRuntime? {
    val payloadZip = getOfflineRuntimePayloadZip() ?: return getLegacyBundledRuntime()
    val runtimeRoot = getUserWebRuntimeRoot() ?: return getLegacyBundledRuntime()
    val expectedStamp = computeRuntimePayloadStamp(payloadZip)
    val stampFile = File(runtimeRoot, ".payload-stamp")
    val needsExtract = !isRuntimeReady(runtimeRoot) ||
        !stampFile.exists() ||
        stampFile.readText() != expectedStamp

    if (needsExtract) {
        logger.info("解压离线网页截图运行时到: ${runtimeRoot.absolutePath}")
        extractOfflineRuntime(payloadZip, runtimeRoot)
    }

    restoreRuntimeExecutablePermissions(runtimeRoot)

    return BundledWebRuntime(
        runtimeRoot = runtimeRoot,
        scriptFile = File(runtimeRoot, "web-screenshot.js"),
        nodeFile = getBundledNodeExecutable(runtimeRoot),
        browsersDir = getBundledPlaywrightBrowsersPath(runtimeRoot)
    )
}

private fun resolveNodeRuntime(): NodeRuntime {
    val bundledNode = ensureBundledWebRuntime()?.nodeFile
    return if (bundledNode != null) {
        logger.info("使用内置 Node.js: ${bundledNode.absolutePath}")
        NodeRuntime(bundledNode.absolutePath, bundled = true)
    } else {
        NodeRuntime("node", bundled = false)
    }
}

/**
 * 从剪贴板获取 URL
 */
fun getUrlFromClipboard(): String? {
    return try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val contents = clipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
            val text = contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
            val trimmed = text.trim()
            // 检查是否是有效的 URL
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                logger.info("从剪贴板获取到 URL: $trimmed")
                trimmed
            } else {
                logger.debug("剪贴板内容不是有效的 URL: $trimmed")
                null
            }
        } else {
            logger.debug("剪贴板中没有文本内容")
            null
        }
    } catch (e: Exception) {
        logger.error("读取剪贴板失败", e)
        null
    }
}

/**
 * 从剪贴板获取 Cookie
 * 支持两种格式：
 * 1. Netscape Cookie 格式（从浏览器扩展如 EditThisCookie 导出）
 * 2. JSON 格式（Playwright Cookie 格式）
 */
fun getCookiesFromClipboard(): List<Cookie> {
    return try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val contents = clipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
            val text = contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
            val trimmed = text.trim()
            
            // 尝试解析 JSON 格式（Playwright Cookie 格式）
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                try {
                    val jsonArray = JsonParser.parseString(trimmed)
                    if (jsonArray.isJsonArray) {
                        val cookies = mutableListOf<Cookie>()
                        jsonArray.asJsonArray.forEach { element ->
                            val obj = element.asJsonObject
                            cookies.add(
                                Cookie(
                                    name = obj.get("name")?.asString ?: "",
                                    value = obj.get("value")?.asString ?: "",
                                    domain = obj.get("domain")?.asString,
                                    path = obj.get("path")?.asString,
                                    expires = obj.get("expires")?.asLong,
                                    httpOnly = obj.get("httpOnly")?.asBoolean ?: false,
                                    secure = obj.get("secure")?.asBoolean ?: false,
                                    sameSite = obj.get("sameSite")?.asString
                                )
                            )
                        }
                        logger.info("从剪贴板解析到 ${cookies.size} 个 Cookie（JSON 格式）")
                        return cookies
                    }
                } catch (e: Exception) {
                    logger.debug("解析 JSON Cookie 失败，尝试 Netscape 格式", e)
                }
            }
            
            // 尝试解析 Netscape Cookie 格式
            val cookies = parseNetscapeCookies(trimmed)
            if (cookies.isNotEmpty()) {
                logger.info("从剪贴板解析到 ${cookies.size} 个 Cookie（Netscape 格式）")
                return cookies
            }
            
            emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        logger.error("读取剪贴板 Cookie 失败", e)
        emptyList()
    }
}

/**
 * 解析 Netscape Cookie 格式
 * 格式：domain	flag	path	secure	expiration	name	value
 */
private fun parseNetscapeCookies(text: String): List<Cookie> {
    val cookies = mutableListOf<Cookie>()
    val lines = text.lines()
    
    // 跳过注释行和标题行
    val dataLines = lines.filter { 
        val trimmed = it.trim()
        !trimmed.startsWith("#") && 
        trimmed.isNotEmpty() &&
        (trimmed.contains("\t") && trimmed.split("\t").size >= 7)
    }
    
    dataLines.forEach { line ->
        try {
            val parts = line.split("\t")
            if (parts.size >= 7) {
                val domain = parts[0].trim()
                val path = parts[2].trim()
                val secure = parts[3].trim() == "TRUE"
                val expiration = parts[4].trim().toLongOrNull()
                val name = parts[5].trim()
                val value = parts[6].trim()
                
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    cookies.add(
                        Cookie(
                            name = name,
                            value = value,
                            domain = domain.takeIf { it.isNotEmpty() },
                            path = path.takeIf { it.isNotEmpty() },
                            expires = expiration,
                            secure = secure
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.debug("解析 Cookie 行失败: $line", e)
        }
    }
    
    return cookies
}

/**
 * 捕获网页长截图
 * 
 * @param url 网页URL
 * @param options 截图选项
 * @return 截图结果，失败返回 Result.failure
 */
suspend fun captureWebPage(
    url: String,
    options: WebScreenshotOptions = WebScreenshotOptions()
): Result<BufferedImage> = withContext(Dispatchers.IO) {
    try {
        val bundledRuntime = ensureBundledWebRuntime()

        // 1. 检查 Node.js 环境
        val nodeRuntime = resolveNodeRuntime()
        if (!checkNodeInstalled(nodeRuntime)) {
            return@withContext Result.failure(
                IllegalStateException(
                    if (nodeRuntime.bundled) {
                        "内置 Node.js 不可用，请检查安装包中的运行时资源"
                    } else {
                        "未检测到 Node.js 环境，请先安装 Node.js"
                    }
                )
            )
        }

        // 2. 检查脚本文件是否存在
        val scriptFile = bundledRuntime?.scriptFile
        if (scriptFile == null || !scriptFile.exists()) {
            val errorPath = scriptFile?.absolutePath ?: "未知路径"
            return@withContext Result.failure(
                FileNotFoundException("网页截图脚本不存在: $errorPath。请确保 web-screenshot.js 文件在 resources 目录下。")
            )
        }

        // 3. 创建临时文件用于输出图片
        val tempImageFile = File.createTempFile("web-screenshot-", ".png")
        tempImageFile.deleteOnExit()

        // 4. 检查并安装依赖
        val workingDir = bundledRuntime?.runtimeRoot ?: scriptFile.parentFile ?: File(".")
        val packageJson = File(workingDir, "package.json")
        val nodeModules = File(workingDir, "node_modules")
        
        // 离线运行时应已自带依赖，这里只做完整性兜底检查
        if (packageJson.exists() && !nodeModules.exists()) {
            logger.warn("检测到离线网页截图运行时不完整: ${workingDir.absolutePath}")
            return@withContext Result.failure(
                IllegalStateException("离线网页截图运行时不完整，请重新安装应用或重新打包")
            )
        }
        
        // 5. 如果有 Cookie，保存到临时文件
        val cookiesFile = if (options.cookies.isNotEmpty()) {
            val tempCookiesFile = File.createTempFile("web-screenshot-cookies-", ".json")
            tempCookiesFile.deleteOnExit()
            
            // 转换为 Playwright Cookie 格式
            val gson = Gson()
            val cookieList = options.cookies.map { cookie ->
                val cookieObj = JsonObject()
                cookieObj.addProperty("name", cookie.name)
                cookieObj.addProperty("value", cookie.value)
                cookie.domain?.let { cookieObj.addProperty("domain", it) }
                cookie.path?.let { cookieObj.addProperty("path", it) }
                cookie.expires?.let { cookieObj.addProperty("expires", it) }
                cookieObj.addProperty("httpOnly", cookie.httpOnly)
                cookieObj.addProperty("secure", cookie.secure)
                cookie.sameSite?.let { cookieObj.addProperty("sameSite", it) }
                cookieObj
            }
            
            tempCookiesFile.writeText(gson.toJson(cookieList))
            logger.info("已保存 ${options.cookies.size} 个 Cookie 到临时文件: ${tempCookiesFile.absolutePath}")
            tempCookiesFile
        } else {
            null
        }

        // 6. 构建命令
        val command = mutableListOf<String>().apply {
            add(nodeRuntime.executable)
            add(scriptFile.absolutePath)
            add(url)
            add(tempImageFile.absolutePath)
            add("--fullPage=${options.fullPage}")
            add("--waitUntil=${options.waitUntil}")
            add("--timeout=${options.timeout}")
            options.viewportWidth?.let { add("--viewportWidth=$it") }
            options.viewportHeight?.let { add("--viewportHeight=$it") }
            add("--deviceScaleFactor=${options.deviceScaleFactor}")
            cookiesFile?.let { add("--cookiesFile=${it.absolutePath}") }
        }

        logger.info("执行网页截图命令: ${command.joinToString(" ")}")

        // 7. 执行进程
        val processBuilder = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)

        bundledRuntime?.browsersDir?.let { browsersDir ->
            processBuilder.environment()["PLAYWRIGHT_BROWSERS_PATH"] = browsersDir.absolutePath
            processBuilder.environment()["PLAYWRIGHT_SKIP_BROWSER_GC"] = "1"
        }

        val process = processBuilder.start()

        // 8. 并发消费输出，避免 stdout 管道写满导致子进程阻塞
        val output = StringBuffer()
        val outputReaderThread = thread(start = true, isDaemon = true, name = "web-screenshot-output-reader") {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        output.appendLine(line)
                        logger.debug("Node输出: $line")
                    }
                }
            } catch (e: IOException) {
                logger.debug("读取网页截图进程输出结束", e)
            }
        }

        // 9. 等待进程完成（带超时）
        val processTimeout = options.timeout + 10000 // 额外10秒缓冲
        val finished = process.waitFor(processTimeout, TimeUnit.MILLISECONDS)

        if (!finished) {
            process.destroyForcibly()
            process.inputStream.close()
            outputReaderThread.join(2000)
            tempImageFile.delete()
            cookiesFile?.delete()
            return@withContext Result.failure(
                java.util.concurrent.TimeoutException("网页截图超时（超过 ${processTimeout}ms）")
            )
        }

        outputReaderThread.join(2000)

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            logger.error("网页截图失败，退出码: $exitCode，输出: $output")
            tempImageFile.delete()
            cookiesFile?.delete()
            return@withContext Result.failure(
                RuntimeException("网页截图失败: $output")
            )
        }

        // 10. 清理 Cookie 临时文件
        cookiesFile?.delete()

        // 11. 读取图片文件
        if (!tempImageFile.exists() || tempImageFile.length() == 0L) {
            cookiesFile?.delete()
            return@withContext Result.failure(
                IOException("截图文件未生成或为空")
            )
        }

        val image = ImageIO.read(tempImageFile)
        if (image == null) {
            tempImageFile.delete()
            cookiesFile?.delete()
            return@withContext Result.failure(
                IOException("无法读取截图文件")
            )
        }

        // 12. 清理临时文件
        tempImageFile.delete()

        logger.info("网页截图成功，尺寸: ${image.width}x${image.height}")
        Result.success(image)

    } catch (e: Exception) {
        logger.error("网页截图异常", e)
        Result.failure(e)
    }
}

/**
 * 将网页截图加载到 ApplicationState
 */
fun loadWebScreenshotToState(
    state: ApplicationState,
    url: String,
    options: WebScreenshotOptions = WebScreenshotOptions()
) {
    state.scope.launchWithSuspendLoading {
        val result = captureWebPage(url, options)
        
        result.onSuccess { image ->
            try {
                logger.info("加载网页截图到应用状态")
                val currentImage = state.currentImage ?: state.rawImage
                if (currentImage != null) {
                    state.addQueue(currentImage)
                }
                state.clearImage()
                state.rawImage = image
                state.currentImage = image
                state.rawImageFile = null
                state.rawImageFormat = null
            } catch (e: Exception) {
                logger.error("加载网页截图失败", e)
                showError(
                    ErrorType.FILE_IO_ERROR,
                    ErrorSeverity.MEDIUM,
                    "加载截图失败",
                    "加载截图失败: ${e.message}"
                )
            }
        }.onFailure { error ->
            logger.error("网页截图失败", error)
            val userMessage: String = when (error) {
                is IllegalStateException -> {
                    if (error.message?.contains("Playwright 依赖未安装") == true) {
                        error.message ?: "Playwright 依赖未安装"
                    } else {
                        error.message ?: "未检测到 Node.js 环境"
                    }
                }
                is java.util.concurrent.TimeoutException -> "截图超时，请检查网络连接或稍后重试"
                is FileNotFoundException -> "网页截图脚本不存在，请检查安装"
                is RuntimeException -> {
                    val errorMsg = error.message ?: ""
                    if (errorMsg.contains("Cannot find module 'playwright'")) {
                        "Playwright 运行时缺失，请重新安装应用或重新打包离线运行时"
                    } else if (errorMsg.contains("加载 Cookie 失败")) {
                        "Cookie 加载失败，请检查 Cookie 格式、域名和有效期"
                    } else {
                        "网页截图失败: $errorMsg"
                    }
                }
                else -> {
                    val errorMsg = error.message ?: ""
                    if (errorMsg.contains("Cannot find module 'playwright'")) {
                        "Playwright 运行时缺失，请重新安装应用或重新打包离线运行时"
                    } else if (errorMsg.contains("加载 Cookie 失败")) {
                        "Cookie 加载失败，请检查 Cookie 格式、域名和有效期"
                    } else {
                        "网页截图失败: $errorMsg"
                    }
                }
            }
            showError(
                ErrorType.NETWORK_ERROR,
                ErrorSeverity.MEDIUM,
                "网页截图失败",
                userMessage
            )
        }
    }
}
