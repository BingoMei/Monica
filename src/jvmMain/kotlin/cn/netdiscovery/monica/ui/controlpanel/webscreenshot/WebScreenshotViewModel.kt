package cn.netdiscovery.monica.ui.controlpanel.webscreenshot

import cn.netdiscovery.monica.state.ApplicationState
import cn.netdiscovery.monica.utils.WebScreenshotOptions
import cn.netdiscovery.monica.utils.checkNodeInstalled
import cn.netdiscovery.monica.utils.loadWebScreenshotToState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 网页截图 ViewModel
 * 
 * @author: Tony Shen
 * @date: 2026/01/12
 * @version: V1.0
 */
class WebScreenshotViewModel {
    
    private val logger: Logger = LoggerFactory.getLogger(WebScreenshotViewModel::class.java)

    /**
     * 捕获网页截图
     */
    fun captureWebScreenshot(
        state: ApplicationState,
        url: String,
        options: WebScreenshotOptions = WebScreenshotOptions()
    ) {
        logger.info("开始捕获网页截图: $url")
        loadWebScreenshotToState(state, url, options)
    }

    /**
     * 检查 Node.js 环境
     */
    fun checkEnvironment(): Boolean {
        return checkNodeInstalled()
    }
}
