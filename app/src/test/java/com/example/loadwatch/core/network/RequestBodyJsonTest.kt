package com.example.loadwatch.core.network

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.network.dto.FavouriteAreaCreateDto
import com.example.loadwatch.core.network.dto.FavouriteChangeDto
import com.example.loadwatch.core.network.dto.NotificationRegistrationDto
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.SettingsChangeDto
import com.example.loadwatch.core.network.dto.SyncBatchDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Every request body is encoded with the Json the app really uses, which leaves out fields equal to their default
// and null values. Each test checks that what the API requires is still on the wire.
class RequestBodyJsonTest {

    private val json = NetworkModule.provideJson()
    private val at = Instant.parse("2026-11-06T10:00:00Z")

    @Test
    fun `the push registration always carries the platform`() {
        val body = encode(NotificationRegistrationDto(fcmToken = "fcm-token"))

        assertEquals(JsonPrimitive("fcm-token"), body["fcmToken"])
        assertEquals(JsonPrimitive("android"), body["platform"])
        assertTrue(json.encodeToString(NotificationRegistrationDto("fcm-token")).contains("\"platform\":\"android\""))
    }

    @Test
    fun `an outage report carries every field the API requires`() {
        val body = encode(report("client-1"))

        assertEquals(JsonPrimitive("eth-berea"), body["areaId"])
        assertEquals(JsonPrimitive("POWER_OFF"), body["eventType"])
        assertEquals(JsonPrimitive("2026-11-06T10:00:00Z"), body["reportedAt"])
        assertEquals(JsonPrimitive("2026-11-06T10:00:00Z"), body["clientCreatedAt"])
        assertEquals(JsonPrimitive("client-1"), body["clientReportId"])
    }

    @Test
    fun `a new favourite carries the area, and the label only when there is one`() {
        assertEquals(setOf("areaId"), encode(FavouriteAreaCreateDto("eth-berea")).keys)
        assertEquals(JsonPrimitive("Home"), encode(FavouriteAreaCreateDto("eth-berea", "Home"))["label"])
    }

    @Test
    fun `a settings update carries every value of the form`() {
        val body = encode(settingsUpdate(defaultAreaId = "eth-berea"))

        assertEquals(
            setOf(
                "defaultAreaId", "notificationLeadMinutes", "notificationsEnabled", "enabledAlertTypes",
                "languageCode", "themePreference", "biometricEnabled",
            ),
            body.keys,
        )
        // false and 0 are real values here, so they must not be dropped like defaults
        assertEquals(JsonPrimitive(false), body["biometricEnabled"])
    }

    @Test
    fun `a settings update without an area leaves defaultAreaId out, which the API reads as no area`() {
        assertFalse("defaultAreaId" in encode(settingsUpdate(defaultAreaId = null)))
    }

    @Test
    fun `a sync batch carries its reports, favourite changes and settings`() {
        val batch = SyncBatchDto(
            reports = listOf(report("client-1")),
            favourites = listOf(FavouriteChangeDto("change-1", "eth-berea", removed = false, changedAt = at)),
            settings = SettingsChangeDto("change-2", at, null, 30, true, "POWER_OFF", "en", "system", false),
        )
        val body = encode(batch)

        val report = body.getValue("reports").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive("client-1"), report["clientReportId"])
        assertEquals(JsonPrimitive("POWER_OFF"), report["eventType"])

        val favourite = body.getValue("favourites").jsonArray.single().jsonObject
        assertEquals(setOf("changeId", "areaId", "removed", "changedAt"), favourite.keys)
        assertEquals(JsonPrimitive(false), favourite["removed"])

        val settings = body.getValue("settings").jsonObject
        assertEquals(JsonPrimitive("change-2"), settings["changeId"])
        assertEquals(JsonPrimitive("2026-11-06T10:00:00Z"), settings["changedAt"])
        assertEquals(JsonPrimitive(30), settings["notificationLeadMinutes"])
    }

    @Test
    fun `a removed favourite says so`() {
        val change = encode(FavouriteChangeDto("change-1", "eth-berea", removed = true, changedAt = at))
        assertEquals(JsonPrimitive(true), change["removed"])
    }

    @Test
    fun `an empty part of a sync batch may be left out, because the API treats it as nothing to sync`() {
        assertEquals(JsonObject(emptyMap()), encode(SyncBatchDto()))
    }

    private inline fun <reified T> encode(value: T): JsonObject = json.encodeToJsonElement(value).jsonObject

    private fun report(clientReportId: String) =
        OutageReportCreateDto("eth-berea", OutageEventType.POWER_OFF, at, at, clientReportId)

    private fun settingsUpdate(defaultAreaId: String?) =
        UserSettingsUpdateDto(defaultAreaId, 30, true, "POWER_OFF,STAGE_CHANGE", "en", "dark", false)
}
