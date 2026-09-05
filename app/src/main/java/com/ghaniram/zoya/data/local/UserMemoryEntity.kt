package com.ghaniram.zoya.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)
