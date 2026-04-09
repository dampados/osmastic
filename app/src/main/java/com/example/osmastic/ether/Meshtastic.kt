package com.example.osmastic.ether

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.example.osmastic.repo.RepoPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.primaryChannel
import org.meshtastic.proto.MeshPacket

import org.meshtastic.proto.ChannelSet

class MeshtasticPortal(
    private val context: Context,
    private val repo: RepoPin,
) {

    // 📝📝📝 PROPS 📝📝📝
    // 0. class props, config
    companion object {
        const val OUR_PORT = 256
        const val MESH_APP_PACKAGE = "com.geeksville.mesh"
        const val MESH_SERVICE_CLASS = "$MESH_APP_PACKAGE.service.MeshService"
    }

    // 1. AIDL interface for sending
    private var osmasticToMeshtasticLinkInterface: IMeshService? = null

    // 3. SCOPE to push suspends into
    private val radioCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // 📝📝📝 PROPS 📝📝📝


    // 🎤️🎤️🎤️ OBJECTS DOERS 🎤️🎤️🎤️
    internal val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val packet = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra("com.geeksville.mesh.Payload", DataPacket::class.java)
            }
            else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra<DataPacket>("com.geeksville.mesh.Payload")
            }

            val bytes = packet?.bytes?.toByteArray()
            if (bytes != null) {
                try {
                    val parsedRawPinMessage = PinMessage.parseFrom(bytes)           // PARSE
                    // try catch for parseFrom? no sense\ use for the app logic . . .
                    radioCoroutineScope.launch {
                        repo.handleIncomingPinMessage(parsedRawPinMessage)   // HANDLE!!!! RAISE UP IN RAW, shoot and forget
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "New message in chat! $e", Toast.LENGTH_SHORT).show() // TODO toast about unparseable - chat?
                }
            }
        }
    }
    internal val serviceConnectionWrapper = ServiceConnectionWrapper(context) { meshService ->
        osmasticToMeshtasticLinkInterface = meshService // BINDING TO THE INTERFACE TO GET THE MENU
    }
    // 🎤️🎤️🎤️ OBJECTS DOERS 🎤️🎤️🎤️


    // 🏮🏮🏮 PUBLIC API 🏮🏮🏮
    fun connect() {
        // 1. Find and bind to the Meshtastic service
        val intent = Intent("com.geeksville.mesh.Service")
        val resolveInfo = context.packageManager.queryIntentServices(intent, 0)

        if (resolveInfo.isEmpty()) {
            println("❌ Meshtastic service not found!")
            return
        }

        val serviceInfo = resolveInfo[0].serviceInfo
        println("✅ Found service: ${serviceInfo.packageName}/${serviceInfo.name}")

        intent.setClassName(serviceInfo.packageName, serviceInfo.name)
        serviceConnectionWrapper.bind(intent)


        val filter = IntentFilter().apply {
            addAction("com.geeksville.mesh.RECEIVED.1") // CATCH MESSAGES! 1
            addAction("com.geeksville.mesh.RECEIVED.${OUR_PORT}")
            addAction("com.geeksville.mesh.RECEIVED_DATA") // optional catch-all?
        }

        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)


        println("✅ Connect complete - bound to service and registered receiver")


    }


    fun disconnect() {
        // TODO: Unregister receiver
        // TODO: Unbind service
    }


    // Get connected node NAME
//    suspend fun getNodeName(): String {
//        //TODO: get freaking name
//        return ""
//    }
//    suspend fun getNodes(): List<NodeInfo>? {
//        // TODO: Return meshService?.nodes or meshService?.getNodes()
//        return null
//    }
//    suspend fun getChannelPSK(channelNumber: Int): String? {
//        return null
//    }

    // 🏮🏮🏮 PUBLIC API 🏮🏮🏮
} // Main Class end




// ↗️↗️↗️ ServiceConnection wrapper class to get meshService ↗️↗️↗️ -> -> ->
class ServiceConnectionWrapper(
    private val context: Context,
    private val onServiceReady: (IMeshService) -> Unit
)
{
    // #1 interface AIDL of the meshtastic app
    private var meshService: IMeshService? = null

    // #2 This is the ACTUAL ServiceConnection!!!!! damn needed
    private val serviceConnectionObject = object : ServiceConnection { //TODO: on service connected TOASTS
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Toast.makeText(context, "✅ onServiceConnected callback!", Toast.LENGTH_SHORT).show() // todo: toast service check

            meshService = IMeshService.Stub.asInterface(service)
            meshService?.let { onServiceReady(it) }

            Toast.makeText(context, "meshService is ${if (meshService != null) "✅" else "❌"}", Toast.LENGTH_SHORT).show() //todo: meshService null check
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            Toast.makeText(context, "❌ onServiceDisconnected callback!", Toast.LENGTH_SHORT).show()
        }
    }

    //METHODS!!!
    fun bind(incomingIntent: Intent) {
        context.bindService(incomingIntent, serviceConnectionObject, Context.BIND_AUTO_CREATE)
    }
    fun unbind() {
        context.unbindService(serviceConnectionObject)
    }
    fun sendToTheEther(outgoingMessage: ByteArray) {
        if (meshService == null) {
            println("❌❌❌❌❌ Cannot send - meshService is dead")
            Toast.makeText(context, "❌❌❌❌❌ Cannot send - meshService is dead", Toast.LENGTH_LONG).show()

            return
        }

        try {
            val packetId = meshService!!.packetId // fetch next id FROM the BB

            val packet = DataPacket(
                to = DataPacket.ID_BROADCAST,
                bytes = outgoingMessage.toByteString(),
                dataType =  MeshtasticPortal.OUR_PORT,// OUR PORT finally! //PortNum.TEXT_MESSAGE_APP.value, // == 1
                from = DataPacket.ID_LOCAL,
                id = packetId, // IMPORTANT, built in id system
                channel = 0, // TODO primary channel for now, switch later (portal send fun argument?)
                wantAck = false,
                transportMechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA.value, // == 1
            )
            meshService?.send(packet) // 🚀🚀🚀 FIRE HERE

            Toast.makeText(context, "⬆️⬆️⬆️ SENT SIZE ${outgoingMessage.size}", Toast.LENGTH_SHORT).show() //TODO: size of the outgoing message

        } catch (e: Exception) {
            println("❌❌❌❌❌ sendToTheEther failed: ${e.message}")
            Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    fun getUniqueNodeIdMark(): String? {
        return meshService?.myId // conflict resolution
    }
    fun getSomethingPleaseMeshtasticPityMe(): String? {
        val channelsProtobufMessageRaw = meshService?.channelSet ?: return null
        val channelsProtobufParsed = ChannelSet.ADAPTER.decode(channelsProtobufMessageRaw)
        val psk = channelsProtobufParsed.settings.firstOrNull()?.psk

        return psk?.toByteArray()?.joinToString("") { "%02x".format(it) }
    }
    fun getPrimaryChannelPsk(): String {
        val raw = meshService?.channelSet ?: return "no_psk"
        val parsed = try {
            ChannelSet.ADAPTER.decode(raw)
        } catch (e: Exception) {
            return "no_psk"
        }
        val pskBytes = parsed.settings.firstOrNull()?.psk?.toByteArray()

        return when (pskBytes?.size) {
            32, 16 -> pskBytes.joinToString("") { "%02x".format(it) } // Real key
            1 -> "default_psk" // This is your "01" case
            else -> "no_psk" // Handles null, 0 bytes, or errors
        }
    }

    //METHODS!!!
}
// ↗️↗️↗️ ServiceConnection to get meshService ↗️↗️↗️ -> -> ->
