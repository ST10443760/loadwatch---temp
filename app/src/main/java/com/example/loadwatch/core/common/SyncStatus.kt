package com.example.loadwatch.core.common

// Lifecycle of an outage report as shown to the user. Names match the API contract exactly.
enum class SyncStatus {
    PENDING,
    SYNCED,
    RETRYING,
    REJECTED,
}
