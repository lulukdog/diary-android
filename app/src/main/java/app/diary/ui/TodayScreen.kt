package app.diary.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.diary.data.DiaryRepository
import app.diary.data.EntryWithPhotos
import app.diary.data.PhotoStore
import app.diary.domain.DayClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DayWriterViewModel(
    private val appContext: Context,
    private val repository: DiaryRepository,
    private val photos: PhotoStore,
    val dayKey: String,
) : DraftViewModel(photos) {
    val entries = repository.observeDay(dayKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override fun photoContext(): Context = appContext

    fun save() {
        if (!hasContent()) {
            message = "先写一句，或加一张图片"
            return
        }
        saving = true
        val snapshotBody = body
        val snapshotPhotos = pending.toList()
        viewModelScope.launch {
            try {
                repository.addEntry(dayKey, snapshotBody, snapshotPhotos)
                clearDraft()
                message = "记下了"
            } catch (error: Exception) {
                message = error.message ?: "没有保存成功"
            } finally {
                saving = false
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    fun file(name: String) = safePhotoFile(photos, name)
}

@Composable
fun TodayScreen(onOpenSummary: (String) -> Unit, onEdit: (Long) -> Unit) {
    val dayKey = DayClock.dayKey()
    val viewModel = diaryViewModel(key = "writer-$dayKey") { container ->
        DayWriterViewModel(container.appContext, container.repository, container.photos, dayKey)
    }
    WriterPage(
        title = "今天",
        subtitle = "${DayClock.formatDay(dayKey)} · 北京时间",
        saveLabel = "记下",
        emptyText = "今天还空着。写下一句也很好。",
        viewModel = viewModel,
        onOpenSummary = { onOpenSummary(dayKey) },
        onEdit = onEdit,
    )
}

@Composable
fun WriterPage(
    title: String,
    subtitle: String,
    saveLabel: String,
    emptyText: String,
    viewModel: DayWriterViewModel,
    onOpenSummary: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val host = rememberMessageHost(viewModel.message) { viewModel.consumeMessage() }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    val photos = rememberPhotoActions(
        onPicked = { uri, file -> viewModel.attach(uri, file) },
        onMessage = { viewModel.message = it },
    )
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这则日记？") },
            text = { Text("文字和配图都会从这台设备上移除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(pendingDelete!!)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
    MessageLayer(host) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack != null) {
                item {
                    DiaryFrame {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            }
            item {
                DiaryFrame {
                    Column {
                        Spacer(Modifier.height(if (onBack == null) 18.dp else 0.dp))
                        Text(title, style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                DiaryFrame {
                    EntryComposer(
                        body = viewModel.body,
                        onBody = { viewModel.body = it },
                        photos = viewModel.pending,
                        photoFile = viewModel::file,
                        importing = viewModel.importing,
                        saving = viewModel.saving,
                        saveLabel = saveLabel,
                        onCaption = viewModel::updateCaption,
                        onRemove = viewModel::removeAt,
                        onCamera = photos.takePhoto,
                        onGallery = photos.pickFromGallery,
                        onSave = viewModel::save,
                    )
                }
            }
            if (entries.isEmpty()) {
                item {
                    DiaryFrame {
                        Text(
                            emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    DiaryFrame {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("这一天", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = onOpenSummary) { Text("当日摘要") }
                        }
                    }
                }
                items(entries, key = { it.entry.id }) { entry ->
                    DiaryFrame {
                        EntryCard(
                            entry = entry,
                            file = viewModel::file,
                            onEdit = { onEdit(entry.entry.id) },
                            onDelete = { pendingDelete = entry.entry.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EntryCard(
    entry: EntryWithPhotos,
    file: (String) -> java.io.File,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                DayClock.formatTime(entry.entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.entry.body.isNotBlank()) {
                Text(entry.entry.body, style = MaterialTheme.typography.bodyLarge)
            }
            entry.photos.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { photo ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            DiaryImage(
                                file = file(photo.fileName),
                                contentDescription = photo.caption.ifBlank { "日记图片" },
                                modifier = Modifier.fillMaxWidth().height(if (entry.photos.size == 1) 220.dp else 140.dp),
                            )
                            if (photo.caption.isNotBlank()) {
                                Text(
                                    photo.caption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (row.size == 1 && entry.photos.size > 1) Spacer(Modifier.weight(1f))
                }
            }
            Row {
                TextButton(onClick = onEdit) { Text("修改") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}
