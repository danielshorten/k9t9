package com.shortendesign.k9keyboard.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shortendesign.k9keyboard.dao.SettingDao

@Entity(indices = [Index(value = ["key"], unique = true)])
data class Setting(
    @PrimaryKey val key: String,
    var value: String
) {
    companion object {
        suspend fun set(key: String, value: String, dao: SettingDao) {
            dao.insert(Setting(key, value))
        }
    }
}
