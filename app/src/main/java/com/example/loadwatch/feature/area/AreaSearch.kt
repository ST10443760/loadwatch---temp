package com.example.loadwatch.feature.area

import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.AreaSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface AreaSearchState {
    // Fewer than MIN_QUERY_LENGTH characters typed
    data object Idle : AreaSearchState

    data object InvalidCharacters : AreaSearchState

    data object Searching : AreaSearchState

    // fromCache: the API was unreachable and these come from the Room catalogue
    data class Results(val areas: List<Area>, val fromCache: Boolean) : AreaSearchState

    data class NoMatches(val query: String) : AreaSearchState

    data class Failed(val failure: DataFailure) : AreaSearchState
}

// Progressive, search-first area lookup shared by S03 and S05 (FR-AREA-01). The query is trimmed and validated,
// and a search only runs once typing pauses for debounceMillis with at least MIN_QUERY_LENGTH characters, so a
// suburb name typed quickly costs one request, not one per keystroke. A newer query cancels an older search.
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AreaSearch(
    private val repository: AreaRepository,
    scope: CoroutineScope,
    debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val retries = MutableStateFlow(0)

    val state: StateFlow<AreaSearchState> =
        combine(
            _query.map { it.trim() }.debounce(debounceMillis).distinctUntilChanged(),
            retries,
        ) { query, _ -> query }
            .flatMapLatest(::search)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AreaSearchState.Idle)

    fun onQueryChange(text: String) {
        _query.value = text.take(MAX_QUERY_LENGTH)
    }

    // Runs the current query again, e.g. after an offline failure
    fun retry() {
        retries.update { it + 1 }
    }

    private fun search(query: String): Flow<AreaSearchState> = flow {
        when {
            query.length < MIN_QUERY_LENGTH -> emit(AreaSearchState.Idle)
            !ALLOWED_QUERY.matches(query) -> emit(AreaSearchState.InvalidCharacters)
            else -> {
                emit(AreaSearchState.Searching)
                emit(
                    when (val result = repository.searchAreas(query)) {
                        is AreaSearchResult.Found ->
                            if (result.areas.isEmpty()) {
                                AreaSearchState.NoMatches(query)
                            } else {
                                AreaSearchState.Results(result.areas, result.fromCache)
                            }
                        is AreaSearchResult.Unavailable -> AreaSearchState.Failed(result.failure)
                    },
                )
            }
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        const val MAX_QUERY_LENGTH = 60
        const val DEFAULT_DEBOUNCE_MILLIS = 300L

        // Letters in any language, digits, spaces, apostrophes, hyphens, full stops and ampersands
        private val ALLOWED_QUERY = Regex("""^[\p{L}\p{N} '’.&-]+$""")
    }
}
