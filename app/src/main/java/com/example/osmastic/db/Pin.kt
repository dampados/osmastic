package com.example.osmastic.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

//EXPERIMENTAL 
//     indices = [Index(value = ["pinLogicalId", "internalId"], unique = true)]

@Entity(tableName = "pin")
data class Pin(
    @PrimaryKey(autoGenerate = true) val internalId: Long = 0,  // auto-increment, NEVER SENT
    val pinLogicalId: Int, // 1-3 bytes varint ENOUGH (no sense to generate 4-5 byte integers)
    val lamportEpoch: Int, // 1-4 bytes varint for infinite pins updates (cheap anyway)
    val editorHash: String, // 1+5 bytes, 5 byte payload
    val latitude: Int,  // 32 bytes full, but shrunk to 24 for meshtastic
    val longitude: Int, // 32 bytes full, but shrunk to 24 for meshtastic
    val iconUnicode: String = "📍", // 4 byte max
    val label: String = "", // max 41 byte, first byte - LENGTH
    val rotationByte: Int? = null, // lol 1 bte rotation stuck to the map body
    val isHiddenBeforeTTL: Boolean = false, // 1 byte
    val expirationTimestamp: Long = 0L,  // milliseconds full epoch (built from local epoch + 1 byte hours from message) 0 = no TTL

)

@Entity(
    tableName = "to_be_rendered_pins",
    foreignKeys = [
        ForeignKey(
            entity = Pin::class,
            parentColumns = ["internalId"],
            childColumns = ["pinVersionInternalID"],
            onDelete = ForeignKey.CASCADE
                // on exact WINNER drop - cascade deletes this winner record too!
        )
    ]
)
data class ToBeRenderedPin(
    @PrimaryKey val pinLogicalId: Int,
    val pinVersionInternalID: Long
)

// EXPERIMENTAL 

//@Entity(
//    tableName = "to_be_rendered_pins",
//    foreignKeys = [
//        ForeignKey(
//            entity = Pin::class,
//            parentColumns = ["pinLogicalId", "internalId"],
//            childColumns = ["pinLogicalId", "pinVersionInternalID"],
//            onDelete = ForeignKey.CASCADE  // удаление Pin → удаляет победителя
//        )
//    ]
//)
//data class ToBeRenderedPin(
//    @PrimaryKey val pinLogicalId: Int,
//    val pinVersionInternalID: Long
//)
