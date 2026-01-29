package com.example.osmastic.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pin")
data class Pin(
    @PrimaryKey
    val id: Int,
    val lamportEpoch: Int,
    val editorHash: ByteArray,
    val latitude: Int,
    val longitude: Int,
    val iconType: Int,
    val isDeleted: Boolean = false,
    val rotationDegrees: Int? = null,
    val lengthMeters: Int? = null,
    val label: String? = null,
    val timeToLiveSeconds: Int? = null
) {
    // we OBLIGED to override the equals fun - bc of the Byte Array type. but its worth it
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Pin

        if (id != other.id) return false
        if (lamportEpoch != other.lamportEpoch) return false
        if (!editorHash.contentEquals(other.editorHash)) return false
        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (iconType != other.iconType) return false
        if (isDeleted != other.isDeleted) return false
        if (rotationDegrees != other.rotationDegrees) return false
        if (lengthMeters != other.lengthMeters) return false
        if (label != other.label) return false
        return timeToLiveSeconds == other.timeToLiveSeconds
    }

    //this too - byte array
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + lamportEpoch
        result = 31 * result + editorHash.contentHashCode()
        result = 31 * result + latitude
        result = 31 * result + longitude
        result = 31 * result + iconType
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + (rotationDegrees ?: 0)
        result = 31 * result + (lengthMeters ?: 0)
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + (timeToLiveSeconds ?: 0)
        return result
    }
}