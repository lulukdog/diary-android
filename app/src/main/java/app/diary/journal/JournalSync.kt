package app.diary.journal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SyncPhase {
    data object Idle : SyncPhase()
    data class Running(val done: Int, val total: Int) : SyncPhase()
    data class Finished(val summary: String) : SyncPhase()
}

fun SyncPhase.line(): String? = when (this) {
    SyncPhase.Idle -> null
    is SyncPhase.Running -> if (total > 0) {
        "正在把手机上的日记记到日记本（$done/$total）…"
    } else {
        "正在看还有哪些日记没同步…"
    }
    is SyncPhase.Finished -> summary
}

class JournalSync(private val journal: JournalRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val phase = MutableStateFlow<SyncPhase>(SyncPhase.Idle)
    val status: StateFlow<SyncPhase> = phase
    private var job: Job? = null

    fun start(announceIfEmpty: Boolean) {
        if (job?.isActive == true) return
        if (!journal.configured()) {
            if (announceIfEmpty) {
                phase.value = SyncPhase.Finished("先保存日记本口令")
            }
            return
        }
        job = scope.launch {
            phase.value = SyncPhase.Running(0, 0)
            try {
                val report = journal.uploadUnsynced { done, total ->
                    phase.value = SyncPhase.Running(done, total)
                }
                val summary = report.summary(announceIfEmpty)
                phase.value = if (summary == null) SyncPhase.Idle else SyncPhase.Finished(summary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                phase.value = SyncPhase.Finished(error.message ?: "没有记上去")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        phase.value = SyncPhase.Idle
    }
}

fun UploadReport.summary(announceIfEmpty: Boolean): String? {
    if (uploaded == 0 && failed == 0) {
        return if (announceIfEmpty) "没有还要上传的日记。已经记过的不会再传。" else null
    }
    if (failed == 0) {
        return "已把 $uploaded 则记到日记本。已经记过的不会再传。日记本按现在的时间收下，较早的日子在手机上还留着。"
    }
    val why = error?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return "记上去 $uploaded 则，还有 $failed 则没传上。$why".trim()
}
