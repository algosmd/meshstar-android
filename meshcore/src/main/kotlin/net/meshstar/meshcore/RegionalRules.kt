package net.meshstar.meshcore

import com.google.gson.Gson

/** Bundled, versioned policy: preset facts, frequency bands and route restrictions are separate. */
object RegionalRules {
    data class Band(val country:String,val lower:Double,val upper:Double)
    private data class Restriction(val country:String)
    private data class Radio(val frequency:Double,val bandwidth:Double,val spreadingFactor:Int,val codingRate:Int)
    private data class Preset(val id:String,val name:String,val radio:Radio,val regulatoryMax:Int?,val region:String,val ham:Boolean,val countries:Set<String>)
    private data class Catalog(val presets:List<Preset>,val bands:List<Band>,val satelliteRestricted:List<Restriction>)
    private val catalog:Catalog by lazy {
        RegionalRules::class.java.getResourceAsStream("/RadioPolicy.json")!!.reader().use { Gson().fromJson(it,Catalog::class.java) }
    }
    val presets:List<RadioPreset> by lazy { catalog.presets.map { p->RadioPreset(p.id,p.name,RadioParameters(p.radio.frequency,p.radio.bandwidth,p.radio.spreadingFactor,p.radio.codingRate),p.regulatoryMax,p.region,p.ham,p.countries) } }
    fun presets(country:String)=presets.filter{!it.ham&&country in it.countries}
    fun bands(country:String)=catalog.bands.filter{it.country==country}
    fun satelliteAllowed(country:String)=catalog.satelliteRestricted.none{it.country==country}
    fun permits(radio:RadioParameters,country:String):Boolean {
        if(!radio.valid)return false
        val half=radio.bandwidth/2000
        return bands(country).any{radio.frequency-half>=it.lower-0.000001&&radio.frequency+half<=it.upper+0.000001}
    }
}
