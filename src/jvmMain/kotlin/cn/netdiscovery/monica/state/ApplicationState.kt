package cn.netdiscovery.monica.state

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import cn.netdiscovery.monica.config.KEY_GENERAL_SETTINGS
import cn.netdiscovery.monica.config.category.ConfigCategoryManager
import cn.netdiscovery.monica.domain.DecodedPreviewImage
import cn.netdiscovery.monica.i18n.LocalizationManager
import cn.netdiscovery.monica.domain.GeneralSettings
import cn.netdiscovery.monica.opencv.ImageProcess
import cn.netdiscovery.monica.ui.theme.ColorTheme
import cn.netdiscovery.monica.ui.theme.ThemeManager
import cn.netdiscovery.monica.utils.ImageFormat
import kotlinx.coroutines.CoroutineScope
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 *
 * @FileName:
 *          cn.netdiscovery.monica.state.ApplicationState
 * @author: Tony Shen
 * @date: 2024/4/26 10:42
 * @version: V1.0 <描述当前版本功能>
 */
const val ZoomPreviewStatus: Int = 1
const val BlurStatus: Int = 2
const val MosaicStatus: Int = 3
const val DoodleStatus: Int = 4
const val ShapeDrawingStatus: Int = 5
const val ColorPickStatus: Int = 6
const val GenerateGifStatus: Int = 7
const val FlipStatus: Int = 8
const val RotateStatus: Int = 9
const val ResizeStatus: Int = 10
const val ShearingStatus: Int = 11
const val CropSizeStatus: Int = 12
const val CompressionStatus: Int = 13

const val ColorCorrectionStatus: Int = 14
const val FilterStatus: Int = 15


const val OpenCVDebugStatus: Int = 16
const val FaceDetectStatus: Int = 17
const val SketchDrawingStatus: Int = 18
const val FaceSwapStatus: Int = 19
const val CartoonStatus: Int = 20
const val WebScreenshotStatus: Int = 21



@Composable
fun rememberApplicationState(
    scope: CoroutineScope,
    trayState: TrayState
) = remember {
    ApplicationState(scope, trayState)
}

class ApplicationState(val scope:CoroutineScope,
                       val trayState: TrayState) {

    lateinit var window: ComposeWindow

    var rawImage: BufferedImage? by mutableStateOf(null)
    var currentImage: BufferedImage? by mutableStateOf( rawImage )
    var rawImageFile: File? = null
    var rawImageFormat: ImageFormat? = null
    var nativeImageInfo: DecodedPreviewImage? = null
    var nativeFullImageProcessed: Boolean = false

    // 表示用于点击了哪个功能
    var currentStatus by mutableStateOf(0)

    var isGeneralSettings by mutableStateOf(false)
    var isBasic by mutableStateOf(false)
    var isColorCorrection by mutableStateOf(false)
    var isFilter by mutableStateOf(false)
    var isAI by mutableStateOf(false)
    var isCompression by mutableStateOf(false)

    var isShowPreviewWindow by mutableStateOf(false)

    private val queue: LinkedBlockingDeque<BufferedImage> = LinkedBlockingDeque(40)

    // 通用输出框的颜色
    private val defaultSettings = GeneralSettings(255, 255, 255, 512, 50, "", "", "", "LIGHT")
    
    private fun loadGeneralSettings(): GeneralSettings {
        return ConfigCategoryManager.load(KEY_GENERAL_SETTINGS, defaultSettings)
    }
    
    private val initialSettings = loadGeneralSettings()
    
    var outputBoxRText by mutableStateOf(initialSettings.outputBoxR)
    var outputBoxGText by mutableStateOf(initialSettings.outputBoxG)
    var outputBoxBText by mutableStateOf(initialSettings.outputBoxB)

    var sizeText by mutableStateOf(initialSettings.size)
    var maxHistorySizeText by mutableStateOf(initialSettings.maxHistorySize)
    var deepSeekApiKeyText by mutableStateOf(initialSettings.deepSeekApiKey)
    var geminiApiKeyText by mutableStateOf(initialSettings.geminiApiKey)
    var algorithmUrlText by mutableStateOf(initialSettings.algorithmUrl)

    // 主题设置 - 作为唯一的状态源
    var currentTheme by mutableStateOf(
        initialSettings.themeId.let { themeId ->
            ThemeManager.getThemeById(themeId) ?: ColorTheme.LIGHT
        }
    )
    
    // 初始化时同步到ThemeManager
    init {
        ThemeManager.setCurrentTheme(currentTheme)
    }

    fun toOutputBoxScalar() = intArrayOf(outputBoxBText, outputBoxGText, outputBoxRText)

    fun saveGeneralSettings() {
        val settings = GeneralSettings(
            outputBoxRText, outputBoxGText, outputBoxBText, 
            sizeText, maxHistorySizeText, 
            deepSeekApiKeyText, geminiApiKeyText, algorithmUrlText, 
            currentTheme.getThemeId()
        )
        ConfigCategoryManager.save(KEY_GENERAL_SETTINGS, settings)
    }

    /**
     * 切换主题 - 确保状态同步
     */
    fun setTheme(theme: ColorTheme) {
        currentTheme = theme
        ThemeManager.setCurrentTheme(theme)
        // 立即保存到缓存
        saveGeneralSettings()
    }

    /**
     * 获取当前主题
     */
    fun getCurrentThemeValue(): ColorTheme = currentTheme

    fun getLastImage():BufferedImage? = queue.pollFirst(1, TimeUnit.SECONDS)

    fun addQueue(bufferedImage: BufferedImage) {
        queue.putFirst(bufferedImage)
    }

    fun clearQueue() {
        queue.clear()
    }

    fun togglePreviewWindow(isShow: Boolean = true) {
        isShowPreviewWindow = isShow
    }

    /**
     * 弹出新的页面，更新 currentStatus 状态
     * @param status 更新为当前的状态
     */
    fun togglePreviewWindowAndUpdateStatus(status:Int) {
        currentStatus = status
        isShowPreviewWindow = true
    }

    /**
     * 关闭当前弹出的页面
     */
    fun closePreviewWindow() {
        resetCurrentStatus()
        togglePreviewWindow(false)
    }

    /**
     * 清空了当前的状态
     */
    fun resetCurrentStatus() {
        currentStatus = 0
    }

    fun clearImage() {
        this.rawImage = null
        this.currentImage = null
        this.rawImageFile = null
        this.rawImageFormat = null
        this.nativeFullImageProcessed = false

        val nativePtr = this.nativeImageInfo?.nativePtr
        if (nativePtr!=null && nativePtr !=0L) {
            ImageProcess.deletePyramidImage(nativePtr)
        }
        this.nativeImageInfo = null
    }

    fun showTray(
        msg: String,
        title: String = LocalizationManager.getString("notification"),
        type: Notification.Type = Notification.Type.Info
    ) {
        val notification = Notification(title, msg, type)
        trayState.sendNotification(notification)
    }
}
