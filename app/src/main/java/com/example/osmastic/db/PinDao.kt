package com.example.osmastic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PinDao {

    // CREATE ONE: Insert a single pin (No REPLACE - keep all versions)
    @Insert
    suspend fun insertVersion(pin: Pin): Long

    // GET ALL: Get all pins
    @Query("""
        SELECT pin.* FROM pin
        JOIN to_be_rendered_pins ON pin.internalId = to_be_rendered_pins.pinVersionInternalID
    """)
    suspend fun getAllActivePins(): List<Pin>
    
    // GET ONE: Get a pin by its ID
    @Query("""
        SELECT pin.* FROM pin
        JOIN to_be_rendered_pins ON pin.internalId = to_be_rendered_pins.pinVersionInternalID
        WHERE to_be_rendered_pins.pinLogicalId = :logicalId
    """)
    suspend fun getOneActivePinByLogId(logicalId: Int): Pin?

    // GET MANY: Get all versions of an active and not stale pin
    @Query("SELECT * FROM pin WHERE pinLogicalId = :logicalId ORDER BY lamportEpoch DESC")
    suspend fun getAllVersionsByLogId(logicalId: Int): List<Pin>

    // DROP BULK BY LOGICAL ID ( drops relations in cascade! )
    @Query("DELETE FROM pin WHERE pinLogicalId IN (:pinLogicalIds)")
    suspend fun deleteBulkByLogIds(pinLogicalIds: Set<Int>): Int

    // ----------------------------------------------------------------------------------- //

    // DEPRECATED CREATE ONE: Insert a single pin (replace on conflict)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pin: Pin)
    
    // DEPRECATED GET ALL: Get all pins
    @Query("SELECT * FROM pin")
    suspend fun getAll(): List<Pin>
    
    // DEPRECATED COLLISION SEEKING
    @Query("SELECT EXISTS(SELECT 1 FROM pin WHERE pinLogicalId = :id)")
    suspend fun pinExists(id: Int): Boolean

    // DEPRECATED UPDATE ONE: Update an existing pin
    @Update
    suspend fun update(pin: Pin)

    // DEPRECATED GET ONE: Get a pin by its ID
    @Query("SELECT * FROM pin WHERE pinLogicalId = :id LIMIT 1")
    suspend fun getById(id: Int): Pin?
}


@Dao
interface ToBeRenderedPinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun choosePinForRendering(winner: ToBeRenderedPin)

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
