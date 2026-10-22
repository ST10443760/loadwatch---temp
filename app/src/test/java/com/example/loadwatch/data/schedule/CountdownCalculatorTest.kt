package com.example.loadwatch.data.schedule

import com.example.loadwatch.core.common.SupplyState
import com.example.loadwatch.data.model.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CountdownCalculatorTest {

    private val calculator = CountdownCalculator()

    // 20:00 to 22:30 UTC, then 04:00 to 06:30 the next morning
    private val evening = window("evening", "2026-10-22T20:00:00Z", "2026-10-22T22:30:00Z", stage = 2)
    private val morning = window("morning", "2026-10-23T04:00:00Z", "2026-10-23T06:30:00Z", stage = 3)
    private val events = listOf(morning, evening) // deliberately unsorted

    @Test
    fun `before a window the power is on and counts down to the window's start`() {
        val countdown = calculator.calculate(events, at("2026-10-22T18:17:42Z"))

        assertEquals(SupplyState.ON, countdown.state)
        assertEquals(evening.startAt, countdown.target)
        assertEquals(Duration.parse("PT1H42M18S"), countdown.remaining)
        assertNull(countdown.currentEvent)
        assertEquals(evening, countdown.nextEvent)
    }

    @Test
    fun `inside a window the power is off and counts down to the window's end`() {
        val countdown = calculator.calculate(events, at("2026-10-22T21:00:00Z"))

        assertEquals(SupplyState.OFF, countdown.state)
        assertEquals(evening.endAt, countdown.target)
        assertEquals(Duration.ofMinutes(90), countdown.remaining)
        assertEquals(evening, countdown.currentEvent)
        assertEquals("The next event is the one after the current window", morning, countdown.nextEvent)
    }

    @Test
    fun `the exact start instant is already off`() {
        val countdown = calculator.calculate(events, evening.startAt)

        assertEquals(SupplyState.OFF, countdown.state)
        assertEquals(evening.endAt, countdown.target)
    }

    @Test
    fun `the exact end instant is on again and targets the next window`() {
        val countdown = calculator.calculate(events, evening.endAt)

        assertEquals(SupplyState.ON, countdown.state)
        assertEquals(morning.startAt, countdown.target)
        assertEquals(Duration.parse("PT5H30M"), countdown.remaining)
    }

    @Test
    fun `reaching zero rolls forward to the next window within one tick`() {
        val lastSecondOff = calculator.calculate(events, evening.endAt.minusSeconds(1))
        val nextTick = calculator.calculate(events, evening.endAt)

        assertEquals(SupplyState.OFF, lastSecondOff.state)
        assertEquals(Duration.ofSeconds(1), lastSecondOff.remaining)
        assertEquals(SupplyState.ON, nextTick.state)
        assertEquals(morning.startAt, nextTick.target)
        assertEquals(morning, nextTick.nextEvent)
    }

    @Test
    fun `after the last window the power is on with nothing to count down to`() {
        val countdown = calculator.calculate(events, at("2026-10-23T08:00:00Z"))

        assertEquals(SupplyState.ON, countdown.state)
        assertNull(countdown.target)
        assertNull(countdown.remaining)
        assertNull(countdown.nextEvent)
    }

    @Test
    fun `no events at all means power on with no countdown`() {
        val countdown = calculator.calculate(emptyList(), at("2026-10-22T12:00:00Z"))

        assertEquals(SupplyState.ON, countdown.state)
        assertNull(countdown.target)
        assertNull(countdown.remaining)
        assertNull(countdown.currentEvent)
        assertNull(countdown.nextEvent)
    }

    @Test
    fun `a device clock running five minutes slow is corrected by the offset`() {
        // The real time is 19:58, two minutes before the window, but the device says 19:53
        val deviceNow = at("2026-10-22T19:53:00Z")
        val offset = Duration.ofMinutes(5)

        val uncorrected = calculator.calculate(events, deviceNow)
        val corrected = calculator.calculate(events, deviceNow, offset)

        assertEquals(Duration.ofMinutes(7), uncorrected.remaining)
        assertEquals(Duration.ofMinutes(2), corrected.remaining)
        assertEquals(at("2026-10-22T19:58:00Z"), corrected.now)
    }

    @Test
    fun `a device clock running seven minutes fast is corrected by a negative offset`() {
        // The real time is 22:25, inside the window, but the device already says 22:32
        val deviceNow = at("2026-10-22T22:32:00Z")
        val offset = Duration.ofMinutes(-7)

        val uncorrected = calculator.calculate(events, deviceNow)
        val corrected = calculator.calculate(events, deviceNow, offset)

        assertEquals("Without the offset the window looks over", SupplyState.ON, uncorrected.state)
        assertEquals(SupplyState.OFF, corrected.state)
        assertEquals(Duration.ofMinutes(5), corrected.remaining)
    }

    @Test
    fun `back to back windows count down to when power actually returns`() {
        val first = window("first", "2026-10-22T08:00:00Z", "2026-10-22T10:30:00Z", stage = 4)
        val second = window("second", "2026-10-22T10:30:00Z", "2026-10-22T12:30:00Z", stage = 4)
        val later = window("later", "2026-10-22T16:00:00Z", "2026-10-22T18:30:00Z", stage = 4)

        val countdown = calculator.calculate(listOf(first, second, later), at("2026-10-22T09:00:00Z"))

        assertEquals(SupplyState.OFF, countdown.state)
        assertEquals(second.endAt, countdown.target)
        assertEquals(later, countdown.nextEvent)
    }

    private fun at(instant: String): Instant = Instant.parse(instant)

    private fun window(id: String, start: String, end: String, stage: Int) =
        ScheduleEvent(id, "eth-berea", stage, at(start), at(end), at("2026-10-22T06:00:00Z"))
}
