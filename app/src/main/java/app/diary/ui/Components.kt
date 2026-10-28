package app.diary.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun DiaryFrame(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.widthIn(max = 680.dp).fillMaxWidth(), content = content)
    }
}

@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
fun rememberMessageHost(message: String?, onConsumed: () -> Unit): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            host.showSnackbar(message)
            onConsumed()
        }
    }
    return host
}

@Composable
fun MessageLayer(host: SnackbarHostState, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            hostState = host,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}

@Composable
fun DiaryImage(file: File, contentDescription: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(14.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
        }
    }
}

class PhotoActions(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit,
)

@Composable
fun rememberPhotoActions(onPicked: (Uri, File?) -> Unit, onMessage: (String) -> Unit): PhotoActions {
    val context = LocalContext.current
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        cameraFile = null
        if (ok && file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            onPicked(uri, file)
        } else {
            file?.delete()
        }
    }
    val pick = rememberLauncherForActivityResult(OpenAlbumImage()) { uri ->
        if (uri != null) onPicked(uri, null)
    }
    fun launchCamera() {
        val shot = createCameraFile(context)
        cameraFile = shot
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shot)
        takePicture.launch(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else onMessage("没有相机权限。可以从相册选择图片。")
    }
    return PhotoActions(
        takePhoto = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
        },
        pickFromGallery = { pick.launch("image/*") },
    )
}

private class OpenAlbumImage : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        intent.data?.let { return it }
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0)?.uri?.let { return it }
        }
        return runCatching {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }.getOrNull()
    }
}

private fun createCameraFile(context: Context): File {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(directory, "shot-${UUID.randomUUID()}.jpg")
}
