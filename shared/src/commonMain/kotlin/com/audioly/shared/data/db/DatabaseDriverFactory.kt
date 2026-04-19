package com.audioly.shared.data.db

import app.cash.sqldelight.db.SqlDriver

/** Creates a platform-specific SQLDelight driver. */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
