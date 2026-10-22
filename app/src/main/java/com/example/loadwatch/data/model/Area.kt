package com.example.loadwatch.data.model

// An area as the features see it. Municipality and supplier travel with the name so similarly named suburbs
// can always be told apart (FR-AREA-02).
data class Area(
    val areaId: String,
    val name: String,
    val municipality: String,
    val province: String,
    val supplier: Supplier,
)

enum class Supplier {
    ESKOM,
    MUNICIPAL,
    UNKNOWN,
    ;

    companion object {
        fun fromSupplyType(supplyType: String): Supplier =
            entries.firstOrNull { it.name == supplyType } ?: UNKNOWN
    }
}
