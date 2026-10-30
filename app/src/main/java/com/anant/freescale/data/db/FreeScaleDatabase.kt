package com.anant.freescale.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.anant.freescale.bia.Ssw532Impedances
import org.json.JSONArray

@Database(
    entities = [MeasurementEntity::class],
    version = 2,
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /**
         * v1 stored channel A/B, Z1-Z8 and a 10-slot "WLA25 input" vector, all built from
         * mis-parsed impedance frames. v2 keeps only the 10 impedances the scale really sent,
         * re-decoded from the stored pkt0/pkt1 frames.
         *
         * Body fat and the metrics derived from it in existing rows were computed from the
         * wrong impedances and are left as they were; the frames are kept so they can be
         * recomputed.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                val frames = mutableListOf<Triple<Long, String, String>>()
                connection.prepare("SELECT id, pkt0Hex, pkt1Hex FROM measurements").use { st ->
                    while (st.step()) frames += Triple(st.getLong(0), st.getText(1), st.getText(2))
                }
                connection.execSQL(
                    "CREATE TABLE `measurements_v2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recordedAtEpochMs` INTEGER NOT NULL, `userId` INTEGER NOT NULL, " +
                        "`genderLabel` TEXT NOT NULL, `algorithm` TEXT NOT NULL, `weight` REAL NOT NULL, " +
                        "`heightCm` REAL NOT NULL, `ageYears` INTEGER NOT NULL, `bmi` REAL NOT NULL, " +
                        "`fat` REAL NOT NULL, `fatMassKg` REAL NOT NULL, `subcutaneousFat` REAL NOT NULL, " +
                        "`visceralFat` REAL NOT NULL, `water` REAL NOT NULL, `waterKg` REAL NOT NULL, " +
                        "`muscle` REAL NOT NULL, `muscleMassKg` REAL NOT NULL, `skeletalMuscle` REAL NOT NULL, " +
                        "`bone` REAL NOT NULL, `protein` REAL NOT NULL, `proteinKg` REAL NOT NULL, " +
                        "`lbm` REAL NOT NULL, `bmr` REAL NOT NULL, `bodyAge` INTEGER NOT NULL, " +
                        "`obesityDegree` REAL NOT NULL, `idealWeightKg` REAL NOT NULL, `bodyScore` REAL NOT NULL, " +
                        "`impedance` REAL NOT NULL, `h2rCoeff` REAL NOT NULL, `pkt0Cmd` INTEGER NOT NULL, " +
                        "`impedancesJson` TEXT NOT NULL, `segmentsJson` TEXT NOT NULL, `pkt0Hex` TEXT NOT NULL, " +
                        "`pkt1Hex` TEXT NOT NULL, `pkt2Hex` TEXT NOT NULL)",
                )
                connection.execSQL(
                    "INSERT INTO measurements_v2 (id, recordedAtEpochMs, userId, genderLabel, algorithm, weight, " +
                        "heightCm, ageYears, bmi, fat, fatMassKg, subcutaneousFat, visceralFat, water, waterKg, " +
                        "muscle, muscleMassKg, skeletalMuscle, bone, protein, proteinKg, lbm, bmr, bodyAge, " +
                        "obesityDegree, idealWeightKg, bodyScore, impedance, h2rCoeff, pkt0Cmd, impedancesJson, " +
                        "segmentsJson, pkt0Hex, pkt1Hex, pkt2Hex) " +
                        "SELECT id, recordedAtEpochMs, userId, genderLabel, algorithm, weight, heightCm, ageYears, " +
                        "bmi, fat, fatMassKg, subcutaneousFat, visceralFat, water, waterKg, muscle, muscleMassKg, " +
                        "skeletalMuscle, bone, protein, proteinKg, lbm, bmr, bodyAge, obesityDegree, idealWeightKg, " +
                        "bodyScore, impedance, h2rCoeff, pkt0Cmd, '[]', segmentsJson, pkt0Hex, pkt1Hex, pkt2Hex " +
                        "FROM measurements",
                )
                for ((id, pkt0, pkt1) in frames) {
                    val imps = Ssw532Impedances.decodeHex(pkt0, pkt1) ?: continue
                    connection.prepare("UPDATE measurements_v2 SET impedancesJson = ? WHERE id = ?").use { st ->
                        st.bindText(1, JSONArray().also { a -> imps.forEach { a.put(it) } }.toString())
                        st.bindLong(2, id)
                        st.step()
                    }
                }
                connection.execSQL("DROP TABLE measurements")
                connection.execSQL("ALTER TABLE measurements_v2 RENAME TO measurements")
            }
        }
    }
}
