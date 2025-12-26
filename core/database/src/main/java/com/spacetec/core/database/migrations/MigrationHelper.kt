package com.spacetec.obd.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Helper class for database migrations
 */
object MigrationHelper {
    
    /**
     * Get all available migrations
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            Migration_1_2(),
            Migration_2_3()
        )
    }
    
    /**
     * Helper method to safely execute SQL with error handling
     */
    fun executeSafely(database: SupportSQLiteDatabase, sql: String) {
        try {
            database.execSQL(sql)
        } catch (e: Exception) {
            // Log error but don't crash migration
            println("Migration SQL error: ${e.message}")
        }
    }
}