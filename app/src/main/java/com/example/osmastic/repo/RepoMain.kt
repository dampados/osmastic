package com.example.osmastic.repo

import android.content.Context
import android.widget.Toast
import com.example.osmastic.PinLogical
import com.example.osmastic.PinUI
import com.example.osmastic.db.AppDatabase
import com.example.osmastic.db.Pin
import com.example.osmastic.ether.MeshtasticPortal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class RepoPin(
    val fuckingContext: Context,
//    private val dao: PinDao,
//    private val radio: MeshtasticRadio
) {
    private val database by lazy { AppDatabase.getDatabase(fuckingContext) }
    private val dao = database.pinDao()

    val portalToMesh = MeshtasticPortal(fuckingContext)

    private sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }


    init {
        portalToMesh.connect()

        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            val info = portalToMesh.serviceConnection.getMyNodeInfo()
            Toast.makeText(fuckingContext, "📡 $info", Toast.LENGTH_LONG).show()
            println("📡 $info")
        }
    }



// 🛟🛟🛟 PRIVATE HELPERS 🛟🛟🛟
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
            latitude = (incomingPinLogical.pinPhysProps.geoPoint.latitude * 1e6).toInt(), // multiply by a million to move floating point to the RIGHT
            longitude = (incomingPinLogical.pinPhysProps.geoPoint.longitude * 1e6).toInt(), // multiply by a million to move floating point to the RIGHT
            iconUnicode = incomingPinLogical.pinPhysProps.iconUnicode,
            label = incomingPinLogical.pinPhysProps.label,
            rotationByte = incomingPinLogical.pinPhysProps.rotationByte,
            isHiddenBeforeTTL = incomingPinLogical.pinPhysProps.isHiddenBeforeTTL,
            expirationTimestamp = incomingPinLogical.expirationTimestamp,
        )
    }

// 🛟🛟🛟 PRIVATE HELPERS 🛟🛟🛟


// 🎊🎊🎊 INTERACTIVE PART 🎊🎊🎊
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
    suspend fun getAllPins(): List<PinLogical> {
        return dao.getAll().map { entity ->
            PinLogical(
                pinLogicalId = entity.pinLogicalId,
                lamportEpoch = entity.lamportEpoch,
                editorHash = entity.editorHash,
                expirationTimestamp = entity.expirationTimestamp,
                pinPhysProps = PinUI(
                    geoPoint = GeoPoint(entity.latitude / 1e6, entity.longitude / 1e6),
                    iconUnicode = entity.iconUnicode,
                    label = entity.label,
                    rotationByte = entity.rotationByte,
                    isHiddenBeforeTTL = entity.isHiddenBeforeTTL,
//                    hoursTTL = 6 // You need to store this in DB or derive from expiration
                )
            )
        }
    }
    suspend fun deleteBulkByLogicalIds(pinLogicalIds: Set<Int>): Int {
        return dao.deleteBulkByLogicalIds(pinLogicalIds)
    }

// 🎊🎊🎊 INTERACTIVE PART 🎊🎊🎊


}
