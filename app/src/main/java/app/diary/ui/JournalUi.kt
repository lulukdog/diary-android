package app.diary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.diary.domain.DayClock
import app.diary.journal.JournalEntry
import app.diary.journal.JournalUtterance
import app.diary.journal.TimelineItem
import app.diary.journal.journalMillis

@Composable
fun PresenceCard(line: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("我在", style = MaterialTheme.typography.labelLarge)
            Text(line, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DaySummaryCard(
    title: String,
    lines: List<String>,
    keywords: List<String>,
    mood: Int?,
    actionLabel: String? = null,
    actionBusy: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (mood != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoodBar(mood)
                }
            }
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
            if (keywords.isNotEmpty()) {
                Text(
                    keywords.joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onAction != null && !actionLabel.isNullOrBlank()) {
                TextButton(onClick = onAction, enabled = !actionBusy) {
                    Text(if (actionBusy) "正在整理…" else actionLabel)
                }
            }
        }
    }
}

@Composable
fun SummaryEmptyCard(
    title: String,
    copy: String,
    action: String?,
    busy: Boolean,
    onAction: () -> Unit,
) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(copy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Button(onClick = onAction, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "正在整理…" else action)
                }
            }
        }
    }
}

@Composable
private fun MoodBar(mood: Int) {
    val level = (mood + 2).coerceIn(0, 4)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { index ->
            val filled = index <= level
            Box(
                Modifier
                    .size(width = 18.dp, height = 8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}

@Composable
fun OnboardingCard(
    prompt: String,
    progress: String,
    answer: String,
    onAnswer: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onSkip: () -> Unit,
) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("先认识一下", style = MaterialTheme.typography.titleMedium)
            Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(prompt, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswer,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("写点什么，也可以跳过") },
            )
            Row {
                TextButton(onClick = onSkip, enabled = !busy) { Text("跳过") }
                Button(onClick = onSend, enabled = !busy) { Text(if (busy) "正在记下…" else "回答") }
            }
        }
    }
}

@Composable
fun AskRow(
    text: String,
    onText: (String) -> Unit,
    asking: Boolean,
    modelReady: Boolean,
    onAsk: () -> Unit,
) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("问它", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("想问一句。它会回答。") },
            )
            if (!modelReady) {
                Text(
                    "日记本还没配好模型，问它可能暂时答不上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAsk, enabled = !asking && text.isNotBlank()) {
                if (asking) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("问它")
                }
            }
        }
    }
}

@Composable
fun JournalBeat(item: TimelineItem, file: (String) -> java.io.File) {
    when (item) {
        is TimelineItem.Mine -> MineBeat(item.entry, file)
        is TimelineItem.Theirs -> TheirsBeat(item.utterance)
    }
}

@Composable
private fun MineBeat(entry: JournalEntry, file: (String) -> java.io.File) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatJournalTime(entry.ts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val fileName = entry.localFileName
            if (fileName != null) {
                DiaryImage(
                    file = file(fileName),
                    contentDescription = entry.content.ifBlank { "日记图片" },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            } else if (entry.kind == "image") {
                Text("图片还没有载下来", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.content.isNotBlank()) {
                Text(entry.content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TheirsBeat(utterance: JournalUtterance) {
    val label = if (utterance.trigger == "ask") "它回答" else "它说"
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    formatJournalTime(utterance.ts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(utterance.content, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun formatJournalTime(ts: String): String {
    val millis = journalMillis(ts) ?: return ts.substringAfter('T', ts).take(5)
    return DayClock.formatTime(millis)
}
