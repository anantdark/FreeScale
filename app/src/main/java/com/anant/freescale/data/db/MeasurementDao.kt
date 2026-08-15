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

    @Query("SELECT * FROM measurements ORDER BY recordedAtEpochMs DESC LIMIT 1")
    suspend fun latest(): MeasurementEntity?

    @Query("SELECT * FROM measurements ORDER BY recordedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<MeasurementEntity>>

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun count(): Int
}
