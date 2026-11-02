package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.database.ConnEntity
import io.nekohasekai.sagernet.database.ConnStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Receives batched connection events from :bg, keeps a bounded in-memory list
 * for the live UI and persists finished flows for the history/statistics views.
 */
object ConnMonitor {

    private const val MEMORY_LIMIT = 5000

    fun interface Listener {
        fun onConnectionsChanged()
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    /** Newest first. Guarded by [lock]. */
    private val rows = LinkedHashMap<Long, ConnEntity>()
    private val lock = Any()

    @Volatile
    var paused = false

    fun register(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun unregister(listener: Listener) {
        listeners.remove(listener)
    }

    fun snapshot(): List<ConnEntity> = synchronized(lock) { rows.values.toList() }

    fun clear() {
        synchronized(lock) { rows.clear() }
        runOnDefaultDispatcher { ConnStore.clear() }
        notifyChanged()
    }

    /** Called from the AIDL callback (main process). */
    fun onEvents(json: String) {
        val parsed = try {
            val array = JSONArray(json)
            buildList<ConnEntity>(array.length()) {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        ConnEntity(
                            id = item.optLong("id"),
                            time = item.optLong("time"),
                            uid = item.optInt("uid"),
                            packageName = item.optString("pkg"),
                            label = item.optString("label"),
                            domain = item.optString("domain"),
                            ip = item.optString("ip"),
                            port = item.optInt("port"),
                            network = item.optString("network"),
                            outbound = item.optString("outbound"),
                            upload = item.optLong("upload"),
                            download = item.optLong("download"),
                            duration = item.optLong("duration"),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Logs.w(e)
            return
        }
        if (parsed.isEmpty()) return

        val finished = ArrayList<ConnEntity>()
        synchronized(lock) {
            for (event in parsed) {
                if (paused) continue
                if (event.time == 0L) event.time = System.currentTimeMillis()
                val existing = rows[event.id]
                var updated: ConnEntity? = null
                if (existing == null) {
                    rows[event.id] = event
                } else {
                    // "closed" event completes the row opened earlier
                    existing.upload = event.upload
                    existing.download = event.download
                    existing.duration = event.duration
                    if (event.domain.isNotBlank()) existing.domain = event.domain
                    if (event.outbound.isNotBlank()) existing.outbound = event.outbound
                    updated = existing
                }
                val done = updated ?: event
                // persist only completed flows; the "open" event has zero counters
                if (done.duration > 0 || done.upload > 0 || done.download > 0) {
                    finished.add(done)
                }
            }
            while (rows.size > MEMORY_LIMIT) {
                val oldest = rows.keys.firstOrNull() ?: break
                rows.remove(oldest)
            }
        }

        if (finished.isNotEmpty()) {
            runOnDefaultDispatcher {
                ConnStore.insert(finished)
            }
        }
        notifyChanged()
    }

    fun cleanup() {
        runOnDefaultDispatcher {
            ConnStore.cleanup()
        }
    }

    private fun notifyChanged() {
        for (listener in listeners) {
            try {
                listener.onConnectionsChanged()
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }
}
