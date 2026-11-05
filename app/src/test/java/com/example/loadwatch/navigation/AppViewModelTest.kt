package com.example.loadwatch.navigation

import com.example.loadwatch.core.authentication.SessionExpiryNotifier
import com.example.loadwatch.core.biometric.BiometricAvailability
import com.example.loadwatch.core.biometric.BiometricOffer
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.local.UserDataLocalDataSource
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import com.example.loadwatch.data.repository.AuthRepository
import com.example.loadwatch.data.repository.SettingsRepository
import com.example.loadwatch.fakes.FakeAreaDao
import com.example.loadwatch.fakes.FakeAreaRepository
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeBiometricChecker
import com.example.loadwatch.fakes.FakeFavouriteAreaDao
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakeOutageReportDao
import com.example.loadwatch.fakes.FakePendingSyncDao
import com.example.loadwatch.fakes.FakeRecentAreaDao
import com.example.loadwatch.fakes.FakeTransactionRunner
import com.example.loadwatch.fakes.FakeUserSettingsDao
import com.example.loadwatch.fakes.MainDispatcherRule
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

// The launch lock is decided from the settings saved on the phone, and the unlock offer only appears when it can help
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val areaDao = FakeAreaDao(listOf(AreaEntity(AREA, "Berea", "eThekwini", "KwaZulu-Natal", "MUNICIPAL", null, null, true)))
    private val biometricChecker = FakeBiometricChecker()
    private val biometricOffer = BiometricOffer()

    private fun newViewModel(
        biometricEnabled: Boolean?,
        authSession: FakeAuthSession = FakeAuthSession(USER),
    ): AppViewModel {
        val stored = biometricEnabled?.let { SettingsRules.defaults(USER, now).copy(defaultAreaId = AREA, biometricEnabled = it) }
        val settingsDao = FakeUserSettingsDao(listOfNotNull(stored))
        val settingsRepository = SettingsRepository(
            authSession = authSession,
            local = SettingsLocalDataSource(settingsDao),
            remote = SettingsRemoteDataSource(FakeLoadWatchApi(), testJson),
            areaRepository = FakeAreaRepository(areaDao),
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
        return AppViewModel(authRepository, settingsRepository, biometricChecker, biometricOffer)
    }

    @Test
    fun `the app opens locked when biometric unlock is saved as on`() = runTest {
        val viewModel = newViewModel(biometricEnabled = true)
        runCurrent()

        assertEquals(AppLockState.LOCKED, viewModel.lockState.value)
    }

    @Test
    fun `the app opens unlocked when biometric unlock is off or nothing is saved`() = runTest {
        val off = newViewModel(biometricEnabled = false)
        val nothingSaved = newViewModel(biometricEnabled = null)
        val signedOut = newViewModel(biometricEnabled = true, authSession = FakeAuthSession(userId = null))
        runCurrent()

        assertEquals(AppLockState.UNLOCKED, off.lockState.value)
        assertEquals(AppLockState.UNLOCKED, nothingSaved.lockState.value)
        assertEquals(AppLockState.UNLOCKED, signedOut.lockState.value)
    }

    @Test
    fun `unlocking opens the app`() = runTest {
        val viewModel = newViewModel(biometricEnabled = true)
        runCurrent()

        viewModel.onUnlocked()

        assertEquals(AppLockState.UNLOCKED, viewModel.lockState.value)
    }

    @Test
    fun `biometric unlock is offered once after signing in`() = runTest {
        val viewModel = newViewModel(biometricEnabled = false)
        runCurrent()
        assertFalse("Not offered before signing in", viewModel.showBiometricOffer.value)

        biometricOffer.offerAfterSignIn()
        runCurrent()
        assertTrue(viewModel.showBiometricOffer.value)

        viewModel.dismissBiometricOffer()
        runCurrent()
        assertFalse(viewModel.showBiometricOffer.value)
    }

    @Test
    fun `biometric unlock is not offered when it is already on or the phone cannot use it`() = runTest {
        val alreadyOn = newViewModel(biometricEnabled = true)
        biometricOffer.offerAfterSignIn()
        runCurrent()
        assertFalse(alreadyOn.showBiometricOffer.value)

        biometricChecker.availability = BiometricAvailability.NONE_ENROLLED
        val notEnrolled = newViewModel(biometricEnabled = false)
        runCurrent()
        assertFalse(notEnrolled.showBiometricOffer.value)
    }

    private companion object {
        const val USER = "user-1"
        const val AREA = "eth-berea"
    }
}
