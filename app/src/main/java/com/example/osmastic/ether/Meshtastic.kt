package com.example.osmastic.ether

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import java.util.concurrent.atomic.AtomicInteger


class MeshtasticPortal(private val context: Context) {

    // 📝📝📝 PROPS 📝📝📝
    // 0. class props, config
    companion object {
        const val OUR_PORT = 459
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
//    fun connect() { // WORKS WORKS WORKS
////        // 1. Bind to service
////        val intent = Intent().apply {
////            setClassName(MESH_APP_PACKAGE, MESH_SERVICE_CLASS)
////        }
////        serviceConnectionWrapper.bind()
////
////        // 2. Register receiver
////        val filter = IntentFilter("com.geeksville.mesh.RECEIVED_DATA")
//////        val filter = IntentFilter("com.geeksville.mesh.RECEIVED.${OUR_PORT}")
////        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
//
//
//
//        // Query for the actual service instead of hardcoding
//        val intent = Intent("com.geeksville.mesh.Service")
//        val resolveInfo = context.packageManager.queryIntentServices(intent, 0)
//
//        if (resolveInfo.isEmpty()) {
//            println("❌ Meshtastic service not found!")
//            return
//        }
//
//        val serviceInfo = resolveInfo[0].serviceInfo
//        println("✅ Found service: ${serviceInfo.packageName}/${serviceInfo.name}")
//
//        intent.setClassName(serviceInfo.packageName, serviceInfo.name)
//        serviceConnectionWrapper.bind(intent) // Modify bind() to accept intent
//    }

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

        // 2. Register broadcast receiver for incoming messages on your port
//        val filter = IntentFilter("com.geeksville.mesh.RECEIVED.${OUR_PORT}")
//        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)

////        val f1 = IntentFilter("com.geeksville.mesh.RECEIVED_DATA")
//        val f1 = IntentFilter("com.geeksville.mesh.RECEIVED.1") // Match what you send
////        val f2 = IntentFilter("com.geeksville.mesh.RECEIVED.${MeshtasticPortal.OUR_PORT}")
//        ContextCompat.registerReceiver(
//            context,
//            broadcastReceiver,
//            f1,
//            ContextCompat.RECEIVER_NOT_EXPORTED
//        )
////        context.registerReceiver(broadcastReceiver, f2)

        val filter = IntentFilter().apply {
            addAction("com.geeksville.mesh.RECEIVED.1")
            addAction("com.geeksville.mesh.RECEIVED.${MeshtasticPortal.OUR_PORT}")
            addAction("com.geeksville.mesh.RECEIVED_DATA") // optional catch-all
        }

        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)


        println("✅ Connect complete - bound to service and registered receiver")


    }


    fun disconnect() {
        // TODO: Unregister receiver
        // TODO: Unbind service
    }
    private val nextPacketId = AtomicInteger(1)
    suspend fun sendToPortal(data: ByteArray) {
        val meshService = osmasticToMeshtasticLinkInterface ?: run {
            println("❌ No meshService connected")
            return
        }

        // Send it - but you need to call the wrapper's send method, not directly on interface
        serviceConnectionWrapper.sendToTheEther(data)
        println("✅ Sent data ${data.size} bytes")
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
//        Toast.makeText(context, "received smth: ${intent?.action}", Toast.LENGTH_SHORT).show()
//
//        // Get the packet from extras
//        val packet = if (android.os.Build.VERSION.SDK_INT >= 33) {
//            intent?.getParcelableExtra("packet", DataPacket::class.java)
//        } else {
//            @Suppress("DEPRECATION")
//            intent?.getParcelableExtra<DataPacket>("packet")
//        }
//
//
//        intent?.extras?.keySet()?.forEach { key ->
//            val value = intent.extras?.get(key)
//            println("🔑 Extra: $key = $value (${value?.javaClass?.simpleName})")
//            Toast.makeText(context, "$key: $value", Toast.LENGTH_SHORT).show()
//        }
//
//
//        // Show packet info in toast
//        if (packet != null) {
//            val bytes = packet.bytes?.toByteArray()
//            val message = String(bytes ?: byteArrayOf())
//            Toast.makeText(context, "📨 $message", Toast.LENGTH_LONG).show()
//
//            // Also emit to your flow
//            bytes?.let { onMessageReceived(it) }
//        } else {
//            Toast.makeText(context, "❌ No packet in intent", Toast.LENGTH_SHORT).show()
//        }

        Toast.makeText(context, "received smth: ${intent?.action}", Toast.LENGTH_SHORT).show()

        // Get the packet using the CORRECT key
        val packet = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("com.geeksville.mesh.Payload", DataPacket::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<DataPacket>("com.geeksville.mesh.Payload")
        }

        if (packet != null) {
            val bytes = packet.bytes?.toByteArray()
            val message = String(bytes ?: byteArrayOf())
            Toast.makeText(context, "📨 $message", Toast.LENGTH_LONG).show()
            bytes?.let { onMessageReceived(it) }
        } else {
            Toast.makeText(context, "❌ No Payload in intent", Toast.LENGTH_SHORT).show()
        }

    }
}
// ↙️↙️↙️ BroadcastReceiver for incoming messages ↙️↙️↙️ <- <- <-








// ↗️↗️↗️ ServiceConnection CLASS to get meshService ↗️↗️↗️ -> -> ->
class ServiceConnectionWrapper(
    private val context: Context,
    private val onServiceReady: (IMeshService) -> Unit
) {
    private var meshService: IMeshService? = null
    private val nextPacketId = AtomicInteger(1)

    // This is the ACTUAL ServiceConnection
    private val serviceConnectionObject = object : ServiceConnection { //TODO: on service connected TOASTS
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            println("✅ onServiceConnected FIRED!")  // ← Add this
            Toast.makeText(context, "✅ onServiceConnected FIRED!", Toast.LENGTH_SHORT)
            meshService = IMeshService.Stub.asInterface(service)
            println("✅ meshService is ${if (meshService != null) "not null" else "null"}")  // ← And this
            meshService?.let { onServiceReady(it) }
            Toast.makeText(context, "✅ meshService is ${if (meshService != null) "not null" else "null"}", Toast.LENGTH_SHORT)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
        }
    }

    fun bind(incomingIntent: Intent) {
//        val intent = Intent().apply {
//            setClassName(MeshtasticPortal.MESH_APP_PACKAGE, MeshtasticPortal.MESH_SERVICE_CLASS)
//        }
        context.bindService(incomingIntent, serviceConnectionObject, Context.BIND_AUTO_CREATE)
    }
    fun unbind() {
        context.unbindService(serviceConnectionObject)
    }
    suspend fun getMyNodeInfo(): String {
        return try {
            meshService?.myNodeInfo.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    fun sendToTheEther(data: ByteArray) {
        if (meshService == null) {
            println("❌ Cannot send - meshService is null")
            return
        }

        try {
//            val packet = DataPacket(
//                to = DataPacket.ID_BROADCAST,
//                bytes = data.toByteString(),
//                dataType = MeshtasticPortal.OUR_PORT,
//                id = nextPacketId.getAndIncrement(),
//                wantAck = true,
//                channel = 0,  // ← CRITICAL: Send on primary channel
//            )

            val testoChannelIndex = 0 /* set to channel index shown in official app */

            val packetId = meshService!!.packetId

            val packet = DataPacket(
                to = DataPacket.ID_BROADCAST,
                bytes = "AIDL-test".encodeToByteArray().toByteString(),
                dataType = PortNum.TEXT_MESSAGE_APP.value, // == 1
                from = DataPacket.ID_LOCAL,
//                time = System.currentTimeMillis(),
//                id = meshService!!.packetId, //nextPacketId.getAndIncrement(),
                id = packetId,
                channel = testoChannelIndex,
                wantAck = true,
                transportMechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA.value, // == 1
                hopLimit = 3,

            )
            meshService?.send(packet)
            println("✅ send() called on meshService")
        } catch (e: Exception) {
            println("❌ AIDL send failed: ${e.message}")
        }
    }
}

// ↗️↗️↗️ ServiceConnection to get meshService ↗️↗️↗️ -> -> ->
