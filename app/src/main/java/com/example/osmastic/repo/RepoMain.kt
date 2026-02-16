package com.example.osmastic.repo

import android.content.Context
import com.example.osmastic.PinLogical
import com.example.osmastic.db.AppDatabase
import com.example.osmastic.db.PinDao
import com.example.osmastic.db.Pin

class RepoPin(
    fuckingContext: Context,
//    private val dao: PinDao,
//    private val radio: MeshtasticRadio
) {

        private val database by lazy { AppDatabase.getDatabase(fuckingContext) }
        private val dao = database.pinDao()

    private sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    private fun validatePin(incomginPinLogical: PinLogical): ValidationResult {
        val errors = mutableListOf<String>()

/*ICON*/if (incomginPinLogical.pinPhysProps.iconUnicode.codePointCount(0, incomginPinLogical.pinPhysProps.iconUnicode.length) != 1) {
            errors.add("Icon must be a single character")
        }
/*LABEL*/incomginPinLogical.pinPhysProps.label?.let {
            if (it.length > 20) {
                errors.add("Label must be ≤ 20 characters")
            }
        }
/*TTL*/ val ttlHours = incomginPinLogical.pinPhysProps.hoursTTL
        if (ttlHours !in 0..255) {
            errors.add("TTL must be 0-255 hours")
        }
        return if (errors.isEmpty())
            ValidationResult.Valid
        else
            ValidationResult.Invalid(errors)
    }

    private fun convertToEntity(incomingPinLogical: PinLogical): Pin {
        return Pin(
            pinLogicalId = incomingPinLogical.pinLogicalId,
            lamportEpoch = incomingPinLogical.lamportEpoch,
            editorHash = incomingPinLogical.editorHash,
            latitude = incomingPinLogical.pinPhysProps.geoPoint.latitude.toInt(),
            longitude = incomingPinLogical.pinPhysProps.geoPoint.longitude.toInt(),
            iconUnicode = incomingPinLogical.pinPhysProps.iconUnicode,
            label = incomingPinLogical.pinPhysProps.label,
            rotationByte = incomingPinLogical.pinPhysProps.rotationByte,
            isHiddenBeforeTTL = incomingPinLogical.pinPhysProps.isHiddenBeforeTTL,
            expirationTimestamp = incomingPinLogical.expirationTimestamp,
        )
    }


    suspend fun pushOnePinFurther(incomingPinLogical: PinLogical): Boolean {

        when (val result = validatePin(incomingPinLogical)) {
            is ValidationResult.Valid -> {
                val idInternal = dao.insert(convertToEntity(incomingPinLogical))
                // <radio send here?>
                return true
            }
            is ValidationResult.Invalid -> {
                // Log errors, emit rollback, etc
                return false
            }
        }

    }




}
