package com.star.shuikebang.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.star.shuikebang.data.entity.Session
import com.star.shuikebang.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SessionRepository
) : ViewModel() {

    val sessions = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun renameSession(session: Session, newTitle: String) {
        viewModelScope.launch {
            repository.updateSession(session.copy(title = newTitle))
        }
    }
}
