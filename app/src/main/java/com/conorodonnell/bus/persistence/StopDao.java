package com.conorodonnell.bus.persistence;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

import io.reactivex.Single;

@Dao
public interface StopDao {

    @Query("SELECT COUNT(*) FROM Stop")
    Single<Integer> count();

    @Query("SELECT * FROM Stop WHERE id = :id LIMIT 1")
    Single<Stop> findById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Stop> stops);
}
