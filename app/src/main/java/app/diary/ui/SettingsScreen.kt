package app.diary.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diary.BuildConfig
import app.diary.export.DiaryExporter
import app.diary.localapi.LocalDiaryServer
import app.diary.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val appContext: Context,
    private val settings: AppSettings,
    private val exporter: DiaryExporter,
    private val localServer: LocalDiaryServer,
) : ViewModel() {
    var hasUserKey by mutableStateOf(settings.hasUserKey())
        private set
    var hasBuildKey by mutableStateOf(settings.hasBuildKey())
        private set
    var updateUrl by mutableStateOf(settings.updateUrl())
    var localEnabled by mutableStateOf(settings.localApiEnabled() && localServer.running)
        private set
    var token by mutableStateOf(settings.token())
        private set
    var exporting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    fun consumeMessage() {
        message = null
    }

    fun saveApiKey(value: String) {
        viewModelScope.launch {
            try {
                settings.setApiKey(value)
                hasUserKey = true
                message = "密钥已保存在这台设备"
            } catch (error: Exception) {
                message = error.message ?: "密钥没有保存"
            }
        }
    }

    fun clearApiKey() {
        settings.clearApiKey()
        hasUserKey = false
        message = if (settings.hasBuildKey()) "已清除设备上的密钥，将使用构建时注入的密钥" else "已清除密钥"
    }

    fun saveUpdateUrl(value: String) {
        settings.setUpdateUrl(value)
        updateUrl = settings.updateUrl()
        message = if (value.isBlank()) "已恢复为 GitHub 上的默认地址" else "更新地址已保存"
    }

    fun setLocalApi(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                localServer.stop()
                settings.setLocalApiEnabled(false)
                localEnabled = false
                return@launch
            }
            if (!settings.secureAvailable) {
                localEnabled = false
                message = "这台设备的加密存储不可用，不能打开本机接口"
                return@launch
            }
            try {
                settings.ensureToken()
                settings.setLocalApiEnabled(true)
                val started = localServer.start()
                localEnabled = started
                token = settings.token()
                message = if (started) {
                    "本机接口已打开。只在应用进程还在时监听。"
                } else {
                    settings.setLocalApiEnabled(false)
                    "端口 ${LocalDiaryServer.PORT} 被占用，本机接口没有打开"
                }
            } catch (error: Exception) {
                settings.setLocalApiEnabled(false)
                localEnabled = false
                message = error.message ?: "本机接口没有打开"
            }
        }
    }

    fun regenerateToken() {
        try {
            token = settings.regenerateToken()
            message = "已更换令牌。旧令牌立即失效。"
        } catch (error: Exception) {
            message = error.message ?: "令牌没有更换"
        }
    }

    fun export() {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { exporter.exportZip() }
                exporter.share(file)
            } catch (error: Exception) {
                message = error.message ?: "导出失败"
            } finally {
                exporting = false
            }
        }
    }

    fun copy(text: String, label: String) {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        message = "已复制$label"
    }
}

@Composable
fun SettingsScreen(
    onCheckUpdate: () -> Unit,
    updateStatus: String,
    updateBusy: Boolean,
    notice: String?,
    noticeToken: Int,
    onNoticeConsumed: () -> Unit,
) {
    val viewModel = diaryViewModel { container ->
        SettingsViewModel(container.appContext, container.settings, container.exporter, container.localServer)
    }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        val text = viewModel.message ?: return@LaunchedEffect
        host.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(noticeToken) {
        val text = notice?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        host.showSnackbar(text)
        onNoticeConsumed()
    }
    var keyInput by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(viewModel.updateUrl) }
    var confirmToken by remember { mutableStateOf(false) }
    if (confirmToken) {
        AlertDialog(
            onDismissRequest = { confirmToken = false },
            title = { Text("更换令牌？") },
            text = { Text("正在使用旧令牌的程序会立刻连不上。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.regenerateToken()
                    confirmToken = false
                }) { Text("更换") }
            },
            dismissButton = {
                TextButton(onClick = { confirmToken = false }) { Text("取消") }
            },
        )
    }
    MessageLayer(host) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DiaryFrame {
                Column {
                    Spacer(Modifier.height(18.dp))
                    Text("设置", style = MaterialTheme.typography.headlineLarge)
                }
            }
            DiaryFrame {
                SectionTitle("智能摘要")
                Spacer(Modifier.height(8.dp))
                QuietCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(keyStatus(viewModel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("DeepSeek API Key") },
                            placeholder = { Text(if (viewModel.hasUserKey) "输入新密钥以替换" else "sk-…") },
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        Row {
                            TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示") }
                            Spacer(Modifier.weight(1f))
                            if (viewModel.hasUserKey) {
                                TextButton(onClick = {
                                    viewModel.clearApiKey()
                                    keyInput = ""
                                }) { Text("清除") }
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.saveApiKey(keyInput)
                                keyInput = ""
                                reveal = false
                            },
                            enabled = keyInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("保存密钥") }
                        Text(
                            "密钥只放在这台手机的加密存储里，不会写进日记正文。构建时如果提供了环境变量 DEEPSEEK_API_KEY，且这里还没保存过，会用那个值。界面不显示密钥本身。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DiaryFrame {
                SectionTitle("更新")
                Spacer(Modifier.height(8.dp))
                QuietCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("版本清单地址") },
                            placeholder = { Text("https://raw.githubusercontent.com/lulukdog/diary-android/main/release/version.json") },
                            singleLine = true,
                        )
                        TextButton(onClick = {
                            viewModel.saveUpdateUrl(urlInput)
                            urlInput = viewModel.updateUrl
                        }) { Text("保存地址") }
                        Button(
                            onClick = onCheckUpdate,
                            enabled = !updateBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("检查并更新") }
                        if (updateStatus.isNotBlank()) {
                            Text(
                                updateStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "地址空着就用 GitHub 上的 version.json，不用另填。点「检查并更新」会立刻检查，有新版本再下载并安装。启动时也会静默看一眼。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DiaryFrame {
                SectionTitle("数据")
                Spacer(Modifier.height(8.dp))
                QuietCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = viewModel::export,
                            enabled = !viewModel.exporting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (viewModel.exporting) "正在打包…" else "导出全部日记") }
                        Text(
                            "导出一个 zip：diary-export.json，以及 photos 文件夹。可用系统分享送到网盘或电脑。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("本机接口", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "默认关闭。不是 DSH，也不会把日记发到网上。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = viewModel.localEnabled, onCheckedChange = viewModel::setLocalApi)
                        }
                        if (viewModel.localEnabled) {
                            Text(
                                "http://127.0.0.1:${LocalDiaryServer.PORT}",
                                fontFamily = FontFamily.Monospace,
                            )
                            Text("令牌", style = MaterialTheme.typography.bodySmall)
                            SelectionContainer {
                                Text(viewModel.token, fontFamily = FontFamily.Monospace)
                            }
                            Row {
                                TextButton(onClick = { viewModel.copy(viewModel.token, "令牌") }) { Text("复制令牌") }
                                TextButton(onClick = { confirmToken = true }) { Text("更换令牌") }
                            }
                            Text(
                                "只监听本机，并且要带 Authorization: Bearer 令牌。应用进程结束之后接口就停了。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            DiaryFrame {
                SectionTitle("关于")
                Spacer(Modifier.height(8.dp))
                QuietCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("日记", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "一天按北京时间（Asia/Shanghai）切开，不按手机时区。正文、图片和密钥都在这台手机上。卸载或清除数据会删掉日记，需要副本时先导出。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "密钥可在 platform.deepseek.com 创建。应用不自带密钥。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

private fun keyStatus(viewModel: SettingsViewModel): String = when {
    viewModel.hasUserKey -> "已保存在这台设备的加密存储里。"
    viewModel.hasBuildKey -> "还没在这里保存。当前会使用构建时注入的密钥，界面不显示它。"
    else -> "还没有密钥。没有它也可以写日记，只是不能生成摘要。"
}
