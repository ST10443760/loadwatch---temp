package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.network.dto.AreaDto
import com.example.loadwatch.core.network.dto.AreaSummaryDto
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.Supplier

/** Maps a full area from the API to its Room row. */
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

// Search results carry no coordinates; AreaDao.cacheSummaries keeps any coordinates already stored
fun AreaSummaryDto.toEntity() = AreaEntity(
    areaId = areaId,
    name = name,
    municipality = municipality,
    province = province,
    supplyType = supplyType,
    latitude = null,
    longitude = null,
    isActive = true,
)

/** Maps an area search result from the API to the domain model. */
fun AreaSummaryDto.toModel() = Area(
    areaId = areaId,
    name = name,
    municipality = municipality,
    province = province,
    supplier = Supplier.fromSupplyType(supplyType),
)

/** Maps a cached area row to the domain model the screens use. */
fun AreaEntity.toModel() = Area(
    areaId = areaId,
    name = name,
    municipality = municipality,
    province = province,
    supplier = Supplier.fromSupplyType(supplyType),
)
