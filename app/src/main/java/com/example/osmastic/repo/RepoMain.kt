package com.example.osmastic.repo

import android.content.Context
import android.widget.Toast
import com.example.osmastic.PinLogical
import com.example.osmastic.PinUI
import com.example.osmastic.db.AppDatabase
import com.example.osmastic.db.Pin
import com.example.osmastic.ether.MeshtasticPortal
import org.osmdroid.util.GeoPoint

import com.example.osmastic.ether.PinMessage
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RepoPin(
    val fuckingContext: Context,
//    var onHandledPinCreationRequestCallback: (builtPinLogical: PinLogical) -> Unit, // todo: stop being stupid
    var onHandledPinCreationRequestCallback: ((PinLogical) -> Unit)? = null
) {
    private val database by lazy { AppDatabase.getDatabase(fuckingContext) }
    private val dao = database.pinDao()
    val portalToMesh = MeshtasticPortal(fuckingContext, this)
    private sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    init {
        portalToMesh.connect()
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
    private fun stripDefaults(builderProtobuf: PinMessage.Builder, incomingPinLogical: PinLogical): PinMessage.Builder {

        val defaultPin = PinLogical(
            pinLogicalId = 1,
//            editorHash = byteArrayOf(),
            editorHash = "myass",
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    0.0,
                    0.0
                ),
            )
        )

        val ipl = incomingPinLogical // conv
        if (ipl.lamportEpoch != defaultPin.lamportEpoch)
            builderProtobuf.setLamportEpoch(ipl.lamportEpoch)
        if (ipl.pinPhysProps.rotationByte != defaultPin.pinPhysProps.rotationByte)
            builderProtobuf.setRotationByte(ipl.pinPhysProps.rotationByte!!)
        if (ipl.pinPhysProps.iconUnicode != defaultPin.pinPhysProps.iconUnicode)
            builderProtobuf.setIconUnicode(ipl.pinPhysProps.iconUnicode)
        if (ipl.pinPhysProps.label != defaultPin.pinPhysProps.label)
            builderProtobuf.setLabel(ipl.pinPhysProps.label)
        if (ipl.pinPhysProps.isHiddenBeforeTTL != defaultPin.pinPhysProps.isHiddenBeforeTTL)
            builderProtobuf.setIsHiddenBeforeTtl(ipl.pinPhysProps.isHiddenBeforeTTL)
        if (ipl.pinPhysProps.hoursTTL != defaultPin.pinPhysProps.hoursTTL)
            builderProtobuf.setHoursTTL(ipl.pinPhysProps.hoursTTL)

        return builderProtobuf
    }
    private fun prepCreationProtobuf(incomingPinLogical: PinLogical): PinMessage {
        val pinMessBuilder = stripDefaults(PinMessage.newBuilder(), incomingPinLogical)
        pinMessBuilder.setPinLogicalId(incomingPinLogical.pinLogicalId)
//        pinMessBuilder.setEditorHash(ByteString.copyFrom(incomingPinLogical.editorHash))
        pinMessBuilder.setEditorHash(incomingPinLogical.editorHash) //TODO uncomment! after generation KSP PROTO
        pinMessBuilder.setLat(incomingPinLogical.pinPhysProps.geoPoint.latitude.toFloat())
        pinMessBuilder.setLon(incomingPinLogical.pinPhysProps.geoPoint.longitude.toFloat())

        return pinMessBuilder.build()
    }

    //TODO deserialize NO COORDINATES CASE YET!
    private fun buildLogicalFromMessage(pinMessage: PinMessage): PinLogical {

        //#1 introduce football teams:
        val defaultPin = PinLogical(
            pinLogicalId = 1,
//            editorHash = byteArrayOf(),
            editorHash = "myass",
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    0.0,
                    0.0
                ),
            )
        )

        //#2 poshla ebka
        val _pinLogicalId = pinMessage.pinLogicalId
        val _editorHash = pinMessage.editorHash
        val _lamportEpoch = if (pinMessage.hasLamportEpoch()) pinMessage.lamportEpoch else defaultPin.lamportEpoch
        val _hoursTTL = if (pinMessage.hasHoursTTL()) pinMessage.hoursTTL else defaultPin.pinPhysProps.hoursTTL

        val HOUR = 3600
        val MINUTE = 60 // todo UGLY DEBUG remove later
        val SECOND = 1  // todo UGLY DEBUG remove later
        val _expirationTimestamp = if (_hoursTTL == 0) 0L else System.currentTimeMillis() + (_hoursTTL * SECOND * 1000)

//        val _lat = if (pinProtobuf.hasLat()) pinProtobuf.lat else dao.getById(_pinLogicalId)?.latitude  // UGLY?
//        val _lon = if (pinProtobuf.hasLon()) pinProtobuf.lon else dao.getById(_pinLogicalId)?.longitude // UGLY?
        val _lat = if (pinMessage.hasLat()) pinMessage.lat else 0.0
        val _lon = if (pinMessage.hasLon()) pinMessage.lon else 0.0 //TODO HOUSTON WE GOT no good way to check if we already have coordinates.

        val _rotationByte = if (pinMessage.hasRotationByte()) pinMessage.rotationByte else defaultPin.pinPhysProps.rotationByte
        val _iconUnicode = if (pinMessage.hasIconUnicode()) pinMessage.iconUnicode else defaultPin.pinPhysProps.iconUnicode
        val _label = if (pinMessage.hasLabel()) pinMessage.label else defaultPin.pinPhysProps.label
        val _isHiddenBeforeTTL = if (pinMessage.hasIsHiddenBeforeTtl()) pinMessage.isHiddenBeforeTtl else defaultPin.pinPhysProps.isHiddenBeforeTTL

        //#3 construct and return
        return PinLogical(
            pinLogicalId = _pinLogicalId,
            editorHash = _editorHash,
//            editorHash = "myass",
            lamportEpoch = _lamportEpoch,
            expirationTimestamp = _expirationTimestamp,
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    _lat.toDouble(),
                    _lon.toDouble()
                ),
                rotationByte = _rotationByte,
                iconUnicode = _iconUnicode,
                label = _label,
                isHiddenBeforeTTL = _isHiddenBeforeTTL,
                hoursTTL = _hoursTTL
            )
        )

    }

// 🛟🛟🛟 PRIVATE HELPERS 🛟🛟🛟


// 🎊🎊🎊 INTERACTIVE PART 🎊🎊🎊
    suspend fun pushOnePinFurther(incomingPinLogical: PinLogical): Boolean {

        when (val result = validatePin(incomingPinLogical)) {
            is ValidationResult.Valid -> {
                // TODO: ОТПРАВКА ТУТА
                val logicalIdInternal = dao.insert(convertToEntity(incomingPinLogical))
                val meshMessageId = portalToMesh.serviceConnectionWrapper.sendToTheEther(prepCreationProtobuf(incomingPinLogical).toByteArray())
                Toast.makeText(fuckingContext, "SIDE EFFECT, logID: ${incomingPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
                return true
            }
            is ValidationResult.Invalid -> {
                // Log errors, emit rollback, etc
                Toast.makeText(fuckingContext, "ROLLBACK, logID: ${incomingPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()

                return false
            }
        }
    }
    suspend fun getAllPins(): Set<PinLogical> {
        return dao.getAll().map { fetchedEntity ->
            PinLogical(
                pinLogicalId = fetchedEntity.pinLogicalId,
                lamportEpoch = fetchedEntity.lamportEpoch,
                editorHash = fetchedEntity.editorHash,
                expirationTimestamp = fetchedEntity.expirationTimestamp,
                pinPhysProps = PinUI(
                    geoPoint = GeoPoint(fetchedEntity.latitude / 1e6, fetchedEntity.longitude / 1e6),
                    iconUnicode = fetchedEntity.iconUnicode,
                    label = fetchedEntity.label,
                    rotationByte = fetchedEntity.rotationByte,
                    isHiddenBeforeTTL = fetchedEntity.isHiddenBeforeTTL,
//                    hoursTTL = fetchedEntity.hour, // TODO: decide do i STORE hoursTTL or NOT?
                )
            )
        }.toSet()
    }
    suspend fun deleteBulkByLogicalIds(pinLogicalIds: Set<Int>): Int {
        return dao.deleteBulkByLogicalIds(pinLogicalIds)
    }
    suspend fun handleIncomingPinMessage(parsedRawPinMessage: PinMessage) {
        // #0 DECISION
        if (parsedRawPinMessage.hasLamportEpoch()) {
            // TODO implement update
            //  if (dao.pinExists(parsedRawPinMessage.pinLogicalId)) {
            //      <update!>
            //  } else {
            //      <drop! invalid garbage!>
            // }

        } else {
            // pure creation
            val builtPinLogical = buildLogicalFromMessage(parsedRawPinMessage)

            when (val result = validatePin(builtPinLogical)) {
                is ValidationResult.Valid -> {
                    val logicalIdInternal = dao.insert(convertToEntity(builtPinLogical))
//                    val meshMessageId = portalToMesh.serviceConnectionWrapper.sendToTheEther(prepCreationProtobuf(builtPinLogical).toByteArray())
                    onHandledPinCreationRequestCallback?.invoke(builtPinLogical)
                }
                is ValidationResult.Invalid -> { /* JUST DROP? */ }
            }

        }

//        Toast.makeText(fuckingContext, "RECEIVED PIN ID: ${parsedRawPinMessage.pinLogicalId}", Toast.LENGTH_SHORT ).show() //TODO: toast receuved debug toast

    }


// 🎊🎊🎊 INTERACTIVE PART 🎊🎊🎊


}
