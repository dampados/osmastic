package com.example.osmastic.repo

import android.content.Context
import android.widget.Toast
import com.example.osmastic.PinLogical
import com.example.osmastic.db.AppDatabase
import com.example.osmastic.db.Pin

class RepoPin(
    val fuckingContext: Context,
//    private val dao: PinDao,
//    private val radio: MeshtasticRadio
) {

        private val database by lazy { AppDatabase.getDatabase(fuckingContext) }
        private val dao = database.pinDao()

    private sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    private fun validatePin(incomingPinLogical: PinLogical): ValidationResult {
        val errors = mutableListOf<String>()

/*ICON*/if (incomingPinLogical.pinPhysProps.iconUnicode.codePointCount(0, incomingPinLogical.pinPhysProps.iconUnicode.length) != 1) {
            errors.add("Icon must be a single character")
        }
/*LABEL*/incomingPinLogical.pinPhysProps.label?.let {
            if (it.length > 20) {
                errors.add("Label must be ≤ 20 characters")
            }
        }
/*TTL*/ val ttlHours = incomingPinLogical.pinPhysProps.hoursTTL
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
                Toast.makeText(fuckingContext, "SAVED, logID: ${incomingPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
                return true
            }
            is ValidationResult.Invalid -> {
                // Log errors, emit rollback, etc
                Toast.makeText(fuckingContext, "ROLLBACK, logID: ${incomingPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()

                return false
            }
        }

    }




}
