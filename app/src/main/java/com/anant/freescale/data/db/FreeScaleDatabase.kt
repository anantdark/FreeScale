package com.anant.freescale.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MeasurementEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FreeScaleDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        const val NAME = "freescale.db"

        @Volatile
        private var instance: FreeScaleDatabase? = null

        fun get(context: Context): FreeScaleDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FreeScaleDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
