package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.TransactionRunner
import com.example.loadwatch.core.database.dao.AreaDao
import com.example.loadwatch.core.database.dao.AreaStatusDao
import com.example.loadwatch.core.database.dao.ScheduleEventDao
import com.example.loadwatch.core.database.entity.AreaRefreshEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.network.dto.NextEventDto
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.ScheduleEvent
import com.example.loadwatch.data.remote.RemoteResult
import com.example.loadwatch.data.remote.ScheduleRemoteDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class AccuracyScore(val score: Double, val sampleSize: Int)

// Everything Home and Schedule show for an area, read from Room
data class AreaSchedule(
    val area: Area?,
    // Cached windows that end after the requested instant, earliest first
    val events: List<ScheduleEvent>,
    val lastRefreshedAt: Instant?,
    // End of the last fetched window; days after it are unknown, not clear
    val loadedUntil: Instant?,
    // serverTime minus the device clock, measured at the last refresh
    val clockOffset: Duration,
    // The API's next event, which may lie beyond the cached window
    val apiNextEvent: ScheduleEvent?,
    val accuracy: AccuracyScore?,
    val currentStage: Int?,
)

sealed interface ScheduleRefreshResult {
    data object Refreshed : ScheduleRefreshResult
    data class Failed(val failure: DataFailure) : ScheduleRefreshResult
}

// Offline-first schedule data: screens read Room through observeAreaSchedule and update when refresh lands.
@Singleton
class ScheduleRepository @Inject constructor(
    private val remote: ScheduleRemoteDataSource,
    private val areaDao: AreaDao,
    private val scheduleEventDao: ScheduleEventDao,
    private val areaStatusDao: AreaStatusDao,
    private val areaRepository: AreaRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {

    fun observeAreaSchedule(areaId: String, eventsEndingAfter: Instant): Flow<AreaSchedule> =
        combine(
            areaDao.observeById(areaId),
            scheduleEventDao.observeEndingAfter(areaId, eventsEndingAfter),
            areaStatusDao.observeRefresh(areaId),
            areaStatusDao.observeAccuracy(areaId),
            areaStatusDao.observeCurrentStage(),
        ) { area, events, refresh, accuracy, stage ->
            AreaSchedule(
                area = area?.toModel(),
                events = events.map { it.toModel() },
                lastRefreshedAt = refresh?.lastRefreshedAt,
                loadedUntil = refresh?.windowEnd,
                clockOffset = Duration.ofMillis(refresh?.clockOffsetMillis ?: 0),
                apiNextEvent = refresh?.toApiNextEvent(),
                accuracy = accuracy?.let { AccuracyScore(it.accuracyScore, it.sampleSize) },
                currentStage = stage?.stage,
            )
        }.onEach {
            Log.d(LogTags.ROOM, "Read ${it.events.size} cached schedule windows for $areaId (last refreshed ${it.lastRefreshedAt ?: "never"})")
        }

    // Fetches days of schedule from the start of today (clamped to 1..MAX_DAYS), the next event, the current stage
    // and the accuracy score, then replaces the area's cache in one transaction. The schedule call decides
    // success: the others are kept from the previous refresh if they fail.
    suspend fun refresh(areaId: String, days: Int = DEFAULT_DAYS): ScheduleRefreshResult = coroutineScope {
        val dayCount = days.coerceIn(1, MAX_DAYS)
        val today = LocalDate.now(clock)
        val windowStart = today.atStartOfDay(clock.zone).toInstant()
        val windowEnd = today.plusDays(dayCount.toLong()).atStartOfDay(clock.zone).toInstant()
        Log.i(LogTags.API, "Refreshing $dayCount days of schedule for $areaId")

        val schedule = async { remote.getSchedule(areaId, windowStart, windowEnd) }
        // Timestamped the moment the response arrives, for the clock offset
        val next = async { remote.getNextEvent(areaId) to clock.instant() }
        val stage = async { remote.getCurrentStage() }
        val accuracy = async { remote.getAccuracy(areaId) }

        val scheduleResult = schedule.await()
        if (scheduleResult !is RemoteResult.Success) {
            // Without the schedule the refresh has failed, so don't wait for the other calls
            listOf(next, stage, accuracy).forEach { it.cancel() }
        }

        when (scheduleResult) {
            is RemoteResult.NetworkFailure -> ScheduleRefreshResult.Failed(DataFailure.OFFLINE)
            is RemoteResult.HttpFailure -> ScheduleRefreshResult.Failed(
                if (scheduleResult.status == 404) DataFailure.NOT_FOUND else DataFailure.SERVER,
            )
            is RemoteResult.Success -> {
                // The cache tables reference the area catalogue
                if (areaDao.getById(areaId) == null && !areaRepository.ensureAreaCached(areaId)) {
                    return@coroutineScope ScheduleRefreshResult.Failed(DataFailure.NOT_FOUND)
                }
                val (nextResult, receivedAt) = next.await()
                save(areaId, scheduleResult.data.map { it.toEntity() }, windowStart, windowEnd, nextResult, receivedAt)
                (stage.await() as? RemoteResult.Success)?.let { areaStatusDao.upsertCurrentStage(it.data.toEntity()) }
                (accuracy.await() as? RemoteResult.Success)?.let { areaStatusDao.upsertAccuracy(it.data.toEntity()) }
                Log.i(LogTags.API, "Refreshed $areaId: ${scheduleResult.data.size} windows cached")
                ScheduleRefreshResult.Refreshed
            }
        }
    }

    private suspend fun save(
        areaId: String,
        events: List<ScheduleEventEntity>,
        windowStart: Instant,
        windowEnd: Instant,
        nextResult: RemoteResult<NextEventDto>,
        receivedAt: Instant,
    ) = transactionRunner.inTransaction {
        val previous = areaStatusDao.getRefresh(areaId)
        val previousOffset = Duration.ofMillis(previous?.clockOffsetMillis ?: 0)
        val nextEvent = (nextResult as? RemoteResult.Success)?.data

        val offset = nextEvent?.let { Duration.between(receivedAt, it.serverTime) } ?: previousOffset
        if (abs(offset.minus(previousOffset).toMillis()) >= OFFSET_LOG_THRESHOLD_MILLIS) {
            Log.i(LogTags.SYNC, "Clock offset changed from ${previousOffset.toMillis()} ms to ${offset.toMillis()} ms")
        }

        // A 404 from /schedules/next means no upcoming event; any other failure keeps what we had
        val noUpcomingEvent = nextResult is RemoteResult.HttpFailure && nextResult.status == 404
        scheduleEventDao.replaceForArea(areaId, events)
        areaStatusDao.upsertRefresh(
            AreaRefreshEntity(
                areaId = areaId,
                lastRefreshedAt = clock.instant(),
                windowStart = windowStart,
                windowEnd = windowEnd,
                clockOffsetMillis = offset.toMillis(),
                nextStage = if (noUpcomingEvent) null else nextEvent?.stage ?: previous?.nextStage,
                nextStartAt = if (noUpcomingEvent) null else nextEvent?.nextStartAt ?: previous?.nextStartAt,
                nextEndAt = if (noUpcomingEvent) null else nextEvent?.nextEndAt ?: previous?.nextEndAt,
            ),
        )
    }

    private fun AreaRefreshEntity.toApiNextEvent(): ScheduleEvent? {
        val start = nextStartAt ?: return null
        val end = nextEndAt ?: return null
        return ScheduleEvent(
            scheduleId = "next-$areaId-${start.epochSecond}",
            areaId = areaId,
            stage = nextStage ?: 0,
            startAt = start,
            endAt = end,
            updatedAt = lastRefreshedAt,
        )
    }

    companion object {
        const val DEFAULT_DAYS = 7
        const val MAX_DAYS = 31
        private const val OFFSET_LOG_THRESHOLD_MILLIS = 1_000L
    }
}
