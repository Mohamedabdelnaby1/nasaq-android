package com.yawmi.diabetes

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

data class CareItem(val id:String=UUID.randomUUID().toString(),val kind:String,val title:String,
    val time:Long=0,val end:Long=0,val done:Boolean=false,val alert:Boolean=false)

data class DayLayout(val name:String,val order:List<String>,val hidden:Set<String>,
    val compact:Boolean,val sizes:Map<String,String>,val palette:String,val theme:String) {
    fun apply(p:Preferences)=p.copy(order=order,hiddenCards=hidden,compactHome=compact,cardSizes=sizes,
        palette=palette,theme=theme,dynamicColors=false)

    companion object {
        fun capture(name:String,p:Preferences)=DayLayout(name,p.order,p.hiddenCards,p.compactHome,p.cardSizes,p.palette,p.theme)
    }
}

data class CareData(val items:List<CareItem> = emptyList(),val layouts:List<DayLayout> = emptyList()){
    fun encode()=JSONObject()
        .put("items",JSONArray(items.map{JSONObject().put("id",it.id).put("kind",it.kind).put("title",it.title)
            .put("time",it.time).put("end",it.end).put("done",it.done).put("alert",it.alert)}))
        .put("layouts",JSONArray(layouts.map{JSONObject().put("name",it.name).put("order",JSONArray(it.order))
            .put("hidden",JSONArray(it.hidden.toList())).put("compact",it.compact).put("sizes",JSONObject(it.sizes))
            .put("palette",it.palette).put("theme",it.theme)}))

    companion object {
        fun decode(json:JSONObject?):CareData {
            if(json==null)return CareData()
            val items=json.optJSONArray("items") ?: JSONArray()
            val layouts=json.optJSONArray("layouts") ?: JSONArray()
            require(items.length()<=500 && layouts.length()<=12)
            return CareData((0 until items.length()).map{index->
                val data=items.getJSONObject(index)
                CareItem(data.getString("id"),data.getString("kind"),data.getString("title"),data.optLong("time"),
                    data.optLong("end"),data.optBoolean("done"),data.optBoolean("alert"))
            },(0 until layouts.length()).map{index->
                val data=layouts.getJSONObject(index)
                val order=data.getJSONArray("order");val hidden=data.getJSONArray("hidden")
                DayLayout(data.getString("name"),(0 until order.length()).map{order.getString(it)},
                    (0 until hidden.length()).map{hidden.getString(it)}.toSet(),data.optBoolean("compact"),
                    readSizes(data.optJSONObject("sizes")),data.getString("palette"),data.getString("theme"))
            })
        }
    }
}

internal fun readSizes(json:JSONObject?):Map<String,String> =
    json?.let { sizes -> sizes.keys().asSequence().associateWith { sizes.getString(it) } }.orEmpty()

fun checkLayout(order:List<String>,hidden:Set<String>,sizes:Map<String,String>){
    val cards=Preferences().order.toSet()
    require(order.size<=cards.size && order.distinct().size==order.size && order.all{it in cards})
    require(hidden.all{it in cards} && sizes.all{(key,size)->key in cards && size in setOf("small","standard","large")})
}
