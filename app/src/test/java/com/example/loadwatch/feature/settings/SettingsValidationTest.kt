package com.example.loadwatch.feature.settings

import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import com.example.loadwatch.data.repository.SaveSettingsResult
import com.example.loadwatch.data.repository.SettingsRepository
import com.example.loadwatch.fakes.FakeAreaDao
import com.example.loadwatch.fakes.FakeAreaRepository
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakeUserSettingsDao
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

// FR-SET-02 and FR-SET-05: the same rules as the API, checked before anything is saved or sent
class SettingsValidationTest {

    private val valid = SettingsRules.defaults("user-1", Instant.parse("2026-10-22T10:00:00Z")).copy(defaultAreaId = AREA)

    @Test
    fun `lead time must be between 5 and 120 minutes`() {
        assertEquals(listOf("notificationLeadMinutes"), SettingsRules.invalidFields(valid.copy(notificationLeadMinutes = 4)))
        assertEquals(emptyList<String>(), SettingsRules.invalidFields(valid.copy(notificationLeadMinutes = 5)))
        assertEquals(emptyList<String>(), SettingsRules.invalidFields(valid.copy(notificationLeadMinutes = 120)))
        assertEquals(listOf("notificationLeadMinutes"), SettingsRules.invalidFields(valid.copy(notificationLeadMinutes = 121)))
    }

    @Test
    fun `language must be en, zu or af`() {
        listOf("en", "zu", "af").forEach { code ->
            assertEquals(emptyList<String>(), SettingsRules.invalidFields(valid.copy(languageCode = code)))
        }
        assertEquals(listOf("languageCode"), SettingsRules.invalidFields(valid.copy(languageCode = "fr")))
        assertEquals(listOf("languageCode"), SettingsRules.invalidFields(valid.copy(languageCode = "EN")))
    }

    @Test
    fun `theme must be system, light or dark`() {
        listOf("system", "light", "dark").forEach { theme ->
            assertEquals(emptyList<String>(), SettingsRules.invalidFields(valid.copy(themePreference = theme)))
        }
        assertEquals(listOf("themePreference"), SettingsRules.invalidFields(valid.copy(themePreference = "blue")))
    }

    @Test
    fun `alert types must be known ones, and none at all is allowed`() {
        assertEquals(emptyList<String>(), SettingsRules.invalidFields(valid.copy(enabledAlertTypes = "")))
        assertEquals(listOf("enabledAlertTypes"), SettingsRules.invalidFields(valid.copy(enabledAlertTypes = "POWER_OFF,BOGUS")))
    }

    @Test
    fun `typed lead times are parsed and range checked`() {
        assertEquals(LeadTimeInput.OutOfRange, SettingsFormValidation.parseLeadMinutes("4"))
        assertEquals(LeadTimeInput.Valid(5), SettingsFormValidation.parseLeadMinutes("5"))
        assertEquals(LeadTimeInput.Valid(120), SettingsFormValidation.parseLeadMinutes(" 120 "))
        assertEquals(LeadTimeInput.OutOfRange, SettingsFormValidation.parseLeadMinutes("121"))
        assertEquals(LeadTimeInput.Empty, SettingsFormValidation.parseLeadMinutes(""))
        assertEquals(LeadTimeInput.NotWholeNumber, SettingsFormValidation.parseLeadMinutes("ten"))
    }

    @Test
    fun `invalid settings are neither stored nor sent`() = runTest {
        val areaDao = FakeAreaDao(listOf(AreaEntity(AREA, "Berea", "eThekwini", "KwaZulu-Natal", "MUNICIPAL", null, null, true)))
        val settingsDao = FakeUserSettingsDao(listOf(valid))
        val api = FakeLoadWatchApi()
        val repository = SettingsRepository(
            authSession = FakeAuthSession(),
            local = SettingsLocalDataSource(settingsDao),
            remote = SettingsRemoteDataSource(api, testJson),
            areaRepository = FakeAreaRepository(areaDao),
        )

        val result = repository.save(valid.toModel().copy(notificationLeadMinutes = 121, themePreference = "blue"))

        assertEquals(SaveSettingsResult.Rejected(listOf("notificationLeadMinutes", "themePreference")), result)
        assertEquals(listOf(valid), settingsDao.settings.value)
        assertEquals(0, api.callCount("updateSettings"))
    }

    private companion object {
        const val AREA = "eth-berea"
    }
}
