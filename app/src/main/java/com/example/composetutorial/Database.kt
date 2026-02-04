package com.example.composetutorial

import android.content.Context
import androidx.room.*

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val imagePath: String?
)

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun getUser(): UserProfile?

    @Upsert
    fun saveUser(user: UserProfile)
}

@Database(entities = [UserProfile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        fun getDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "app_database"
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}