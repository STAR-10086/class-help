package com.star.shuikebang.data.db

import androidx.room.*
import com.star.shuikebang.data.entity.Question
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: Long): Flow<List<Question>>

    @Insert
    suspend fun insert(question: Question): Long

    @Query("DELETE FROM questions WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)
}
