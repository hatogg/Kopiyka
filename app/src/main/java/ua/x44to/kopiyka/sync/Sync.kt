package ua.x44to.kopiyka.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ua.x44to.kopiyka.BuildConfig
import ua.x44to.kopiyka.data.AppData
import ua.x44to.kopiyka.data.Store
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Спільний бюджет на телефоні: живе з'єднання, поки застосунок відкритий, і обмін у фоні раз на годину.
 * Без інтернету зміни чекають у черзі й надсилаються, щойно з'явиться мережа.
 */
object Sync {
    enum class Phase { OFF, CONNECTING, ONLINE, OFFLINE, DENIED }

    data class Status(
        val phase: Phase = Phase.OFF,
        val code: String? = null,
        val name: String = "",
        val device: String = "",
        val lastSync: Long = 0,
        val pending: Int = 0,
        /** учасники (ідентифікатор телефона → ім'я); цей телефон — першим */
        val members: List<Pair<String, Member>> = emptyList(),
    )

    /** Адреса бази; у тестах можна підмінити (локальний емулятор). */
    var server: String = BuildConfig.SYNC_URL
    var namespace: String? = null
    val configured get() = server.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private var app: Context? = null
    private var file: File? = null
    @Volatile private var state = SyncState()
    @Volatile private var engine: SyncEngine? = null
    private var liveJob: Job? = null
    private var kick = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var foreground = false
    @Volatile private var phase = Phase.OFF
    /** поки створюємо чи приєднуємось — бюджет для інтерфейсу ще не спільний */
    @Volatile private var settingUp = false

    val active get() = state.code != null && engine != null

    /** Ім'я для поля «хто додав» — лише коли бюджет спільний. */
    fun author(): String? = if (active) state.name.ifBlank { null } else null

    private val local = object : LocalData {
        override val data: AppData get() = Store.data.value
        override fun mutateRemote(transform: (AppData) -> AppData) = Store.mutateRemote(transform)
    }

    @Synchronized
    fun init(c: Context) {
        if (app != null) return
        app = c.applicationContext
        val f = File(c.filesDir, "sync.json")
        file = f
        state = try {
            if (f.exists()) json.decodeFromString(SyncState.serializer(), f.readText()) else SyncState()
        } catch (_: Exception) {
            SyncState()
        }
        if (state.device.isEmpty()) persist(state.copy(device = UUID.randomUUID().toString().replace("-", "").take(16)))
        state.code?.let { if (configured) engine = makeEngine(it) }
        Store.onLocalChange = { prev, next ->
            if (engine?.localChanged(prev, next) == true) {
                kick.trySend(Unit)
                if (!foreground) app?.let { flushLater(it) }
            }
            publish()
        }
        publish()
    }

    private fun makeEngine(code: String) = SyncEngine(
        FirebaseRemote(server, BudgetCode.budgetId(code), namespace), code, local, state,
        save = { persist(it) },
    )

    private fun persist(s: SyncState) {
        state = s
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(SyncState.serializer(), s))
            if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
        } catch (_: Exception) {
        }
        publish()
    }

    private fun publish(p: Phase = phase) {
        phase = if (state.code == null || settingUp) Phase.OFF else p
        val s = state
        _status.value = Status(
            phase, if (settingUp) null else s.code, s.name, s.device, s.lastSync, s.pending.size,
            s.members.entries.sortedWith(compareBy({ if (it.key == s.device) 0 else 1 }, { it.value.name })).map { it.key to it.value },
        )
    }

    // ───────── життєвий цикл ─────────

    /** Застосунок на екрані: тримаємо живе з'єднання. */
    @Synchronized
    fun start() {
        foreground = true
        val e = engine ?: return
        if (liveJob?.isActive == true) return
        kick = Channel(Channel.CONFLATED)
        liveJob = scope.launch { live(e) }
    }

    /** Застосунок згорнули: з'єднання закриваємо, недоіслане — доженемо у фоні. */
    @Synchronized
    fun stop() {
        foreground = false
        halt()
        val c = app ?: return
        if (state.pending.isNotEmpty()) flushLater(c)
    }

    /** Закрити живе з'єднання (позначку «застосунок на екрані» не чіпаємо). */
    @Synchronized
    private fun halt() {
        liveJob?.cancel()
        liveJob = null
        engine?.cancel()
    }

    private suspend fun live(e: SyncEngine) = kotlinx.coroutines.coroutineScope {
        val sender = launch {
            for (x in kick) {
                try {
                    if (e.push() > 0) publish(Phase.ONLINE)
                } catch (_: AccessDenied) {
                    publish(Phase.DENIED)
                } catch (_: IOException) {
                    publish(Phase.OFFLINE)
                }
            }
        }
        var backoff = 2_000L
        try {
            while (isActive) {
                try {
                    publish(if (phase == Phase.ONLINE) Phase.ONLINE else Phase.CONNECTING)
                    try { e.announce() } catch (_: IOException) {}
                    e.listen({ !isActive }) { full ->
                        backoff = 2_000L
                        publish(Phase.ONLINE)
                        if (full) kick.trySend(Unit) // після (пере)підключення досилаємо чергу
                    }
                } catch (_: AccessDenied) {
                    publish(Phase.DENIED)
                    delay(60_000)
                } catch (_: IOException) {
                    if (!isActive) break
                    publish(Phase.OFFLINE)
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000)
                }
            }
        } finally {
            sender.cancel()
        }
    }

    /** Разовий обмін (у фоні або перед перевіркою регулярних платежів). */
    fun syncOnceBlocking(): Boolean {
        val e = engine ?: return false
        return try {
            e.syncOnce()
            publish()
            true
        } catch (_: IOException) {
            false
        }
    }

    fun schedule(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (state.code == null) {
            wm.cancelUniqueWork("sync")
            return
        }
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            "sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(net).build(),
        )
    }

    private fun flushLater(c: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(c).enqueueUniqueWork(
            "sync-flush", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).setInitialDelay(5, TimeUnit.SECONDS).build(),
        )
    }

    // ───────── створити / приєднатися / вийти ─────────

    class JoinError(val notFound: Boolean, cause: Throwable? = null) : Exception(cause)

    /** Новий спільний бюджет із поточних даних. Повертає код. */
    suspend fun create(name: String): String = withContext(Dispatchers.IO) {
        val c = app ?: error("Sync.init")
        halt()
        val code = BudgetCode.generate()
        val old = state
        settingUp = true
        persist(old.copy(code = code, name = name.trim(), known = emptyMap(), pending = emptyMap(), members = emptyMap(), lastSync = 0))
        val e = makeEngine(code)
        engine = e // правки, зроблені під час завантаження, теж потраплять у чергу
        try {
            e.create()
        } catch (ex: Exception) {
            engine = null
            settingUp = false
            persist(old)
            throw ex
        }
        settingUp = false
        publish(Phase.ONLINE)
        schedule(c)
        if (foreground) start()
        code
    }

    /** Приєднатися за кодом: дані на телефоні замінюються спільними (копію старих кладемо поруч). */
    suspend fun join(input: String, name: String): Unit = withContext(Dispatchers.IO) {
        val c = app ?: error("Sync.init")
        val code = BudgetCode.normalize(input) ?: throw JoinError(notFound = true)
        halt()
        try { File(c.filesDir, "before-shared.json").writeText(Store.exportJson()) } catch (_: Exception) {}
        val old = state
        settingUp = true
        persist(old.copy(code = code, name = name.trim(), known = emptyMap(), pending = emptyMap(), members = emptyMap(), lastSync = 0))
        val e = makeEngine(code)
        val ok = try {
            e.join()
        } catch (ex: Exception) {
            settingUp = false
            persist(old)
            throw JoinError(notFound = false, ex)
        }
        if (!ok) {
            settingUp = false
            persist(old)
            throw JoinError(notFound = true)
        }
        engine = e
        settingUp = false
        publish(Phase.ONLINE)
        schedule(c)
        if (foreground) start()
    }

    /** Вийти зі спільного бюджету: дані лишаються на телефоні, але більше не синхронізуються. */
    fun leave() {
        halt()
        engine = null
        persist(state.copy(code = null, known = emptyMap(), pending = emptyMap(), members = emptyMap(), lastSync = 0))
        app?.let { schedule(it) }
    }

    fun rename(name: String) {
        persist(state.copy(name = name.trim()))
        val e = engine ?: return
        scope.launch { try { e.announce() } catch (_: IOException) {} }
    }
}

/** Фоновий обмін: раз на годину й після змін, зроблених без мережі. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        if (!Sync.active) return Result.success()
        return if (Sync.syncOnceBlocking()) Result.success() else Result.retry()
    }
}
