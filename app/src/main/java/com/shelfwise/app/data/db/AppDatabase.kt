package com.shelfwise.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shelfwise.app.data.db.entity.BookEntity
import com.shelfwise.app.data.db.entity.BookFtsEntity
import com.shelfwise.app.data.db.migrations.AllMigrations

@Database(
    entities = [BookEntity::class, BookFtsEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shelfwise.db"
                )
                    .addMigrations(*AllMigrations.LIST)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
