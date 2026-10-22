package com.example.loadwatch.data.remote

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.OutageReportDto
import com.example.loadwatch.core.network.dto.ProblemDetailsDto
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

sealed interface ReportPostResult {
    // 201 for a new report, or 200 when this clientReportId was already accepted (a safe retry)
    data class Accepted(val report: OutageReportDto) : ReportPostResult

    // 409: the same user reported the same event for this area inside the API's duplicate window
    data class AlreadyReported(val existingReportId: String?) : ReportPostResult

    // Another 4xx, such as 400 validation or 401 when the session is no longer valid
    data class Rejected(val status: Int, val problem: ProblemDetailsDto?) : ReportPostResult

    data class ServerError(val status: Int) : ReportPostResult

    data class NetworkFailure(val cause: IOException) : ReportPostResult
}

class ReportRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    // POST /api/outage-reports with the Firebase token (added by AuthInterceptor). The response is read directly
    // because 200, 201 and 409 all mean something different here.
    suspend fun submit(request: OutageReportCreateDto): ReportPostResult {
        val response = try {
            api.createOutageReport(request)
        } catch (e: IOException) {
            Log.w(LogTags.API, "Report POST failed without a response: ${e.javaClass.simpleName}")
            return ReportPostResult.NetworkFailure(e)
        }

        val body = response.body()
        if (response.isSuccessful && body != null) {
            Log.i(LogTags.API, "Report POST accepted with HTTP ${response.code()}")
            return ReportPostResult.Accepted(body)
        }

        val problem = response.errorBody()?.string()?.let { text ->
            runCatching { json.decodeFromString<ProblemDetailsDto>(text) }.getOrNull()
        }
        Log.w(LogTags.API, "Report POST answered HTTP ${response.code()}: ${problem?.title}")
        return when (response.code()) {
            409 -> ReportPostResult.AlreadyReported(problem?.existingReportId)
            in 400..499 -> ReportPostResult.Rejected(response.code(), problem)
            else -> ReportPostResult.ServerError(response.code())
        }
    }
}
