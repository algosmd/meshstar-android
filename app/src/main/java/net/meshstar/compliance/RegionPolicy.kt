package net.meshstar.compliance

import android.content.Context
import android.telephony.TelephonyManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.meshstar.meshcore.*
import java.util.Locale

class RegionPolicy(context:Context) {
    data class CommunityConsent(val country:String,val id:String,val radio:RadioParameters)
    data class State(val country:String="",val preset:String?=null,val custom:RadioParameters?=null,val reconfirm:Boolean=true,val nodes:Set<String> = emptySet(),val sim:String?=null,val hint:String?=null,val communityConsent:CommunityConsent?=null) {
        fun acknowledged(preset:RadioPreset)=communityConsent==CommunityConsent(country,preset.id,preset.radio)
        fun canApply(radio:RadioParameters,communityPresetID:String?=null):Boolean {
            if(communityPresetID!=null){val p=selected?:return false;return p.id==communityPresetID&&p.id!="custom"&&p.radio==radio&&(RegionalRules.permits(radio,country)||acknowledged(p))}
            return RegionalRules.permits(radio,country)
        }
        fun permitsAdvertRadio(radio:RadioParameters):Boolean {
            if(RegionalRules.permits(radio,country))return true
            val p=selected?:return false
            return acknowledged(p)&&kotlin.math.abs(p.radio.frequency-radio.frequency)<0.0011&&p.radio.bandwidth==radio.bandwidth&&p.radio.sf==radio.sf&&p.radio.cr==radio.cr
        }
        val satelliteAllowed get()=RegionalRules.satelliteAllowed(country)
        val selected get()=if(preset=="custom")custom?.takeIf{RegionalRules.permits(it,country)}?.let{RadioPreset("custom","custom",it,null)} else RegionalRules.presets(country).firstOrNull{it.id==preset&&(RegionalRules.permits(it.radio,country)||acknowledged(it))}
    }
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("meshstar.region",Context.MODE_PRIVATE)
    private val gson=Gson()
    private val mutable=MutableStateFlow(runCatching{gson.fromJson(prefs.getString("state",null),State::class.java)}.getOrNull()?:State())
    val state=mutable.asStateFlow()
    @Synchronized private fun update(value:State){check(prefs.edit().putString("state",gson.toJson(value)).commit());mutable.value=value}
    @Synchronized fun chooseCountry(code:String){require(code in Locale.getISOCountries());update(state.value.copy(country=code,preset=null,custom=null,reconfirm=true,nodes=emptySet(),communityConsent=null))}
    @Synchronized fun confirm(id:String,custom:RadioParameters?=null,acknowledgeWarning:Boolean=false){
        val before=state.value
        val selected=if(id=="custom")custom else RegionalRules.presets(before.country).firstOrNull{it.id==id}?.radio
        require(selected!=null&&selected.valid){"compliance.band_error"}
        val within=RegionalRules.permits(selected,before.country)
        require(id!="custom"||within){"compliance.band_error"}
        require(within||acknowledgeWarning){"compliance.community_required"}
        update(before.copy(preset=id,custom=if(id=="custom")custom else null,reconfirm=false,nodes=emptySet(),communityConsent=if(id!="custom"&&acknowledgeWarning)CommunityConsent(before.country,id,selected)else null))
    }
    fun mayAdvertise(id:String?)=state.value.let{!it.reconfirm&&it.selected!=null&&id in it.nodes}
    @Synchronized fun approve(id:String){require(state.value.selected!=null&&!state.value.reconfirm);update(state.value.copy(nodes=state.value.nodes+id))}
    @Synchronized fun observeSim(signature:String?,hint:String?,locale:String?){
        val old=state.value
        val changed=signature!=null&&old.sim!=null&&signature!=old.sim
        update(old.copy(sim=signature?:old.sim,hint=hint?:locale,reconfirm=old.reconfirm||changed,preset=if(changed)null else old.preset,nodes=if(changed)emptySet()else old.nodes,communityConsent=if(changed)null else old.communityConsent))
    }
    fun checkHint(){
        val manager=app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val mcc=runCatching{manager?.simOperator?.takeIf{it.length>=5}?.take(3)}.getOrNull()
        val country=runCatching{manager?.simCountryIso?.uppercase(Locale.ROOT)?.takeIf{it.length==2}}.getOrNull()
        observeSim(mcc,country,Locale.getDefault().country.takeIf{it.length==2})
    }
    companion object {
        @Volatile private var instance:RegionPolicy?=null
        fun get(context:Context):RegionPolicy=instance?:synchronized(this){instance?:RegionPolicy(context).also{instance=it}}
    }
}
