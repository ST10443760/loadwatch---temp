package com.example.loadwatch.feature.area

import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Supplier
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.AreaSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AreaSearchTest {

    private val repository = FakeAreaRepository()

    private fun TestScope.newSearch(): AreaSearch {
        val search = AreaSearch(repository, backgroundScope, debounceMillis = DEBOUNCE)
        // state only runs while it has a subscriber, as it does while a screen is showing
        backgroundScope.launch { search.state.collect {} }
        runCurrent()
        return search
    }

    private fun TestScope.type(search: AreaSearch, vararg steps: String, gapMillis: Long = 100) {
        steps.forEach { text ->
            search.onQueryChange(text)
            advanceTimeBy(gapMillis)
        }
    }

    @Test
    fun `typing quickly searches once, for the final query, after the pause`() = runTest {
        val search = newSearch()

        type(search, "D", "Du", "Dur", "Durb", "Durba", "Durban")
        assertEquals("No search while the user is still typing", emptyList<String>(), repository.queries)

        advanceTimeBy(DEBOUNCE + 1)
        assertEquals(listOf("Durban"), repository.queries)
    }

    @Test
    fun `a search waits for the full debounce period`() = runTest {
        val search = newSearch()

        search.onQueryChange("Berea")
        advanceTimeBy(DEBOUNCE - 1)
        assertTrue(repository.queries.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf("Berea"), repository.queries)
    }

    @Test
    fun `fewer than three characters never searches`() = runTest {
        val search = newSearch()

        type(search, "U", "Um", gapMillis = DEBOUNCE * 3)

        assertTrue(repository.queries.isEmpty())
        assertEquals(AreaSearchState.Idle, search.state.value)
    }

    @Test
    fun `each settled query is searched once and surrounding spaces do not repeat a search`() = runTest {
        val search = newSearch()

        type(search, "Dur", gapMillis = DEBOUNCE * 2)
        type(search, "Durban", gapMillis = DEBOUNCE * 2)
        type(search, "  Durban ", gapMillis = DEBOUNCE * 2)

        assertEquals(listOf("Dur", "Durban"), repository.queries)
    }

    @Test
    fun `invalid characters are rejected without searching`() = runTest {
        val search = newSearch()

        type(search, "Berea%;", gapMillis = DEBOUNCE * 2)

        assertTrue(repository.queries.isEmpty())
        assertEquals(AreaSearchState.InvalidCharacters, search.state.value)
    }

    @Test
    fun `results and failures are reported, and retry searches again`() = runTest {
        val search = newSearch()

        repository.nextResult = AreaSearchResult.Unavailable(DataFailure.OFFLINE)
        type(search, "Westville", gapMillis = DEBOUNCE * 2)
        assertEquals(AreaSearchState.Failed(DataFailure.OFFLINE), search.state.value)

        repository.nextResult = AreaSearchResult.Found(listOf(WESTVILLE), fromCache = false)
        search.retry()
        runCurrent()

        assertEquals(listOf("Westville", "Westville"), repository.queries)
        assertEquals(AreaSearchState.Results(listOf(WESTVILLE), fromCache = false), search.state.value)
    }

    private class FakeAreaRepository : AreaRepository {
        val queries = mutableListOf<String>()
        var nextResult: AreaSearchResult = AreaSearchResult.Found(listOf(WESTVILLE), fromCache = false)

        override suspend fun searchAreas(query: String): AreaSearchResult {
            queries += query
            return nextResult
        }

        override suspend fun ensureAreaCached(areaId: String) = true
        override fun observeArea(areaId: String): Flow<Area?> = flowOf(null)
        override fun observeRecentAreas(): Flow<List<Area>> = flowOf(emptyList())
        override suspend fun recordRecentArea(areaId: String) = Unit
    }

    private companion object {
        const val DEBOUNCE = 300L
        val WESTVILLE = Area("eth-westville", "Westville", "eThekwini Metropolitan Municipality", "KwaZulu-Natal", Supplier.MUNICIPAL)
    }
}
