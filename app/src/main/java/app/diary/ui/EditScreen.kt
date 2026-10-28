package app.diary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.diary.data.DiaryRepository
import app.diary.data.PendingPhoto
import app.diary.data.PhotoStore
import kotlinx.coroutines.launch

class EditEntryViewModel(
    private val repository: DiaryRepository,
    private val photos: PhotoStore,
    private val entryId: Long,
) : DraftViewModel(photos) {
    var missing = androidx.compose.runtime.mutableStateOf(false)
    var loaded = androidx.compose.runtime.mutableStateOf(false)

    init {
        viewModelScope.launch {
            val current = repository.entry(entryId)
            if (current == null) {
                missing.value = true
            } else {
                body = current.entry.body
                pending = current.photos.map { photo ->
                    PendingPhoto(
                        fileName = photo.fileName,
                        caption = photo.caption,
                        existingId = photo.id,
                        freshlyImported = false,
                    )
                }
                loaded.value = true
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (!loaded.value) return
        if (!hasContent()) {
            message = "留下一点文字，或至少一张图片"
            return
        }
        saving = true
        val snapshotBody = body
        val snapshotPhotos = pending.toList()
        viewModelScope.launch {
            try {
                repository.updateEntry(entryId, snapshotBody, snapshotPhotos)
                keepImportedFiles()
                onDone()
            } catch (error: Exception) {
                message = error.message ?: "没有保存成功"
                saving = false
            }
        }
    }

    fun file(name: String) = safePhotoFile(photos, name)
}

@Composable
fun EditScreen(entryId: Long, onBack: () -> Unit) {
    val viewModel = diaryViewModel(key = "edit-$entryId") { container ->
        EditEntryViewModel(container.repository, container.photos, entryId)
    }
    val host = rememberMessageHost(viewModel.message) { viewModel.consumeMessage() }
    val context = LocalContext.current
    val photos = rememberPhotoActions(
        onPicked = { uri, file -> viewModel.attach(context, uri, file) },
        onMessage = { viewModel.message = it },
    )
    MessageLayer(host) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            DiaryFrame {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            DiaryFrame {
                Column {
                    Text("修改", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    if (viewModel.missing.value) {
                        Text("找不到这则日记。")
                    } else if (!viewModel.loaded.value) {
                        Text("正在打开…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        EntryComposer(
                            body = viewModel.body,
                            onBody = { viewModel.body = it },
                            photos = viewModel.pending,
                            photoFile = viewModel::file,
                            importing = viewModel.importing,
                            saving = viewModel.saving,
                            saveLabel = "保存",
                            onCaption = viewModel::updateCaption,
                            onRemove = viewModel::removeAt,
                            onCamera = photos.takePhoto,
                            onGallery = photos.pickFromGallery,
                            onSave = { viewModel.save(onBack) },
                        )
                    }
                }
            }
        }
    }
}
