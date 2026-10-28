package app.diary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.diary.data.DiaryRepository
import app.diary.domain.CalendarMath
import app.diary.domain.DayClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(repository: DiaryRepository) : ViewModel() {
    val marked = repository.observeMarkedDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val selectedDay = MutableStateFlow(DayClock.dayKey())
    val month = MutableStateFlow(YearMonth.now(DayClock.zone))

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedEntries = selectedDay
        .flatMapLatest { day -> repository.observeDay(day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selected = selectedDay

    fun select(day: LocalDate) {
        selectedDay.value = day.toString()
    }

    fun shiftMonth(delta: Int) {
        month.value = month.value.plusMonths(delta.toLong())
    }

    fun showCurrentMonth() {
        val today = LocalDate.now(DayClock.zone)
        month.value = YearMonth.from(today)
        selectedDay.value = today.toString()
    }
}

@Composable
fun CalendarScreen(onOpenDay: (String) -> Unit) {
    val viewModel = diaryViewModel { CalendarViewModel(it.repository) }
    val marked by viewModel.marked.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val entries by viewModel.selectedEntries.collectAsStateWithLifecycle()
    val today = DayClock.dayKey()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DiaryFrame {
                Column {
                    Spacer(Modifier.height(18.dp))
                    Text("日历", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "按北京时间的日期归档",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            DiaryFrame {
                QuietCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.shiftMonth(-1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                            }
                            Text(
                                DayClock.formatMonth(month),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            IconButton(onClick = { viewModel.shiftMonth(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                            }
                        }
                        if (month != YearMonth.now(DayClock.zone)) {
                            TextButton(onClick = viewModel::showCurrentMonth) { Text("回到本月") }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                                Text(
                                    label,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        CalendarMath.cells(month).chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    DayCell(
                                        date = date,
                                        selected = date?.toString() == selected,
                                        today = date?.toString() == today,
                                        marked = date != null && date.toString() in marked,
                                        onClick = { if (date != null) viewModel.select(date) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            DiaryFrame {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(DayClock.formatDay(selected), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (entries.isEmpty()) "没有记录" else "${entries.size} 则",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onOpenDay(selected) }) { Text("打开这一天") }
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                DiaryFrame {
                    Text(
                        "这一天还没有写下什么。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(entries, key = { it.entry.id }) { entry ->
                DiaryFrame {
                    QuietCard {
                        Column(Modifier.padding(16.dp).clickable { onOpenDay(selected) }) {
                            Text(
                                DayClock.formatTime(entry.entry.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                entry.entry.body.ifBlank { "（图片）" }.lineSequence().first().take(80),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selected: Boolean,
    today: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(44.dp)
            .semantics {
                if (date != null) {
                    contentDescription = buildString {
                        append(date.monthValue).append("月").append(date.dayOfMonth).append("日")
                        if (marked) append("，有记录")
                    }
                }
            }
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                selected -> MaterialTheme.colorScheme.primary
                                today -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        color = when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (marked) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        ),
                )
            }
        }
    }
}
