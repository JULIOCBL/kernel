package kernel.session

import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import kernel.foundation.app
import java.nio.file.Path
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

object Session {
    private object NullValue : Serializable

    private val state = ConcurrentHashMap<String, Any?>()
    private val stateLock = Any()
    private val initLock = Any()
    private val saveLock = Any()

    @Volatile
    private var driver: SessionDriver? = null

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var stateVersion: Long = 0

    @Volatile
    private var persistedVersion: Long = 0

    fun bootstrap(
        application: Application = app(),
        driver: SessionDriver = resolveDriver(application)
    ) {
        synchronized(initLock) {
            val shouldReload = this.driver !== driver || !initialized
            this.driver = driver

            if (!shouldReload) {
                initialized = true
                return
            }

            loadFromDriver(driver)
            initialized = true
        }
    }

    fun put(key: String, value: Any?) {
        ensureInitialized()
        val version = synchronized(stateLock) {
            state[key] = wrapValue(value)
            stateVersion += 1
            stateVersion
        }

        persistLatest(version)
    }

    inline fun <reified T> get(key: String): T? {
        ensureInitialized()
        return castValue(valueFor(key))
    }

    inline fun <reified T> getOrDefault(key: String, default: T): T {
        return get<T>(key) ?: default
    }

    fun has(key: String): Boolean {
        ensureInitialized()
        return state.containsKey(key)
    }

    fun remove(key: String) {
        ensureInitialized()
        val version = synchronized(stateLock) {
            state.remove(key)
            stateVersion += 1
            stateVersion
        }

        persistLatest(version)
    }

    fun clear() {
        ensureInitialized()
        val version = synchronized(stateLock) {
            state.clear()
            stateVersion += 1
            stateVersion
        }

        persistLatest(version)
    }

    fun all(): Map<String, Any?> {
        ensureInitialized()
        return snapshot()
    }

    fun save() {
        ensureInitialized()
        persistLatest(stateVersion)
    }

    fun reload() {
        ensureInitialized()
        val currentDriver = driver ?: return

        synchronized(saveLock) {
            runCatching {
                loadFromDriver(currentDriver)
            }
        }
    }

    fun toJson(): String {
        ensureInitialized()
        return SessionSerializer.toJson(snapshot())
    }

    internal fun resetForTests() {
        synchronized(initLock) {
            synchronized(stateLock) {
                state.clear()
                stateVersion = 0
                persistedVersion = 0
            }

            driver = null
            initialized = false
        }
    }

    @PublishedApi
    internal fun valueFor(key: String): Any? {
        return unwrapValue(state[key])
    }

    @PublishedApi
    internal inline fun <reified T> castValue(value: Any?): T? {
        return when {
            value == null -> null
            value is T -> value
            else -> null
        }
    }

    @PublishedApi
    internal fun ensureInitialized() {
        if (initialized) {
            return
        }

        check(ApplicationRuntime.isInitialized()) {
            "Session no ha sido inicializada. Inicializa la Application runtime o llama Session.bootstrap(app)."
        }

        bootstrap(app())
    }

    private fun persistLatest(requestedVersion: Long) {
        val currentDriver = driver ?: return

        synchronized(saveLock) {
            val (version, snapshot) = synchronized(stateLock) {
                val latestVersion = stateVersion
                if (latestVersion == persistedVersion && requestedVersion <= persistedVersion) {
                    return
                }

                latestVersion to snapshotUnlocked()
            }

            runCatching {
                currentDriver.save(snapshot)
            }.onSuccess {
                synchronized(stateLock) {
                    if (version > persistedVersion) {
                        persistedVersion = version
                    }
                }
            }
        }
    }

    private fun loadFromDriver(targetDriver: SessionDriver) {
        val loadedState = runCatching { targetDriver.load() }
            .getOrDefault(emptyMap())

        synchronized(stateLock) {
            state.clear()
            loadedState.forEach { (key, value) ->
                state[key] = wrapValue(value)
            }
            stateVersion += 1
            persistedVersion = stateVersion
        }
    }

    private fun snapshot(): Map<String, Any?> = synchronized(stateLock) {
        snapshotUnlocked()
    }

    private fun snapshotUnlocked(): Map<String, Any?> {
        return linkedMapOf<String, Any?>().apply {
            state.entries
                .sortedBy { it.key }
                .forEach { (key, value) -> put(key, unwrapValue(value)) }
        }
    }

    private fun defaultPath(application: Application): Path {
        return application.path(
            application.config.string("session.path", "storage/session/session.dat")
        )
    }

    private fun resolveDriver(application: Application): SessionDriver {
        val driverName = application.config.string("session.driver", "file").lowercase()
        val encrypt = application.config.bool("session.encrypt", true)

        return when (driverName) {
            "file" -> FileSessionDriver(
                path = defaultPath(application),
                encrypt = encrypt
            )
            else -> error("El driver de session `$driverName` no esta soportado.")
        }
    }

    private fun wrapValue(value: Any?): Any? {
        return value ?: NullValue
    }

    private fun unwrapValue(value: Any?): Any? {
        return if (value === NullValue) null else value
    }
}
