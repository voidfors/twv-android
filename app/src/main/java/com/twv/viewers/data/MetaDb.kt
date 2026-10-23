package com.twv.viewers.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.twv.viewers.CachedMeta
import com.twv.viewers.Meta
import com.twv.viewers.MetaCache

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val login: String,
    val displayName: String,
    val avatarUrl: String?,
    val createdAt: String?,
    val followers: Int?,
    val updatedAt: Long
)

@Dao
interface MetaDao {
    @Query("SELECT * FROM meta WHERE login = :login LIMIT 1")
    suspend fun get(login: String): MetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: MetaEntity)

    /** Чистка старых записей, чтобы кэш не рос бесконечно. */
    @Query("DELETE FROM meta WHERE updatedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}

@Database(entities = [MetaEntity::class], version = 1, exportSchema = false)
abstract class MetaDb : RoomDatabase() {
    abstract fun dao(): MetaDao
}

/** Реализация [MetaCache] поверх Room. Ошибки диска не должны ломать сбор зрителей. */
class RoomMetaCache(private val dao: MetaDao) : MetaCache {
    override suspend fun get(login: String): CachedMeta? = try {
        dao.get(login)?.let {
            CachedMeta(Meta(it.displayName, it.avatarUrl, it.createdAt, it.followers), it.updatedAt)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override suspend fun put(login: String, meta: Meta, updatedAt: Long) {
        try {
            dao.put(MetaEntity(login, meta.displayName, meta.avatarUrl, meta.createdAt, meta.followers, updatedAt))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun cleanup(now: Long, maxAgeMs: Long = 30L * 24 * 3600 * 1000) {
        try { dao.deleteOlderThan(now - maxAgeMs) } catch (_: Exception) {}
    }
}
