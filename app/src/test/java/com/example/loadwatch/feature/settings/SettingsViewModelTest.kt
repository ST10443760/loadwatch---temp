package com.example.loadwatch.feature.settings

import com.example.loadwatch.core.authentication.SessionExpiryNotifier
import com.example.loadwatch.core.biometric.BiometricAvailability
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
import com.example.loadwatch.fakes.FakeBiometricChecker
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
import org.junit.Assert.assertTrue
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
    private val biometricChecker = FakeBiometricChecker()

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
        val viewModel = SettingsViewModel(settingsRepository, authRepository, areaRepository, FakeFavouritesRepository(), biometricChecker)
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

    @Test
    fun `turning biometric unlock on is saved to Room and the API straight away`() = runTest {
        api.getSettingsHandler = { userId -> serverSettings(userId, leadMinutes = 30) }
        api.updateSettingsHandler = { userId, update -> serverSettings(userId, leadMinutes = 30).copy(biometricEnabled = update.biometricEnabled) }
        val viewModel = newViewModel()
        runCurrent()

        viewModel.setBiometricEnabled(true)
        runCurrent()

        assertEquals(1, api.callCount("updateSettings"))
        assertTrue(settingsDao.get(USER)!!.biometricEnabled)
        assertTrue(viewModel.uiState.value.form!!.biometricEnabled)
        assertFalse("Nothing is left unsaved", viewModel.uiState.value.isDirty)
    }

    @Test
    fun `turning biometric unlock on keeps other unsaved edits in the form`() = runTest {
        api.getSettingsHandler = { userId -> serverSettings(userId, leadMinutes = 30) }
        api.updateSettingsHandler = { userId, update -> serverSettings(userId, leadMinutes = 30).copy(biometricEnabled = update.biometricEnabled) }
        val viewModel = newViewModel()
        runCurrent()

        viewModel.setLeadMinutesText("60")
        viewModel.setBiometricEnabled(true)
        runCurrent()

        assertEquals("60", viewModel.uiState.value.form?.leadMinutesText)
        assertEquals("The lead time was not saved with it", 30, settingsDao.get(USER)!!.notificationLeadMinutes)
    }

    @Test
    fun `biometric availability follows the phone each time it is refreshed`() = runTest {
        val viewModel = newViewModel()
        runCurrent()

        biometricChecker.availability = BiometricAvailability.NONE_ENROLLED
        viewModel.refreshBiometricAvailability()
        runCurrent()
        assertEquals(BiometricAvailability.NONE_ENROLLED, viewModel.uiState.value.biometricAvailability)

        biometricChecker.availability = BiometricAvailability.AVAILABLE
        viewModel.refreshBiometricAvailability()
        runCurrent()
        assertEquals(BiometricAvailability.AVAILABLE, viewModel.uiState.value.biometricAvailability)
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
