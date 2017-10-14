package com.conorodonnell.bus.persistence

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class Stop(
        @PrimaryKey
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double
)
