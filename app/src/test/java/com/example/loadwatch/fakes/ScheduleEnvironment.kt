package com.example.loadwatch.fakes

import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.AreaRefreshEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.network.dto.ScheduleEventDto
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.remote.ScheduleRemoteDataSource
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import com.example.loadwatch.data.repository.ScheduleRepository
import com.example.loadwatch.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.Duration
import java.time.Instant

// A signed-in user whose default area is Berea, with real settings and schedule repositories over fakes and a
// clock that follows the test scheduler. Used by the Home and Schedule view model tests.
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEnvironment(scheduler: TestCoroutineScheduler, val start: Instant = Instant.parse("2026-10-22T18:00:00Z")) {
    val clock = TestClock(start, scheduler)
    val api = FakeLoadWatchApi()
    val areaDao = FakeAreaDao(listOf(AreaEntity(AREA, "Berea", "eThekwini Metropolitan Municipality", "KwaZulu-Natal", "MUNICIPAL", null, null, true)))
    val settingsDao = FakeUserSettingsDao(listOf(SettingsRules.defaults(USER, start).copy(defaultAreaId = AREA)))
    val transactions = FakeTransactionRunner()
    val scheduleDao = FakeScheduleEventDao(transactions)
    val statusDao = FakeAreaStatusDao()
    private val areaRepository = FakeAreaRepository(areaDao)

    val settingsRepository = SettingsRepository(
        authSession = FakeAuthSession(USER),
        local = SettingsLocalDataSource(settingsDao),
        remote = SettingsRemoteDataSource(api, testJson),
        areaRepository = areaRepository,
    )

    val scheduleRepository = ScheduleRepository(
        remote = ScheduleRemoteDataSource(api, testJson),
        areaDao = areaDao,
        scheduleEventDao = scheduleDao,
        areaStatusDao = statusDao,
        areaRepository = areaRepository,
        transactionRunner = transactions,
        clock = clock,
    )

    // Puts a schedule in the cache as if refreshed `age` ago, covering seven days from local midnight
    suspend fun cache(events: List<ScheduleEventEntity>, age: Duration = Duration.ofHours(1)) {
        scheduleDao.events.value = events
        val todayStart = start.atZone(SAST).toLocalDate().atStartOfDay(SAST).toInstant()
        statusDao.upsertRefresh(
            AreaRefreshEntity(AREA, start.minus(age), todayStart, todayStart.plus(Duration.ofDays(7)), 0, null, null, null),
        )
    }

    fun event(id: String, startAt: Instant, endAt: Instant, stage: Int = 2) =
        ScheduleEventEntity(id, AREA, stage, startAt, endAt, "SEED", start)

    fun dto(id: String, startAt: Instant, endAt: Instant, stage: Int = 2) =
        ScheduleEventDto(id, AREA, stage, startAt, endAt, "SEED", start)

    companion object {
        const val USER = "user-1"
        const val AREA = "eth-berea"
    }
}
