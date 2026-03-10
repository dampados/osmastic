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

class RepoPin(
    val fuckingContext: Context,
//    var onHandledPinCreationRequestCallback: (builtPinLogical: PinLogical) -> Unit, // todo: stop being stupid
    var onHandledPinCreationRequestCallback: ((PinLogical) -> Unit)? = null,
    var onHandledPinUpdateRequestCallback: ((PinLogical) -> Unit)? = null,
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
        pinMessBuilder.setEditorHash(incomingPinLogical.editorHash)
        pinMessBuilder.setLat(incomingPinLogical.pinPhysProps.geoPoint.latitude.toFloat())
        pinMessBuilder.setLon(incomingPinLogical.pinPhysProps.geoPoint.longitude.toFloat())

        return pinMessBuilder.build()
    }
    private fun stripUnchanged(builderProtobuf: PinMessage.Builder, oldPinLogical: PinLogical, newPinLogical: PinLogical): PinMessage.Builder {

        if (newPinLogical.pinPhysProps.geoPoint != oldPinLogical.pinPhysProps.geoPoint) {
            builderProtobuf.setLat(newPinLogical.pinPhysProps.geoPoint.latitude.toFloat())
            builderProtobuf.setLon(newPinLogical.pinPhysProps.geoPoint.longitude.toFloat())
        }

        if (newPinLogical.pinPhysProps.rotationByte != oldPinLogical.pinPhysProps.rotationByte)
            builderProtobuf.setRotationByte(newPinLogical.pinPhysProps.rotationByte!!)
        if (newPinLogical.pinPhysProps.iconUnicode != oldPinLogical.pinPhysProps.iconUnicode)
            builderProtobuf.setIconUnicode(newPinLogical.pinPhysProps.iconUnicode)
        if (newPinLogical.pinPhysProps.label != oldPinLogical.pinPhysProps.label)
            builderProtobuf.setLabel(newPinLogical.pinPhysProps.label)
        if (newPinLogical.pinPhysProps.isHiddenBeforeTTL != oldPinLogical.pinPhysProps.isHiddenBeforeTTL)
            builderProtobuf.setIsHiddenBeforeTtl(newPinLogical.pinPhysProps.isHiddenBeforeTTL)


        return builderProtobuf
    }
    private fun prepUpdateProtobuf(oldPinLogical: PinLogical, newPinLogical: PinLogical): PinMessage {
        val pinDeltaBuilder = stripUnchanged(PinMessage.newBuilder(), oldPinLogical, newPinLogical)
        pinDeltaBuilder.setPinLogicalId(newPinLogical.pinLogicalId)
        pinDeltaBuilder.setEditorHash(newPinLogical.editorHash)
        pinDeltaBuilder.setLamportEpoch(newPinLogical.lamportEpoch)

        return pinDeltaBuilder.build()
    }
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
        val _lon = if (pinMessage.hasLon()) pinMessage.lon else 0.0

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
                val pinLogicalId = dao.insert(convertToEntity(incomingPinLogical))
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
    suspend fun pushOneDeltaFurther(incomOldPinLogical: PinLogical, incomUpdPinLogical: PinLogical): Boolean {

        when (val result = validatePin(incomUpdPinLogical)) {
            is ValidationResult.Valid -> {
                // <PUSH TO ROOM>
                // #1 Get existing entity with its internalId
                val existingEntity = dao.getById(incomUpdPinLogical.pinLogicalId)
                    ?: return false  // SIMPLE ROLLBACK IF STATE SYNC SCREWED!
                // #2 Convert updated pin to entity
                val updatedEntity = convertToEntity(incomUpdPinLogical)
                // #3 Preserve the primary key
                val entityToUpdate = updatedEntity.copy(
                    internalId = existingEntity.internalId
                )
                // #4 Update using Room's @Update
                dao.update(entityToUpdate)

                // <construct DELTA -> PUSH to radio!>
                val meshMessageId = portalToMesh.serviceConnectionWrapper.sendToTheEther(prepUpdateProtobuf(incomOldPinLogical,incomUpdPinLogical).toByteArray())
                Toast.makeText(fuckingContext, "SIDE EFFECT UPDATE!", Toast.LENGTH_SHORT).show()
                return true
            }
            is ValidationResult.Invalid -> {
                // Log errors, emit rollback, etc
                Toast.makeText(fuckingContext, "ROLLBACK, logID: ${incomUpdPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
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

              if (dao.pinExists(parsedRawPinMessage.pinLogicalId)) {

                  val storedPinEntity = dao.getById(parsedRawPinMessage.pinLogicalId)!! // !! bc i got checks OUTSIDE, at this point im sure pin exists!
                  val newPinLogicalHalfBuilt = buildLogicalFromMessage(parsedRawPinMessage)
                  val newPinLogical = newPinLogicalHalfBuilt.copy(
                      pinPhysProps = newPinLogicalHalfBuilt.pinPhysProps.copy(
                          geoPoint = GeoPoint(
                              storedPinEntity.latitude.toDouble() / 1e6,
                              storedPinEntity.longitude.toDouble() / 1e6, //TODO float/double PROBLEMS, remove after testing
                          )
                      ),
                      expirationTimestamp = storedPinEntity.expirationTimestamp // TODO TTL timestampt after updates PROBLEM solved? test!
                  )

                  if (newPinLogical.lamportEpoch > storedPinEntity.lamportEpoch) { // TODO бля, второй час ночи, попытка починить ХОЛОД
                      // #1 Get existing entity with its internalId
                      val existingEntity = dao.getById(newPinLogical.pinLogicalId)
                            // SIMPLE ROLLBACK IF STATE SYNC SCREWED!
                      // #2 Convert updated pin to entity
                      val updatedEntity = convertToEntity(newPinLogical)
                      // #3 Preserve the primary key
                      val entityToUpdate = updatedEntity.copy(
                          internalId = existingEntity!!.internalId
                      )
                      // #4 Update using Room's @Update
                      dao.update(entityToUpdate)

                      onHandledPinUpdateRequestCallback?.invoke(newPinLogical)


                  } else if (newPinLogical.lamportEpoch < storedPinEntity.lamportEpoch) {
                      // drop! too logically old //TODO implement HISTORY (so no information gets dropped ever) 3
                  } else {
                      // CONFLICT! // TODO determenistic decision mkaing based on meshtastic channel PSK salt + new.editorHash vs old.editorHash
                  }

              } else {
//                  <drop! for now its invalid garbage for us!> // TODO implement HISTORY (so no information gets dropped ever) 1
                  return
             }

        } else {
            // pure creation CHECK:
            if (parsedRawPinMessage.hasLat() && parsedRawPinMessage.hasLon()) {

                val builtPinLogical = buildLogicalFromMessage(parsedRawPinMessage)
                when (val result = validatePin(builtPinLogical)) {
                    is ValidationResult.Valid -> {
                        val logicalIdInternal = dao.insert(convertToEntity(builtPinLogical))
                        onHandledPinCreationRequestCallback?.invoke(builtPinLogical)
                    }
                    is ValidationResult.Invalid -> { /* JUST DROP? */ }
                }
            } else {
                //                  <drop! for now its invalid garbage for us!> // TODO implement HISTORY (so no information gets dropped ever) 2
                return
            }


        }

//        Toast.makeText(fuckingContext, "RECEIVED PIN ID: ${parsedRawPinMessage.pinLogicalId}", Toast.LENGTH_SHORT ).show() //TODO: toast receuved debug toast

    }


// 🎊🎊🎊 INTERACTIVE PART 🎊🎊🎊


}
