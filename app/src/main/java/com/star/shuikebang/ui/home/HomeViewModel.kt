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

    private val _downloadState = MutableStateFlow<DownloadState>(
        if (modelManager.isModelReady()) DownloadState.Ready else DownloadState.NotDownloaded
    )
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    init {
        collectRecordingEvents()
        // Auto-download model if not ready
        if (!modelManager.isModelReady()) {
            downloadModel()
        }
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
                if (state is DownloadState.Ready || state is DownloadState.Error) {
                    _isDownloading.value = false
                }
            }
        }
    }

    fun downloadModel() {
        if (_isDownloading.value) return
        _isDownloading.value = true
        viewModelScope.launch {
            val result = modelManager.ensureModelReady()
            if (result.isFailure) {
                _errorMessage.value = "模型下载失败: ${result.exceptionOrNull()?.message ?: "请检查网络后重试"}"
            }
        }
    }

    fun toggleRecording() {
        val context = getApplication<Application>()
        if (_isRecording.value) {
            RecordingService.stop(context)
        } else {
            // Check model first
            if (!modelManager.isModelReady()) {
                _errorMessage.value = "语音模型尚未下载完成，请等待下载完成后重试"
                downloadModel()
                return
            }
            _transcriptLines.value = emptyList()
            _questionCount.value = 0
            RecordingService.start(context)
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
