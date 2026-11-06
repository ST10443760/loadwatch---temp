package com.example.loadwatch.core.database

import androidx.room.withTransaction
import javax.inject.Inject

// Runs a block of DAO calls atomically. An interface so data-layer classes can be unit tested with fake DAOs.
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/** [TransactionRunner] backed by Room's withTransaction, so several DAO writes commit or roll back together. */
class RoomTransactionRunner @Inject constructor(
    private val database: LoadWatchDatabase,
) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction(block)
}
