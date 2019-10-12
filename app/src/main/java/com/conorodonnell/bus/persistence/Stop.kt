package com.conorodonnell.bus.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Stop(
  @PrimaryKey
  val id: String,
  val name: String,
  val latitude: Double,
  val longitude: Double
)
