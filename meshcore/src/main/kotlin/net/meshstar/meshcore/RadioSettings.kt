package net.meshstar.meshcore

import kotlin.math.*

data class RadioParameters(val frequency:Double,val bandwidth:Double,val sf:Int,val cr:Int) {
    val valid get() = frequency.isFinite() && frequency in 150.0..2500.0 && bandwidth in bandwidths && sf in 5..12 && cr in 5..8
    val bitRate get() = sf * bandwidth * 1000 / 2.0.pow(sf) * 4 / cr
    fun airtime(bytes:Int=216):Double {
        valid(valid && bytes>=0)
        val symbol=2.0.pow(sf)/(bandwidth*1000)
        val de=if(symbol>=0.016)1 else 0
        val payload=8+max(ceil((8.0*bytes-4*sf+28+16)/(4*(sf-2*de)))*cr,0.0)
        return (8+4.25+payload)*symbol
    }
    fun command(repeat:Boolean?=null):ByteArray {
        valid(valid)
        return byteArrayOf(0x0b)+little((frequency*1000).toLong())+little((bandwidth*1000).toLong())+byteArrayOf(sf.toByte(),cr.toByte())+(repeat?.let{byteArrayOf(if(it)1 else 0)}?:byteArrayOf())
    }
    companion object { val bandwidths=listOf(7.8,10.4,15.6,20.8,31.25,41.7,62.5,125.0,250.0,500.0) }
}
data class RadioPreset(val id:String,val name:String,val radio:RadioParameters,val limit:Int?,val region:String="CEPT",val ham:Boolean=false,val countries:Set<String> = emptySet()) {
    companion object { val all get()=RegionalRules.presets }
}

data class NodeSettings(val name:String,val key:ByteArray,val radio:RadioParameters,val power:Int,val maxPower:Int,
    val latitude:Double,val longitude:Double,val advertPolicy:Int,val telemetry:Int,val manualContacts:Int,val multiAcks:Int,
    val firmware:String="",val repeat:Boolean?=null,val batteryMV:Int?=null,val measurements:Map<String,Double> = emptyMap()) {
    val batteryPercent:Int? get()=batteryMV?.takeIf{it in 2500..4500}?.let{((it-3000)*100/1200).coerceIn(0,100)}
    companion object { fun parse(b:ByteArray):NodeSettings {
        valid(b.size>=58&&b.u(0)==5)
        return NodeSettings(b.text(58),b.copyOfRange(4,36),RadioParameters(b.word(48)/1000.0,b.word(52)/1000.0,b.u(56),b.u(57)),
            b[2].toInt(),b[3].toInt(),b.word(36).toInt()/1e6,b.word(40).toInt()/1e6,b.u(45),b.u(46),b.u(47),b.u(44))
    } }
}
sealed interface NodeChange {
    data class Radio(val value:RadioParameters):NodeChange
    data class Power(val value:Int):NodeChange
    data class Name(val value:String):NodeChange
    data class Coordinates(val latitude:Double,val longitude:Double):NodeChange
    data class AdvertPolicy(val value:Int):NodeChange
    fun frame(old:NodeSettings):ByteArray = when(this) {
        is Radio->value.command(old.repeat)
        is Power->{valid(value in -9..old.maxPower.coerceIn(-9,127));byteArrayOf(0x0c)+little(value.toLong())}
        is Name->{valid(value.toByteArray().size in 1..31&&value.none{it.isISOControl()});byteArrayOf(8)+value.toByteArray()}
        is Coordinates->{valid(latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0);byteArrayOf(0x0e)+little((latitude*1e6).toLong())+little((longitude*1e6).toLong())+ByteArray(4)}
        is AdvertPolicy->{valid(value in 0..1);byteArrayOf(0x26,old.manualContacts.toByte(),old.telemetry.toByte(),value.toByte(),old.multiAcks.toByte())}
    }
    fun confirmed(actual:NodeSettings):Boolean=when(this){
        is Radio->abs(actual.radio.frequency-value.frequency)<0.0011 && actual.radio.copy(frequency=value.frequency)==value
        is Power->actual.power==value
        is Name->actual.name==value
        is Coordinates->abs(actual.latitude-latitude)<0.000002&&abs(actual.longitude-longitude)<0.000002
        is AdvertPolicy->actual.advertPolicy==value
    }
}
object LocalTelemetry {
    fun parse(b:ByteArray,key:ByteArray):Map<String,Double> {
        valid(b.size>=8&&b.u(0)==0x8b&&b.copyOfRange(2,8).contentEquals(key.copyOf(6)))
        val sizes=mapOf(0 to 1,1 to 1,2 to 2,3 to 2,100 to 4,101 to 2,102 to 1,103 to 2,104 to 1,113 to 6,115 to 2,116 to 2,117 to 4,118 to 4,120 to 1,121 to 2,125 to 2,128 to 2,130 to 4,131 to 6,132 to 2,133 to 4,134 to 6,135 to 3,136 to 9,137 to 11,138 to 4,142 to 1)
        val result=mutableMapOf<String,Double>();var offset=8
        while(offset+2<=b.size){
            val type=b.u(offset+1);val size=sizes[type]?:break;valid(offset+2+size<=b.size)
            if(size==2){val v=(b.u(offset+2) shl 8) or b.u(offset+3)
                when(type){116->result["V"]=v/100.0;103->result["°C"]=v.toShort()/10.0;115->result["hPa"]=v/10.0}
            }
            if(type==104)result["%"]=b.u(offset+2)/2.0
            offset+=2+size
        }
        return result // GPS telemetry is deliberately ignored.
    }
}
