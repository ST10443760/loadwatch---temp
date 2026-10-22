package com.example.loadwatch.data.repository

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.OutageReport
import kotlinx.coroutines.flow.Flow

sealed interface ReportResult {
    data class Accepted(val report: OutageReport) : ReportResult

    // The API already has this event from this user for this area (409); a friendly outcome, not an error
    data object AlreadyReported : ReportResult

    // No connection: the report is kept on the device as PENDING until it is sent again
    data class SavedOffline(val report: OutageReport) : ReportResult

    // The API refused the report as invalid (400); fields are the JSON field names it complained about
    data class Rejected(val fields: List<String>) : ReportResult

    // Nobody is signed in, or the API no longer accepts the session (401)
    data object NotSignedIn : ReportResult

    // The API failed (5xx); the report stays PENDING
    data class Failed(val failure: DataFailure) : ReportResult
}

// Community confirmations (FR-REP-01 to 04). Every report is stored in Room before it is sent, so nothing a user
// submits is lost if the connection drops.
interface ReportRepository {

    // The signed-in user's latest reports for an area, newest first
    fun observeRecentReports(areaId: String): Flow<List<OutageReport>>

    fun observeAccuracy(areaId: String): Flow<AccuracyScore?>

    // Returns false when the accuracy could not be fetched; the cached value stays
    suspend fun refreshAccuracy(areaId: String): Boolean

    suspend fun submit(areaId: String, eventType: OutageEventType): ReportResult

    // Sends this area's PENDING reports again. Null when there was nothing to send.
    suspend fun retryUnsent(areaId: String): ReportResult?
}
