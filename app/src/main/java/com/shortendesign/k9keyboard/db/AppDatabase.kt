package com.shortendesign.k9keyboard.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shortendesign.k9keyboard.dao.SettingDao
import com.shortendesign.k9keyboard.dao.WordDao
import com.shortendesign.k9keyboard.entity.Setting
import com.shortendesign.k9keyboard.entity.Word

@Database(entities = [Word::class, Setting::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getWordDao(): WordDao
    abstract fun getSettingDao(): SettingDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "k9keyboard_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
