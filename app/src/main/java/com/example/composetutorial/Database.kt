package com.example.composetutorial

import android.content.Context
import androidx.room.*


@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val imagePath: String?
)

@Entity(tableName = "visits")
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val placeName: String,
    val rating: Int,
    val note: String,
    val imagePath: String?,
    val timestamp: Long = System.currentTimeMillis()
)


@Dao
interface UserDao {
    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun getUser(): UserProfile?

    @Upsert
    fun saveUser(user: UserProfile)
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY timestamp DESC")
    fun getAllVisits(): List<Visit>

    @Insert
    fun insertVisit(visit: Visit)

    @Query("DELETE FROM visits WHERE id = :visitId")
    fun deleteVisit(visitId: Int)
}


@Database(entities = [UserProfile::class, Visit::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun visitDao(): VisitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}