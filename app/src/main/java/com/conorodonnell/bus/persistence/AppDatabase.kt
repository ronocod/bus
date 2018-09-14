package com.conorodonnell.bus.persistence

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase

@Database(entities = [Stop::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun stops(): StopRepository
}
