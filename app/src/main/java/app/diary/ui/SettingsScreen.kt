package app.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diary.BuildConfig
import app.diary.export.DiaryExporter
import app.diary.journal.JournalLink
import app.diary.journal.JournalRepository
import app.diary.journal.JournalSync
import app.diary.journal.JournalUrls
import app.diary.journal.SyncPhase
import app.diary.journal.line
import app.diary.localapi.LocalDiaryServer
import app.diary.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settings: AppSettings,
    private val exporter: DiaryExporter,
    private val localServer: LocalDiaryServer,
    private val journal: JournalRepository,
    private val sync: JournalSync,
) : ViewModel() {
    var hasJournalToken by mutableStateOf(settings.hasJournalToken())
        private set
    var link by mutableStateOf<JournalLink?>(
        if (settings.hasJournalToken()) null else JournalLink.MissingToken,
    )
        private set
    var checkingJournal by mutableStateOf(false)
        private set
    var uploadPhase by mutableStateOf<SyncPhase>(SyncPhase.Idle)
        private set
    var localEnabled by mutableStateOf(settings.localApiEnabled() && localServer.running)
        private set
    var exporting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            sync.status.collect { uploadPhase = it }
        }
        if (settings.hasJournalToken()) checkJournal(announce = false)
    }

    fun consumeMessage() {
        message = null
    }

    fun connectionLabel(): String = when (link) {
        JournalLink.Ready -> "已连接"
        null -> "正在确认…"
        else -> "未连接"
    }

    fun connectionDetail(): String = when (val current = link) {
        JournalLink.Ready -> "日记会同步过去。这一天的陪伴和小结也会出现在首页。"
        JournalLink.Rejected -> "口令没有通过。可以重新连接。"
        is JournalLink.Failed -> current.message
        null -> "正在确认是不是连得上。"
        else -> "日记先留在这台手机上。连上之后会同步，也能看到陪伴和每天的小结。"
    }

    fun saveJournalToken(value: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                settings.setJournalToken(value)
                hasJournalToken = true
                onSaved()
                message = "口令只保存在这台手机上。正在连接。"
                sync.start(announceIfEmpty = true)
                checkJournal(announce = false)
            } catch (error: Exception) {
                message = error.message ?: "没有连上"
            }
        }
    }

    fun disconnect() {
        sync.cancel()
        settings.clearJournalToken()
        hasJournalToken = false
        link = JournalLink.MissingToken
        message = "已断开。这台手机上的日记还在。"
    }

    fun uploadLocalDiary() {
        sync.start(announceIfEmpty = true)
    }

    fun checkJournal(announce: Boolean = true) {
        if (checkingJournal) return
        if (!hasJournalToken) {
            link = JournalLink.MissingToken
            if (announce) message = "还没连接"
            return
        }
        checkingJournal = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { journal.check() }
                link = result
                if (announce) {
                    message = when (result) {
                        JournalLink.Ready -> "已连接"
                        JournalLink.MissingToken -> "还没连接"
                        JournalLink.Rejected -> "口令没有通过"
                        is JournalLink.Failed -> result.message
                    }
                }
            } finally {
                checkingJournal = false
            }
        }
    }

    fun closeLocalApi() {
        localServer.stop()
        settings.setLocalApiEnabled(false)
        localEnabled = false
        message = "本机接口已关闭"
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
        SettingsViewModel(
            container.settings,
            container.exporter,
            container.localServer,
            container.journal,
            container.journalSync,
        )
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
    var connectOpen by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var revealToken by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    if (connectOpen) {
        AlertDialog(
            onDismissRequest = { connectOpen = false },
            title = { Text("连接日记本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "口令和网页登录相同，只保存在这台手机上。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("口令") },
                        visualTransformation = if (revealToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    TextButton(onClick = { revealToken = !revealToken }) {
                        Text(if (revealToken) "隐藏" else "显示")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveJournalToken(tokenInput) {
                            tokenInput = ""
                            revealToken = false
                            connectOpen = false
                        }
                    },
                    enabled = tokenInput.isNotBlank(),
                ) { Text("连接") }
            },
            dismissButton = {
                TextButton(onClick = {
                    tokenInput = ""
                    revealToken = false
                    connectOpen = false
                }) { Text("取消") }
            },
        )
    }
    if (privacyOpen) {
        AlertDialog(
            onDismissRequest = { privacyOpen = false },
            title = { Text("隐私说明") },
            text = {
                Text(
                    "日记默认只在这台手机上。连接日记本之后，文字和图片会送到 ${JournalUrls.DEFAULT_BASE}。口令放在这台手机的加密存储里，不会写进安装包。卸载或清除数据会删掉手机上的副本，需要的话先导出。",
                )
            },
            confirmButton = {
                TextButton(onClick = { privacyOpen = false }) { Text("知道了") }
            },
        )
    }
    val connected = viewModel.link is JournalLink.Ready
    val uploading = viewModel.uploadPhase is SyncPhase.Running
    MessageLayer(host) {
        Column(
            Modifier
                .fillMaxSize()
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
                SectionTitle("日记本")
                Spacer(Modifier.height(8.dp))
                QuietCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(viewModel.connectionLabel(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            viewModel.connectionDetail(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        viewModel.uploadPhase.line()?.let { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (!connected) {
                            Button(
                                onClick = {
                                    tokenInput = ""
                                    revealToken = false
                                    connectOpen = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (viewModel.hasJournalToken) "重新连接" else "连接日记本") }
                        }
                        if (viewModel.hasJournalToken) {
                            Button(
                                onClick = { viewModel.checkJournal(announce = true) },
                                enabled = !viewModel.checkingJournal,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (viewModel.checkingJournal) "正在连接…" else "检查连接") }
                            Button(
                                onClick = viewModel::uploadLocalDiary,
                                enabled = !uploading,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (uploading) "正在同步…" else "立即同步") }
                            TextButton(onClick = viewModel::disconnect) { Text("断开") }
                        }
                    }
                }
            }
            DiaryFrame {
                SectionTitle("更新")
                Spacer(Modifier.height(8.dp))
                QuietCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "有新版本时会下载安装包，并交给系统安装。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onCheckUpdate,
                            enabled = !updateBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (updateBusy) "正在检查…" else "检查更新") }
                        if (updateStatus.isNotBlank()) {
                            Text(
                                updateStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            "导出一个 zip：diary-export.json，以及照片。可以用系统分享送出去。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (viewModel.localEnabled) {
                            Row {
                                Text(
                                    "本机接口开着",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Button(
                                onClick = viewModel::closeLocalApi,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("关闭本机接口") }
                        }
                    }
                }
            }
            DiaryFrame {
                SectionTitle("关于")
                Spacer(Modifier.height(8.dp))
                QuietCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("日记 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "一天从北京时间凌晨 4 点切开。浅色和深色跟随系统。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { privacyOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("隐私说明") }
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
