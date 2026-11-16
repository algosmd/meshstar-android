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

@Composable fun RadioParametersEditor(value:RadioParameters,showPresets:Boolean=true,onPreset:((RadioPreset)->Unit)?=null,change:(RadioParameters)->Unit) {
    var frequency by remember { mutableStateOf(value.frequency.toString()) }
    LaunchedEffect(value.frequency){if(value.frequency.isFinite() && frequency.replace(',','.').toDoubleOrNull()!=value.frequency)frequency=value.frequency.toString()}
    var presets by remember { mutableStateOf(false) }
    if(showPresets)MeshButton(tr(T.radio_preset)){presets=!presets}
    val region by net.meshstar.compliance.RegionPolicy.get(androidx.compose.ui.platform.LocalContext.current).state.collectAsState()
    if(presets)MeshCard { RegionalRules.presets(region.country).forEach { preset->MeshButton(preset.name){if(onPreset!=null)onPreset(preset)else change(preset.radio);presets=false} };Secondary(tr(T.radio_custom),size=10) }
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
    val region by net.meshstar.compliance.RegionPolicy.get(androidx.compose.ui.platform.LocalContext.current).state.collectAsState()
    var selectedPreset by remember(settings.radio,region.country){mutableStateOf<String?>(null)}
    var power by remember(settings.power){mutableFloatStateOf(settings.power.toFloat())}
    var name by remember(settings.name){mutableStateOf(settings.name)}
    var lat by remember(settings.latitude){mutableStateOf(settings.latitude.toString())}
    var lon by remember(settings.longitude){mutableStateOf(settings.longitude.toString())}
    MeshCard {
        RadioParametersEditor(radio,onPreset={selectedPreset=it.id;radio=it.radio}){radio=it;selectedPreset=null}
        Text(tr(T.radio_warning),fontSize=12.sp,fontWeight=FontWeight.Bold)
        NodeChangeButton(tr(T.radio_apply),NodeChange.Radio(radio),enabled=radio.valid&&(selectedPreset!=null||RegionalRules.permits(radio,region.country)),action=action,communityPresetID=selectedPreset,warning=if(selectedPreset!=null&&!RegionalRules.permits(radio,region.country))communityWarning(region.country)else null,country=region.country)
    }
    MeshCard {
        Secondary(tr(T.radio_power)+" ${power.toInt()} dBm",size=12)
        Slider(power,{power=it},valueRange=-9f..settings.maxPower.coerceAtLeast(-8).toFloat(),steps=(settings.maxPower+8).coerceAtLeast(0))
        RadioPreset.all.firstOrNull{it.radio==radio}?.limit?.let{Secondary(tr(T.radio_limit)+" $it dBm",size=10)}
        NodeChangeButton(tr(T.save),NodeChange.Power(power.toInt()),action=action)
    }
    MeshCard { CompactField(name,{name=it},tr(T.nickname));MeshButton(tr(T.save)){act(UiAction.ChangeNode(NodeChange.Name(name)))} }
    MeshCard {
        CompactField(lat,{lat=it},tr(T.radio_latitude));CompactField(lon,{lon=it},tr(T.radio_longitude))
        val coordinates=NodeChange.Coordinates(lat.toDoubleOrNull()?:Double.NaN,lon.toDoubleOrNull()?:Double.NaN)
        NodeChangeButton(tr(T.radio_coordinates),coordinates,enabled=coordinates.latitude.isFinite()&&coordinates.longitude.isFinite(),action=action)
        PhoneCoordinates { a,b->lat=a.toString();lon=b.toString() }
        MeshButton(tr(T.radio_hide)+if(settings.advertPolicy==1)" ✓" else ""){act(UiAction.ChangeNode(NodeChange.AdvertPolicy(if(settings.advertPolicy==1)0 else 1)))}
    }
    MeshCard {MeshButton(tr(T.node_advertise)){act(UiAction.NodeAdvert(false))};MeshButton(tr(T.radio_flood)){act(UiAction.NodeAdvert(true))};Secondary(tr(T.radio_flood_hint),size=10)}
}

/** Confirmation replaces the initiating control, so it stays visible on a small screen. */
@Composable private fun NodeChangeButton(label:String,change:NodeChange,enabled:Boolean=true,action:suspend(UiAction)->Boolean,communityPresetID:String?=null,warning:String?=null,country:String="") {
    val scope=rememberCoroutineScope()
    var pending by remember(change,communityPresetID,country){mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}
    var result by remember(change){mutableStateOf<Boolean?>(null)}
    if(pending){
        if(warning!=null)Secondary(warning,size=12)
        Row {
            MeshButton(tr(T.channel_confirm),enabled=!saving){
                saving=true
                scope.launch {
                    try { result=action(UiAction.ChangeNode(change,communityPresetID,warning!=null));pending=false }
                    finally { saving=false }
                }
            }
            MeshButton(tr(T.back),enabled=!saving){pending=false}
        }
    } else MeshButton(label,enabled=enabled&&!saving){result=null;pending=true}
    if(saving)Secondary(tr(T.admin_waiting),size=10)
    else result?.let{Secondary(tr(if(it)T.radio_saved else T.failed),size=10)}
}
