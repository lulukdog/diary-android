package app.diary

import android.app.Application
import android.content.Context
import app.diary.ai.DeepSeekClient
import app.diary.data.DiaryDatabase
import app.diary.data.DiaryRepository
import app.diary.data.PhotoStore
import app.diary.export.DiaryExporter
import app.diary.journal.JournalClient
import app.diary.journal.JournalRepository
import app.diary.journal.JournalSync
import app.diary.localapi.LocalDiaryServer
import app.diary.settings.AppSettings
import app.diary.update.UpdateRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DiaryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val appContext: Context = app.applicationContext
    val settings = AppSettings(app)
    val photos = PhotoStore(app)
    private val database = DiaryDatabase.create(app)
    val repository = DiaryRepository(
        dao = database.diaryDao(),
        photos = photos,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
    )
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
    val deepSeek = DeepSeekClient(http)
    val journal = JournalRepository(settings, JournalClient(http), repository, photos)
    val journalSync = JournalSync(journal)
    val updates = UpdateRepository(app, http, settings, BuildConfig.VERSION_CODE)
    val exporter = DiaryExporter(app, repository, photos)
    val localServer = LocalDiaryServer(repository, settings)

    init {
        Thread {
            runCatching { runBlocking { repository.sweepStaleFiles() } }
        }.apply {
            name = "diary-sweep"
            isDaemon = true
            start()
        }
        if (settings.localApiEnabled() && settings.secureAvailable && settings.token().isNotBlank()) {
            localServer.start()
        }
        if (journal.configured()) {
            journalSync.start(announceIfEmpty = false)
        }
    }
}
