package com.example.osmastic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PinDao {

    // CREATE ONE: Insert a single pin (replace on conflict)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pin: Pin)

    // GET ALL: Get all pins
    @Query("SELECT * FROM pin")
    suspend fun getAll(): List<Pin>

    // GET ONE: Get a pin by its ID
    @Query("SELECT * FROM pin WHERE pinLogicalId = :id LIMIT 1")
    suspend fun getById(id: Int): Pin?

    // UPDATE ONE: Update an existing pin
    @Update
    suspend fun update(pin: Pin)

    // BULK BY LOGICAL ID
    @Query("DELETE FROM pin WHERE pinLogicalId IN (:pinLogicalIds)")
    suspend fun deleteBulkByLogicalIds(pinLogicalIds: Set<Int>): Int

    // COLLISION SEEKING
    @Query("SELECT EXISTS(SELECT 1 FROM pin WHERE pinLogicalId = :id)")
    suspend fun pinExists(id: Int): Boolean

}

// !!!OBSOLETE!!! think about it...

//    // CREATE ONE: Insert a single pin (replace on conflict)
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insert(pin: Pin)

// CREATE ONE (with return value): Returns the row ID as Long
//@Insert(onConflict = OnConflictStrategy.REPLACE)
//suspend fun insertAndReturnId(pin: Pin): Long

//    // GET ALL (ordered by ID, optional but useful)
//    @Query("SELECT * FROM pin ORDER BY id ASC")
//    suspend fun getAllOrdered(): List<Pin>

//    // DELETE ONE by ID: Alternative way using query
//    @Query("DELETE FROM pin WHERE id = :id")
//    suspend fun deleteById(id: Int)

//// DELETE ONE: Delete a specific pin
//@Delete
//suspend fun delete(pin: Pin)
