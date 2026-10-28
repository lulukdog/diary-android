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
import app.diary.journal.JournalDay
import app.diary.journal.JournalEntry
import app.diary.journal.JournalUtterance
import app.diary.journal.TimelineItem
import app.diary.journal.companionLines
import app.diary.journal.hasEmotionCard
import app.diary.journal.journalMillis

@Composable
fun EmotionCard(day: JournalDay) {
    if (!day.hasEmotionCard()) return
    val card = day.card
    val lines = day.companionLines()
    val title = if (day.day == day.today) "今天的你" else "那天的你"
    val counts = buildList {
        val entries = card?.entryCount ?: 0
        val images = card?.imageCount ?: 0
        if (entries > 0) add("$entries 段")
        if (images > 0) add("$images 张图")
    }
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (counts.isNotEmpty()) {
                    Text(
                        counts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (card?.mood != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoodBar(card.mood)
                }
            }
            val label = card?.moodLabel
            if (!label.isNullOrBlank()) {
                Text("「$label」", style = MaterialTheme.typography.bodyLarge)
            }
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            val headline = card?.headline
            if (!headline.isNullOrBlank()) {
                Text(headline, style = MaterialTheme.typography.bodyMedium)
            }
            val keywords = card?.keywords.orEmpty()
            if (keywords.isNotEmpty()) {
                Text(
                    keywords.joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card != null && card.mood == null && !card.finalized && card.entryCount > 0) {
                Text(
                    "今天的总结要等晚上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
