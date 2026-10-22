package ua.x44to.kopiyka.sync

import kotlinx.serialization.Serializable
import ua.x44to.kopiyka.data.AppData

/** Запис на сервері: час зміни (мс) і зашифрований вміст; без вмісту — запис видалено. */
@Serializable
data class RemoteEntry(val t: Long, val d: String? = null)

/** Сервер спільного бюджету (Firebase Realtime Database або підробка в тестах). */
interface Remote {
    /** Усі записи бюджету; порожньо — такого бюджету немає. Без мережі кидає IOException. */
    fun getAll(): Map<String, RemoteEntry>

    /** Записати кілька записів одним запитом. */
    fun patch(entries: Map<String, RemoteEntry>)

    /**
     * Слухати зміни, доки не обірветься з'єднання або [stop] не поверне true.
     * Перша подія — повний знімок (full = true).
     */
    fun listen(onChange: (entries: Map<String, RemoteEntry>, full: Boolean) -> Unit, stop: () -> Boolean)

    /** Обірвати [listen] з іншого потоку (застосунок згорнули). */
    fun cancel() {}
}

@Serializable
data class Member(val name: String, val seen: Long = 0)

/** Що пам'ятає телефон про спільний бюджет. Зберігається окремо від самих даних. */
@Serializable
data class SyncState(
    /** код бюджету; null — бюджет не спільний */
    val code: String? = null,
    /** випадковий ідентифікатор цього телефона */
    val device: String = "",
    /** ім'я, яке бачать інші учасники */
    val name: String = "",
    /** версія (час) кожного запису, яку ми вже маємо */
    val known: Map<String, Long> = emptyMap(),
    /** записи, змінені тут і ще не надіслані: ключ → час зміни */
    val pending: Map<String, Long> = emptyMap(),
    /** учасники: ідентифікатор телефона → ім'я */
    val members: Map<String, Member> = emptyMap(),
    /** коли востаннє вдалося обмінятися з сервером */
    val lastSync: Long = 0,
)

/** Локальне сховище очима синхронізації. */
interface LocalData {
    val data: AppData

    /** Змінити дані під замком сховища, не позначаючи зміни як «надіслати» (вони прийшли ззовні). */
    fun mutateRemote(transform: (AppData) -> AppData)
}

/**
 * Синхронізація «запис за записом», перемагає новіша зміна (last-writer-wins за часом зміни).
 *
 * Порядок замків завжди «сховище → рушій»: локальні зміни приходять зі сховища під його замком,
 * а чужі застосовуються через [LocalData.mutateRemote] — теж під замком сховища.
 */
class SyncEngine(
    private val remote: Remote,
    code: String,
    private val local: LocalData,
    initial: SyncState,
    private val save: (SyncState) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val seal = Seal(code)
    private val lock = Any()

    @Volatile
    var state: SyncState = initial
        private set

    private fun set(s: SyncState) {
        state = s
        save(s)
    }

    val pendingCount get() = state.pending.size

    /** Викликається сховищем після кожної локальної зміни. Повертає true, якщо є що надсилати. */
    fun localChanged(prev: AppData, next: AppData): Boolean {
        val keys = Records.changed(prev, next).filter(Records::validKey)
        if (keys.isEmpty()) return false
        synchronized(lock) {
            val now = clock()
            val p = state.pending.toMutableMap()
            // час зміни більший за будь-яку відому версію запису — інакше чужий годинник, що поспішає, «переміг» би нашу правку
            for (k in keys) p[k] = maxOf(now, (state.known[k] ?: 0) + 1, (p[k] ?: 0) + 1)
            set(state.copy(pending = p))
        }
        return true
    }

    /** Надіслати все, що чекає. Повертає кількість записів. Без мережі кидає IOException (зміни лишаються в черзі). */
    fun push(): Int {
        val batch: Map<String, Long>
        val entries: Map<String, RemoteEntry>
        synchronized(lock) {
            batch = state.pending.toMap()
            if (batch.isEmpty()) return 0
            val d = local.data
            entries = batch.mapValues { (k, ts) -> RemoteEntry(ts, Records.encode(d, k)?.let(seal::seal)) }
        }
        for (chunk in entries.entries.chunked(400)) remote.patch(chunk.associate { it.key to it.value })
        synchronized(lock) {
            val p = state.pending.toMutableMap()
            val known = state.known.toMutableMap()
            for ((k, ts) in batch) {
                if (p[k] == ts) p.remove(k)
                if ((known[k] ?: 0) < ts) known[k] = ts
            }
            set(state.copy(pending = p, known = known, lastSync = clock()))
        }
        return batch.size
    }

    /** Повідомити інших, що цей телефон у бюджеті (ім'я в списку учасників). */
    fun announce() {
        val s = state
        if (s.device.isEmpty()) return
        val m = Member(s.name.ifBlank { "?" }, clock())
        remote.patch(mapOf("m_${s.device}" to RemoteEntry(m.seen, seal.seal(Records.json.encodeToString(Member.serializer(), m)))))
        synchronized(lock) { set(state.copy(members = state.members + (s.device to m))) }
    }

    /** Застосувати записи з сервера. [full] — це повний знімок бюджету. */
    fun receive(entries: Map<String, RemoteEntry>, full: Boolean = false) {
        local.mutateRemote { d ->
            synchronized(lock) {
                val changes = HashMap<String, String?>()
                val known = state.known.toMutableMap()
                val pending = state.pending.toMutableMap()
                val members = state.members.toMutableMap()
                for ((k, e) in entries) {
                    if (k.startsWith("m_")) {
                        val id = k.removePrefix("m_")
                        val m = e.d?.let(seal::open)?.let { runCatching { Records.json.decodeFromString(Member.serializer(), it) }.getOrNull() }
                        if (m != null) members[id] = m else if (e.d == null) members.remove(id)
                        continue
                    }
                    if (!Records.validKey(k)) continue
                    val mine = pending[k]
                    if (mine != null && mine >= e.t) continue // наша правка новіша — вона й поїде на сервер
                    if (mine == null && (known[k] ?: -1) >= e.t) continue // це ми вже маємо (зокрема власне відлуння)
                    val plain = if (e.d == null) null else (seal.open(e.d) ?: continue) // битий або чужий шифр — пропускаємо
                    changes[k] = plain
                    known[k] = e.t
                    pending.remove(k)
                }
                val next = if (changes.isEmpty()) d else Records.apply(d, changes)
                if (full) {
                    // запис є тут, але зник із сервера (напр., базу почистили) — надішлемо його знову
                    val now = clock()
                    for (k in Records.keys(next)) if (k !in entries && k !in pending && Records.validKey(k)) pending[k] = maxOf(now, (known[k] ?: 0) + 1)
                }
                set(state.copy(known = known, pending = pending, members = members, lastSync = clock()))
                next
            }
        }
    }

    /** Разовий обмін: забрати зміни й надіслати свої. */
    fun syncOnce() {
        receive(remote.getAll(), full = true)
        push()
    }

    /** Слухати сервер, доки не обірветься з'єднання (тоді — IOException) або [stop]. [onEvent] — після кожної порції змін. */
    fun listen(stop: () -> Boolean, onEvent: (full: Boolean) -> Unit = {}) = remote.listen({ e, full -> receive(e, full); onEvent(full) }, stop)

    fun cancel() = remote.cancel()

    /** Новий спільний бюджет із поточних даних цього телефона. */
    fun create() {
        synchronized(lock) {
            val now = clock()
            set(state.copy(known = emptyMap(), pending = Records.keys(local.data).filter(Records::validKey).associateWith { now }))
        }
        push()
        announce()
    }

    /**
     * Приєднатися до бюджету: забрати його з сервера й замінити ним локальні дані
     * (особисті налаштування — тема, сповіщення — лишаються). false — бюджету з таким кодом немає.
     */
    fun join(): Boolean {
        val all = remote.getAll()
        val recs = all.filterKeys { !it.startsWith("m_") && Records.validKey(it) }
        if (recs.isEmpty()) return false
        val opened = recs.mapValues { (_, e) -> e.d?.let(seal::open) }
        // нічого не розшифрувалось — це не наш бюджет
        if (opened.values.none { it != null }) return false
        local.mutateRemote { d ->
            synchronized(lock) {
                set(state.copy(known = recs.mapValues { it.value.t }, pending = emptyMap()))
                Records.apply(AppData(settings = d.settings), opened.filterValues { it != null })
            }
        }
        receive(all.filterKeys { it.startsWith("m_") })
        announce()
        return true
    }
}
