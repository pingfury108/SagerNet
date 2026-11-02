package io.nekohasekai.sagernet.database

import android.content.Context
import androidx.room.*
import io.nekohasekai.sagernet.SagerNet
import java.io.File

/**
 * One outbound flow recorded by the tun entry (see library/core/conns.go).
 * Stored in its own database so no migration of the profile/rule databases
 * is needed.
 */
@Entity(
    tableName = "conn_events",
    indices = [Index("time"), Index("domain"), Index("packageName")]
)
data class ConnEntity(
    @PrimaryKey var id: Long = 0L,
    var time: Long = 0L,
    var uid: Int = 0,
    var packageName: String = "",
    var label: String = "",
    var domain: String = "",
    var ip: String = "",
    var port: Int = 0,
    var network: String = "",
    var outbound: String = "",
    var upload: Long = 0L,
    var download: Long = 0L,
    var duration: Long = 0L,
)

@Dao
interface ConnDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(events: List<ConnEntity>)

    @Query("SELECT * FROM conn_events ORDER BY time DESC LIMIT :limit")
    fun recent(limit: Int): List<ConnEntity>

    @Query("SELECT * FROM conn_events WHERE time >= :since ORDER BY time DESC")
    fun since(since: Long): List<ConnEntity>

    @Query(
        "SELECT domain AS name, COUNT(*) AS count, SUM(upload) AS upload, SUM(download) AS download, " +
                "MIN(uid) AS uid FROM conn_events WHERE domain != '' AND time >= :since " +
                "GROUP BY domain ORDER BY count DESC LIMIT :limit"
    )
    fun topDomains(since: Long, limit: Int): List<AggregateRow>

    @Query(
        "SELECT (CASE WHEN label != '' THEN label ELSE packageName END) AS name, COUNT(*) AS count, " +
                "SUM(upload) AS upload, SUM(download) AS download, MIN(uid) AS uid " +
                "FROM conn_events WHERE time >= :since " +
                "GROUP BY packageName, uid ORDER BY count DESC LIMIT :limit"
    )
    fun topApps(since: Long, limit: Int): List<AggregateRow>

    @Query("DELETE FROM conn_events WHERE time < :before")
    fun deleteBefore(before: Long)

    @Query("DELETE FROM conn_events")
    fun clear()

    @Query("SELECT COUNT(*) FROM conn_events")
    fun count(): Long
}

data class AggregateRow(
    var name: String = "",
    var count: Long = 0,
    var upload: Long = 0,
    var download: Long = 0,
    var uid: Int = 0,
)

@Database(entities = [ConnEntity::class], version = 1, exportSchema = false)
abstract class ConnDatabase : RoomDatabase() {

    abstract fun connDao(): ConnDao

    companion object {
        @Volatile
        private var instance: ConnDatabase? = null

        fun get(context: Context = SagerNet.application): ConnDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context, ConnDatabase::class.java, "conn_events.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}

object ConnStore {

    private const val RETENTION_DAYS = 7L

    private val dao get() = ConnDatabase.get().connDao()

    fun insert(events: List<ConnEntity>) {
        if (events.isEmpty()) return
        dao.insertAll(events)
    }

    fun recent(limit: Int = 5000) = dao.recent(limit)

    fun since(hours: Int) = dao.since(System.currentTimeMillis() - hours * 3_600_000L)

    fun topDomains(hours: Int = 24, limit: Int = 200) =
        dao.topDomains(System.currentTimeMillis() - hours * 3_600_000L, limit)

    fun topApps(hours: Int = 24, limit: Int = 100) =
        dao.topApps(System.currentTimeMillis() - hours * 3_600_000L, limit)

    fun cleanup() {
        dao.deleteBefore(System.currentTimeMillis() - RETENTION_DAYS * 24 * 3_600_000L)
    }

    fun clear() = dao.clear()

    fun count() = dao.count()

    /** CSV export, returns the written file. */
    fun exportCsv(hours: Int = 24 * 7): File {
        val file = File(SagerNet.application.cacheDir, "conn-events.csv")
        file.bufferedWriter().use { out ->
            out.write("time,app,package,domain,ip,port,network,outbound,upload,download,duration_ms\n")
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            for (row in since(hours.coerceAtMost(24 * 7))) {
                val app = row.label.ifBlank { row.packageName }
                out.write(
                    listOf(
                        fmt.format(java.util.Date(row.time)),
                        csv(app), csv(row.packageName), csv(row.domain), row.ip, row.port.toString(),
                        row.network, row.outbound, row.upload.toString(), row.download.toString(),
                        row.duration.toString()
                    ).joinToString(",")
                )
                out.write("\n")
            }
        }
        return file
    }

    private fun csv(value: String): String {
        if (value.isBlank()) return ""
        return if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }
}
