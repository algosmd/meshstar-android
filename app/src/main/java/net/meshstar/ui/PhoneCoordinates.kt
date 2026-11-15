package net.meshstar.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/** Explicit one-shot request. No last-known location, storage, or background subscription. */
@Composable fun PhoneCoordinates(requestPermission:Boolean=false,label:T=T.radio_phone,clear:()->Unit={},received:(Double,Double)->Unit) {
    val context=LocalContext.current
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val granted=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    if(!granted&&!requestPermission)return
    var requested by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    val callback by rememberUpdatedState(received)
    val cleanup by rememberUpdatedState(clear)
    DisposableEffect(lifecycle){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)cleanup()}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);cleanup()}
    }
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()){result->if(result.values.any{it})requested=true else failed=true}
    MeshButton(tr(label),enabled=!requested){failed=false;if(granted)requested=true else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
    if(failed)Secondary(tr(T.location_unavailable),size=10)
    if(requested){
        DisposableEffect(Unit){
            val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var active=true
            val listener=object:LocationListener{
                override fun onLocationChanged(location:Location){if(!active)return;active=false;callback(location.latitude,location.longitude);requested=false}
                override fun onProviderEnabled(provider:String){}
                override fun onProviderDisabled(provider:String){}
                @Deprecated("platform") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
            }
            try{
                val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                val provider=if(fine&&manager.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                manager.requestSingleUpdate(provider,listener,Looper.getMainLooper())
            }catch(_:Exception){requested=false;failed=true}
            val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){active=false;manager.removeUpdates(listener);requested=false}}
            lifecycle.addObserver(observer)
            onDispose{active=false;lifecycle.removeObserver(observer);manager.removeUpdates(listener)}
        }
        LaunchedEffect(Unit){delay(20000);requested=false;failed=true}
    }
}
