package net.meshstar.ui

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import net.meshstar.compliance.RegionPolicy
import net.meshstar.meshcore.*
import java.util.Locale

/** Country is an explicit choice; SIM/locale provide suggestions only. Never requests GPS. */
@Composable fun RegionControls(region:RegionPolicy.State,action:suspend(UiAction)->Boolean) {
    val scope=rememberCoroutineScope()
    val locale=LocalContext.current.resources.configuration.locales[0]
    var choose by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var radio by remember { mutableStateOf(RadioParameters(Double.NaN,62.5,8,5)) }
    var saving by remember { mutableStateOf(false) }
    var pendingPreset by remember(region.country,region.sim){mutableStateOf<String?>(null)}
    var saved by remember { mutableStateOf(false) }
    fun select(code:String){scope.launch{if(action(UiAction.RegionCountry(code))){choose=false;saved=false}}}
    fun confirm(id:String,value:RadioParameters?=null,acknowledged:Boolean=false){saving=true;scope.launch{try{saved=action(UiAction.RegionConfirm(id,value,acknowledged))}finally{saving=false}}}
    Text(tr(T.compliance_title));Secondary(tr(T.compliance_hint),size=12)
    if(region.reconfirm&&region.country.isNotEmpty())Secondary(tr(T.compliance_reconfirm),size=12)
    if(choose){
        region.hint?.let{code->MeshButton(tr(T.compliance_suggested)+": "+Locale("",code).getDisplayCountry(locale),enabled=!saving){select(code)}}
        CompactField(query,{query=it},tr(T.compliance_search))
        Locale.getISOCountries().filter{query.isBlank()||it.contains(query,true)||Locale("",it).getDisplayCountry(locale).contains(query,true)}
            .sortedBy{Locale("",it).getDisplayCountry(locale)}.forEach{code->
                MeshButton(Locale("",code).getDisplayCountry(locale),enabled=!saving){select(code)}
            }
    } else {
        MeshButton(tr(T.compliance_change),enabled=!saving){choose=true}
        Text(Locale("",region.country).getDisplayCountry(locale))
        Secondary(RegionalRules.bands(region.country).joinToString(" · "){"${it.lower}–${it.upper} MHz"},size=10)
        if(RegionalRules.presets(region.country).isEmpty())Secondary(tr(T.compliance_unavailable),size=12)
        MeshButton(tr(T.compliance_custom),enabled=!saving){custom=!custom}
        if(custom)MeshCard {
            RadioParametersEditor(radio,showPresets=false){radio=it;saved=false}
            if(!RegionalRules.permits(radio,region.country))Secondary(tr(T.compliance_band_error),size=12)
            MeshButton(tr(T.compliance_confirm),enabled=!saving&&RegionalRules.permits(radio,region.country)){confirm("custom",radio)}
        }
        RegionalRules.presets(region.country).forEach{preset->MeshCard{
            MeshButton(tr(T.compliance_confirm)+" · "+preset.name,enabled=!saving){if(RegionalRules.permits(preset.radio,region.country))confirm(preset.id)else pendingPreset=preset.id}
            if(pendingPreset==preset.id){
                Secondary(communityWarning(region.country),size=12)
                MeshButton(tr(T.compliance_accept_warning),enabled=!saving){confirm(preset.id,acknowledged=true);pendingPreset=null}
                MeshButton(tr(T.back),enabled=!saving){pendingPreset=null}
            }
            Secondary("${preset.radio.frequency} MHz · ${preset.radio.bandwidth} kHz · SF${preset.radio.sf} · CR 4/${preset.radio.cr}",size=10)
        }}
        Secondary(tr(T.compliance_apply_hint),size=12)
        if(saving)Secondary(tr(T.admin_waiting),size=10)
        if(saved)Secondary(tr(T.radio_saved),size=10)
    }
}

@Composable fun communityWarning(country:String):String {
    val context=LocalContext.current
    val name=Locale("",country).getDisplayCountry(context.resources.configuration.locales[0])
    return context.getString(T.compliance_community_warning.resource,name)
}
