package net.meshstar.meshcore

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class NodeDevice(val id:String,val name:String,val rssi:Int)
/** No location APIs, raw-frame logging or broadcast discovery. Only Nordic UART advertisements. */
@SuppressLint("MissingPermission")
class MeshCoreBle(private val context:Context,private val scope:CoroutineScope) {
    private val adapter get()=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val devicesValue=MutableStateFlow<List<NodeDevice>>(emptyList());val devices=devicesValue.asStateFlow()
    val rssi=MutableStateFlow<Int?>(null)
    val frames=EventChannel<ByteArray>(256)
    var disconnected: (Throwable?)->Unit={}
    private var gatt:BluetoothGatt?=null
    private var rx:BluetoothGattCharacteristic?=null
    private var mtu=23
    private var ready=CompletableDeferred<Unit>()
    private var written:CompletableDeferred<Unit>?=null
    private val operations=Mutex()
    private var scanning:Job?=null
    private val scan=object:ScanCallback(){
        override fun onScanResult(type:Int,result:ScanResult){
            val item=NodeDevice(result.device.address,result.scanRecord?.deviceName?:"MeshCore",result.rssi)
            devicesValue.value=(devicesValue.value.filterNot{it.id==item.id}+item).takeLast(64)
        }
        override fun onScanFailed(code:Int){scanning?.cancel()}
    }
    fun scan(){
        stopScan();devicesValue.value=emptyList()
        val scanner=adapter?.bluetoothLeScanner?:throw NodeError(NodeError.Code.OFFLINE)
        scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(Protocol.SERVICE))).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan)
        scanning=scope.launch{delay(15000);stopScan()}
    }
    fun stopScan(){runCatching{adapter?.bluetoothLeScanner?.stopScan(scan)};scanning?.cancel();scanning=null}
    suspend fun connect(id:String){
        disconnect();ready=CompletableDeferred();mtu=23
        val device=adapter?.getRemoteDevice(id)?:throw NodeError(NodeError.Code.OFFLINE)
        gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
        try{withTimeout(25000){ready.await()}}catch(e:Exception){disconnect();throw e}
    }
    private fun fail(error:Throwable){ready.completeExceptionally(error);written?.completeExceptionally(error);disconnected(error)}
    private val callback=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,state:Int){scope.launch{
            if(g!==gatt)return@launch
            if(status!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){fail(NodeError(NodeError.Code.OFFLINE,status));return@launch}
            if(state==BluetoothProfile.STATE_CONNECTED&&!g.discoverServices())fail(NodeError(NodeError.Code.FRAME))
        }}
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int){scope.launch{
            if(g!==gatt)return@launch
            val service=g.getService(UUID.fromString(Protocol.SERVICE))
            rx=service?.getCharacteristic(UUID.fromString(Protocol.RX))
            if(status!=0||rx==null||!g.requestMtu(185))fail(NodeError(NodeError.Code.MTU))
        }}
        override fun onMtuChanged(g:BluetoothGatt,value:Int,status:Int){scope.launch{
            if(g!==gatt)return@launch
            mtu=value
            if(status!=0||mtu<171){fail(NodeError(NodeError.Code.MTU));return@launch}
            val tx=g.getService(UUID.fromString(Protocol.SERVICE))?.getCharacteristic(UUID.fromString(Protocol.TX))
            val descriptor=tx?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if(tx==null||descriptor==null||!g.setCharacteristicNotification(tx,true)){fail(NodeError(NodeError.Code.FRAME));return@launch}
            descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if(!g.writeDescriptor(descriptor))fail(NodeError(NodeError.Code.FRAME))
        }}
        override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int){scope.launch{
            if(g!==gatt)return@launch
            if(status==0)ready.complete(Unit)else fail(NodeError(NodeError.Code.REJECTED,status))
        }}
        @Deprecated("Legacy callback required for API 26")
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic){offer(g,c.uuid,c.value?.copyOf()?:byteArrayOf())}
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray){offer(g,c.uuid,value.copyOf())}
        override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int){scope.launch{
            if(g!==gatt)return@launch
            if(status==0)written?.complete(Unit)else written?.completeExceptionally(NodeError(NodeError.Code.REJECTED,status))
        }}
        override fun onReadRemoteRssi(g:BluetoothGatt,value:Int,status:Int){scope.launch{if(g===gatt&&status==0)rssi.value=value}}
    }
    private fun offer(g:BluetoothGatt,uuid:UUID,bytes:ByteArray){scope.launch{
        if(g!==gatt||uuid!=UUID.fromString(Protocol.TX))return@launch
        if(bytes.size !in 1..512||!frames.trySend(bytes).isSuccess)fail(NodeError(NodeError.Code.FRAME))
    }}
    suspend fun write(bytes:ByteArray)=operations.withLock{
        val g=gatt?:throw NodeError(NodeError.Code.OFFLINE);val c=rx?:throw NodeError(NodeError.Code.OFFLINE)
        if(bytes.size>mtu-3)throw NodeError(NodeError.Code.MTU)
        val completion=CompletableDeferred<Unit>();written=completion
        try{
            c.writeType=if(c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE!=0)BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            c.value=bytes
            if(!g.writeCharacteristic(c))throw NodeError(NodeError.Code.OFFLINE)
            withTimeout(8000){completion.await()}
        }finally{if(written===completion)written=null}
    }
    fun readRssi(){runCatching{gatt?.readRemoteRssi()}}
    fun disconnect(){
        stopScan();val old=gatt;gatt=null;rx=null;rssi.value=null
        ready.completeExceptionally(NodeError(NodeError.Code.OFFLINE));written?.completeExceptionally(NodeError(NodeError.Code.OFFLINE));written=null
        runCatching{old?.disconnect();old?.close()}
        while(frames.tryReceive().isSuccess){} // Discard notifications from the closed connection epoch.
    }
}
