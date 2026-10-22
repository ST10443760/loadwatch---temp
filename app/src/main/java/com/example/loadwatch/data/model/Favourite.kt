package com.example.loadwatch.data.model

// A saved area with the user's optional label, such as Home or Work (FR-FAV-01)
data class Favourite(
    val favouriteId: String,
    val area: Area,
    val label: String,
    val sortOrder: Int,
)

// Favourite labels are optional and at most 40 characters, matching the API (FR-FAV-01)
object FavouriteLabel {
    const val MAX_LENGTH = 40

    sealed interface Validation {
        data class Valid(val label: String) : Validation
        data object TooLong : Validation
    }

    // Trims surrounding spaces; an empty label is allowed
    fun validate(raw: String?): Validation {
        val label = raw?.trim().orEmpty()
        return if (label.length > MAX_LENGTH) Validation.TooLong else Validation.Valid(label)
    }
}
