package com.anant.freescale.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MeasurementEntity>): List<Long>

    @Query("SELECT * FROM measurements ORDER BY recordedAtEpochMs DESC LIMIT 1")
    suspend fun latest(): MeasurementEntity?

    @Query("SELECT * FROM measurements ORDER BY recordedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<MeasurementEntity>>

    @Query(
        """
        SELECT * FROM measurements
        WHERE recordedAtEpochMs >= :fromEpochMs AND recordedAtEpochMs < :toEpochMs
        ORDER BY recordedAtEpochMs ASC
        """,
    )
    fun observeInRange(fromEpochMs: Long, toEpochMs: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements ORDER BY recordedAtEpochMs DESC")
    fun observeAllNewestFirst(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements ORDER BY recordedAtEpochMs ASC")
    suspend fun getAllOldestFirst(): List<MeasurementEntity>

    @Query("SELECT recordedAtEpochMs FROM measurements")
    suspend fun allRecordedAtEpochMs(): List<Long>

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM measurements")
    fun observeCount(): Flow<Int>
}
