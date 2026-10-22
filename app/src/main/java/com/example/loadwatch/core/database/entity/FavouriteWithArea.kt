package com.example.loadwatch.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

// A favourite together with its area, so S08 and S05 can show names without a second query
data class FavouriteWithArea(
    @Embedded val favourite: FavouriteAreaEntity,
    @Relation(parentColumn = "areaId", entityColumn = "areaId")
    val area: AreaEntity,
)
