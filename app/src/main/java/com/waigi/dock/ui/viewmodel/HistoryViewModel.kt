package com.waigi.dock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waigi.dock.database.DownloadedItem
import com.waigi.dock.repository.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val searchQuery: String = "",
    val isDeleteAllDialogVisible: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** Live list of items, automatically re-filtered when searchQuery changes. */
    val items: StateFlow<List<DownloadedItem>> = _uiState
        .flatMapLatest { state -> repository.search(state.searchQuery) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onClearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun deleteItem(item: DownloadedItem) {
        viewModelScope.launch { repository.delete(item) }
    }

    fun showDeleteAllDialog() {
        _uiState.update { it.copy(isDeleteAllDialogVisible = true) }
    }

    fun dismissDeleteAllDialog() {
        _uiState.update { it.copy(isDeleteAllDialogVisible = false) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
            _uiState.update { it.copy(isDeleteAllDialogVisible = false) }
        }
    }
}
