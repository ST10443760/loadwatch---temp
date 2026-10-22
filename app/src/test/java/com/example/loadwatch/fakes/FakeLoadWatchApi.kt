package com.example.loadwatch.fakes

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.AccuracySummaryDto
import com.example.loadwatch.core.network.dto.AreaDto
import com.example.loadwatch.core.network.dto.AreaSummaryDto
import com.example.loadwatch.core.network.dto.CurrentStageDto
import com.example.loadwatch.core.network.dto.FavouriteAreaCreateDto
import com.example.loadwatch.core.network.dto.FavouriteAreaDto
import com.example.loadwatch.core.network.dto.NextEventDto
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.OutageReportDto
import com.example.loadwatch.core.network.dto.ScheduleEventDto
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

// The API with a configurable handler per endpoint. Anything not configured behaves as if the phone is offline.
class FakeLoadWatchApi : LoadWatchApi {
    val calls = mutableListOf<String>()
    val scheduleRequests = mutableListOf<Triple<String, Instant, Instant>>()
    val settingsUpdates = mutableListOf<UserSettingsUpdateDto>()
    val favouriteRequests = mutableListOf<FavouriteAreaCreateDto>()

    var searchHandler: suspend (String?) -> List<AreaSummaryDto> = { offline() }
    var areaHandler: suspend (String) -> AreaDto = { offline() }
    var scheduleHandler: suspend (String, Instant, Instant) -> List<ScheduleEventDto> = { _, _, _ -> offline() }
    var nextEventHandler: suspend (String) -> NextEventDto = { offline() }
    var currentStageHandler: suspend () -> CurrentStageDto = { offline() }
    var accuracyHandler: suspend (String) -> AccuracySummaryDto = { offline() }
    var getSettingsHandler: suspend (String) -> UserSettingsDto = { offline() }
    var updateSettingsHandler: suspend (String, UserSettingsUpdateDto) -> UserSettingsDto = { _, _ -> offline() }
    var getFavouritesHandler: suspend (String) -> List<FavouriteAreaDto> = { offline() }
    var addFavouriteHandler: suspend (String, FavouriteAreaCreateDto) -> FavouriteAreaDto = { _, _ -> offline() }
    var removeFavouriteHandler: suspend (String, String) -> Response<Unit> = { _, _ -> offline() }
    var createReportHandler: suspend (OutageReportCreateDto) -> Response<OutageReportDto> = { offline() }

    fun callCount(name: String) = calls.count { it == name }

    override suspend fun searchAreas(query: String?, page: Int): List<AreaSummaryDto> {
        calls += "searchAreas"
        return searchHandler(query)
    }

    override suspend fun getArea(areaId: String): AreaDto {
        calls += "getArea"
        return areaHandler(areaId)
    }

    override suspend fun getSchedule(areaId: String, from: Instant, to: Instant): List<ScheduleEventDto> {
        calls += "getSchedule"
        scheduleRequests += Triple(areaId, from, to)
        return scheduleHandler(areaId, from, to)
    }

    override suspend fun getNextEvent(areaId: String): NextEventDto {
        calls += "getNextEvent"
        return nextEventHandler(areaId)
    }

    override suspend fun getCurrentStage(): CurrentStageDto {
        calls += "getCurrentStage"
        return currentStageHandler()
    }

    override suspend fun getSettings(userId: String): UserSettingsDto {
        calls += "getSettings"
        return getSettingsHandler(userId)
    }

    override suspend fun updateSettings(userId: String, update: UserSettingsUpdateDto): UserSettingsDto {
        calls += "updateSettings"
        settingsUpdates += update
        return updateSettingsHandler(userId, update)
    }

    override suspend fun getFavourites(userId: String): List<FavouriteAreaDto> {
        calls += "getFavourites"
        return getFavouritesHandler(userId)
    }

    override suspend fun addFavourite(userId: String, request: FavouriteAreaCreateDto): FavouriteAreaDto {
        calls += "addFavourite"
        favouriteRequests += request
        return addFavouriteHandler(userId, request)
    }

    override suspend fun removeFavourite(userId: String, areaId: String): Response<Unit> {
        calls += "removeFavourite"
        return removeFavouriteHandler(userId, areaId)
    }

    override suspend fun createOutageReport(request: OutageReportCreateDto): Response<OutageReportDto> {
        calls += "createOutageReport"
        return createReportHandler(request)
    }

    override suspend fun getAreaOutageReports(areaId: String, since: Instant?): List<OutageReportDto> {
        calls += "getAreaOutageReports"
        offline()
    }

    override suspend fun getAreaAccuracy(areaId: String): AccuracySummaryDto {
        calls += "getAreaAccuracy"
        return accuracyHandler(areaId)
    }
}

fun offline(): Nothing = throw IOException("Offline (fake)")

// An HTTP error with a ProblemDetails body, as the API returns
fun httpError(code: Int, problemJson: String = """{"status":$code}"""): Nothing =
    throw HttpException(Response.error<Any>(code, problemJson.toResponseBody("application/problem+json".toMediaType())))
