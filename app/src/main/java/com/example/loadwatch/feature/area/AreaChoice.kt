package com.example.loadwatch.feature.area

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.SaveSettingsResult
import com.example.loadwatch.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AreaChoiceState(
    val savingAreaId: String? = null,
    // The last save that did not fully succeed, with the area it was for
    val problem: SaveSettingsResult? = null,
    val problemAreaId: String? = null,
) {
    val isSaving: Boolean get() = savingAreaId != null
}

// Saves a chosen area as the user's default (FR-AREA-03) and reports the outcome. Shared by S03 and S05.
class AreaChoice(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val areaRepository: AreaRepository,
) {
    private val _state = MutableStateFlow(AreaChoiceState())
    val state: StateFlow<AreaChoiceState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)

    // Emits once the area is saved, or the user chose to continue with it saved on this device only
    val done: Flow<Unit> = _done.receiveAsFlow()

    fun save(areaId: String) = run(areaId) { settingsRepository.setDefaultArea(areaId) }

    // Retries whatever failed last: only the sync if the area is already saved on the device
    fun retry() {
        val state = _state.value
        val areaId = state.problemAreaId ?: return
        if (state.problem is SaveSettingsResult.SavedOnDeviceOnly) {
            run(areaId) { settingsRepository.retrySync() }
        } else {
            save(areaId)
        }
    }

    fun continueOnThisDevice() {
        _state.value = AreaChoiceState()
        _done.trySend(Unit)
    }

    fun clearProblem() {
        _state.update { it.copy(problem = null, problemAreaId = null) }
    }

    private fun run(areaId: String, block: suspend () -> SaveSettingsResult) {
        if (_state.value.isSaving) return
        _state.value = AreaChoiceState(savingAreaId = areaId)
        scope.launch {
            val result = block()
            Log.i(LogTags.API, "Default area save finished: ${result.javaClass.simpleName}")
            if (result is SaveSettingsResult.Saved || result is SaveSettingsResult.SavedOnDeviceOnly) {
                areaRepository.recordRecentArea(areaId)
            }
            if (result is SaveSettingsResult.Saved) {
                _state.value = AreaChoiceState()
                _done.send(Unit)
            } else {
                _state.value = AreaChoiceState(problem = result, problemAreaId = areaId)
            }
        }
    }
}
