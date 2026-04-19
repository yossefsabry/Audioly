package com.audioly.shared.data.db

/**
 * Creates the iOS database service backed by SQLDelight / SQLite.
 * Call from SwiftUI app init code.
 */
fun createDatabaseService(): AudiolyDatabaseService =
    SqlDelightDatabaseService(DatabaseDriverFactory())
