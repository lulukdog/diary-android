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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.diary.domain.DayClock

@Composable
fun DayScreen(
    dayKey: String,
    onBack: () -> Unit,
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
    var shown by remember(dayKey) { mutableStateOf(dayKey) }
    val viewModel = diaryViewModel(key = "writer-$shown") { container ->
        DayWriterViewModel(
            container.repository,
            container.photos,
            container.journal,
            container.journalSync,
            container.deepSeek,
            container.settings,
            shown,
        )
    }
    val today = DayClock.isToday(shown)
    WriterPage(
        title = DayClock.relativeTitle(shown),
        subtitle = if (today) {
            "${DayClock.formatDay(shown)} · 凌晨 4 点后算新的一天"
        } else {
            DayClock.formatDay(shown)
        },
        saveLabel = if (today) "记下" else "补记",
        emptyText = if (today) "今天还空着。写下一句也很好。" else "这一天还是空的。",
        viewModel = viewModel,
        onEdit = onEdit,
        canGoNext = DayClock.shiftDay(shown, 1) != null,
        onPrevious = { DayClock.shiftDay(shown, -1)?.let { shown = it } },
        onNext = { DayClock.shiftDay(shown, 1)?.let { shown = it } },
        onBack = onBack,
    )
}
