package app.diary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.diary.domain.DayClock

@Composable
fun DayScreen(
    dayKey: String,
    onBack: () -> Unit,
    onOpenSummary: (String) -> Unit,
    onEdit: (Long) -> Unit,
) {
    if (!DayClock.isDayKey(dayKey)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("日期不对")
        }
        return
    }
    val viewModel = diaryViewModel(key = "writer-$dayKey") { container ->
        DayWriterViewModel(container.appContext, container.repository, container.photos, dayKey)
    }
    WriterPage(
        title = if (DayClock.isToday(dayKey)) "今天" else DayClock.formatDay(dayKey),
        subtitle = if (DayClock.isToday(dayKey)) {
            "${DayClock.formatDay(dayKey)} · 北京时间"
        } else {
            "补记也会归到这一天"
        },
        saveLabel = if (DayClock.isToday(dayKey)) "记下" else "补记",
        emptyText = "这一天还是空的。",
        viewModel = viewModel,
        onOpenSummary = { onOpenSummary(dayKey) },
        onEdit = onEdit,
        onBack = onBack,
    )
}
