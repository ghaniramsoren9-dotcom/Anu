package com.ghaniram.zoya.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM user_memories ORDER BY timestamp ASC")
    fun getAllMemoriesFlow(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories ORDER BY timestamp ASC")
    suspend fun getAllMemories(): List<UserMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity): Long

    @Query("SELECT COUNT(*) FROM user_memories WHERE LOWER(fact) = LOWER(:fact)")
    suspend fun countMatchingFact(fact: String): Int

    @Query("DELETE FROM user_memories WHERE LOWER(fact) = LOWER(:fact)")
    suspend fun deleteByExactFact(fact: String): Int

    @Query("DELETE FROM user_memories WHERE LOWER(fact) LIKE '%' || LOWER(:query) || '%'")
    suspend fun deleteMatching(query: String): Int

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM user_memories")
    suspend fun clearAll()
}
