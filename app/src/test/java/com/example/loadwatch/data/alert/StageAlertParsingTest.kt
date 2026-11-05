package com.example.loadwatch.data.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

// The payload the API sends (StageAlertMessage.Data); anything else must be ignored, never crash the service
class StageAlertParsingTest {

    private val valid = mapOf(
        "type" to "STAGE_CHANGE",
        "areaId" to "eth-berea",
        "areaName" to "Berea",
        "previousStage" to "2",
        "stage" to "4",
        "changedAt" to "2026-11-05T10:00:00Z",
    )

    @Test
    fun `a stage change message is read field by field`() {
        assertEquals(
            StageAlert("eth-berea", "Berea", previousStage = 2, stage = 4, changedAt = Instant.parse("2026-11-05T10:00:00Z")),
            parseStageAlert(valid),
        )
    }

    @Test
    fun `another message type is ignored`() {
        assertNull(parseStageAlert(valid + ("type" to "SOMETHING_ELSE")))
        assertNull(parseStageAlert(valid - "type"))
    }

    @Test
    fun `a missing or malformed field is ignored`() {
        assertNull(parseStageAlert(valid - "areaId"))
        assertNull(parseStageAlert(valid + ("stage" to "four")))
        assertNull(parseStageAlert(valid + ("stage" to "9")))
        assertNull(parseStageAlert(valid + ("previousStage" to "-1")))
        assertNull(parseStageAlert(valid + ("changedAt" to "yesterday")))
    }

    @Test
    fun `a missing area name falls back to the area id`() {
        assertEquals("eth-berea", parseStageAlert(valid - "areaName")?.areaName)
    }
}
