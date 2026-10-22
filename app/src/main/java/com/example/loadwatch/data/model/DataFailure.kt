package com.example.loadwatch.data.model

// Why a request could not be completed, so screens can show the right recovery message (FR-AREA-04)
enum class DataFailure {
    // No connection to the API
    OFFLINE,

    // The API answered with an error
    SERVER,

    // The API refused the request because nobody is signed in
    NOT_SIGNED_IN,

    // The API does not know the requested area
    NOT_FOUND,
}
