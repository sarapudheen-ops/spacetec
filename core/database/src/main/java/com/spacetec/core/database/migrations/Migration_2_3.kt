package com.spacetec.obd.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 2 to 3 - Enhanced DTC Database System
 */
class Migration_2_3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create enhanced DTC tables
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS dtc_codes (
                code TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                sub_type TEXT NOT NULL,
                category TEXT NOT NULL,
                manufacturer TEXT,
                description TEXT NOT NULL,
                detailed_description TEXT NOT NULL,
                consumer_description TEXT NOT NULL,
                severity TEXT NOT NULL,
                emissions_related INTEGER NOT NULL DEFAULT 0,
                safety_related INTEGER NOT NULL DEFAULT 0,
                mil_status INTEGER NOT NULL DEFAULT 0,
                possible_causes TEXT NOT NULL DEFAULT '[]',
                possible_symptoms TEXT NOT NULL DEFAULT '[]',
                affected_systems TEXT NOT NULL DEFAULT '[]',
                related_codes TEXT NOT NULL DEFAULT '[]',
                repair_difficulty TEXT NOT NULL,
                estimated_repair_time_minutes INTEGER NOT NULL,
                estimated_cost_min REAL,
                estimated_cost_max REAL,
                technical_notes TEXT,
                required_tools TEXT NOT NULL DEFAULT '[]',
                safety_warnings TEXT NOT NULL DEFAULT '[]',
                driving_recommendations TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                version INTEGER NOT NULL DEFAULT 1,
                source TEXT NOT NULL,
                confidence_level REAL NOT NULL DEFAULT 1.0,
                is_active INTEGER NOT NULL DEFAULT 1
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS repair_procedures (
                id TEXT PRIMARY KEY NOT NULL,
                dtc_code TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                steps TEXT NOT NULL DEFAULT '[]',
                difficulty TEXT NOT NULL,
                estimated_time_minutes INTEGER NOT NULL,
                required_tools TEXT NOT NULL DEFAULT '[]',
                required_parts TEXT NOT NULL DEFAULT '[]',
                success_rate REAL NOT NULL DEFAULT 0.0,
                labor_cost_min REAL,
                labor_cost_max REAL,
                parts_cost_min REAL,
                parts_cost_max REAL,
                manufacturer TEXT,
                model_years TEXT,
                engine_types TEXT NOT NULL DEFAULT '[]',
                safety_precautions TEXT NOT NULL DEFAULT '[]',
                special_notes TEXT,
                warranty_implications TEXT,
                times_used INTEGER NOT NULL DEFAULT 0,
                successful_repairs INTEGER NOT NULL DEFAULT 0,
                failed_repairs INTEGER NOT NULL DEFAULT 0,
                average_completion_time INTEGER,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                version INTEGER NOT NULL DEFAULT 1,
                source TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (dtc_code) REFERENCES dtc_codes (code) ON DELETE CASCADE
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS technical_service_bulletins (
                id TEXT PRIMARY KEY NOT NULL,
                manufacturer TEXT NOT NULL,
                bulletin_number TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                detailed_content TEXT NOT NULL,
                applicable_vehicles TEXT NOT NULL DEFAULT '[]',
                applicable_dtc_codes TEXT NOT NULL DEFAULT '[]',
                publish_date TEXT NOT NULL,
                effective_date TEXT,
                supersedes TEXT NOT NULL DEFAULT '[]',
                category TEXT NOT NULL,
                severity TEXT NOT NULL,
                repair_procedure TEXT NOT NULL,
                parts_required TEXT NOT NULL DEFAULT '[]',
                labor_time_hours REAL,
                warranty_extension INTEGER NOT NULL DEFAULT 0,
                warranty_details TEXT,
                recall_related INTEGER NOT NULL DEFAULT 0,
                recall_number TEXT,
                attachments TEXT NOT NULL DEFAULT '[]',
                special_tools TEXT NOT NULL DEFAULT '[]',
                training_required INTEGER NOT NULL DEFAULT 0,
                certification_level TEXT,
                view_count INTEGER NOT NULL DEFAULT 0,
                success_rate REAL,
                average_repair_time REAL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                version INTEGER NOT NULL DEFAULT 1,
                source_url TEXT,
                is_active INTEGER NOT NULL DEFAULT 1
            )
        """)
        
        // Create indices for performance
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dtc_codes_type ON dtc_codes (type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dtc_codes_severity ON dtc_codes (severity)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dtc_codes_manufacturer ON dtc_codes (manufacturer)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dtc_codes_category ON dtc_codes (category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dtc_codes_emissions_related ON dtc_codes (emissions_related)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dtc_codes_safety_related ON dtc_codes (safety_related)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dtc_codes_mil_status ON dtc_codes (mil_status)")
        
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_procedures_dtc_code ON repair_procedures (dtc_code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_procedures_difficulty ON repair_procedures (difficulty)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_procedures_success_rate ON repair_procedures (success_rate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_procedures_estimated_time_minutes ON repair_procedures (estimated_time_minutes)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_repair_procedures_is_active ON repair_procedures (is_active)")
        
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_technical_service_bulletins_manufacturer ON technical_service_bulletins (manufacturer)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_technical_service_bulletins_category ON technical_service_bulletins (category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_technical_service_bulletins_severity ON technical_service_bulletins (severity)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_technical_service_bulletins_publish_date ON technical_service_bulletins (publish_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_technical_service_bulletins_is_active ON technical_service_bulletins (is_active)")
    }
}