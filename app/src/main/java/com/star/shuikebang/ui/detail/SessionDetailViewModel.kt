package com.star.shuikebang.ui.detail

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.star.shuikebang.data.entity.Question
import com.star.shuikebang.data.entity.TranscriptLine
import com.star.shuikebang.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository
) : AndroidViewModel(application) {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    val transcriptLines = repository.getTranscriptLines(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questions = repository.getQuestions(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun copyAllText() {
        val lines = transcriptLines.value
        if (lines.isEmpty()) return

        val text = lines.joinToString("\n") { it.text }
        copyToClipboard(text)
    }

    fun copyQuestionText() {
        val qs = questions.value
        if (qs.isEmpty()) return

        val text = qs.joinToString("\n") { it.text }
        copyToClipboard(text)
    }

    fun copySingleLine(line: TranscriptLine) {
        copyToClipboard(line.text)
    }

    private fun copyToClipboard(text: String) {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("transcript", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}
