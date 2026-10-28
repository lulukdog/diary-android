package app.diary.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.diary.AppContainer
import app.diary.DiaryApplication
import app.diary.data.PendingPhoto
import app.diary.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
inline fun <reified T : ViewModel> diaryViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> T,
): T {
    val context = LocalContext.current
    val container = (context.applicationContext as DiaryApplication).container
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                @Suppress("UNCHECKED_CAST")
                return create(container) as VM
            }
        },
    )
}

abstract class DraftViewModel(
    private val photos: PhotoStore,
) : ViewModel() {
    var body by mutableStateOf("")
    var pending by mutableStateOf<List<PendingPhoto>>(emptyList())
    var importing by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    private var keepFiles = false

    fun consumeMessage() {
        message = null
    }

    fun updateCaption(index: Int, caption: String) {
        pending = pending.mapIndexed { itemIndex, photo ->
            if (itemIndex == index) photo.copy(caption = caption) else photo
        }
    }

    fun removeAt(index: Int) {
        val photo = pending.getOrNull(index) ?: return
        pending = pending.filterIndexed { itemIndex, _ -> itemIndex != index }
        if (photo.freshlyImported) {
            viewModelScope.launch(Dispatchers.IO) { photos.delete(photo.fileName) }
        }
    }

    fun attach(context: Context, uri: Uri, deleteAfter: File?) {
        if (pending.size >= 9) {
            deleteAfter?.delete()
            message = "一则日记最多 9 张图片"
            return
        }
        val opened = try {
            photos.open(context, uri)
        } catch (error: Exception) {
            deleteAfter?.delete()
            message = error.message ?: "这张图片没有读出来"
            return
        }
        importing = true
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    try {
                        photos.importStream(opened.stream, opened.thumbnail)
                    } finally {
                        runCatching { opened.stream.close() }
                        deleteAfter?.delete()
                    }
                }
                pending = pending + PendingPhoto(
                    fileName = name,
                    caption = "",
                    existingId = null,
                    freshlyImported = true,
                )
            } catch (error: Exception) {
                message = error.message ?: "这张图片没有读出来"
            } finally {
                importing = false
            }
        }
    }

    protected fun keepImportedFiles() {
        keepFiles = true
    }

    protected fun clearDraft() {
        body = ""
        pending = emptyList()
    }

    protected fun hasContent(): Boolean =
        body.isNotBlank() || pending.isNotEmpty()

    override fun onCleared() {
        if (!keepFiles) {
            pending.filter { it.freshlyImported }.forEach { photos.delete(it.fileName) }
        }
    }
}
