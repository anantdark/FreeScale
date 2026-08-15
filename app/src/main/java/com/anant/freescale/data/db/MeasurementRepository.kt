package com.anant.freescale.data.db

import android.content.Context
import com.anant.freescale.data.ScaleMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeasurementRepository(context: Context) {
    private val dao = FreeScaleDatabase.get(context).measurementDao()

    suspend fun save(measurement: ScaleMeasurement): Long =
        dao.insert(MeasurementEntity.fromDomain(measurement))

    suspend fun latest(): ScaleMeasurement? =
        dao.latest()?.toDomain()

    fun observeRecent(limit: Int = 200): Flow<List<ScaleMeasurement>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    fun observeInRange(fromEpochMs: Long, toEpochMs: Long): Flow<List<ScaleMeasurement>> =
        dao.observeInRange(fromEpochMs, toEpochMs).map { rows -> rows.map { it.toDomain() } }

    fun observeAllNewestFirst(): Flow<List<ScaleMeasurement>> =
        dao.observeAllNewestFirst().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAllOldestFirst(): List<ScaleMeasurement> =
        dao.getAllOldestFirst().map { it.toDomain() }

    suspend fun allRecordedAtEpochMs(): Set<Long> =
        dao.allRecordedAtEpochMs().toSet()

    suspend fun insertAll(measurements: List<ScaleMeasurement>) {
        if (measurements.isEmpty()) return
        dao.insertAll(measurements.map { MeasurementEntity.fromDomain(it) })
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun count(): Int = dao.count()

    fun observeCount(): Flow<Int> = dao.observeCount()
}
