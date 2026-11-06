package com.example.loadwatch.data.repository

import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.network.dto.SettingsChangeDto
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import com.example.loadwatch.data.sync.SyncEntityType
import com.example.loadwatch.fakes.FakeAreaDao
import com.example.loadwatch.fakes.FakeAreaRepository
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakePendingSyncDao
import com.example.loadwatch.fakes.FakeUserSettingsDao
import com.example.loadwatch.fakes.testJson
import com.example.loadwatch.fakes.testSyncQueue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Settings saved offline wait in the sync queue, and stay on the phone until the API has them
class SettingsSyncTest {

    private val now = Instant.parse("2026-11-06T10:00:00Z")
    private val api = FakeLoadWatchApi()
    private val pendingSyncDao = FakePendingSyncDao()
    private val settingsDao = FakeUserSettingsDao(listOf(SettingsRules.defaults(USER, now).copy(defaultAreaId = AREA)))
    private val repository = SettingsRepository(
        authSession = FakeAuthSession(USER),
        local = SettingsLocalDataSource(settingsDao),
        remote = SettingsRemoteDataSource(api, testJson),
        areaRepository = FakeAreaRepository(FakeAreaDao(listOf(AreaEntity(AREA, "Berea", "eThekwini", "KwaZulu-Natal", "MUNICIPAL", null, null, true)))),
        syncQueue = testSyncQueue(pendingSyncDao),
    )

    @Test
    fun `offline a save is kept on the phone and queued with the time it was saved`() = runTest {
        val result = repository.save(current().copy(languageCode = "zu"))

        assertTrue(result is SaveSettingsResult.SavedOnDeviceOnly)
        assertEquals("zu", settingsDao.get(USER)?.languageCode)
        val change = testJson.decodeFromString<SettingsChangeDto>(pendingSyncDao.items.value.single().payloadJson)
        assertEquals("zu", change.languageCode)
        assertEquals(settingsDao.get(USER)?.updatedAt, change.changedAt)
    }

    @Test
    fun `a later save that reaches the API clears the older queued change`() = runTest {
        repository.save(current().copy(languageCode = "zu"))
        api.updateSettingsHandler = { userId, update ->
            UserSettingsDto(userId, update.defaultAreaId, update.notificationLeadMinutes, update.notificationsEnabled,
                update.enabledAlertTypes, update.languageCode, update.themePreference, update.biometricEnabled, now)
        }

        assertEquals(SaveSettingsResult.Saved, repository.save(current().copy(languageCode = "af")))

        assertTrue(pendingSyncDao.items.value.isEmpty())
    }

    @Test
    fun `restoring from the API keeps settings that are still waiting to sync`() = runTest {
        repository.save(current().copy(languageCode = "zu"))
        api.getSettingsHandler = { userId -> UserSettingsDto(userId, AREA, 30, true, "POWER_OFF", "en", "system", false, now) }

        repository.refreshFromServer()

        assertEquals(0, api.callCount("getSettings"))
        assertEquals("zu", settingsDao.get(USER)?.languageCode)
        assertTrue(pendingSyncDao.items.value.single().entityType == SyncEntityType.SETTINGS)
    }

    private suspend fun current() = settingsDao.get(USER)!!.toModel()

    private companion object {
        const val USER = "user-1"
        const val AREA = "eth-berea"
    }
}
