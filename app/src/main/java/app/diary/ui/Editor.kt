package app.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.diary.data.PendingPhoto
import app.diary.data.PhotoStore
import java.io.File

@Composable
fun EntryComposer(
    body: String,
    onBody: (String) -> Unit,
    photos: List<PendingPhoto>,
    photoFile: (String) -> File,
    importing: Boolean,
    saving: Boolean,
    saveLabel: String,
    onCaption: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSave: () -> Unit,
) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = body,
                onValueChange = onBody,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                placeholder = { Text("写下来就好") },
            )
            photos.forEachIndexed { index, photo ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiaryImage(
                        file = photoFile(photo.fileName),
                        contentDescription = photo.caption.ifBlank { "待保存的图片" },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                    OutlinedTextField(
                        value = photo.caption,
                        onValueChange = { onCaption(index, it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("图片说明，可留空") },
                    )
                    TextButton(onClick = { onRemove(index) }) { Text("移除这张") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCamera, enabled = !importing && !saving) { Text("拍照") }
                TextButton(onClick = onGallery, enabled = !importing && !saving) { Text("相册") }
                if (importing) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSave, enabled = !saving && !importing) {
                    Text(if (saving) "正在保存" else saveLabel)
                }
            }
        }
    }
}

fun safePhotoFile(store: PhotoStore, name: String): File =
    runCatching { store.file(name) }.getOrElse { File(name) }
