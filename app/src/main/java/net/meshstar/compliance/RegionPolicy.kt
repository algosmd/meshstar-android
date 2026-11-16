package net.meshstar.compliance

import android.content.Context
import android.telephony.TelephonyManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.meshstar.meshcore.*
import java.util.Locale

class RegionPolicy(context:Context) {
    data class State(val country:String="",val preset:String?=null,val custom:RadioParameters?=null,val reconfirm:Boolean=true,val nodes:Set<String> = emptySet(),val sim:String?=null,val hint:String?=null) {
        val satelliteAllowed get()=RegionalRules.satelliteAllowed(country)
        val selected get()=if(preset=="custom")custom?.takeIf{RegionalRules.permits(it,country)}?.let{RadioPreset("custom","custom",it,null)} else RegionalRules.presets(country).firstOrNull{it.id==preset&&RegionalRules.permits(it.radio,country)}
    }
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("meshstar.region",Context.MODE_PRIVATE)
    private val gson=Gson()
    private val mutable=MutableStateFlow(runCatching{gson.fromJson(prefs.getString("state",null),State::class.java)}.getOrNull()?:State())
    val state=mutable.asStateFlow()
    @Synchronized private fun update(value:State){check(prefs.edit().putString("state",gson.toJson(value)).commit());mutable.value=value}
    @Synchronized fun chooseCountry(code:String){require(code in Locale.getISOCountries());update(state.value.copy(country=code,preset=null,custom=null,reconfirm=true,nodes=emptySet()))}
    @Synchronized fun confirm(id:String,custom:RadioParameters?=null){
        val before=state.value
        val selected=if(id=="custom")custom else RegionalRules.presets(before.country).firstOrNull{it.id==id}?.radio
        require(selected!=null&&RegionalRules.permits(selected,before.country)){"compliance.band_error"}
        update(before.copy(preset=id,custom=custom,reconfirm=false,nodes=emptySet()))
    }
    fun mayAdvertise(id:String?)=state.value.let{!it.reconfirm&&it.selected!=null&&id in it.nodes}
    @Synchronized fun approve(id:String){require(state.value.selected!=null&&!state.value.reconfirm);update(state.value.copy(nodes=state.value.nodes+id))}
    @Synchronized fun observeSim(signature:String?,hint:String?,locale:String?){
        val old=state.value
        val changed=signature!=null&&old.sim!=null&&signature!=old.sim
        update(old.copy(sim=signature?:old.sim,hint=hint?:locale,reconfirm=old.reconfirm||changed,preset=if(changed)null else old.preset,nodes=if(changed)emptySet()else old.nodes))
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
