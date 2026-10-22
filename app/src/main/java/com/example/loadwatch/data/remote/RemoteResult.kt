package com.example.loadwatch.data.remote

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.network.dto.ProblemDetailsDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

// Outcome of an API call, so repositories can choose between showing cached data, queueing, or surfacing an error.
sealed interface RemoteResult<out T> {
    data class Success<T>(val data: T) : RemoteResult<T>

    // The API answered with an error status; problem holds its ProblemDetails body when one was sent
    data class HttpFailure(val status: Int, val problem: ProblemDetailsDto?) : RemoteResult<Nothing>

    // No usable connection, so the caller should fall back to Room or queue the write
    data class NetworkFailure(val cause: IOException) : RemoteResult<Nothing>
}

suspend fun <T> safeApiCall(json: Json, name: String, call: suspend () -> T): RemoteResult<T> =
    try {
        RemoteResult.Success(call()).also { Log.d(LogTags.API, "$name succeeded") }
    } catch (e: HttpException) {
        val problem = e.response()?.errorBody()?.string()?.let { body ->
            runCatching { json.decodeFromString<ProblemDetailsDto>(body) }.getOrNull()
        }
        Log.w(LogTags.API, "$name failed with HTTP ${e.code()}: ${problem?.title ?: e.message()}")
        RemoteResult.HttpFailure(e.code(), problem)
    } catch (e: IOException) {
        Log.w(LogTags.API, "$name failed without a response: ${e.message}")
        RemoteResult.NetworkFailure(e)
    }
