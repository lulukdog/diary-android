package app.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.diary.ai.DeepSeekClient
import app.diary.data.DiaryRepository
import app.diary.data.toSummarySource
import app.diary.domain.DayClock
import app.diary.domain.buildSummaryPrompt
import app.diary.domain.diarySourceHash
import app.diary.domain.summaryMaterialExists
import app.diary.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SummaryViewModel(
    private val repository: DiaryRepository,
    private val deepSeek: DeepSeekClient,
    private val settings: AppSettings,
    val dayKey: String,
) : ViewModel() {
    val summary = repository.observeSummary(dayKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val entries = repository.observeDay(dayKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun generate() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val key = settings.resolveApiKey()
                if (key.isBlank()) {
                    error = "小结以日记本上的一天卡片为准。这台设备没有另外的摘要密钥。"
                    return@launch
                }
                val rows = repository.entriesForDay(dayKey)
                val sources = rows.map { it.toSummarySource() }
                if (!summaryMaterialExists(sources)) {
                    error = if (rows.isEmpty()) {
                        "这一天还没有日记"
                    } else {
                        "图片本身不会上传。写一点文字，或给图片加一句说明，再生成摘要。"
                    }
                    return@launch
                }
                val prompt = buildSummaryPrompt(dayKey, sources)
                val text = withContext(Dispatchers.IO) { deepSeek.summarize(key, prompt) }
                repository.saveSummary(dayKey, text, diarySourceHash(sources))
            } catch (thrown: Exception) {
                error = thrown.message ?: "摘要没有生成"
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun SummaryScreen(dayKey: String, onBack: () -> Unit) {
    if (!DayClock.isDayKey(dayKey)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text("日期不对")
        }
        return
    }
    val viewModel = diaryViewModel(key = "summary-$dayKey") { container ->
        SummaryViewModel(container.repository, container.deepSeek, container.settings, dayKey)
    }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val hash = diarySourceHash(entries.map { it.toSummarySource() })
    val stale = summary != null && summary?.sourceHash != hash
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        DiaryFrame {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
        DiaryFrame {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当日摘要", style = MaterialTheme.typography.headlineLarge)
                Text(
                    DayClock.formatDay(dayKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary != null) {
                    QuietCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(summary!!.text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "生成于 ${DayClock.formatDateTime(summary!!.generatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (stale) {
                                Text(
                                    "这天的日记改过，摘要可能已经过时。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
                if (viewModel.loading) {
                    CircularProgressIndicator()
                    Text("正在整理这一天…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                viewModel.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = viewModel::generate, enabled = !viewModel.loading) {
                    Text(if (summary == null) "生成摘要" else "重新生成")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "每天的小结优先显示日记本上的卡片。这里只在这台设备已经有密钥时，才另外整理一段，不会在设置里再填一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
