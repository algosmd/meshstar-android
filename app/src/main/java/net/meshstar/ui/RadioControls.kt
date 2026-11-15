package net.meshstar.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.meshstar.meshcore.*
import kotlinx.coroutines.launch
import java.util.Locale

@Composable fun RadioParametersEditor(value:RadioParameters,change:(RadioParameters)->Unit) {
    var frequency by remember { mutableStateOf(value.frequency.toString()) }
    LaunchedEffect(value.frequency){if(value.frequency.isFinite() && frequency.replace(',','.').toDoubleOrNull()!=value.frequency)frequency=value.frequency.toString()}
    var presets by remember { mutableStateOf(false) }
    MeshButton(tr(T.radio_preset)){presets=!presets}
    if(presets)MeshCard { RadioPreset.all.forEach { preset->MeshButton(preset.name){change(preset.radio);presets=false} };Secondary(tr(T.radio_custom),size=10) }
    CompactField(frequency,{frequency=it;change(value.copy(frequency=it.replace(',','.').toDoubleOrNull()?:Double.NaN))},"MHz")
    var bw by remember { mutableStateOf(false) }
    MeshButton("${value.bandwidth} kHz"){bw=!bw}
    if(bw)RadioParameters.bandwidths.forEach{bandwidth->MeshButton("$bandwidth kHz"){change(value.copy(bandwidth=bandwidth));bw=false}}
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { MeshButton("SF −",enabled=value.sf>5){change(value.copy(sf=value.sf-1))};Text("SF ${value.sf}");MeshButton("SF +",enabled=value.sf<12){change(value.copy(sf=value.sf+1))} }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { MeshButton("CR −",enabled=value.cr>5){change(value.copy(cr=value.cr-1))};Text("CR 4/${value.cr}");MeshButton("CR +",enabled=value.cr<8){change(value.copy(cr=value.cr+1))} }
    if(value.valid)Secondary(String.format(Locale.ROOT,"%.1f bit/s · 216 B · %.3f s",value.bitRate,value.airtime()),size=10)
}
@Composable fun RadioControls(settings:NodeSettings?,action:suspend(UiAction)->Boolean) {
    val scope=rememberCoroutineScope()
    fun act(a:UiAction){scope.launch{action(a)}}
    LaunchedEffect(Unit){if(action(UiAction.NodeSettings))action(UiAction.NodeTelemetry)}
    Text(tr(T.radio_settings))
    MeshButton(tr(T.radio_refresh)){scope.launch{if(action(UiAction.NodeSettings))action(UiAction.NodeTelemetry)}}
    if(settings==null){Secondary(tr(T.node_unavailable));return}
    Text(settings.name)
    Secondary(settings.firmware+" · "+(settings.batteryPercent?.let{"≈$it% · "}?:"")+(settings.batteryMV?.let{"${it/1000.0} V"}?:""),size=10)
    settings.measurements.forEach{(unit,value)->Secondary("$value $unit",size=10)}
    var radio by remember(settings.radio){mutableStateOf(settings.radio)}
    var power by remember(settings.power){mutableFloatStateOf(settings.power.toFloat())}
    var name by remember(settings.name){mutableStateOf(settings.name)}
    var lat by remember(settings.latitude){mutableStateOf(settings.latitude.toString())}
    var lon by remember(settings.longitude){mutableStateOf(settings.longitude.toString())}
    var confirm by remember{mutableStateOf<NodeChange?>(null)}
    MeshCard {
        RadioParametersEditor(radio){radio=it}
        Text(tr(T.radio_warning),fontSize=12.sp,fontWeight=FontWeight.Bold)
        MeshButton(tr(T.radio_apply),enabled=radio.valid){confirm=NodeChange.Radio(radio)}
    }
    MeshCard {
        Secondary(tr(T.radio_power)+" ${power.toInt()} dBm",size=12)
        Slider(power,{power=it},valueRange=-9f..settings.maxPower.coerceAtLeast(-8).toFloat(),steps=(settings.maxPower+8).coerceAtLeast(0))
        RadioPreset.all.firstOrNull{it.radio==radio}?.limit?.let{Secondary(tr(T.radio_limit)+" $it dBm",size=10)}
        MeshButton(tr(T.save)){confirm=NodeChange.Power(power.toInt())}
    }
    MeshCard { CompactField(name,{name=it},tr(T.nickname));MeshButton(tr(T.save)){act(UiAction.ChangeNode(NodeChange.Name(name)))} }
    MeshCard {
        CompactField(lat,{lat=it},tr(T.radio_latitude));CompactField(lon,{lon=it},tr(T.radio_longitude))
        MeshButton(tr(T.radio_coordinates),enabled=lat.toDoubleOrNull()!=null&&lon.toDoubleOrNull()!=null){confirm=NodeChange.Coordinates(lat.toDouble(),lon.toDouble())}
        PhoneCoordinates { a,b->lat=a.toString();lon=b.toString();confirm=NodeChange.Coordinates(a,b) }
        MeshButton(tr(T.radio_hide)+if(settings.advertPolicy==1)" ✓" else ""){act(UiAction.ChangeNode(NodeChange.AdvertPolicy(if(settings.advertPolicy==1)0 else 1)))}
    }
    MeshCard {MeshButton(tr(T.node_advertise)){act(UiAction.NodeAdvert(false))};MeshButton(tr(T.radio_flood)){act(UiAction.NodeAdvert(true))};Secondary(tr(T.radio_flood_hint),size=10)}
    confirm?.let { change -> MeshCard {
        Secondary(tr(T.radio_confirm),size=12)
        MeshButton(tr(T.channel_confirm)){act(UiAction.ChangeNode(change));confirm=null}
        MeshButton(tr(T.back)){confirm=null}
    } }
}
