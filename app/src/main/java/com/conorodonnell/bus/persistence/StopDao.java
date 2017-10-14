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
            "WHERE (latitude BETWEEN MIN(:top, :bottom) AND MAX(:top, :bottom)) " +
            "AND (longitude BETWEEN MIN(:left, :right) AND MAX(:left, :right))")
    Single<List<Stop>> findInArea(double top, double bottom, double left, double right);

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("SELECT *, (ABS(latitude - :latitude) +  ABS(longitude - :longitude)) as distance FROM Stop " +
            "ORDER BY distance ASC LIMIT 200")
    Single<List<Stop>> findNearest(double latitude, double longitude);

    @Query("SELECT * FROM Stop ")
    Single<List<Stop>> findAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Stop> stops);
}
