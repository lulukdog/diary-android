package app.diary.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.diary.ai.DeepSeekClient
import app.diary.data.DiaryRepository
import app.diary.data.EntryWithPhotos
import app.diary.data.PendingPhoto
import app.diary.data.PhotoStore
import app.diary.domain.CompanionVoice
import app.diary.domain.DayBrief
import app.diary.domain.DayClock
import app.diary.domain.SummarySourceEntry
import app.diary.domain.buildSummaryPrompt
import app.diary.domain.dayBrief
import app.diary.domain.diarySourceHash
import app.diary.domain.summaryActionLabel
import app.diary.domain.summaryCardVisible
import app.diary.domain.summaryEmptyMessage
import app.diary.domain.summaryMaterialExists
import app.diary.journal.JournalDay
import app.diary.settings.AppSettings
import app.diary.data.toSummarySource
import app.diary.journal.JournalRepository
import app.diary.journal.companionLines
import app.diary.journal.JournalSync
import app.diary.journal.LocalRow
import app.diary.journal.SyncPhase
import app.diary.journal.WriteOutcome
import app.diary.journal.line
import app.diary.journal.timeline
import app.diary.journal.visibleLocalKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DayBrowserViewModel : ViewModel() {
    var dayKey by mutableStateOf(DayClock.dayKey())
        private set

    fun shift(days: Long) {
        dayKey = DayClock.shiftDay(dayKey, days) ?: return
    }
}

class DayWriterViewModel(
    private val repository: DiaryRepository,
    private val photos: PhotoStore,
    private val journal: JournalRepository,
    private val sync: JournalSync,
    private val deepSeek: DeepSeekClient,
    private val settings: AppSettings,
    val dayKey: String,
) : DraftViewModel(photos) {
    val entries = repository.observeDay(dayKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val summary = repository.observeSummary(dayKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var connected by mutableStateOf(journal.configured())
        private set
    var page by mutableStateOf<JournalDay?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var askText by mutableStateOf("")
    var asking by mutableStateOf(false)
        private set
    var onboardingText by mutableStateOf("")
    var answering by mutableStateOf(false)
        private set
    var uploadPhase by mutableStateOf<SyncPhase>(SyncPhase.Idle)
        private set
    var linkNote by mutableStateOf<String?>(null)
        private set
    var presenceOverride by mutableStateOf<String?>(null)
        private set
    var presenceToken by mutableStateOf(0)
        private set
    var summarizing by mutableStateOf(false)
        private set
    val hasLocalSummaryKey: Boolean = settings.resolveApiKey().isNotBlank()

    init {
        viewModelScope.launch {
            sync.status.collect { phase ->
                val wasFinished = uploadPhase is SyncPhase.Finished
                uploadPhase = phase
                if (phase is SyncPhase.Finished && !wasFinished) {
                    page = runCatching { withContext(Dispatchers.IO) { journal.load(dayKey) } }.getOrNull() ?: page
                }
            }
        }
    }

    fun refresh() {
        connected = journal.configured()
        if (!connected) {
            page = null
            linkNote = null
            refreshing = false
            return
        }
        refreshing = true
        viewModelScope.launch {
            try {
                page = withContext(Dispatchers.IO) { journal.load(dayKey) }
                linkNote = null
            } catch (error: Exception) {
                linkNote = error.message ?: "日记本没有连上"
            } finally {
                refreshing = false
            }
        }
    }

    fun save() {
        if (!hasContent()) {
            message = "先写一句，或加一张图片"
            return
        }
        val serverToday = page?.today ?: DayClock.dayKey()
        if (connected && dayKey == serverToday) {
            saveRemote()
        } else {
            saveLocal(onServerButNotToday = connected)
        }
    }

    fun ask() {
        val text = askText.trim()
        if (text.isEmpty()) {
            message = "想问什么？先说一句。"
            return
        }
        if (!connected) {
            message = "先在设置里连接日记本"
            return
        }
        asking = true
        viewModelScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { journal.ask(text) }
                askText = ""
                page = runCatching { withContext(Dispatchers.IO) { journal.load(dayKey) } }.getOrNull() ?: page
                notePresence(reply.mood.line.orEmpty(), serverSpoke())
                val shown = page?.utterances?.any { it.content == reply.reply } == true
                if (!shown && reply.reply.isNotBlank()) {
                    message = reply.reply
                } else if (reply.mood.line.orEmpty().isNotBlank() && page?.companionLines()?.contains(reply.mood.line) != true) {
                    message = reply.mood.line
                }
            } catch (error: Exception) {
                message = error.message ?: "它没有答上"
            } finally {
                asking = false
            }
        }
    }

    fun submitOnboarding(skip: Boolean) {
        val question = page?.question ?: return
        val answer = onboardingText.trim()
        if (!skip && answer.isEmpty()) {
            message = "写点什么，或者跳过。"
            return
        }
        answering = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    journal.answerOnboarding(question.key, if (skip) null else answer, skip)
                }
                onboardingText = ""
                page = withContext(Dispatchers.IO) { journal.load(dayKey) }
            } catch (error: Exception) {
                message = error.message ?: "没有送出去"
            } finally {
                answering = false
            }
        }
    }

    fun uploadLocals() {
        sync.start(announceIfEmpty = true)
    }

    fun prepareSummary() {
        if (summarizing) return
        summarizing = true
        viewModelScope.launch {
            try {
                connected = journal.configured()
                if (connected) {
                    try {
                        page = withContext(Dispatchers.IO) { journal.load(dayKey) }
                        linkNote = null
                    } catch (error: Exception) {
                        linkNote = error.message ?: "日记本没有连上"
                    }
                }
                if (summaryCardVisible(currentBrief())) return@launch
                val key = settings.resolveApiKey()
                if (key.isBlank()) {
                    message = if (DayClock.isToday(dayKey)) {
                        "小结还没好。日记本一般在凌晨 4 点之后才收口。"
                    } else {
                        "日记本上还没有这一天的小结。"
                    }
                    return@launch
                }
                val rows = repository.entriesForDay(dayKey)
                val localSources = rows.map { it.toSummarySource() }
                val sources = if (summaryMaterialExists(localSources)) {
                    localSources
                } else {
                    page?.entries.orEmpty().map { entry ->
                        SummarySourceEntry(body = entry.content, captions = emptyList())
                    }.filter { it.body.isNotBlank() }
                }
                if (!summaryMaterialExists(sources)) {
                    message = "这一天还没有可以整理的文字。"
                    return@launch
                }
                val prompt = buildSummaryPrompt(dayKey, sources)
                val text = withContext(Dispatchers.IO) { deepSeek.summarize(key, prompt) }
                repository.saveSummary(dayKey, text, diarySourceHash(sources))
                message = "小结好了"
            } catch (error: Exception) {
                message = error.message ?: "小结没有生成"
            } finally {
                summarizing = false
            }
        }
    }

    private fun currentBrief(): DayBrief = dayBrief(
        headline = page?.card?.headline,
        moodLabel = page?.card?.moodLabel,
        mood = page?.card?.mood,
        keywords = page?.card?.keywords.orEmpty(),
        companion = page?.card?.companion,
        moodLine = page?.mood?.line,
        localSummary = summary.value?.text,
        entryTexts = page?.entries?.sortedBy { it.ts }?.map { it.content }.orEmpty(),
        justSaved = presenceOverride,
    )

    private fun notePresence(body: String, serverSpoke: Boolean) {
        val line = CompanionVoice.lineFor(body)
        if (line != null) presenceOverride = line
        if (line != null || serverSpoke) presenceToken++
    }

    private fun serverSpoke(): Boolean {
        val current = page ?: return false
        return !current.mood.line.isNullOrBlank() || !current.card?.companion.isNullOrBlank()
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    fun file(name: String) = safePhotoFile(photos, name)

    private fun saveLocal(onServerButNotToday: Boolean) {
        saving = true
        val snapshotBody = body
        val snapshotPhotos = pending.toList()
        viewModelScope.launch {
            try {
                repository.addEntry(dayKey, snapshotBody, snapshotPhotos)
                clearDraft()
                notePresence(snapshotBody, serverSpoke = false)
                message = if (onServerButNotToday) {
                    "补记先留在这台手机上。日记本只按它现在的时间归天。"
                } else {
                    "记下了"
                }
            } catch (error: Exception) {
                message = error.message ?: "没有保存成功"
            } finally {
                saving = false
            }
        }
    }

    private fun saveRemote() {
        saving = true
        val snapshotBody = body
        val snapshotPhotos = pending.toList()
        viewModelScope.launch {
            try {
                when (val outcome = withContext(Dispatchers.IO) { journal.write(snapshotBody, snapshotPhotos) }) {
                    is WriteOutcome.Done -> {
                        snapshotPhotos.filter { it.freshlyImported }.forEach { photos.delete(it.fileName) }
                        clearDraft()
                        page = runCatching { withContext(Dispatchers.IO) { journal.load(dayKey) } }.getOrNull() ?: page
                        notePresence(snapshotBody, serverSpoke())
                        message = if (outcome.day != dayKey) {
                            "记下了。日记本把它归在 ${DayClock.formatDay(outcome.day)}。"
                        } else {
                            "记下了"
                        }
                    }
                    is WriteOutcome.Partial -> {
                        val sent = snapshotPhotos.map { it.fileName }.toSet() -
                            outcome.remainingPhotos.map { it.fileName }.toSet()
                        snapshotPhotos.filter { it.freshlyImported && it.fileName in sent }
                            .forEach { photos.delete(it.fileName) }
                        if (outcome.remainingBody.isNotBlank() || outcome.remainingPhotos.isNotEmpty()) {
                            repository.addEntry(dayKey, outcome.remainingBody, outcome.remainingPhotos)
                        }
                        clearDraft()
                        page = runCatching { withContext(Dispatchers.IO) { journal.load(dayKey) } }.getOrNull() ?: page
                        notePresence(snapshotBody, serverSpoke())
                        message = "有一部分没送出去，先留在这台手机上。${outcome.error.message.orEmpty()}".trim()
                    }
                }
            } catch (error: Exception) {
                try {
                    repository.addEntry(dayKey, snapshotBody, snapshotPhotos)
                    clearDraft()
                    notePresence(snapshotBody, serverSpoke = false)
                    message = "没连上日记本，先记在这台手机上。"
                } catch (local: Exception) {
                    message = error.message ?: local.message ?: "没有保存成功"
                }
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun TodayScreen(onEdit: (Long) -> Unit) {
    val browser = diaryViewModel { DayBrowserViewModel() }
    val dayKey = browser.dayKey
    val viewModel = diaryViewModel(key = "writer-$dayKey") { container ->
        DayWriterViewModel(
            container.repository,
            container.photos,
            container.journal,
            container.journalSync,
            container.deepSeek,
            container.settings,
            dayKey,
        )
    }
    WriterPage(
        title = DayClock.relativeTitle(dayKey),
        subtitle = daySubtitle(dayKey),
        saveLabel = if (DayClock.isToday(dayKey)) "记下" else "补记",
        emptyText = if (DayClock.isToday(dayKey)) "今天还空着。写下一句也很好。" else "这一天还是空的。",
        viewModel = viewModel,
        onEdit = onEdit,
        canGoNext = DayClock.shiftDay(dayKey, 1) != null,
        onPrevious = { browser.shift(-1) },
        onNext = { browser.shift(1) },
    )
}

private fun daySubtitle(dayKey: String): String {
    val date = DayClock.formatDay(dayKey)
    return if (DayClock.isToday(dayKey)) "$date · 凌晨 4 点后算新的一天" else date
}

@Composable
fun WriterPage(
    title: String,
    subtitle: String,
    saveLabel: String,
    emptyText: String,
    viewModel: DayWriterViewModel,
    onEdit: (Long) -> Unit,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val summaryState by viewModel.summary.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.dayKey) {
        listState.scrollToItem(0)
        viewModel.refresh()
    }
    LaunchedEffect(viewModel.presenceToken) {
        if (viewModel.presenceToken > 0) listState.animateScrollToItem(0)
    }
    val page = viewModel.page
    val serverToday = page?.today ?: DayClock.dayKey()
    val showAsk = viewModel.connected && viewModel.dayKey == serverToday
    val visibleIds = visibleLocalKeys(entries.map { it.toLocalRow() }, page != null, viewModel.dayKey)
    val localOnly = entries.filter { it.entry.id in visibleIds.toSet() }
    val unsynced = entries.any { !it.entry.cached && !it.entry.synced }
    val host = rememberMessageHost(viewModel.message) { viewModel.consumeMessage() }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val photos = rememberPhotoActions(
        onPicked = { uri, file -> viewModel.attach(context, uri, file) },
        onMessage = { viewModel.message = it },
    )
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这则日记？") },
            text = { Text("文字和配图都会从这台设备上移除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(pendingDelete!!)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
    val remoteTexts = page?.entries?.sortedBy { it.ts }?.map { it.content }.orEmpty()
    val brief = dayBrief(
        headline = page?.card?.headline,
        moodLabel = page?.card?.moodLabel,
        mood = page?.card?.mood,
        keywords = page?.card?.keywords.orEmpty(),
        companion = page?.card?.companion,
        moodLine = page?.mood?.line,
        localSummary = summaryState?.text,
        entryTexts = remoteTexts + entries.map { it.entry.body },
        justSaved = viewModel.presenceOverride,
    )
    val hasEntries = entries.isNotEmpty() ||
        page?.entries?.isNotEmpty() == true ||
        (page?.card?.entryCount ?: 0) > 0
    val summaryTitle = DayClock.summaryHeading(viewModel.dayKey)
    val showSummary = summaryCardVisible(brief)
    MessageLayer(host) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DiaryFrame {
                    Column {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPrevious) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
                            }
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.headlineLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            IconButton(onClick = onNext, enabled = canGoNext) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                viewModel.connected && viewModel.linkNote != null -> viewModel.linkNote!!
                                viewModel.connected && page != null -> "已连接"
                                viewModel.connected && viewModel.refreshing -> "正在打开日记本…"
                                viewModel.connected -> "正在连接日记本…"
                                else -> "还没连接日记本。设置里连上之后，能看到陪伴，也会同步。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        viewModel.uploadPhase.line()?.let { line ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            val question = page?.question
            if (question != null && question.text.isNotBlank()) {
                item {
                    DiaryFrame {
                        OnboardingCard(
                            prompt = question.text,
                            progress = "${question.index + 1} / ${question.total.coerceAtLeast(1)}",
                            answer = viewModel.onboardingText,
                            onAnswer = { viewModel.onboardingText = it },
                            busy = viewModel.answering,
                            onSend = { viewModel.submitOnboarding(skip = false) },
                            onSkip = { viewModel.submitOnboarding(skip = true) },
                        )
                    }
                }
            }
            val presence = brief.presence
            if (!presence.isNullOrBlank()) {
                item {
                    DiaryFrame { PresenceCard(presence) }
                }
            }
            if (showSummary) {
                item {
                    DiaryFrame {
                        DaySummaryCard(
                            title = summaryTitle,
                            lines = brief.summaryLines,
                            keywords = brief.keywords,
                            mood = brief.mood,
                        )
                    }
                }
            } else if (hasEntries) {
                item {
                    DiaryFrame {
                        SummaryEmptyCard(
                            title = summaryTitle,
                            copy = summaryEmptyMessage(
                                today = DayClock.isToday(viewModel.dayKey),
                                connected = viewModel.connected,
                                finalized = page?.card?.finalized == true,
                                hasPresence = !presence.isNullOrBlank(),
                            ),
                            action = summaryActionLabel(
                                today = DayClock.isToday(viewModel.dayKey),
                                connected = viewModel.connected,
                                canDraftLocally = viewModel.hasLocalSummaryKey,
                            ),
                            busy = viewModel.summarizing,
                            onAction = viewModel::prepareSummary,
                        )
                    }
                }
            }
            item {
                DiaryFrame {
                    EntryComposer(
                        body = viewModel.body,
                        onBody = { viewModel.body = it },
                        photos = viewModel.pending,
                        photoFile = viewModel::file,
                        importing = viewModel.importing,
                        saving = viewModel.saving,
                        saveLabel = saveLabel,
                        onCaption = viewModel::updateCaption,
                        onRemove = viewModel::removeAt,
                        onCamera = photos.takePhoto,
                        onGallery = photos.pickFromGallery,
                        onSave = viewModel::save,
                    )
                }
            }
            if (showAsk) {
                item {
                    DiaryFrame {
                        AskRow(
                            text = viewModel.askText,
                            onText = { viewModel.askText = it },
                            asking = viewModel.asking,
                            modelReady = page?.configured != false,
                            onAsk = viewModel::ask,
                        )
                    }
                }
            }
            val beats = page?.timeline().orEmpty()
            val showEmpty = beats.isEmpty() && localOnly.isEmpty() && !(viewModel.connected && viewModel.refreshing && page == null)
            if (showEmpty) {
                item {
                    DiaryFrame {
                        Text(
                            emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (beats.isNotEmpty()) {
                item {
                    DiaryFrame {
                        Text("这一天", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(beats, key = { beat ->
                    when (beat) {
                        is app.diary.journal.TimelineItem.Mine -> "e-${beat.entry.id}"
                        is app.diary.journal.TimelineItem.Theirs -> "u-${beat.utterance.id}"
                    }
                }) { beat ->
                    DiaryFrame { JournalBeat(beat, viewModel::file) }
                }
            }
            if (localOnly.isNotEmpty()) {
                item {
                    DiaryFrame {
                        Text(
                            if (page != null) "还在这台手机上" else "这一天",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (viewModel.connected && unsynced) {
                            val running = viewModel.uploadPhase is SyncPhase.Running
                            TextButton(
                                onClick = viewModel::uploadLocals,
                                enabled = !running,
                            ) { Text(if (running) "正在记到日记本…" else "记到日记本") }
                        }
                    }
                }
                items(localOnly, key = { it.entry.id }) { entry ->
                    DiaryFrame {
                        EntryCard(
                            entry = entry,
                            file = viewModel::file,
                            onEdit = { onEdit(entry.entry.id) },
                            onDelete = { pendingDelete = entry.entry.id },
                            allowChange = !entry.entry.cached && !entry.entry.synced,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EntryCard(
    entry: EntryWithPhotos,
    file: (String) -> java.io.File,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    allowChange: Boolean = true,
) {
    QuietCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                DayClock.formatTime(entry.entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.entry.body.isNotBlank()) {
                Text(entry.entry.body, style = MaterialTheme.typography.bodyLarge)
            }
            entry.photos.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { photo ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            DiaryImage(
                                file = file(photo.fileName),
                                contentDescription = photo.caption.ifBlank { "日记图片" },
                                modifier = Modifier.fillMaxWidth().height(if (entry.photos.size == 1) 220.dp else 140.dp),
                            )
                            if (photo.caption.isNotBlank()) {
                                Text(
                                    photo.caption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (row.size == 1 && entry.photos.size > 1) Spacer(Modifier.weight(1f))
                }
            }
            if (allowChange) {
                Row {
                    TextButton(onClick = onEdit) { Text("修改") }
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}

private fun EntryWithPhotos.toLocalRow(): LocalRow = LocalRow(
    key = entry.id,
    cached = entry.cached,
    synced = entry.synced,
    syncedServerDay = entry.syncedServerDay,
    serverIds = buildSet {
        entry.textRemoteId?.let { add(it) }
        entry.remoteId?.let { add(it) }
        photos.forEach { photo -> photo.remoteId?.let { add(it) } }
    },
)
