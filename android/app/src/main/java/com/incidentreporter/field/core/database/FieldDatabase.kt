package com.incidentreporter.field.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [IncidentEntity::class, PhotoEntity::class], version = 1, exportSchema = false)
abstract class FieldDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
}
