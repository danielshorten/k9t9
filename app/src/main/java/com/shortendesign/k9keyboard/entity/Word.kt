package com.shortendesign.k9keyboard.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["word"]), Index(value = ["code"])])
data class Word(
    var word: String,
    var code: String,
    var length: Int,
    var frequency: Int,
    var locale: String
){
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

}
