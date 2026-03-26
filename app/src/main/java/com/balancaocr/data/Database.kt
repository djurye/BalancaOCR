package com.balancaocr.data

import androidx.room.*

// ── Entidade: leitura individual ──────────────────────────────────────────────
@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val value: Double,
    val unit: String = "g",
    val timestamp: Long = System.currentTimeMillis(),
    val index: Int = 0
)

// ── Entidade: sessão de pesagem ───────────────────────────────────────────────
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val count: Int = 0
)

// ── DAO ───────────────────────────────────────────────────────────────────────
@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements WHERE sessionId = :sessionId ORDER BY `index` ASC")
    suspend fun getBySession(sessionId: Long): List<Measurement>

    @Insert
    suspend fun insert(m: Measurement): Long

    @Delete
    suspend fun delete(m: Measurement)

    @Query("DELETE FROM measurements WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    suspend fun getAll(): List<Session>

    @Insert
    suspend fun insert(s: Session): Long

    @Update
    suspend fun update(s: Session)

    @Delete
    suspend fun delete(s: Session)
}

// ── Database ──────────────────────────────────────────────────────────────────
@Database(entities = [Measurement::class, Session::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "balanca_db"
                ).build().also { INSTANCE = it }
            }
    }
}
