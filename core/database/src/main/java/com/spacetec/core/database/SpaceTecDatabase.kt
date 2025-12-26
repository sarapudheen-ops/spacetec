package com.spacetec.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.spacetec.core.database.entities.*
import com.spacetec.core.database.dao.*

@Database(
    entities = [
        DTCEntity::class,
        DiagnosticSessionEntity::class,
        SessionDTCEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SpaceTecDatabase : RoomDatabase() {
    
    abstract fun dtcDao(): DTCDao
    abstract fun diagnosticSessionDao(): DiagnosticSessionDao
    abstract fun sessionDTCDao(): SessionDTCDao
    
    companion object {
        const val DATABASE_NAME = "spacetec_database"
        
        @Volatile
        private var INSTANCE: SpaceTecDatabase? = null
        
        fun getDatabase(context: Context): SpaceTecDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpaceTecDatabase::class.java,
                    DATABASE_NAME
                )
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populate with common DTCs
                populateInitialData(db)
            }
        }
        
        private fun populateInitialData(db: SupportSQLiteDatabase) {
            // Insert common DTCs
            val commonDTCs = listOf(
                "INSERT INTO dtc_codes (code, description, explanation, category, system, severity, possibleCauses, symptoms, diagnosticSteps, isEmissionRelated) VALUES " +
                "('P0300', 'Random/Multiple Cylinder Misfire Detected', 'Engine is misfiring randomly across multiple cylinders', 'P', 'Ignition', 'HIGH', '[\"Faulty spark plugs\", \"Bad ignition coils\", \"Fuel delivery issues\"]', '[\"Rough idle\", \"Loss of power\", \"Check engine light\"]', '[\"Check spark plugs\", \"Test ignition coils\", \"Check fuel pressure\"]', 1)",
                
                "INSERT INTO dtc_codes (code, description, explanation, category, system, severity, possibleCauses, symptoms, diagnosticSteps, isEmissionRelated) VALUES " +
                "('P0420', 'Catalyst System Efficiency Below Threshold (Bank 1)', 'Catalytic converter is not working efficiently', 'P', 'Emission', 'MEDIUM', '[\"Faulty catalytic converter\", \"Oxygen sensor failure\", \"Engine running rich/lean\"]', '[\"Reduced fuel economy\", \"Failed emissions test\", \"Check engine light\"]', '[\"Test oxygen sensors\", \"Check exhaust system\", \"Verify catalyst efficiency\"]', 1)",
                
                "INSERT INTO dtc_codes (code, description, explanation, category, system, severity, possibleCauses, symptoms, diagnosticSteps, isEmissionRelated) VALUES " +
                "('P0171', 'System Too Lean (Bank 1)', 'Engine is running too lean on bank 1', 'P', 'Fuel', 'MEDIUM', '[\"Vacuum leak\", \"Faulty MAF sensor\", \"Fuel pump issues\"]', '[\"Rough idle\", \"Hesitation\", \"Poor acceleration\"]', '[\"Check for vacuum leaks\", \"Test MAF sensor\", \"Check fuel pressure\"]', 1)"
            )
            
            commonDTCs.forEach { sql ->
                db.execSQL(sql)
            }
        }
    }
}
