package com.example.loadwatch.data.repository

import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.authentication.SessionExpiryNotifier
import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.database.TransactionRunner
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.dao.RecentAreaDao
import com.example.loadwatch.core.database.dao.UserSettingsDao
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.OutageReportEntity
import com.example.loadwatch.core.database.entity.PendingSyncEntity
import com.example.loadwatch.core.database.entity.PendingSyncStatus
import com.example.loadwatch.core.database.entity.RecentAreaEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import com.example.loadwatch.data.local.UserDataLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

// FR-AUTH-04: sign-out clears tokens and user-scoped data but never the public area catalogue.
class SignOutTest {

    private val store = FakeLocalStore()
    private val authSession = FakeAuthSession()
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        store.seed()
        val userData = UserDataLocalDataSource(
            transactionRunner = store.transactionRunner,
            userSettingsDao = store.userSettingsDao,
            favouriteAreaDao = store.favouriteAreaDao,
            outageReportDao = store.outageReportDao,
            pendingSyncDao = store.pendingSyncDao,
            recentAreaDao = store.recentAreaDao,
        )
        repository = AuthRepository(authSession, userData, SessionExpiryNotifier())
    }

    @Test
    fun `sign out ends the Firebase session`() = runTest {
        repository.signOut()

        assertEquals(1, authSession.signOutCalls)
        assertNull(repository.signedInUserId.value)
    }

    @Test
    fun `sign out clears every user-scoped table`() = runTest {
        repository.signOut()

        assertTrue(store.userSettings.isEmpty())
        assertTrue(store.favourites.isEmpty())
        assertTrue(store.outageReports.isEmpty())
        assertTrue(store.pendingSync.isEmpty())
        assertTrue(store.recentAreas.isEmpty())
    }

    @Test
    fun `sign out keeps the public area catalogue and schedules`() = runTest {
        val areasBefore = store.areas.toList()
        val schedulesBefore = store.scheduleEvents.toList()

        repository.signOut()

        assertEquals(areasBefore, store.areas)
        assertEquals(schedulesBefore, store.scheduleEvents)
    }

    @Test
    fun `user data is cleared in a single transaction`() = runTest {
        repository.signOut()

        assertEquals(1, store.transactionCount)
        assertTrue("Every delete ran inside the transaction", store.deletesOutsideTransaction == 0)
    }

    private class FakeAuthSession : AuthSession {
        override val signedInUserId = MutableStateFlow<String?>(USER_ID)
        var signOutCalls = 0

        override suspend fun idToken(forceRefresh: Boolean): String? = signedInUserId.value?.let { "token" }
        override suspend fun signInWithGoogleIdToken(googleIdToken: String) = Unit
        override suspend fun signOut() {
            signOutCalls++
            signedInUserId.value = null
        }
    }

    // One in-memory "database" shared by all the fake DAOs, including the public tables that must survive
    private class FakeLocalStore {
        val areas = mutableListOf<AreaEntity>()
        val scheduleEvents = mutableListOf<ScheduleEventEntity>()
        val userSettings = mutableListOf<UserSettingsEntity>()
        val favourites = mutableListOf<FavouriteAreaEntity>()
        val outageReports = mutableListOf<OutageReportEntity>()
        val pendingSync = mutableListOf<PendingSyncEntity>()
        val recentAreas = mutableListOf<RecentAreaEntity>()

        var transactionCount = 0
        var deletesOutsideTransaction = 0
        private var inTransaction = false

        private fun delete(table: MutableList<*>) {
            if (!inTransaction) deletesOutsideTransaction++
            table.clear()
        }

        fun seed() {
            val now = Instant.parse("2026-10-22T10:00:00Z")
            areas += AreaEntity(AREA_ID, "Berea", "eThekwini Metropolitan Municipality", "KwaZulu-Natal", "MUNICIPAL", -29.8, 31.0, true)
            scheduleEvents += ScheduleEventEntity("$AREA_ID-1", AREA_ID, 2, now, now.plusSeconds(9000), "SEED", now)
            userSettings += UserSettingsEntity(USER_ID, AREA_ID, 30, true, "POWER_OFF", "en", "system", false, now)
            favourites += FavouriteAreaEntity("fav-1", USER_ID, AREA_ID, "Home", 0, now)
            outageReports += OutageReportEntity("r-1", "c-1", USER_ID, AREA_ID, OutageEventType.POWER_OFF, now, now, SyncStatus.SYNCED)
            pendingSync += PendingSyncEntity("s-1", "OutageReport", "r-1", "{}", 0, now, PendingSyncStatus.PENDING, null)
            recentAreas += RecentAreaEntity(AREA_ID, now)
        }

        val transactionRunner = object : TransactionRunner {
            override suspend fun <T> inTransaction(block: suspend () -> T): T {
                transactionCount++
                inTransaction = true
                try {
                    return block()
                } finally {
                    inTransaction = false
                }
            }
        }

        val userSettingsDao = object : UserSettingsDao {
            override suspend fun upsert(settings: UserSettingsEntity) {
                userSettings.removeAll { it.userId == settings.userId }
                userSettings += settings
            }
            override suspend fun get(userId: String) = userSettings.firstOrNull { it.userId == userId }
            override fun observe(userId: String): Flow<UserSettingsEntity?> = flowOf(userSettings.firstOrNull { it.userId == userId })
            override suspend fun delete(userId: String) {
                userSettings.removeAll { it.userId == userId }
            }
            override suspend fun deleteAll() = delete(userSettings)
        }

        val favouriteAreaDao = object : FavouriteAreaDao {
            override suspend fun upsertAll(favourites: List<FavouriteAreaEntity>) {
                this@FakeLocalStore.favourites += favourites
            }
            override fun observe(userId: String): Flow<List<FavouriteAreaEntity>> = flowOf(favourites.filter { it.userId == userId })
            override suspend fun delete(userId: String, areaId: String): Int {
                val before = favourites.size
                favourites.removeAll { it.userId == userId && it.areaId == areaId }
                return before - favourites.size
            }
            override suspend fun deleteAllForUser(userId: String) {
                favourites.removeAll { it.userId == userId }
            }
            override suspend fun deleteAll() = delete(favourites)
        }

        val outageReportDao = object : OutageReportDao {
            override suspend fun insert(report: OutageReportEntity) {
                outageReports += report
            }
            override suspend fun upsertAll(reports: List<OutageReportEntity>) {
                outageReports += reports
            }
            override suspend fun getByClientReportId(userId: String, clientReportId: String) =
                outageReports.firstOrNull { it.userId == userId && it.clientReportId == clientReportId }
            override fun observeForArea(areaId: String, since: Instant): Flow<List<OutageReportEntity>> =
                flowOf(outageReports.filter { it.areaId == areaId })
            override suspend fun getByStatus(statuses: List<SyncStatus>) = outageReports.filter { it.syncStatus in statuses }
            override suspend fun updateSyncStatus(reportId: String, status: SyncStatus) {
                outageReports.replaceAll { if (it.reportId == reportId) it.copy(syncStatus = status) else it }
            }
            override suspend fun deleteAll() = delete(outageReports)
        }

        val pendingSyncDao = object : PendingSyncDao {
            override suspend fun insert(item: PendingSyncEntity) {
                pendingSync += item
            }
            override suspend fun update(item: PendingSyncEntity) {
                pendingSync.replaceAll { if (it.syncId == item.syncId) item else it }
            }
            override suspend fun delete(syncId: String) {
                pendingSync.removeAll { it.syncId == syncId }
            }
            override suspend fun getDue(now: Instant, rejected: PendingSyncStatus) =
                pendingSync.filter { it.status != rejected && !it.nextAttemptAt.isAfter(now) }
            override fun observeAll(): Flow<List<PendingSyncEntity>> = flowOf(pendingSync.toList())
            override suspend fun deleteAll() = delete(pendingSync)
        }

        val recentAreaDao = object : RecentAreaDao {
            override suspend fun upsert(recent: RecentAreaEntity) {
                recentAreas.removeAll { it.areaId == recent.areaId }
                recentAreas += recent
            }
            override suspend fun trimTo(keep: Int) {
                val keepIds = recentAreas.sortedByDescending { it.selectedAt }.take(keep).map { it.areaId }.toSet()
                recentAreas.removeAll { it.areaId !in keepIds }
            }
            override fun observeRecentAreas(): Flow<List<AreaEntity>> =
                flowOf(areas.filter { area -> recentAreas.any { it.areaId == area.areaId } })
            override suspend fun deleteAll() = delete(recentAreas)
        }
    }

    private companion object {
        const val USER_ID = "user-1"
        const val AREA_ID = "eth-berea"
    }
}
