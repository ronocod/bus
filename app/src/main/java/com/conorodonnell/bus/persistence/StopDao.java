package com.conorodonnell.bus.persistence;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.RoomWarnings;

import java.util.List;

import io.reactivex.Single;

@Dao
public interface StopDao {

    @Query("SELECT COUNT(*) FROM Stop")
    Single<Integer> count();

    @Query("SELECT * FROM Stop WHERE id = :id LIMIT 1")
    Single<Stop> findById(String id);

    @Query("SELECT * FROM Stop " +
            "WHERE (latitude BETWEEN :south AND :north) " +
            "AND (longitude BETWEEN :west AND :east)")
    Single<List<Stop>> findInArea(double north, double south, double west, double east);

    @Query("SELECT * FROM Stop " +
            "ORDER BY (ABS(latitude - :latitude) +  ABS(longitude - :longitude)) ASC " +
            "LIMIT :limit")
    Single<List<Stop>> findNearest(double latitude, double longitude, int limit);


    @Query("SELECT * FROM Stop ")
    Single<List<Stop>> findAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Stop> stops);
}
