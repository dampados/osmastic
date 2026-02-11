package com.example.osmastic.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pins")
data class PinEntity(
    @PrimaryKey(autoGenerate = true) val internalId: Long = 0,  // auto-increment, NEVER SENT
    val pinLogicalId: Int, // 1-3 bytes varint ENOUGH (no sense to generate 4-5 byte integers)
    val lamportEpoch: Int, // 1-4 bytes varint for infinite pins updates (cheap anyway)
    val editorHash: ByteArray, // 3 byte FIXED, not varint
    val latitude: Int,  // 32 bytes full, but shrinked to 24 for meshtastic
    val longitude: Int, // 32 bytes full, but shrinked to 24 for meshtastic
    val iconUnicode: Int, // 4 byte max
    val label: String? = null, // max 41 byte, first byte - LENGTH
    val isHiddenBeforeTTL: Boolean = false, // 1 byte
    val expirationTimestamp: Long = 0L,  // milliseconds full epoch (built from local epoch + 1 byte hours from message) 0 = no TTL

) {
    // we OBLIGED to override the equals fun - bc of the Byte Array type. but its worth it
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PinEntity

        if (pinLogicalId != other.pinLogicalId) return false
        if (lamportEpoch != other.lamportEpoch) return false
        if (!editorHash.contentEquals(other.editorHash)) return false
        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (iconUnicode != other.iconUnicode) return false
        if (isHiddenBeforeTTL != other.isHiddenBeforeTTL) return false
        if (label != other.label) return false
        return expirationTimestamp == other.expirationTimestamp
    }

    //this too - byte array
    override fun hashCode(): Int {
        var result = pinLogicalId
        result = 31 * result + lamportEpoch
        result = 31 * result + editorHash.contentHashCode()
        result = 31 * result + latitude
        result = 31 * result + longitude
        result = 31 * result + iconUnicode
        result = 31 * result + isHiddenBeforeTTL.hashCode()
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + expirationTimestamp.hashCode()
        return result
    }
}