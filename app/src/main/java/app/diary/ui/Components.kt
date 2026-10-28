package app.diary.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.diary.data.PhotoBitmaps
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

private class ShownPhoto(val bitmap: Bitmap?)

@Composable
fun DiaryImage(file: File, contentDescription: String, modifier: Modifier = Modifier) {
    val loaded by produceState<ShownPhoto?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            ShownPhoto(runCatching { PhotoBitmaps.decodeStored(file) }.getOrNull())
        }
    }
    var viewing by remember(file.absolutePath) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val current = loaded
    val bitmap = current?.bitmap
    if (bitmap != null) {
        Box(modifier.clip(shape)) {
            PlatformPhoto(
                bitmap = bitmap,
                description = contentDescription,
                scaleType = ImageView.ScaleType.CENTER_CROP,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(role = Role.Button, onClickLabel = "放大") { viewing = true },
            )
        }
        if (viewing) {
            PhotoViewer(bitmap = bitmap, description = contentDescription, onClose = { viewing = false })
        }
    } else {
        Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (current != null) {
                Text(
                    "没有显示出来，请删掉后重加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PlatformPhoto(
    bitmap: Bitmap,
    description: String,
    scaleType: ImageView.ScaleType,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                this.scaleType = scaleType
                contentDescription = description
                setImageBitmap(bitmap)
                tag = bitmap
                isClickable = false
                isFocusable = false
            }
        },
        update = { view ->
            view.scaleType = scaleType
            view.contentDescription = description
            if (view.tag !== bitmap) {
                view.setImageBitmap(bitmap)
                view.tag = bitmap
            }
        },
    )
}

@Composable
private fun PhotoViewer(bitmap: Bitmap, description: String, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                factory = { context ->
                    PinchImageView(context).apply { setPhoto(bitmap, description) }
                },
                update = { view -> view.setPhoto(bitmap, description) },
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("关闭")
            }
        }
    }
}

private class PinchImageView(context: Context) : ImageView(context) {
    private val drawMatrix = Matrix()
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
                if (zoom <= 1f) {
                    panX = 0f
                    panY = 0f
                }
                applyMatrix()
                return true
            }
        },
    )
    private val dragDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (zoom <= 1f) return false
                panX -= distanceX
                panY -= distanceY
                applyMatrix()
                return true
            }
        },
    )

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    fun setPhoto(bitmap: Bitmap, description: String) {
        contentDescription = description
        if (tag !== bitmap) {
            tag = bitmap
            zoom = 1f
            panX = 0f
            panY = 0f
            setImageBitmap(bitmap)
        }
        post { applyMatrix() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        dragDetector.onTouchEvent(event)
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        applyMatrix()
    }

    private fun applyMatrix() {
        val drawable = drawable ?: return
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f || width == 0 || height == 0) return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val fit = minOf(viewWidth / imageWidth, viewHeight / imageHeight) * zoom
        val overflowX = ((imageWidth * fit - viewWidth) / 2f).coerceAtLeast(0f)
        val overflowY = ((imageHeight * fit - viewHeight) / 2f).coerceAtLeast(0f)
        panX = panX.coerceIn(-overflowX, overflowX)
        panY = panY.coerceIn(-overflowY, overflowY)
        val dx = (viewWidth - imageWidth * fit) / 2f + panX
        val dy = (viewHeight - imageHeight * fit) / 2f + panY
        drawMatrix.setScale(fit, fit)
        drawMatrix.postTranslate(dx, dy)
        imageMatrix = drawMatrix
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
