package com.example.loadwatch.core.network.dto

import kotlinx.serialization.Serializable

// GET /api/areas?q=&page= returns AreaSummary[]
@Serializable
data class AreaSummaryDto(
    val areaId: String,
    val name: String,
    val municipality: String,
    val province: String,
    val supplyType: String,
)

// GET /api/areas/{areaId} returns Area
@Serializable
data class AreaDto(
    val areaId: String,
    val name: String,
    val municipality: String,
    val province: String,
    val supplyType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isActive: Boolean,
)
