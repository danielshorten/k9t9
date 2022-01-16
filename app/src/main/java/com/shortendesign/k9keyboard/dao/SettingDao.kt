package com.shortendesign.k9keyboard.dao

import androidx.room.*
import com.shortendesign.k9keyboard.entity.Setting

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: Setting)

    @Query("SELECT * FROM setting WHERE key = :key")
    suspend fun getByKey(key: String): Setting?

    @Delete
    suspend fun delete(setting: Setting)
}