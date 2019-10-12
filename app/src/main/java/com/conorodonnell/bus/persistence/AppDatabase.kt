package com.conorodonnell.bus.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Stop::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun stops(): StopRepository
}
