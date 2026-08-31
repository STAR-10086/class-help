package com.star.shuikebang.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.star.shuikebang.domain.asr.DownloadState
import com.star.shuikebang.domain.asr.ModelManager
import com.star.shuikebang.service.RecordingEvent
import com.star.shuikebang.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscriptItem(
    val text: String,
    val isQuestion: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val modelManager: ModelManager
) : AndroidViewModel(application) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _transcriptLines = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val transcriptLines: StateFlow<List<TranscriptItem>> = _transcriptLines.asStateFlow()

    private val _questionCount = MutableStateFlow(0)
    val questionCount: StateFlow<Int> = _questionCount.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        collectRecordingEvents()
    }

    private fun collectRecordingEvents() {
        viewModelScope.launch {
            RecordingService.status.collect { status ->
                _isRecording.value = status
            }
        }

        viewModelScope.launch {
            RecordingService.events.collect { event ->
                when (event) {
                    is RecordingEvent.TranscriptLine -> {
                        _transcriptLines.value = _transcriptLines.value + TranscriptItem(
                            text = event.text,
                            isQuestion = event.isQuestion
                        )
                    }
                    is RecordingEvent.QuestionDetected -> {
                        _questionCount.value++
                    }
                    is RecordingEvent.Error -> {
                        _errorMessage.value = event.message
                    }
                    is RecordingEvent.StatusChanged -> {
                        _isRecording.value = event.isRecording
                        if (!event.isRecording) {
                            _transcriptLines.value = emptyList()
                            _questionCount.value = 0
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            modelManager.downloadState.collect { state ->
                _downloadState.value = state
            }
        }
    }

    fun toggleRecording() {
        val context = getApplication<Application>()
        if (_isRecording.value) {
            RecordingService.stop(context)
        } else {
            _transcriptLines.value = emptyList()
            _questionCount.value = 0
            RecordingService.start(context)
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
