package com.star.shuikebang.domain.session

import com.star.shuikebang.data.entity.Question
import com.star.shuikebang.data.entity.TranscriptLine
import com.star.shuikebang.data.repository.SessionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val repository: SessionRepository
) {
    private var currentSessionId: Long? = null
    private var lineNumber: Int = 0

    suspend fun startNewSession(): Long {
        val title = generateSessionTitle()
        val sessionId = repository.createSession(title)
        currentSessionId = sessionId
        lineNumber = 0
        return sessionId
    }

    suspend fun addTranscriptLine(text: String, isQuestion: Boolean): Long? {
        val sessionId = currentSessionId ?: return null

        val line = TranscriptLine(
            sessionId = sessionId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isQuestion = isQuestion,
            lineNumber = lineNumber++
        )
        return repository.insertTranscriptLine(line)
    }

    suspend fun addQuestion(text: String, sourceLineId: Long): Long? {
        val sessionId = currentSessionId ?: return null

        val question = Question(
            sessionId = sessionId,
            text = text,
            timestamp = System.currentTimeMillis(),
            sourceLineId = sourceLineId
        )
        return repository.insertQuestion(question)
    }

    suspend fun endCurrentSession() {
        val sessionId = currentSessionId ?: return
        repository.endSession(sessionId)
        currentSessionId = null
        lineNumber = 0
    }

    fun getCurrentSessionId(): Long? = currentSessionId

    private fun generateSessionTitle(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "课堂记录 ${sdf.format(Date())}"
    }
}
