package com.netwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyUsageEntity): Long

    @Query("SELECT * FROM daily_usage WHERE dateEpochDay BETWEEN :startDay AND :endDay ORDER BY dateEpochDay ASC")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE dateEpochDay BETWEEN :startDay AND :endDay ORDER BY dateEpochDay ASC")
    suspend fun getRange(startDay: Long, endDay: Long): List<DailyUsageEntity>

    @Query("SELECT * FROM daily_usage WHERE dateEpochDay = :day AND kind = :kind AND identityKey = :identityKey LIMIT 1")
    suspend fun find(day: Long, kind: String, identityKey: String): DailyUsageEntity?

    @Query("DELETE FROM daily_usage")
    suspend fun clear()
}

@Dao
interface AttributedDeltaDao {
    @Insert
    suspend fun insert(entity: AttributedDeltaEntity): Long

    @Query(
        """
        SELECT slotIndex, subscriptionId, identityKey,
               SUM(rxBytes) AS rxBytes, SUM(txBytes) AS txBytes
        FROM attributed_deltas
        WHERE timestampMs >= :startMs AND timestampMs < :endMs
          AND identityKey = :identityKey
        GROUP BY slotIndex, subscriptionId, identityKey
        """,
    )
    suspend fun sumForIdentity(identityKey: String, startMs: Long, endMs: Long): AttributedSum?

    @Query(
        """
        SELECT slotIndex, subscriptionId, identityKey,
               SUM(rxBytes) AS rxBytes, SUM(txBytes) AS txBytes
        FROM attributed_deltas
        WHERE timestampMs >= :startMs AND timestampMs < :endMs
        GROUP BY slotIndex, subscriptionId, identityKey
        """,
    )
    suspend fun sumAll(startMs: Long, endMs: Long): List<AttributedSum>

    @Query("DELETE FROM attributed_deltas")
    suspend fun clear()
}

data class AttributedSum(
    val slotIndex: Int?,
    val subscriptionId: Int?,
    val identityKey: String,
    val rxBytes: Long,
    val txBytes: Long,
)

@Dao
interface AlertEventDao {
    @Insert
    suspend fun insert(entity: AlertEventEntity): Long

    @Query("SELECT * FROM alert_events ORDER BY triggeredAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events ORDER BY triggeredAtMs DESC")
    suspend fun getAll(): List<AlertEventEntity>

    @Query("DELETE FROM alert_events")
    suspend fun clear()
}

@Dao
interface HotspotSessionDao {
    @Insert
    suspend fun insert(entity: HotspotSessionEntity): Long

    @Update
    suspend fun update(entity: HotspotSessionEntity)

    @Query("SELECT * FROM hotspot_sessions WHERE endedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun findOpen(): HotspotSessionEntity?

    @Query("SELECT * FROM hotspot_sessions ORDER BY startedAtMs DESC")
    fun observeAll(): Flow<List<HotspotSessionEntity>>

    @Query("SELECT * FROM hotspot_sessions ORDER BY startedAtMs DESC")
    suspend fun getAll(): List<HotspotSessionEntity>

    @Query("DELETE FROM hotspot_sessions")
    suspend fun clear()
}

@Dao
interface SimIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SimIdentityEntity)

    @Query("SELECT * FROM sim_identities WHERE slotIndex = :slotIndex")
    suspend fun forSlot(slotIndex: Int): List<SimIdentityEntity>

    @Query("SELECT * FROM sim_identities")
    suspend fun getAll(): List<SimIdentityEntity>

    @Query("DELETE FROM sim_identities")
    suspend fun clear()
}

@Dao
interface SimChangeEventDao {
    @Insert
    suspend fun insert(entity: SimChangeEventEntity): Long

    @Query("SELECT * FROM sim_change_events ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SimChangeEventEntity>>

    @Query("DELETE FROM sim_change_events")
    suspend fun clear()
}
