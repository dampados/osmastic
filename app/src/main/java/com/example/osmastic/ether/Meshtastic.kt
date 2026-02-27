package com.example.osmastic.ether

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import java.util.concurrent.atomic.AtomicInteger


class MeshtasticPortal(private val context: Context) {

    // 📝📝📝 PROPS 📝📝📝
    // 0. class props, config
    companion object {
        const val OUR_PORT = 256
        const val MESH_APP_PACKAGE = "com.geeksville.mesh"
        const val MESH_SERVICE_CLASS = "$MESH_APP_PACKAGE.service.MeshService"
    }

    // 1. AIDL interface for sending
    private var osmasticToMeshtasticLinkInterface: IMeshService? = null

    // 2. Flow for receiving messages (repo will collect this)
    private val _incomingMessagesFlowR = MutableSharedFlow<ByteArray>()
    val _incomingMessagesFlowRW: SharedFlow<ByteArray> = _incomingMessagesFlowR
    // 📝📝📝 PROPS 📝📝📝


    // 🎤️🎤️🎤️ OBJECTS DOERS 🎤️🎤️🎤️
    internal val broadcastReceiver = ExtendedBroadcastReceiver { bytes ->
        _incomingMessagesFlowR.tryEmit(bytes)
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
            addAction("com.geeksville.mesh.RECEIVED.1")
            addAction("com.geeksville.mesh.RECEIVED.${OUR_PORT}")
            addAction("com.geeksville.mesh.RECEIVED_DATA") // optional catch-all
        }

        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)


        println("✅ Connect complete - bound to service and registered receiver")


    }


    fun disconnect() {
        // TODO: Unregister receiver
        // TODO: Unbind service
    }

    suspend fun sendToPortal(data: ByteArray) { // TODO: sendToPortal func obsolete?

        // #1 check if theres anyone to ASK to send the message
        val meshService = osmasticToMeshtasticLinkInterface ?: run {
            Toast.makeText(context, "can't send - meshService is not CONNECTED ):", Toast.LENGTH_SHORT) // TODO: TOAST sendToPortal check before sending
            return
        }
        serviceConnectionWrapper.sendToTheEther(data)
    }

    // Get connected node NAME
    suspend fun getNodeName(): String {
        //TODO: get freaking name
        return ""
    }
    suspend fun getNodes(): List<NodeInfo>? {
        // TODO: Return meshService?.nodes or meshService?.getNodes()
        return null
    }
    // 🏮🏮🏮 PUBLIC API 🏮🏮🏮
} // Main Class end







// ↙️↙️↙️ BroadcastReceiver for incoming messages ↙️↙️↙️ <- <- <-
class ExtendedBroadcastReceiver(
    private val onMessageReceived: (ByteArray) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        Toast.makeText(context, "received smth: ${intent?.action}", Toast.LENGTH_SHORT).show() // todo: TOAST catching smth at all

        //#1 extract the PACKET from extras."com.geeksville.mesh.Payload" thing
        val packet = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("com.geeksville.mesh.Payload", DataPacket::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<DataPacket>("com.geeksville.mesh.Payload")
        }

        //#2 shoot callback if extracted EXISTS
        if (packet != null) {
//            val bytes = packet.bytes?.toByteArray()
//            val message = String(bytes ?: byteArrayOf())


            val bytes = packet.bytes?.toByteArray()
            if (bytes != null) {
                // Попробуем распарсить как наш PinMessage
                try {
                    val pin = PinMessage.parseFrom(bytes)
                    Toast.makeText(context, "✅ Pin ${pin.pinLogicalId}", Toast.LENGTH_SHORT).show()
                    onMessageReceived(bytes)  // передаём байты дальше
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ Не наш protobuf", Toast.LENGTH_SHORT).show()
                }
            }


//            Toast.makeText(context, "📨 $message", Toast.LENGTH_LONG).show() // todo: TOAST parsed NOT NULL message from the outside

            bytes?.let { onMessageReceived(it) }
        } else {
            Toast.makeText(context, "EMPTY MESSAGE", Toast.LENGTH_SHORT).show() // todo: TOAST parsed NOT NULL message from the outside FAILURE
        }
    }
}
// ↙️↙️↙️ BroadcastReceiver for incoming messages ↙️↙️↙️ <- <- <-








// ↗️↗️↗️ ServiceConnection wrapper class to get meshService ↗️↗️↗️ -> -> ->
class ServiceConnectionWrapper(
    private val context: Context,
    private val onServiceReady: (IMeshService) -> Unit
) {
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

    fun bind(incomingIntent: Intent) {
        context.bindService(incomingIntent, serviceConnectionObject, Context.BIND_AUTO_CREATE)
    }
    fun unbind() {
        context.unbindService(serviceConnectionObject)
    }
    fun sendToTheEther(outgoingMessage: ByteArray) {
        if (meshService == null) {
            println("❌❌❌❌❌ Cannot send - meshService is dead")
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
        } catch (e: Exception) {
            println("❌❌❌❌❌ sendToTheEther failed: ${e.message}")
        }
    }
}
// ↗️↗️↗️ ServiceConnection to get meshService ↗️↗️↗️ -> -> ->
