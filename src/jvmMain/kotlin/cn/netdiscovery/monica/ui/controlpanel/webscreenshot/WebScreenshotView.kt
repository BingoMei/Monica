package cn.netdiscovery.monica.ui.controlpanel.webscreenshot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.netdiscovery.monica.exception.ErrorSeverity
import cn.netdiscovery.monica.exception.ErrorType
import cn.netdiscovery.monica.exception.showError
import cn.netdiscovery.monica.state.ApplicationState
import cn.netdiscovery.monica.ui.i18n.rememberI18nState
import cn.netdiscovery.monica.ui.widget.*
import cn.netdiscovery.monica.utils.WebScreenshotOptions
import cn.netdiscovery.monica.utils.checkNodeInstalled
import org.koin.compose.koinInject

/**
 * 网页截图视图
 * 
 * @author: Tony Shen
 * @date: 2026/01/12
 * @version: V1.0
 */
@Composable
fun webScreenshot(state: ApplicationState) {
    val i18nState = rememberI18nState()
    val viewModel: WebScreenshotViewModel = koinInject()

    var urlText by remember { mutableStateOf("https://") }
    var fullPage by remember { mutableStateOf(true) }
    var waitUntil by remember { mutableStateOf("networkidle") }
    var timeoutText by remember { mutableStateOf("30000") }
    var viewportWidthText by remember { mutableStateOf("") }
    var viewportHeightText by remember { mutableStateOf("") }
    var clarityScaleText by remember { mutableStateOf("2.0") }
    
    var nodeInstalled by remember { mutableStateOf(false) }
    
    // 检查 Node.js 环境
    LaunchedEffect(Unit) {
        nodeInstalled = checkNodeInstalled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        title(
            modifier = Modifier.padding(bottom = 8.dp),
            text = i18nState.getString("web_screenshot"),
            color = MaterialTheme.colors.onBackground
        )

        // Node.js 环境检查提示
        if (!nodeInstalled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = i18nState.getString("nodejs_not_installed"),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = i18nState.getString("please_install_nodejs"),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }

        // URL 输入框
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = i18nState.getString("website_url"),
                    style = MaterialTheme.typography.subtitle1
                )
                
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18nState.getString("enter_url")) },
                    placeholder = { Text("https://example.com") },
                    singleLine = true
                )

                // 截图选项
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = i18nState.getString("screenshot_options"),
                    style = MaterialTheme.typography.subtitle2
                )

                // 全页截图选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = i18nState.getString("full_page_screenshot"))
                    Switch(
                        checked = fullPage,
                        onCheckedChange = { fullPage = it }
                    )
                }

                // 等待策略
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = i18nState.getString("wait_until"))
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { expanded = true },
                            modifier = Modifier.width(150.dp)
                        ) {
                            Text(waitUntil, style = MaterialTheme.typography.body2)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            listOf("load", "domcontentloaded", "networkidle").forEach { option ->
                                DropdownMenuItem(onClick = {
                                    waitUntil = option
                                    expanded = false
                                }) {
                                    Text(option)
                                }
                            }
                        }
                    }
                }

                // 超时设置
                basicTextFieldWithTitle(
                    titleText = i18nState.getString("timeout_ms"),
                    value = timeoutText,
                    width = 120.dp,
                    onValueChange = { timeoutText = it }
                )

                // 视口尺寸（可选）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    basicTextFieldWithTitle(
                        titleText = i18nState.getString("viewport_width"),
                        value = viewportWidthText,
                        width = 100.dp,
                        onValueChange = { viewportWidthText = it }
                    )
                    basicTextFieldWithTitle(
                        titleText = i18nState.getString("viewport_height"),
                        value = viewportHeightText,
                        width = 100.dp,
                        onValueChange = { viewportHeightText = it }
                    )
                }

                basicTextFieldWithTitle(
                    titleText = i18nState.getString("screenshot_clarity"),
                    value = clarityScaleText,
                    width = 120.dp,
                    onValueChange = { clarityScaleText = it }
                )
            }
        }

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (!nodeInstalled) {
                        showError(
                            ErrorType.NETWORK_ERROR,
                            ErrorSeverity.MEDIUM,
                            i18nState.getString("nodejs_not_installed"),
                            i18nState.getString("please_install_nodejs")
                        )
                        return@Button
                    }

                    if (urlText.isBlank() || !urlText.startsWith("http")) {
                        showError(
                            ErrorType.VALIDATION_ERROR,
                            ErrorSeverity.LOW,
                            i18nState.getString("invalid_url"),
                            i18nState.getString("please_enter_valid_url")
                        )
                        return@Button
                    }

                    val clarityScale = clarityScaleText.toDoubleOrNull()
                    if (clarityScale == null || clarityScale <= 0.0 || clarityScale > 4.0) {
                        showError(
                            ErrorType.VALIDATION_ERROR,
                            ErrorSeverity.LOW,
                            i18nState.getString("invalid_screenshot_clarity"),
                            i18nState.getString("please_enter_valid_screenshot_clarity")
                        )
                        return@Button
                    }

                    val options = WebScreenshotOptions(
                        fullPage = fullPage,
                        waitUntil = waitUntil,
                        timeout = timeoutText.toLongOrNull() ?: 30000L,
                        viewportWidth = viewportWidthText.toIntOrNull(),
                        viewportHeight = viewportHeightText.toIntOrNull(),
                        deviceScaleFactor = clarityScale
                    )

                    viewModel.captureWebScreenshot(state, urlText.trim(), options)
                },
                modifier = Modifier.weight(1f),
                enabled = nodeInstalled && urlText.isNotBlank()
            ) {
                Text(i18nState.getString("capture_screenshot"))
            }

            OutlinedButton(
                onClick = {
                    urlText = "https://"
                    fullPage = true
                    waitUntil = "networkidle"
                    timeoutText = "30000"
                    viewportWidthText = ""
                    viewportHeightText = ""
                    clarityScaleText = "2.0"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(i18nState.getString("reset"))
            }
        }

        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = i18nState.getString("usage_tips"),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
