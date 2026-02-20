package com.example.osmastic.ether

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.model.DataPacket
import java.util.concurrent.atomic.AtomicInteger


class MeshtasticPortal(private val context: Context) {

    // 📝📝📝 PROPS 📝📝📝
    // 0. class props, config
    companion object {
        const val OUR_PORT = 16661
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

    internal val serviceConnection = ExtendedServiceConnection(context) { meshService ->
        osmasticToMeshtasticLinkInterface = meshService // BINDING TO THE INTERFACE TO GET THE MENU

//        CoroutineScope(Dispatchers.IO).launch {
//            val nodes = meshService.nodes
//            val nodeName = nodes?.firstOrNull()?.user?.longName
//            withContext(Dispatchers.Main) {
//                Toast.makeText(context, "📡 Connected to $nodeName", Toast.LENGTH_LONG).show() // TODO debug toast
//            }
//        }
    }
    // 🎤️🎤️🎤️ OBJECTS DOERS 🎤️🎤️🎤️






    // 🏮🏮🏮 PUBLIC API 🏮🏮🏮
    fun connect() {
        // 1. Bind to service
        val intent = Intent().apply {
            setClassName(MESH_APP_PACKAGE, MESH_SERVICE_CLASS)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // 2. Register receiver
        val filter = IntentFilter("com.geeksville.mesh.RECEIVED.${OUR_PORT}")
        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
    }
    fun disconnect() {
        // TODO: Unregister receiver
        // TODO: Unbind service
    }
    private val nextPacketId = AtomicInteger(1)
    suspend fun send(data: ByteArray) {
        val meshService = this.serviceConnection ?: run {
            println("❌ No meshService connected")
            return
        }

        // Create the packet
        val packet = DataPacket(
            to = DataPacket.ID_BROADCAST,  // Send to everyone in channel
            bytes = data.toByteString(),    // Your "HUY" bytes
            dataType = MeshtasticPortal.OUR_PORT,  // 16661
            id = nextPacketId.getAndIncrement(),
            wantAck = true
        )

        // Send it
        meshService.send(packet)
        println("✅ Sent packet ID ${packet.id}")
    }
    // Get connected node NAME
//    suspend fun getMyNodeInfo(): String {
//        return serviceConnection.getMyNodeInfo()
//    }
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
        if (intent?.action != "com.geeksville.mesh.RECEIVED.${MeshtasticPortal.OUR_PORT}") return
//        val packet = if (Build.VERSION.SDK_INT >= 33) {                   // TODO might still be useful? sdk check
//            intent.getParcelableExtra("packet", DataPacket::class.java)
//        } else {
//            @Suppress("DEPRECATION")
//            intent.getParcelableExtra<DataPacket>("packet")
//        }
        val packet = intent.getParcelableExtra<DataPacket>("packet")

        packet?.bytes?.let { onMessageReceived(it.toByteArray()) } // callback?
    }
}
// ↙️↙️↙️ BroadcastReceiver for incoming messages ↙️↙️↙️ <- <- <-


// ↗️↗️↗️ ServiceConnection CLASS to get meshService ↗️↗️↗️ -> -> ->

class ExtendedServiceConnection(
    private val context: Context,
    private val onServiceReady: (IMeshService) -> Unit
) {
    private var meshService: IMeshService? = null
    private val nextPacketId = AtomicInteger(1)

    // This is the ACTUAL ServiceConnection
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            meshService = IMeshService.Stub.asInterface(service)
            meshService?.let { onServiceReady(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
        }
    }

    fun bind() {
        val intent = Intent().apply {
            setClassName(MeshtasticPortal.MESH_APP_PACKAGE, MeshtasticPortal.MESH_SERVICE_CLASS)
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        context.unbindService(connection)
    }

    suspend fun getMyNodeInfo(): String {
        return try {
            meshService?.myNodeInfo.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun send(data: ByteArray) {
        val packet = DataPacket(
            to = DataPacket.ID_BROADCAST,
            bytes = data.toByteString(),
            dataType = MeshtasticPortal.OUR_PORT,
            id = nextPacketId.getAndIncrement(),
            wantAck = true
        )
        meshService?.send(packet)
    }
}

//
//class ExtendedServiceConnection(
//    private val context: Context,
//    private val onServiceReady: (IMeshService) -> Unit
//) : ServiceConnection {
//
//    private var meshService: IMeshService? = null
//
//    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//        meshService = IMeshService.Stub.asInterface(service)
//        meshService?.let { onServiceReady(it) }
//    }
//    override fun onServiceDisconnected(name: ComponentName?) {
//        meshService = null
//    }
//
//    suspend fun getMyNodeInfo(): String {
//        return try {
//            val myInfo = meshService?.myNodeInfo
//            myInfo.toString()  // Just dump everything
//        } catch (e: Exception) {
//            "Error: ${e.message}"
//        }
//    }
//
//    fun send(data: ByteArray) {
//        // Send logic here
//    }
//}
// ↗️↗️↗️ ServiceConnection to get meshService ↗️↗️↗️ -> -> ->
