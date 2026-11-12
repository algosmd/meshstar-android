package net.meshstar

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import com.bitchat.android.mesh.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[30],application=android.app.Application::class,manifest=org.robolectric.annotation.Config.NONE)
class BluetoothFirstLaunchTest {
    @Test fun advertiserBecomesAvailableAfterPermissionWithoutRecreatingManager()=runTest {
        val context=mock(Context::class.java);val manager=mock(BluetoothManager::class.java);val adapter=mock(BluetoothAdapter::class.java)
        `when`(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(manager);`when`(manager.adapter).thenReturn(adapter);`when`(adapter.isEnabled).thenReturn(true)
        val permissions=mock(BluetoothPermissionManager::class.java);`when`(permissions.hasBluetoothPermissions()).thenReturn(true)
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        val transport=BluetoothGattServerManager(context,scope,mock(BluetoothConnectionTracker::class.java),permissions,mock(PowerManager::class.java),null,"synthetic")
        `when`(adapter.bluetoothLeAdvertiser).thenReturn(null)
        assertFalse(transport.start())
        `when`(adapter.bluetoothLeAdvertiser).thenReturn(mock(BluetoothLeAdvertiser::class.java))
        assertTrue(transport.start());scope.cancel()
    }
    @Test fun scannerBecomesAvailableAfterPermissionWithoutRecreatingManager()=runTest {
        val context=mock(Context::class.java);val manager=mock(BluetoothManager::class.java);val adapter=mock(BluetoothAdapter::class.java)
        `when`(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(manager);`when`(manager.adapter).thenReturn(adapter);`when`(adapter.isEnabled).thenReturn(true)
        val permissions=mock(BluetoothPermissionManager::class.java);`when`(permissions.hasBluetoothPermissions()).thenReturn(true)
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        val transport=BluetoothGattClientManager(context,scope,mock(BluetoothConnectionTracker::class.java),permissions,mock(PowerManager::class.java),null)
        `when`(adapter.bluetoothLeScanner).thenReturn(null)
        assertFalse(transport.start())
        `when`(adapter.bluetoothLeScanner).thenReturn(mock(BluetoothLeScanner::class.java))
        assertTrue(transport.start());scope.cancel()
    }
}
