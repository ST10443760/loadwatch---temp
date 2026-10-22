package com.example.loadwatch.feature.settings

import com.example.loadwatch.core.authentication.SessionExpiryNotifier
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.local.UserDataLocalDataSource
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import com.example.loadwatch.data.repository.AuthRepository
import com.example.loadwatch.data.repository.SettingsRepository
import com.example.loadwatch.fakes.FakeAreaDao
import com.example.loadwatch.fakes.FakeAreaRepository
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeFavouriteAreaDao
import com.example.loadwatch.fakes.FakeFavouritesRepository
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakeOutageReportDao
import com.example.loadwatch.fakes.FakePendingSyncDao
import com.example.loadwatch.fakes.FakeRecentAreaDao
import com.example.loadwatch.fakes.FakeTransactionRunner
import com.example.loadwatch.fakes.FakeUserSettingsDao
import com.example.loadwatch.fakes.MainDispatcherRule
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

// FR-SET-04: settings open from Room first, then update from the API
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-22T10:00:00Z")
    private val areaDao = FakeAreaDao(listOf(AreaEntity(AREA, "Berea", "eThekwini", "KwaZulu-Natal", "MUNICIPAL", null, null, true)))
    private val cached = SettingsRules.defaults(USER, now).copy(defaultAreaId = AREA, notificationLeadMinutes = 30)
    private val settingsDao = FakeUserSettingsDao(listOf(cached))
    private val api = FakeLoadWatchApi()
    private val authSession = FakeAuthSession(USER)

    private fun TestScope.newViewModel(): SettingsViewModel {
        val areaRepository = FakeAreaRepository(areaDao)
        val settingsRepository = SettingsRepository(
            authSession = authSession,
            local = SettingsLocalDataSource(settingsDao),
            remote = SettingsRemoteDataSource(api, testJson),
            areaRepository = areaRepository,
        )
        val authRepository = AuthRepository(
            authSession = authSession,
            userData = UserDataLocalDataSource(
                FakeTransactionRunner(),
                settingsDao,
                FakeFavouriteAreaDao(areaDao),
                FakeOutageReportDao(),
                FakePendingSyncDao(),
                FakeRecentAreaDao(areaDao),
            ),
            sessionExpiryNotifier = SessionExpiryNotifier(),
        )
        val viewModel = SettingsViewModel(settingsRepository, authRepository, areaRepository, FakeFavouritesRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `the cached settings show first and the API's copy replaces them when it arrives`() = runTest {
        api.getSettingsHandler = { userId ->
            delay(1_000)
            serverSettings(userId, leadMinutes = 45)
        }
        val viewModel = newViewModel()

        runCurrent()
        assertEquals("Room answers before the network", "30", viewModel.uiState.value.form?.leadMinutesText)
        assertFalse(viewModel.uiState.value.isLoading)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals("45", viewModel.uiState.value.form?.leadMinutesText)
        assertNull(viewModel.uiState.value.loadFailure)
    }

    @Test
    fun `edits made before the API answers are kept`() = runTest {
        api.getSettingsHandler = { userId ->
            delay(1_000)
            serverSettings(userId, leadMinutes = 45)
        }
        val viewModel = newViewModel()
        runCurrent()

        viewModel.setLeadMinutesText("60")
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals("60", viewModel.uiState.value.form?.leadMinutesText)
        assertEquals(45, viewModel.uiState.value.saved?.notificationLeadMinutes)
    }

    @Test
    fun `offline the cached settings stay with a notice`() = runTest {
        val viewModel = newViewModel()
        runCurrent()

        assertEquals("30", viewModel.uiState.value.form?.leadMinutesText)
        assertEquals(DataFailure.OFFLINE, viewModel.uiState.value.loadFailure)
    }

    @Test
    fun `a lead time outside 5 to 120 cannot be saved`() = runTest {
        api.getSettingsHandler = { userId -> serverSettings(userId, leadMinutes = 30) }
        val viewModel = newViewModel()
        runCurrent()

        viewModel.setLeadMinutesText("121")
        runCurrent()
        assertEquals(LeadTimeInput.OutOfRange, viewModel.uiState.value.leadTime)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.save()
        runCurrent()
        assertEquals(0, api.callCount("updateSettings"))
    }

    private fun serverSettings(userId: String, leadMinutes: Int) = UserSettingsDto(
        userId = userId,
        defaultAreaId = AREA,
        notificationLeadMinutes = leadMinutes,
        notificationsEnabled = true,
        enabledAlertTypes = "POWER_OFF,POWER_BACK_ON,STAGE_CHANGE",
        languageCode = "en",
        themePreference = "system",
        biometricEnabled = false,
        updatedAt = now,
    )

    private companion object {
        const val USER = "user-1"
        const val AREA = "eth-berea"
    }
}
