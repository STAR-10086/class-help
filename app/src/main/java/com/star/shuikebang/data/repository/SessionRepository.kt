package com.star.shuikebang.data.repository

import com.star.shuikebang.data.db.QuestionDao
import com.star.shuikebang.data.db.SessionDao
import com.star.shuikebang.data.db.TranscriptLineDao
import com.star.shuikebang.data.entity.Question
import com.star.shuikebang.data.entity.Session
import com.star.shuikebang.data.entity.TranscriptLine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val transcriptLineDao: TranscriptLineDao,
    private val questionDao: QuestionDao
) {
    fun getAllSessions(): Flow<List<Session>> = sessionDao.getAllSessions()

    suspend fun getSessionById(id: Long): Session? = sessionDao.getById(id)

    fun getTranscriptLines(sessionId: Long): Flow<List<TranscriptLine>> =
        transcriptLineDao.getBySessionId(sessionId)

    fun getQuestions(sessionId: Long): Flow<List<Question>> =
        questionDao.getBySessionId(sessionId)

    suspend fun createSession(title: String): Long {
        return sessionDao.insert(
            Session(
                title = title,
                startTime = System.currentTimeMillis(),
                isRecording = true
            )
        )
    }

    suspend fun updateSession(session: Session) = sessionDao.update(session)

    suspend fun endSession(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(endTime = System.currentTimeMillis(), isRecording = false))
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteById(sessionId)
    }

    suspend fun insertTranscriptLine(line: TranscriptLine): Long =
        transcriptLineDao.insert(line)

    suspend fun insertQuestion(question: Question): Long =
        questionDao.insert(question)
}
