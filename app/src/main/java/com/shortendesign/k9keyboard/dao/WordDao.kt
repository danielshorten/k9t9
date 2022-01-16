package com.shortendesign.k9keyboard.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.shortendesign.k9keyboard.entity.Word

@Dao
interface WordDao {
    @Insert
    suspend fun insert(vararg words: Word)

    @Delete
    fun delete(word: Word)

    @Query("SELECT * FROM word")
    fun getAll(): List<Word>

    @Query("SELECT * FROM word WHERE code LIKE :code || '%' " +
            "ORDER BY length, frequency DESC LIMIT 10")
    suspend fun findCandidates(code: String): List<Word>

}