package com.example.loadwatch.core.network

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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant

// The Part 2 endpoints from Table 6. Routes marked bearer (settings, favourites, the outage report POST) get the
// Firebase ID token from AuthInterceptor. The sync batch, notification registration and history endpoints are
// Final POE scope and are added with those features.
// Instant query values are sent as ISO-8601 UTC via Instant.toString().
interface LoadWatchApi {

    // Area catalogue (anonymous)

    @GET("api/areas")
    suspend fun searchAreas(@Query("q") query: String?, @Query("page") page: Int = 1): List<AreaSummaryDto>

    @GET("api/areas/{areaId}")
    suspend fun getArea(@Path("areaId") areaId: String): AreaDto

    // Schedules and stage (anonymous)

    @GET("api/schedules")
    suspend fun getSchedule(
        @Query("areaId") areaId: String,
        @Query("from") from: Instant,
        @Query("to") to: Instant,
    ): List<ScheduleEventDto>

    @GET("api/schedules/next")
    suspend fun getNextEvent(@Query("areaId") areaId: String): NextEventDto

    @GET("api/stages/current")
    suspend fun getCurrentStage(): CurrentStageDto

    // User settings and favourites (bearer)

    @GET("api/users/{userId}/settings")
    suspend fun getSettings(@Path("userId") userId: String): UserSettingsDto

    @PUT("api/users/{userId}/settings")
    suspend fun updateSettings(@Path("userId") userId: String, @Body update: UserSettingsUpdateDto): UserSettingsDto

    @GET("api/users/{userId}/favourites")
    suspend fun getFavourites(@Path("userId") userId: String): List<FavouriteAreaDto>

    // 201 on success, 409 if the area is already a favourite
    @POST("api/users/{userId}/favourites")
    suspend fun addFavourite(@Path("userId") userId: String, @Body request: FavouriteAreaCreateDto): FavouriteAreaDto

    // 204 on success, 404 if it was not a favourite
    @DELETE("api/users/{userId}/favourites/{areaId}")
    suspend fun removeFavourite(@Path("userId") userId: String, @Path("areaId") areaId: String): Response<Unit>

    // Community confirmation

    // 201 for a new report, 200 when the same clientReportId was already accepted, 409 for a duplicate event
    // inside the window. Response is returned rather than the body so callers can tell those apart.
    // The reporter is the signed-in user; AuthInterceptor adds the Firebase ID token.
    @POST("api/outage-reports")
    suspend fun createOutageReport(@Body request: OutageReportCreateDto): Response<OutageReportDto>

    @GET("api/areas/{areaId}/outage-reports")
    suspend fun getAreaOutageReports(
        @Path("areaId") areaId: String,
        @Query("since") since: Instant?,
    ): List<OutageReportDto>

    @GET("api/areas/{areaId}/accuracy")
    suspend fun getAreaAccuracy(@Path("areaId") areaId: String): AccuracySummaryDto
}
