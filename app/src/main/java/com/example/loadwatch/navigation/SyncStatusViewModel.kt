package com.example.loadwatch.navigation

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.LoggedViewModel
import com.example.loadwatch.data.sync.SyncQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// How many offline changes are still waiting to sync, for the banner above every hub screen
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    syncQueue: SyncQueue,
) : LoggedViewModel() {

    val unsentCount: StateFlow<Int> = syncQueue.observeUnsentCount()
        .onEach { Log.d(LogTags.SYNC, "$it changes waiting to sync") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
