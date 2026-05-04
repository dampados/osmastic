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

import java.security.MessageDigest

import android.util.Log

import com.example.osmastic.db.ToBeRenderedPin

class RepoPin(
    val fuckingContext: Context,
//    var onHandledPinCreationRequestCallback: (builtPinLogical: PinLogical) -> Unit, // todo: stop being stupid
    var onHandledPinCreationRequestCallback: ((PinLogical) -> Unit)? = null,
    var onHandledPinUpdateRequestCallback: ((PinLogical) -> Unit)? = null,
) {
    private val database by lazy { AppDatabase.getDatabase(fuckingContext) }
    private val pinDao = database.pinDao()
    private val winnerDao = database.winnerDao()
    val portalToMesh = MeshtasticPortal(fuckingContext, this)
    private sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    init {
        portalToMesh.connect()
    }


// 🛟🛟🛟 PRIVATE HELPERS 🛟🛟🛟
    private fun validatePin(pinLogical: PinLogical): ValidationResult {
        val errors = mutableListOf<String>()
///*ICON*/if (incomingPinLogical.pinPhysProps.iconUnicode.codePointCount(0, incomingPinLogical.pinPhysProps.iconUnicode.length) != 1) {
//            errors.add("Icon must be a single character")
//        }
/*ICON*/ val icon = pinLogical.pinPhysProps.iconUnicode
//        val hasAsciiLetterOrDigit = icon.any { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
        val hasAsciiLetterOrDigit = icon.any { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in 'А'..'Я' || it in 'а'..'я' || it == 'ё' || it == 'Ё' }
        if (hasAsciiLetterOrDigit) {
            errors.add("Use emojis or symbols only (no letters or numbers)")
        }
        if (icon.length > 8) {
            errors.add("Icon too long (max 8 chars)")
        }
/*LABEL*/pinLogical.pinPhysProps.label?.let {
            if (it.length > 20) {
                errors.add("Label must be ≤ 20 characters")
            }
        }
/*TTL*/ val ttlHours = pinLogical.pinPhysProps.minutesTTL
        if (ttlHours !in 0..16383) {
            errors.add("TTL must be 0-16383 minutes")
        }
        return if (errors.isEmpty())
            ValidationResult.Valid
        else
            ValidationResult.Invalid(errors)
    }
    private fun convertToEntity(pinLogical: PinLogical): Pin {
        return Pin(
            pinLogicalId = pinLogical.pinLogicalId,
            lamportEpoch = pinLogical.lamportEpoch,
            editorHash = pinLogical.editorMark,
            latitude = (pinLogical.pinPhysProps.geoPoint.latitude * 1e6).toInt(), // multiply by a million to move floating point to the RIGHT
            longitude = (pinLogical.pinPhysProps.geoPoint.longitude * 1e6).toInt(), // multiply by a million to move floating point to the RIGHT
            iconUnicode = pinLogical.pinPhysProps.iconUnicode,
            label = pinLogical.pinPhysProps.label,
            rotationByte = pinLogical.pinPhysProps.rotationByte,
            isHiddenBeforeTTL = pinLogical.pinPhysProps.isHiddenBeforeTTL,
            expirationTimestamp = pinLogical.expirationTimestamp,
        )
    }
    //->
    private fun stripDefaults(builderProtobuf: PinMessage.Builder, outgoingPinLogical: PinLogical): PinMessage.Builder {

        val defaultPin = PinLogical(
            pinLogicalId = 1,
            editorMark = "dflt",
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    0.0,
                    0.0
                ),
            )
        )

        val opl = outgoingPinLogical // conv
        if (opl.lamportEpoch != defaultPin.lamportEpoch)
            builderProtobuf.setLamportEpoch(opl.lamportEpoch)
        if (opl.pinPhysProps.rotationByte != defaultPin.pinPhysProps.rotationByte)
            builderProtobuf.setRotationByte(opl.pinPhysProps.rotationByte!!)
        if (opl.pinPhysProps.iconUnicode != defaultPin.pinPhysProps.iconUnicode)
            builderProtobuf.setIconUnicode(opl.pinPhysProps.iconUnicode)
        if (opl.pinPhysProps.label != defaultPin.pinPhysProps.label)
            builderProtobuf.setLabel(opl.pinPhysProps.label)
        if (opl.pinPhysProps.isHiddenBeforeTTL != defaultPin.pinPhysProps.isHiddenBeforeTTL)
            builderProtobuf.setIsHiddenBeforeTtl(opl.pinPhysProps.isHiddenBeforeTTL)
        if (opl.pinPhysProps.minutesTTL != defaultPin.pinPhysProps.minutesTTL)
            builderProtobuf.setHoursTTL(opl.pinPhysProps.minutesTTL)

        return builderProtobuf
    }
    private fun prepCreationProtobuf(outgoingPinLogical: PinLogical): PinMessage {
        val pinMessBuilder = stripDefaults(PinMessage.newBuilder(), outgoingPinLogical)

        pinMessBuilder.setPinLogicalId(outgoingPinLogical.pinLogicalId)
        pinMessBuilder.setEditorHash(outgoingPinLogical.editorMark)
        pinMessBuilder.setLat(outgoingPinLogical.pinPhysProps.geoPoint.latitude.toFloat())
        pinMessBuilder.setLon(outgoingPinLogical.pinPhysProps.geoPoint.longitude.toFloat())
//            if (outgoingPinLogical.pinPhysProps.isHiddenBeforeTTL)
//                pinMessBuilder.setIsHiddenBeforeTtl(true)

        return pinMessBuilder.build()
    }
    private fun stripUnchanged(builderProtobuf: PinMessage.Builder, oldPinLogical: PinLogical, newPinLogical: PinLogical): PinMessage.Builder {

        val defaultPin = PinLogical(
            pinLogicalId = 1,
            editorMark = "dflt",
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    0.0,
                    0.0
                ),
            )
        )

//        if (newPinLogical.pinPhysProps.geoPoint != oldPinLogical.pinPhysProps.geoPoint) {
        builderProtobuf.setLat(newPinLogical.pinPhysProps.geoPoint.latitude.toFloat())
        builderProtobuf.setLon(newPinLogical.pinPhysProps.geoPoint.longitude.toFloat())
//        }

        // optional for convergence fields! we can omit those
        if (newPinLogical.pinPhysProps.rotationByte != oldPinLogical.pinPhysProps.rotationByte)
            builderProtobuf.setRotationByte(newPinLogical.pinPhysProps.rotationByte!!)
        if (newPinLogical.pinPhysProps.iconUnicode != oldPinLogical.pinPhysProps.iconUnicode)
            builderProtobuf.setIconUnicode(newPinLogical.pinPhysProps.iconUnicode)
        if (newPinLogical.pinPhysProps.label != oldPinLogical.pinPhysProps.label)
            builderProtobuf.setLabel(newPinLogical.pinPhysProps.label)

        // TTL freaking 5D chess! omit only if default. but ALWAYS LOGICALLY PRESENT
        if (newPinLogical.pinPhysProps.minutesTTL != defaultPin.pinPhysProps.minutesTTL)
            builderProtobuf.setHoursTTL(newPinLogical.pinPhysProps.minutesTTL)

        // is hidden optional field: if omitted -> pin NOT hidden. ALWAYS LOGICALLY PRESENT.
        if (newPinLogical.pinPhysProps.isHiddenBeforeTTL != defaultPin.pinPhysProps.isHiddenBeforeTTL)
            builderProtobuf.setIsHiddenBeforeTtl(newPinLogical.pinPhysProps.isHiddenBeforeTTL)

        return builderProtobuf
    }
    private fun prepUpdateProtobuf(oldPinLogical: PinLogical, newPinLogical: PinLogical): PinMessage {
        val pinDeltaBuilder = stripUnchanged(PinMessage.newBuilder(), oldPinLogical, newPinLogical)
        pinDeltaBuilder.setPinLogicalId(newPinLogical.pinLogicalId)
        pinDeltaBuilder.setEditorHash(newPinLogical.editorMark)
        pinDeltaBuilder.setLamportEpoch(newPinLogical.lamportEpoch)

        return pinDeltaBuilder.build()
    }
    //->

    //<-
    private fun buildBasedOnDefaultsFromMessage(incomingPinMessage: PinMessage): PinLogical {

        //#1 introduce football teams:
        val defaultPin = PinLogical(
            pinLogicalId = 1,
            editorMark = "dflt",
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    0.0,
                    0.0
                ),
            )
        )

        //#2 poshla ebka
        val _pinLogicalId = incomingPinMessage.pinLogicalId
        val _editorHash = incomingPinMessage.editorHash
        val _lamportEpoch = if (incomingPinMessage.hasLamportEpoch()) incomingPinMessage.lamportEpoch else defaultPin.lamportEpoch
        val _minutesTTL = if (incomingPinMessage.hasHoursTTL()) incomingPinMessage.hoursTTL else defaultPin.pinPhysProps.minutesTTL

        // here _minutesTTL must be already either 0, or 6 (1 on debug stage), or custom.
        val SECOND_IN_MIL = 1000
        val MINUTE_IN_SEC = 60
        val _expirationTimestamp = if (_minutesTTL == 0) 0L else System.currentTimeMillis() + (_minutesTTL * SECOND_IN_MIL * MINUTE_IN_SEC)

//        val _lat = if (pinProtobuf.hasLat()) pinProtobuf.lat else dao.getById(_pinLogicalId)?.latitude  // UGLY?
//        val _lon = if (pinProtobuf.hasLon()) pinProtobuf.lon else dao.getById(_pinLogicalId)?.longitude // UGLY?
//        val _lat = if (incomingPinMessage.hasLat()) incomingPinMessage.lat else 0.0
//        val _lon = if (incomingPinMessage.hasLon()) incomingPinMessage.lon else 0.0 // AHAHHA logic ver 2.0 incoming!
        val _lat = incomingPinMessage.lat // now ALWAYS INCLUDED!
        val _lon = incomingPinMessage.lon // now ALWAYS INCLUDED!\

        // stil optional, 3 fields only!
        val _rotationByte = if (incomingPinMessage.hasRotationByte()) incomingPinMessage.rotationByte else defaultPin.pinPhysProps.rotationByte
        val _iconUnicode = if (incomingPinMessage.hasIconUnicode()) incomingPinMessage.iconUnicode else defaultPin.pinPhysProps.iconUnicode
        val _label = if (incomingPinMessage.hasLabel()) incomingPinMessage.label else defaultPin.pinPhysProps.label

        // unchanged, BUT NOW ITS ALWAYS LOGICALLY PRESENT (logic ver 2.00)
        val _isHiddenBeforeTTL = if (incomingPinMessage.hasIsHiddenBeforeTtl()) incomingPinMessage.isHiddenBeforeTtl else defaultPin.pinPhysProps.isHiddenBeforeTTL

        //#3 construct and return
        return PinLogical(
            pinLogicalId = _pinLogicalId,
            editorMark = _editorHash,
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
                minutesTTL = _minutesTTL
            )
        )
    }
    private fun buildBasedOnOldPinFromMessage(foundStoredPin: Pin, incomingPinMessage: PinMessage): PinLogical {

        //#1 introduce football teams:
        val defaultPin = PinLogical(
            pinLogicalId = 1,
            editorMark = "dflt",
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    0.0,
                    0.0
                ),
            )
        )

        //#2 poshla ebka
        val _pinLogicalId = incomingPinMessage.pinLogicalId
        val _editorHash = incomingPinMessage.editorHash

//        val _lamportEpoch = if (incomingPinMessage.hasLamportEpoch()) incomingPinMessage.lamportEpoch else foundStoredPin.lamportEpoch
        // lamport now ALWAYS PRESENT logically, when omitted == default which is 1 (creation)
        val _lamportEpoch = if (incomingPinMessage.hasLamportEpoch()) incomingPinMessage.lamportEpoch else defaultPin.lamportEpoch

        val _expirationTimestamp = foundStoredPin.expirationTimestamp

//        val _lat = if (incomingPinMessage.hasLat()) incomingPinMessage.lat else foundStoredPin.latitude
//        val _lon = if (incomingPinMessage.hasLon()) incomingPinMessage.lon else foundStoredPin.longitude
        val _lat = incomingPinMessage.lat.toDouble()
        val _lon = incomingPinMessage.lon.toDouble() // logic ver 2.0

        val _rotationByte = if (incomingPinMessage.hasRotationByte()) incomingPinMessage.rotationByte else foundStoredPin.rotationByte
        val _iconUnicode = if (incomingPinMessage.hasIconUnicode()) incomingPinMessage.iconUnicode else foundStoredPin.iconUnicode
        val _label = if (incomingPinMessage.hasLabel()) incomingPinMessage.label else foundStoredPin.label

//        val _isHiddenBeforeTTL = if (incomingPinMessage.hasIsHiddenBeforeTtl()) incomingPinMessage.isHiddenBeforeTtl else foundStoredPin.isHiddenBeforeTTL
        val _isHiddenBeforeTTL = if (incomingPinMessage.hasIsHiddenBeforeTtl()) incomingPinMessage.isHiddenBeforeTtl else defaultPin.pinPhysProps.isHiddenBeforeTTL

        //#3 construct and return
        return PinLogical(
            pinLogicalId = _pinLogicalId,
            editorMark = _editorHash,
            lamportEpoch = _lamportEpoch,
            expirationTimestamp = _expirationTimestamp,
            pinPhysProps = PinUI(
                geoPoint = GeoPoint(
                    _lat,
                    _lon,
                ),
                rotationByte = _rotationByte,
                iconUnicode = _iconUnicode,
                label = _label,
                isHiddenBeforeTTL = _isHiddenBeforeTTL,
            )
        )
    }
    //<-
    private fun tieBreakConflict(incoming: PinLogical, stored: Pin): Boolean {

        val chPSK = portalToMesh.serviceConnectionWrapper.getPrimaryChannelPsk()
        val md5er = MessageDigest.getInstance("MD5")
        val newHash = md5er.digest("$chPSK$incoming.editorHash".toByteArray())
        val oldHash = md5er.digest("$chPSK$stored.editorHash".toByteArray())
        val newInt = newHash.take(2).joinToString("") { "%02x".format(it) }.toInt(16)
        val oldInt = oldHash.take(2).joinToString("") { "%02x".format(it) }.toInt(16)

        return newInt < oldInt
    }

// 🛟🛟🛟 PRIVATE HELPERS 🛟🛟🛟


// 🎊🎊🎊 INTERACTIVE PART 🎊🎊🎊
    suspend fun pushOnePinFurther(outgoingPinLogical: PinLogical): Boolean {

        when (validatePin(outgoingPinLogical)) {
            is ValidationResult.Valid -> {
//                val pinLogicalId = pinDao.insert(convertToEntity(outgoingPinLogical))
                val versionInternalID = pinDao.insertVersion(convertToEntity(outgoingPinLogical))
                winnerDao.choosePinForRendering(ToBeRenderedPin(
                    pinLogicalId = outgoingPinLogical.pinLogicalId,
                    pinVersionInternalID = versionInternalID)
                )

                portalToMesh.serviceConnectionWrapper.sendToTheEther(prepCreationProtobuf(outgoingPinLogical).toByteArray())
                Toast.makeText(fuckingContext, "SIDE EFFECT, logID: ${outgoingPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
                return true
            }
            is ValidationResult.Invalid -> {
                // Log errors, emit rollback, etc
                Toast.makeText(fuckingContext, "ROLLBACK, logID: ${outgoingPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
                return false
            }
        }
    }
    suspend fun pushOneDeltaFurther(oldPinLogical: PinLogical, updPinLogical: PinLogical): Boolean {

        when (validatePin(updPinLogical)) {
            is ValidationResult.Valid -> {
                // <PUSH TO ROOM>
//                // #1 Get existing entity with its internalId
//                val existingEntity = pinDao.getById(updPinLogical.pinLogicalId)
//                    ?: return false  // SIMPLE ROLLBACK IF STATE SYNC SCREWED!
//                // #2 Convert updated pin to entity
//                val updatedEntity = convertToEntity(updPinLogical)
//                // #3 Preserve the primary key
//                val entityToUpdate = updatedEntity.copy(
//                    internalId = existingEntity.internalId
//                )
//                // #4 Update using Room's @Update
//                pinDao.update(entityToUpdate)

                // validation passed?
                // #1 INSERT VERSION
                // #2 CHANGE WINNER
                val versionInternalID = pinDao.insertVersion(convertToEntity(updPinLogical))
                winnerDao.choosePinForRendering(ToBeRenderedPin(
                    pinLogicalId = updPinLogical.pinLogicalId,
                    pinVersionInternalID = versionInternalID)
                )

                // <construct DELTA -> PUSH to radio!>
                portalToMesh.serviceConnectionWrapper.sendToTheEther(prepUpdateProtobuf(oldPinLogical,updPinLogical).toByteArray())
                Toast.makeText(fuckingContext, "SIDE EFFECT UPDATE!", Toast.LENGTH_SHORT).show()
                return true
            }
            is ValidationResult.Invalid -> {
                // Log errors, emit rollback, etc
                Toast.makeText(fuckingContext, "ROLLBACK, logID: ${updPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
                return false
            }
        }

    }

    // 🧪🧪🧪 ------- EXPERIMENTAL ------- 🧪🧪🧪 //
    suspend fun pushPinFurther(
        oldPinLogical: PinLogical?,  // null for new pin
        newPinLogical: PinLogical
    ): Boolean {
        when (validatePin(newPinLogical)) {
            is ValidationResult.Valid -> {
                //#1 side effect COLD
                val versionInternalID = pinDao.insertVersion(convertToEntity(newPinLogical))
                winnerDao.choosePinForRendering(
                    ToBeRenderedPin(newPinLogical.pinLogicalId, versionInternalID)
                )

                //#2 side effect RADIO
                val message = if (oldPinLogical == null) {
                    prepCreationProtobuf(newPinLogical)
                } else {
                    prepUpdateProtobuf(oldPinLogical, newPinLogical)
                }
                portalToMesh.serviceConnectionWrapper.sendToTheEther(message.toByteArray())

                Toast.makeText(fuckingContext, "PUSHED: ${newPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
                return true
            }
            is ValidationResult.Invalid -> {
                Toast.makeText(fuckingContext, "INVALID: ${newPinLogical.pinLogicalId}", Toast.LENGTH_SHORT).show()
                return false
            }
        }
    }

    suspend fun getAllPins(): Set<PinLogical> {
        return pinDao.getAllActivePins().map { fetchedEntity ->
            PinLogical(
                pinLogicalId = fetchedEntity.pinLogicalId,
                lamportEpoch = fetchedEntity.lamportEpoch,
                editorMark = fetchedEntity.editorHash,
                expirationTimestamp = fetchedEntity.expirationTimestamp,
                pinPhysProps = PinUI(
                    geoPoint = GeoPoint(fetchedEntity.latitude / 1e6, fetchedEntity.longitude / 1e6),
                    iconUnicode = fetchedEntity.iconUnicode,
                    label = fetchedEntity.label,
                    rotationByte = fetchedEntity.rotationByte,
                    isHiddenBeforeTTL = fetchedEntity.isHiddenBeforeTTL,
                )
            )
        }.toSet()
    }
    suspend fun deleteBulkByLogicalIds(pinLogicalIds: Set<Int>): Int {
        return pinDao.deleteBulkByLogIds(pinLogicalIds)
    }

    /*
    @Deprecated("Still debugging", level = DeprecationLevel.HIDDEN)
    suspend fun handleIncomingPinMessageOld(parsedRawPinMessage: PinMessage) {
        // #0 DECISION
        if (parsedRawPinMessage.hasLamportEpoch()) {

              if (pinDao.pinExists(parsedRawPinMessage.pinLogicalId)) {

                  val storedPinEntity = pinDao.getById(parsedRawPinMessage.pinLogicalId)!! // !! bc i got checks OUTSIDE, at this point im sure pin exists!

                  val newPinLogical = buildBasedOnOldPinFromMessage(storedPinEntity, parsedRawPinMessage)


                  if (newPinLogical.lamportEpoch > storedPinEntity.lamportEpoch) { // TODO бля, второй час ночи, попытка починить ХОЛОД

                      val existingEntity = pinDao.getById(newPinLogical.pinLogicalId)          // side effect 1!
                      val updatedEntity = convertToEntity(newPinLogical)
                      val entityToUpdate = updatedEntity.copy(
                              internalId = existingEntity!!.internalId
                      )
                      pinDao.update(entityToUpdate)

                      onHandledPinUpdateRequestCallback?.invoke(newPinLogical) // side effect 2!


                  } else if (newPinLogical.lamportEpoch < storedPinEntity.lamportEpoch) {
                      // drop! too logically old //TODO implement HISTORY (so no information gets dropped ever) 3
                  } else if (newPinLogical.lamportEpoch == storedPinEntity.lamportEpoch) {


                      // #0 prep data: current primary channel PSK + stored editorMark + new editorMark
                      val chPSK = portalToMesh.serviceConnectionWrapper.getPrimaryChannelPsk()
                      val md5er = MessageDigest.getInstance("MD5")
                      val newHash = md5er.digest("$chPSK$newPinLogical.editorHash".toByteArray())
                      val oldHash = md5er.digest("$chPSK$storedPinEntity.editorHash".toByteArray())

                      val newInt = newHash.take(2).joinToString("") { "%02x".format(it) }.toInt(16)
                      val oldInt = oldHash.take(2).joinToString("") { "%02x".format(it) }.toInt(16)

                      val newHashString = newHash.joinToString("") { "%02x".format(it) }
                      val oldHashString = oldHash.joinToString("") { "%02x".format(it) }

                      // CONFLICT! // TODO deterministic decision mkaing based on meshtastic channel PSK salt + new.editorHash vs old.editorHash
                      if ( newInt < oldInt ) {
                          //< do rewrite! > < new one wins! >
                          val existingEntity = pinDao.getById(newPinLogical.pinLogicalId)          // side effect 1!
                          val updatedEntity = convertToEntity(newPinLogical)
                          val entityToUpdate = updatedEntity.copy(
                              internalId = existingEntity!!.internalId
                          )
                          pinDao.update(entityToUpdate)

                          onHandledPinUpdateRequestCallback?.invoke(newPinLogical) // side effect 2!


                          Log.d("ASS", "NEW PIN WON ${newHashString}, ${oldHashString}")

                      } else {
                          Log.d("ASS", "OLD PIN WON ${newHashString}, ${oldHashString}")
                      }
                  }

              } else {
//                  <drop! for now its invalid garbage for us!> // TODO implement HISTORY (so no information gets dropped ever) 1
                  return
             }

        } else {
            // pure creation CHECK:
            if (parsedRawPinMessage.hasLat() && parsedRawPinMessage.hasLon()) {

                val builtPinLogical = buildBasedOnDefaultsFromMessage(parsedRawPinMessage)

                when (val result = validatePin(builtPinLogical)) {
                    is ValidationResult.Valid -> {
                        val logicalIdInternal = pinDao.insert(convertToEntity(builtPinLogical))
                        onHandledPinCreationRequestCallback?.invoke(builtPinLogical)
                    }
                    is ValidationResult.Invalid -> { /* DROP? */ }
                }
            } else {
                //                  <drop! for now its invalid garbage for us!> // TODO implement HISTORY (so no information gets dropped ever) 2
                return
            }
        }
    }
*/
    suspend fun handleIncomingPinMessage(parsedRawPinMessage: PinMessage) {

        // # 00
//        val foundStoredPin = pinDao.getById(parsedRawPinMessage.pinLogicalId)
        val foundStoredPin = pinDao.getOneActivePinByLogId(parsedRawPinMessage.pinLogicalId)

        // #0 first SEEK for the pin in COLD STORAGE
        val superpositionedPin = when ( foundStoredPin ) {
            null -> buildBasedOnDefaultsFromMessage(parsedRawPinMessage)
            else -> buildBasedOnOldPinFromMessage(foundStoredPin, parsedRawPinMessage)
        }

        // #1 REUSE validation! complexity needed bc gotta validate ASAP BEFORE further logic
        when (val result = validatePin(superpositionedPin)) {
            is ValidationResult.Invalid -> {
                Log.e("PinValidation", "Invalid pin: ${superpositionedPin.pinLogicalId}, errors: ${result.errors}")
                return  // < BREAK // >
            }
            is ValidationResult.Valid -> { /* < VALIDATION PASS > */ }
        }

        // #2 merge! ( merge decision )
        // < merge func call? with when. and call side effects>
        // #1 no pin was found!                                 -> push to the DB + make a winner (no matter what lamport, no matter how was rebuilt)
        // #2 pin was found! but incoming pin is NEWER          -> push to the DB + make a winner
        // #3 pin was found! but incoming pin IS OLDER!         -> push to the DB + dont make a winner
        // #4 pin was found! but incoming pin lamport == stored -> tie break messages + push incoming to the DB + make winner... the new winner
        val toBeRendered = when {
            foundStoredPin == null -> true                                                      // case #1
            superpositionedPin.lamportEpoch > foundStoredPin.lamportEpoch -> true               // case #2
            superpositionedPin.lamportEpoch < foundStoredPin.lamportEpoch -> false              // case #3
            else -> tieBreakConflict(superpositionedPin, foundStoredPin)     // case #4 // null safe! bc goes AFTER null check case!
        }

        // #3 STORE THIS VERSION ANYWAY + get generated ID from the DB
        val justAddedInternalID = pinDao.insertVersion(convertToEntity(superpositionedPin))

        // #4 make this version the winner if it won and shld be rendered!
        if (toBeRendered) {
            winnerDao.choosePinForRendering(ToBeRenderedPin(pinLogicalId = superpositionedPin.pinLogicalId, pinVersionInternalID = justAddedInternalID))

            // still gotta choose the correct callback! still different paths!
            if (foundStoredPin != null) {
                onHandledPinUpdateRequestCallback?.invoke(superpositionedPin)
            } else {
                onHandledPinCreationRequestCallback?.invoke(superpositionedPin)
            }

        } else {
            // < mark some how in GUI that THIS logical id PIN is 100% saved WRONG on smn's phone! manual rebroadcast recommended >
        }
    } // handler ver 3.0 finish




// 🎊🎊🎊 INTERACTIVE PART 🎊🎊🎊


}
