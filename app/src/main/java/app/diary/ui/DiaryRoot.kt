package app.diary.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import app.diary.AppContainer
import app.diary.BuildConfig
import app.diary.domain.RemoteRelease
import app.diary.update.UpdateCheck
import app.diary.update.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UpdateViewModel(private val updates: UpdateRepository) : ViewModel() {
    var release by mutableStateOf<RemoteRelease?>(null)
        private set
    var downloading by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var statusText by mutableStateOf("")
        private set
    var manualMessage by mutableStateOf<String?>(null)
        private set
    var manualToken by mutableStateOf(0)
        private set

    private var downloaded: File? = null
    private var dismissed = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun consumeManual() {
        manualMessage = null
    }

    fun dismiss() {
        if (downloading) return
        dismissed = true
        release = null
        error = null
    }

    fun check(manual: Boolean) {
        if (downloading || checking) return
        if (manual) dismissed = false
        if (manual) statusText = "正在检查…"
        checking = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { updates.check() }
                when (result) {
                    UpdateCheck.NoUrl -> if (manual) {
                        statusText = "还没有填写更新地址"
                        postManual(statusText)
                    }
                    UpdateCheck.UpToDate -> {
                        statusText = "已是最新版本"
                        if (manual) postManual("已是最新版本 ${BuildConfig.VERSION_NAME}")
                    }
                    is UpdateCheck.Failed -> if (manual) {
                        statusText = result.message
                        postManual(result.message)
                    }
                    is UpdateCheck.Available -> {
                        statusText = "发现新版本 ${result.release.versionName}"
                        if (manual || !dismissed) {
                            release = result.release
                            downloaded = null
                            progress = 0
                            error = null
                        }
                    }
                }
            } finally {
                checking = false
            }
        }
    }

    fun downloadOrInstall() {
        val current = release ?: return
        if (downloading) return
        val existing = downloaded
        if (existing != null && existing.exists()) {
            install(existing)
            return
        }
        downloading = true
        error = null
        statusText = "正在下载 0%"
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    updates.download(current) { value ->
                        mainHandler.post {
                            progress = value
                            statusText = "正在下载 $value%"
                        }
                    }
                }
                downloaded = apk
                downloading = false
                install(apk)
            } catch (thrown: Exception) {
                downloading = false
                error = thrown.message ?: "下载失败"
                statusText = error.orEmpty()
            }
        }
    }

    private fun install(apk: File) {
        if (!updates.canInstallPackages()) {
            error = "请允许本应用安装未知应用，回到这里再点一次「安装」。"
            statusText = error.orEmpty()
            runCatching { updates.openInstallPermissionSettings() }
            return
        }
        try {
            updates.install(apk)
        } catch (_: Exception) {
            error = "没有打开系统安装器"
            statusText = error.orEmpty()
        }
    }

    private fun postManual(text: String) {
        manualMessage = text
        manualToken += 1
    }
}

@Composable
fun DiaryRoot(container: AppContainer) {
    val nav = rememberNavController()
    val updates = diaryViewModel { UpdateViewModel(it.updates) }
    LaunchedEffect(Unit) { updates.check(manual = false) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == null || route == "today" || route == "calendar" || route == "settings"
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    DiaryTab(nav, "today", "今天", Icons.Filled.Create)
                    DiaryTab(nav, "calendar", "日历", Icons.Filled.DateRange)
                    DiaryTab(nav, "settings", "设置", Icons.Filled.Settings)
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                TodayScreen(
                    onOpenSummary = { day -> nav.navigate("summary/$day") },
                    onEdit = { id -> nav.navigate("edit/$id") },
                )
            }
            composable("calendar") {
                CalendarScreen(onOpenDay = { day -> nav.navigate("day/$day") })
            }
            composable("settings") {
                SettingsScreen(
                    onCheckUpdate = { updates.check(manual = true) },
                    updateStatus = updates.statusText,
                    updateBusy = updates.checking || updates.downloading,
                    notice = updates.manualMessage,
                    noticeToken = updates.manualToken,
                    onNoticeConsumed = updates::consumeManual,
                )
            }
            composable(
                route = "day/{dayKey}",
                arguments = listOf(navArgument("dayKey") { type = NavType.StringType }),
            ) { entry ->
                val day = entry.arguments?.getString("dayKey").orEmpty()
                DayScreen(
                    dayKey = day,
                    onBack = { nav.popBackStack() },
                    onOpenSummary = { target -> nav.navigate("summary/$target") },
                    onEdit = { id -> nav.navigate("edit/$id") },
                )
            }
            composable(
                route = "summary/{dayKey}",
                arguments = listOf(navArgument("dayKey") { type = NavType.StringType }),
            ) { entry ->
                SummaryScreen(
                    dayKey = entry.arguments?.getString("dayKey").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = "edit/{entryId}",
                arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("entryId") ?: 0L
                EditScreen(entryId = id, onBack = { nav.popBackStack() })
            }
        }
    }
    UpdateDialog(updates)
}

@Composable
private fun RowScope.DiaryTab(
    nav: androidx.navigation.NavHostController,
    route: String,
    label: String,
    icon: ImageVector,
) {
    val backStack by nav.currentBackStackEntryAsState()
    val selected = backStack?.destination?.route == route
    NavigationBarItem(
        selected = selected,
        onClick = {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun UpdateDialog(viewModel: UpdateViewModel) {
    val release = viewModel.release
    if (release != null) AlertDialog(
        onDismissRequest = { if (!viewModel.downloading) viewModel.dismiss() },
        title = { Text("发现新版本 ${release.versionName}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    release.changelog.ifBlank { "有一个新的安装包可以下载。" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (viewModel.downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { viewModel.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("正在下载 ${viewModel.progress}%")
                }
                viewModel.error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::downloadOrInstall, enabled = !viewModel.downloading) {
                Text(if (viewModel.progress == 100 && !viewModel.downloading) "安装" else "下载并安装")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismiss, enabled = !viewModel.downloading) { Text("稍后") }
        },
    )
}
