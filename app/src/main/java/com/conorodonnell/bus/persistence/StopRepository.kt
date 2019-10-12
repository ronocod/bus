package com.conorodonnell.bus.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import io.reactivex.Flowable
import io.reactivex.Single

@Dao
interface StopRepository {

    @Query("SELECT COUNT(*) FROM Stop")
    fun count(): Single<Int>

    @Query("SELECT * FROM Stop WHERE id = :id LIMIT 1")
    fun findById(id: String): Single<Stop>

    @Query("SELECT * FROM Stop ")
    fun findAll(): Flowable<List<Stop>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(stops: List<Stop>)
}
