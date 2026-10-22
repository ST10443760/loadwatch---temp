package com.example.loadwatch.data.repository

import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.network.dto.FavouriteAreaDto
import com.example.loadwatch.data.model.FavouriteLabel
import com.example.loadwatch.data.remote.FavouritesRemoteDataSource
import com.example.loadwatch.fakes.FakeAreaDao
import com.example.loadwatch.fakes.FakeAreaRepository
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeFavouriteAreaDao
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.httpError
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class FavouritesRepositoryTest {

    private val now = Instant.parse("2026-10-22T10:00:00Z")
    private val areaDao = FakeAreaDao(
        listOf(AreaEntity(AREA, "Berea", "eThekwini Metropolitan Municipality", "KwaZulu-Natal", "MUNICIPAL", null, null, true)),
    )
    private val favouriteDao = FakeFavouriteAreaDao(areaDao)
    private val api = FakeLoadWatchApi()
    private val repository = OfflineFirstFavouritesRepository(
        authSession = FakeAuthSession(),
        remote = FavouritesRemoteDataSource(api, testJson),
        favouriteAreaDao = favouriteDao,
        areaRepository = FakeAreaRepository(areaDao),
    )

    @Test
    fun `labels are optional, trimmed and at most 40 characters`() {
        assertEquals(FavouriteLabel.Validation.Valid(""), FavouriteLabel.validate(null))
        assertEquals(FavouriteLabel.Validation.Valid("Home"), FavouriteLabel.validate("  Home  "))
        assertEquals(FavouriteLabel.Validation.Valid("a".repeat(40)), FavouriteLabel.validate("a".repeat(40)))
        assertEquals(FavouriteLabel.Validation.TooLong, FavouriteLabel.validate("a".repeat(41)))
    }

    @Test
    fun `a label longer than 40 characters is rejected without calling the API`() = runTest {
        val result = repository.add(AREA, "a".repeat(41))

        assertEquals(FavouriteResult.LabelTooLong, result)
        assertEquals(0, api.callCount("addFavourite"))
    }

    @Test
    fun `a valid favourite is sent trimmed and cached in Room`() = runTest {
        api.addFavouriteHandler = { userId, request -> FavouriteAreaDto("fav-1", userId, request.areaId, request.label.orEmpty(), 0, now) }

        val result = repository.add(AREA, "  Work ")

        assertEquals(FavouriteResult.Saved, result)
        assertEquals("Work", api.favouriteRequests.single().label)
        assertEquals("Work", favouriteDao.favourites.value.single().label)
    }

    @Test
    fun `a 409 from the API is already in your favourites`() = runTest {
        api.addFavouriteHandler = { _, _ -> httpError(409, """{"status":409,"title":"Favourite already exists"}""") }
        api.getFavouritesHandler = { userId -> listOf(FavouriteAreaDto("fav-1", userId, AREA, "Home", 0, now)) }

        val result = repository.add(AREA, "Home")

        assertEquals(FavouriteResult.AlreadyFavourite, result)
        // The server's list is pulled in so this phone shows the favourite saved elsewhere
        assertEquals(AREA, favouriteDao.favourites.value.single().areaId)
    }

    @Test
    fun `an area already saved on this phone is a duplicate without calling the API`() = runTest {
        favouriteDao.upsertAll(listOf(FavouriteAreaEntity("fav-1", "user-1", AREA, "Home", 0, now)))

        val result = repository.add(AREA, "Again")

        assertEquals(FavouriteResult.AlreadyFavourite, result)
        assertEquals(0, api.callCount("addFavourite"))
    }

    @Test
    fun `removing a favourite never deletes the area`() = runTest {
        favouriteDao.upsertAll(listOf(FavouriteAreaEntity("fav-1", "user-1", AREA, "Home", 0, now)))
        api.removeFavouriteHandler = { _, _ -> Response.success(204, Unit) }

        val result = repository.remove(AREA)

        assertEquals(FavouriteResult.Saved, result)
        assertTrue(favouriteDao.favourites.value.isEmpty())
        assertNotNull(areaDao.getById(AREA))
    }

    private companion object {
        const val AREA = "eth-berea"
    }
}
