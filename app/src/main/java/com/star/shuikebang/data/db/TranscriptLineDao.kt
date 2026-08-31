package com.star.shuikebang.data.db

import androidx.room.*
import com.star.shuikebang.data.entity.TranscriptLine
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptLineDao {
    @Query("SELECT * FROM transcript_lines WHERE sessionId = :sessionId ORDER BY lineNumber ASC")
    fun getBySessionId(sessionId: Long): Flow<List<TranscriptLine>>

    @Insert
    suspend fun insert(line: TranscriptLine): Long

    @Insert
    suspend fun insertAll(lines: List<TranscriptLine>)

    @Query("DELETE FROM transcript_lines WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)
}
