package com.example.loadwatch.data.model

import com.example.loadwatch.core.common.OutageEventType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// A scheduled outage that has started, with how many community reports fall inside it
data class HistoryOutage(
    val scheduleId: String,
    val stage: Int,
    val startAt: Instant,
    val endAt: Instant,
    val reportCount: Int,
)

// A community report from anyone in the area, without who sent it
data class HistoryReport(
    val reportId: String,
    val eventType: OutageEventType,
    val reportedAt: Instant,
)

// One South African day of an area's history
data class HistoryDay(
    val date: LocalDate,
    val outages: List<HistoryOutage>,
    val reports: List<HistoryReport>,
)

// An area's cached history for one month. fetchedAt is null until the month has been loaded at least once, so an
// empty month can be told apart from one never fetched.
data class HistoryMonth(
    val areaId: String,
    val month: YearMonth,
    val outages: List<HistoryOutage>,
    val reports: List<HistoryReport>,
    val fetchedAt: Instant?,
)
