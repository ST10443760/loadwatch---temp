package com.example.loadwatch.core.network.dto

import kotlinx.serialization.Serializable

// Every API failure is a ProblemDetails object rather than an HTML page.
// errors carries per-field validation messages on a 400; existingReportId accompanies a duplicate-report 409.
@Serializable
data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val errors: Map<String, List<String>>? = null,
    val existingReportId: String? = null,
)
