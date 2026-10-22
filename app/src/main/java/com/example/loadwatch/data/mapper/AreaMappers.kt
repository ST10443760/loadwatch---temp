package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.network.dto.AreaDto

fun AreaDto.toEntity() = AreaEntity(
    areaId = areaId,
    name = name,
    municipality = municipality,
    province = province,
    supplyType = supplyType,
    latitude = latitude,
    longitude = longitude,
    isActive = isActive,
)
